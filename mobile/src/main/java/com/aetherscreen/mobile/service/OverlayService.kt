package com.aetherscreen.mobile.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.aetherscreen.core.model.AppSettings
import com.aetherscreen.core.preferences.PreferencesManager
import com.aetherscreen.core.util.MediaControlUtil
import com.aetherscreen.mobile.sensor.SensorController
import com.aetherscreen.mobile.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.google.android.gms.wearable.Wearable

class OverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "aetherscreen_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.aetherscreen.action.START"
        const val ACTION_ARM = "com.aetherscreen.action.ARM"
        const val ACTION_ENGAGE_NOW = "com.aetherscreen.action.ENGAGE_NOW"
        const val ACTION_STOP = "com.aetherscreen.action.STOP"
        const val ACTION_ADD_10_MIN = "com.aetherscreen.action.ADD_10_MIN"

        // How often to poll for active media playback while armed.
        private const val PLAYBACK_POLL_INTERVAL_MS = 700L

        private val _isRunningFlow = MutableStateFlow(false)
        val isRunningFlow: StateFlow<Boolean> = _isRunningFlow.asStateFlow()

        var isRunning: Boolean
            get() = _isRunningFlow.value
            set(value) {
                _isRunningFlow.value = value
            }
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var windowManager: WindowManager
    private lateinit var preferencesManager: PreferencesManager
    private var overlayView: View? = null
    private var sensorController: SensorController? = null
    private var isOverlayShowing = false

    private var currentSettings: AppSettings = AppSettings()
    private var timerJob: Job? = null
    private var waitForPlaybackJob: Job? = null
    private var isWaitingForPlayback = false
    private var remainingSeconds = 0

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        preferencesManager = PreferencesManager(this)

        createNotificationChannel()

        // Read preferences asynchronously
        serviceScope.launch {
            preferencesManager.settingsFlow.collect { settings ->
                currentSettings = settings
                if (isOverlayShowing) {
                    // Update overlay parameters on settings changes
                    updateOverlayView()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                serviceScope.launch {
                    currentSettings = preferencesManager.settingsFlow.first()
                    startForeground(NOTIFICATION_ID, buildNotification())
                    engageOverlay()
                }
            }
            ACTION_ARM -> {
                serviceScope.launch {
                    currentSettings = preferencesManager.settingsFlow.first()
                    isWaitingForPlayback = true
                    startForeground(NOTIFICATION_ID, buildNotification())
                    awaitPlaybackThenEngage()
                }
            }
            ACTION_ENGAGE_NOW -> {
                engageOverlay()
            }
            ACTION_STOP -> {
                stopSelf()
            }
            ACTION_ADD_10_MIN -> {
                addTime(10)
            }
        }
        return START_NOT_STICKY
    }

    /**
     * Arms the service: waits (without covering the screen) until media playback is
     * detected, then engages the overlay. This lets the user launch a media app and
     * have the dimmer kick in only once audio actually starts.
     */
    private fun awaitPlaybackThenEngage() {
        waitForPlaybackJob?.cancel()
        waitForPlaybackJob = serviceScope.launch {
            while (isActive && !isOverlayShowing) {
                if (MediaControlUtil.isMediaPlaying(this@OverlayService)) {
                    engageOverlay()
                    break
                }
                delay(PLAYBACK_POLL_INTERVAL_MS)
            }
        }
    }

    /** Shows the overlay and starts the timer/sensors. */
    private fun engageOverlay() {
        waitForPlaybackJob?.cancel()
        isWaitingForPlayback = false
        if (isOverlayShowing) return
        showOverlay()
        if (currentSettings.sleepTimerMinutes > 0) {
            startTimer(currentSettings.sleepTimerMinutes * 60)
        }
        setupSensors(currentSettings)
        updateNotification()
    }

    private fun showOverlay() {
        if (isOverlayShowing) return

        val view = View(this)
        view.setBackgroundColor(Color.BLACK)

        val params = getOverlayLayoutParams(currentSettings)
        
        // Setup Gesture Control for waking up when screen is blacked out
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (currentSettings.doubleTapToWakeEnabled) {
                    triggerVibration()
                    stopSelf()
                    return true
                }
                return false
            }

            override fun onLongPress(e: MotionEvent) {
                // Alternative wake gesture
                triggerVibration()
                stopSelf()
            }
        })

        view.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            // Consume touches if lockTouch is enabled
            currentSettings.lockTouch
        }

        try {
            windowManager.addView(view, params)
            overlayView = view
            isOverlayShowing = true
            updateOverlayAlpha()
            broadcastStatusToWear(true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateOverlayView() {
        val view = overlayView ?: return
        val params = getOverlayLayoutParams(currentSettings)
        try {
            windowManager.updateViewLayout(view, params)
            updateOverlayAlpha()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateOverlayAlpha() {
        val view = overlayView ?: return
        val alpha = if (currentSettings.isBlackoutMode) 1.0f else currentSettings.dimLevel
        view.alpha = alpha
    }

    private fun getOverlayLayoutParams(settings: AppSettings): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        var flags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

        // If touch is not locked, let touches pass through so user can interact
        if (!settings.lockTouch) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        } else {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            flags,
            PixelFormat.TRANSLUCENT
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        return params
    }

    private fun removeOverlay() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        overlayView = null
        isOverlayShowing = false
        broadcastStatusToWear(false)
    }

    private fun setupSensors(settings: AppSettings) {
        sensorController?.stopListening()
        
        if (settings.pocketModeEnabled || settings.shakeToWakeEnabled) {
            sensorController = SensorController(
                context = this,
                onProximityChanged = { isClose ->
                    if (settings.pocketModeEnabled && isClose) {
                        // Pocket Mode triggered: force pure blackout and lock touch
                        serviceScope.launch {
                            currentSettings = currentSettings.copy(isBlackoutMode = true, lockTouch = true)
                            updateOverlayView()
                        }
                    }
                },
                onShakeDetected = {
                    if (settings.shakeToWakeEnabled) {
                        triggerVibration()
                        stopSelf()
                    }
                }
            )
            sensorController?.startListening()
        }
    }

    private fun startTimer(seconds: Int) {
        timerJob?.cancel()
        remainingSeconds = seconds
        timerJob = serviceScope.launch {
            while (remainingSeconds > 0) {
                updateNotification()
                delay(1000)
                remainingSeconds--
            }
            // Timer expired: Pause playback & dismiss
            MediaControlUtil.pausePlayback(this@OverlayService)
            triggerVibration()
            stopSelf()
        }
    }

    private fun addTime(minutes: Int) {
        if (timerJob == null || remainingSeconds <= 0) {
            startTimer(minutes * 60)
        } else {
            remainingSeconds += minutes * 60
            updateNotification()
        }
    }

    private fun triggerVibration() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

        vibrator?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                it.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                it.vibrate(100)
            }
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, OverlayService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val addTimeIntent = Intent(this, OverlayService::class.java).apply { action = ACTION_ADD_10_MIN }
        val addTimePendingIntent = PendingIntent.getService(
            this, 1, addTimeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            this, 2, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val waiting = isWaitingForPlayback && !isOverlayShowing

        val title = if (waiting) "AetherScreen Armed" else "AetherScreen Active"
        val contentText = when {
            waiting -> "Waiting for media playback to start\u2026"
            remainingSeconds > 0 -> {
                val mins = remainingSeconds / 60
                val secs = remainingSeconds % 60
                String.format("Sleep Timer: %02d:%02d remaining", mins, secs)
            }
            else -> "Overlay active and dimming display."
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(mainPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopPendingIntent)

        if (waiting) {
            val engageIntent = Intent(this, OverlayService::class.java).apply { action = ACTION_ENGAGE_NOW }
            val engagePendingIntent = PendingIntent.getService(
                this, 3, engageIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_menu_view, "Black out now", engagePendingIntent)
        }

        if (remainingSeconds > 0) {
            builder.addAction(android.R.drawable.ic_input_add, "+10 Mins", addTimePendingIntent)
        }

        return builder.build()
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "AetherScreen Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps overlay active and manages sleep timers."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        removeOverlay()
        sensorController?.stopListening()
        timerJob?.cancel()
        waitForPlaybackJob?.cancel()
        serviceScope.cancel()
    }

    private fun broadcastStatusToWear(running: Boolean) {
        val statusStr = if (running) "active:Phone" else "inactive:Phone"
        val nodeClient = Wearable.getNodeClient(this)
        val messageClient = Wearable.getMessageClient(this)
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            for (node in nodes) {
                messageClient.sendMessage(node.id, "/status", statusStr.toByteArray())
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
