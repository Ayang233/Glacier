package com.ayang.glacier.feature.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.ayang.glacier.feature.common.AppIconView
import com.ayang.glacier.feature.common.AppListCardRow
import com.ayang.glacier.feature.common.AppSearchResults
import com.ayang.glacier.feature.common.SearchResultVerticalPadding
import com.ayang.glacier.feature.common.actionSpecOf
import com.ayang.glacier.feature.common.appsMenuActions
import com.ayang.glacier.ui.rememberAppSafePadding
import com.ayang.glacier.ui.LocalGlassActive
import com.ayang.glacier.ui.LocalRootBackdrop
import com.ayang.glacier.ui.LocalAppPageInsets
import com.ayang.glacier.ui.TopLayer
import com.ayang.glacier.ui.appPageHorizontalInsets
import com.ayang.glacier.ui.components.EmptyState
import com.ayang.glacier.ui.components.PageFrame
import com.ayang.glacier.ui.components.PageMetrics
import com.ayang.glacier.ui.components.TopBarAction
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.glass.GlassCard
import top.yukonga.miuix.kmp.glass.GlassPopupAnchor
import top.yukonga.miuix.kmp.glass.GlassPopupDefaults
import top.yukonga.miuix.kmp.glass.GlassPopupItem
import top.yukonga.miuix.kmp.glass.GlassSearchOverlay
import top.yukonga.miuix.kmp.glass.GlassSearchOverlayDefaults
import top.yukonga.miuix.kmp.glass.GlassTransformPopup
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Close
import top.yukonga.miuix.kmp.icon.glass.Filter
import top.yukonga.miuix.kmp.icon.glass.Search
import top.yukonga.miuix.kmp.icon.glass.Sort
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 应用页：全部应用 + 排序 + 筛选。
 *
 * 排序与筛选都是"离散选项"，按 OS4 惯例走底部弹层，而不是塞进顶栏的图标菜单。
 *
 * **这一页的职责是"挑应用"**：点一行就把它移进/移出主页（行尾勾选框 = 是否在主页），
 * 没有多选模式、没有计数、顶栏永远只有搜索/排序/筛选；冻结统一在主页做。
 * 长按 = 该应用的管理菜单（应用信息 / 导出到剪切板 / 提取安装包）。
 * 两者都由行容器 `AppListCardRow` 的官方 `Card(onClick, onLongPress)` 承担，
 * 行内 `BasicComponent` 不再自己注册点击。
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
    // 浮层退场时保留最后一次的条目（锚点菜单的内容不能中途清空）。
    var lastActionEntry by remember { mutableStateOf<AppEntry?>(null) }

    // 弹层退场期间要保留最后一份内容：排序项在 pick 后立即改设置，退场动画里不应闪成新选中态。
    var lastSort by remember { mutableStateOf(preferences.appSort) }
    if (sortOpen) lastSort = preferences.appSort

    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val runner = remember(repository) { AppActionRunner(context, repository) }
    val glassActive = LocalGlassActive.current
    // 长按那一刻记下被长按行图标的官方锚点；`GlassTransformPopup` 从它原地展开。
    var popupAnchor by remember { mutableStateOf<GlassPopupAnchor?>(null) }
    val placeholderAnchor = rememberGlassPopupAnchor()
    // 搜索结果列表的滚动量要回传给搜索条：滚动后搜索条才转玻璃（官方 `IconPage.kt:499-544`）。
    val searchListState = rememberLazyListState()
    // 主列表状态：滚动条（PageFrame.scrollBarAdapter）与列表滚动共用同一个 state。
    val listState = rememberLazyListState()

    val entries = remember(repository.snapshot, revision) { repository.apps() }
    // 预热：浮层常驻组合，首帧即带完整菜单内容（否则首次打开会先量出"空菜单"并被
    // `GlassPopupSurface` 的固有尺寸缓存记住，之后内容补上也不重量）。浮层此时不可见，
    // 真正被长按的条目会在同一次组合里替换进来。
    if (lastActionEntry == null) lastActionEntry = entries.firstOrNull()
    // 「不在主页」徽标需要的集合：与搜索结果行共用同一份摘要文案（`appRowSummary`）。
    val homePackages = remember(repository.snapshot) {
        repository.snapshot.home.mapTo(HashSet()) { it.packageName }
    }

    // 点按语义：把这一行移进 / 移出主页。**没有多选态、没有计数**——应用页只负责挑应用，
    // 冻结统一在主页做（FAB / 长按 / 多选后的批量动作）。
    // 语义走 `AppActionRunner`，与主页操作单的"从主页移除"共用同一个出口（提示语只有一份）。
    val toggleHome: (AppEntry) -> Unit = { entry ->
        scope.launch {
            val onHome = entry.packageName in homePackages
            runner.run(entry, if (onHome) AppAction.RemoveFromHome else AppAction.AddToHome)
        }
    }
    // 长按语义：打开该应用的操作单（管理向：应用信息 / 导包名 / 提包）。
    // 按下态不做人工保持：松手后由 `MiuixIndication` 自己淡出（beta04 的 200ms 释放保持），
    // 菜单开着的时候不需要一行"永远按着"的灰底。
    val openActions: (String, GlassPopupAnchor?) -> Unit = { packageName, anchor ->
        if (anchor != null) popupAnchor = anchor
        actionTarget = packageName
    }

    PageFrame(
        title = stringResource(R.string.nav_apps),
        subtitle = stringResource(R.string.app_count, entries.size),
        scrollBehavior = scrollBehavior,
        searchExpanded = searchOpen,
        // 官方滚动条是每页显式添加的能力（`example/MainPage.kt:341-345`）；轨道内边距由 PageFrame
        // 按 Scaffold 实测值计算，页面只提供主列表的 state。空列表不挂（此时正文是空态，没有滚动容器）。
        scrollBarAdapter = rememberScrollBarAdapter(listState).takeIf { entries.isNotEmpty() },
        // 本页只有一态（搜索 / 排序 / 筛选），不存在"批量动作"那一档：
        // 动作间距 8dp 用官方值（`example/GlassPage.kt:363`），不需要 `AnimatedTopBarActions`。
        topBarActions = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopBarAction(MiuixIcons.Glass.Search, stringResource(R.string.search)) { searchOpen = true }
                TopBarAction(MiuixIcons.Glass.Sort, stringResource(R.string.sort)) { sortOpen = true }
                TopBarAction(MiuixIcons.Glass.Filter, stringResource(R.string.filter)) { filterOpen = true }
            }
        },
        overlay = { pageBackdrop ->
            GlassSearchOverlay(
                expanded = searchOpen,
                onDismissRequest = { searchOpen = false },
                label = stringResource(R.string.search_hint),
                // 与页面正文同一套官方契约：横向 = contentInsets，顶部 = safeInsets.top，
                // 折叠胶囊 = searchTop（官方 `IconPage.kt:495-498`）。
                horizontalInsetsModifier = Modifier.appPageHorizontalInsets(),
                topInsets = LocalAppPageInsets.current.safeInsets.only(WindowInsetsSides.Top),
                // 传 lambda 本身，让库在布局期逐帧读取，避免侧栏 morph 触发整页重组。
                searchTopPadding = LocalAppPageInsets.current.searchTop,
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
                // 搜索结果与列表同一套手势：点按 = 移进/移出主页（不收起浮层，可以连着挑几个），
                // 长按 = 锚点菜单（先收浮层，避免两层浮层叠着）。
                AppSearchResults(
                    query = query,
                    onPick = { entry -> toggleHome(entry) },
                    onLongPick = { entry, anchor ->
                        searchOpen = false
                        openActions(entry.packageName, anchor)
                    },
                    homePicker = true,
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

            // 应用页长按菜单：官方锚点浮层，从该行图标原地展开（不再用底部弹层，
            // 弹层顶部会贴住被长按的图标，且窗口会吃掉 UP 导致行按下层残留）。
            val actionEntry = actionTarget?.let { pkg ->
                repository.snapshot.all.firstOrNull { it.packageName == pkg }
            }
            SideEffect {
                if (actionEntry != null) lastActionEntry = actionEntry
            }
            // 浮层**常驻组合**（官方文档：退场期间仍须保留调用，不要用 `if (show)` 包裹）：
            // 首次长按才组合的话，`updateTransition` 初始值已是 `true`，展开动画不播。
            // 浮层未激活时 `GlassPopupSurface` 不组合，真正激活那一帧的内容由上面的预热保证。
            TopLayer {
                GlassTransformPopup(
                    show = actionTarget != null,
                    onDismissRequest = { actionTarget = null },
                    anchor = popupAnchor ?: placeholderAnchor,
                    backdrop = pageBackdrop,
                    anchorContent = {
                        lastActionEntry?.let { entry ->
                            AppIconView(
                                packageName = entry.packageName,
                                size = PageMetrics.ListAppIcon,
                                frozen = entry.frozen,
                            )
                        }
                    },
                    simplified = true,
                    gap = 8.dp,
                ) {
                    lastActionEntry?.let { entry ->
                        Text(
                            text = entry.label,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                            // 宽度交回官方内容驱动（MinWidth/MaxWidth），只保留菜单行内边距。
                            modifier = Modifier.padding(
                                horizontal = GlassPopupDefaults.ItemPaddingHorizontal,
                                vertical = 8.dp,
                            ),
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(
                                horizontal = GlassPopupDefaults.DividerPaddingHorizontal,
                                vertical = GlassPopupDefaults.DividerPaddingVertical,
                            ),
                            thickness = GlassPopupDefaults.DividerThickness,
                        )
                        appsMenuActions().forEach { action ->
                            val row = actionSpecOf(action, entry) { picked ->
                                actionTarget = null
                                scope.launch { runner.run(entry, picked) }
                            }
                            GlassPopupItem(
                                text = row.label,
                                icon = row.icon,
                                enabled = row.enabled,
                                onClick = row.onClick,
                            )
                        }
                    }
                }
            }
        },
    ) { contentPadding ->
        if (entries.isEmpty()) {
            // 空态必须吃页面 padding（顶栏 / 底栏 / 安全区），否则文本被顶栏压住（规范 1.3、审计 5.2 P0）。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                EmptyState(text = stringResource(R.string.empty_apps))
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // 页面左右 12dp = 官方卡片外边距（`MainPage.kt:312`），由 PageFrame 统一算好。
                contentPadding = contentPadding,
                // 弹性 overscroll 由 PageFrame 的官方 pageScrollModifiers 统一消费（官方列表页同参）。
                overscrollEffect = null,
                // 卡片之间 12dp = 官方卡片间距（规范第 3 节 `PageMetrics.SectionGap`）。
                verticalArrangement = Arrangement.spacedBy(PageMetrics.SectionGap),
            ) {
                items(entries, key = { it.packageName }) { entry ->
                    // 与搜索结果行同一实现：官方 Card(onClick, onLongPress) + BasicComponent 行。
                    // 长按菜单的锚点是这一行图标的官方锚点（在 AppRow.startAction 里登记）。
                    val anchor = rememberGlassPopupAnchor()
                    AppListCardRow(
                        entry = entry,
                        inHome = entry.packageName in homePackages,
                        onClick = { toggleHome(entry) },
                        onLongClick = { openActions(entry.packageName, anchor) },
                        // 勾选框 = 主页归属（官方 Checkbox），不是多选计数。
                        onHome = entry.packageName in homePackages,
                        anchor = anchor,
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
    // 弹层内容自己让出系统底部安全区（官方 BottomSheet KDoc 的同款做法），
    // 值来自官方 safeInsets 契约，不再逐处手写 WindowInsets。
    val navigationBarBottom = rememberAppSafePadding().calculateBottomPadding()
    GlassBottomSheet(
        onDismissRequest = onDismiss,
        show = show,
        backdrop = LocalRootBackdrop.current.takeIf { LocalGlassActive.current },
        title = stringResource(R.string.sort),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .scrollEndHaptic(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = navigationBarBottom + PageMetrics.SectionGap,
            ),
        ) {
            item(key = "sort-options") {
                GlassCard(Modifier.fillMaxWidth()) {
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
    // filterEnabled() 直接读 SharedPreferences，不是 Compose 快照状态；setFilter() 写入后
    // 只 bump revision。本函数是独立 Composable，父级读 revision 不会传导到这里，
    // 所以必须自己订阅 revision，并用它做 key 重新读取，开关才能点击后立即回显。
    val filterRevision = preferences.revision.intValue
    val filterStates = remember(filterRevision) {
        AppFilter.entries.associateWith(preferences::filterEnabled)
    }
    // 弹层内容自己让出系统底部安全区（官方 BottomSheet KDoc 的同款做法），
    // 值来自官方 safeInsets 契约，不再逐处手写 WindowInsets。
    val navigationBarBottom = rememberAppSafePadding().calculateBottomPadding()
    GlassBottomSheet(
        onDismissRequest = onDismiss,
        show = show,
        backdrop = LocalRootBackdrop.current.takeIf { LocalGlassActive.current },
        title = stringResource(R.string.filter),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .scrollEndHaptic(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = navigationBarBottom + PageMetrics.SectionGap,
            ),
        ) {
            item(key = "filters") {
                GlassCard(Modifier.fillMaxWidth()) {
                    AppFilter.entries.forEach { filter ->
                        SwitchPreference(
                            checked = filterStates[filter] == true,
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
