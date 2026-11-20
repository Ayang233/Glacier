package com.ayang.glacier.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ayang.glacier.platform.Platform
import com.ayang.glacier.ui.LocalGlassActive
import com.ayang.glacier.ui.LocalPageStartReserved
import com.ayang.glacier.ui.LocalShellBottomBarHeight
import com.ayang.glacier.ui.LocalTopBarTitleAlpha
import com.ayang.glacier.ui.isWideLayout
import com.ayang.glacier.ui.rememberSurfaceBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBarAdapter
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.VerticalScrollBar
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.glass.glassSearchBarTransition
import top.yukonga.miuix.kmp.glass.rememberGlassSearchBarAlpha
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.glass.ChevronBackward
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * 页面级尺寸。
 *
 * 全部取自 MIUIX 官方 example 的实际数值，不凭感觉调：
 * - 卡片外边距 12dp（`example/MainPage.kt:312`、`AboutPage.kt:348`）
 * - 卡片/分节间距 12dp（`AboutPage.kt:399`、`:442`）
 * - 列表末尾留白 12dp（`MainPage.kt:339`）
 * - 卡片圆角取官方 [CardDefaults.CornerRadius]
 * - 顶栏动作图标 28dp（`example/GlassPage.kt:357`）
 */
object PageMetrics {
    /** 卡片 / 列表到页面边缘的距离。 */
    val Horizontal = 12.dp

    /** 分节之间的纵向间距。 */
    val SectionGap = 12.dp

    /** 列表末尾的留白。 */
    val BottomGap = 12.dp

    /** 官方卡片圆角。 */
    val CardCorner = CardDefaults.CornerRadius

    /** 顶栏动作按钮里的图标尺寸（官方 example 28dp）。 */
    val TopBarActionIcon = 28.dp

    /** 行内应用图标尺寸（列表行）。 */
    val ListAppIcon = 40.dp

    /** 主页网格图标尺寸。 */
    val GridAppIcon = 56.dp
}

