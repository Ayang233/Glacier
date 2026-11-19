package com.ayang.glacier.feature.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.ayang.glacier.feature.common.AppSearchResults
import com.ayang.glacier.feature.common.SearchResultVerticalPadding
import com.ayang.glacier.ui.LocalGlassActive
import com.ayang.glacier.ui.LocalRootBackdrop
import com.ayang.glacier.ui.LocalSearchTopInset
import com.ayang.glacier.ui.components.EmptyState
import com.ayang.glacier.ui.components.PageFrame
import com.ayang.glacier.ui.components.PageMetrics
import com.ayang.glacier.ui.components.TopBarAction
import com.ayang.glacier.ui.components.pageContentPadding
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.glass.GlassSearchOverlay
import top.yukonga.miuix.kmp.glass.GlassSearchOverlayDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Close
import top.yukonga.miuix.kmp.icon.glass.Filter
import top.yukonga.miuix.kmp.icon.glass.Ok
import top.yukonga.miuix.kmp.icon.glass.Search
import top.yukonga.miuix.kmp.icon.glass.Sort
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * 应用页：全部应用 + 排序 + 筛选。
 *
 * 排序与筛选都是"离散选项"，按 OS4 惯例走底部弹层，而不是塞进顶栏的图标菜单。
 *
 * 浮层（搜索 / 排序 / 筛选 / 操作单）全部**常驻组合**，只由 `expanded` / `show` 驱动：
 * 官方 [GlassSearchOverlay] 的 `active = expanded || transition.isRunning`（`GlassSearchOverlay.kt:229-240`）
 * 与 [GlassBottomSheet] 的退出动画（`MiuixPopupUtils.kt:93-145`）都要求组件在退场期间仍在组合树里；
 * 用 `if (open)` 包裹会砍掉退出动画、搜索结果 query 清理与返回键处理（规范 0.1 红线）。
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

    // 弹层退场期间要保留最后一份内容：排序项在 pick 后立即改设置，退场动画里不应闪成新选中态。
    var lastSort by remember { mutableStateOf(preferences.appSort) }
    if (sortOpen) lastSort = preferences.appSort

    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val runner = remember(repository) { AppActionRunner(context, repository) }
    val glassActive = LocalGlassActive.current
    // 搜索结果列表的滚动量要回传给搜索条：滚动后搜索条才转玻璃（官方 `IconPage.kt:499-544`）。
    val searchListState = rememberLazyListState()

    val entries = remember(repository.snapshot, revision) { repository.apps() }

    PageFrame(
        title = stringResource(R.string.nav_apps),
        subtitle = stringResource(R.string.app_count, entries.size),
        scrollBehavior = scrollBehavior,
        searchExpanded = searchOpen,
        topBarActions = {
            TopBarAction(MiuixIcons.Glass.Search, stringResource(R.string.search)) { searchOpen = true }
            TopBarAction(MiuixIcons.Glass.Sort, stringResource(R.string.sort)) { sortOpen = true }
            TopBarAction(MiuixIcons.Glass.Filter, stringResource(R.string.filter)) { filterOpen = true }
        },
        overlay = {
            GlassSearchOverlay(
                expanded = searchOpen,
                onDismissRequest = { searchOpen = false },
                label = stringResource(R.string.search_hint),
                // 横向只让开刘海/显示挖孔：侧栏让位已经由 Shell 的 `railStartInset` 作用在整页上，
                // 搜索浮层不再重复让位（官方等价写法 `IconPage.kt:518`、`GlassPage.kt:691`）。
                horizontalInsetsModifier = Modifier.windowInsetsPadding(
                    WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
                ),
                // 官方语义：宽屏侧栏折叠成顶部胶囊时搜索条下移（`AppContent.kt:239-245`）。
                // 传 lambda 本身，让库在布局期逐帧读取，避免侧栏 morph 触发整页重组。
                searchTopPadding = LocalSearchTopInset.current,
                scrollMaterialThreshold = GlassSearchOverlayDefaults.VerticalPadding + SearchResultVerticalPadding,
                scrollOffset = {
                    when {
                        searchListState.layoutInfo.totalItemsCount == 0 -> 0f
                        searchListState.firstVisibleItemIndex > 0 -> Float.MAX_VALUE
                        else -> searchListState.firstVisibleItemScrollOffset.toFloat()
                    }
                },
                blurEnabled = glassActive,
                searchIcon = MiuixIcons.Glass.Search,
                closeIcon = MiuixIcons.Glass.Close,
            ) { query ->
                AppSearchResults(
                    query = query,
                    onPick = { entry ->
                        searchOpen = false
                        actionTarget = entry.packageName
                    },
                    listState = searchListState,
                )
            }

            SortSheet(
                show = sortOpen,
                current = lastSort,
                onDismiss = { sortOpen = false },
                onPick = { sort ->
                    preferences.appSort = sort
                    preferences.bumpRevision()
                    sortOpen = false
                },
            )

            FilterSheet(
                show = filterOpen,
                onDismiss = { filterOpen = false },
            )

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
            // 空态必须吃页面 padding（顶栏 / 底栏 / 安全区），否则文本被顶栏压住（规范 1.3、审计 5.2 P0）。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pageContentPadding(padding)),
            ) {
                EmptyState(text = stringResource(R.string.empty_apps))
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                // 页面左右 12dp = 官方卡片外边距（`MainPage.kt:312`），由 `pageContentPadding` 默认值给出。
                contentPadding = pageContentPadding(padding),
                // 卡片之间 12dp = 官方卡片间距（规范第 3 节 `PageMetrics.SectionGap`）。
                verticalArrangement = Arrangement.spacedBy(PageMetrics.SectionGap),
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
    // 点击与按压反馈交给官方 Card（Sink 反馈），行本身只负责排版，避免两层 clickable。
    // `insideMargin` / `colors` 不传：默认值就是 0dp / `CardDefaults.defaultColors()`（`Card.kt:192-204`）。
    Card(
        modifier = Modifier.fillMaxWidth(),
        pressFeedbackType = PressFeedbackType.Sink,
        onClick = onClick,
    ) {
        AppRow(
            packageName = entry.packageName,
            label = entry.label,
            summary = buildString {
                append(entry.packageName)
                if (entry.pinned) append(" · ").append(stringResource(R.string.badge_pinned))
                if (entry.whitelisted) append(" · ").append(stringResource(R.string.badge_whitelisted))
                if (!inHome) append(" · ").append(stringResource(R.string.badge_not_on_home))
            },
            frozen = entry.frozen,
            // 外层 Card 承担点击，行不再注册 clickable（`AppRow` 语义：onClick = null）。
        )
    }
}

/**
 * 排序弹层。
 *
 * 所有行统一 [BasicComponent]（同文字基线、同行高），选中态只把 `Ok` 图标放进 `endActions`，
 * 不再混用"带图标行 / 不带图标行"两种结构（审计 5.2 排序弹层、规范 2.2）。
 */
@Composable
private fun SortSheet(
    show: Boolean,
    current: AppSort,
    onDismiss: () -> Unit,
    onPick: (AppSort) -> Unit,
) {
    GlassBottomSheet(
        onDismissRequest = onDismiss,
        show = show,
        backdrop = LocalRootBackdrop.current.takeIf { LocalGlassActive.current },
        title = stringResource(R.string.sort),
    ) {
        Column(Modifier.fillMaxWidth()) {
            AppSort.entries.forEach { sort ->
                val label = stringResource(
                    when (sort) {
                        AppSort.Name -> R.string.sort_name
                        AppSort.Install -> R.string.sort_install
                        AppSort.Update -> R.string.sort_update
                    },
                )
                BasicComponent(
                    title = label,
                    // 选中态只放尾部 Ok 图标：所有行同一结构、同一文字基线（规范 2.2）。
                    endActions = {
                        if (sort == current) {
                            Icon(
                                imageVector = MiuixIcons.Glass.Ok,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = MiuixTheme.colorScheme.primary,
                            )
                        }
                    },
                    onClick = { onPick(sort) },
                )
            }
            Spacer(Modifier.height(PageMetrics.BottomGap))
        }
    }
}

/**
 * 筛选弹层。
 *
 * [SwitchPreference] 全部用默认 `insideMargin`（= [BasicComponentDefaults.InsideMargin]，16dp），
 * 这里不覆盖任何行内边距；弹层常驻组合，由 `show` 驱动进出动画。
 */
@Composable
private fun FilterSheet(
    show: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val preferences = (context.applicationContext as GlacierApplication).preferences
    GlassBottomSheet(
        onDismissRequest = onDismiss,
        show = show,
        backdrop = LocalRootBackdrop.current.takeIf { LocalGlassActive.current },
        title = stringResource(R.string.filter),
    ) {
        Column(Modifier.fillMaxWidth()) {
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
                )
            }
            Spacer(Modifier.height(PageMetrics.BottomGap))
        }
    }
}
