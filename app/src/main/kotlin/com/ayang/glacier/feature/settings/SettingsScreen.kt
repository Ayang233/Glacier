package com.ayang.glacier.feature.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ayang.glacier.GlacierApplication
import com.ayang.glacier.R
import com.ayang.glacier.domain.BackendState
import com.ayang.glacier.domain.QuickAction
import com.ayang.glacier.domain.ThemeMode
import com.ayang.glacier.domain.WorkingMode
import com.ayang.glacier.platform.auto.AutoFreezeScheduler
import com.ayang.glacier.platform.PackageTools
import com.ayang.glacier.platform.UiFeedback
import com.ayang.glacier.ui.LocalGlassActive
import com.ayang.glacier.ui.LocalRootBackdrop
import com.ayang.glacier.ui.components.GroupCard
import com.ayang.glacier.ui.components.PageFrame
import com.ayang.glacier.ui.components.PageMetrics
import com.ayang.glacier.ui.components.pageContentPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

/**
 * 设置页。
 *
 * 行一律用官方 Preference 组件：设置页是信息密度单位，整页玻璃会让文字对比度不稳。
 * 只有"离散选项"用官方玻璃下拉，这是 OS4 的既有范式。
 */
@Composable
fun SettingsScreen(onOpenAbout: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as GlacierApplication
    val preferences = app.preferences
    val freeze = app.freeze
    val repository = app.repository

    val scrollBehavior = MiuixScrollBehavior()
    val glassActive = LocalGlassActive.current
    val backdrop = LocalRootBackdrop.current.takeIf { glassActive }

    // 设置项本身是普通属性，读 revision 让页面在写入后重组。
    val revision = preferences.revision.intValue

    val modeState = produceState(initialValue = freeze.stateOf(), preferences.workingMode) {
        value = withContext(Dispatchers.IO) { freeze.stateOf() }
    }

    val iconPacks by produceState(initialValue = emptyList<Pair<String, String>>()) {
        value = withContext(Dispatchers.IO) {
            val packs = mutableListOf<Pair<String, String>>()
            PackageTools.installedApplications(context).forEach { info ->
                val hasFilter = runCatching {
                    context.packageManager.getResourcesForApplication(info.packageName)
                        .getIdentifier("appfilter", "xml", info.packageName) != 0
                }.getOrDefault(false)
                if (hasFilter) {
                    packs += info.packageName to runCatching {
                        info.loadLabel(context.packageManager).toString()
                    }.getOrDefault(info.packageName)
                }
            }
            packs
        }
    }

    var autoFreezeEnabled by remember(revision) { mutableStateOf(preferences.autoFreezeEnabled) }
    var requireUnlock by remember(revision) { mutableStateOf(preferences.requireUnlock) }
    var glassEnabled by remember(revision) { mutableStateOf(preferences.glassEnabled) }
    var grayscale by remember(revision) { mutableStateOf(preferences.grayscaleFrozenIcons) }
    var compact by remember(revision) { mutableStateOf(preferences.compactGrid) }
    var synthesize by remember(revision) { mutableStateOf(preferences.synthesizeAdaptiveIcons) }
    var fuzzy by remember(revision) { mutableStateOf(preferences.fuzzySearch) }
    var nineKey by remember(revision) { mutableStateOf(preferences.nineKeySearch) }
    var skipCharging by remember(revision) { mutableStateOf(preferences.skipWhileCharging) }
    var skipForeground by remember(revision) { mutableStateOf(preferences.skipForegroundApp) }
    var skipNotifying by remember(revision) { mutableStateOf(preferences.skipNotifyingApp) }
    var labelSize by remember(revision) { mutableStateOf(preferences.homeLabelSize) }
    var delay by remember(revision) { mutableStateOf(preferences.autoFreezeDelayMinutes.toFloat()) }

    // onClick 不是 @Composable 上下文，字符串必须在组合期取好。
    val appName = stringResource(R.string.app_name)
    val deviceOwnerCommand = stringResource(R.string.settings_device_owner_command)

    PageFrame(
        title = stringResource(R.string.nav_settings),
        subtitle = repository.deviceSummary(),
        scrollBehavior = scrollBehavior,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = pageContentPadding(padding),
            verticalArrangement = Arrangement.spacedBy(PageMetrics.SectionGap),
        ) {
            item { SmallTitle(stringResource(R.string.settings_general)) }
            item {
                GroupCard {
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
                        summary = if (preferences.workingMode.backend == com.ayang.glacier.domain.Backend.None) {
                            modeLabel(preferences.workingMode)
                        } else {
                            stringResource(
                                R.string.working_mode_summary,
                                modeLabel(preferences.workingMode),
                                backendSummary(modeState.value),
                            )
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

            item { SmallTitle(stringResource(R.string.settings_appearance)) }
            item {
                GroupCard {
                    GlassDropdownPreference(
                        onSelectedIndexChange = { index ->
                            preferences.updateThemeMode(ThemeMode.entries[index])
                            preferences.bumpRevision()
                        },
                        items = ThemeMode.entries.map { themeLabel(it) },
                        selectedIndex = ThemeMode.entries.indexOf(preferences.themeMode),
                        title = stringResource(R.string.app_theme),
                        backdrop = backdrop,
                    )
                    SwitchPreference(
                        checked = glassEnabled,
                        onCheckedChange = {
                            glassEnabled = it
                            preferences.updateGlass(it)
                        },
                        title = stringResource(R.string.glass_effect),
                        summary = stringResource(R.string.glass_effect_summary),
                    )
                    GlassDropdownPreference(
                        onSelectedIndexChange = { index ->
                            preferences.iconPack = if (index == 0) null else iconPacks[index - 1].first
                            preferences.bumpRevision()
                        },
                        items = listOf(stringResource(R.string.action_none)) + iconPacks.map { it.second },
                        selectedIndex = (iconPacks.indexOfFirst { it.first == preferences.iconPack } + 1)
                            .coerceAtLeast(0),
                        title = stringResource(R.string.icon_pack),
                        summary = stringResource(R.string.icon_pack_summary),
                        backdrop = backdrop,
                    )
                    SwitchPreference(
                        checked = grayscale,
                        onCheckedChange = {
                            grayscale = it
                            preferences.grayscaleFrozenIcons = it
                            preferences.bumpRevision()
                        },
                        title = stringResource(R.string.grayscale_icon),
                        summary = stringResource(R.string.grayscale_icon_summary),
                    )
                    SwitchPreference(
                        checked = compact,
                        onCheckedChange = {
                            compact = it
                            preferences.compactGrid = it
                            preferences.bumpRevision()
                        },
                        title = stringResource(R.string.compact_icon),
                        summary = stringResource(R.string.compact_icon_summary),
                    )
                    SwitchPreference(
                        checked = synthesize,
                        onCheckedChange = {
                            synthesize = it
                            preferences.synthesizeAdaptiveIcons = it
                            preferences.bumpRevision()
                        },
                        title = stringResource(R.string.synthesize_adaptive_icons),
                        summary = stringResource(R.string.synthesize_adaptive_icons_summary),
                    )
                    SliderPreference(
                        value = labelSize,
                        onValueChange = { labelSize = it },
                        onValueChangeFinished = {
                            preferences.homeLabelSize = labelSize
                            preferences.bumpRevision()
                        },
                        title = stringResource(R.string.home_font_size),
                        valueText = stringResource(R.string.settings_font_size_value, labelSize.toInt()),
                        valueRange = 11f..18f,
                        steps = 6,
                    )
                }
            }

            item { SmallTitle(stringResource(R.string.settings_search)) }
            item {
                GroupCard {
                    SwitchPreference(
                        checked = fuzzy,
                        onCheckedChange = {
                            fuzzy = it
                            preferences.fuzzySearch = it
                            preferences.bumpRevision()
                        },
                        title = stringResource(R.string.fuzzy_search),
                        summary = stringResource(R.string.fuzzy_search_summary),
                    )
                    SwitchPreference(
                        checked = nineKey,
                        onCheckedChange = {
                            nineKey = it
                            preferences.nineKeySearch = it
                            preferences.bumpRevision()
                        },
                        title = stringResource(R.string.nine_key_search),
                        summary = stringResource(R.string.nine_key_search_summary),
                    )
                }
            }

            item { SmallTitle(stringResource(R.string.settings_automation)) }
            item {
                GroupCard {
                    SwitchPreference(
                        checked = autoFreezeEnabled,
                        onCheckedChange = {
                            autoFreezeEnabled = it
                            preferences.autoFreezeEnabled = it
                            preferences.bumpRevision()
                            AutoFreezeScheduler.syncService(context)
                        },
                        title = stringResource(R.string.auto_freeze),
                        summary = stringResource(R.string.auto_freeze_summary),
                    )
                    SliderPreference(
                        value = delay,
                        onValueChange = { delay = it },
                        onValueChangeFinished = {
                            preferences.autoFreezeDelayMinutes = delay.toLong()
                            preferences.bumpRevision()
                        },
                        title = stringResource(R.string.auto_freeze_delay),
                        summary = stringResource(R.string.auto_freeze_delay_summary, delay.toInt()),
                        valueText = stringResource(R.string.settings_minutes_value, delay.toInt()),
                        valueRange = 0f..30f,
                        steps = 29,
                        enabled = autoFreezeEnabled,
                    )
                    SwitchPreference(
                        checked = skipCharging,
                        onCheckedChange = {
                            skipCharging = it
                            preferences.skipWhileCharging = it
                        },
                        title = stringResource(R.string.skip_while_charging),
                        enabled = autoFreezeEnabled,
                    )
                    SwitchPreference(
                        checked = skipForeground,
                        onCheckedChange = {
                            skipForeground = it
                            preferences.skipForegroundApp = it
                        },
                        title = stringResource(R.string.skip_foreground_app),
                        enabled = autoFreezeEnabled,
                    )
                    SwitchPreference(
                        checked = skipNotifying,
                        onCheckedChange = {
                            skipNotifying = it
                            preferences.skipNotifyingApp = it
                        },
                        title = stringResource(R.string.skip_notifying_app),
                        enabled = autoFreezeEnabled,
                    )
                    GlassDropdownPreference(
                        onSelectedIndexChange = { index ->
                            preferences.tileAction = QuickAction.entries[index]
                            preferences.bumpRevision()
                        },
                        items = QuickAction.entries.map { actionLabel(it) },
                        selectedIndex = QuickAction.entries.indexOf(preferences.tileAction),
                        title = stringResource(R.string.tile_action),
                        backdrop = backdrop,
                    )
                    GlassDropdownPreference(
                        onSelectedIndexChange = { index ->
                            preferences.shortcutAction = QuickAction.entries[index]
                            preferences.bumpRevision()
                        },
                        items = QuickAction.entries.map { actionLabel(it) },
                        selectedIndex = QuickAction.entries.indexOf(preferences.shortcutAction),
                        title = stringResource(R.string.dynamic_shortcut_action),
                        backdrop = backdrop,
                    )
                }
            }

            item { SmallTitle(stringResource(R.string.settings_system)) }
            item {
                GroupCard {
                    // 这里只保留"跳系统设置 / 复制命令"的入口。
                    // 自动冻结已在「自动化」节完整配置过，不再重复一个只弹提示的 ArrowPreference。
                    ArrowPreference(
                        title = stringResource(R.string.settings_notification_access),
                        summary = stringResource(R.string.settings_notification_access_summary),
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_usage_access),
                        summary = stringResource(R.string.settings_usage_access_summary),
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                    )
                    ArrowPreference(
                        title = stringResource(R.string.settings_device_owner),
                        summary = stringResource(R.string.settings_device_owner_summary),
                        onClick = {
                            UiFeedback.copy(
                                context,
                                appName,
                                deviceOwnerCommand,
                            )
                            UiFeedback.toast(context, R.string.ok)
                        },
                    )
                }
            }

            item { SmallTitle(stringResource(R.string.settings_about)) }
            item {
                GroupCard {
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
private fun themeLabel(mode: ThemeMode): String = stringResource(
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
