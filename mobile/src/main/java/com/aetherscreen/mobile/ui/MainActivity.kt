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
    var isServiceRunning by remember { mutableStateOf(OverlayService.isRunning) }
    var installedApps by remember { mutableStateOf<List<AppInfoItem>>(emptyList()) }
    var updateAvailableVersion by remember { mutableStateOf<String?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current

    // Check service state periodically or when returning
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
                isServiceRunning = OverlayService.isRunning
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
                text = "AETHER SCREEN",
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
                text = "Ambient Screen & Audio Preservation",
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
                                text = "New Update Available!",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E5FF),
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Version $version is now available. Update for new optimization features.",
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
                            Text("Update", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                            text = "Permission Required",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF8B8B),
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "AetherScreen needs permission to draw over other apps to dim the screen and prevent OLED burn-in.",
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
                            Text("Grant Permission", color = Color.White)
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
                        if (!hasOverlayPermission) return@IconButton

                        val intent = Intent(context, OverlayService::class.java)
                        if (isServiceRunning) {
                            intent.action = OverlayService.ACTION_STOP
                            context.startService(intent)
                            isServiceRunning = false
                        } else {
                            intent.action = OverlayService.ACTION_START
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                            isServiceRunning = true
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
                text = if (isServiceRunning) "AETHER SCREEN ACTIVE" else "AETHER SCREEN OFF",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = if (isServiceRunning) Color(0xFFD946EF) else Color.Gray,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(28.dp))
        }

        // DIM CONTROL CARD
        item {
            CardSection(title = "Dim & Blackout Controls", icon = Icons.Default.Settings) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Dim Display Only", color = Color.White)
                        Switch(
                            checked = !settings.isBlackoutMode,
                            onCheckedChange = { viewModel.updateIsBlackoutMode(scope, !it) }
                        )
                    }
                    Text(
                        text = "If enabled, you can still view the app underneath. If disabled, screen goes pure black.",
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
                                Text("Dim Opacity Level", color = Color.LightGray)
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

                    Divider(color = Color(0x1AFFFFFF), modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Touch Block / Screen Lock", color = Color.White)
                        Switch(
                            checked = settings.lockTouch,
                            onCheckedChange = { viewModel.updateLockTouch(scope, it) }
                        )
                    }
                    Text(
                        text = "Ignores accidental touches while active (pocket mode). Dismiss with double-tap gesture.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        // SLEEP TIMER PRESETS CARD
        item {
            CardSection(title = "Auto-Pause Sleep Timer", icon = Icons.Default.PlayArrow) {
                Column {
                    Text(
                        text = "Fades screen out and pauses background audio after time elapsed.",
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
                                    text = if (minutes == 0) "Off" else "${minutes}m",
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
            CardSection(title = "Intelligent Triggers", icon = Icons.Default.Build) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Auto Pocket/Face-down Mode", color = Color.White)
                        Switch(
                            checked = settings.pocketModeEnabled,
                            onCheckedChange = { viewModel.updatePocketMode(scope, it) }
                        )
                    }
                    Text(
                        text = "Automatically blacks out display matrix when proximity sensor is covered (face down on table or inside pocket).",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Divider(color = Color(0x1AFFFFFF), modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Shake to Wake/Dismiss", color = Color.White)
                        Switch(
                            checked = settings.shakeToWakeEnabled,
                            onCheckedChange = { viewModel.updateShakeToWake(scope, it) }
                        )
                    }
                    Text(
                        text = "Shake the phone vigorously to dismiss overlay.",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Divider(color = Color(0x1AFFFFFF), modifier = Modifier.padding(vertical = 12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Double-Tap Screen to Wake", color = Color.White)
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
            CardSection(title = "Support & Feedback", icon = Icons.Default.Star) {
                Column {
                    Text(
                        text = "Love using AetherScreen? Help us improve by leaving a rating or feedback!",
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
                        Text("Rate AetherScreen", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // PER APP RULES
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Target Media Applications",
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
                text = "AETHER SCREEN",
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
                        Text("BACK", color = Color.Gray, fontWeight = FontWeight.Bold)
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
                        Text("NEXT", color = Color.White, fontWeight = FontWeight.Bold)
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
                            text = "GET STARTED",
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
            text = "Welcome to AetherScreen",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Preserve audio and save battery on OLED screens. Fully blackout or dim your display while keeping YouTube, browsers, or music players running in the background.",
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
            text = "Screen Wake Gestures",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "When the screen is blacked out, dismiss the overlay and wake the screen using these interactive gestures:",
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
                            Text("Double-Tap to Wake", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                            Text("Tap twice anywhere to wake screen", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                    Switch(
                        checked = settings.doubleTapToWakeEnabled,
                        onCheckedChange = { viewModel.updateDoubleTapToWake(scope, it) }
                    )
                }

                Divider(color = Color(0x1AFFFFFF), modifier = Modifier.padding(vertical = 12.dp))

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
                            Text("Shake to Wake", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                            Text("Shake device vigorously to wake screen", color = Color.Gray, fontSize = 11.sp)
                        }
                    }
                    Switch(
                        checked = settings.shakeToWakeEnabled,
                        onCheckedChange = { viewModel.updateShakeToWake(scope, it) }
                    )
                }

                Divider(color = Color(0x1AFFFFFF), modifier = Modifier.padding(vertical = 12.dp))

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
                            Text("Auto Pocket Mode", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                            Text("Auto blackout using proximity sensor", color = Color.Gray, fontSize = 11.sp)
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
            text = "Overlay Permission",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "AetherScreen needs display overlay permissions to show the black layer over other active applications.",
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
                text = "Permission Granted!",
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
                Text("Grant Overlay Permission", color = Color.White, fontWeight = FontWeight.Bold)
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
                        text = "Xiaomi / Redmi / MIUI Users:",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Go to App Info -> Other Permissions -> enable 'Display pop-up windows while running in the background' if overlays do not show.",
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

