package com.aetherscreen.mobile.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AetherTileService : TileService() {

    private var listenJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        listenJob = CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            OverlayService.isRunningFlow.collect { running ->
                val tile = qsTile ?: return@collect
                tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                tile.updateTile()
            }
        }
    }

    override fun onStopListening() {
        listenJob?.cancel()
        listenJob = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val isServiceRunning = OverlayService.isRunning
        val intent = Intent(this, OverlayService::class.java)

        if (isServiceRunning) {
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

        // Instantly toggle visual state for snappy feedback
        val tile = qsTile ?: return
        tile.state = if (isServiceRunning) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.updateTile()
    }
}
