package com.ayang.glacier.feature.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.CapturedAnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ayang.glacier.GlacierApplication
import com.ayang.glacier.R
import com.ayang.glacier.domain.QuickAction
import com.ayang.glacier.domain.ThemeMode
import com.ayang.glacier.platform.PackageTools
import com.ayang.glacier.platform.Platform
import com.ayang.glacier.platform.PredictiveBack
import com.ayang.glacier.platform.UiFeedback
import com.ayang.glacier.platform.auto.AutoFreezeScheduler
import com.ayang.glacier.platform.findActivity
import com.ayang.glacier.platform.recreateWithoutTransition
import com.ayang.glacier.ui.LocalGlassActive
import com.ayang.glacier.ui.LocalRootBackdrop
import com.ayang.glacier.ui.components.GroupCard
import com.ayang.glacier.ui.components.PageMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.glass.GlassDropdownPreference
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference

/**
 * 设置二级页集合（R16）。
 *
 * 首页只留通用项与入口，四组设置各自独立成页，页面骨架统一用 [SettingsSubPage]
 * （官方二级页：`expandable = false` + 返回按钮 + 滚动条）。
 * 每页内部仍然是官方 Preference 组件；「依赖上一项的选项用 CapturedAnimatedVisibility 收起」
 * 这条官方范式（`SettingsPage.kt:257-265`）原样保留。
 */

/** 主题设置：外观全部设置项（含预测性返回动画）。 */
@Composable
fun ThemeSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as GlacierApplication
    val preferences = app.preferences
    val glassActive = LocalGlassActive.current
    val backdrop = LocalRootBackdrop.current.takeIf { glassActive }

    val revision = preferences.revision.intValue

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

    var glassEnabled by remember(revision) { mutableStateOf(preferences.glassEnabled) }
    var grayscale by remember(revision) { mutableStateOf(preferences.grayscaleFrozenIcons) }
    var compact by remember(revision) { mutableStateOf(preferences.compactGrid) }
    var synthesize by remember(revision) { mutableStateOf(preferences.synthesizeAdaptiveIcons) }
    var labelSize by remember(revision) { mutableStateOf(preferences.homeLabelSize) }
    var hideHomeLabels by remember(revision) { mutableStateOf(preferences.hideHomeLabels) }
    var predictiveBack by remember(revision) { mutableStateOf(preferences.predictiveBackEnabled) }

    val glassSupported = isRuntimeShaderSupported()

    SettingsSubPage(
        title = stringResource(R.string.settings_appearance),
        onBack = onBack,
    ) {
        item {
            GroupCard(modifier = Modifier.padding(horizontal = PageMetrics.Horizontal)) {
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
                // 设备不支持 RuntimeShader 时玻璃开关是"可点但无效"的控件；
                // 官方 example 同样只在支持时展示玻璃/模糊开关（SettingsPage.kt:210-229）。
                if (glassSupported) {
                    SwitchPreference(
                        checked = glassEnabled,
                        onCheckedChange = {
                            glassEnabled = it
                            preferences.updateGlass(it)
                        },
                        title = stringResource(R.string.glass_effect),
                        summary = stringResource(R.string.glass_effect_summary),
                    )
                }
                // 没有任何已安装图标包时，这一行的候选项只有 "None"，属于"看起来可点但选了没效果"。
                // 官方 example 对"候选依赖其它状态"的行一律用 CapturedAnimatedVisibility 收起。
                CapturedAnimatedVisibility(visible = iconPacks.isNotEmpty()) {
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
                }
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
                // 顺序保持「合成自适应图标 → 主页标签字号」。
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
                // 名称隐藏后字号就没有作用对象，按官方 dependent-option 范式收起这一行。
                CapturedAnimatedVisibility(visible = !hideHomeLabels) {
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
                SwitchPreference(
                    checked = hideHomeLabels,
                    onCheckedChange = {
                        hideHomeLabels = it
                        preferences.hideHomeLabels = it
                        preferences.bumpRevision()
                    },
                    title = stringResource(R.string.hide_home_labels),
                    summary = stringResource(R.string.hide_home_labels_summary),
                )
                // 预测性返回动画：照官方 example 的做法（改进程内 ApplicationInfo 标志 + 重建
                // Activity）。关闭只是不再有系统跟手预览，返回手势仍会触发 OnBackPressedDispatcher，
                // MIUIX NavDisplay 照常播自己的 pop 转场，动画不会消失。
                if (PredictiveBack.isSupported) {
                    SwitchPreference(
                        checked = predictiveBack,
                        onCheckedChange = { enabled ->
                            predictiveBack = enabled
                            preferences.predictiveBackEnabled = enabled
                            preferences.bumpRevision()
                            context.findActivity()?.recreateWithoutTransition()
                        },
                        title = stringResource(R.string.predictive_back),
                        summary = stringResource(R.string.predictive_back_summary),
                    )
                }
            }
        }
    }
}

