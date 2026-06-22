package com.aetherscreen.wear.service

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class WearSyncService : WearableListenerService() {

    companion object {
        @Volatile
        var isDeviceActive = false

        @Volatile
        var activeDeviceName = "Phone"
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/status") {
            val statusStr = String(messageEvent.data)
            // Expecting "active:Phone", "inactive:Phone", "active:TV", etc.
            val parts = statusStr.split(":")
            if (parts.size >= 2) {
                isDeviceActive = parts[0] == "active"
                activeDeviceName = parts[1]
            }
        }
    }
}
