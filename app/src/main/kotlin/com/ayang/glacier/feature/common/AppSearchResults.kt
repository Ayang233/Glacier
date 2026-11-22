package com.ayang.glacier.feature.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ayang.glacier.GlacierApplication
import com.ayang.glacier.R
import com.ayang.glacier.domain.AppEntry
import com.ayang.glacier.ui.LocalAppPageInsets
import com.ayang.glacier.ui.LocalShellBottomBarHeight
import com.ayang.glacier.ui.appPageHorizontalInsets
import com.ayang.glacier.ui.components.EmptyState
import com.ayang.glacier.ui.components.PageMetrics
import com.ayang.glacier.ui.pageBottomPadding
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.glass.GlassSearchOverlayDefaults
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/** 官方搜索结果行的上下间距：`example/IconPage.kt:104`（`SearchResultVerticalPadding = 4.dp`）。 */
internal val SearchResultVerticalPadding = 4.dp

/**
 * 应用列表行与搜索结果行**共用的唯一实现**。
 *
 * 行结构是官方 `Card`（交互版）+ `BasicComponent`：`Card` 负责分组表面**和**点击/长按，
 * 行内 `AppRow` 不带 `onClick`，因此只有一层按压反馈。
 *
 * 为什么由 `Card` 而不是行内 `BasicComponent` 承担手势：`BasicComponent` 只有 `clickable`，
 * 没有长按（`miuix-ui/basic/Component.kt:148-160`）；`Card` 的交互版有官方
 * `onClick` / `onLongPress` / `holdDownState` / `showIndication` 组合
 * （`miuix-ui/basic/Card.kt:87-154`），官方示例的长按写法就是它
 * （`example/component/CardSection.kt:110-143`：长按弹层 + `holdDownState` 保持按下态）。
 *
 * 语义：
 * - 点按 = 选中 / 取消选中该应用（[selected] 走 [AppRow] 的 squircle 描边，与主页网格同一套语言）；
 * - 长按 = 弹出该应用的操作单；弹层打开期间 [holdDown] 为 true，
 *   `Card` 用官方 `HoldDownObserver` 保持 0.10 alpha 的按下态，弹层退场后复位。
 *
 * `showIndication = true` + `pressFeedbackType = None`：反馈是 `Card` 的 squircle 表面
 * 裁切过的 `MiuixIndication` 叠层，与设置页 / 弹层行（`BasicComponent`）同一种按压语言，
 * 不引入第二套缩放反馈。
 */
@Composable
fun AppListCardRow(
    entry: AppEntry,
    inHome: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    holdDown: Boolean = false,
) {
    Card(
        modifier = modifier,
        showIndication = true,
        pressFeedbackType = PressFeedbackType.None,
        holdDownState = holdDown,
        onClick = onClick,
        onLongPress = onLongClick,
    ) {
        AppRow(
            packageName = entry.packageName,
            label = entry.label,
            summary = appRowSummary(entry, inHome),
            frozen = entry.frozen,
            selected = selected,
        )
    }
}

/**
 * 行摘要文案：包名 + 状态徽标。
 *
 * Apps 列表与搜索结果共用同一份文案，避免同一个应用在两处显示不同信息
 * （规范 §9「Home 和 Apps 统一性」、任务书 §7 第 8 项）。
 */
@Composable
fun appRowSummary(entry: AppEntry, inHome: Boolean): String {
    // 每个 stringResource 都直接在 @Composable 函数体里求值，不依赖 inline 构建器的组合上下文。
    val pinned = if (entry.pinned) " · " + stringResource(R.string.badge_pinned) else ""
    val whitelisted = if (entry.whitelisted) " · " + stringResource(R.string.badge_whitelisted) else ""
    val notOnHome = if (!inHome) " · " + stringResource(R.string.badge_not_on_home) else ""
    return entry.packageName + pinned + whitelisted + notOnHome
}

