package com.ayang.glacier.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.CapturedAnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.computedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ayang.glacier.GlacierApplication
import com.ayang.glacier.R
import com.ayang.glacier.domain.AppEntry
import com.ayang.glacier.domain.AppSnapshot
import com.ayang.glacier.domain.DEFAULT_TAG_ID
import com.ayang.glacier.domain.Tag
import com.ayang.glacier.feature.common.AppAction
import com.ayang.glacier.feature.common.AppActionRunner
import com.ayang.glacier.feature.common.AppIconCornerRatio
import com.ayang.glacier.feature.common.AppIconView
import com.ayang.glacier.feature.common.AppLabelTextStyle
import com.ayang.glacier.feature.common.AppSearchResults
import com.ayang.glacier.feature.common.SearchResultVerticalPadding
import com.ayang.glacier.feature.common.SelectionActionRunner
import com.ayang.glacier.feature.common.SheetActionRow
import com.ayang.glacier.feature.common.actionSpecOf
import com.ayang.glacier.feature.common.homeMenuActions
import com.ayang.glacier.feature.common.homeSelectionMenuActions
import com.ayang.glacier.feature.common.reportFreezeBatch
import com.ayang.glacier.platform.UiFeedback
import com.ayang.glacier.system.FreezeOutcome
import com.ayang.glacier.ui.LocalGlassActive
import com.ayang.glacier.ui.LocalRootBackdrop
import com.ayang.glacier.ui.LocalAppPageInsets
import com.ayang.glacier.ui.TopLayer
import com.ayang.glacier.ui.appPageHorizontalInsets
import com.ayang.glacier.ui.components.PageMetrics
import com.ayang.glacier.ui.rememberAppSafePadding
import com.ayang.glacier.ui.components.EmptyState
import com.ayang.glacier.ui.components.PageFrame
import com.ayang.glacier.ui.components.TopBarAction
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.glass.GlassCard
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.glass.GlassFloatingActionButton
import top.yukonga.miuix.kmp.glass.GlassMotion
import top.yukonga.miuix.kmp.glass.GlassPopupAnchor
import top.yukonga.miuix.kmp.glass.GlassPopupDefaults
import top.yukonga.miuix.kmp.glass.GlassPopupItem
import top.yukonga.miuix.kmp.glass.GlassSearchOverlay
import top.yukonga.miuix.kmp.glass.GlassSearchOverlayDefaults
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRow
import top.yukonga.miuix.kmp.glass.GlassTextField
import top.yukonga.miuix.kmp.glass.GlassTransformPopup
import top.yukonga.miuix.kmp.glass.glassPopupAnchor
import top.yukonga.miuix.kmp.glass.glassPopupAnchorContent
import top.yukonga.miuix.kmp.glass.rememberGlassPopupAnchor
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Add
import top.yukonga.miuix.kmp.icon.glass.Close
import top.yukonga.miuix.kmp.icon.glass.Delete
import top.yukonga.miuix.kmp.icon.glass.Edit
import top.yukonga.miuix.kmp.icon.glass.MultiSelect
import top.yukonga.miuix.kmp.icon.glass.QuietMode
import top.yukonga.miuix.kmp.icon.glass.Search
import top.yukonga.miuix.kmp.icon.glass.Timer
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * Home 网格尺寸：`docs/plan/r3-implementation-spec.md` §2.1 的 12dp 网格体系。
 *
 * 图标 56dp 用 [PageMetrics.GridAppIcon]，其余为规范登记值：图标与标签 6dp、
 * 单元水平间距 8dp、垂直间距 16dp；不再使用上一版的 6dp/18dp 混搭。
 */
private object HomeGridMetrics {
    val IconLabelGap = 6.dp
    val HorizontalSpacing = 8.dp
    val VerticalSpacing = 16.dp

    /**
     * 网格单元按压/长按叠层的裁剪半径。
     *
     * 与图标是同一根 squircle 曲线：`GridAppIcon(56dp) * AppIconCornerRatio(0.22) = 12.32dp`。
     * 官方图标网格单元也是整格先 `squircleClip(12.dp)` 再 `clickable`（`IconPage.kt:403-411`），
     * 这里不引入新的圆角数值。
     */
    val CellCorner = PageMetrics.GridAppIcon * AppIconCornerRatio

    /** 单元目标宽度：与官方 `example/IconPage.kt` 的图标网格公式一致（80dp 一列）。 */
    val CellWidth = 80.dp

    /** 紧凑模式（设置项）把目标宽度压小，一行放更多图标。 */
    val CompactCellWidth = 68.dp

    /**
     * 列数按可用宽度自适应，不再写死 4/5 列。
     *
     * 官方 `example/IconPage.kt:347-350` 用 `((maxWidth - 24.dp) / 80.dp).toInt().coerceIn(3, 8)`，
     * 竖屏 411dp → 4 列，横屏/平板 → 6~8 列；否则横屏会四列拉满、图标之间出现巨大空洞。
     */
    fun columns(availableWidth: Dp, compact: Boolean): Int {
        val cell = if (compact) CompactCellWidth else CellWidth
        if (availableWidth <= 0.dp) return 4
        return (availableWidth / cell).toInt().coerceIn(3, 8)
    }
}

