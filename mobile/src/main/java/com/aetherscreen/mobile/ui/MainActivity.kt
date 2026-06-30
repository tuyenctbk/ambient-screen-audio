package com.aetherscreen.mobile.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aetherscreen.core.model.AppSettings
import com.aetherscreen.core.preferences.PreferencesManager
import com.aetherscreen.mobile.R
import androidx.compose.ui.res.stringResource
import com.aetherscreen.mobile.service.OverlayService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import com.google.firebase.initialize

// --- VIEWMODEL ---
class AetherViewModel(context: Context) : ViewModel() {
    private val preferencesManager = PreferencesManager(context.applicationContext)
    val settingsFlow = preferencesManager.settingsFlow

    fun updateDimLevel(scope: kotlinx.coroutines.CoroutineScope, level: Float) {
        scope.launch { preferencesManager.updateDimLevel(level) }
    }

    fun updateIsBlackoutMode(scope: kotlinx.coroutines.CoroutineScope, enabled: Boolean) {
        scope.launch { preferencesManager.updateIsBlackoutMode(enabled) }
    }

    fun updateLockTouch(scope: kotlinx.coroutines.CoroutineScope, enabled: Boolean) {
        scope.launch { preferencesManager.updateLockTouch(enabled) }
    }

    fun updateSleepTimer(scope: kotlinx.coroutines.CoroutineScope, minutes: Int) {
        scope.launch { preferencesManager.updateSleepTimerMinutes(minutes) }
    }

    fun updatePocketMode(scope: kotlinx.coroutines.CoroutineScope, enabled: Boolean) {
        scope.launch { preferencesManager.updatePocketModeEnabled(enabled) }
    }

    fun updateShakeToWake(scope: kotlinx.coroutines.CoroutineScope, enabled: Boolean) {
        scope.launch { preferencesManager.updateShakeToWakeEnabled(enabled) }
    }

    fun updateDoubleTapToWake(scope: kotlinx.coroutines.CoroutineScope, enabled: Boolean) {
        scope.launch { preferencesManager.updateDoubleTapToWakeEnabled(enabled) }
    }

    fun toggleTargetApp(scope: kotlinx.coroutines.CoroutineScope, packageName: String, currentSet: Set<String>) {
        scope.launch {
            if (currentSet.contains(packageName)) {
                preferencesManager.removeTargetApp(packageName)
            } else {
                preferencesManager.addTargetApp(packageName)
            }
        }
    }

    fun updateOnboardingCompleted(scope: kotlinx.coroutines.CoroutineScope, completed: Boolean) {
        scope.launch { preferencesManager.updateOnboardingCompleted(completed) }
    }
}

