package com.ayang.glacier.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
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
import com.ayang.glacier.feature.common.AppActionSheet
import com.ayang.glacier.feature.common.AppIconCornerRatio
import com.ayang.glacier.feature.common.AppIconView
import com.ayang.glacier.feature.common.AppLabelTextStyle
import com.ayang.glacier.feature.common.AppRow
import com.ayang.glacier.feature.common.SheetActionRow
import com.ayang.glacier.platform.UiFeedback
import com.ayang.glacier.ui.LocalGlassActive
import com.ayang.glacier.ui.LocalRootBackdrop
import com.ayang.glacier.ui.LocalSearchTopInset
import com.ayang.glacier.ui.LocalShellBottomBarHeight
import com.ayang.glacier.ui.components.EmptyState
import com.ayang.glacier.ui.components.PageFrame
import com.ayang.glacier.ui.components.PageMetrics
import com.ayang.glacier.ui.components.TopBarAction
import com.ayang.glacier.ui.components.pageContentPadding
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.glass.GlassFloatingActionButton
import top.yukonga.miuix.kmp.glass.GlassSearchOverlay
import top.yukonga.miuix.kmp.glass.GlassSearchOverlayDefaults
import top.yukonga.miuix.kmp.glass.GlassSegmentedTabRow
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Add
import top.yukonga.miuix.kmp.icon.glass.Close
import top.yukonga.miuix.kmp.icon.glass.Delete
import top.yukonga.miuix.kmp.icon.glass.Edit
import top.yukonga.miuix.kmp.icon.glass.More
import top.yukonga.miuix.kmp.icon.glass.MultiSelect
import top.yukonga.miuix.kmp.icon.glass.QuietMode
import top.yukonga.miuix.kmp.icon.glass.Search
import top.yukonga.miuix.kmp.icon.glass.Tags
import top.yukonga.miuix.kmp.icon.glass.Timer
import top.yukonga.miuix.kmp.icon.glass.UnlockOutline
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
 * 搜索结果的卡片上下留白。
 *
 * 官方 `IconPage.kt:104`（`SearchResultVerticalPadding = 4.dp`）与
 * `IconPage.kt:221`（卡片 `padding(vertical = SearchResultVerticalPadding)`）同值。
 */
private val SearchResultVerticalPadding = 4.dp