/**
 * 页面骨架。
 *
 * 结构对照官方 `GlassPage`（`miuix/example/shared/src/commonMain/kotlin/GlassPage.kt:264-341`）：
 *
 * ```
 * Box
 * ├ Scaffold                                   ← 顶栏 / 正文 / FAB
 * │  └ 正文录制层（满宽，先垫底色）→ 内容
 * └ overlay                                    ← 浮层，画在 Scaffold 之上
 * ```
 *
 * - **浮层必须在 Scaffold 之外**：官方 Scaffold 的摆放顺序是
 *   `body → topBar → snackbar → bottomBar → fab → popup`，把浮层写进 body 槽会被顶栏盖住；
 *   顶栏淡出后仍参与命中测试，搜索浮层的 × 与顶栏动作坐标重叠时点击会被顶栏吃掉。
 * - **底栏高度来自 Shell 实测**：Shell 的 GlassNavigationBar 已经在自己的 Scaffold 里占位，
 *   这里用透明 bottomBar 复现同样的布局语义，正文底部 padding 与 FAB 位置都由 Scaffold 计算，
 *   不再手写 `Height + 32dp` 之类的魔法值。
 * - **搜索态**：整页按官方 `rememberGlassSearchBarAlpha` 淡出，顶栏再用
 *   `glassSearchBarTransition` 做缩放 + 模糊，两者分工与官方 example 一致。
 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun PageFrame(
    title: String,
    scrollBehavior: ScrollBehavior,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    /**
     * 是否允许大标题折叠。二级页传 `false`（官方 `AdaptiveTopAppBar(expandable=...)`）。
     *
     * 宽屏不属于这个开关的语义：宽屏时大标题本来就不展开，标题透明度由 Shell 的
     * `LocalTopBarTitleAlpha` 跟随侧栏 morph 淡出（官方 `AppContent.kt:217-223` 就是
     * `if (useGlassSidebar) sidebarState.progress else 1f`）。因此**折叠胶囊形态下页面标题不可见
     * 是官方语义**（胶囊占据顶栏标题位），不是布局丢失；动作按钮不受影响。
     */
    expandable: Boolean = true,
    navigationIcon: @Composable () -> Unit = {},
    topBarActions: @Composable RowScope.() -> Unit = {},
    /**
     * 顶栏下方的内容槽。
     *
     * 页签、分段控件这类"浮在被滚动内容之上"的玻璃组件必须放在这里：
     * 它由 topBar 槽绘制，与正文录制层是兄弟；如果塞进正文列表里，
     * 就会变成自己采样源的子节点，直接触发 libhwui 递归崩溃。
     */
    topBarBottom: @Composable (LayerBackdrop?) -> Unit = {},
    floatingActionButton: @Composable (LayerBackdrop?) -> Unit = {},
    /** 全屏浮层（搜索、弹层）。画在 Scaffold 之上，因此能盖住顶栏。 */
    overlay: @Composable () -> Unit = {},
    /** 搜索浮层是否展开：整页按官方动画淡出并屏蔽无障碍语义。 */
    searchExpanded: Boolean = false,
    /**
     * 额外的顶栏标题透明度（0..1），`null` = 不接管（保持 R3 行为）。
     *
     * 官方 AboutPage 的标题随滚动进度淡入（`example/AboutPage.kt:127`），
     * 这类页面级曲线经这里传入。与 Shell 宽屏侧栏折叠胶囊的
     * [LocalTopBarTitleAlpha] **相乘**（官方同样把两者相乘：
     * `example/utils/PageUtils.kt:264` `titleAlpha() * chromeTitleAlpha()`），
     * 因为"侧栏盖住标题"与"标题随滚动淡出"是两个独立的遮挡来源，任一为 0 都必须不可见。
     */
    topBarTitleAlpha: (() -> Float)? = null,
    /**
     * 滚动到边界时的触觉反馈。官方设置项 `enableScrollEndHaptic` 默认 `true`
     * （`example/AppState.kt:35`），Glacier 没有该设置项，因此按官方默认开启。
     */
    scrollEndHaptic: Boolean = true,
    /**
     * 是否由页面自己把 [Modifier.pageScrollModifiers] 挂到实际的滚动容器上（官方页面位置：
     * `example/MainPage.kt:281-285`）。默认 `false`：PageFrame 在正文 wrapper 上统一接管，
     * 页面不需要任何改动。传 `true` 时必须由页面在自己列表的 modifier 上调用该扩展，
     * 否则顶栏折叠会失效（两处都挂则会重复消费，官方明确警告不要喂两次：
     * `example/PullToRefreshPage.kt:156`）。
     */
    pageScrollOnContent: Boolean = false,
    /**
     * 垂直滚动条适配器，`null` = 不显示。
     *
     * 官方示例的滚动条不是库默认能力，而是每页显式添加
     * （`example/MainPage.kt:341-345` 等）；页面把
     * `rememberScrollBarAdapter(lazyListState / lazyGridState)` 传进来即可，
     * 轨道内边距由 PageFrame 按 Scaffold 实际测量值自动计算。
     */
    scrollBarAdapter: ScrollBarAdapter? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    val surface = MiuixTheme.colorScheme.surface
    val glassActive = LocalGlassActive.current
    val backdrop = rememberSurfaceBackdrop(surface)
    val searchBarAlpha = rememberGlassSearchBarAlpha(searchExpanded)
    val shellBottom = LocalShellBottomBarHeight.current
    val safeBottom = pageSafeBottom()
    // 底栏可见时它已经含了手势区；宽屏/二级页则自己让出系统底部安全区。
    val bottomReserve = if (shellBottom > 0.dp) shellBottom else safeBottom
    val largeTitle = expandable && !isWideLayout()
    // 页面曲线 × Shell 侧栏曲线：任一遮挡来源为 0 时标题都不可见（官方 PageUtils.kt:264 同乘）。
    val chromeTitleAlpha = LocalTopBarTitleAlpha.current
    val mergedTitleAlpha = remember(topBarTitleAlpha, chromeTitleAlpha) {
        { (topBarTitleAlpha?.invoke() ?: 1f).coerceIn(0f, 1f) * chromeTitleAlpha().coerceIn(0f, 1f) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = searchBarAlpha.value }
                .then(if (searchExpanded) Modifier.clearAndSetSemantics { } else Modifier),
            containerColor = surface,
            contentWindowInsets = WindowInsets(0),
            topBar = {
                PageTopBar(
                    title = title,
                    subtitle = subtitle,
                    backdrop = backdrop.takeIf { glassActive },
                    scrollBehavior = scrollBehavior,
                    expandable = largeTitle,
                    titleAlpha = mergedTitleAlpha,
                    navigationIcon = navigationIcon,
                    actions = topBarActions,
                    bottomContent = { topBarBottom(backdrop.takeIf { glassActive }) },
                    searchExpanded = searchExpanded,
                )
            },
            bottomBar = {
                if (bottomReserve > 0.dp) {
                    Spacer(Modifier.fillMaxWidth().height(bottomReserve))
                }
            },
            floatingActionButton = {
                floatingActionButton(backdrop.takeIf { glassActive })
            },
        ) { padding ->
            // 不再暴露 LocalPageBackdrop：provider 在本录制层子树内，页面内组件若读它采样，
            // 就在自己的采样源子树里采样 → 自采样崩溃（Glass 审计 R4-P2）。
            // 页面内的玻璃组件要 backdrop 时由 Shell 的 LocalRootBackdrop 提供（弹层、顶栏、FAB 均如此）。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (glassActive) Modifier.layerBackdrop(backdrop) else Modifier),
            ) {
                Box(
                    modifier = if (pageScrollOnContent) {
                        Modifier.fillMaxSize()
                    } else {
                        // 官方 pageScrollModifiers 的等价落地：滚动末端触感 + 弹性 overscroll
                        // （并回写 scrollBehavior.state.overscrollOffset，驱动 GlassTopAppBar
                        // 的弹性材质进度）+ 顶栏折叠的 nestedScroll。
                        Modifier
                            .fillMaxSize()
                            .pageScrollModifiers(
                                scrollBehavior = scrollBehavior,
                                enableScrollEndHaptic = scrollEndHaptic,
                            )
                    },
                ) {
                    content(padding)
                }
                if (scrollBarAdapter != null) {
                    // 画在滚动 wrapper 之外：弹性 overscroll 位移不应该带着滚动条一起动。
                    // 层序与官方一致（`MainPage.kt:341-345` 的滚动条同样在 layerBackdrop 内）。
                    VerticalScrollBar(
                        adapter = scrollBarAdapter,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight(),
                        trackPadding = PaddingValues(
                            top = padding.calculateTopPadding(),
                            bottom = padding.calculateBottomPadding(),
                        ),
                    )
                }
            }
        }

        overlay()
    }
}