// --- ACTIVITY ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize AdMob safely
        try {
            MobileAds.initialize(this) {}
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Initialize Firebase safely
        try {
            Firebase.initialize(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setContent {
            AetherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F1016) // Premium Deep Indigo-Black background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
fun AetherTheme(content: @Composable () -> Unit) {
    val darkColorScheme = darkColorScheme(
        primary = Color(0xFF8B5CF6),      // Vivid Violet
        secondary = Color(0xFFD946EF),    // Bright Magenta
        background = Color(0xFF0F1016),
        surface = Color(0xFF1E1F29),
        onPrimary = Color.White,
        onSecondary = Color.White
    )
    MaterialTheme(
        colorScheme = darkColorScheme,
        content = content
    )
}

data class AppInfoItem(val name: String, val packageName: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // ViewModel setup
    val viewModel: AetherViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return AetherViewModel(context) as T
        }
    })
    
    val settingsState = viewModel.settingsFlow.collectAsState(initial = AppSettings())
    val settings = settingsState.value

    if (!settings.onboardingCompleted) {
        OnboardingScreen(
            settings = settings,
            viewModel = viewModel,
            onFinished = {
                viewModel.updateOnboardingCompleted(scope, true)
            }
        )
        return
    }

    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val isServiceRunning by OverlayService.isRunningFlow.collectAsState()
    var installedApps by remember { mutableStateOf<List<AppInfoItem>>(emptyList()) }
    var updateAvailableVersion by remember { mutableStateOf<String?>(null) }
    var showAppPicker by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current

    // Check service state periodically or when returning
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Load launchable user apps & check for updates
    LaunchedEffect(Unit) {
        checkForUpdates(context) { version ->
            updateAvailableVersion = version
        }

        scope.launch(Dispatchers.IO) {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val launchable = apps.filter { app ->
                (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 && pm.getLaunchIntentForPackage(app.packageName) != null
            }.map { app ->
                AppInfoItem(pm.getApplicationLabel(app).toString(), app.packageName)
            }.sortedBy { it.name }
            installedApps = launchable
        }
    }

    if (showAppPicker) {
        val pickerApps = remember(installedApps, settings.targetApps) {
            if (settings.targetApps.isNotEmpty()) {
                installedApps.filter { settings.targetApps.contains(it.packageName) }
            } else {
                installedApps
            }
        }
        AppPickerDialog(
            apps = pickerApps,
            onDismiss = { showAppPicker = false },
            onSelect = { packageName ->
                showAppPicker = false
                launchAppAndArm(context, packageName)
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // App Title / Branding Header
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 4.sp,
                style = androidx.compose.ui.text.TextStyle(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFF8B5CF6), Color(0xFFD946EF))
                    )
                )
            )
            Text(
                text = stringResource(R.string.app_subtitle),
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Update Alert Banner
        updateAvailableVersion?.let { version ->
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x3D00E5FF)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.new_update_title),
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E5FF),
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.new_update_desc, version),
                                fontSize = 12.sp,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { openPlayStore(context) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(stringResource(R.string.update), color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Permission Banner
        if (!hasOverlayPermission) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x3DF25C5C)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.permission_required_title),
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF8B8B),
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.permission_required_desc),
                            fontSize = 13.sp,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF25C5C))
                        ) {
                            Text(stringResource(R.string.grant_permission), color = Color.White)
                        }
                    }
                }
            }
        }

        // Animated Power Toggle
        item {
            val scale by animateFloatAsState(
                targetValue = if (isServiceRunning) 1.05f else 1.0f,
                animationSpec = tween(300),
                label = "power_scale"
            )
            val glowColor by animateColorAsState(
                targetValue = if (isServiceRunning) Color(0xFFD946EF) else Color(0xFF8B5CF6),
                animationSpec = tween(500),
                label = "power_glow"
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(160.dp)
                    .shadow(
                        elevation = 20.dp,
                        shape = CircleShape,
                        ambientColor = glowColor,
                        spotColor = glowColor
                    )
                    .background(
                        Brush.radialGradient(
                            colors = listOf(glowColor.copy(alpha = 0.2f), Color.Transparent)
                        ),
                        CircleShape
                    )
            ) {
                IconButton(
                    onClick = {
                        hasOverlayPermission = Settings.canDrawOverlays(context)
                        if (!hasOverlayPermission) {
                            android.widget.Toast.makeText(
                                context,
                                context.getString(R.string.permission_required_title),
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            return@IconButton
                        }

                        val intent = Intent(context, OverlayService::class.java)
                        if (isServiceRunning) {
                            intent.action = OverlayService.ACTION_STOP
                            context.startService(intent)
                        } else {
                            intent.action = OverlayService.ACTION_START
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                        }
                    },
                    modifier = Modifier
                        .size(120.dp)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF1E1F29), Color(0xFF13141B))
                            ),
                            CircleShape
                        )
                        .clip(CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Toggle Screen Dimmer",
                        tint = glowColor,
                        modifier = Modifier.size(54.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = if (isServiceRunning) stringResource(R.string.aether_screen_active) else stringResource(R.string.aether_screen_off),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (isServiceRunning) Color(0xFFD946EF) else Color.Gray,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(20.dp))

            // Primary flow: open a media app, then auto-dim once playback starts.
            Button(
                onClick = {
                    hasOverlayPermission = Settings.canDrawOverlays(context)
                    if (!hasOverlayPermission) {
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.permission_required_title),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        showAppPicker = true
                    }
                },
                enabled = !isServiceRunning,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.dim_over_app_button), fontWeight = FontWeight.Bold)
            }
            Text(
                text = stringResource(R.string.dim_over_app_desc),
                fontSize = 11.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp)
            )
            Spacer(modifier = Modifier.height(28.dp))
        }

        // DIM CONTROL CARD
        item {
            CardSection(title = stringResource(R.string.dim_blackout_controls_title), icon = Icons.Default.Settings) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.dim_display_only), color = Color.White)
                        Switch(
                            checked = !settings.isBlackoutMode,
                            onCheckedChange = { viewModel.updateIsBlackoutMode(scope, !it) }
                        )
                    }
                    Text(
                        text = stringResource(R.string.dim_display_only_desc),
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    AnimatedVisibility(visible = !settings.isBlackoutMode) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.dim_opacity_level), color = Color.LightGray)
                                Text("${(settings.dimLevel * 100).toInt()}%", fontWeight = FontWeight.Bold, color = Color(0xFF8B5CF6))
                            }
                            Slider(
                                value = settings.dimLevel,
                                onValueChange = { viewModel.updateDimLevel(scope, it) },
                                valueRange = 0.1f..0.95f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF8B5CF6),
                                    activeTrackColor = Color(0xFF8B5CF6)
                                )
                            )
                        }
                    }

                    HorizontalDivider(color = Color(0x1AFFFFFF), modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.touch_block_title), color = Color.White)
                        Switch(
                            checked = settings.lockTouch,
                            onCheckedChange = { viewModel.updateLockTouch(scope, it) }
                        )
                    }
                    Text(
                        text = stringResource(R.string.touch_block_desc),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        // SLEEP TIMER PRESETS CARD
        item {
            CardSection(title = stringResource(R.string.sleep_timer_title), icon = Icons.Default.PlayArrow) {
                Column {
                    Text(
                        text = stringResource(R.string.sleep_timer_desc),
                        fontSize = 12.sp,
                        color = Color.LightGray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val presets = listOf(0, 15, 30, 45, 60)
                        presets.forEach { minutes ->
                            val isSelected = settings.sleepTimerMinutes == minutes
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Color(0xFF8B5CF6) else Color(0xFF13141B))
                                    .clickable { viewModel.updateSleepTimer(scope, minutes) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (minutes == 0) stringResource(R.string.preset_off) else stringResource(R.string.preset_minutes, minutes),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (isSelected) Color.White else Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }

        // SENSOR CONTROLS CARD
        item {
            CardSection(title = stringResource(R.string.intelligent_triggers_title), icon = Icons.Default.Build) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.pocket_mode_title), color = Color.White)
                        Switch(
                            checked = settings.pocketModeEnabled,
                            onCheckedChange = { viewModel.updatePocketMode(scope, it) }
                        )
                    }
                    Text(
                        text = stringResource(R.string.pocket_mode_desc),
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    HorizontalDivider(color = Color(0x1AFFFFFF), modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.shake_wake_title), color = Color.White)
                        Switch(
                            checked = settings.shakeToWakeEnabled,
                            onCheckedChange = { viewModel.updateShakeToWake(scope, it) }
                        )
                    }
                    Text(
                        text = stringResource(R.string.shake_wake_desc),
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    HorizontalDivider(color = Color(0x1AFFFFFF), modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.double_tap_wake_title), color = Color.White)
                        Switch(
                            checked = settings.doubleTapToWakeEnabled,
                            onCheckedChange = { viewModel.updateDoubleTapToWake(scope, it) }
                        )
                    }
                }
            }
        }

        // SUPPORT & RATING CARD
        item {
            CardSection(title = stringResource(R.string.support_feedback_title), icon = Icons.Default.Star) {
                Column {
                    Text(
                        text = stringResource(R.string.support_feedback_desc),
                        fontSize = 12.sp,
                        color = Color.LightGray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Button(
                        onClick = { triggerInAppReview(context) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
                    ) {
                        Icon(imageVector = Icons.Default.Star, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.rate_aetherscreen), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // PER APP RULES
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.target_media_apps),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                textAlign = TextAlign.Start
            )
        }

        if (installedApps.isEmpty()) {
            item {
                CircularProgressIndicator(color = Color(0xFF8B5CF6))
            }
        } else {
            items(installedApps) { app ->
                val isTargeted = settings.targetApps.contains(app.packageName)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1F29)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                            Text(app.packageName, fontSize = 11.sp, color = Color.Gray)
                        }
                        Checkbox(
                            checked = isTargeted,
                            onCheckedChange = { viewModel.toggleTargetApp(scope, app.packageName, settings.targetApps) }
                        )
                    }
                }
            }
        }

        // AdMob Banner Ad
        item {
            Spacer(modifier = Modifier.height(16.dp))
            AdMobBanner()
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun CardSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .shadow(4.dp, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16171E)), // Glassmorphic card back
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = title, tint = Color(0xFF8B5CF6))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun AdMobBanner(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(Color(0xFF16171E)),
        factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = "ca-app-pub-3940256099942544/6300978111" // Test Banner ID
                try {
                    loadAd(AdRequest.Builder().build())
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    )
}

