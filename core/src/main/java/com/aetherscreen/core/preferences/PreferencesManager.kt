package com.aetherscreen.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.aetherscreen.core.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aether_settings")

class PreferencesManager(private val context: Context) {

    companion object {
        private val KEY_DIM_LEVEL = floatPreferencesKey("dim_level")
        private val KEY_IS_BLACKOUT_MODE = booleanPreferencesKey("is_blackout_mode")
        private val KEY_LOCK_TOUCH = booleanPreferencesKey("lock_touch")
        private val KEY_SLEEP_TIMER_MINUTES = intPreferencesKey("sleep_timer_minutes")
        private val KEY_POCKET_MODE_ENABLED = booleanPreferencesKey("pocket_mode_enabled")
        private val KEY_SHAKE_TO_WAKE_ENABLED = booleanPreferencesKey("shake_to_wake_enabled")
        private val KEY_DOUBLE_TAP_TO_WAKE_ENABLED = booleanPreferencesKey("double_tap_to_wake_enabled")
        private val KEY_BEDSIDE_CLOCK_ENABLED = booleanPreferencesKey("bedside_clock_enabled")
        private val KEY_TARGET_APPS = stringSetPreferencesKey("target_apps")
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data
        .map { preferences ->
            AppSettings(
                dimLevel = preferences[KEY_DIM_LEVEL] ?: 0.70f,
                isBlackoutMode = preferences[KEY_IS_BLACKOUT_MODE] ?: true,
                lockTouch = preferences[KEY_LOCK_TOUCH] ?: true,
                sleepTimerMinutes = preferences[KEY_SLEEP_TIMER_MINUTES] ?: 0,
                pocketModeEnabled = preferences[KEY_POCKET_MODE_ENABLED] ?: false,
                shakeToWakeEnabled = preferences[KEY_SHAKE_TO_WAKE_ENABLED] ?: true,
                doubleTapToWakeEnabled = preferences[KEY_DOUBLE_TAP_TO_WAKE_ENABLED] ?: true,
                bedsideClockEnabled = preferences[KEY_BEDSIDE_CLOCK_ENABLED] ?: false,
                targetApps = preferences[KEY_TARGET_APPS] ?: emptySet(),
                onboardingCompleted = preferences[KEY_ONBOARDING_COMPLETED] ?: false
            )
        }

    suspend fun updateDimLevel(level: Float) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DIM_LEVEL] = level
        }
    }

    suspend fun updateIsBlackoutMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_IS_BLACKOUT_MODE] = enabled
        }
    }

    suspend fun updateLockTouch(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_LOCK_TOUCH] = enabled
        }
    }

    suspend fun updateSleepTimerMinutes(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SLEEP_TIMER_MINUTES] = minutes
        }
    }

    suspend fun updatePocketModeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_POCKET_MODE_ENABLED] = enabled
        }
    }

    suspend fun updateShakeToWakeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SHAKE_TO_WAKE_ENABLED] = enabled
        }
    }

    suspend fun updateDoubleTapToWakeEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DOUBLE_TAP_TO_WAKE_ENABLED] = enabled
        }
    }

    suspend fun updateBedsideClockEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BEDSIDE_CLOCK_ENABLED] = enabled
        }
    }

    suspend fun addTargetApp(packageName: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[KEY_TARGET_APPS] ?: emptySet()
            preferences[KEY_TARGET_APPS] = current + packageName
        }
    }

    suspend fun removeTargetApp(packageName: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[KEY_TARGET_APPS] ?: emptySet()
            preferences[KEY_TARGET_APPS] = current - packageName
        }
    }

    suspend fun updateOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_ONBOARDING_COMPLETED] = completed
        }
    }
}
