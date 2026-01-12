package com.example.screenannotation

import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

class AnnotationTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()

        // 检查悬浮窗权限
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.need_overlay_permission, Toast.LENGTH_LONG).show()
            // 打开应用让用户授权
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivityAndCollapse(intent)
            return
        }

        // 切换悬浮窗服务状态
        if (OverlayService.isRunning) {
            stopOverlayService()
        } else {
            startOverlayService()
        }

        updateTileState()
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        stopService(intent)
    }

    private fun updateTileState() {
        qsTile?.let { tile ->
            if (OverlayService.isRunning) {
                tile.state = Tile.STATE_ACTIVE
                tile.label = getString(R.string.tile_label_active)
            } else {
                tile.state = Tile.STATE_INACTIVE
                tile.label = getString(R.string.tile_label)
            }
            tile.updateTile()
        }
    }
}