@Composable
fun AppPickerDialog(
    apps: List<AppInfoItem>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel), color = Color.Gray, fontWeight = FontWeight.Bold)
            }
        },
        title = {
            Text(
                text = stringResource(R.string.app_picker_title),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 18.sp
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.app_picker_desc),
                    fontSize = 12.sp,
                    color = Color.LightGray,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                if (apps.isEmpty()) {
                    Text(
                        text = stringResource(R.string.app_picker_empty),
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                        items(apps) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onSelect(app.packageName) }
                                    .padding(vertical = 12.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color(0xFF8B5CF6),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(app.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text(app.packageName, color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        containerColor = Color(0xFF1E1F29),
        shape = RoundedCornerShape(16.dp)
    )
}

private fun launchAppAndArm(context: Context, packageName: String) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (launchIntent == null) {
        android.widget.Toast.makeText(
            context,
            context.getString(R.string.app_launch_failed),
            android.widget.Toast.LENGTH_SHORT
        ).show()
        return
    }

    // Arm the overlay first (allowed: the activity is still foreground), then open the
    // media app. The overlay engages automatically once playback is detected.
    val armIntent = Intent(context, OverlayService::class.java).apply { action = OverlayService.ACTION_ARM }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(armIntent)
    } else {
        context.startService(armIntent)
    }

    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(launchIntent)
}

fun triggerInAppReview(context: Context) {
    try {
        val manager = ReviewManagerFactory.create(context)
        val request = manager.requestReviewFlow()
        request.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val reviewInfo = task.result
                if (context is android.app.Activity) {
                    manager.launchReviewFlow(context, reviewInfo)
                }
            } else {
                openPlayStore(context)
            }
        }
    } catch (e: Exception) {
        openPlayStore(context)
    }
}