/** 搜索：模糊搜索 / T9。 */
@Composable
fun SearchSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val preferences = (context.applicationContext as GlacierApplication).preferences
    val revision = preferences.revision.intValue
    var fuzzy by remember(revision) { mutableStateOf(preferences.fuzzySearch) }
    var nineKey by remember(revision) { mutableStateOf(preferences.nineKeySearch) }

    SettingsSubPage(
        title = stringResource(R.string.settings_search),
        onBack = onBack,
    ) {
        item {
            GroupCard(modifier = Modifier.padding(horizontal = PageMetrics.Horizontal)) {
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
    }
}

/** 自动化：自动冻结、延迟与跳过规则、磁贴/快捷方式动作。 */
@Composable
fun AutomationSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as GlacierApplication
    val preferences = app.preferences
    val glassActive = LocalGlassActive.current
    val backdrop = LocalRootBackdrop.current.takeIf { glassActive }
    val revision = preferences.revision.intValue

    var autoFreezeEnabled by remember(revision) { mutableStateOf(preferences.autoFreezeEnabled) }
    var skipCharging by remember(revision) { mutableStateOf(preferences.skipWhileCharging) }
    var skipForeground by remember(revision) { mutableStateOf(preferences.skipForegroundApp) }
    var skipNotifying by remember(revision) { mutableStateOf(preferences.skipNotifyingApp) }
    var delay by remember(revision) { mutableStateOf(preferences.autoFreezeDelayMinutes.toFloat()) }

    // 权限/能力状态不是 Compose 状态：从系统设置返回时在 ON_RESUME 重新读一次，
    // 否则会出现"刚授权完，开关仍是禁用态"或相反。
    var accessEpoch by remember { mutableStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { accessEpoch++ }
    val hasUsageAccess = remember(accessEpoch, context) { Platform.hasUsageStatsAccess(context) }
    val hasNotificationAccess = remember(accessEpoch, context) {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }

    SettingsSubPage(
        title = stringResource(R.string.settings_automation),
        onBack = onBack,
    ) {
        item {
            GroupCard(modifier = Modifier.padding(horizontal = PageMetrics.Horizontal)) {
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
                // 依赖"自动冻结已开启"的项整组收起，而不是置灰后不给任何解释：
                // 官方的 dependent-option 范式就是 CapturedAnimatedVisibility(visible = parent)。
                CapturedAnimatedVisibility(visible = autoFreezeEnabled) {
                    Column {
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
                        )
                        SwitchPreference(
                            checked = skipCharging,
                            onCheckedChange = {
                                skipCharging = it
                                preferences.skipWhileCharging = it
                                preferences.bumpRevision()
                            },
                            title = stringResource(R.string.skip_while_charging),
                        )
                        // 这两个开关各依赖一项系统权限；未授权时 AutoFreezeWorker 拿不到前台/通知数据，
                        // 开关会"点了但没效果"。按 InstallerX 成熟做法：禁用开关 + summary 说明缺哪项权限。
                        SwitchPreference(
                            checked = skipForeground,
                            onCheckedChange = {
                                skipForeground = it
                                preferences.skipForegroundApp = it
                                preferences.bumpRevision()
                            },
                            title = stringResource(R.string.skip_foreground_app),
                            summary = if (hasUsageAccess) null else {
                                stringResource(R.string.settings_skip_foreground_needs_access)
                            },
                            enabled = hasUsageAccess,
                        )
                        SwitchPreference(
                            checked = skipNotifying,
                            onCheckedChange = {
                                skipNotifying = it
                                preferences.skipNotifyingApp = it
                                preferences.bumpRevision()
                            },
                            title = stringResource(R.string.skip_notifying_app),
                            summary = if (hasNotificationAccess) null else {
                                stringResource(R.string.settings_skip_notifying_needs_access)
                            },
                            enabled = hasNotificationAccess,
                        )
                    }
                }
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
    }
}

/** 系统权限：通知使用权 / 使用情况访问权 / 设备所有者命令。 */
@Composable
fun SystemSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val preferences = (context.applicationContext as GlacierApplication).preferences
    val appName = stringResource(R.string.app_name)
    val deviceOwnerCommand = stringResource(R.string.settings_device_owner_command)

    SettingsSubPage(
        title = stringResource(R.string.settings_system),
        onBack = onBack,
    ) {
        item {
            GroupCard(modifier = Modifier.padding(horizontal = PageMetrics.Horizontal)) {
                // 这里只保留"跳系统设置 / 复制命令"的入口。
                // 自动冻结已在「自动化」页完整配置过，不再重复一个只弹提示的 ArrowPreference。
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
                        UiFeedback.copy(context, appName, deviceOwnerCommand)
                        UiFeedback.toast(context, R.string.settings_device_owner_copied)
                    },
                )
            }
        }
    }
}
