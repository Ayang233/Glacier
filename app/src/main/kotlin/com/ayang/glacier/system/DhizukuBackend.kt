package com.ayang.glacier.system

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.core.content.getSystemService
import com.ayang.glacier.domain.Backend
import com.ayang.glacier.domain.BackendState
import com.ayang.glacier.domain.FreezeAction
import com.ayang.glacier.platform.PackageTools
import com.rosan.dhizuku.api.Dhizuku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dhizuku 后端：借用设备所有者应用的权限代理。
 *
 * Dhizuku 的 DPM 调用要求 `admin == null`（由 Dhizuku 侧填充），所以这里直接反射调用。
 */
class DhizukuBackend(private val context: Context) : FreezeBackend {

    override val kind: Backend = Backend.Dhizuku

    private val dpm: DevicePolicyManager? = context.getSystemService()

    private val TAG = "GlacierDhizuku"

    @Volatile
    private var initialized = false

    fun initialize() {
        if (initialized) return
        initialized = runCatching {
            if (Dhizuku.init(context) && Dhizuku.isPermissionGranted()) {
                setDelegatedScopes()
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    private fun setDelegatedScopes() {
        runCatching {
            val scopes = Dhizuku.getDelegatedScopes()
            if (!scopes.contains(DevicePolicyManager.DELEGATION_PACKAGE_ACCESS)) {
                Dhizuku.setDelegatedScopes(arrayOf(DevicePolicyManager.DELEGATION_PACKAGE_ACCESS))
            }
        }.onFailure { Log.w(TAG, "setDelegatedScopes failed", it) }
    }

    private fun permissionGranted(): Boolean = runCatching { Dhizuku.isPermissionGranted() }.getOrDefault(false)

    override fun state(): BackendState {
        val bound = runCatching { Dhizuku.init(context) }.getOrDefault(false)
        return when {
            !bound -> BackendState.Unavailable
            !permissionGranted() -> BackendState.NeedsPermission
            else -> BackendState.Ready
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
        when (action) {
            FreezeAction.Hide -> runCatching {
                policy.javaClass.getMethod(
                    "setApplicationHidden",
                    ComponentName::class.java,
                    String::class.java,
                    Boolean::class.java,
                ).invoke(policy, null, packageName, frozen) as Boolean
            }.getOrElse {
                Log.w(TAG, "setApplicationHidden failed", it)
                false
            }

            FreezeAction.Suspend -> runCatching {
                (policy.javaClass.getMethod(
                    "setPackagesSuspended",
                    ComponentName::class.java,
                    Array<String>::class.java,
                    Boolean::class.java,
                ).invoke(policy, null, arrayOf(packageName), frozen) as Array<*>).isEmpty()
            }.getOrElse {
                Log.w(TAG, "setPackagesSuspended failed", it)
                false
            }

            else -> false
        }
    }

    override fun lockScreen(): Boolean = runCatching {
        dpm?.lockNow()
        true
    }.getOrDefault(false)
}
