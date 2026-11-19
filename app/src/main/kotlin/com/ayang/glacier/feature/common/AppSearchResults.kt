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
 * 搜索浮层里的结果列表。
 *
 * [top.yukonga.miuix.kmp.glass.GlassSearchOverlay] 的内容槽从 y=0 开始铺，
 * 搜索条是叠在它上面的，所以结果列表必须自己让出「状态栏 + 折叠胶囊让位 + 搜索条」的高度
 * （官方 `IconPage.kt:537-540`：`safeInsets.top + searchTop + BarHeight + VerticalPadding * 2`）。
 *
 * 行结构照官方搜索结果：`Card` + `BasicComponent`（`IconPage.kt:221-244`）。
 * 本文件不做任何手写 `Row + clickable`；应用图标 / 标题 / 包名摘要 / 冻结徽标全部由 [AppRow] 提供。
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
            Card(
                modifier = Modifier.padding(
                    horizontal = PageMetrics.Horizontal,
                    vertical = SearchResultVerticalPadding,
                ),
            ) {
                AppRow(
                    packageName = entry.packageName,
                    label = entry.label,
                    summary = entry.packageName,
                    frozen = entry.frozen,
                    onClick = { onPick(entry) },
                )
            }
        }
    }
}
