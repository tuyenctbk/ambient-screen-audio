package com.aetherscreen.tv.service

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
import android.view.Gravity
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.aetherscreen.core.model.AppSettings
import com.aetherscreen.core.preferences.PreferencesManager
import com.aetherscreen.core.util.MediaControlUtil
import com.aetherscreen.tv.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.google.android.gms.wearable.Wearable

class TvOverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "aether_tv_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START = "com.aetherscreen.tv.action.START"
        const val ACTION_STOP = "com.aetherscreen.tv.action.STOP"

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

    private var overlayContainer: FrameLayout? = null
    private var clockTextView: TextView? = null
    private var isOverlayShowing = false

    private var currentSettings = AppSettings()
    private var timerJob: Job? = null
    private var clockJob: Job? = null
    private var remainingSeconds = 0

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        preferencesManager = PreferencesManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                serviceScope.launch {
                    currentSettings = preferencesManager.settingsFlow.first()
                    startForeground(NOTIFICATION_ID, buildNotification())
                    showOverlay()
                    if (currentSettings.sleepTimerMinutes > 0) {
                        startTimer(currentSettings.sleepTimerMinutes * 60)
                    }
                }
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun showOverlay() {
        if (isOverlayShowing) return

        // Container to intercept physical remote keys to dismiss overlay
        val container = object : FrameLayout(this) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    stopSelf()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        container.setBackgroundColor(Color.BLACK)

        // Dim Clock display for bedside screensaver mode
        if (currentSettings.bedsideClockEnabled) {
            val tv = TextView(this).apply {
                setTextColor(Color.DKGRAY)
                textSize = 28f
                alpha = 0.35f
                text = getCurrentTime()
            }
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START
            ).apply {
                leftMargin = 100
                topMargin = 100
            }
            container.addView(tv, lp)
            clockTextView = tv
            startClockAnimation()
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Overlay is focusable so we capture remote key clicks
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        try {
            windowManager.addView(container, params)
            overlayContainer = container
            isOverlayShowing = true
            broadcastStatusToWear(true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startClockAnimation() {
        clockJob?.cancel()
        clockJob = serviceScope.launch {
            val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            while (isActive) {
                clockTextView?.text = sdf.format(Date())

                val tv = clockTextView ?: break
                val lp = tv.layoutParams as FrameLayout.LayoutParams

                // Shift coordinates randomly to prevent static OLED burning
                val maxW = (screenWidth * 0.75f).toInt()
                val maxH = (screenHeight * 0.75f).toInt()
                val minMargin = 80

                lp.leftMargin = Random.nextInt(minMargin, maxOf(minMargin + 10, maxW))
                lp.topMargin = Random.nextInt(minMargin, maxOf(minMargin + 10, maxH))

                tv.layoutParams = lp

                delay(60000) // Update position and time every 60s
            }
        }
    }

    private fun getCurrentTime(): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    private fun startTimer(seconds: Int) {
        timerJob?.cancel()
        remainingSeconds = seconds
        timerJob = serviceScope.launch {
            while (remainingSeconds > 0) {
                delay(1000)
                remainingSeconds--
            }
            MediaControlUtil.pausePlayback(this@TvOverlayService)
            stopSelf()
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, TvOverlayService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mainIntent = Intent(this, MainActivity::class.java)
        val mainPendingIntent = PendingIntent.getActivity(
            this, 1, mainIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AetherScreen TV Dimmer Active")
            .setContentText("OLED Burn-in protection active. Press any remote button to wake screen.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(mainPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Wake Screen", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "AetherScreen TV Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Manages TV black overlay screensaver."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun removeOverlay() {
        overlayContainer?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        overlayContainer = null
        isOverlayShowing = false
        broadcastStatusToWear(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        removeOverlay()
        timerJob?.cancel()
        clockJob?.cancel()
        serviceScope.cancel()
    }

    private fun broadcastStatusToWear(running: Boolean) {
        val statusStr = if (running) "active:TV" else "inactive:TV"
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
