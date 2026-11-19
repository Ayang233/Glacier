package com.ayang.glacier.system

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import android.view.InputEvent
import android.view.KeyEvent
import com.ayang.glacier.BuildConfig
import com.ayang.glacier.domain.Backend
import com.ayang.glacier.domain.BackendState
import com.ayang.glacier.domain.FreezeAction
import com.ayang.glacier.platform.PackageTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.shizuku.server.IShizukuService
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * Shizuku 后端。
 *
 * 走 Shizuku 提供的 system_server Binder 代理，必要时以 shell/root 身份启动子进程执行 `pm` 命令。
 * 所有反射都集中在 [asInterface] 与两个隐藏方法调用上，便于随 Android 版本调整。
 */
class ShizukuBackend(private val context: Context) : FreezeBackend {

    override val kind: Backend = Backend.Shizuku

    private val TAG = "GlacierShizuku"

    fun isBinderAlive(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun hasPermission(): Boolean = runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    val isRoot: Boolean get() = runCatching { Shizuku.getUid() == 0 }.getOrDefault(false)

    private val callerPackage: String
        get() = if (isRoot) BuildConfig.APPLICATION_ID else "com.android.shell"

    override fun state(): BackendState = when {
        !isBinderAlive() -> BackendState.Unavailable
        !hasPermission() -> BackendState.NeedsPermission
        else -> BackendState.Ready
    }

    // ---- 反射入口 ----

    private fun asInterface(className: String, original: IBinder): Any {
        val stub = Class.forName("$className\$Stub")
        return HiddenApiBypass.invoke(
            stub,
            null,
            "asInterface",
            ShizukuBinderWrapper(original),
        )
    }

    private fun asInterface(className: String, serviceName: String): Any =
        asInterface(className, SystemServiceHelper.getSystemService(serviceName))

    // ---- 状态读取 ----

    override fun isFrozen(packageName: String, action: FreezeAction): Boolean = when (action) {
        FreezeAction.Stop -> PackageTools.isStopped(context, packageName)
        FreezeAction.Disable -> PackageTools.isDisabled(context, packageName)
        FreezeAction.Hide -> PackageTools.isHidden(context, packageName)
        FreezeAction.Suspend -> PackageTools.isSuspended(context, packageName)
        FreezeAction.None -> false
    }

    // ---- 写操作 ----

    override suspend fun setFrozen(
        packageName: String,
        action: FreezeAction,
        frozen: Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        if (state() != BackendState.Ready) return@withContext false
        when (action) {
            FreezeAction.Stop -> !frozen || forceStop(packageName)
            FreezeAction.Disable -> setDisabled(packageName, frozen)
            FreezeAction.Hide -> setHidden(packageName, frozen)
            FreezeAction.Suspend -> setSuspended(packageName, frozen)
            FreezeAction.None -> false
        }
    }

    private fun forceStop(packageName: String): Boolean = runCatching {
        val manager = asInterface("android.app.IActivityManager", Context.ACTIVITY_SERVICE)
        HiddenApiBypass.invoke(
            manager.javaClass,
            manager,
            "forceStopPackage",
            packageName,
            android.os.Process.myUserHandle().hashCode(),
        )
        true
    }.getOrElse {
        Log.w(TAG, "forceStop failed", it)
        false
    }

    private fun setDisabled(packageName: String, disabled: Boolean): Boolean {
        if (PackageTools.applicationInfoOrNull(context, packageName) == null) return false
        if (disabled) forceStop(packageName)
        runCatching {
            val manager = asInterface("android.content.pm.IPackageManager", "package")
            val newState = when {
                !disabled -> PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                isRoot -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                else -> PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
            }
            manager.javaClass.getMethod(
                "setApplicationEnabledSetting",
                String::class.java,
                Int::class.java,
                Int::class.java,
                Int::class.java,
                String::class.java,
            ).invoke(
                manager,
                packageName,
                newState,
                0,
                android.os.Process.myUserHandle().hashCode(),
                BuildConfig.APPLICATION_ID,
            )
        }.onFailure { Log.w(TAG, "setDisabled failed", it) }
        return PackageTools.isDisabled(context, packageName) == disabled
    }

    private fun setHidden(packageName: String, hidden: Boolean): Boolean {
        if (PackageTools.applicationInfoOrNull(context, packageName) == null) return false
        if (hidden) forceStop(packageName)
        return runCatching {
            val manager = asInterface("android.content.pm.IPackageManager", "package")
            manager.javaClass.getMethod(
                "setApplicationHiddenSettingAsUser",
                String::class.java,
                Boolean::class.java,
                Int::class.java,
            ).invoke(
                manager,
                packageName,
                hidden,
                android.os.Process.myUserHandle().hashCode(),
            ) as Boolean
        }.getOrElse {
            Log.w(TAG, "setHidden failed", it)
            false
        }
    }

    private fun setSuspended(packageName: String, suspended: Boolean): Boolean {
        if (PackageTools.applicationInfoOrNull(context, packageName) == null) return false
        setBackgroundRestricted(packageName, suspended)
        if (suspended) forceStop(packageName)
        return runCatching {
            val manager = asInterface("android.content.pm.IPackageManager", "package")
            val userId = android.os.Process.myUserHandle().hashCode()
            val result = HiddenApiBypass.invoke(
                manager.javaClass,
                manager,
                "setPackagesSuspendedAsUser",
                arrayOf(packageName),
                suspended,
                null,
                null,
                if (suspended) suspendDialogInfo() else null,
                0,
                callerPackage,
                userId,
                userId,
            )
            (result as? Array<*>)?.isEmpty() ?: false
        }.getOrElse {
            Log.w(TAG, "setSuspended failed", it)
            false
        }
    }

    private fun suspendDialogInfo(): Any = HiddenApiBypass.newInstance(
        Class.forName("android.content.pm.SuspendDialogInfo\$Builder"),
    ).let {
        HiddenApiBypass.invoke(it.javaClass, it, "setNeutralButtonAction", 1)
        HiddenApiBypass.invoke(it.javaClass, it, "build")
    }

    /** 挂起时同时限制后台运行，否则被挂起的应用仍可能被拉起。 */
    private fun setBackgroundRestricted(packageName: String, restricted: Boolean): Boolean = runCatching {
        val ops = asInterface("com.android.internal.app.IAppOpsService", Context.APP_OPS_SERVICE)
        HiddenApiBypass.invoke(
            ops.javaClass,
            ops,
            "setMode",
            HiddenApiBypass.invoke(
                AppOpsManager::class.java,
                null,
                "strOpToOp",
                "android:run_any_in_background",
            ),
            PackageTools.uidOf(context, packageName),
            packageName,
            if (restricted) AppOpsManager.MODE_IGNORED else AppOpsManager.MODE_ALLOWED,
        )
        true
    }.getOrElse {
        Log.w(TAG, "setBackgroundRestricted failed", it)
        false
    }

    override fun lockScreen(): Boolean = runCatching {
        val input = asInterface("android.hardware.input.IInputManager", Context.INPUT_SERVICE)
        val inject = input.javaClass.getMethod(
            "injectInputEvent",
            InputEvent::class.java,
            Int::class.javaPrimitiveType,
        )
        val now = SystemClock.uptimeMillis()
        inject.invoke(input, KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_POWER, 0), 0)
        inject.invoke(input, KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_POWER, 0), 0)
        true
    }.getOrElse {
        Log.w(TAG, "lockScreen failed", it)
        false
    }

    override suspend fun uninstall(packageName: String): Boolean = withContext(Dispatchers.IO) {
        val args = if (PackageTools.canUninstallNormally(context, packageName)) {
            packageName
        } else {
            "--user current $packageName"
        }
        shellInternal("pm uninstall $args").first == 0
    }

    override suspend fun reinstall(packageName: String): Boolean = withContext(Dispatchers.IO) {
        shellInternal("pm install-existing --user current $packageName").first == 0
    }

    override suspend fun shell(command: String): Pair<Int, String?> = withContext(Dispatchers.IO) {
        shellInternal(command)
    }

    private fun shellInternal(command: String): Pair<Int, String?> = runCatching {
        val service = IShizukuService.Stub.asInterface(Shizuku.getBinder())
        val process = service.newProcess(arrayOf(if (isRoot) "su" else "sh"), null, null)
        ParcelFileDescriptor.AutoCloseOutputStream(process.outputStream).use {
            it.write(command.toByteArray())
        }
        val exit = process.waitFor()
        val output = ParcelFileDescriptor.AutoCloseInputStream(process.inputStream)
            .bufferedReader().use { it.readText() }
            .ifBlank {
                ParcelFileDescriptor.AutoCloseInputStream(process.errorStream)
                    .bufferedReader().use { it.readText() }
            }
        process.destroy()
        exit to output
    }.getOrElse {
        Log.w(TAG, "shell failed: $command", it)
        1 to it.stackTraceToString()
    }
}