/** 顶栏的两档实现：玻璃档用官方 [GlassTopAppBar]，非玻璃档用普通 [TopAppBar]。 */
@Composable
fun PageTopBar(
    title: String,
    backdrop: LayerBackdrop?,
    scrollBehavior: ScrollBehavior,
    subtitle: String = "",
    expandable: Boolean = true,
    titleAlpha: () -> Float = { 1f },
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
    searchExpanded: Boolean = false,
) {
    if (LocalGlassActive.current) {
        GlassTopAppBar(
            title = title,
            subtitle = subtitle,
            backdrop = backdrop,
            scrollBehavior = scrollBehavior,
            expandable = expandable,
            // AGSL 渐进模糊在模拟器上产出的像素不可信，交给库的 band 渐变路径。
            progressiveBlur = !Platform.isEmulator,
            // 官方搜索转场：缩放 + 模糊；整页透明度由 PageFrame 的 Scaffold 负责。
            contentModifier = Modifier.glassSearchBarTransition(
                expanded = searchExpanded,
                blurEnabled = !Platform.isEmulator,
                barAlpha = { 1f },
            ),
            titleAlpha = titleAlpha,
            navigationIcon = navigationIcon,
            actions = actions,
            bottomContent = bottomContent,
        )
    } else {
        // 非玻璃档没有 titleAlpha 形参，用 titleColor / largeTitleColor 的 alpha 表达同一曲线
        // （官方 TopAppBar 参数：`miuix-ui/.../basic/TopAppBar.kt:108-125`）。
        // alpha == 1 时与原默认值逐字节等价，不影响未传 topBarTitleAlpha 的页面。
        // 代价：alpha 是组合期参数，滚动淡出会让顶栏文字每帧重组（Glass 档在绘制期读 lambda，
        // 没有这个问题）；二级页顶栏只有标题/副标题，成本可接受。
        val alpha = titleAlpha().coerceIn(0f, 1f)
        val textColor = MiuixTheme.colorScheme.onSurface.copy(alpha = alpha)
        TopAppBar(
            title = title,
            subtitle = subtitle,
            scrollBehavior = scrollBehavior,
            expandable = expandable,
            titleColor = textColor,
            largeTitleColor = textColor,
            subtitleColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = alpha),
            navigationIcon = navigationIcon,
            actions = actions,
            bottomContent = bottomContent,
        )
    }
}

