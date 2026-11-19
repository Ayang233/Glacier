package com.ayang.glacier.platform.auto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ayang.glacier.GlacierApplication
import com.ayang.glacier.domain.BackendState
import com.ayang.glacier.domain.FreezeAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 用户在系统界面手动解挂应用时，同步解除后台限制。
 *
 * 如果不处理，应用会处于"已解挂但被禁止后台运行"的半冻结状态。
 */
class UnsuspendReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_PACKAGE_UNSUSPENDED_MANUALLY) return
        val packageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME) ?: return
        val app = context.applicationContext as? GlacierApplication ?: return

        val backend = app.freeze.shizuku
        if (backend.state() == BackendState.Ready) {
            // 复用挂起时使用的 appops 通道，把后台限制恢复为允许。
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                runCatching { backend.setFrozen(packageName, FreezeAction.Suspend, false) }
            }
        }
        app.syncAutoFreezeService()
    }

    companion object {
        private const val ACTION_PACKAGE_UNSUSPENDED_MANUALLY =
            "android.intent.action.PACKAGE_UNSUSPENDED_MANUALLY"
    }
}
