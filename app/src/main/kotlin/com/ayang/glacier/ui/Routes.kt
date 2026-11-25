package com.ayang.glacier.ui

import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

/**
 * 顶层路由。
 *
 * 一级页（主页/应用/设置）在 [Main] 内部用 pager 承载，
 * 这样底栏可以跟着一级页一起被二级页盖住，而不是靠挂载/卸载切换。
 */
@Serializable
sealed interface Route : NavKey {

    @Serializable
    data object Main : Route

    @Serializable
    data object About : Route

    @Serializable
    data object Licenses : Route

    // ---- 设置二级页（R16：设置首页只留通用项与入口，其余归类到二级页）----

    @Serializable
    data object ThemeSettings : Route

    @Serializable
    data object SearchSettings : Route

    @Serializable
    data object AutomationSettings : Route

    @Serializable
    data object SystemSettings : Route
}
