package com.ayang.glacier.system

import android.content.Context
import com.ayang.glacier.domain.Backend
import com.ayang.glacier.domain.BackendState
import com.ayang.glacier.domain.FreezeAction
import com.ayang.glacier.platform.PackageTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Root（su）后端。
 *
 * 所有写操作都通过 `su` 执行系统命令，命令语义与 Hail 已验证的 shell 路径一致。
 */
class RootBackend(private val context: Context) : FreezeBackend {

    override val kind: Backend = Backend.Root

    @Volatile
    private var rootCache: Boolean = false

    @Volatile
    private var rootCheckedAt: Long = 0L

    override fun state(): BackendState = if (hasRoot()) BackendState.Ready else BackendState.Unavailable

    fun hasRoot(): Boolean {
        val now = System.currentTimeMillis()
        if (now - rootCheckedAt < 30_000L) return rootCache
        val result = NativeShell.run("whoami")
        rootCache = result.ok && result.output?.trim()?.contains("root") == true
        rootCheckedAt = now
        return rootCache
    }

    private val userArg: String get() = "--user ${NativeShell.currentUserId()}"

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
    ): Boolean = withContext(Dispatchers.IO) {
        val result = when (action) {
            FreezeAction.Stop -> NativeShell.run("am force-stop $userArg $packageName")
            FreezeAction.Disable -> NativeShell.run(
                "pm ${if (frozen) "disable" else "enable"} $userArg $packageName",
            )
            FreezeAction.Hide -> NativeShell.run(
                "pm ${if (frozen) "hide" else "unhide"} $userArg $packageName",
            )
            FreezeAction.Suspend -> NativeShell.run(
                "pm ${if (frozen) "suspend" else "unsuspend"} $userArg $packageName",
            )
            FreezeAction.None -> return@withContext false
        }
        result.ok
    }

    override fun lockScreen(): Boolean =
        NativeShell.run("input keyevent KEYCODE_POWER").ok

    override suspend fun uninstall(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val scoped = if (PackageTools.canUninstallNormally(context, packageName)) "" else "$userArg "
        NativeShell.run("pm uninstall $scoped$packageName").ok
    }

    override suspend fun reinstall(packageName: String): Boolean = withContext(Dispatchers.IO) {
        NativeShell.run("pm install-existing $userArg $packageName").ok
    }

    override suspend fun shell(command: String): Pair<Int, String?> = withContext(Dispatchers.IO) {
        NativeShell.run(command).let { it.code to it.output }
    }
}
