package com.ayang.glacier.feature.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ayang.glacier.GlacierApplication
import com.ayang.glacier.R
import com.ayang.glacier.domain.AppEntry
import com.ayang.glacier.ui.components.EmptyState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassSearchOverlayDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 搜索浮层里的结果列表。
 *
 * [top.yukonga.miuix.kmp.glass.GlassSearchOverlay] 的内容槽从 y=0 开始铺，
 * 搜索条是叠在它上面的，所以结果列表必须自己让出「状态栏 + 搜索条」的高度，
 * 否则第一条结果会被搜索条压住。
 */
@Composable
fun AppSearchResults(
    query: String,
    onPick: (AppEntry) -> Unit,
) {
    val context = LocalContext.current
    val repository = (context.applicationContext as GlacierApplication).repository
    val results = remember(query, repository.snapshot) {
        if (query.isBlank()) emptyList() else repository.apps(query)
    }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val barSpace = GlassSearchOverlayDefaults.BarHeight +
        GlassSearchOverlayDefaults.VerticalPadding * 2

    if (results.isEmpty()) {
        Column(Modifier.padding(top = topInset + barSpace)) {
            EmptyState(
                text = stringResource(
                    if (query.isBlank()) R.string.search_hint else R.string.empty_search,
                ),
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = topInset + barSpace + 8.dp,
            start = 16.dp,
            end = 16.dp,
            bottom = 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(results.take(50), key = { it.packageName }) { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(entry) }
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                AppIconView(entry.packageName, 40.dp, entry.frozen)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = entry.label,
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = entry.packageName,
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (entry.frozen) {
                    Text(
                        text = stringResource(R.string.frozen),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
