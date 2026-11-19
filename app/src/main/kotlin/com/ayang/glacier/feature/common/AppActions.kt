package com.ayang.glacier.feature.common

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import com.ayang.glacier.R
import com.ayang.glacier.data.GlacierRepository
import com.ayang.glacier.domain.AppEntry
import com.ayang.glacier.domain.WorkingMode
import com.ayang.glacier.platform.UiFeedback
import com.ayang.glacier.ui.LocalGlassActive
import com.ayang.glacier.ui.LocalRootBackdrop
import com.ayang.glacier.ui.components.PageMetrics
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Delete
import top.yukonga.miuix.kmp.icon.glass.Extract
import top.yukonga.miuix.kmp.icon.glass.Info
import top.yukonga.miuix.kmp.icon.glass.Install
import top.yukonga.miuix.kmp.icon.glass.LockOutline
import top.yukonga.miuix.kmp.icon.glass.MultiSelect
import top.yukonga.miuix.kmp.icon.glass.Pin
import top.yukonga.miuix.kmp.icon.glass.Play
import top.yukonga.miuix.kmp.icon.glass.QuietMode
import top.yukonga.miuix.kmp.icon.glass.Security
import top.yukonga.miuix.kmp.icon.glass.Tags
import top.yukonga.miuix.kmp.icon.glass.Timer
import top.yukonga.miuix.kmp.icon.glass.UnlockOutline
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 单个应用可执行的操作。 */
enum class AppAction {
    Launch,
    Freeze,
    Unfreeze,
    TogglePin,
    ToggleWhitelist,
    ToggleHome,
    AppInfo,
    ExtractApk,
    Uninstall,
    Reinstall,
    EditTags,
    DeferredFreeze,
}

/**
 * 应用操作的执行器。
 *
 * 主页、应用页、API 入口共用同一套语义，避免"同一个冻结操作在三处表现不一致"。
 */
class AppActionRunner(
    private val context: Context,
    private val repository: GlacierRepository,
) {

    suspend fun run(entry: AppEntry, action: AppAction, onEditTags: (String) -> Unit = {}) {
        when (action) {
            AppAction.Launch -> launch(entry)
            AppAction.Freeze -> setFrozen(entry, true)
            AppAction.Unfreeze -> setFrozen(entry, false)
            AppAction.TogglePin -> {
                repository.setPinned(entry.packageName, !entry.pinned)
            }

            AppAction.ToggleWhitelist -> {
                repository.setWhitelisted(entry.packageName, !entry.whitelisted)
            }

            AppAction.ToggleHome -> {
                if (repository.snapshot.home.any { it.packageName == entry.packageName }) {
                    repository.removeFromHome(listOf(entry.packageName))
                } else {
                    repository.addToHome(listOf(entry.packageName))
                }
            }

            AppAction.AppInfo -> repository.openAppInfo(entry.packageName)
            AppAction.ExtractApk -> {
                val file = repository.extractApk(entry.packageName)
                if (file != null) {
                    UiFeedback.toast(context, context.getString(R.string.apk_extracted, file.absolutePath), true)
                } else {
                    UiFeedback.toast(context, R.string.operation_failed, context.getString(R.string.action_extract_apk))
                }
            }

            AppAction.Uninstall -> {
                val done = repository.uninstall(entry.packageName)
                if (!done) {
                    UiFeedback.toast(context, R.string.permission_denied)
                }
            }

            AppAction.Reinstall -> {
                if (!repository.reinstall(entry.packageName)) {
                    UiFeedback.toast(context, R.string.operation_failed, context.getString(R.string.action_reinstall))
                }
            }

            AppAction.EditTags -> onEditTags(entry.packageName)
            AppAction.DeferredFreeze -> Unit // 由调用方打开时间选择
        }
    }

    private suspend fun launch(entry: AppEntry) {
        if (entry.frozen) {
            repository.setFrozen(entry.packageName, false)
            if (repository.selectedMode() == WorkingMode.IslandFreeze ||
                repository.selectedMode() == WorkingMode.IslandSuspend
            ) {
                repository.freeze.island.awaitLaunchIntent(entry.packageName)
            }
        }
        if (!repository.launch(entry.packageName)) {
            UiFeedback.toast(context, R.string.activity_not_found)
        }
    }

    private suspend fun setFrozen(entry: AppEntry, frozen: Boolean) {
        val ok = repository.setFrozen(entry.packageName, frozen)
        if (ok) {
            val template = if (frozen) R.string.msg_freeze else R.string.msg_unfreeze
            UiFeedback.toast(context, context.getString(template, entry.label))
        } else {
            UiFeedback.toast(context, R.string.permission_denied)
        }
    }
}