/**
 * 主页：标签 + 应用网格 + 多选批量操作。
 *
 * 页面骨架由 [PageFrame] 统一：录制层在正文、顶栏在 topBar 槽、浮层在 overlay 槽
 * （浮层永远在 Scaffold 之后组合）。所有弹层都“常驻组合 + show 驱动”，
 * 关闭时保留最后一次内容，保证退出动画、键盘与返回键都由官方组件处理。
 *
 * **标题不随多选变化**：大标题恒为应用名，多选进度写进官方副标题（`title` + `subtitle`
 * 是 MIUIX 顶栏的既有两行，`TopAppBar.kt` 里没有第三行、也没有按状态换字号的入口）。
 * 早先为了把「已选 N 个」塞进大标题，额外做了一套中文字形补偿（0.83 字号缩放 +
 * 整条顶栏交叉淡入淡出），那是自造的排版层：同一个 `title1` 被临时缩成 26.5sp，
 * 与其它页的大标题不再同源。现在改回官方两行，不再有任何字号缩放。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalScrollBarApi::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as GlacierApplication
    val repository = app.repository
    val preferences = app.preferences
    val snapshot: AppSnapshot = repository.snapshot
    // compactGrid / homeLabelSize / 搜索与筛选设置都是直接读 SharedPreferences 的普通属性，
    // 不是快照状态。订阅 revision 并放进下面的 remember key，设置页改完返回后
    // 列数、字号和列表内容才会立即刷新，不必切页重建。
    val revision = preferences.revision.intValue

    var selectedTag by rememberSaveable { mutableIntStateOf(DEFAULT_TAG_ID) }
    var selecting by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(emptySet<String>()) }
    var searchOpen by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<String?>(null) }
    // 菜单作用域：`true` = 多选集合。打开时定下来，退场期间保持不变
    // （否则"移除"类动作清空选区后，标题会在退场动画里突然变成「已选择：0」）。
    var actionBatch by remember { mutableStateOf(false) }
    var actionCount by remember { mutableStateOf(0) }
    var tagEditorOpen by remember { mutableStateOf(false) }
    var tagPickerOpen by remember { mutableStateOf(false) }
    // 标签弹层的作用域快照：打开时锁定，弹层里勾选的过程中即使选区变了也不会串。
    var tagPickerTargets by remember { mutableStateOf(emptyList<String>()) }
    var tagNameDialog by remember { mutableStateOf<TagNameRequest?>(null) }
    var deleteTagTarget by remember { mutableStateOf<Tag?>(null) }
    var deferredTarget by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    // 长按那一刻记下被长按格的官方锚点；`GlassTransformPopup` 从它原地展开。
    // 只在长按回调里写入（不在组合期猜），网格滚动收起菜单时也不会用到已回收的锚点。
    var popupAnchor by remember { mutableStateOf<GlassPopupAnchor?>(null) }
    // 浮层常驻组合，第一次打开前也需要一个合法锚点；它永远不会被展示。
    val placeholderAnchor = rememberGlassPopupAnchor()
    val runner = remember(repository) { AppActionRunner(context, repository) }
    val selectionRunner = remember(repository) { SelectionActionRunner(context, repository) }
    val glassActive = LocalGlassActive.current
    val searchListState = rememberLazyListState()
    // 网格滚动状态只有一个用途：告诉顶栏下的标签行"内容是否已经滚进顶栏"，
    // 决定玻璃档 surfaceAlpha（官方 `IconPage.kt:283`：`if (blurActive && contentUnderTopBar) 1f else 0f`）。
    val gridState = rememberLazyGridState()
    // 菜单锚定在某一格上：一旦网格开始滚动，那一格的坐标就变了，浮层会"漂"在别处。
    // 直接收起（比让菜单跟着滚更符合"就地弹出"的心智，官方 popup 也只处理点外部/返回键）。
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.isScrollInProgress }.collect { scrolling ->
            if (scrolling) actionTarget = null
        }
    }
    // 对齐 0.9.5-beta03 迁移要点：canScrollBackward / overscrollOffset 都是逐帧值，
    // 用 computedStateOf 收敛成 0f/1f 两个离散状态，只有翻转时才重组顶栏；
    // 叠加 overscrollOffset < -1f 让顶部回弹时轨道也能浮起，模糊关闭时保持静止态 0f。
    val tabSurfaceAlpha by remember(gridState, scrollBehavior, glassActive) {
        computedStateOf {
            if (glassActive && (gridState.canScrollBackward || scrollBehavior.state.overscrollOffset < -1f)) {
                1f
            } else {
                0f
            }
        }
    }

    // 标签被删除后，选中项要回到默认标签，否则页面会空白。
    if (snapshot.tags.none { it.id == selectedTag }) {
        selectedTag = DEFAULT_TAG_ID
    }

    val entries = remember(snapshot, selectedTag, revision) { repository.homeApps(selectedTag) }
    val tagLabel = snapshot.tags.firstOrNull { it.id == selectedTag }?.let { repository.tagLabel(it) }
        ?: stringResource(R.string.label_default)

    val inSelection = selecting || selection.isNotEmpty()

    // 两行文案都是官方顶栏自带的位置：大标题恒为应用名，副标题表达"哪一组 / 选了多少"。
    // 多选进度放在副标题，不再靠换大标题 + 缩字号（见函数头的说明）。
    // 「已全部冻结」这半句是必要的状态出口：此时冻结入口（FAB / 多选）都隐去了，
    // 若用户又关了「冻结图标去色」，整页就再无任何线索说明"为什么没有冻结按钮"。
    val allFrozen = entries.isNotEmpty() && entries.all { it.frozen }
    val tagSubtitle = buildString {
        append(tagLabel)
        append(" · ")
        append(stringResource(R.string.app_count, entries.size))
        if (allFrozen) {
            append(" · ")
            append(stringResource(R.string.home_all_frozen))
        }
    }
    val selectionSubtitle =
        stringResource(R.string.selected_count, selection.size) + " · " + tagLabel

    // 多选态下返回键先退出多选，而不是结束 Activity（与 Apps 页同一出口）。
    BackHandler(enabled = inSelection) {
        selecting = false
        selection = emptySet()
    }

    // 浮层退场期间继续使用最后一次内容（规范 0 节红线 1）。
    var lastActionEntry by remember { mutableStateOf<AppEntry?>(null) }
    // 预热：浮层常驻组合（官方要求），首帧就要有完整菜单内容。否则第一次长按时会先按
    // "空菜单"量一次面板，`GlassPopupSurface` 的固有尺寸缓存会把这次量到的极小尺寸记住，
    // 内容随后补上也不再重量（表现为只留一条细条）。用列表第一项预热即可：浮层此时不可见，
    // 真正被长按的条目会在同一次组合里替换进来。
    if (lastActionEntry == null) lastActionEntry = entries.firstOrNull()
    var lastTagNameRequest by remember { mutableStateOf<TagNameRequest?>(null) }
    var lastDeleteTag by remember { mutableStateOf<Tag?>(null) }
    var lastDeferredPackage by remember { mutableStateOf<String?>(null) }
    actionTarget?.let { pkg ->
        snapshot.all.firstOrNull { it.packageName == pkg }?.let { lastActionEntry = it }
    }
    tagNameDialog?.let { lastTagNameRequest = it }
    deleteTagTarget?.let { lastDeleteTag = it }
    deferredTarget?.let { lastDeferredPackage = it }

    PageFrame(
        // 标题恒为应用名：多选态只换副标题，顶栏结构与高度都不变。
        title = stringResource(R.string.app_name),
        subtitle = if (inSelection) selectionSubtitle else tagSubtitle,
        scrollBehavior = scrollBehavior,
        searchExpanded = searchOpen,
        // 官方图标网格页每页显式加滚动条（`example/IconPage.kt` / `MainPage.kt:341-345`）。
        scrollBarAdapter = rememberScrollBarAdapter(gridState).takeIf { entries.isNotEmpty() },
        topBarActions = {
            // 顶栏**只有一态**：搜索 / 多选 / 编辑标签。多选时不换成批量按钮那一排
            // （那一排的代码已删除），它的执行出口是右下角 FAB（见 floatingActionButton）。
            // 动作间距 8dp 是官方示例的值（`example/GlassPage.kt:363`）。
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TopBarAction(MiuixIcons.Glass.Search, stringResource(R.string.search)) {
                    searchOpen = true
                }
                // 同一个按钮进/出多选：顶栏不再提供"关闭"，没有别的地方能退出这个模式。
                // 常驻不隐藏：冻结入口在页面上的位置必须稳定，不能因为"这一页全冻上了"
                // 就让顶栏少一个按钮（布局跳动比"点了没反应"更让人困惑）；
                // 全冻时进去确实没有可执行的目标，由 FAB 的点击反馈说明原因。
                TopBarAction(MiuixIcons.Glass.MultiSelect, stringResource(R.string.action_select)) {
                    if (inSelection) {
                        selecting = false
                        selection = emptySet()
                    } else {
                        selecting = true
                        UiFeedback.toast(context, R.string.hint_tap_to_select)
                    }
                }
                TopBarAction(MiuixIcons.Glass.Edit, stringResource(R.string.title_tag_editor)) {
                    tagEditorOpen = true
                }
            }
        },
        // 页签是顶栏的子级（官方 `GlassPage.kt:394-401` 的 bottomContent）：
        // 玻璃档由顶栏上下文自动继承 backdrop，不能放进正文网格。
        // **多选时页签保持原位**：收起它会让顶栏矮下去、正文整体上移，进多选/退多选都要跳一次；
        // 页签本身是"切换看哪个标签"，与"挑几项操作"不冲突，没必要让位。
        // 只有标签数变成 1 个（没有可切换对象）时才收，仍用官方 `CapturedAnimatedVisibility`。
        topBarBottom = { topBarBackdrop ->
            CapturedAnimatedVisibility(
                visible = snapshot.tags.size > 1,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                TagRow(
                    tags = snapshot.tags,
                    selectedTag = selectedTag,
                    labelOf = { repository.tagLabel(it) },
                    backdrop = topBarBackdrop,
                    surfaceAlpha = tabSurfaceAlpha,
                    onSelect = { selectedTag = it },
                )
            }
        },
        // FAB 的位置交给 Scaffold：新版 PageFrame 已把底栏高度计入 bottomBar，
        // 这里不再手算 `padding(bottom = reserve - 20.dp)`。
        // 只有"这一页没有任何应用"（空态）时才不挂 FAB；挂载本身仍走官方动画
        // （`CapturedAnimatedVisibility` + `GlassMotion.scaleIn/scaleOut`），进出不硬切。
        floatingActionButton = { fabBackdrop ->
            // 作用范围：选了几个就只处理这几个，没选则整页。
            val scopeEntries = if (selection.isEmpty()) {
                entries
            } else {
                entries.filter { it.packageName in selection }
            }
            // 「冻结」就只是冻结：主页唯一的批量入口不再按状态翻成解冻。
            // 解冻只有两个出口——点开应用自动解冻（`AppActionRunner.launch`）、
            // 长按应用在菜单里选「解冻」；两者都是"针对某个应用"的决定，
            // 而不是"这一页现在全冻着，所以这个按钮变成反操作"。
            // **FAB 常驻**：只要这一页有应用就一直在原位，不随冻结状态出现/消失
            // （位置和语义都稳定，用户不需要先读一遍图标再猜它是什么）。
            // 范围内已经没有可冻结目标时，点它给一句话说明（官方 Glass FAB 没有
            // `enabled` 形参，不做自造的灰化禁用态）。
            CapturedAnimatedVisibility(
                visible = entries.isNotEmpty(),
                enter = fadeIn(GlassMotion.barExpand()) + scaleIn(GlassMotion.scaleIn(), initialScale = 0.8f),
                exit = fadeOut(GlassMotion.barCollapse()) + scaleOut(GlassMotion.scaleOut(), targetScale = 0.8f),
            ) {
                GlassFloatingActionButton(
                    onClick = {
                        // 目标在点击时才取一次，避免长按/退场动画期间列表变化导致冻错对象。
                        val targets = scopeEntries.filter { !it.frozen }.map { it.packageName }
                        if (targets.isEmpty()) {
                            // 没有可冻结目标：如实说明，而不是静默无反应。
                            // 多选态说"所选"，整页态才说"全部"——同一个事实两种范围不能共用一句话。
                            UiFeedback.toast(
                                context,
                                if (selection.isEmpty()) {
                                    R.string.home_all_frozen
                                } else {
                                    R.string.home_selection_all_frozen
                                },
                            )
                            return@GlassFloatingActionButton
                        }
                        scope.launch {
                            val outcome = repository.setFrozenBatch(targets, true)
                            reportFreezeBatch(context, outcome, true)
                            // 真正落地了才退出多选：失败（权限/后端不可用）时保留选区，
                            // 否则用户白选一遍、还得重新挑。
                            if (selection.isNotEmpty() && outcome is FreezeOutcome.Applied && outcome.count > 0) {
                                selecting = false
                                selection = emptySet()
                            }
                        }
                    },
                    backdrop = fabBackdrop,
                ) {
                    Icon(
                        imageVector = MiuixIcons.Glass.QuietMode,
                        contentDescription = stringResource(R.string.action_freeze),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        },
        overlay = { pageBackdrop ->
            // 搜索浮层常驻组合：只由 expanded 驱动，官方组件负责 400ms 进出动画、
            // query 清理、焦点/键盘与返回键（GlassSearchOverlay.kt:229-245）。
            // onDismissRequest 只关闭搜索，绝不触达标签编辑。
            // 让位与页面正文同一套官方契约：横向 = contentInsets（安全区 + 悬浮侧栏让位），
            // 顶部 = safeInsets.top，折叠胶囊用 searchTop（官方 IconPage.kt:495-498）。
            GlassSearchOverlay(
                expanded = searchOpen,
                onDismissRequest = { searchOpen = false },
                scrollMaterialThreshold = GlassSearchOverlayDefaults.VerticalPadding + SearchResultVerticalPadding,
                // 结果滚动时搜索条转玻璃：官方用第一项滚动偏移近似（IconPage.kt:508-514）。
                scrollOffset = {
                    when {
                        searchListState.layoutInfo.totalItemsCount == 0 -> 0f
                        searchListState.firstVisibleItemIndex > 0 -> Float.MAX_VALUE
                        else -> searchListState.firstVisibleItemScrollOffset.toFloat()
                    }
                },
                horizontalInsetsModifier = Modifier.appPageHorizontalInsets(),
                topInsets = LocalAppPageInsets.current.safeInsets.only(WindowInsetsSides.Top),
                searchTopPadding = LocalAppPageInsets.current.searchTop,
                label = stringResource(R.string.search_hint),
                blurEnabled = glassActive,
                searchIcon = MiuixIcons.Glass.Search,
                closeIcon = MiuixIcons.Glass.Close,
            ) { query ->
                // 与 Apps 页同一个搜索结果实现（`AppSearchResults`：官方 Card + BasicComponent 行、
                // 同一套让位与空态）。这里不再维护第二份手写列表。
                AppSearchResults(
                    query = query,
                    listState = searchListState,
                    onPick = { entry ->
                        searchOpen = false
                        scope.launch { runner.run(entry, AppAction.Launch) }
                    },
                )
            }

            // 锚点菜单：从被长按的那一格**原地长大**成菜单（官方 `GlassTransformPopup`）。
            // 作用域在打开时定死：
            // - 长按的是已选中的应用 → 批量菜单，标题「已选择：N」；
            // - 长按的是未选中的应用（含非多选态）→ 单个应用菜单，标题是应用名。
            val menuTarget = lastActionEntry
            val handleMenuAction: (AppAction) -> Unit = { action ->
                val entry = menuTarget
                val batchTargets = if (actionBatch) {
                    snapshot.home.filter { it.packageName in selection }
                } else {
                    emptyList()
                }
                actionTarget = null
                when {
                    actionBatch -> when (action) {
                        // 批量打标签要弹标签多选层，是 UI 决策，不交给批量执行器。
                        AppAction.EditTags -> {
                            tagPickerTargets = batchTargets.map { it.packageName }
                            tagPickerOpen = true
                        }

                        else -> scope.launch {
                            // 移除类动作会让这批应用不再在主页，此时多选也没有对象了。
                            if (selectionRunner.run(batchTargets, action)) {
                                selection = emptySet()
                                selecting = false
                            }
                        }
                    }

                    entry != null -> when (action) {
                        AppAction.EditTags -> tagNameDialog = TagNameRequest.Assign(entry.packageName)
                        AppAction.DeferredFreeze -> deferredTarget = entry.packageName
                        else -> scope.launch {
                            runner.run(entry, action)
                            // 移出主页后这一行没了，多选里也不该再留着它。
                            if (action == AppAction.RemoveFromHome ||
                                action == AppAction.UnfreezeAndRemoveFromHome
                            ) {
                                selection = selection - entry.packageName
                            }
                        }
                    }
                }
            }

            val menuActions = when {
                menuTarget == null -> emptyList()
                actionBatch -> homeSelectionMenuActions()
                else -> homeMenuActions(menuTarget)
            }
            // 当前长按格的锚点；`popupAnchor` 在退场期间保持最后一格（锚点不能中途变）。
            val menuAnchor = popupAnchor ?: placeholderAnchor
            // 菜单标题：单应用是应用名，多选是「已选择：N」；退场期间沿用最后一份。
            val popupTitle = if (actionBatch) {
                stringResource(R.string.home_selected_title, actionCount)
            } else {
                menuTarget?.label.orEmpty()
            }
            // 锚点浮层提升到根部 TopLayer：不进页面录制层（避免自采样），也不被玻璃侧栏盖住。
            // 浮层必须**常驻组合**，只用 `show` 控制开合（官方文档："关闭时仍须保留组件调用，
            // 直到离场动画完成；不要使用 `if (show)` 包裹调用"）。首次长按才组合的话，
            // `updateTransition` 的初始值已经是 `true`，展开动画不会播——这正是"第一次长按
            // 直接弹出、没有动画"的原因。浮层未激活时 `GlassPopupSurface` 不组合，
            // 真正激活那一帧的内容由下面的 `lastActionEntry` 预热保证是完整菜单。
            TopLayer {
                GlassTransformPopup(
                    show = actionTarget != null,
                    onDismissRequest = { actionTarget = null },
                    anchor = menuAnchor,
                    // 采样页面录制层：浮层自己不在这一层里（TopLayer 在外），不会采到自己。
                    backdrop = pageBackdrop,
                    anchorContent = {
                        menuTarget?.let { target ->
                            AppIconView(
                                packageName = target.packageName,
                                size = PageMetrics.GridAppIcon,
                                frozen = target.frozen,
                            )
                        }
                    },
                    // 与官方 example 一致：普通展开不复制锚点外围内容，只做原地生长。
                    simplified = true,
                    gap = 8.dp,
                ) {
                    menuTarget?.let { target ->
                        Text(
                            text = popupTitle,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceContainerVariant,
                            // 面板宽度交回官方内容驱动（GlassPopupDefaults.MinWidth/MaxWidth）；
                            // 这里只给菜单行的同一套内边距，不再写死宽度把面板顶到 MaxWidth。
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
                        menuActions.forEach { action ->
                            val row = actionSpecOf(action, target, handleMenuAction)
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

            TagPickerSheet(
                show = tagPickerOpen,
                tags = snapshot.tags,
                packageNames = tagPickerTargets,
                entries = snapshot.home,
                onDismiss = { tagPickerOpen = false },
                onToggle = { tag, add ->
                    scope.launch {
                        repository.setTagForApps(tagPickerTargets, tag.id, add)
                    }
                },
                labelOf = { repository.tagLabel(it) },
            )

            TagEditorSheet(
                tags = snapshot.tags,
                show = tagEditorOpen,
                onDismiss = { tagEditorOpen = false },
                onNew = { tagNameDialog = TagNameRequest.Create },
                onRename = { tag -> tagNameDialog = TagNameRequest.Rename(tag) },
                onDelete = { tag -> deleteTagTarget = tag },
                labelOf = { repository.tagLabel(it) },
            )

            TagNameDialog(
                request = lastTagNameRequest,
                show = tagNameDialog != null,
                onDismiss = { tagNameDialog = null },
                onConfirm = { request, name ->
                    scope.launch {
                        when (request) {
                            is TagNameRequest.Create -> repository.addTag(name)
                            is TagNameRequest.Rename -> repository.renameTag(request.tag.id, name)
                            is TagNameRequest.Assign -> {
                                val current = repository.entryOf(request.packageName)?.tagIds ?: emptyList()
                                val tag = snapshot.tags.firstOrNull { it.name == name }
                                if (tag != null) {
                                    val next = if (tag.id in current) current - tag.id else current + tag.id
                                    repository.setTags(request.packageName, next)
                                }
                            }
                        }
                        tagNameDialog = null
                    }
                },
            )

            DeleteTagDialog(
                tag = lastDeleteTag,
                show = deleteTagTarget != null,
                onDismiss = { deleteTagTarget = null },
                onConfirm = { tag ->
                    scope.launch { repository.deleteTag(tag.id) }
                    deleteTagTarget = null
                    if (selectedTag == tag.id) selectedTag = DEFAULT_TAG_ID
                },
            )

            DeferredSheet(
                show = deferredTarget != null,
                onDismiss = { deferredTarget = null },
                onPick = { minutes ->
                    lastDeferredPackage?.let { packageName ->
                        com.ayang.glacier.platform.auto.AutoFreezeScheduler.scheduleDeferredFreeze(
                            context,
                            packageName,
                            true,
                            minutes,
                        )
                        // 提示语是"已安排 N 分钟后冻结"，不是菜单项标题「延时冻结」。
                        UiFeedback.toast(
                            context,
                            context.getString(R.string.home_deferred_freeze_scheduled, minutes.toInt()),
                        )
                    }
                    deferredTarget = null
                },
            )
        },
    ) { contentPadding ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // 列数要按"减掉页面横向留白后的可用宽度"算；这里在组合期读 padding 是刻意的：
            // 列数变化必须重组网格，而 12dp 留白与侧栏让位只在宽度/形态变化时才变。
            val available = maxWidth - contentPadding.calculateLeftPadding(LocalLayoutDirection.current) -
                contentPadding.calculateRightPadding(LocalLayoutDirection.current)
            val columns = HomeGridMetrics.columns(available, preferences.compactGrid)
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                // 页面留白走统一 token（12dp），不再出现 16dp 硬编码。
                contentPadding = contentPadding,
                // 弹性 overscroll 由 PageFrame 的官方 pageScrollModifiers 统一消费，
                // 列表自己再开一份会双份回弹（官方所有列表页都在这里传 null）。
                overscrollEffect = null,
                horizontalArrangement = Arrangement.spacedBy(HomeGridMetrics.HorizontalSpacing),
                verticalArrangement = Arrangement.spacedBy(HomeGridMetrics.VerticalSpacing),
            ) {
            if (entries.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(text = stringResource(R.string.empty_home))
                }
            } else {
                items(entries, key = { it.packageName }) { entry ->
                    HomeAppItem(
                        entry = entry,
                        labelSize = preferences.homeLabelSize,
                        selected = entry.packageName in selection,
                        selectionMode = inSelection,
                        onClick = {
                            if (inSelection) {
                                selection = if (entry.packageName in selection) {
                                    selection - entry.packageName
                                } else {
                                    selection + entry.packageName
                                }
                            } else {
                                scope.launch { runner.run(entry, AppAction.Launch) }
                            }
                        },
                        // 长按的作用域按"这一个应用有没有被选中"决定，而不是"在不在多选态"：
                        // - 已选中 → 批量菜单（「已选择：N」），操作整批；
                        // - 未选中（含非多选态）→ 该应用的单应用菜单。
                        // 这样长按的对象永远落在作用域里，不会出现"我长按 A，菜单却操作 B、C"。
                        onLongClick = { anchor ->
                            actionBatch = entry.packageName in selection
                            actionCount = selection.size
                            popupAnchor = anchor
                            actionTarget = entry.packageName
                        },
                    )
                }
            }
            }
        }
    }
}

/**
 * 网格单元。
 *
 * 点击热区是整格 [Column]（含标签）：点 = 启动（多选态里 = 切换勾选）；
 * 长按 = 打开锚点菜单（作用域见调用点：已选中 → 批量，未选中 → 单应用）。
 *
 * **锚点**：整个单元用 `glassPopupAnchor` 登记为锚点（官方要求挂在控件最外层，
 * 不是图标上），图标节点再用 `glassPopupAnchorContent` 标出"过渡里要复制的内容"。
 * 菜单展开时 `GlassTransformPopup` 会把锚点内容淡出，所以长按后不会有任何残留的按下层。
 *
 * **按压叠层只覆盖图标**：`combinedClickable` 的 `indication` 置 `null`，改由图标
 * 自己订阅同一个 `interactionSource`（`Modifier.indication`）——与官方 `GlassIconButton`
 * 的写法同构（`matchParentSize().clip(shape).indication(...)`）。
 * 叠层因此是图标的 squircle，而不是"整格含标签"的一块方块；松手后由
 * `MiuixIndication` 自己淡出（beta04 的 200ms 释放保持），不再有任何外力维持按下态。
 * 锚点浮层展开时整格内容由 `glassPopupAnchor` 隐藏，按下叠层和标签都不会残留。
 *
 * **选中态**：官方 [Checkbox] 叠在图标右上角作角标；`onClick = null`（整格手势已由
 * `combinedClickable` 承担），勾选框只做状态显示。网格里只在已选中时画角标：
 * 未选中态是不透明的 `secondary` 圆，叠在 56dp 图标上会把图标糊掉。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeAppItem(
    entry: AppEntry,
    labelSize: Float,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: (GlassPopupAnchor) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val anchor = rememberGlassPopupAnchor()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            // 整格登记为锚点：浮层从这一格原地长大，展开期间整格（图标 + 标签）一起隐藏。
            .glassPopupAnchor(anchor, cornerRadius = HomeGridMetrics.CellCorner)
            .combinedClickable(
                interactionSource = interactionSource,
                // 叠层不铺整格：由下面的图标自己画（见函数头）。
                indication = null,
                onClick = onClick,
                onLongClick = { onLongClick(anchor) },
            ),
    ) {
        Box(
            modifier = Modifier
                .size(PageMetrics.GridAppIcon)
                .squircleClip(HomeGridMetrics.CellCorner)
                .indication(interactionSource, LocalIndication.current),
        ) {
            AppIconView(
                packageName = entry.packageName,
                size = PageMetrics.GridAppIcon,
                frozen = entry.frozen,
                // 过渡动画里复制的是图标本身（不含左上角的勾选角标与按压叠层）。
                modifier = Modifier.glassPopupAnchorContent(anchor),
            )
            if (selectionMode && selected) {
                Checkbox(
                    state = ToggleableState.On,
                    onClick = null,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
        }
        Spacer(Modifier.height(HomeGridMetrics.IconLabelGap))
        Text(
            text = entry.label,
            // 与列表行同一个排版权威（`AppLabelTextStyle`：footnote1 字号 + Medium 字重），
            // 字号仍由「主页图标字号」设置覆盖，但不再与列表标题出现字重/截断方式的分叉。
            style = AppLabelTextStyle(labelSize),
            color = if (entry.frozen) MiuixTheme.colorScheme.onSurfaceVariantSummary
            else MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 标签页签。
 *
 * 两档都是官方"页面级标签"的范式（不是组件演示页的特例档）：
 *
 * - **Glass 开**：官方 [GlassSegmentedTabRow]。官方 example 的图标网格页
 *   （`IconPage.kt:274-285`，和 Home 一样是"顶栏 bottomContent + 网格"）以及 Mishka 的
 *   Provider 页（`ProviderScreen.kt:133-146`）都用它做页面标签：相连轨道 + 滑动指示器，
 *   选中态只是 6%（浅色）/13.3%（深色）的半透明指示器，文字才是强调元素。
 *   `surfaceAlpha` 按内容是否滚进顶栏给：顶栏下没有内容时轨道回到静止底色，
 *   与官方 `IconPage.kt:283` 的 `if (blurActive && contentUnderTopBar) 1f else 0f` 同义。
 * - **Glass 关**：官方 [TabRow]（`IconPage.kt:308-315` 的非玻璃分支），
 *   选中指示器是 `surfaceContainer`（浅色白 / 深色近黑）胶囊，与页面同为中性色。
 *
 * 旧实现在玻璃档用 `GlassTabRow(colors = neutralColors())`：neutral 是"内容强调色过强时"的
 * 单色档，官方唯一示例是组件演示页且必须配 `NeutralHeight = 35dp`
 * （`GlassTabRow.kt:210-262` KDoc、`docs/zh_CN/components/glass/glasstabrow.md:44-58`、
 * `GlassPage.kt:237-249`），它的选中填充在浅色下是 `#383838`（`GlassTabRow.kt:237-243`）——
 * 也就是实拍里的"黑色胶囊 + 白字"。既不是页面标签的官方范式，也与 Glass 关档的轻指示器割裂。
 */
