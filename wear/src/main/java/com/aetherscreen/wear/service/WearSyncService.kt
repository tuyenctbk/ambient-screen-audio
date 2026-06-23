package com.aetherscreen.wear.service

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WearSyncService : WearableListenerService() {

    companion object {
        private val _isPhoneActive = MutableStateFlow(false)
        private val _isTvActive = MutableStateFlow(false)

        private val _isDeviceActiveFlow = MutableStateFlow(false)
        val isDeviceActiveFlow: StateFlow<Boolean> = _isDeviceActiveFlow.asStateFlow()

        private val _activeDeviceNameFlow = MutableStateFlow("Phone")
        val activeDeviceNameFlow: StateFlow<String> = _activeDeviceNameFlow.asStateFlow()

        private fun updateCombinedState() {
            val phoneActive = _isPhoneActive.value
            val tvActive = _isTvActive.value
            _isDeviceActiveFlow.value = phoneActive || tvActive

            _activeDeviceNameFlow.value = when {
                phoneActive && tvActive -> "Phone & TV"
                phoneActive -> "Phone"
                tvActive -> "TV"
                else -> "Phone"
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/status") {
            val statusStr = String(messageEvent.data)
            // Expecting "active:Phone", "inactive:Phone", "active:TV", etc.
            val parts = statusStr.split(":")
            if (parts.size >= 2) {
                val isActive = parts[0] == "active"
                val deviceName = parts[1]
                if (deviceName.equals("Phone", ignoreCase = true)) {
                    _isPhoneActive.value = isActive
                } else if (deviceName.equals("TV", ignoreCase = true)) {
                    _isTvActive.value = isActive
                }
                updateCombinedState()
            }
        }
    }
}
