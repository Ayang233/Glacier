package com.ayang.glacier.feature.apps

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.state.ToggleableState
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
import com.ayang.glacier.feature.common.reportFreezeBatch
import com.ayang.glacier.ui.rememberAppSafePadding
import com.ayang.glacier.ui.LocalGlassActive
import com.ayang.glacier.ui.LocalRootBackdrop
import com.ayang.glacier.ui.LocalAppPageInsets
import com.ayang.glacier.ui.appPageHorizontalInsets
import com.ayang.glacier.ui.components.AnimatedTopBarActions
import com.ayang.glacier.ui.components.EmptyState
import com.ayang.glacier.ui.components.PageFrame
import com.ayang.glacier.ui.components.PageMetrics
import com.ayang.glacier.ui.components.TopBarAction
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.glass.GlassCard
import top.yukonga.miuix.kmp.glass.GlassSearchOverlay
import top.yukonga.miuix.kmp.glass.GlassSearchOverlayDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Close
import top.yukonga.miuix.kmp.icon.glass.Filter
import top.yukonga.miuix.kmp.icon.glass.More
import top.yukonga.miuix.kmp.icon.glass.QuietMode
import top.yukonga.miuix.kmp.icon.glass.Search
import top.yukonga.miuix.kmp.icon.glass.Sort
import top.yukonga.miuix.kmp.icon.glass.UnlockOutline
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
 * **行手势（OS4 语义）**：点按 = 选中该应用（进入多选，顶栏变成批量动作），
 * 长按 = 弹出该应用的操作单。两者都由行容器 `AppListCardRow` 的官方
 * `Card(onClick, onLongPress)` 承担，行内 `BasicComponent` 不再自己注册点击。
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
    // 操作单打开期间保持按下态的行（官方 `Card(holdDownState)` 用法，见 `CardSection.kt:110-143`）：
    // 与 `actionTarget` 分开是因为弹层退场时官方是"先收面板、再复位按下态"，
    // 复位挂在 `onDismissFinished` 上，所以面板内容清空后这一行仍要保持一小会儿按下态。
    var heldPackage by remember { mutableStateOf<String?>(null) }
    // 多选：`selecting` 与 Home 同义（点过一次就进多选，直到关闭），
    // `selection` 为空时也保持多选态，避免"取消最后一个后顶栏突然跳回普通状态"。
    var selecting by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(emptySet<String>()) }
    val inSelection = selecting || selection.isNotEmpty()

    // 多选态下返回键先退出多选，而不是直接把 Activity 结束掉。
    // 点按即进入多选之后，这条退路必须有：否则用户点错一行、按返回就退出应用。
    // 官方 `BackHandler`（activity-compose）注册在 NavigationEventDispatcher 栈顶，
    // 位于 NavDisplay 的返回处理之前，与返回手势/三键导航同一入口。
    BackHandler(enabled = inSelection) {
        selecting = false
        selection = emptySet()
    }

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

    // 点按语义：切换该应用在多选里的成员关系。第一次点按就进入多选态（顶栏切批量动作）。
    val toggleSelection: (String) -> Unit = { packageName ->
        selecting = true
        selection = if (packageName in selection) selection - packageName else selection + packageName
    }
    // 长按语义：打开该应用的操作单，并让这一行保持官方按下态直到弹层退场。
    val openActions: (String) -> Unit = { packageName ->
        actionTarget = packageName
        heldPackage = packageName
    }
    // 批量冻结 / 解冻：与 Home 共用同一个结果出口（`reportFreezeBatch`），两页措辞一致。
    val applyBatch: (Boolean) -> Unit = { frozen ->
        val targets = selection
        scope.launch {
            val outcome = repository.setFrozenBatch(targets, frozen)
            reportFreezeBatch(context, outcome, frozen)
            selecting = false
            selection = emptySet()
        }
    }

    PageFrame(
        title = if (inSelection) {
            stringResource(R.string.selected_count, selection.size)
        } else {
            stringResource(R.string.nav_apps)
        },
        subtitle = if (inSelection) "" else stringResource(R.string.app_count, entries.size),
        scrollBehavior = scrollBehavior,
        searchExpanded = searchOpen,
        // 官方滚动条是每页显式添加的能力（`example/MainPage.kt:341-345`）；轨道内边距由 PageFrame
        // 按 Scaffold 实测值计算，页面只提供主列表的 state。空列表不挂（此时正文是空态，没有滚动容器）。
        scrollBarAdapter = rememberScrollBarAdapter(listState).takeIf { entries.isNotEmpty() },
        // 普通档（搜索/排序/筛选）与多选档（冻结/解冻/更多/关闭）之间走官方的两态切换动画，
        // 不再整排图标一帧替换。
        topBarActions = {
            AnimatedTopBarActions(
                selectionMode = inSelection,
                normal = {
                    TopBarAction(MiuixIcons.Glass.Search, stringResource(R.string.search)) { searchOpen = true }
                    TopBarAction(MiuixIcons.Glass.Sort, stringResource(R.string.sort)) { sortOpen = true }
                    TopBarAction(MiuixIcons.Glass.Filter, stringResource(R.string.filter)) { filterOpen = true }
                },
                selection = {
                    TopBarAction(MiuixIcons.Glass.QuietMode, stringResource(R.string.action_freeze)) {
                        applyBatch(true)
                    }
                    TopBarAction(MiuixIcons.Glass.UnlockOutline, stringResource(R.string.action_unfreeze)) {
                        applyBatch(false)
                    }
                    TopBarAction(MiuixIcons.Glass.More, stringResource(R.string.more)) {
                        if (selection.size == 1) openActions(selection.first())
                    }
                    TopBarAction(MiuixIcons.Glass.Close, stringResource(R.string.close)) {
                        selecting = false
                        selection = emptySet()
                    }
                },
            )
        },
        overlay = {
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
                // 搜索结果与列表同一套手势：点按 = 选中（选中后收起浮层，回到列表的多选态），
                // 长按 = 操作单。搜索浮层盖住顶栏，选完必须回到列表才能看到批量动作。
                AppSearchResults(
                    query = query,
                    onPick = { entry ->
                        searchOpen = false
                        toggleSelection(entry.packageName)
                    },
                    onLongPick = { entry ->
                        searchOpen = false
                        openActions(entry.packageName)
                    },
                    selected = selection,
                    selectionMode = inSelection,
                    holdDownPackage = heldPackage,
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
                // 官方长按示例的收尾：按下态在弹层完全退场后复位，避免行的 alpha 在退场动画中途跳回。
                onDismissFinished = { heldPackage = null },
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
                // 卡片之间 12dp = 官方卡片间距（规范第 3 节 `PageMetrics.SectionGap`）。
                verticalArrangement = Arrangement.spacedBy(PageMetrics.SectionGap),
            ) {
                items(entries, key = { it.packageName }) { entry ->
                    // 与搜索结果行同一实现：官方 Card(onClick, onLongPress) + BasicComponent 行。
                    AppListCardRow(
                        entry = entry,
                        inHome = entry.packageName in homePackages,
                        onClick = { toggleSelection(entry.packageName) },
                        onLongClick = { openActions(entry.packageName) },
                        // 多选指示走官方 Checkbox（行尾 endActions）；不在多选态时不渲染。
                        selectionState = if (inSelection) {
                            ToggleableState(entry.packageName in selection)
                        } else {
                            null
                        },
                        holdDown = entry.packageName == heldPackage,
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
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .scrollEndHaptic()
                .overScrollVertical(),
            contentPadding = PaddingValues(bottom = navigationBarBottom + PageMetrics.SectionGap),
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
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .scrollEndHaptic()
                .overScrollVertical(),
            contentPadding = PaddingValues(bottom = navigationBarBottom + PageMetrics.SectionGap),
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
