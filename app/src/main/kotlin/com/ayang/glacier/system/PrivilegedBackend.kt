package com.ayang.glacier.system

import android.content.Context
import com.ayang.glacier.domain.Backend
import com.ayang.glacier.domain.BackendState
import com.ayang.glacier.domain.FreezeAction
import com.ayang.glacier.platform.PackageTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 特权后端：应用自身已经拿到系统权限（特权应用 / 越狱环境 / 系统签名）。
 *
 * 不依赖 su，也不依赖 Shizuku；直接调用 PackageManager 与隐藏 API。
 */
class PrivilegedBackend(private val context: Context) : FreezeBackend {

    override val kind: Backend = Backend.Privileged

    override fun state(): BackendState = BackendState.Ready

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
        when (action) {
            FreezeAction.Stop -> !frozen || PackageTools.forceStop(context, packageName)
            FreezeAction.Disable -> PackageTools.setEnabled(context, packageName, !frozen)
            // 隐藏/挂起不是普通应用能做的，直接返回失败，由上层提示权限不足。
            FreezeAction.Hide, FreezeAction.Suspend -> false
            FreezeAction.None -> false
        }
    }

    override fun lockScreen(): Boolean = false
}
