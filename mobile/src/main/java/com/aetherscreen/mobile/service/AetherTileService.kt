package com.aetherscreen.mobile.service

import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class AetherTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
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

    private fun updateTileState() {
        val tile = qsTile ?: return
        tile.state = if (OverlayService.isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
