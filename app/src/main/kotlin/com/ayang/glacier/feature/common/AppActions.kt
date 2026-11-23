package com.ayang.glacier.feature.common

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.ayang.glacier.R
import com.ayang.glacier.data.GlacierRepository
import com.ayang.glacier.domain.AppEntry
import com.ayang.glacier.domain.WorkingMode
import com.ayang.glacier.platform.UiFeedback
import com.ayang.glacier.platform.shortcut.ShortcutBridge
import com.ayang.glacier.system.FreezeOutcome
import com.ayang.glacier.ui.LocalGlassActive
import com.ayang.glacier.ui.LocalRootBackdrop
import com.ayang.glacier.ui.components.PageMetrics
import com.ayang.glacier.ui.rememberAppSafePadding
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.glass.GlassCard
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Add
import top.yukonga.miuix.kmp.icon.glass.Copy
import top.yukonga.miuix.kmp.icon.glass.Extract
import top.yukonga.miuix.kmp.icon.glass.HomeOutline
import top.yukonga.miuix.kmp.icon.glass.Info
import top.yukonga.miuix.kmp.icon.glass.Pin
import top.yukonga.miuix.kmp.icon.glass.Play
import top.yukonga.miuix.kmp.icon.glass.QuietMode
import top.yukonga.miuix.kmp.icon.glass.Remove
import top.yukonga.miuix.kmp.icon.glass.Security
import top.yukonga.miuix.kmp.icon.glass.Tags
import top.yukonga.miuix.kmp.icon.glass.Timer
import top.yukonga.miuix.kmp.icon.glass.UnlockOutline
import top.yukonga.miuix.kmp.icon.glass.Unpin
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 单个应用可执行的操作。
 *
 * 枚举只描述"能做什么"，**哪一页提供哪些、按什么顺序**由 [appsMenuActions] /
 * [homeMenuActions] / [apiMenuActions] 三份清单决定：菜单内容变成数据，弹层只负责渲染。
 */
enum class AppAction {
    Launch,
    Freeze,
    Unfreeze,
    TogglePin,
    ToggleWhitelist,
    AddToHome,
    RemoveFromHome,
    UnfreezeAndRemoveFromHome,
    AppInfo,
    CopyPackageName,
    ExtractApk,
    PinShortcut,
    EditTags,
    DeferredFreeze,
}

/**
 * 应用操作的执行器。
 *
 * 主页、应用页、API 入口共用同一套语义，避免"同一个操作在三处表现不一致"。
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

            AppAction.AddToHome -> repository.addToHome(listOf(entry.packageName))
            AppAction.RemoveFromHome -> {
                repository.removeFromHome(listOf(entry.packageName))
                UiFeedback.toast(context, R.string.msg_removed_from_home, entry.label)
            }

            AppAction.UnfreezeAndRemoveFromHome -> {
                // 先解冻再移出主页：移出后这一行就不在主页列表里了，残留的冻结态会变成
                // "看不见但被冻着"的应用。解冻失败就整件事中止（不静默移出）。
                if (repository.setFrozen(entry.packageName, false)) {
                    repository.removeFromHome(listOf(entry.packageName))
                    UiFeedback.toast(context, R.string.msg_removed_from_home, entry.label)
                } else {
                    UiFeedback.toast(context, R.string.permission_denied)
                }
            }

            AppAction.AppInfo -> repository.openAppInfo(entry.packageName)
            AppAction.CopyPackageName -> {
                UiFeedback.copy(context, entry.label, entry.packageName)
                UiFeedback.toast(context, R.string.msg_package_copied)
            }

            AppAction.ExtractApk -> {
                val file = repository.extractApk(entry.packageName)
                if (file != null) {
                    UiFeedback.toast(context, context.getString(R.string.apk_extracted, file.absolutePath), true)
                } else {
                    UiFeedback.toast(context, R.string.operation_failed, context.getString(R.string.action_extract_apk))
                }
            }

            // 桌面快捷方式走系统 Pin 请求：首次会弹系统确认框，用户不确认不会创建。
            AppAction.PinShortcut -> ShortcutBridge.requestPin(context, entry.packageName)

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
 * 应用页长按菜单：**管理向**（应用信息 / 导包名 / 提包）。
 *
 * 这一页不提供冻结、置顶、标签这些"使用向"操作；冻结只在主页（FAB 一键冻结、
 * 长按里的冻结/解冻、多选后的批量冻结）。装卸包整体不做——这是应用商店的职责，
 * 塞进冻结工具里只会让人误点。顺序即渲染顺序。
 */
fun appsMenuActions(): List<AppAction> = listOf(
    AppAction.AppInfo,
    AppAction.CopyPackageName,
    AppAction.ExtractApk,
)

