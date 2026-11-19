package com.ayang.glacier.platform.auto

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.ayang.glacier.GlacierApplication
import kotlinx.coroutines.runBlocking

/** 单应用的延迟冻结：`Freeze after…` 走这条路径。 */
class DeferredFreezeWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val packageName = inputData.getString(KEY_PACKAGE) ?: return Result.failure()
        val frozen = inputData.getBoolean(KEY_FROZEN, true)
        val app = applicationContext as? GlacierApplication ?: return Result.failure()
        val ok = runBlocking { app.repository.setFrozen(packageName, frozen) }
        if (ok) app.syncAutoFreezeService()
        return if (ok) Result.success() else Result.failure()
    }

    companion object {
        const val KEY_PACKAGE = "package"
        const val KEY_FROZEN = "frozen"
    }
}