@Composable
private fun TagRow(
    tags: List<Tag>,
    selectedTag: Int,
    labelOf: (Tag) -> String,
    backdrop: LayerBackdrop?,
    surfaceAlpha: Float,
    onSelect: (Int) -> Unit,
) {
    val names = remember(tags) { tags.map(labelOf) }
    val index = tags.indexOfFirst { it.id == selectedTag }.coerceAtLeast(0)
    // 分段轨道要求"有界宽度 + 互不相同的标签"（docs/zh_CN/components/glass/glasstabrow.md:72），
    // 标签太多时每格会被压到放不下；超过 4 个退回官方 TabRow（它本身就支持横向滚动）。
    val segmentedBackdrop = backdrop.takeIf { LocalGlassActive.current }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PageMetrics.Horizontal)
            .padding(top = 6.dp, bottom = PageMetrics.Horizontal),
    ) {
        if (segmentedBackdrop != null && names.size in 2..4) {
            GlassSegmentedTabRow(
                tabs = names,
                selectedIndex = index,
                onSelect = { onSelect(tags[it].id) },
                backdrop = segmentedBackdrop,
                surfaceAlpha = surfaceAlpha,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            TabRow(
                tabs = names,
                selectedTabIndex = index,
                onTabSelected = { onSelect(tags[it].id) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * 多选态的「标签分组」弹层。
 *
 * 单应用的标签编辑是"输入标签名 → 存在就切换"（[TagNameRequest.Assign]），
 * 批量场景下不能用同一套：要一次看好几个标签、还要能表达"这批应用里只有一部分带了这个标签"。
 * 所以这里用官方 [Checkbox] 的三态 + 官方 [BasicComponent] 行：
 *
 * - `On`（全部已带）→ 点一下**全部移除**
 * - `Off` / `Indeterminate`（部分或没有）→ 点一下**全部添加**
 *
 * 勾选立即生效（[onToggle] 直接落盘），不设"保存"——与设置页的开关同一手感；
 * 底部只留一个「完成」用来收起弹层。作用域在打开时锁定（[packageNames]），
 * 弹层里改标签不会因为主页选区变化而串对象。
 */
@Composable
private fun TagPickerSheet(
    show: Boolean,
    tags: List<Tag>,
    packageNames: List<String>,
    entries: List<AppEntry>,
    onDismiss: () -> Unit,
    onToggle: (Tag, Boolean) -> Unit,
    labelOf: (Tag) -> String,
) {
    val targets = remember(entries, packageNames) {
        entries.filter { it.packageName in packageNames }
    }
    val states = remember(targets, tags) {
        tags.associate { tag ->
            val withTag = targets.count { tag.id in it.tagIds }
            tag.id to when {
                withTag == 0 -> ToggleableState.Off
                withTag == targets.size -> ToggleableState.On
                else -> ToggleableState.Indeterminate
            }
        }
    }
    val navigationBarBottom = rememberAppSafePadding().calculateBottomPadding()
    GlassBottomSheet(
        onDismissRequest = onDismiss,
        show = show,
        backdrop = LocalRootBackdrop.current.takeIf { LocalGlassActive.current },
        title = stringResource(R.string.action_edit_tags),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .scrollEndHaptic(),
            // 新版 GlassBottomSheet 的标题栏**覆盖**在内容上（progressiveBlur），
            // 首屏内容必须让出实测的标题栏高度，否则第一张卡会压到标题下面。
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = navigationBarBottom + PageMetrics.SectionGap,
            ),
        ) {
            item(key = "tags") {
                GlassCard(Modifier.fillMaxWidth()) {
                    tags.forEach { tag ->
                        val state = states[tag.id] ?: ToggleableState.Off
                        BasicComponent(
                            title = labelOf(tag),
                            onClick = { onToggle(tag, state != ToggleableState.On) },
                            // 勾选框自己也受理点击：与行同义（点哪里都是切换）。
                            endActions = {
                                Checkbox(state = state, onClick = { onToggle(tag, state != ToggleableState.On) })
                            },
                        )
                    }
                }
            }
            item(key = "done") {
                GlassDialogButton(
                    onClick = onDismiss,
                    role = GlassDialogButtonRole.Primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = PageMetrics.SectionGap),
                ) {
                    Text(stringResource(R.string.done), style = MiuixTheme.textStyles.button)
                }
            }
        }
    }
}

/**
 * 标签编辑 BottomSheet。
 *
 * 常驻组合 + `show`：关闭时仍保留行内容，官方组件自己播放退出动画。
 * 内容结构照官方 `component/BottomSheetSection.kt:209-267`：`Card` 分组 + `BasicComponent` 行，
 * 弹层横向 24dp 内边距由 [GlassBottomSheet] 默认 `insideMargin` 提供，不再叠 24dp 外层 padding。
 */
@Composable
private fun TagEditorSheet(
    tags: List<Tag>,
    show: Boolean,
    onDismiss: () -> Unit,
    onNew: () -> Unit,
    onRename: (Tag) -> Unit,
    onDelete: (Tag) -> Unit,
    labelOf: (Tag) -> String,
) {
    val backdrop = LocalRootBackdrop.current.takeIf { LocalGlassActive.current }
    GlassBottomSheet(
        onDismissRequest = onDismiss,
        show = show,
        backdrop = backdrop,
        title = stringResource(R.string.title_tag_editor),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(top = innerPadding.calculateTopPadding()),
        ) {
            item {
                GlassCard(Modifier.padding(bottom = PageMetrics.SectionGap)) {
                    tags.forEach { tag ->
                        BasicComponent(
                            title = labelOf(tag),
                            endActions = {
                                IconButton(onClick = { onRename(tag) }) {
                                    Icon(
                                        imageVector = MiuixIcons.Glass.Edit,
                                        contentDescription = stringResource(R.string.rename),
                                        modifier = Modifier.size(24.dp),
                                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                                IconButton(onClick = { onDelete(tag) }) {
                                    Icon(
                                        imageVector = MiuixIcons.Glass.Delete,
                                        contentDescription = stringResource(R.string.action_delete_tag),
                                        modifier = Modifier.size(24.dp),
                                        tint = MiuixTheme.colorScheme.error,
                                    )
                                }
                            },
                        )
                    }
                }
            }
            item {
                GlassCard(Modifier.padding(bottom = PageMetrics.SectionGap)) {
                    SheetActionRow(
                        icon = MiuixIcons.Glass.Add,
                        label = stringResource(R.string.action_new_tag),
                        onClick = onNew,
                    )
                }
            }
            item { Spacer(Modifier.height(PageMetrics.BottomGap)) }
        }
    }
}

/**
 * 标签名 Dialog（新建 / 重命名 / 给应用分配标签）。
 *
 * 常驻组合 + `show`，`request` 保留最后一次内容用于退场；
 * 文本状态以 `request` 为 key，换一个请求会重新初始化，关闭时不会清空。
 * 动作按钮照官方 `GlassPage.kt:422-436`：`Row(spacedBy(12.dp))` + 两个 `weight(1f)`。
 */
@Composable
private fun TagNameDialog(
    request: TagNameRequest?,
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (TagNameRequest, String) -> Unit,
) {
    if (request == null) return
    val initial = when (request) {
        is TagNameRequest.Rename -> request.tag.name
        else -> ""
    }
    // 官方新版输入框是 TextFieldState 驱动（旧的 value/onValueChange 重载已 @Deprecated）。
    // 常驻组合下状态不会随关闭销毁，因此每次重新打开都显式写回该请求的初始文本。
    val textState = rememberTextFieldState(initial)
    LaunchedEffect(show, request) {
        if (show) textState.setTextAndPlaceCursorAtEnd(initial)
    }
    // 读 TextFieldState.text 会订阅快照状态：输入变化时按钮 enabled 与点击值同步更新。
    val text = textState.text
    val titleRes = when (request) {
        is TagNameRequest.Create -> R.string.action_new_tag
        is TagNameRequest.Rename -> R.string.action_rename_tag
        is TagNameRequest.Assign -> R.string.action_edit_tags
    }

    GlassDialog(
        onDismissRequest = onDismiss,
        show = show,
        backdrop = LocalRootBackdrop.current.takeIf { LocalGlassActive.current },
        title = stringResource(titleRes),
    ) {
        // 玻璃档用官方 GlassTextField（官方文档指定用于 GlassDialog / GlassBottomSheet 这类
        // 半透明的玻璃表面）；非玻璃档用普通 TextField，避免玻璃半透明底色落在实色弹层上。
        if (LocalGlassActive.current) {
            GlassTextField(
                state = textState,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.tag_name_hint),
            )
        } else {
            TextField(
                state = textState,
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.tag_name_hint),
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassDialogButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.cancel), style = MiuixTheme.textStyles.button)
            }
            GlassDialogButton(
                onClick = { if (text.isNotBlank()) onConfirm(request, text.toString().trim()) },
                enabled = text.isNotBlank(),
                role = GlassDialogButtonRole.Primary,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.save), style = MiuixTheme.textStyles.button)
            }
        }
    }
}