/**
 * 主页长按菜单：**使用向**（启动 / 冻结解冻 / 定时 / 归类 / 快捷方式 / 移出主页）。
 *
 * - 第 2 行是语境行：已冻结显示「解冻」，未冻结显示「冻结」，位置固定，
 *   不会因为状态不同让整张菜单变长变短；
 * - 「解冻并从主页移除」**只在已冻结时出现**：未冻结时它与「从主页移除」完全等价，
 *   两行并排只会让人犹豫点哪个。
 */
fun homeMenuActions(entry: AppEntry): List<AppAction> = buildList {
    add(AppAction.Launch)
    add(if (entry.frozen) AppAction.Unfreeze else AppAction.Freeze)
    add(AppAction.DeferredFreeze)
    add(AppAction.TogglePin)
    add(AppAction.ToggleWhitelist)
    add(AppAction.EditTags)
    add(AppAction.PinShortcut)
    add(AppAction.CopyPackageName)
    add(AppAction.RemoveFromHome)
    if (entry.frozen) add(AppAction.UnfreezeAndRemoveFromHome)
}

/**
 * API 深链 / 快捷方式入口的菜单：没有页面级 UI，所以不放标签编辑与定时任务
 * （两者的后续交互都要落在页面里），其余保持"对一个包能做的所有事"。
 */
fun apiMenuActions(entry: AppEntry, inHome: Boolean): List<AppAction> = buildList {
    add(AppAction.Launch)
    add(if (entry.frozen) AppAction.Unfreeze else AppAction.Freeze)
    add(AppAction.TogglePin)
    add(AppAction.ToggleWhitelist)
    add(if (inHome) AppAction.RemoveFromHome else AppAction.AddToHome)
    add(AppAction.CopyPackageName)
    add(AppAction.AppInfo)
    add(AppAction.ExtractApk)
}

/**
 * 操作单里的一行（渲染前的纯数据，避免在 LazyColumn 的 item 作用域里重复拼装条件）。
 */
private class SheetActionSpec(
    val icon: ImageVector,
    val label: String,
    val action: AppAction,
)

/** 动作 → 图标 / 文案。语境文案（置顶↔取消置顶、白名单↔移出白名单）在这里收敛。 */
@Composable
private fun specOf(action: AppAction, entry: AppEntry): SheetActionSpec = when (action) {
    AppAction.Launch ->
        SheetActionSpec(MiuixIcons.Glass.Play, stringResource(R.string.action_launch), action)
    AppAction.Freeze ->
        SheetActionSpec(MiuixIcons.Glass.QuietMode, stringResource(R.string.action_freeze), action)
    AppAction.Unfreeze ->
        SheetActionSpec(MiuixIcons.Glass.UnlockOutline, stringResource(R.string.action_unfreeze), action)
    AppAction.DeferredFreeze ->
        SheetActionSpec(MiuixIcons.Glass.Timer, stringResource(R.string.action_deferred_freeze), action)
    AppAction.TogglePin -> SheetActionSpec(
        MiuixIcons.Glass.Pin,
        stringResource(if (entry.pinned) R.string.action_unpin else R.string.action_pin),
        action,
    )
    AppAction.ToggleWhitelist -> SheetActionSpec(
        MiuixIcons.Glass.Security,
        stringResource(if (entry.whitelisted) R.string.action_unwhitelist else R.string.action_whitelist),
        action,
    )
    AppAction.EditTags ->
        SheetActionSpec(MiuixIcons.Glass.Tags, stringResource(R.string.action_edit_tags), action)
    AppAction.PinShortcut ->
        SheetActionSpec(MiuixIcons.Glass.HomeOutline, stringResource(R.string.action_add_pin_shortcut), action)
    AppAction.CopyPackageName ->
        SheetActionSpec(MiuixIcons.Glass.Copy, stringResource(R.string.action_copy_package), action)
    AppAction.AddToHome ->
        SheetActionSpec(MiuixIcons.Glass.Add, stringResource(R.string.action_add_to_home), action)
    AppAction.RemoveFromHome ->
        SheetActionSpec(MiuixIcons.Glass.Remove, stringResource(R.string.action_remove_from_home), action)
    AppAction.UnfreezeAndRemoveFromHome ->
        SheetActionSpec(MiuixIcons.Glass.Unpin, stringResource(R.string.action_unfreeze_and_remove), action)
    AppAction.AppInfo ->
        SheetActionSpec(MiuixIcons.Glass.Info, stringResource(R.string.action_app_info), action)
    AppAction.ExtractApk ->
        SheetActionSpec(MiuixIcons.Glass.Extract, stringResource(R.string.action_extract_apk), action)
}

