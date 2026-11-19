package com.ayang.glacier.system

import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.getSystemService
import com.ayang.glacier.domain.Backend
import com.ayang.glacier.domain.BackendState
import com.ayang.glacier.domain.FreezeAction
import com.ayang.glacier.platform.PackageTools
import com.ayang.glacier.platform.admin.GlacierDeviceAdminReceiver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 设备所有者 / 配置文件所有者后端。
 *
 * 只有真的被设为 owner 才可用；普通安装下 [state] 返回 Unavailable，
 * 设置页会给出 `adb shell dpm set-device-owner …` 的提示而不是假装能用。
 */
class DeviceOwnerBackend(private val context: Context) : FreezeBackend {

    override val kind: Backend = Backend.DeviceOwner

    private val dpm: DevicePolicyManager? = context.getSystemService()
    private val admin = ComponentName(context, GlacierDeviceAdminReceiver::class.java)

    private val TAG = "GlacierOwner"

    val isDeviceOwner: Boolean
        get() = runCatching { dpm?.isDeviceOwnerApp(context.packageName) == true }.getOrDefault(false)

    val isProfileOwner: Boolean
        get() = runCatching { dpm?.isProfileOwnerApp(context.packageName) == true }.getOrDefault(false)

    val isAdminActive: Boolean
        get() = runCatching { dpm?.isAdminActive(admin) == true }.getOrDefault(false)

    private val isOwnerActive: Boolean get() = (isDeviceOwner || isProfileOwner) && isAdminActive

    override fun state(): BackendState = when {
        isOwnerActive -> BackendState.Ready
        !isAdminActive -> BackendState.NeedsPermission
        else -> BackendState.Unavailable
    }

    fun setOrganizationNameIfPossible(name: String) {
        val policy = dpm ?: return
        if (isDeviceOwner && isAdminActive) {
            runCatching { policy.setOrganizationName(admin, name) }
        }
    }

    fun enableBackupServiceIfPossible() {
        val policy = dpm ?: return
        if (isDeviceOwner && isAdminActive && !policy.isBackupServiceEnabled(admin)) {
            runCatching { policy.setBackupServiceEnabled(admin, true) }
        }
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
    ): Boolean = withContext(Dispatchers.IO) {
        val policy = dpm ?: return@withContext false
        if (!isOwnerActive) return@withContext false
        when (action) {
            FreezeAction.Hide -> runCatching {
                policy.setApplicationHidden(admin, packageName, frozen)
            }.getOrElse {
                Log.w(TAG, "setApplicationHidden failed", it)
                false
            }

            FreezeAction.Suspend -> runCatching {
                policy.setPackagesSuspended(admin, arrayOf(packageName), frozen).isEmpty()
            }.getOrElse {
                Log.w(TAG, "setPackagesSuspended failed", it)
                false
            }

            else -> false
        }
    }

    override fun lockScreen(): Boolean = runCatching {
        if (isAdminActive) dpm?.lockNow()
        isAdminActive
    }.getOrDefault(false)

    override suspend fun uninstall(packageName: String): Boolean = withContext(Dispatchers.IO) {
        if (!isOwnerActive) return@withContext false
        runCatching {
            context.packageManager.packageInstaller.uninstall(
                packageName,
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(),
                    PendingIntent.FLAG_IMMUTABLE,
                ).intentSender,
            )
            true
        }.getOrDefault(false)
    }
}
