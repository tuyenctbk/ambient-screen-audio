package com.aetherscreen.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import com.aetherscreen.wear.R
import androidx.compose.ui.res.stringResource
import com.aetherscreen.wear.service.WearSyncService
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearApp()
        }
    }
}

@Composable
fun WearApp() {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val deviceActive by WearSyncService.isDeviceActiveFlow.collectAsState()
    val activeDeviceName by WearSyncService.activeDeviceNameFlow.collectAsState()

    LaunchedEffect(Unit) {
        // Query status from companion nodes on launch
        sendMessage(context, coroutineScope, "/query_status", "")
    }

    val listState = rememberScalingLazyListState()

    MaterialTheme {
        Scaffold(
            timeText = { TimeText() },
            vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
            positionIndicator = {
                PositionIndicator(scalingLazyListState = listState)
            }
        ) {
            ScalingLazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                state = listState,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.app_name),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF8B5CF6),
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }

                item {
                    val statusText = if (deviceActive) stringResource(R.string.device_on, activeDeviceName) else stringResource(R.string.devices_off)
                    val statusColor = if (deviceActive) Color(0xFFD946EF) else Color.Gray
                    Text(
                        text = statusText,
                        fontSize = 11.sp,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                item {
                    Chip(
                        onClick = {
                            sendMessage(context, coroutineScope, "/toggle_phone", "")
                        },
                        label = { Text(stringResource(R.string.toggle_phone), fontSize = 11.sp) },
                        colors = ChipDefaults.primaryChipColors(
                            backgroundColor = Color(0xFF1E1F29)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Chip(
                        onClick = {
                            sendMessage(context, coroutineScope, "/toggle_tv", "")
                        },
                        label = { Text(stringResource(R.string.toggle_tv), fontSize = 11.sp) },
                        colors = ChipDefaults.primaryChipColors(
                            backgroundColor = Color(0xFF1E1F29)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Chip(
                        onClick = {
                            sendMessage(context, coroutineScope, "/add_time", "10")
                        },
                        label = { Text(stringResource(R.string.add_minutes), fontSize = 11.sp) },
                        colors = ChipDefaults.secondaryChipColors(
                            backgroundColor = Color(0xFF8B5CF6)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private fun sendMessage(
    context: android.content.Context,
    scope: CoroutineScope,
    path: String,
    data: String
) {
    try {
        val nodeClient = Wearable.getNodeClient(context)
        val messageClient = Wearable.getMessageClient(context)
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            android.util.Log.d("AetherScreenWear", "Connected nodes count: ${nodes.size}")
            for (node in nodes) {
                android.util.Log.d("AetherScreenWear", "Sending message $path to node: ${node.id} (${node.displayName})")
                messageClient.sendMessage(node.id, path, data.toByteArray())
                    .addOnSuccessListener {
                        android.util.Log.d("AetherScreenWear", "Message $path sent successfully to ${node.displayName}")
                    }
                    .addOnFailureListener {
                        android.util.Log.e("AetherScreenWear", "Failed to send message $path to ${node.displayName}: ${it.message}")
                    }
            }
        }.addOnFailureListener {
            android.util.Log.e("AetherScreenWear", "Failed to fetch connected nodes: ${it.message}")
        }
    } catch (e: Exception) {
        android.util.Log.e("AetherScreenWear", "Error in sendMessage: ${e.message}")
        e.printStackTrace()
    }
}