/** 顶栏右侧动作：玻璃档自动继承顶栏材质，非玻璃档退回普通图标按钮。 */
@Composable
fun TopBarAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    if (LocalGlassActive.current) {
        GlassIconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(PageMetrics.TopBarActionIcon),
                tint = MiuixTheme.colorScheme.onSurface,
            )
        }
    } else {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(PageMetrics.TopBarActionIcon),
                tint = MiuixTheme.colorScheme.onSurface,
            )
        }
    }
}

/**
 * 侧栏让位。
 *
 * 只移动内容，不动录制层（录制层必须满宽，否则侧栏位置没有像素可采）。
 * inset 在量测期读取，避免侧栏展开的 300ms 里整页重组。
 */
fun Modifier.railStartInset(inset: () -> Dp): Modifier = layout { measurable, constraints ->
    val extra = inset().roundToPx()
        .coerceIn(0, if (constraints.hasBoundedWidth) constraints.maxWidth else 0)
    if (extra == 0) {
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    } else {
        // 父级可能给的是固定宽度（minWidth == maxWidth），扣掉让位宽度后必须同步放开 min，
        // 否则 copy() 会构造出 maxWidth < minWidth 的非法约束。
        val reduced = constraints.copy(
            minWidth = 0,
            maxWidth = (constraints.maxWidth - extra).coerceAtLeast(0),
        )
        val placeable = measurable.measure(reduced)
        layout(placeable.width + extra, placeable.height) {
            placeable.placeRelative(extra, 0)
        }
    }
}

/** 系统底部安全区（手势条 / 三键导航 / 刘海横屏的 caption bar）。 */
@Composable
private fun pageSafeBottom(): Dp {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val insets = WindowInsets.navigationBars
        .union(WindowInsets.displayCutout)
    return with(density) { insets.getBottom(this).toDp() }
}

/**
 * 页面内容内边距。
 *
 * 语义与官方 `example/utils/PageUtils.kt:186-222` 一致：
 * - start/end = `max(页面横向留白, 系统横向安全区)`
 * - top = Scaffold 给的顶栏高度 + extraTop
 * - bottom = Scaffold 给的底栏高度 + 末尾留白 + extraBottom
 *
 * 横向安全区只算刘海/横屏导航条。**宽屏 pane 的 start 侧由 Shell 的 `railStartInset` 让过**
 * （它已经包含侧栏宽度与 start 侧系统 inset），此时 [LocalPageStartReserved] 为 true，
 * 页面不能再让一次同一块 cutout —— 官方用 `startAlreadyReserved` 表达同一语义
 * （`PageUtils.kt:99-103`：为 true 时 `safeInsets` 只保留 End + Vertical）。
 */
