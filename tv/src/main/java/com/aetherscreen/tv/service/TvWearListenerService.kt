package com.aetherscreen.tv.service

import android.content.Intent
import android.os.Build
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class TvWearListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val intent = Intent(this, TvOverlayService::class.java)

        when (messageEvent.path) {
            "/toggle_tv" -> {
                if (TvOverlayService.isRunning) {
                    intent.action = TvOverlayService.ACTION_STOP
                    startService(intent)
                } else {
                    intent.action = TvOverlayService.ACTION_START
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                }
            }
            "/query_status" -> {
                sendStatus(messageEvent.sourceNodeId, TvOverlayService.isRunning, "TV")
            }
        }
    }

    private fun sendStatus(nodeId: String, running: Boolean, deviceName: String) {
        val statusStr = if (running) "active:$deviceName" else "inactive:$deviceName"
        com.google.android.gms.wearable.Wearable.getMessageClient(this)
            .sendMessage(nodeId, "/status", statusStr.toByteArray())
    }
}
