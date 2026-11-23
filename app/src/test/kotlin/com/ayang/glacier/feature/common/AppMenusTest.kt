package com.ayang.glacier.feature.common

import com.ayang.glacier.domain.AppEntry
import com.ayang.glacier.domain.SearchKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 三份菜单的结构守护。
 *
 * 菜单内容已经从组件里的 `if` 分支抽成纯函数（[appsMenuActions] / [homeMenuActions] /
 * [apiMenuActions]），所以"两页职责不许串味"这条可以在这里钉住：
 * - 应用页只给管理向三项，**不能**出现冻结/置顶/标签/装卸包；
 * - 主页给使用向，冻结位随状态切「冻结/解冻」，且「解冻并从主页移除」只在已冻结时追加一行。
 *
 * 这些分支很难在模拟器上逐个人肉验（要真机冻结后端），所以放在这里当回归网。
 */
class AppMenusTest {

    private fun entry(frozen: Boolean = false, pinned: Boolean = false, whitelisted: Boolean = false) = AppEntry(
        packageName = "com.example.app",
        label = "Example",
        isSystem = false,
        isInstalled = true,
        frozen = frozen,
        pinned = pinned,
        whitelisted = whitelisted,
        tagIds = emptyList(),
        installTime = 0L,
        updateTime = 0L,
        search = SearchKeys("example", "", "", ""),
    )

    @Test
    fun `apps menu is management only`() {
        assertEquals(
            listOf(AppAction.AppInfo, AppAction.CopyPackageName, AppAction.ExtractApk),
            appsMenuActions(),
        )
        // 管理向菜单里不允许出现这些"使用向"动作，否则两页职责又混了。
        val forbidden = setOf(
            AppAction.Freeze,
            AppAction.Unfreeze,
            AppAction.TogglePin,
            AppAction.ToggleWhitelist,
            AppAction.EditTags,
            AppAction.DeferredFreeze,
            AppAction.RemoveFromHome,
            AppAction.UnfreezeAndRemoveFromHome,
        )
        assertTrue(appsMenuActions().none { it in forbidden })
    }

    @Test
    fun `home menu of an unfrozen app offers freeze and no unfreeze-remove row`() {
        val menu = homeMenuActions(entry(frozen = false))
        assertEquals(
            listOf(
                AppAction.Launch,
                AppAction.Freeze,
                AppAction.DeferredFreeze,
                AppAction.TogglePin,
                AppAction.ToggleWhitelist,
                AppAction.EditTags,
                AppAction.PinShortcut,
                AppAction.CopyPackageName,
                AppAction.RemoveFromHome,
            ),
            menu,
        )
        assertFalse(AppAction.UnfreezeAndRemoveFromHome in menu)
    }

    @Test
    fun `home menu of a frozen app swaps to unfreeze and appends unfreeze-remove last`() {
        val menu = homeMenuActions(entry(frozen = true))
        assertEquals(AppAction.Unfreeze, menu[1])
        assertFalse(AppAction.Freeze in menu)
        // 「解冻并从主页移除」只在已冻结时出现，且固定在最后一行。
        assertEquals(AppAction.UnfreezeAndRemoveFromHome, menu.last())
        assertEquals(10, menu.size)
    }

    @Test
    fun `api menu swaps home action by membership and skips page-bound actions`() {
        val inHome = apiMenuActions(entry(), inHome = true)
        val notInHome = apiMenuActions(entry(), inHome = false)
        assertTrue(AppAction.RemoveFromHome in inHome)
        assertTrue(AppAction.AddToHome in notInHome)
        // 标签编辑 / 定时任务需要页面里的后续交互，深链入口不提供。
        assertFalse(AppAction.EditTags in inHome)
        assertFalse(AppAction.DeferredFreeze in inHome)
    }
}
