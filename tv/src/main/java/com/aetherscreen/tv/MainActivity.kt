package com.aetherscreen.tv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.aetherscreen.tv.R
import androidx.compose.ui.res.stringResource
import com.aetherscreen.tv.service.TvOverlayService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class TvAppInfoItem(val name: String, val packageName: String)

// --- VIEWMODEL ---
class TvAetherViewModel(context: Context) : ViewModel() {
    private val preferencesManager = PreferencesManager(context.applicationContext)
    val settingsFlow = preferencesManager.settingsFlow

    fun updateSleepTimer(scope: kotlinx.coroutines.CoroutineScope, minutes: Int) {
        scope.launch { preferencesManager.updateSleepTimerMinutes(minutes) }
    }

    fun updateBedsideClock(scope: kotlinx.coroutines.CoroutineScope, enabled: Boolean) {
        scope.launch { preferencesManager.updateBedsideClockEnabled(enabled) }
    }
}

// --- ACTIVITY ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TvAetherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF090A0F) // Ultra-dark backdrop for TV comfort
                ) {
                    TvMainScreen()
                }
            }
        }
    }
}

@Composable
fun TvAetherTheme(content: @Composable () -> Unit) {
    val darkColorScheme = darkColorScheme(
        primary = Color(0xFF7C4DFF),     // Bright TV Violet
        secondary = Color(0xFF00E5FF),   // TV Cyan highlight
        background = Color(0xFF090A0F),
        surface = Color(0xFF14151F),
        onPrimary = Color.White
    )
    MaterialTheme(
        colorScheme = darkColorScheme,
        content = content
    )
}

fun launchOverlaySettings(context: Context) {
    try {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            context.startActivity(intent)
        } catch (e2: Exception) {
            val intent = Intent(Settings.ACTION_SETTINGS)
            context.startActivity(intent)
        }
    }
}

@Composable
fun TvMainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    val viewModel: TvAetherViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return TvAetherViewModel(context) as T
        }
    })

    val settingsState = viewModel.settingsFlow.collectAsState(initial = AppSettings())
    val settings = settingsState.value

    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val isServiceRunning by TvOverlayService.isRunningFlow.collectAsState()
    var showPermissionGuide by remember { mutableStateOf(false) }
    var installedApps by remember { mutableStateOf<List<TvAppInfoItem>>(emptyList()) }
    var showAppPicker by remember { mutableStateOf(false) }

    // Load TV-launchable apps so the user can pick what to dim over.
    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            val pm = context.packageManager
            val leanbackIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            val resolved = pm.queryIntentActivities(leanbackIntent, 0)
            installedApps = resolved
                .mapNotNull { info ->
                    val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == context.packageName) return@mapNotNull null
                    TvAppInfoItem(info.loadLabel(pm).toString(), pkg)
                }
                .distinctBy { it.packageName }
                .sortedBy { it.name }
        }
    }

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

    if (showPermissionGuide) {
        AlertDialog(
            onDismissRequest = { showPermissionGuide = false },
            confirmButton = {
                TvFocusButton(
                    text = stringResource(R.string.btn_open_settings),
                    isSelected = true,
                    onClick = {
                        launchOverlaySettings(context)
                        showPermissionGuide = false
                    },
                    modifier = Modifier.width(160.dp)
                )
            },
            dismissButton = {
                TvFocusButton(
                    text = stringResource(R.string.btn_cancel),
                    isSelected = false,
                    onClick = { showPermissionGuide = false },
                    modifier = Modifier.width(120.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.permission_guide_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.permission_guide_desc),
                        fontSize = 13.sp,
                        color = Color.LightGray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text(
                        text = stringResource(R.string.instructions_header),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E5FF),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    Text(
                        text = stringResource(R.string.instructions_body),
                        fontSize = 12.sp,
                        color = Color.Gray,
                        lineHeight = 16.sp
                    )
                }
            },
            containerColor = Color(0xFF14151F),
            titleContentColor = Color.White,
            textContentColor = Color.LightGray,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showAppPicker) {
        TvAppPickerDialog(
            apps = installedApps,
            onDismiss = { showAppPicker = false },
            onSelect = { packageName ->
                showAppPicker = false
                launchTvAppAndArm(context, packageName)
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 6.sp,
                style = androidx.compose.ui.text.TextStyle(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFF7C4DFF), Color(0xFF00E5FF))
                    )
                )
            )
            Text(
                text = stringResource(R.string.app_subtitle),
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Center Content Area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Card: Main Trigger / Action
            Box(modifier = Modifier.weight(1.2f)) {
                TvFocusCard(
                    onClick = {
                        hasOverlayPermission = Settings.canDrawOverlays(context)
                        if (!hasOverlayPermission) {
                            showPermissionGuide = true
                        } else {
                            val intent = Intent(context, TvOverlayService::class.java)
                            if (isServiceRunning) {
                                intent.action = TvOverlayService.ACTION_STOP
                                context.startService(intent)
                            } else {
                                intent.action = TvOverlayService.ACTION_START
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(intent)
                                } else {
                                    context.startService(intent)
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isServiceRunning) stringResource(R.string.stop_blackout) else stringResource(R.string.start_blackout),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isServiceRunning) Color(0xFFFF5252) else Color.White
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isServiceRunning) stringResource(R.string.active_desc) else stringResource(R.string.inactive_desc),
                            fontSize = 12.sp,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center
                        )
                        if (!hasOverlayPermission) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = stringResource(R.string.permission_required),
                                color = Color(0xFFFFD54F),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Right Column: Settings and Presets
            Column(
                modifier = Modifier
                    .weight(1.8f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Sleep Timer Card
                TvCardContainer(title = stringResource(R.string.sleep_timer)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val timerPresets = listOf(0, 15, 30, 60, 120)
                        timerPresets.forEach { minutes ->
                            val isSelected = settings.sleepTimerMinutes == minutes
                            TvFocusButton(
                                text = if (minutes == 0) stringResource(R.string.preset_off) else stringResource(R.string.preset_minutes, minutes),
                                isSelected = isSelected,
                                onClick = { viewModel.updateSleepTimer(scope, minutes) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Dim over an app card: launch a media app, auto-dim on playback
                TvCardContainer(title = stringResource(R.string.dim_over_app_title)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.dim_over_app_desc),
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        TvFocusButton(
                            text = stringResource(R.string.dim_over_app_button),
                            isSelected = true,
                            onClick = {
                                hasOverlayPermission = Settings.canDrawOverlays(context)
                                if (!hasOverlayPermission) {
                                    showPermissionGuide = true
                                } else {
                                    showAppPicker = true
                                }
                            }
                        )
                    }
                }

                // Ambient Clock screensaver card
                TvCardContainer(title = stringResource(R.string.bedside_screensaver)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.show_clock), color = Color.White, fontSize = 14.sp)
                        TvFocusButton(
                            text = if (settings.bedsideClockEnabled) stringResource(R.string.enabled) else stringResource(R.string.disabled),
                            isSelected = settings.bedsideClockEnabled,
                            onClick = { viewModel.updateBedsideClock(scope, !settings.bedsideClockEnabled) }
                        )
                    }
                }
            }
        }

        // Bottom Footer
        Text(
            text = stringResource(R.string.tip),
            fontSize = 12.sp,
            color = Color.DarkGray
        )
    }
}