/**
 * 搜索浮层里的结果列表。
 *
 * [top.yukonga.miuix.kmp.glass.GlassSearchOverlay] 的内容槽从 y=0 开始铺，
 * 搜索条是叠在它上面的，所以结果列表必须自己让出「状态栏 + 折叠胶囊让位 + 搜索条」的高度
 * （官方 `IconPage.kt:537-540`：`safeInsets.top + searchTop + BarHeight + VerticalPadding * 2`）。
 *
 * 行结构直接复用 [AppListCardRow]，与 Apps 列表同一实现；本文件不做任何手写 `Row + clickable`。
 *
 * 手势语义与 Apps 列表完全一致（点按 = 选中、长按 = 操作单），[selected] / [onLongPick]
 * 由调用方按自己的多选状态传入；不在 Apps 页的多选场景（如主页搜索）不传这两个参数即可。
 *
 * @param onPick 点按一行。
 * @param selected 已选中的应用包名集合，用于画行内图标的 squircle 选中环。
 * @param onLongPick 长按一行；`null` = 该入口不支持长按。
 * @param holdDownPackage 操作单正在打开的应用：该行保持官方长按按下态，弹层退场后复位。
 */
@Composable
fun AppSearchResults(
    query: String,
    onPick: (AppEntry) -> Unit,
    listState: LazyListState = rememberLazyListState(),
    selected: Set<String> = emptySet(),
    onLongPick: ((AppEntry) -> Unit)? = null,
    holdDownPackage: String? = null,
) {
    val context = LocalContext.current
    val app = context.applicationContext as GlacierApplication
    val repository = app.repository
    // repository.apps(query) 读模糊搜索/九键/筛选/排序这些普通 SharedPreferences 属性；
    // 只靠 query + snapshot 做 key 会一直缓存旧结果，补 revision 才能在设置变更后刷新。
    val revision = app.preferences.revision.intValue
    val results = remember(query, repository.snapshot, revision) {
        if (query.isBlank()) emptyList() else repository.apps(query)
    }
    // 行摘要要标「不在主页」，这里与 Apps 列表同一份 home 集合，避免逐行 O(n) 查找。
    val homePackages = remember(repository.snapshot) {
        repository.snapshot.home.mapTo(HashSet()) { it.packageName }
    }

    // 与官方 `IconPage.kt:537-540` 同一公式：safeInsets.top + searchTop + 搜索条高度。
    // safeInsets 已经是「系统安全区 ∪ 宿主 padding」，不再自己拼 WindowInsets。
    val pageInsets = LocalAppPageInsets.current
    val topInset = pageInsets.safeInsets.asPaddingValues().calculateTopPadding()
    // 宽屏侧栏折叠成顶部胶囊时的让位（官方 `pageInsets.searchTop()`）。
    val searchTop = pageInsets.searchTop()
    val barSpace = GlassSearchOverlayDefaults.BarHeight + GlassSearchOverlayDefaults.VerticalPadding * 2
    // 底部留白走官方 pageBottomPadding：宿主底栏与系统手势区取较大值，不相加。
    val bottomPadding = pageBottomPadding(
        outerPadding = PaddingValues(bottom = LocalShellBottomBarHeight.current),
        extraBottom = PageMetrics.BottomGap,
    )

    if (results.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(Modifier.appPageHorizontalInsets())
                .padding(top = topInset + searchTop + barSpace),
        ) {
            EmptyState(
                text = stringResource(
                    if (query.isBlank()) R.string.search_hint else R.string.empty_search,
                ),
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .then(Modifier.appPageHorizontalInsets()),
        contentPadding = PaddingValues(
            top = topInset + searchTop + barSpace,
            bottom = bottomPadding.calculateBottomPadding(),
        ),
    ) {
        items(results, key = { it.packageName }) { entry ->
            AppListCardRow(
                entry = entry,
                inHome = entry.packageName in homePackages,
                onClick = { onPick(entry) },
                selected = entry.packageName in selected,
                onLongClick = onLongPick?.let { pick -> { pick(entry) } },
                holdDown = entry.packageName == holdDownPackage,
                // 官方搜索结果行间距：横向页面留白 12dp + 上下 4dp（`IconPage.kt:221`）。
                modifier = Modifier.padding(
                    horizontal = PageMetrics.Horizontal,
                    vertical = SearchResultVerticalPadding,
                ),
            )
        }
    }
}