/**
 * 主页：标签 + 应用网格 + 多选批量操作。
 *
 * 页面骨架由 [PageFrame] 统一：录制层在正文、顶栏在 topBar 槽、浮层在 overlay 槽
 * （浮层永远在 Scaffold 之后组合）。所有弹层都“常驻组合 + show 驱动”，
 * 关闭时保留最后一次内容，保证退出动画、键盘与返回键都由官方组件处理。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalScrollBarApi::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as GlacierApplication
    val repository = app.repository
    val preferences = app.preferences
    val snapshot: AppSnapshot = repository.snapshot

    var selectedTag by rememberSaveable { mutableIntStateOf(DEFAULT_TAG_ID) }
    var selecting by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(emptySet<String>()) }
    var searchOpen by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<String?>(null) }
    var tagEditorOpen by remember { mutableStateOf(false) }
    var tagNameDialog by remember { mutableStateOf<TagNameRequest?>(null) }
    var deleteTagTarget by remember { mutableStateOf<Tag?>(null) }
    var deferredTarget by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val runner = remember(repository) { AppActionRunner(context, repository) }
    val glassActive = LocalGlassActive.current
    val searchListState = rememberLazyListState()
    // 网格滚动状态只有一个用途：告诉顶栏下的标签行"内容是否已经滚进顶栏"，
    // 决定玻璃档 surfaceAlpha（官方 `IconPage.kt:283`：`if (blurActive && contentUnderTopBar) 1f else 0f`）。
    val gridState = rememberLazyGridState()

    // 标签被删除后，选中项要回到默认标签，否则页面会空白。
    if (snapshot.tags.none { it.id == selectedTag }) {
        selectedTag = DEFAULT_TAG_ID
    }

    val entries = remember(snapshot, selectedTag) { repository.homeApps(selectedTag) }
    val tagLabel = snapshot.tags.firstOrNull { it.id == selectedTag }?.let { repository.tagLabel(it) }
        ?: stringResource(R.string.label_default)

    val inSelection = selecting || selection.isNotEmpty()

    // 浮层退场期间继续使用最后一次内容（规范 0 节红线 1）。
    var lastActionEntry by remember { mutableStateOf<AppEntry?>(null) }
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
        title = if (inSelection) {
            stringResource(R.string.selected_count, selection.size)
        } else {
            stringResource(R.string.app_name)
        },
        subtitle = if (inSelection) "" else "$tagLabel · ${stringResource(R.string.app_count, entries.size)}",
        scrollBehavior = scrollBehavior,
        searchExpanded = searchOpen,
        // 官方图标网格页每页显式加滚动条（`example/IconPage.kt` / `MainPage.kt:341-345`）。
        scrollBarAdapter = rememberScrollBarAdapter(gridState).takeIf { entries.isNotEmpty() },
        topBarActions = {
            // 官方顶栏动作间距：`GlassPage.kt:363` 用 `Arrangement.spacedBy(8.dp)`。
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (inSelection) {
                    TopBarAction(MiuixIcons.Glass.QuietMode, stringResource(R.string.action_freeze)) {
                        val targets = selection
                        scope.launch {
                            val outcome = repository.setFrozenBatch(targets, true)
                            reportBatch(outcome, true, context, targets.size)
                            selecting = false
                            selection = emptySet()
                        }
                    }
                    TopBarAction(MiuixIcons.Glass.UnlockOutline, stringResource(R.string.action_unfreeze)) {
                        val targets = selection
                        scope.launch {
                            val outcome = repository.setFrozenBatch(targets, false)
                            reportBatch(outcome, false, context, targets.size)
                            selecting = false
                            selection = emptySet()
                        }
                    }
                    TopBarAction(MiuixIcons.Glass.Tags, stringResource(R.string.action_edit_tags)) {
                        if (selection.size == 1) {
                            tagNameDialog = TagNameRequest.Assign(selection.first())
                        }
                    }
                    TopBarAction(MiuixIcons.Glass.More, stringResource(R.string.more)) {
                        if (selection.size == 1) actionTarget = selection.first()
                    }
                    TopBarAction(MiuixIcons.Glass.Close, stringResource(R.string.close)) {
                        selecting = false
                        selection = emptySet()
                    }
                } else {
                    TopBarAction(MiuixIcons.Glass.Search, stringResource(R.string.search)) {
                        searchOpen = true
                    }
                    TopBarAction(MiuixIcons.Glass.MultiSelect, stringResource(R.string.action_select)) {
                        selecting = true
                    }
                    TopBarAction(MiuixIcons.Glass.Edit, stringResource(R.string.title_tag_editor)) {
                        tagEditorOpen = true
                    }
                }
            }
        },
        // 页签是顶栏的子级（官方 `GlassPage.kt:394-401` 的 bottomContent）：
        // 玻璃档由顶栏上下文自动继承 backdrop，不能放进正文网格。
        topBarBottom = { topBarBackdrop ->
            if (snapshot.tags.size > 1 && !inSelection) {
                TagRow(
                    tags = snapshot.tags,
                    selectedTag = selectedTag,
                    labelOf = { repository.tagLabel(it) },
                    backdrop = topBarBackdrop,
                    surfaceAlpha = if (gridState.canScrollBackward) 1f else 0f,
                    onSelect = { selectedTag = it },
                )
            }
        },
        // FAB 的位置交给 Scaffold：新版 PageFrame 已把底栏高度计入 bottomBar，
        // 这里不再手算 `padding(bottom = reserve - 20.dp)`。
        floatingActionButton = { fabBackdrop ->
            if (!inSelection && entries.isNotEmpty()) {
                GlassFloatingActionButton(
                    onClick = {
                        val targets = entries.filter { !it.frozen }.map { it.packageName }
                        val unfreeze = targets.isEmpty()
                        val set = if (unfreeze) entries.map { it.packageName } else targets
                        scope.launch {
                            val outcome = repository.setFrozenBatch(set, !unfreeze)
                            reportBatch(outcome, !unfreeze, context, set.size)
                        }
                    },
                    backdrop = fabBackdrop,
                ) {
                    Icon(
                        imageVector = if (entries.any { !it.frozen }) {
                            MiuixIcons.Glass.QuietMode
                        } else {
                            MiuixIcons.Glass.UnlockOutline
                        },
                        contentDescription = stringResource(
                            if (entries.any { !it.frozen }) R.string.action_freeze else R.string.action_unfreeze,
                        ),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        },
        overlay = {
            // 搜索浮层常驻组合：只由 expanded 驱动，官方组件负责 400ms 进出动画、
            // query 清理、焦点/键盘与返回键（GlassSearchOverlay.kt:229-245）。
            // onDismissRequest 只关闭搜索，绝不触达标签编辑。
            // 横向只让刘海安全区：侧栏展开时整个页面已被 Shell 的 railStartInset 右移，
            // 页面内再让一次会双算。顶部让位用 PageFrame 提供的 LocalSearchTopInset
            // （宽屏侧栏折叠成顶部胶囊时非 0，窄屏恒 0）。
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
                horizontalInsetsModifier = Modifier.windowInsetsPadding(
                    WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
                ),
                searchTopPadding = LocalSearchTopInset.current,
                label = stringResource(R.string.search_hint),
                blurEnabled = glassActive,
                searchIcon = MiuixIcons.Glass.Search,
                closeIcon = MiuixIcons.Glass.Close,
            ) { query ->
                HomeSearchResults(
                    query = query,
                    state = searchListState,
                    onPick = { entry ->
                        searchOpen = false
                        scope.launch { runner.run(entry, AppAction.Launch) }
                    },
                )
            }

            AppActionSheet(
                entry = lastActionEntry,
                show = actionTarget != null,
                inHome = true,
                onDismiss = { actionTarget = null },
                onAction = { action ->
                    val entry = lastActionEntry
                    actionTarget = null
                    if (entry != null) {
                        when (action) {
                            AppAction.EditTags -> tagNameDialog = TagNameRequest.Assign(entry.packageName)
                            AppAction.DeferredFreeze -> deferredTarget = entry.packageName
                            else -> scope.launch {
                                runner.run(entry, action)
                                repository.refresh()
                            }
                        }
                    }
                },
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
    ) { padding ->
        val contentPadding = pageContentPadding(padding)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val available = maxWidth - contentPadding.calculateLeftPadding(LocalLayoutDirection.current) -
                contentPadding.calculateRightPadding(LocalLayoutDirection.current)
            val columns = HomeGridMetrics.columns(available, preferences.compactGrid)
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                // 页面留白走统一 token（12dp），不再出现 16dp 硬编码。
                contentPadding = contentPadding,
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
                        onLongClick = {
                            selecting = true
                            selection = if (entry.packageName in selection) {
                                selection - entry.packageName
                            } else {
                                selection + entry.packageName
                            }
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
 * 点击热区是整格 [Column]（含标签），长按进入多选。
 *
 * **按压/长按叠层的形状**：`combinedClickable` 默认 `indication = LocalIndication`，
 * 而 MIUIX 主题把它设成 [top.yukonga.miuix.kmp.utils.MiuixIndication]（`MiuixTheme.kt:37/67`），
 * 后者的绘制是 `drawRect(color, alpha, size)` —— 一整块矩形、没有任何圆角
 * （`MiuixIndication.kt:129-135`，KDoc 第 36 行写明 "draws a rectangular overlay"）。
 * 只要 no-clip，整格（含标签、宽度=格宽）就会被盖上一块方角灰底，也就是实拍里的"正方形叠层"。
 *
 * 修法与官方图标网格完全一致：**先裁 squircle 再 clickable**，让叠层被裁剪到与图标同一条
 * squircle 轮廓（`IconPage.kt:403-411`：`squircleClip(12.dp)` → `clickable{...}` → `padding(...)`；
 * MIUIX 自己的 `TabRow` 同样用 `squircleClip(cornerRadius)` 包住带 indication 的 `selectable`
 * （`TabRow.kt:410-418`）。`Modifier.clip`/`squircleClip` 只裁绘制、不裁命中测试，
 * 整格热区（含标签）保持不变，不需要把叠层缩小到图标上，也不会引入缩放/位移。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeAppItem(
    entry: AppEntry,
    labelSize: Float,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .squircleClip(HomeGridMetrics.CellCorner)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        AppIconView(
            packageName = entry.packageName,
            size = PageMetrics.GridAppIcon,
            frozen = entry.frozen,
            selected = selected,
        )
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
 * 搜索结果列表。
 *
 * 官方结构：`Card + BasicComponent`（`IconPage.kt:215-244`）；这里直接用共享的
 * [AppRow]（内部就是官方 [BasicComponent] + 40dp [AppIconView]），与 Apps 列表、
 * Apps 搜索结果同一份行实现。
 */