/**
 * 删除标签确认 Dialog。
 *
 * 常驻组合 + `show`，`tag` 保留最后一次内容；按钮横向 `spacedBy(12.dp)` + `weight(1f)`，
 * 不再是上下贴在一起的两个全宽按钮。
 */
@Composable
private fun DeleteTagDialog(
    tag: Tag?,
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Tag) -> Unit,
) {
    GlassDialog(
        onDismissRequest = onDismiss,
        show = show,
        backdrop = LocalRootBackdrop.current.takeIf { LocalGlassActive.current },
        title = stringResource(R.string.action_delete_tag),
        summary = tag?.let { stringResource(R.string.home_delete_tag_summary, it.name) }
            ?: stringResource(R.string.delete_tag_message),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassDialogButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.cancel), style = MiuixTheme.textStyles.button)
            }
            GlassDialogButton(
                onClick = { tag?.let(onConfirm) },
                enabled = tag != null,
                role = GlassDialogButtonRole.Destructive,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.delete), style = MiuixTheme.textStyles.button)
            }
        }
    }
}

/**
 * 延时冻结 BottomSheet。
 *
 * 常驻组合 + `show`，行统一 [SheetActionRow]（24dp 图标 + `BasicComponent`），
 * 用 `Timer` 图标与应用操作面板里的“延时冻结”保持一致。
 */
@Composable
private fun DeferredSheet(
    show: Boolean,
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
) {
    val options = remember { listOf(5L, 15L, 30L, 60L) }
    GlassBottomSheet(
        onDismissRequest = onDismiss,
        show = show,
        backdrop = LocalRootBackdrop.current.takeIf { LocalGlassActive.current },
        title = stringResource(R.string.action_deferred_freeze),
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxWidth()
                // 让出实测标题栏高度（新版标题栏覆盖在内容之上）。
                .padding(top = innerPadding.calculateTopPadding()),
        ) {
            GlassCard(Modifier.padding(bottom = PageMetrics.SectionGap)) {
                options.forEach { minutes ->
                    SheetActionRow(
                        icon = MiuixIcons.Glass.Timer,
                        label = stringResource(R.string.minutes_format, minutes.toInt()),
                        onClick = { onPick(minutes) },
                    )
                }
            }
            Spacer(Modifier.height(PageMetrics.BottomGap))
        }
    }
}

private sealed interface TagNameRequest {
    data object Create : TagNameRequest
    data class Rename(val tag: Tag) : TagNameRequest
    data class Assign(val packageName: String) : TagNameRequest
}
