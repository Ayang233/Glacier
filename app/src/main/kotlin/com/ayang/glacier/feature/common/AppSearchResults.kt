package com.ayang.glacier.feature.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
import com.ayang.glacier.ui.LocalSearchTopInset
import com.ayang.glacier.ui.LocalShellBottomBarHeight
import com.ayang.glacier.ui.components.EmptyState
import com.ayang.glacier.ui.components.PageMetrics
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.glass.GlassSearchOverlayDefaults

/** 官方搜索结果行的上下间距：`example/IconPage.kt:104`（`SearchResultVerticalPadding = 4.dp`）。 */
internal val SearchResultVerticalPadding = 4.dp

/**
 * 应用列表行与搜索结果行**共用的唯一实现**。
 *
 * 官方行结构是 `Card` + `BasicComponent`（`example/IconPage.kt:221-244`，搜索行）：
 * - 点击/按压语义交给行内的官方 `BasicComponent`（`AppRow` → `BasicComponent(onClick)`，
 *   `miuix-ui/basic/Component.kt:148-160`）；
 * - `Card` 只负责分组表面，不注册点击（`Card(colors=...)` 无交互版，`miuix-ui/basic/Card.kt:50-67`）。
 *
 * 这样只有一层 indication；且 [Card] 的 `squircleSurface` 会把行内的 MiuixIndication 矩形叠层
 * 裁成圆角（`miuix-squircle/SquircleBackground.kt:124-165` + `Card.kt:174-183`），
 * 不会出现「正方形按压层」。搜索行与 Apps 列表行因此是同一份代码、同一视觉。
 */
@Composable
fun AppListCardRow(
    entry: AppEntry,
    inHome: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        AppRow(
            packageName = entry.packageName,
            label = entry.label,
            summary = appRowSummary(entry, inHome),
            frozen = entry.frozen,
            onClick = onClick,
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
 */
@Composable
fun AppSearchResults(
    query: String,
    onPick: (AppEntry) -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val context = LocalContext.current
    val repository = (context.applicationContext as GlacierApplication).repository
    val results = remember(query, repository.snapshot) {
        if (query.isBlank()) emptyList() else repository.apps(query)
    }
    // 行摘要要标「不在主页」，这里与 Apps 列表同一份 home 集合，避免逐行 O(n) 查找。
    val homePackages = remember(repository.snapshot) {
        repository.snapshot.home.mapTo(HashSet()) { it.packageName }
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // 宽屏侧栏折叠成顶部胶囊时的让位（官方 `IconPage.kt:538` 的 `pageInsets.searchTop()`）。
    val searchTop = LocalSearchTopInset.current()
    val barSpace = GlassSearchOverlayDefaults.BarHeight + GlassSearchOverlayDefaults.VerticalPadding * 2
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    // 横向只让开刘海/显示挖孔；侧栏让位已由 Shell 的 `railStartInset` 作用在整页上。
    val horizontalInsets = Modifier.windowInsetsPadding(
        WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
    )

    if (results.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(horizontalInsets)
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
            .then(horizontalInsets),
        contentPadding = PaddingValues(
            top = topInset + searchTop + barSpace,
            bottom = bottomInset + LocalShellBottomBarHeight.current + PageMetrics.BottomGap,
        ),
    ) {
        items(results, key = { it.packageName }) { entry ->
            AppListCardRow(
                entry = entry,
                inHome = entry.packageName in homePackages,
                onClick = { onPick(entry) },
                // 官方搜索结果行间距：横向页面留白 12dp + 上下 4dp（`IconPage.kt:221`）。
                modifier = Modifier.padding(
                    horizontal = PageMetrics.Horizontal,
                    vertical = SearchResultVerticalPadding,
                ),
            )
        }
    }
}
