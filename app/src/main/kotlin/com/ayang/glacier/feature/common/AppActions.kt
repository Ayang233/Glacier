package com.ayang.glacier.feature.common

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Delete
import top.yukonga.miuix.kmp.icon.glass.Extract
import top.yukonga.miuix.kmp.icon.glass.Info
import top.yukonga.miuix.kmp.icon.glass.Install
import top.yukonga.miuix.kmp.icon.glass.MultiSelect
import top.yukonga.miuix.kmp.icon.glass.Pin
import top.yukonga.miuix.kmp.icon.glass.Play
import top.yukonga.miuix.kmp.icon.glass.QuietMode
import top.yukonga.miuix.kmp.icon.glass.Security
import top.yukonga.miuix.kmp.icon.glass.Tags
import top.yukonga.miuix.kmp.icon.glass.Timer
import top.yukonga.miuix.kmp.icon.glass.UnlockOutline
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

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
 * 操作单里的一行（渲染前的纯数据，避免在 LazyColumn 的 item 作用域里重复拼装条件）。
 */
private class SheetActionSpec(
    val icon: ImageVector,
    val label: String,
    val action: AppAction,
    val destructive: Boolean = false,
)

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
 * **大字体 / 横屏**：行容器是 [LazyColumn]（官方 BottomSheet 内容写法
 * `example/component/BottomSheetSection.kt:209-213`，`scrollEndHaptic + overScrollVertical`），
 * 10 行在 2.0x 字体下超过弹层可用高度时可以滚动，不再是 `Column` 被弹层 `heightIn(max)` 裁掉、
 * 底部若干行永远点不到（`BottomSheetContentLayout.kt:653-700` 的 `heightIn(max = windowHeight - topInset)`）。
 *
 * **按压反馈**：行分组用 [Card]（官方 BottomSheet 的 Card 分组写法，
 * `BottomSheetSection.kt:219-237`）。`AppRow`/`SheetActionRow` 内部的 `BasicComponent` 是唯一
 * indication 来源；Card 的 `squircleSurface` 会把矩形 MiuixIndication 裁成圆角
 * （`miuix-squircle/SquircleBackground.kt:124-165`），不会出现正方形按压层。
 *
 * @param unsupportedActions 当前入口实现不了的动作。它们**不渲染**，避免"看起来可点但无效"
 *   （任务书 §7.4）。默认空集合，保持 Home / API 入口的既有行为。
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
    unsupportedActions: Set<AppAction> = emptySet(),
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
    // 弹层内容贴屏幕底边（glass 延伸到边缘），滚动内容自己让出导航栏高度（官方 BottomSheet KDoc：
    // "Apply navigation-bar padding inside scrollable content"；`BottomSheetSection.kt:260-265` 同一做法）。
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    GlassBottomSheet(
        onDismissRequest = onDismiss,
        show = show,
        backdrop = backdrop,
        title = lastEntry?.label,
        onDismissFinished = onDismissFinished,
    ) {
        lastEntry?.let { item ->
            // 行数据在组合期一次算好（label 走 stringResource），item 里只做纯渲染。
            val rows = listOfNotNull(
                SheetActionSpec(MiuixIcons.Glass.Play, stringResource(R.string.action_launch), AppAction.Launch),
                if (item.frozen) {
                    SheetActionSpec(
                        MiuixIcons.Glass.UnlockOutline,
                        stringResource(R.string.action_unfreeze),
                        AppAction.Unfreeze,
                    )
                } else {
                    SheetActionSpec(
                        MiuixIcons.Glass.QuietMode,
                        stringResource(R.string.action_freeze),
                        AppAction.Freeze,
                    )
                },
                if (AppAction.DeferredFreeze !in unsupportedActions) {
                    SheetActionSpec(
                        MiuixIcons.Glass.Timer,
                        stringResource(R.string.action_deferred_freeze),
                        AppAction.DeferredFreeze,
                    )
                } else {
                    null
                },
                SheetActionSpec(
                    MiuixIcons.Glass.Pin,
                    stringResource(if (item.pinned) R.string.action_unpin else R.string.action_pin),
                    AppAction.TogglePin,
                ),
                SheetActionSpec(
                    MiuixIcons.Glass.Security,
                    stringResource(
                        if (item.whitelisted) R.string.action_unwhitelist else R.string.action_whitelist,
                    ),
                    AppAction.ToggleWhitelist,
                ),
                if (AppAction.EditTags !in unsupportedActions) {
                    SheetActionSpec(
                        MiuixIcons.Glass.Tags,
                        stringResource(R.string.action_edit_tags),
                        AppAction.EditTags,
                    )
                } else {
                    null
                },
                SheetActionSpec(
                    MiuixIcons.Glass.MultiSelect,
                    stringResource(
                        if (lastInHome) R.string.action_remove_from_home else R.string.action_add_to_home,
                    ),
                    AppAction.ToggleHome,
                ),
                SheetActionSpec(
                    MiuixIcons.Glass.Info,
                    stringResource(R.string.action_app_info),
                    AppAction.AppInfo,
                ),
                SheetActionSpec(
                    MiuixIcons.Glass.Extract,
                    stringResource(R.string.action_extract_apk),
                    AppAction.ExtractApk,
                ),
                if (!item.isInstalled) {
                    SheetActionSpec(
                        MiuixIcons.Glass.Install,
                        stringResource(R.string.action_reinstall),
                        AppAction.Reinstall,
                    )
                } else {
                    SheetActionSpec(
                        MiuixIcons.Glass.Delete,
                        stringResource(R.string.action_uninstall),
                        AppAction.Uninstall,
                        destructive = true,
                    )
                },
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .scrollEndHaptic()
                    .overScrollVertical(),
                contentPadding = PaddingValues(bottom = navigationBarBottom + PageMetrics.SectionGap),
            ) {
                item(key = "actions") {
                    Card(Modifier.fillMaxWidth()) {
                        rows.forEach { row ->
                            SheetActionRow(
                                icon = row.icon,
                                label = row.label,
                                destructive = row.destructive,
                            ) { onAction(row.action) }
                        }
                    }
                }

                item(key = "package-name") {
                    // 包名脚注：与行内文字左对齐（BasicComponentDefaults.InsideMargin），上距卡片 12dp。
                    Spacer(Modifier.height(PageMetrics.SectionGap))
                    Text(
                        text = item.packageName,
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = rowInset),
                    )
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
