package com.aetherscreen.mobile.service

import android.content.Intent
import android.os.Build
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class MobileWearListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val intent = Intent(this, OverlayService::class.java)
        
        when (messageEvent.path) {
            "/toggle_phone" -> {
                if (OverlayService.isRunning) {
                    intent.action = OverlayService.ACTION_STOP
                    startService(intent)
                } else {
                    intent.action = OverlayService.ACTION_START
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                }
            }
            "/add_time" -> {
                intent.action = OverlayService.ACTION_ADD_10_MIN
                startService(intent)
            }
            "/query_status" -> {
                sendStatus(messageEvent.sourceNodeId, OverlayService.isRunning, "Phone")
            }
        }
    }

    private fun sendStatus(nodeId: String, running: Boolean, deviceName: String) {
        val statusStr = if (running) "active:$deviceName" else "inactive:$deviceName"
        com.google.android.gms.wearable.Wearable.getMessageClient(this)
            .sendMessage(nodeId, "/status", statusStr.toByteArray())
    }
}
