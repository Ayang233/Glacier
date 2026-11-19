package com.ayang.glacier.system

import com.ayang.glacier.domain.Backend
import com.ayang.glacier.domain.BackendState
import com.ayang.glacier.domain.FreezeAction

/**
 * 一个"能执行冻结"的后端。
 *
 * 约定：
 * - [isFrozen] 必须是廉价读操作（查系统状态，不发起 Binder 事务）。
 * - 所有写操作都可失败，失败就是 `false`，由上层决定展示什么。
 */
interface FreezeBackend {

    val kind: Backend

    fun state(): BackendState

    fun isFrozen(packageName: String, action: FreezeAction): Boolean

    suspend fun setFrozen(packageName: String, action: FreezeAction, frozen: Boolean): Boolean

    fun lockScreen(): Boolean

    suspend fun uninstall(packageName: String): Boolean = false

    suspend fun reinstall(packageName: String): Boolean = false

    suspend fun shell(command: String): Pair<Int, String?> = 1 to null
}
