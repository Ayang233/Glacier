package com.ayang.glacier.feature.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
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
import com.ayang.glacier.GlacierApplication
import com.ayang.glacier.R
import com.ayang.glacier.domain.AppFilter
import com.ayang.glacier.domain.AppSort
import com.ayang.glacier.feature.common.AppAction
import com.ayang.glacier.feature.common.AppActionRunner
import com.ayang.glacier.feature.common.AppActionSheet
import com.ayang.glacier.feature.common.AppListCardRow
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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.glass.GlassSearchOverlay
import top.yukonga.miuix.kmp.glass.GlassSearchOverlayDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Close
import top.yukonga.miuix.kmp.icon.glass.Filter
import top.yukonga.miuix.kmp.icon.glass.Search
import top.yukonga.miuix.kmp.icon.glass.Sort
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * Apps 页上「点击了但没有实现」的动作：不渲染，避免看起来可点却无效（任务书 §7.4）。
 *
 * 标签编辑与定时冻结目前只有主页有完整入口（`HomeScreen.kt` 的 TagNameDialog / DeferredSheet）；
 * Apps 页没有这两个弹层，若仍渲染，`AppActionRunner` 的默认回调是空实现，用户点了不会有任何反应。
 * 需要补齐时先在这两个入口实现，再把对应 [AppAction] 从集合里移除即可。
 */
private val AppsUnsupportedActions = setOf(AppAction.EditTags, AppAction.DeferredFreeze)

/**
 * 应用页：全部应用 + 排序 + 筛选。
 *
 * 排序与筛选都是"离散选项"，按 OS4 惯例走底部弹层，而不是塞进顶栏的图标菜单。
 *
 * 浮层（搜索 / 排序 / 筛选 / 操作单）全部**常驻组合**，只由 `expanded` / `show` 驱动：
 * 官方 [GlassSearchOverlay] 的 `active = expanded || transition.isRunning`（`GlassSearchOverlay.kt:229-240`）
 * 与 [GlassBottomSheet] 的退出动画（`MiuixPopupUtils.kt:93-145`）都要求组件在退场期间仍在组合树里；
 * 用 `if (open)` 包裹会砍掉退出动画、搜索结果 query 清理与返回键处理（规范 0.1 红线）。
 *
 * 滚动修饰（overscroll / 滚动结束触觉）由 [PageFrame] 统一提供，本页不再重复挂
 * （官方 `utils/PageUtils.kt:168-183`；重复挂载会与 PageFrame 的 nestedScroll 双份，见
 * `PullToRefreshPage.kt:156` 的官方警告）。滚动条是官方示例的"每页显式添加"能力
 * （`MainPage.kt:341-345`），这里把主列表的 [LazyListState] 交给 PageFrame 的 `scrollBarAdapter`。
 */
