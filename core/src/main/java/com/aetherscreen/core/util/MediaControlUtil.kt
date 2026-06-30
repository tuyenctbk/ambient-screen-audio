package com.aetherscreen.core.util

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent

object MediaControlUtil {
    /**
     * Dispatches system-level KEYCODE_MEDIA_PAUSE event to pause background media playback.
     */
    fun pausePlayback(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        
        val eventTime = SystemClock.uptimeMillis()
        
        val downEvent = KeyEvent(
            eventTime, eventTime,
            KeyEvent.ACTION_DOWN,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            0
        )
        audioManager.dispatchMediaKeyEvent(downEvent)
        
        val upEvent = KeyEvent(
            eventTime, eventTime,
            KeyEvent.ACTION_UP,
            KeyEvent.KEYCODE_MEDIA_PAUSE,
            0
        )
        audioManager.dispatchMediaKeyEvent(upEvent)
    }

    /**
     * Returns true when audio/media is actively playing on the device.
     *
     * Used by the "auto-engage on playback" flow so the overlay only blacks out the
     * screen once the user has actually started media in another app, instead of
     * covering an idle home screen.
     */
    fun isMediaPlaying(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        return audioManager.isMusicActive
    }
}