// --- D-PAD / TV INTERACTION HELPERS ---

@Composable
fun TvCardContainer(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF14151F)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun TvFocusCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) Color(0xFF00E5FF) else Color(0x1AFFFFFF)
    )
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.03f else 1.0f
    )

    Box(
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E1F29))
            .border(2.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onClick() }
    ) {
        content()
    }
}

@Composable
fun TvFocusButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val baseColor = if (isSelected) Color(0xFF7C4DFF) else Color(0xFF090A0F)
    val containerColor by animateColorAsState(
        targetValue = if (isFocused) Color(0xFF00E5FF) else baseColor
    )
    val textColor = if (isFocused) Color.Black else Color.White

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .border(1.dp, if (isFocused) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}

@Composable
fun TvAppPickerDialog(
    apps: List<TvAppInfoItem>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TvFocusButton(
                text = stringResource(R.string.btn_cancel),
                isSelected = false,
                onClick = onDismiss,
                modifier = Modifier.width(120.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.app_picker_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.app_picker_desc),
                    fontSize = 13.sp,
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
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(apps) { app ->
                            TvFocusButton(
                                text = app.name,
                                isSelected = false,
                                onClick = { onSelect(app.packageName) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        },
        containerColor = Color(0xFF14151F),
        titleContentColor = Color.White,
        textContentColor = Color.LightGray,
        shape = RoundedCornerShape(16.dp)
    )
}

private fun launchTvAppAndArm(context: Context, packageName: String) {
    val pm = context.packageManager
    val launchIntent = pm.getLeanbackLaunchIntentForPackage(packageName)
        ?: pm.getLaunchIntentForPackage(packageName)
    if (launchIntent == null) {
        android.widget.Toast.makeText(
            context,
            context.getString(R.string.app_launch_failed),
            android.widget.Toast.LENGTH_SHORT
        ).show()
        return
    }

    // Arm the overlay first (activity is still foreground), then open the media app.
    // The overlay engages automatically once playback is detected.
    val armIntent = Intent(context, TvOverlayService::class.java).apply { action = TvOverlayService.ACTION_ARM }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(armIntent)
    } else {
        context.startService(armIntent)
    }

    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(launchIntent)
}