@OptIn(ExperimentalScrollBarApi::class)
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
    // 主列表状态：滚动条（PageFrame.scrollBarAdapter）与列表滚动共用同一个 state。
    val listState = rememberLazyListState()

    val entries = remember(repository.snapshot, revision) { repository.apps() }
    // 「不在主页」徽标需要的集合：与搜索结果行共用同一份摘要文案（`appRowSummary`）。
    val homePackages = remember(repository.snapshot) {
        repository.snapshot.home.mapTo(HashSet()) { it.packageName }
    }

    PageFrame(
        title = stringResource(R.string.nav_apps),
        subtitle = stringResource(R.string.app_count, entries.size),
        scrollBehavior = scrollBehavior,
        searchExpanded = searchOpen,
        // 官方滚动条是每页显式添加的能力（`example/MainPage.kt:341-345`）；轨道内边距由 PageFrame
        // 按 Scaffold 实测值计算，页面只提供主列表的 state。空列表不挂（此时正文是空态，没有滚动容器）。
        scrollBarAdapter = rememberScrollBarAdapter(listState).takeIf { entries.isNotEmpty() },
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
                // Apps 页没有标签编辑 / 定时冻结入口：隐藏这两行，不留「看起来可点但无效」的控件。
                unsupportedActions = AppsUnsupportedActions,
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
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // 页面左右 12dp = 官方卡片外边距（`MainPage.kt:312`），由 `pageContentPadding` 默认值给出。
                contentPadding = pageContentPadding(padding),
                // 卡片之间 12dp = 官方卡片间距（规范第 3 节 `PageMetrics.SectionGap`）。
                verticalArrangement = Arrangement.spacedBy(PageMetrics.SectionGap),
            ) {
                items(entries, key = { it.packageName }) { entry ->
                    // 与搜索结果行同一实现：官方 Card + BasicComponent 行（`IconPage.kt:221-244`）。
                    AppListCardRow(
                        entry = entry,
                        inHome = entry.packageName in homePackages,
                        onClick = { actionTarget = entry.packageName },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * 排序弹层。
 *
 * 排序是单选项，用官方 [RadioButtonPreference]（`miuix-preference/.../RadioButtonPreference.kt:50-143`）：
 * - 选中态由官方 RadioButton + 主色标题表达，并带选中/取消触觉（同文件 `:127-138`）；
 * - `role = Role.RadioButton`，无障碍语义正确（R3 的 `Ok` 图标版没有选中语义）；
 * - 行全部同构（都是 RadioButtonPreference），不再混用带图标 / 不带图标两种结构（规范 2.2）。
 *
 * 行分组用 [Card]（官方 BottomSheet 写法 `BottomSheetSection.kt:219-237`）：Card 的
 * `squircleSurface` 负责把行的矩形 MiuixIndication 裁成圆角，不会出现方形按压层。
 *
 * 内容用 [LazyColumn] 同官方 BottomSheet 示例（`BottomSheetSection.kt:209-213`），
 * 大字体 / 横屏下超过弹层可用高度时可滚动（弹层本身 `heightIn(max = windowHeight - topInset)`，
 * `BottomSheetContentLayout.kt:653-700`，`Column` 会被直接裁掉）。
 */
@Composable
private fun SortSheet(
    show: Boolean,
    current: AppSort,
    onDismiss: () -> Unit,
    onPick: (AppSort) -> Unit,
) {
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    GlassBottomSheet(
        onDismissRequest = onDismiss,
        show = show,
        backdrop = LocalRootBackdrop.current.takeIf { LocalGlassActive.current },
        title = stringResource(R.string.sort),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .scrollEndHaptic()
                .overScrollVertical(),
            contentPadding = PaddingValues(bottom = navigationBarBottom + PageMetrics.SectionGap),
        ) {
            item(key = "sort-options") {
                Card(Modifier.fillMaxWidth()) {
                    AppSort.entries.forEach { sort ->
                        val label = stringResource(
                            when (sort) {
                                AppSort.Name -> R.string.sort_name
                                AppSort.Install -> R.string.sort_install
                                AppSort.Update -> R.string.sort_update
                            },
                        )
                        RadioButtonPreference(
                            title = label,
                            selected = sort == current,
                            onClick = { onPick(sort) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 筛选弹层。
 *
 * [SwitchPreference] 全部用默认 `insideMargin`（= [top.yukonga.miuix.kmp.basic.BasicComponentDefaults.InsideMargin]，16dp），
 * 这里不覆盖任何行内边距；弹层常驻组合，由 `show` 驱动进出动画。
 * 分组同样是官方 Card（`BottomSheetSection.kt:219-237`），内容用 LazyColumn 保证大字体可滚动。
 */
@Composable
private fun FilterSheet(
    show: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val preferences = (context.applicationContext as GlacierApplication).preferences
    val navigationBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    GlassBottomSheet(
        onDismissRequest = onDismiss,
        show = show,
        backdrop = LocalRootBackdrop.current.takeIf { LocalGlassActive.current },
        title = stringResource(R.string.filter),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .scrollEndHaptic()
                .overScrollVertical(),
            contentPadding = PaddingValues(bottom = navigationBarBottom + PageMetrics.SectionGap),
        ) {
            item(key = "filters") {
                Card(Modifier.fillMaxWidth()) {
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
                }
            }
        }
    }
}
