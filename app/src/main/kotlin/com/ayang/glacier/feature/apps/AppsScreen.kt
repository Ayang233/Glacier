package com.ayang.glacier.feature.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ayang.glacier.GlacierApplication
import com.ayang.glacier.R
import com.ayang.glacier.domain.AppEntry
import com.ayang.glacier.domain.AppFilter
import com.ayang.glacier.domain.AppSort
import com.ayang.glacier.feature.common.AppAction
import com.ayang.glacier.feature.common.AppActionRunner
import com.ayang.glacier.feature.common.AppActionSheet
import com.ayang.glacier.feature.common.AppRow
import com.ayang.glacier.feature.common.SheetActionRow
import com.ayang.glacier.ui.LocalGlassActive
import com.ayang.glacier.ui.LocalRootBackdrop
import com.ayang.glacier.ui.components.EmptyState
import com.ayang.glacier.ui.components.PageFrame
import com.ayang.glacier.ui.components.TopBarAction
import com.ayang.glacier.ui.components.pageContentPadding
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.glass.GlassSearchOverlay
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Ok
import top.yukonga.miuix.kmp.icon.glass.Filter
import top.yukonga.miuix.kmp.icon.glass.Search
import top.yukonga.miuix.kmp.icon.glass.Sort
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 应用页：全部应用 + 排序 + 筛选。
 *
 * 排序与筛选都是"离散选项"，按 OS4 惯例走底部弹层，而不是塞进顶栏的图标菜单。
 */
@Composable
fun AppsScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as GlacierApplication
    val repository = app.repository
    val preferences = app.preferences

    // 读一次 revision，让设置项变化（筛选/排序/模糊搜索）能触发重组。
    val revision = preferences.revision.intValue

    var searchOpen by remember { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    var filterOpen by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val runner = remember(repository) { AppActionRunner(context, repository) }
    val glassActive = LocalGlassActive.current
    val backdrop = LocalRootBackdrop.current

    val entries = remember(repository.snapshot, revision) { repository.apps() }

    PageFrame(
        title = stringResource(R.string.nav_apps),
        subtitle = stringResource(R.string.app_count, entries.size),
        scrollBehavior = scrollBehavior,
        topBarAlpha = if (searchOpen) 0f else 1f,
        topBarActions = {
            TopBarAction(MiuixIcons.Glass.Search, stringResource(R.string.search)) { searchOpen = true }
            TopBarAction(MiuixIcons.Glass.Sort, stringResource(R.string.sort)) { sortOpen = true }
            TopBarAction(MiuixIcons.Glass.Filter, stringResource(R.string.filter)) { filterOpen = true }
        },
        overlay = {
            if (searchOpen) {
                GlassSearchOverlay(
                    expanded = true,
                    onDismissRequest = { searchOpen = false },
                    label = stringResource(R.string.search_hint),
                    blurEnabled = glassActive,
                ) { query ->
                    com.ayang.glacier.feature.common.AppSearchResults(
                        query = query,
                        onPick = { entry ->
                            searchOpen = false
                            actionTarget = entry.packageName
                        },
                    )
                }
            }

            if (sortOpen) {
                SortSheet(
                    current = preferences.appSort,
                    onDismiss = { sortOpen = false },
                    onPick = { sort ->
                        preferences.appSort = sort
                        preferences.bumpRevision()
                        sortOpen = false
                    },
                )
            }

            if (filterOpen) {
                FilterSheet(
                    onDismiss = { filterOpen = false },
                )
            }

            AppActionSheet(
                entry = actionTarget?.let { pkg -> repository.snapshot.all.firstOrNull { it.packageName == pkg } },
                show = actionTarget != null,
                inHome = repository.snapshot.home.any { it.packageName == actionTarget },
                onDismiss = { actionTarget = null },
                onAction = { action ->
                    val entry = actionTarget?.let { pkg -> repository.snapshot.all.firstOrNull { it.packageName == pkg } }
                    actionTarget = null
                    if (entry != null) {
                        scope.launch {
                            runner.run(entry, action)
                            repository.refresh()
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (entries.isEmpty()) {
            EmptyState(text = stringResource(R.string.empty_apps))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = pageContentPadding(padding, horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries, key = { it.packageName }) { entry ->
                    AppCard(
                        entry = entry,
                        inHome = repository.snapshot.home.any { it.packageName == entry.packageName },
                        onClick = { actionTarget = entry.packageName },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppCard(
    entry: AppEntry,
    inHome: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(),
    ) {
        AppRow(
            packageName = entry.packageName,
            label = entry.label,
            summary = buildString {
                append(entry.packageName)
                if (entry.pinned) append(" · pinned")
                if (entry.whitelisted) append(" · whitelisted")
                if (!inHome) append(" · not on home")
            },
            frozen = entry.frozen,
            onClick = onClick,
            trailing = {
                if (entry.frozen) {
                    Text(
                        text = stringResource(R.string.frozen),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
            },
        )
    }
}

@Composable
private fun SortSheet(
    current: AppSort,
    onDismiss: () -> Unit,
    onPick: (AppSort) -> Unit,
) {
    GlassBottomSheet(
        onDismissRequest = onDismiss,
        show = true,
        backdrop = LocalRootBackdrop.current.takeIf { LocalGlassActive.current },
        title = stringResource(R.string.sort),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            AppSort.entries.forEach { sort ->
                val label = stringResource(
                    when (sort) {
                        AppSort.Name -> R.string.sort_name
                        AppSort.Install -> R.string.sort_install
                        AppSort.Update -> R.string.sort_update
                    },
                )
                if (sort == current) {
                    SheetActionRow(
                        icon = MiuixIcons.Glass.Ok,
                        label = label,
                        onClick = { onPick(sort) },
                    )
                } else {
                    PlainSheetRow(label = label, onClick = { onPick(sort) })
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** 未选中的排序项：不带图标，保持与选中项同高。 */
@Composable
private fun PlainSheetRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 14.dp),
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun FilterSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val preferences = (context.applicationContext as GlacierApplication).preferences
    GlassBottomSheet(
        onDismissRequest = onDismiss,
        show = true,
        backdrop = LocalRootBackdrop.current.takeIf { LocalGlassActive.current },
        title = stringResource(R.string.filter),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            AppFilter.entries.forEach { filter ->
                SwitchPreference(
                    checked = preferences.filterEnabled(filter),
                    onCheckedChange = { checked ->
                        preferences.setFilter(filter, checked)
                    },
                    title = stringResource(
                        when (filter) {
                            AppFilter.UserApps -> R.string.filter_user_apps
                            AppFilter.SystemApps -> R.string.filter_system_apps
                            AppFilter.FrozenApps -> R.string.filter_frozen_apps
                            AppFilter.UnfrozenApps -> R.string.filter_unfrozen_apps
                        },
                    ),
                    insideMargin = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