@Composable
private fun HomeSearchResults(
    query: String,
    state: LazyListState,
    onPick: (AppEntry) -> Unit,
) {
    val context = LocalContext.current
    val repository = (context.applicationContext as GlacierApplication).repository
    val results = remember(query, repository.snapshot) {
        if (query.isBlank()) emptyList() else repository.apps(query)
    }
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val searchBarSpace = GlassSearchOverlayDefaults.BarHeight +
        GlassSearchOverlayDefaults.VerticalPadding * 2
    // 与浮层搜索条一致：宽屏侧栏折叠成顶部胶囊时，结果列表也要让出同一段（官方 IconPage.kt:538）。
    val searchTop = LocalSearchTopInset.current()

    LazyColumn(
        state = state,
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)),
        contentPadding = PaddingValues(
            top = topInset + searchTop + searchBarSpace + SearchResultVerticalPadding,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                // 搜索浮层盖不住 Shell 的悬浮导航胶囊（它是 Shell 的兄弟节点、画在页面之上），
                // 结果列表底部必须避开胶囊高度，否则最后几条会压在胶囊下面。
                LocalShellBottomBarHeight.current +
                PageMetrics.BottomGap,
        ),
    ) {
        if (results.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    text = stringResource(if (query.isBlank()) R.string.search_hint else R.string.empty_search),
                )
            }
        } else {
            lazyItems(results, key = { it.packageName }) { entry ->
                // 与 Apps 列表 / 搜索结果共用同一个 `AppRow`（官方 `BasicComponent` 行 + 40dp
                // `AppIconView` + 包名 summary + 冻结徽标），保证三处的图标轮廓、字体层级、
                // 图文间距、单行结构与点击反馈是同一套，而不是这里再手搓一份。
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
    val useSegmented = LocalGlassActive.current && backdrop != null && names.size in 2..4
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PageMetrics.Horizontal)
            .padding(top = 6.dp, bottom = PageMetrics.Horizontal),
    ) {
        if (useSegmented && backdrop != null) {
            GlassSegmentedTabRow(
                tabs = names,
                selectedIndex = index,
                onSelect = { onSelect(tags[it].id) },
                backdrop = backdrop,
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
    ) {
        LazyColumn(Modifier.fillMaxWidth()) {
            item {
                Card(Modifier.padding(bottom = PageMetrics.SectionGap)) {
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
                Card(Modifier.padding(bottom = PageMetrics.SectionGap)) {
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
    var text by remember(request) { mutableStateOf(initial) }
    // 常驻组合后，每次重新打开都要回到该请求的初始文本（关闭时保留文本用于退场）。
    LaunchedEffect(show) {
        if (show) text = initial
    }
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
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.tag_name_hint),
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GlassDialogButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.cancel), style = MiuixTheme.textStyles.button)
            }
            GlassDialogButton(
                onClick = { if (text.isNotBlank()) onConfirm(request, text.trim()) },
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
    ) {
        Column(Modifier.fillMaxWidth()) {
            Card(Modifier.padding(bottom = PageMetrics.SectionGap)) {
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

private fun reportBatch(
    outcome: com.ayang.glacier.system.FreezeOutcome,
    frozen: Boolean,
    context: android.content.Context,
    requested: Int,
) {
    when (outcome) {
        is com.ayang.glacier.system.FreezeOutcome.Applied -> {
            if (outcome.count == 0) {
                UiFeedback.toast(context, R.string.permission_denied)
            } else {
                val res = if (frozen) R.string.msg_freeze_many else R.string.msg_unfreeze_many
                UiFeedback.toast(context, context.getString(res, outcome.count.toString()))
            }
        }

        com.ayang.glacier.system.FreezeOutcome.Denied ->
            UiFeedback.toast(context, R.string.permission_denied)
    }
}
