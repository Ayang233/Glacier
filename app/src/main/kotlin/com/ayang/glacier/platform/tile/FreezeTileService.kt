package com.ayang.glacier.platform.tile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.ayang.glacier.GlacierApplication
import com.ayang.glacier.R
import com.ayang.glacier.app.GlacierApi
import com.ayang.glacier.domain.QuickAction

/**
 * 快捷设置磁贴。
 *
 * 磁贴动作由设置决定；`AutoFreezeAfterLock` 是唯一"就地切换"的动作，
 * 其余动作都转到 ApiActivity 执行，保证与外部 API 走同一条路径。
 */
class FreezeTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onTileAdded() {
        super.onTileAdded()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val app = applicationContext as? GlacierApplication ?: return
        val action = app.preferences.tileAction

        if (action == QuickAction.AutoFreezeAfterLock) {
            val enabled = !app.preferences.autoFreezeEnabled
            app.preferences.autoFreezeEnabled = enabled
            app.syncAutoFreezeService()
            updateTile()
            return
        }

        val target = when (action) {
            QuickAction.FreezeAll -> GlacierApi.ACTION_FREEZE_ALL
            QuickAction.UnfreezeAll -> GlacierApi.ACTION_UNFREEZE_ALL
            QuickAction.FreezeNonWhitelisted -> GlacierApi.ACTION_FREEZE_NON_WHITELISTED
            QuickAction.Lock -> GlacierApi.ACTION_LOCK
            QuickAction.LockFreeze -> GlacierApi.ACTION_LOCK_FREEZE
            else -> return
        }
        val intent = Intent(this, com.ayang.glacier.feature.api.ApiActivity::class.java)
            .setAction(target)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivityAndCollapse(
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ),
        )
    }

    private fun updateTile() {
        val app = applicationContext as? GlacierApplication ?: return
        val action = app.preferences.tileAction
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_frozen)
        tile.label = getString(R.string.tile_label)
        tile.state = when (action) {
            QuickAction.AutoFreezeAfterLock ->
                if (app.preferences.autoFreezeEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE

            else -> Tile.STATE_INACTIVE
        }
        tile.updateTile()
    }
}
