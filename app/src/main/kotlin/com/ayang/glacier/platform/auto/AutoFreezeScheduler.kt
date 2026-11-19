package com.ayang.glacier.platform.auto

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ayang.glacier.GlacierApplication
import java.util.concurrent.TimeUnit

/**
 * 自动冻结与延迟冻结的调度。
 *
 * 唯一任务名统一带 `glacier.` 前缀，避免与其他应用的 WorkManager 任务混淆，
 * 也便于升级后清理历史任务。
 */
object AutoFreezeScheduler {

    const val WORK_AUTO_FREEZE = "glacier.auto_freeze"
    const val WORK_DEFERRED_PREFIX = "glacier.deferred."

    /** 根据"是否还有可冻结的应用"决定前台服务的存在与否。 */
    fun syncService(context: Context) {
        val app = context.applicationContext as? GlacierApplication ?: return
        val preferences = app.preferences
        val hasWork = preferences.autoFreezeEnabled && app.repository.autoFreezeCandidates().isNotEmpty()
        val intent = Intent(app, AutoFreezeService::class.java)
        if (hasWork) {
            app.setAutoFreezeServiceComponentEnabled(true)
            runCatching { ContextCompat.startForegroundService(app, intent) }
        } else {
            runCatching { app.stopService(intent) }
            app.setAutoFreezeServiceComponentEnabled(false)
        }
    }

    fun scheduleAutoFreeze(context: Context, fromScreenOff: Boolean) {
        val app = context.applicationContext as GlacierApplication
        val preferences = app.preferences
        if (!preferences.autoFreezeEnabled) return

        val request = OneTimeWorkRequestBuilder<AutoFreezeWorker>()
            .setInputData(
                Data.Builder()
                    .putBoolean(AutoFreezeWorker.KEY_FROM_LOCK, fromScreenOff)
                    .build(),
            )
            .apply {
                if (fromScreenOff && preferences.autoFreezeDelayMinutes > 0) {
                    setInitialDelay(preferences.autoFreezeDelayMinutes, TimeUnit.MINUTES)
                }
            }
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_AUTO_FREEZE,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun scheduleDeferredFreeze(context: Context, packageName: String, frozen: Boolean, minutes: Long) {
        val request = OneTimeWorkRequestBuilder<DeferredFreezeWorker>()
            .setInputData(
                Data.Builder()
                    .putString(DeferredFreezeWorker.KEY_PACKAGE, packageName)
                    .putBoolean(DeferredFreezeWorker.KEY_FROZEN, frozen)
                    .build(),
            )
            .setInitialDelay(minutes, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_DEFERRED_PREFIX + packageName,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancelDeferredFreeze(context: Context, packageName: String) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_DEFERRED_PREFIX + packageName)
    }
}
