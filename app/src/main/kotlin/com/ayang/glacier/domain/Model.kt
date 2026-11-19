package com.ayang.glacier.domain

/** 主页默认标签的固定 ID。历史数据与外部 API 都依赖 0 为默认标签。 */
const val DEFAULT_TAG_ID: Int = 0

/**
 * 被 Glacier 管理的应用条目。
 *
 * 只保存"用户意图"（是否置顶、是否白名单、归属哪些标签），
 * 冻结状态、图标、名称等运行时信息一律不落盘，始终从系统读取。
 */
data class ManagedApp(
    val packageName: String,
    var pinned: Boolean = false,
    var whitelisted: Boolean = false,
    val tagIds: MutableList<Int> = mutableListOf(DEFAULT_TAG_ID),
)

/** 用户自建标签。[id] 一旦分配不再变化，重命名只改 [name]。 */
data class Tag(
    val name: String,
    val id: Int,
)

/** 冻结动作的后端类型。决定"谁去执行"和"需要什么权限"。 */
enum class Backend {
    None,
    Shizuku,
    Root,
    Jailbreak,
    Dhizuku,
    DeviceOwner,
    Island,
    Privileged,
}

/** 冻结动作的语义。决定"算不算冻结"以及"怎么查状态"。 */
enum class FreezeAction {
    None,
    Stop,
    Disable,
    Hide,
    Suspend,
}

/**
 * 工作模式：后端 + 动作的组合。
 *
 * [key] 与历史数据、外部调用保持一致，避免升级后用户设置被重置。
 */
enum class WorkingMode(
    val key: String,
    val backend: Backend,
    val action: FreezeAction,
) {
    Default("default", Backend.None, FreezeAction.None),

    ShizukuStop("shizuku_stop", Backend.Shizuku, FreezeAction.Stop),
    ShizukuDisable("shizuku_disable", Backend.Shizuku, FreezeAction.Disable),
    ShizukuHide("shizuku_hide", Backend.Shizuku, FreezeAction.Hide),
    ShizukuSuspend("shizuku_suspend", Backend.Shizuku, FreezeAction.Suspend),

    RootStop("su_stop", Backend.Root, FreezeAction.Stop),
    RootDisable("su_disable", Backend.Root, FreezeAction.Disable),
    RootHide("su_hide", Backend.Root, FreezeAction.Hide),
    RootSuspend("su_suspend", Backend.Root, FreezeAction.Suspend),

    JailbreakDisable("jailbreak_disable", Backend.Jailbreak, FreezeAction.Disable),

    DhizukuHide("dhizuku_hide", Backend.Dhizuku, FreezeAction.Hide),
    DhizukuSuspend("dhizuku_suspend", Backend.Dhizuku, FreezeAction.Suspend),

    DeviceOwnerHide("owner_hide", Backend.DeviceOwner, FreezeAction.Hide),
    DeviceOwnerSuspend("owner_suspend", Backend.DeviceOwner, FreezeAction.Suspend),

    IslandFreeze("island_hide", Backend.Island, FreezeAction.Hide),
    IslandSuspend("island_suspend", Backend.Island, FreezeAction.Suspend),

    PrivilegedStop("privapp_stop", Backend.Privileged, FreezeAction.Stop),
    PrivilegedDisable("privapp_disable", Backend.Privileged, FreezeAction.Disable),
    ;

    companion object {
        fun fromKey(key: String?): WorkingMode = entries.firstOrNull { it.key == key } ?: Default
    }
}

/** 应用列表排序方式。 */
enum class AppSort(val key: String) {
    Name("name"),
    Install("install"),
    Update("update"),
    ;

    companion object {
        fun fromKey(key: String?): AppSort = entries.firstOrNull { it.key == key } ?: Name
    }
}

/** 应用列表过滤维度。四个维度可任意组合。 */
enum class AppFilter(val key: String, val default: Boolean) {
    UserApps("filter_user_apps", true),
    SystemApps("filter_system_apps", false),
    FrozenApps("filter_frozen_apps", true),
    UnfrozenApps("filter_unfrozen_apps", true),
}

/** 磁贴 / 快捷方式可绑定的动作。 */
enum class QuickAction(val key: String) {
    None("none"),
    AutoFreezeAfterLock("auto_freeze_after_lock"),
    FreezeAll("freeze_all"),
    UnfreezeAll("unfreeze_all"),
    FreezeNonWhitelisted("freeze_non_whitelisted"),
    Lock("lock"),
    LockFreeze("lock_freeze"),
    ;

    companion object {
        fun fromKey(key: String?): QuickAction = entries.firstOrNull { it.key == key } ?: None
    }
}

/** 界面主题模式。 */
enum class ThemeMode(val key: String) {
    FollowSystem("follow_system"),
    Light("theme_light"),
    Dark("theme_dark"),
    ;

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: FollowSystem
    }
}

/** 后端的可用状态，用于设置页如实展示能力，而不是"点了没反应"。 */
enum class BackendState {
    /** 可用，且已授权 */
    Ready,

    /** 可安装/可授权，但当前没有权限 */
    NeedsPermission,

    /** 当前设备完全不适用（例如没有 Root、没有设备所有者） */
    Unavailable,
}