private fun openPlayStore(context: Context) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

fun checkForUpdates(context: Context, onUpdateRequired: (String) -> Unit) {
    try {
        val remoteConfig = Firebase.remoteConfig
        val configSettings = remoteConfigSettings {
            minimumFetchIntervalInSeconds = 3600
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(mapOf("latest_version" to "1.0.0"))
        remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val latestVersion = remoteConfig.getString("latest_version")
                val currentVersion = try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
                } catch (e: Exception) {
                    "1.0.0"
                }
                if (latestVersion > currentVersion) {
                    onUpdateRequired(latestVersion)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
fun OnboardingScreen(
    settings: AppSettings,
    viewModel: AetherViewModel,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentSlide by remember { mutableStateOf(0) }
    val totalSlides = 3

    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1016))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top branding
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = Color(0xFF8B5CF6),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                color = Color.White
            )
        }

        // Slide Content Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            when (currentSlide) {
                0 -> WelcomeSlide()
                1 -> GesturesSlide(settings, viewModel, scope)
                2 -> PermissionSlide(context, hasOverlayPermission)
            }
        }

        // Navigation Footer
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Dot Indicators
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                repeat(totalSlides) { index ->
                    val isSelected = index == currentSlide
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (isSelected) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) Color(0xFF8B5CF6) else Color.Gray.copy(alpha = 0.5f))
                    )
                }
            }

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button
                if (currentSlide > 0) {
                    TextButton(onClick = { currentSlide-- }) {
                        Text(stringResource(R.string.btn_back), color = Color.Gray, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp)) // Placeholder to balance
                }

                // Next / Start Button
                if (currentSlide < totalSlides - 1) {
                    Button(
                        onClick = { currentSlide++ },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
                    ) {
                        Text(stringResource(R.string.btn_next), color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = onFinished,
                        enabled = hasOverlayPermission,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFD946EF),
                            disabledContainerColor = Color(0xFF1E1F29)
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.btn_get_started),
                            color = if (hasOverlayPermission) Color.White else Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WelcomeSlide() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Glowing Icon Box
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(120.dp)
                .shadow(16.dp, CircleShape, ambientColor = Color(0xFF8B5CF6), spotColor = Color(0xFF8B5CF6))
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF8B5CF6).copy(alpha = 0.2f), Color.Transparent)
                    ),
                    CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color(0xFF8B5CF6),
                modifier = Modifier.size(64.dp)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.onboarding_welcome_desc),
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
fun GesturesSlide(
    settings: AppSettings,
    viewModel: AetherViewModel,
    scope: kotlinx.coroutines.CoroutineScope
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.onboarding_gestures_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.onboarding_gestures_desc),
            fontSize = 13.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF16171E)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Double tap
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF8B5CF6))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.onboarding_double_tap_title), fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                            Text(stringResource(R.string.onboarding_double_tap_desc), color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                    Switch(
                        checked = settings.doubleTapToWakeEnabled,
                        onCheckedChange = { viewModel.updateDoubleTapToWake(scope, it) }
                    )
                }

                HorizontalDivider(color = Color(0x1AFFFFFF), modifier = Modifier.padding(vertical = 12.dp))

                // Shake to Wake
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Build, contentDescription = null, tint = Color(0xFF8B5CF6))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.onboarding_shake_title), fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                            Text(stringResource(R.string.onboarding_shake_desc), color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                    Switch(
                        checked = settings.shakeToWakeEnabled,
                        onCheckedChange = { viewModel.updateShakeToWake(scope, it) }
                    )
                }

                HorizontalDivider(color = Color(0x1AFFFFFF), modifier = Modifier.padding(vertical = 12.dp))

                // Pocket Mode
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = Color(0xFF8B5CF6))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(stringResource(R.string.onboarding_pocket_title), fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                            Text(stringResource(R.string.onboarding_pocket_desc), color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                    Switch(
                        checked = settings.pocketModeEnabled,
                        onCheckedChange = { viewModel.updatePocketMode(scope, it) }
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionSlide(
    context: Context,
    hasOverlayPermission: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.onboarding_permission_title),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.onboarding_permission_desc),
            fontSize = 13.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (hasOverlayPermission) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(80.dp)
                    .background(Color(0xFF1E3A1E), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Granted",
                    tint = Color.Green,
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.onboarding_permission_granted),
                color = Color.Green,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        } else {
            Button(
                onClick = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF25C5C))
            ) {
                Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.onboarding_grant_permission), color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Xiaomi / Custom ROM Guide Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0x1F8B5CF6)),
            border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.3f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = Color(0xFF8B5CF6),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = stringResource(R.string.onboarding_xiaomi_title),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.onboarding_xiaomi_desc),
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

