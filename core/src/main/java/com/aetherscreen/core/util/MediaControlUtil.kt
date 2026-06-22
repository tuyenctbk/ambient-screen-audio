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
}