@Composable
fun pageContentPadding(
    scaffoldPadding: PaddingValues,
    horizontal: Dp = PageMetrics.Horizontal,
    extraTop: Dp = 0.dp,
    extraBottom: Dp = 0.dp,
): PaddingValues {
    val layoutDirection = LocalLayoutDirection.current
    val horizontalInsets = WindowInsets.displayCutout
        .only(WindowInsetsSides.Horizontal)
        .union(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
        .asPaddingValues()
    val startReserved = LocalPageStartReserved.current
    // 不能 remember：MIUIX Scaffold 的 contentPadding 是一个实例不变、
    // 内部 paddingHolder 变化的 PaddingValues。用 remember(scaffoldPadding) 会缓存第一帧
    // （顶栏底栏高度还没量出来）的旧值，导致正文永远按错误的顶栏高度起排。
    // 直接读 calculate*Padding() 在组合期订阅其内部状态，高度变化时自动重组。
    return PaddingValues(
        start = if (startReserved) {
            horizontal
        } else {
            maxOf(horizontal, horizontalInsets.calculateStartPadding(layoutDirection))
        },
        end = maxOf(horizontal, horizontalInsets.calculateEndPadding(layoutDirection)),
        top = scaffoldPadding.calculateTopPadding() + extraTop,
        bottom = scaffoldPadding.calculateBottomPadding() + PageMetrics.BottomGap + extraBottom,
    )
}

/**
 * 设置页与列表页共用的分组卡片容器。
 *
 * 直接就是官方 [Card]：圆角、底色、内容色全部走 [CardDefaults]，
 * 内部留白交给每一行（`BasicComponentDefaults.InsideMargin` = 16dp），不叠第二层 padding。
 */
@Composable
fun GroupCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(),
    ) {
        Column(content = content)
    }
}

/** 空状态：没有图标替代、没有自绘插画，只用官方排版。 */
@Composable
fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 官方 `example/utils/PageUtils.kt:168-183` 的 `pageScrollModifiers` 等价实现。
 *
 * ```
 * scrollEndHaptic()                      // 滚动末端触感（官方设置项默认开）
 *   .overScrollVertical(onOffsetChanged) // 弹性 overscroll + 回写顶栏状态
 *   .nestedScroll(顶栏折叠连接)
 *   .fillMaxHeight()
 * ```
 *
 * 正常路径由 [PageFrame] 在正文 wrapper 上统一调用，页面无需接入；
 * 只有列表不是正文唯一滚动容器、必须精确挂到列表本身时才由页面调用，并给
 * [PageFrame] 传 `pageScrollOnContent = true`（否则两处重复消费，顶栏会折叠两次）。
 */
@Composable
fun Modifier.pageScrollModifiers(
    scrollBehavior: ScrollBehavior,
    showTopAppBar: Boolean = true,
    enableScrollEndHaptic: Boolean = true,
): Modifier {
    val state = scrollBehavior.state
    val onOffsetChanged: (Float) -> Unit = remember(state) {
        // 官方同款：`example/utils/PageUtils.kt:175-177`。
        { offset -> state.overscrollOffset = offset }
    }
    return this
        .then(if (enableScrollEndHaptic) Modifier.scrollEndHaptic() else Modifier)
        .overScrollVertical(onOffsetChanged = if (showTopAppBar) onOffsetChanged else null)
        .then(if (showTopAppBar) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier)
        .fillMaxHeight()
}

/**
 * 顶栏返回按钮。
 *
 * 照官方 `example/component/BackNavigationIcon.kt`：玻璃档 28dp `ChevronBackward`，
 * RTL 下用 `graphicsLayer.scaleX = -1f` 镜像（官方 `BackNavigationIcon.kt:36-41`）。
 * 不要用 [TopBarAction] 代替——它是通用动作按钮，搜索/排序/筛选等非方向性图标
 * 不应该被镜像。
 */
@Composable
fun BackNavigationAction(
    onClick: () -> Unit,
    contentDescription: String,
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val mirror = if (rtl) Modifier.graphicsLayer { scaleX = -1f } else Modifier
    if (LocalGlassActive.current) {
        GlassIconButton(onClick = onClick) {
            Icon(
                imageVector = MiuixIcons.Glass.ChevronBackward,
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(PageMetrics.TopBarActionIcon)
                    .then(mirror),
                tint = MiuixTheme.colorScheme.onSurface,
            )
        }
    } else {
        // 官方非玻璃档：普通 `MiuixIcons.Back`、24dp、onBackground（`BackNavigationIcon.kt:29-43`）。
        IconButton(onClick = onClick) {
            Icon(
                imageVector = MiuixIcons.Back,
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(24.dp)
                    .then(mirror),
                tint = MiuixTheme.colorScheme.onBackground,
            )
        }
    }
}
