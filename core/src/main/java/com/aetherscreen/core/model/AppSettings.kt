package com.aetherscreen.core.model

data class AppSettings(
    val dimLevel: Float = 0.70f,            // 0.0f (fully transparent) to 0.95f (very dark)
    val isBlackoutMode: Boolean = true,     // Pure black (#000000) display overlay
    val lockTouch: Boolean = true,          // Disable touchscreen touches when blackout is active
    val sleepTimerMinutes: Int = 0,         // Sleep timer (0 = disabled)
    val pocketModeEnabled: Boolean = false,  // Proximity sensor automatic activation
    val shakeToWakeEnabled: Boolean = true, // Shake device to dismiss overlay
    val doubleTapToWakeEnabled: Boolean = true, // Double tap overlay to dismiss
    val bedsideClockEnabled: Boolean = false, // Floating dim clock on blackout screen
    val targetApps: Set<String> = emptySet(), // List of package names that trigger custom rules
    val onboardingCompleted: Boolean = false
)
