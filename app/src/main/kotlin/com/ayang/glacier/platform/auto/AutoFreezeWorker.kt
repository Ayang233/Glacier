package com.ayang.glacier.platform.auto

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.ayang.glacier.GlacierApplication
import com.ayang.glacier.platform.Platform
import com.ayang.glacier.system.FreezeOutcome
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

/**
 * 自动冻结任务。
 *
 * 输入 `fromLock=true` 表示由熄屏触发：如果此刻屏幕已经亮了，就直接放弃，
 * 否则会出现在用户正在用手机时把应用冻住的情况。
 */
class AutoFreezeWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val app = applicationContext as? GlacierApplication ?: return Result.failure()
        val preferences = app.preferences
        if (!preferences.autoFreezeEnabled) return Result.success()

        val fromLock = inputData.getBoolean(KEY_FROM_LOCK, true)
        if (fromLock && Platform.isInteractive(applicationContext)) return Result.success()
        if (preferences.skipWhileCharging && Platform.isCharging(applicationContext)) return Result.success()

        val notifying = if (preferences.skipNotifyingApp) AutoFreezeService.activePackages() else emptySet()
        val foreground = if (preferences.skipForegroundApp) {
            Platform.lastForegroundPackage(
                applicationContext,
                TimeUnit.MINUTES.toMillis(preferences.autoFreezeDelayMinutes + 1),
            )
        } else {
            null
        }

        val candidates = app.repository.autoFreezeCandidates().filter { packageName ->
            packageName != foreground && packageName !in notifying
        }
        if (candidates.isEmpty()) {
            app.syncAutoFreezeService()
            return Result.success()
        }

        val outcome = runBlocking { app.repository.setFrozenBatch(candidates, true) }
        return when (outcome) {
            is FreezeOutcome.Applied -> {
                app.syncAutoFreezeService()
                Result.success()
            }

            FreezeOutcome.Denied -> Result.retry()
        }
    }

    companion object {
        const val KEY_FROM_LOCK = "from_lock"
    }
}
