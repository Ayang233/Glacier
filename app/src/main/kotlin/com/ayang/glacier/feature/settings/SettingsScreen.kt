package com.ayang.glacier.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ayang.glacier.GlacierApplication
import com.ayang.glacier.R
import com.ayang.glacier.domain.BackendState
import com.ayang.glacier.domain.QuickAction
import com.ayang.glacier.domain.ThemeMode
import com.ayang.glacier.domain.WorkingMode
import com.ayang.glacier.platform.auto.AutoFreezeScheduler
import com.ayang.glacier.ui.LocalGlassActive
import com.ayang.glacier.ui.LocalRootBackdrop
import com.ayang.glacier.ui.components.BackNavigationAction
import com.ayang.glacier.ui.components.GroupCard
import com.ayang.glacier.ui.components.PageFrame
import com.ayang.glacier.ui.components.PageMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

/**
 * 设置**首页**。
 *
 * R16 起按"一级页面只留常用项与入口"重排：
 * - 去掉「通用 / 外观 / 搜索 / …」这些小标题（`SmallTitle`），页面不再是一长串分组标题；
 * - **通用**两项（工作模式、启动时验证）直接留在首页；
 * - 外观 / 搜索 / 自动化 / 系统权限各自归到一个**二级页**，首页只放一行入口
 *   （官方 `ArrowPreference`），点进去是同一套 `PageFrame` 的二级页；
 * - 关于保持一级页面一行入口（用户要求）。
 *
 * 入口行只负责导航，不显示计数或摘要，避免首页重新变成一页设置清单。
 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun SettingsScreen(
    onOpenThemeSettings: () -> Unit,
    onOpenSearchSettings: () -> Unit,
    onOpenAutomationSettings: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as GlacierApplication
    val preferences = app.preferences
    val freeze = app.freeze
    val repository = app.repository

    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    val glassActive = LocalGlassActive.current
    val backdrop = LocalRootBackdrop.current.takeIf { glassActive }

    // 设置项本身是普通属性，读 revision 让页面在写入后重组。
    val revision = preferences.revision.intValue

    // 初值不要在主线程算：RootBackend.state() 可能同步跑一次 `su whoami`（RootBackend.kt:26-35），
    // 放在 produceState 的 initialValue 里会阻塞组合。先给 null（摘要只显示模式名），
    // IO 读完后再补上后端状态，摘要与真实能力始终一致。
    val modeState = produceState<BackendState?>(initialValue = null, preferences.workingMode) {
        value = withContext(Dispatchers.IO) { freeze.stateOf() }
    }

    var requireUnlock by remember(revision) { mutableStateOf(preferences.requireUnlock) }

    PageFrame(
        title = stringResource(R.string.nav_settings),
        subtitle = repository.deviceSummary(),
        scrollBehavior = scrollBehavior,
        scrollBarAdapter = rememberScrollBarAdapter(listState),
        // 横向 12dp 只给卡片，由每张卡片的 horizontal padding 提供。
        contentPaddingHorizontal = 0.dp,
    ) { contentPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            // 弹性 overscroll 由 PageFrame 的官方 pageScrollModifiers 统一消费（官方 SettingsPage 同参）。
            overscrollEffect = null,
            verticalArrangement = Arrangement.spacedBy(PageMetrics.SectionGap),
        ) {
            item {
                GroupCard(modifier = Modifier.padding(horizontal = PageMetrics.Horizontal)) {
                    GlassDropdownPreference(
                        onSelectedIndexChange = { index ->
                            val mode = WorkingMode.entries[index]
                            preferences.workingMode = mode
                            freeze.initialize()
                            preferences.bumpRevision()
                            AutoFreezeScheduler.syncService(context)
                        },
                        items = WorkingMode.entries.map { modeLabel(it) },
                        selectedIndex = WorkingMode.entries.indexOf(preferences.workingMode),
                        title = stringResource(R.string.working_mode),
                        summary = when {
                            preferences.workingMode.backend == com.ayang.glacier.domain.Backend.None ->
                                modeLabel(preferences.workingMode)

                            else -> modeState.value?.let { state ->
                                stringResource(
                                    R.string.working_mode_summary,
                                    modeLabel(preferences.workingMode),
                                    backendSummary(state),
                                )
                            } ?: modeLabel(preferences.workingMode)
                        },
                        backdrop = backdrop,
                    )
                    SwitchPreference(
                        checked = requireUnlock,
                        onCheckedChange = {
                            requireUnlock = it
                            preferences.requireUnlock = it
                            preferences.bumpRevision()
                        },
                        title = stringResource(R.string.biometric_login),
                        summary = stringResource(R.string.biometric_login_summary),
                    )
                }
            }

            item {
                GroupCard(modifier = Modifier.padding(horizontal = PageMetrics.Horizontal)) {
                    ArrowPreference(
                        title = stringResource(R.string.settings_appearance),
                        summary = stringResource(R.string.settings_theme_summary),
                        onClick = onOpenThemeSettings,
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_search),
                        summary = stringResource(R.string.settings_search_summary),
                        onClick = onOpenSearchSettings,
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_automation),
                        summary = stringResource(R.string.settings_automation_summary),
                        onClick = onOpenAutomationSettings,
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_system),
                        summary = stringResource(R.string.settings_system_summary),
                        onClick = onOpenSystemSettings,
                    )
                }
            }

            item {
                GroupCard(modifier = Modifier.padding(horizontal = PageMetrics.Horizontal)) {
                    ArrowPreference(
                        title = stringResource(R.string.title_about),
                        summary = stringResource(R.string.about_built_with),
                        onClick = onOpenAbout,
                    )
                }
            }
        }
    }
}

/**
 * 设置二级页的统一骨架：官方二级页的 `PageFrame(expandable = false)` + 返回按钮 + 滚动条。
 *
 * 与 About / Licenses 两个二级页同构（同一个 [BackNavigationAction]、同一套滚动条适配），
 * 页面内容由各页自己给。
 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
internal fun SettingsSubPage(
    title: String,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    PageFrame(
        title = title,
        scrollBehavior = scrollBehavior,
        // 二级页不折叠大标题（官方 AboutPage.kt:139 的 `expandable = false`）。
        expandable = false,
        contentPaddingHorizontal = 0.dp,
        scrollBarAdapter = rememberScrollBarAdapter(listState),
        navigationIcon = {
            BackNavigationAction(
                onClick = onBack,
                contentDescription = stringResource(R.string.close),
            )
        },
    ) { contentPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            overscrollEffect = null,
            verticalArrangement = Arrangement.spacedBy(PageMetrics.SectionGap),
        ) {
            content()
        }
    }
}

@Composable
private fun modeLabel(mode: WorkingMode): String = stringResource(
    when (mode) {
        WorkingMode.Default -> R.string.mode_default
        WorkingMode.ShizukuStop -> R.string.mode_shizuku_stop
        WorkingMode.ShizukuDisable -> R.string.mode_shizuku_disable
        WorkingMode.ShizukuHide -> R.string.mode_shizuku_hide
        WorkingMode.ShizukuSuspend -> R.string.mode_shizuku_suspend
        WorkingMode.RootStop -> R.string.mode_su_stop
        WorkingMode.RootDisable -> R.string.mode_su_disable
        WorkingMode.RootHide -> R.string.mode_su_hide
        WorkingMode.RootSuspend -> R.string.mode_su_suspend
        WorkingMode.JailbreakDisable -> R.string.mode_jailbreak_disable
        WorkingMode.DhizukuHide -> R.string.mode_dhizuku_hide
        WorkingMode.DhizukuSuspend -> R.string.mode_dhizuku_suspend
        WorkingMode.DeviceOwnerHide -> R.string.mode_owner_hide
        WorkingMode.DeviceOwnerSuspend -> R.string.mode_owner_suspend
        WorkingMode.IslandFreeze -> R.string.mode_island_hide
        WorkingMode.IslandSuspend -> R.string.mode_island_suspend
        WorkingMode.PrivilegedStop -> R.string.mode_privapp_stop
        WorkingMode.PrivilegedDisable -> R.string.mode_privapp_disable
    },
)

@Composable
private fun backendSummary(state: BackendState): String = stringResource(
    when (state) {
        BackendState.Ready -> R.string.backend_ready
        BackendState.NeedsPermission -> R.string.backend_needs_permission
        BackendState.Unavailable -> R.string.working_mode_unavailable
    },
)

@Composable
internal fun themeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.FollowSystem -> R.string.theme_follow_system
        ThemeMode.Light -> R.string.theme_light
        ThemeMode.Dark -> R.string.theme_dark
    },
)

@Composable
internal fun actionLabel(action: QuickAction): String = stringResource(
    when (action) {
        QuickAction.None -> R.string.action_none
        QuickAction.AutoFreezeAfterLock -> R.string.auto_freeze
        QuickAction.FreezeAll -> R.string.action_freeze_all
        QuickAction.UnfreezeAll -> R.string.action_unfreeze_all
        QuickAction.FreezeNonWhitelisted -> R.string.action_freeze_non_whitelisted
        QuickAction.Lock -> R.string.action_lock
        QuickAction.LockFreeze -> R.string.action_lock_freeze
    },
)