/**
 * 应用操作单。
 *
 * 用官方 [GlassBottomSheet]；玻璃关闭时传 `backdrop = null`，
 * 库会走不透明回退，功能入口一个不少。
 *
 * **常驻组合**：不按 `entry` 是否为空做 early return，也不在调用方用 `if` 包裹 ——
 * 官方弹层的退出动画（[GlassBottomSheet] → `MiuixPopupUtils.kt:93-145`）要求组件在 `show = false`
 * 期间仍在组合树里。这里保留最后一份 `entry`，退场动画期间标题/行内容不闪空（规范 0.1 红线）。
 *
 * @param onDismissFinished 退场动画结束回调（官方参数）。独立窗口（ApiActivity）应在该回调里
 *   `finish()`，而不是在 [onDismiss] 里立刻销毁窗口，否则动画播不出来。
 */
@Composable
fun AppActionSheet(
    entry: AppEntry?,
    show: Boolean,
    inHome: Boolean,
    onDismiss: () -> Unit,
    onAction: (AppAction) -> Unit,
    onDismissFinished: (() -> Unit)? = null,
) {
    // 保留最后一次内容用于退场：entry 变 null 后仍按上一份 entry 排版。
    var lastEntry by remember { mutableStateOf<AppEntry?>(null) }
    if (entry != null) lastEntry = entry
    var lastInHome by remember { mutableStateOf(inHome) }
    if (entry != null) lastInHome = inHome

    val backdrop = LocalRootBackdrop.current.takeIf { LocalGlassActive.current }
    // 脚注左缘与 BasicComponent 行内文字对齐：行内边距就是官方 BasicComponentDefaults.InsideMargin。
    val rowInset = BasicComponentDefaults.InsideMargin.calculateLeftPadding(LocalLayoutDirection.current)

    GlassBottomSheet(
        onDismissRequest = onDismiss,
        show = show,
        backdrop = backdrop,
        title = lastEntry?.label,
        onDismissFinished = onDismissFinished,
    ) {
        lastEntry?.let { item ->
            Column(Modifier.fillMaxWidth()) {
                SheetActionRow(
                    icon = MiuixIcons.Glass.Play,
                    label = stringResource(R.string.action_launch),
                ) { onAction(AppAction.Launch) }

                if (item.frozen) {
                    SheetActionRow(
                        icon = MiuixIcons.Glass.UnlockOutline,
                        label = stringResource(R.string.action_unfreeze),
                    ) { onAction(AppAction.Unfreeze) }
                } else {
                    SheetActionRow(
                        icon = MiuixIcons.Glass.QuietMode,
                        label = stringResource(R.string.action_freeze),
                    ) { onAction(AppAction.Freeze) }
                }

                SheetActionRow(
                    icon = MiuixIcons.Glass.Timer,
                    label = stringResource(R.string.action_deferred_freeze),
                ) { onAction(AppAction.DeferredFreeze) }

                SheetActionRow(
                    icon = MiuixIcons.Glass.Pin,
                    label = stringResource(if (item.pinned) R.string.action_unpin else R.string.action_pin),
                ) { onAction(AppAction.TogglePin) }

                SheetActionRow(
                    icon = MiuixIcons.Glass.Security,
                    label = stringResource(
                        if (item.whitelisted) R.string.action_unwhitelist else R.string.action_whitelist,
                    ),
                ) { onAction(AppAction.ToggleWhitelist) }

                SheetActionRow(
                    icon = MiuixIcons.Glass.Tags,
                    label = stringResource(R.string.action_edit_tags),
                ) { onAction(AppAction.EditTags) }

                SheetActionRow(
                    icon = MiuixIcons.Glass.MultiSelect,
                    label = stringResource(
                        if (lastInHome) R.string.action_remove_from_home else R.string.action_add_to_home,
                    ),
                ) { onAction(AppAction.ToggleHome) }

                SheetActionRow(
                    icon = MiuixIcons.Glass.Info,
                    label = stringResource(R.string.action_app_info),
                ) { onAction(AppAction.AppInfo) }

                SheetActionRow(
                    icon = MiuixIcons.Glass.Extract,
                    label = stringResource(R.string.action_extract_apk),
                ) { onAction(AppAction.ExtractApk) }

                if (!item.isInstalled) {
                    SheetActionRow(
                        icon = MiuixIcons.Glass.Install,
                        label = stringResource(R.string.action_reinstall),
                    ) { onAction(AppAction.Reinstall) }
                } else {
                    SheetActionRow(
                        icon = MiuixIcons.Glass.Delete,
                        label = stringResource(R.string.action_uninstall),
                        destructive = true,
                    ) { onAction(AppAction.Uninstall) }
                }

                // 包名脚注与底部间距 12dp（规范 2.2）；横向 16dp 对齐 BasicComponentDefaults.InsideMargin，
                // 不额外加 24dp 外层 padding（弹层自身 insideMargin 已经是 24dp）。
                Spacer(Modifier.height(PageMetrics.SectionGap))
                Text(
                    text = item.packageName,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = rowInset),
                )
                Spacer(Modifier.height(PageMetrics.SectionGap))
            }
        }
    }
}

/** 锁屏动作走同一个出口，避免磁贴/API/页面各写一遍。 */
fun lockNow(repository: GlacierRepository, context: Context): Boolean {
    val ok = repository.lockScreen()
    if (!ok) UiFeedback.toast(context, R.string.permission_denied)
    return ok
}
