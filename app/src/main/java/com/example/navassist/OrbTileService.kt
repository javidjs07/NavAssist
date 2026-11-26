package com.example.navassist

import android.content.Intent
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick Settings tile: starts the orb, or hides / shows it (handy before taking a screenshot). */
class OrbTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    private fun refresh() {
        val t = qsTile ?: return
        val on = OverlayService.running && !OverlayService.hiddenFlag
        t.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.label = "NavAssist"
        t.updateTile()
    }

    @Suppress("DEPRECATION")
    override fun onClick() {
        super.onClick()
        if (!Settings.canDrawOverlays(this)) {
            startActivityAndCollapse(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val i = Intent(this, OverlayService::class.java)
        if (OverlayService.running) i.action = OverlayService.ACTION_TOGGLE
        try {
            startForegroundService(i)
        } catch (e: Exception) {
            startActivityAndCollapse(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        refresh()
    }
}
