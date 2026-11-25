package com.ayang.glacier.system

import android.content.Context
import android.content.pm.ApplicationInfo
import com.ayang.glacier.data.GlacierPreferences
import com.ayang.glacier.domain.Backend
import com.ayang.glacier.domain.BackendState
import com.ayang.glacier.domain.FreezeAction
import com.ayang.glacier.domain.WorkingMode
import com.ayang.glacier.platform.PackageTools
import com.ayang.glacier.platform.UiFeedback

/** 批量冻结的结果。批量里"部分成功"也要如实回报，而不是一概而论。 */
sealed interface FreezeOutcome {
    data class Applied(val count: Int, val lastName: String?) : FreezeOutcome
    data object Denied : FreezeOutcome
}

/**
 * 冻结能力的唯一入口。
 *
 * UI 只跟它打交道，不直接碰 Shizuku / DPM / su。
 * 模式决定后端与动作；后端是否可用由 [stateOf] 如实回答。
 */
class FreezeController(
    private val context: Context,
    private val preferences: GlacierPreferences,
) {

    val shizuku = ShizukuBackend(context)
    val root = RootBackend(context)
    val dhizuku = DhizukuBackend(context)
    val deviceOwner = DeviceOwnerBackend(context)
    val island = IslandBackend(context)
    val privileged = PrivilegedBackend(context)

    val mode: WorkingMode get() = preferences.workingMode

    fun backendOf(mode: WorkingMode): FreezeBackend? = when (mode.backend) {
        Backend.None -> null
        Backend.Shizuku -> shizuku
        Backend.Root -> root
        Backend.Jailbreak -> privileged
        Backend.Dhizuku -> dhizuku
        Backend.DeviceOwner -> deviceOwner
        Backend.Island -> island
        Backend.Privileged -> privileged
    }

    fun stateOf(mode: WorkingMode = this.mode): BackendState =
        backendOf(mode)?.state() ?: BackendState.Unavailable

    /** 当前模式是否已经能真正执行冻结。 */
    fun canFreeze(): Boolean = stateOf() == BackendState.Ready

    fun initialize() {
        if (mode.backend == Backend.Dhizuku) dhizuku.initialize()
    }

    // ---- 状态 ----

    /**
     * 是否处于冻结状态。
     *
     * 默认模式下无法确定"用户到底想怎么冻结"，按 Hail 的语义取三种状态的并集，
     * 这样从其他后端切换过来时列表不会全部显示为未冻结。
     */
    fun isFrozen(packageName: String): Boolean {
        val action = mode.action
        if (action == FreezeAction.None) {
            return PackageTools.isDisabled(context, packageName) ||
                PackageTools.isHidden(context, packageName) ||
                PackageTools.isSuspended(context, packageName)
        }
        return backendOf(mode)?.isFrozen(packageName, action) ?: false
    }

    /**
     * 已经拿到 [ApplicationInfo] 时的冻结态判断。
     *
     * 所有后端的 `isFrozen` 都只是按当前动作去读同一个 [ApplicationInfo] 的
     * flags / privateFlags（见 `ShizukuBackend` / `RootBackend` 等实现），
     * 所以这里不经过后端、不再查一次 `PackageManager`，语义与 [isFrozen] 完全一致。
     *
     * 用途：冷启动首帧与主页快照——那里本来就要读 info，冻结态顺带算出来，
     * 不必等"枚举全部应用"的后台刷新跑完才有冻结图标。
     */
    fun isFrozen(info: ApplicationInfo): Boolean = when (mode.action) {
        FreezeAction.None -> PackageTools.isDisabled(info) ||
            PackageTools.isHidden(info) ||
            PackageTools.isSuspended(info)
        FreezeAction.Stop -> PackageTools.isStopped(info)
        FreezeAction.Disable -> PackageTools.isDisabled(info)
        FreezeAction.Hide -> PackageTools.isHidden(info)
        FreezeAction.Suspend -> PackageTools.isSuspended(info)
    }

    // ---- 写 ----

    suspend fun setFrozen(packageName: String, frozen: Boolean): Boolean {
        if (packageName == context.packageName) return false
        if (mode.action == FreezeAction.None) return false
        val backend = backendOf(mode) ?: return false
        if (backend.state() != BackendState.Ready) return false
        return backend.setFrozen(packageName, mode.action, frozen)
    }

    /** 只处理"当前状态与目标不同"的条目，避免无意义地反复下发命令。 */
    suspend fun setListFrozen(packageNames: Collection<String>, frozen: Boolean): FreezeOutcome {
        val targets = packageNames.filter { it != context.packageName && isFrozen(it) != frozen }
        var applied = 0
        var lastName: String? = null
        var denied = false
        targets.forEach { packageName ->
            if (setFrozen(packageName, frozen)) {
                applied++
                lastName = PackageTools.labelOf(context, packageName).toString()
            } else {
                denied = true
            }
        }
        return if (applied == 0 && denied) FreezeOutcome.Denied else FreezeOutcome.Applied(applied, lastName)
    }

    fun lockScreen(): Boolean = when (mode.backend) {
        Backend.None -> false
        else -> backendOf(mode)?.lockScreen() ?: false
    }

    suspend fun uninstall(packageName: String): Boolean {
        val backend = backendOf(mode)
        if (backend != null && backend.state() == BackendState.Ready && backend.uninstall(packageName)) {
            return true
        }
        // 退回系统卸载确认对话框，至少不会静默失败。
        UiFeedback.startSystemActivity(
            context,
            android.content.Intent.ACTION_DELETE,
            PackageTools.packageUri(packageName),
        )
        return false
    }

    suspend fun reinstall(packageName: String): Boolean {
        val backend = backendOf(mode) ?: return false
        if (backend.state() != BackendState.Ready) return false
        return backend.reinstall(packageName)
    }

    suspend fun shell(command: String): Pair<Int, String?> {
        val backend = backendOf(mode) ?: return 0 to null
        return backend.shell(command)
    }
}
