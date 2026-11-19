package com.ayang.glacier.system

import android.app.Activity
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.ayang.glacier.domain.Backend
import com.ayang.glacier.domain.BackendState
import com.ayang.glacier.domain.FreezeAction
import com.ayang.glacier.platform.PackageTools
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Island（炼妖壶）后端。
 *
 * Island 通过有序广播对外提供冻结/挂起能力，结果由 `resultCode` 返回。
 * 广播可能永远不回来，所以必须有硬超时。
 */
class IslandBackend(private val context: Context) : FreezeBackend {

    override val kind: Backend = Backend.Island

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    private var ownerApp: String? = null

    @Volatile
    private var ownerChecked = false

    private fun resolveOwner(): String? {
        if (ownerChecked) return ownerApp
        val policy = context.getSystemService<DevicePolicyManager>()
        val candidates = arrayOf("com.oasisfeng.island", "com.oasisfeng.island.fdroid")
        ownerApp = candidates.firstOrNull { pkg ->
            runCatching { policy?.isProfileOwnerApp(pkg) == true }.getOrDefault(false)
        }
        ownerChecked = true
        return ownerApp
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    override fun state(): BackendState = when {
        resolveOwner() == null -> BackendState.Unavailable
        !granted(PERMISSION_FREEZE) && !granted(PERMISSION_SUSPEND) -> BackendState.NeedsPermission
        else -> BackendState.Ready
    }

    override fun isFrozen(packageName: String, action: FreezeAction): Boolean = when (action) {
        FreezeAction.Stop -> PackageTools.isStopped(context, packageName)
        FreezeAction.Disable -> PackageTools.isDisabled(context, packageName)
        FreezeAction.Hide -> PackageTools.isHidden(context, packageName)
        FreezeAction.Suspend -> PackageTools.isSuspended(context, packageName)
        FreezeAction.None -> false
    }

    override suspend fun setFrozen(
        packageName: String,
        action: FreezeAction,
        frozen: Boolean,
    ): Boolean {
        val owner = resolveOwner() ?: return false
        val broadcast = when (action) {
            FreezeAction.Hide -> if (frozen) ACTION_FREEZE else ACTION_UNFREEZE
            FreezeAction.Suspend -> if (frozen) ACTION_SUSPEND else ACTION_UNSUSPEND
            else -> return false
        }
        return sendAction(owner, packageName, broadcast)
    }

    private suspend fun sendAction(owner: String, packageName: String, action: String): Boolean {
        val intent = Intent(action).apply {
            data = Uri.fromParts("package", packageName, null)
            setPackage(owner)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            putExtra(
                EXTRA_CALLER,
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
        val delivered = withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(receiverContext: Context, receiverIntent: Intent) {
                        Log.i(TAG, "island result ${resultCode} ${resultData.orEmpty()}")
                        if (continuation.isActive) continuation.resume(resultCode == Activity.RESULT_OK)
                    }
                }
                runCatching {
                    context.sendOrderedBroadcast(
                        intent,
                        null,
                        receiver,
                        handler,
                        Activity.RESULT_OK,
                        null,
                        null,
                    )
                }.onFailure {
                    Log.w(TAG, "sendOrderedBroadcast failed", it)
                    if (continuation.isActive) continuation.resume(false)
                }
            }
        }
        return delivered ?: false
    }

    /** Island 冻结后启动 Intent 需要一点时间才可见，最多等 500ms。 */
    fun awaitLaunchIntent(packageName: String) {
        repeat(5) {
            if (PackageTools.launchIntent(context, packageName) != null) return
            Thread.sleep(100)
        }
    }

    override fun lockScreen(): Boolean = false

    companion object {
        const val PERMISSION_FREEZE = "com.oasisfeng.island.permission.FREEZE_PACKAGE"
        const val PERMISSION_SUSPEND = "com.oasisfeng.island.permission.SUSPEND_PACKAGE"

        private const val ACTION_FREEZE = "com.oasisfeng.island.action.FREEZE"
        private const val ACTION_UNFREEZE = "com.oasisfeng.island.action.UNFREEZE"
        private const val ACTION_SUSPEND = "com.oasisfeng.island.action.SUSPEND"
        private const val ACTION_UNSUSPEND = "com.oasisfeng.island.action.UNSUSPEND"
        private const val EXTRA_CALLER = "caller"
        private const val TIMEOUT_MS = 900L
        private const val TAG = "GlacierIsland"
    }
}
