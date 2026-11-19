package com.ayang.glacier.platform.auto

import android.app.Notification
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.ayang.glacier.R
import com.ayang.glacier.app.GlacierApi
import com.ayang.glacier.feature.api.ApiActivity
import com.ayang.glacier.platform.Notifications
import com.ayang.glacier.ui.MainActivity

/**
 * 自动冻结的前台服务。
 *
 * 同时也是通知监听服务：只有用户授予通知使用权后，
 * "跳过有通知的应用"这个条件才真正生效；没有授权时该条件自动视为不满足。
 */
class AutoFreezeService : NotificationListenerService() {

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                AutoFreezeScheduler.scheduleAutoFreeze(context, fromScreenOff = true)
            } else if (intent.action == Intent.ACTION_SCREEN_ON) {
                // 用户回到前台，取消尚未执行的延迟冻结。
                AutoFreezeScheduler.scheduleAutoFreeze(context, fromScreenOff = false)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val content = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val freezeNow = PendingIntent.getActivity(
            this,
            1,
            Intent(this, ApiActivity::class.java).setAction(GlacierApi.ACTION_FREEZE_ALL),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, Notifications.CHANNEL_AUTO_FREEZE)
            .setContentTitle(getString(R.string.notification_auto_freeze_title))
            .setContentText(getString(R.string.notification_auto_freeze_text))
            .setSmallIcon(R.drawable.ic_tile_frozen)
            .setContentIntent(content)
            .setOngoing(true)
            .setSilent(true)
            .addAction(0, getString(R.string.action_freeze_all), freezeNow)
            .build()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
    }

    companion object {
        private const val NOTIFICATION_ID = 100

        @Volatile
        var instance: AutoFreezeService? = null
            private set

        /** 当前有活动通知的包名集合；未授权通知使用权时返回空集。 */
        fun activePackages(): Set<String> {
            val service = instance ?: return emptySet()
            val notifications: Array<StatusBarNotification> = runCatching {
                service.activeNotifications
            }.getOrNull() ?: return emptySet()
            return notifications.mapNotNull { it.packageName }.toSet()
        }
    }
}