/**
 * 应用操作单。
 *
 * 用官方 [GlassBottomSheet]；玻璃关闭时传 `backdrop = null`，
 * 库会走不透明回退，功能入口一个不少。
 *
 * **菜单内容由调用方给**（[actions] 列表，顺序即渲染顺序）：主页 / 应用页 / API 入口的
 * 职责完全不同，把三份菜单写成三份数据，比在组件里堆 `if (inHome)` / `unsupportedActions`
 * 更不容易串味。包名不再作为脚注贴在弹层底部（不雅观），改由「导出到剪切板」这一行承担。
 *
 * **常驻组合**：不按 `entry` 是否为空做 early return，也不在调用方用 `if` 包裹 ——
 * 官方弹层的退出动画（[GlassBottomSheet] → `MiuixPopupUtils.kt:93-145`）要求组件在 `show = false`
 * 期间仍在组合树里。这里保留最后一份 `entry` 与 [actions]，退场动画期间标题/行内容不闪空
 * （规范 0.1 红线）。
 *
 * **大字体 / 横屏**：行容器是 [LazyColumn]（官方 BottomSheet 内容写法
 * `example/component/BottomSheetSection.kt:209-213`，`scrollEndHaptic + overScrollVertical`），
 * 10 行在 2.0x 字体下超过弹层可用高度时可以滚动，不再是 `Column` 被弹层 `heightIn(max)` 裁掉、
 * 底部若干行永远点不到（`BottomSheetContentLayout.kt:653-700` 的 `heightIn(max = windowHeight - topInset)`）。
 *
 * **按压反馈**：行分组用 [GlassCard]（玻璃弹层内的官方分组卡），
 * `SheetActionRow` 内部的 `BasicComponent` 是唯一 indication 来源。
 *
 * @param actions 本次要显示的动作，顺序即渲染顺序（见 [appsMenuActions] / [homeMenuActions] / [apiMenuActions]）。
 * @param onDismissFinished 退场动画结束回调（官方参数）。独立窗口（ApiActivity）应在该回调里
 *   `finish()`，而不是在 [onDismiss] 里立刻销毁窗口，否则动画播不出来。
 */
@Composable
fun AppActionSheet(
    entry: AppEntry?,
    show: Boolean,
    actions: List<AppAction>,
    onDismiss: () -> Unit,
    onAction: (AppAction) -> Unit,
    onDismissFinished: (() -> Unit)? = null,
) {
    // 保留最后一次内容用于退场：entry 变 null 后仍按上一份 entry / 清单排版。
    var lastEntry by remember { mutableStateOf<AppEntry?>(null) }
    if (entry != null) lastEntry = entry
    var lastActions by remember { mutableStateOf(actions) }
    if (entry != null) lastActions = actions

    val backdrop = LocalRootBackdrop.current.takeIf { LocalGlassActive.current }
    // 弹层内容自己让出系统底部安全区（官方 BottomSheet KDoc 的同款做法），
    // 值来自官方 safeInsets 契约，不再逐处手写 WindowInsets。
    val navigationBarBottom = rememberAppSafePadding().calculateBottomPadding()

    GlassBottomSheet(
        onDismissRequest = onDismiss,
        show = show,
        backdrop = backdrop,
        title = lastEntry?.label,
        onDismissFinished = onDismissFinished,
    ) {
        lastEntry?.let { item ->
            // 行数据在组合期一次算好（label 走 stringResource），item 里只做纯渲染。
            val rows = lastActions.map { specOf(it, item) }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .scrollEndHaptic()
                    .overScrollVertical(),
                contentPadding = PaddingValues(bottom = navigationBarBottom + PageMetrics.SectionGap),
            ) {
                item(key = "actions") {
                    GlassCard(Modifier.fillMaxWidth()) {
                        rows.forEach { row ->
                            SheetActionRow(
                                icon = row.icon,
                                label = row.label,
                                onClick = { onAction(row.action) },
                            )
                        }
                    }
                }
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

/**
 * 批量冻结 / 解冻的结果提示。
 *
 * 主页的批量动作（多选 / FAB）与 API 入口共用这一出口：同一操作在不同入口的措辞、
 * 是否提示权限失败必须一致，否则同一个"批量冻结"会有两种反馈（规范 §9 一致性）。
 */
fun reportFreezeBatch(
    context: Context,
    outcome: FreezeOutcome,
    frozen: Boolean,
) {
    when (outcome) {
        is FreezeOutcome.Applied -> {
            if (outcome.count == 0) {
                UiFeedback.toast(context, R.string.permission_denied)
            } else {
                val res = if (frozen) R.string.msg_freeze_many else R.string.msg_unfreeze_many
                UiFeedback.toast(context, context.getString(res, outcome.count.toString()))
            }
        }

        FreezeOutcome.Denied ->
            UiFeedback.toast(context, R.string.permission_denied)
    }
}
