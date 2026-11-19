package com.ayang.glacier.platform

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.getSystemService
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * 包状态读取与"不需要特权也能做"的系统操作。
 *
 * 冻结本身不在这里：那是 [com.ayang.glacier.system] 的职责。
 * 本对象只回答"这个包现在是什么状态"。
 */
object PackageTools {

    private const val TAG = "GlacierPackages"

    private val flagsAll: Int
        get() = PackageManager.MATCH_UNINSTALLED_PACKAGES

    fun context(base: Context): Context = base.applicationContext

    fun applicationInfoOrNull(context: Context, packageName: String): ApplicationInfo? = runCatching {
        context.packageManager.getApplicationInfo(packageName, flagsAll)
    }.getOrNull()

    fun packageInfoOrNull(context: Context, packageName: String) = runCatching {
        context.packageManager.getPackageInfo(packageName, flagsAll)
    }.getOrNull()

    fun labelOf(context: Context, packageName: String): CharSequence =
        applicationInfoOrNull(context, packageName)?.loadLabel(context.packageManager) ?: packageName

    fun isInstalled(context: Context, packageName: String): Boolean =
        applicationInfoOrNull(context, packageName)?.let {
            it.flags and ApplicationInfo.FLAG_INSTALLED == ApplicationInfo.FLAG_INSTALLED
        } ?: false

    fun isSystemApp(context: Context, packageName: String): Boolean =
        applicationInfoOrNull(context, packageName)?.let {
            it.flags and ApplicationInfo.FLAG_SYSTEM == ApplicationInfo.FLAG_SYSTEM
        } ?: false

    fun isDisabled(context: Context, packageName: String): Boolean =
        applicationInfoOrNull(context, packageName)?.enabled == false

    /** 隐藏（`pm hide`）状态读的是 privateFlags 的第 0 位。 */
    fun isHidden(context: Context, packageName: String): Boolean =
        applicationInfoOrNull(context, packageName)?.let { info ->
            runCatching { privateFlags(info) and PRIVATE_FLAG_HIDDEN == PRIVATE_FLAG_HIDDEN }
                .getOrDefault(false)
        } ?: false

    fun isStopped(context: Context, packageName: String): Boolean =
        applicationInfoOrNull(context, packageName)?.let {
            it.flags and ApplicationInfo.FLAG_STOPPED == ApplicationInfo.FLAG_STOPPED
        } ?: false

    fun isSuspended(context: Context, packageName: String): Boolean =
        applicationInfoOrNull(context, packageName)?.let {
            it.flags and ApplicationInfo.FLAG_SUSPENDED == ApplicationInfo.FLAG_SUSPENDED
        } ?: false

    fun isPrivileged(context: Context, packageName: String): Boolean =
        applicationInfoOrNull(context, packageName)?.let { info ->
            runCatching { privateFlags(info) and PRIVATE_FLAG_PRIVILEGED == PRIVATE_FLAG_PRIVILEGED }
                .getOrDefault(false)
        } ?: false

    fun canUninstallNormally(context: Context, packageName: String): Boolean =
        applicationInfoOrNull(context, packageName)?.sourceDir?.startsWith("/data") == true

    fun uidOf(context: Context, packageName: String): Int =
        runCatching { context.packageManager.getPackageUid(packageName, 0) }.getOrDefault(-1)

    fun launchIntent(context: Context, packageName: String): Intent? =
        context.packageManager.getLaunchIntentForPackage(packageName)

    /**
     * 可列出"已安装"的应用（含被冻结、被禁用、被挂起的）。
     *
     * 查询带 `MATCH_UNINSTALLED_PACKAGES`，这样隐藏/禁用/挂起的应用仍然可见；
     * 但"卸载后保留数据"的幽灵条目也会被带出来，所以再用 sourceDir 是否存在过滤一次。
     */
    fun installedApplications(context: Context): List<ApplicationInfo> =
        runCatching { context.packageManager.getInstalledApplications(flagsAll) }
            .getOrDefault(emptyList())
            .filter { info ->
                info.flags and ApplicationInfo.FLAG_INSTALLED == ApplicationInfo.FLAG_INSTALLED &&
                    info.sourceDir?.let { java.io.File(it).exists() } == true
            }

    fun packageUri(packageName: String): String = "package:$packageName"

    /** 无特权路径的强停；失败就返回 false，由调用方选择特权后端。 */
    fun forceStop(context: Context, packageName: String): Boolean = runCatching {
        val manager = context.getSystemService<ActivityManager>() ?: return false
        HiddenApiBypass.invoke(manager.javaClass, manager, "forceStopPackage", packageName)
        true
    }.getOrElse {
        Log.w(TAG, "forceStop $packageName failed", it)
        false
    }

    /** 无特权路径的启用/禁用；是否成功以系统回报的状态为准。 */
    fun setEnabled(context: Context, packageName: String, enabled: Boolean): Boolean {
        if (applicationInfoOrNull(context, packageName) == null) return false
        if (!enabled) forceStop(context, packageName)
        runCatching {
            context.packageManager.setApplicationEnabledSetting(
                packageName,
                if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                0,
            )
        }.onFailure { Log.w(TAG, "setEnabled $packageName failed", it) }
        return isDisabled(context, packageName) == !enabled
    }

    private fun privateFlags(info: ApplicationInfo): Int =
        ApplicationInfo::class.java.getField("privateFlags").get(info) as Int

    private const val PRIVATE_FLAG_HIDDEN = 1
    private const val PRIVATE_FLAG_PRIVILEGED = 8
}
