package com.ayang.glacier.feature.common

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ayang.glacier.R
import com.ayang.glacier.data.GlacierRepository
import com.ayang.glacier.domain.AppEntry
import com.ayang.glacier.domain.WorkingMode
import com.ayang.glacier.platform.UiFeedback
import com.ayang.glacier.ui.LocalGlassActive
import com.ayang.glacier.ui.LocalRootBackdrop
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
 */
@Composable
fun AppActionSheet(
    entry: AppEntry?,
    show: Boolean,
    inHome: Boolean,
    onDismiss: () -> Unit,
    onAction: (AppAction) -> Unit,
) {
    if (entry == null) return
    val backdrop = LocalRootBackdrop.current.takeIf { LocalGlassActive.current }

    GlassBottomSheet(
        onDismissRequest = onDismiss,
        show = show,
        backdrop = backdrop,
        title = entry.label,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SheetActionRow(
                icon = MiuixIcons.Glass.Play,
                label = stringResource(R.string.action_launch),
            ) { onAction(AppAction.Launch) }

            if (entry.frozen) {
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
                label = stringResource(if (entry.pinned) R.string.action_unpin else R.string.action_pin),
            ) { onAction(AppAction.TogglePin) }

            SheetActionRow(
                icon = MiuixIcons.Glass.Security,
                label = stringResource(
                    if (entry.whitelisted) R.string.action_unwhitelist else R.string.action_whitelist,
                ),
            ) { onAction(AppAction.ToggleWhitelist) }

            SheetActionRow(
                icon = MiuixIcons.Glass.Tags,
                label = stringResource(R.string.action_edit_tags),
            ) { onAction(AppAction.EditTags) }

            SheetActionRow(
                icon = MiuixIcons.Glass.MultiSelect,
                label = stringResource(
                    if (inHome) R.string.action_remove_from_home else R.string.action_add_to_home,
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

            if (!entry.isInstalled) {
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

            Spacer(Modifier.height(8.dp))
            Text(
                text = entry.packageName,
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(12.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

/** 锁屏动作走同一个出口，避免磁贴/API/页面各写一遍。 */
fun lockNow(repository: GlacierRepository, context: Context): Boolean {
    val ok = repository.lockScreen()
    if (!ok) UiFeedback.toast(context, R.string.permission_denied)
    return ok
}
