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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aetherscreen.core.model.AppSettings
import com.aetherscreen.core.preferences.PreferencesManager
import com.aetherscreen.tv.service.TvOverlayService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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

@Composable
fun TvMainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val viewModel: TvAetherViewModel = viewModel(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return TvAetherViewModel(context) as T
        }
    })

    val settingsState = viewModel.settingsFlow.collectAsState(initial = AppSettings())
    val settings = settingsState.value

    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isServiceRunning by remember { mutableStateOf(TvOverlayService.isRunning) }

    DisposableEffect(Unit) {
        isServiceRunning = TvOverlayService.isRunning
        onDispose {}
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
                text = "AETHER SCREEN TV",
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
                text = "Blackout display matrix & save OLED pixels while keeping audio active.",
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
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } else {
                            val intent = Intent(context, TvOverlayService::class.java)
                            if (isServiceRunning) {
                                intent.action = TvOverlayService.ACTION_STOP
                                context.startService(intent)
                                isServiceRunning = false
                            } else {
                                intent.action = TvOverlayService.ACTION_START
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    context.startForegroundService(intent)
                                } else {
                                    context.startService(intent)
                                }
                                isServiceRunning = true
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
                            text = if (isServiceRunning) "STOP BLACKOUT" else "START BLACKOUT",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isServiceRunning) Color(0xFFFF5252) else Color.White
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isServiceRunning) "Press any remote key to return." else "Dims screen. TV remote key dismisses.",
                            fontSize = 12.sp,
                            color = Color.LightGray,
                            textAlign = TextAlign.Center
                        )
                        if (!hasOverlayPermission) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Permission required: Click to grant.",
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
                TvCardContainer(title = "Sleep Auto-Pause Timer") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val timerPresets = listOf(0, 15, 30, 60, 120)
                        timerPresets.forEach { minutes ->
                            val isSelected = settings.sleepTimerMinutes == minutes
                            TvFocusButton(
                                text = if (minutes == 0) "Off" else "${minutes}m",
                                isSelected = isSelected,
                                onClick = { viewModel.updateSleepTimer(scope, minutes) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // Ambient Clock screensaver card
                TvCardContainer(title = "Bedside Screensaver Mode") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Show Dim Moving Clock", color = Color.White, fontSize = 14.sp)
                        TvFocusButton(
                            text = if (settings.bedsideClockEnabled) "Enabled" else "Disabled",
                            isSelected = settings.bedsideClockEnabled,
                            onClick = { viewModel.updateBedsideClock(scope, !settings.bedsideClockEnabled) }
                        )
                    }
                }
            }
        }

        // Bottom Footer
        Text(
            text = "Tip: Start audio on your favorite app, then open AetherScreen TV to dim the screen.",
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
            .focusable()
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
            .focusable()
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .border(1.dp, if (isFocused) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
    }
}
