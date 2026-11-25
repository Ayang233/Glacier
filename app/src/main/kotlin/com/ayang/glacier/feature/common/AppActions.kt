package com.ayang.glacier.feature.common

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import com.ayang.glacier.ui.rememberSheetBottomPadding
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

    /**
     * 点开应用：**冻结态先解冻再启动**，这是"冻结"这个产品语义的闭环——
     * 主页上的图标就是入口，用户不该为了打开一个应用先去菜单里解冻。
     *
     * 解冻失败就直接停下并如实提示：此时硬启动只会得到一个 `ActivityNotFound`，
     * 用户看到的是"应用打不开"，而不是"没权限解冻"。
     */
    private suspend fun launch(entry: AppEntry) {
        if (entry.frozen) {
            if (!repository.setFrozen(entry.packageName, false)) {
                UiFeedback.toast(context, R.string.permission_denied)
                return
            }
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
 * 主页多选态的长按菜单：**只留与"一批应用"有关的动作**，顺序即渲染顺序。
 *
 * 单应用菜单里的启动 / 定时 / 置顶 / 白名单 / 添加到主屏幕都要落到"某一个应用"，
 * 批量语义不成立（或语义很怪），所以这里不出现。标签分组在批量语境下走标签多选弹层。
 */
fun homeSelectionMenuActions(): List<AppAction> = listOf(
    AppAction.Freeze,
    AppAction.Unfreeze,
    AppAction.EditTags,
    AppAction.CopyPackageName,
    AppAction.RemoveFromHome,
    AppAction.UnfreezeAndRemoveFromHome,
)

/**
 * 菜单里的一行（渲染前的纯数据）。
 *
 * sheet 与锚点菜单共用这一份文案/图标表：同一个动作在两个容器里
 * 不能出现两种措辞。`label` 在组合期用 `stringResource` 取好后再构造。
 */
@Immutable
class ActionRowSpec(
    val icon: ImageVector,
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/** 动作 → 图标 / 文案 / 可点性。语境文案与禁用态都在这里收敛，两个菜单容器共用。 */
@Composable
fun actionSpecOf(
    action: AppAction,
    entry: AppEntry,
    onAction: (AppAction) -> Unit,
): ActionRowSpec {
    val spec = when (action) {
        AppAction.Launch ->
            ActionRowSpec(MiuixIcons.Glass.Play, stringResource(R.string.action_launch), onClick = { onAction(action) })
        AppAction.Freeze ->
            ActionRowSpec(MiuixIcons.Glass.QuietMode, stringResource(R.string.action_freeze), onClick = { onAction(action) })
        AppAction.Unfreeze ->
            ActionRowSpec(MiuixIcons.Glass.UnlockOutline, stringResource(R.string.action_unfreeze), onClick = { onAction(action) })
        AppAction.DeferredFreeze ->
            ActionRowSpec(MiuixIcons.Glass.Timer, stringResource(R.string.action_deferred_freeze), onClick = { onAction(action) })
        AppAction.TogglePin -> ActionRowSpec(
            MiuixIcons.Glass.Pin,
            stringResource(if (entry.pinned) R.string.action_unpin else R.string.action_pin),
            onClick = { onAction(action) },
        )
        AppAction.ToggleWhitelist -> ActionRowSpec(
            MiuixIcons.Glass.Security,
            stringResource(if (entry.whitelisted) R.string.action_unwhitelist else R.string.action_whitelist),
            onClick = { onAction(action) },
        )
        AppAction.EditTags ->
            ActionRowSpec(MiuixIcons.Glass.Tags, stringResource(R.string.action_edit_tags), onClick = { onAction(action) })
        AppAction.PinShortcut ->
            ActionRowSpec(MiuixIcons.Glass.HomeOutline, stringResource(R.string.action_add_pin_shortcut), onClick = { onAction(action) })
        AppAction.CopyPackageName ->
            ActionRowSpec(MiuixIcons.Glass.Copy, stringResource(R.string.action_copy_package), onClick = { onAction(action) })
        AppAction.AddToHome ->
            ActionRowSpec(MiuixIcons.Glass.Add, stringResource(R.string.action_add_to_home), onClick = { onAction(action) })
        AppAction.RemoveFromHome ->
            ActionRowSpec(MiuixIcons.Glass.Remove, stringResource(R.string.action_remove_from_home), onClick = { onAction(action) })
        AppAction.UnfreezeAndRemoveFromHome ->
            ActionRowSpec(MiuixIcons.Glass.Unpin, stringResource(R.string.action_unfreeze_and_remove), onClick = { onAction(action) })
        AppAction.AppInfo ->
            ActionRowSpec(MiuixIcons.Glass.Info, stringResource(R.string.action_app_info), onClick = { onAction(action) })
        AppAction.ExtractApk ->
            ActionRowSpec(MiuixIcons.Glass.Extract, stringResource(R.string.action_extract_apk), onClick = { onAction(action) })
    }
    return spec
}

/**
 * 应用操作单（底部弹层）。
 *
 * 主页 / 应用页的长按菜单在 R13 已改成官方锚点浮层（`GlassTransformPopup`），
 * 这里只服务没有页面锚点的入口——独立窗口的 API Activity（`ApiActivity`）。
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
 * `example/component/BottomSheetSection.kt:209-213`，`scrollEndHaptic（弹层自己处理嵌套滚动与上沿延伸）`），
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
    title: String? = null,
    onDismissFinished: (() -> Unit)? = null,
) {
    // 保留最后一次内容用于退场：entry 变 null 后仍按上一份 entry / 清单排版。
    var lastEntry by remember { mutableStateOf<AppEntry?>(null) }
    if (entry != null) lastEntry = entry
    var lastActions by remember { mutableStateOf(actions) }
    if (entry != null) lastActions = actions
    // 标题可以由调用方覆盖（多选态是「已选择：N」，单应用用应用名）。
    // 同样只保留最后一份，退场动画期间不会闪空。
    var lastTitle by remember { mutableStateOf<String?>(null) }
    if (title != null) lastTitle = title

    val backdrop = LocalRootBackdrop.current.takeIf { LocalGlassActive.current }
    // 弹层内容自己让出系统底部安全区（官方 BottomSheet KDoc 的同款做法）。
    // [rememberSheetBottomPadding] 在导航栏之外并入手势区与屏幕圆角。
    val sheetBottom = rememberSheetBottomPadding(PageMetrics.SectionGap)

    GlassBottomSheet(
        onDismissRequest = onDismiss,
        show = show,
        backdrop = backdrop,
        title = lastTitle ?: lastEntry?.label,
        onDismissFinished = onDismissFinished,
    ) { innerPadding ->
        lastEntry?.let { item ->
            // 行数据在组合期一次算好（label 走 stringResource），item 里只做纯渲染。
            val rows = lastActions.map { actionSpecOf(it, item, onAction) }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .scrollEndHaptic(),
                contentPadding = PaddingValues(
                    // 新版标题栏覆盖在内容之上（progressiveBlur）：首屏让出实测标题高度。
                    top = innerPadding.calculateTopPadding(),
                    bottom = sheetBottom,
                ),
            ) {
                item(key = "actions") {
                    GlassCard(Modifier.fillMaxWidth()) {
                        rows.forEach { row ->
                            SheetActionRow(
                                icon = row.icon,
                                label = row.label,
                                enabled = row.enabled,
                                onClick = row.onClick,
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

/**
 * 多选菜单的批量执行器。
 *
 * 与 [AppActionRunner] 分开：那边每个动作都绑定**一个** [AppEntry]（启动 / 置顶 / 定时…），
 * 这边的每个动作都作用于一个集合，"是否需要退出多选"用返回值表达而不是副作用。
 *
 * 只处理 [homeSelectionMenuActions] 里除「标签分组」以外的动作——标签分组要打开标签多选弹层，
 * 属于 UI 决策，由调用方拦下。
 */
class SelectionActionRunner(
    private val context: Context,
    private val repository: GlacierRepository,
) {

    /** @return `true` = 这批应用已经不在主页，调用方应清空选区并退出多选。 */
    suspend fun run(entries: List<AppEntry>, action: AppAction): Boolean {
        if (entries.isEmpty()) return false
        val names = entries.map { it.packageName }
        return when (action) {
            AppAction.Freeze -> {
                val targets = entries.filter { !it.frozen }.map { it.packageName }
                if (targets.isNotEmpty()) {
                    reportFreezeBatch(context, repository.setFrozenBatch(targets, true), true)
                }
                false
            }

            AppAction.Unfreeze -> {
                val targets = entries.filter { it.frozen }.map { it.packageName }
                if (targets.isNotEmpty()) {
                    reportFreezeBatch(context, repository.setFrozenBatch(targets, false), false)
                }
                false
            }

            AppAction.CopyPackageName -> {
                // 一次复制整批，每行一个包名——脚本/终端可以直接按行读取。
                UiFeedback.copy(
                    context,
                    context.getString(R.string.action_copy_package),
                    names.joinToString("\n"),
                )
                UiFeedback.toast(context, context.getString(R.string.home_copied_packages, names.size))
                false
            }

            AppAction.RemoveFromHome -> {
                repository.removeFromHome(names)
                UiFeedback.toast(context, context.getString(R.string.home_removed_many, names.size))
                true
            }

            AppAction.UnfreezeAndRemoveFromHome -> {
                // 先解冻再移除：解冻失败就整件事中止（不静默移出一批"看不见但被冻着"的应用）。
                val frozen = entries.filter { it.frozen }.map { it.packageName }
                if (frozen.isNotEmpty()) {
                    val outcome = repository.setFrozenBatch(frozen, false)
                    val failed = outcome is FreezeOutcome.Denied ||
                        (outcome as? FreezeOutcome.Applied)?.count == 0
                    if (failed) {
                        UiFeedback.toast(context, R.string.permission_denied)
                        return false
                    }
                }
                repository.removeFromHome(names)
                UiFeedback.toast(context, context.getString(R.string.home_removed_many, names.size))
                true
            }

            else -> false
        }
    }
}
