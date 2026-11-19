package com.ayang.glacier.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService

/** 通知渠道统一创建，渠道 ID 与名称都属于 Glacier 自己的命名空间。 */
object Notifications {

    const val CHANNEL_AUTO_FREEZE = "glacier.auto_freeze"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService<NotificationManager>() ?: return
        val channel = NotificationChannel(
            CHANNEL_AUTO_FREEZE,
            context.getString(com.ayang.glacier.R.string.notification_channel_auto_freeze),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(com.ayang.glacier.R.string.notification_auto_freeze_text)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }
}
