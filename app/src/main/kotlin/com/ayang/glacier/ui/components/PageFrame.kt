package com.ayang.glacier.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ayang.glacier.ui.LocalAppPageInsets
import com.ayang.glacier.ui.LocalGlassActive
import com.ayang.glacier.ui.LocalShellBottomBarHeight
import com.ayang.glacier.ui.LocalTopBarObstructionStart
import com.ayang.glacier.ui.LocalTopBarTitleAlpha
import com.ayang.glacier.ui.appPageTopBarInsets
import com.ayang.glacier.ui.isWideLayout
import com.ayang.glacier.ui.rememberAppSafePadding
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
import top.yukonga.miuix.kmp.glass.GlassMotion
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
 * 结构对照官方 `GlassPage`（`miuix/example/shared/src/commonMain/kotlin/GlassPage.kt:280-470`）：
 *
 * ```
 * Box
 * ├ Scaffold                                   ← 顶栏 / 正文 / FAB
 * │  └ 正文录制层（满宽，先垫底色）→ 内容
 * └ overlay                                    ← 浮层，画在 Scaffold 之上
 * ```
 *
 * - **让位只发生在内容层**：横向与顶部安全区由 [appPageTopBarInsets] 与
 *   [pageContentPadding] 交给布局阶段读取，录制层与背景始终满宽（官方 `appPageInsets` 语义）。
 * - **浮层必须在 Scaffold 之外**：官方 Scaffold 的摆放顺序是
 *   `body → topBar → snackbar → bottomBar → fab → popup`，把浮层写进 body 槽会被顶栏盖住；
 *   顶栏淡出后仍参与命中测试，搜索浮层的 × 与顶栏动作坐标重叠时点击会被顶栏吃掉。
 * - **底栏高度来自宿主实测**：Shell 的 GlassNavigationBar 在自己的 Scaffold 里占位，
 *   这里用透明 bottomBar 复现同样的布局语义，正文底部 padding 与 FAB 位置都由 Scaffold 计算。
 * - **搜索态**：整页按官方 `rememberGlassSearchBarAlpha` 淡出，顶栏再用
 *   `glassSearchBarTransition` 做缩放 + 模糊，两者分工与官方 example 一致。
 *
 * 传给 [content] 的是**已经算好的内容内边距**（含顶栏/底栏/安全区/12dp 页码），
 * 页面直接当成 `contentPadding` 用即可；滚动条的 `trackPadding` 与它同源，
 * 因此与官方"每页显式加滚动条、轨道贴着内容区"的写法一致。
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
     * `example/utils/PageUtils.kt:264` `titleAlpha() * chromeTitleAlpha()`）。
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
     * 页面内容到屏幕边缘的横向留白（官方 12dp 卡片外边距）。
     *
     * 设置页 / 许可证页的分节标题自带 28dp，不希望再叠一层 12dp 时传 `0.dp`
     * （官方 `SettingsPage.kt:415` 的 `SmallTitle` 与 `groupedCardItems`
     * 的 `outerHorizontalPadding` 各自只管一层）。
     */
    contentPaddingHorizontal: Dp = PageMetrics.Horizontal,
    /**
     * 垂直滚动条适配器，`null` = 不显示。
     *
     * 官方示例的滚动条不是库默认能力，而是每页显式添加
     * （`example/MainPage.kt:341-345` 等）；页面把
     * `rememberScrollBarAdapter(lazyListState / lazyGridState)` 传进来即可，
     * 轨道内边距与顶栏隐现跟随都由 PageFrame 按官方参数传好。
     */
    scrollBarAdapter: ScrollBarAdapter? = null,
    /** 页面内容。参数是**最终内容内边距**，直接传给 `LazyColumn(contentPadding = ...)`。 */
    content: @Composable (PaddingValues) -> Unit,
) {
    val surface = MiuixTheme.colorScheme.surface
    val glassActive = LocalGlassActive.current
    val backdrop = rememberSurfaceBackdrop(surface)
    val searchBarAlpha = rememberGlassSearchBarAlpha(searchExpanded)
    // 宿主让位：底栏可见时它已经含了手势区（Shell 实测高度）；宽屏/二级页则让出系统底部安全区。
    // 系统那一半来自官方 AppPageInsets 契约，不再自己算 WindowInsets。
    val shellBottom = LocalShellBottomBarHeight.current
    val safeBottom = rememberAppSafePadding().calculateBottomPadding()
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
                    obstructionStart = LocalTopBarObstructionStart.current,
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
        ) { innerPadding ->
            // 内容内边距在这里一次算好：横向 = max(12dp, 安全区 + 侧栏让位)，纵向 = Scaffold 实测，
            // 全部在布局阶段读取（PaddingValues 的 calculate* 是惰性的），侧栏 morph 不触发重组。
            val contentPadding = pageContentPadding(innerPadding, horizontal = contentPaddingHorizontal)
            // 不再暴露 LocalPageBackdrop：provider 在本录制层子树内，页面内组件若读它采样，
            // 就在自己的采样源子树里采样 → 自采样崩溃（Glass 审计 R4-P2）。
            // 页面内的玻璃组件要 backdrop 时由 Shell 的 LocalRootBackdrop 提供。
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
                                enableScrollEndHaptic = scrollEndHaptic,
                                showTopAppBar = true,
                                topAppBarScrollBehavior = scrollBehavior,
                            )
                    },
                ) {
                    content(contentPadding)
                }
                if (scrollBarAdapter != null) {
                    // 画在滚动 wrapper 之外：弹性 overscroll 位移不应该带着滚动条一起动。
                    // 参数与官方一致（`MainPage.kt:359-364`）：轨道内边距取内容内边距，
                    // 并把自己挂到顶栏滚动行为上，顶栏隐藏时滚动条跟着收起。
                    VerticalScrollBar(
                        adapter = scrollBarAdapter,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight(),
                        trackPadding = contentPadding,
                        topAppBarScrollBehavior = scrollBehavior,
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
    modifier: Modifier = Modifier,
    obstructionStart: Dp = Dp.Unspecified,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
    searchExpanded: Boolean = false,
) {
    // 顶栏自己不再猜安全区：横向让位（含悬浮侧栏）+ 顶部剩余安全区都由官方契约给出，
    // 与官方 `AdaptiveTopAppBar` 的 `defaultWindowInsetsPadding = false` +
    // `contentModifier.appPageTopBarInsets()` 同构（`utils/PageUtils.kt:265/282`）。
    if (LocalGlassActive.current) {
        GlassTopAppBar(
            title = title,
            subtitle = subtitle,
            backdrop = backdrop,
            scrollBehavior = scrollBehavior,
            expandable = expandable,
            // 官方新增的居中标题避让：宽屏折叠胶囊盖住标题位时把标题右移，而不是让它消失
            // （`GlassTopAppBar.kt:293` / 官方 `GlassPage.kt:347`）。
            centerObstructionStart = obstructionStart,
            modifier = modifier,
            defaultWindowInsetsPadding = false,
            // 渐进模糊走库的官方管线（AGSL + 渐进纹理），不再按运行环境关掉。
            progressiveBlur = true,
            // 官方搜索转场：缩放 + 模糊；整页透明度由 PageFrame 的 Scaffold 负责。
            contentModifier = Modifier
                .glassSearchBarTransition(
                    expanded = searchExpanded,
                    blurEnabled = true,
                    barAlpha = { 1f },
                )
                .then(Modifier.appPageTopBarInsets()),
            titleAlpha = titleAlpha,
            navigationIcon = navigationIcon,
            actions = actions,
            bottomContent = bottomContent,
        )
    } else {
        // 非玻璃档没有 titleAlpha 形参，用 titleColor / largeTitleColor 的 alpha 表达同一曲线
        // （官方 TopAppBar 参数：`miuix-ui/.../basic/TopAppBar.kt:113-133`）。
        // alpha == 1 时与原默认值逐字节等价，不影响未传 topBarTitleAlpha 的页面。
        val alpha = titleAlpha().coerceIn(0f, 1f)
        val textColor = MiuixTheme.colorScheme.onSurface.copy(alpha = alpha)
        // 官方非玻璃档把 inset 挂在整条顶栏上（`PageUtils.kt:299-304`），底色不铺到刘海下面。
        TopAppBar(
            title = title,
            subtitle = subtitle,
            scrollBehavior = scrollBehavior,
            expandable = expandable,
            titleColor = textColor,
            largeTitleColor = textColor,
            subtitleColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = alpha),
            modifier = modifier.then(Modifier.appPageTopBarInsets()),
            defaultWindowInsetsPadding = false,
            centerObstructionStart = obstructionStart,
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
 * 顶栏动作区的两态切换（普通档 ⇄ 多选档）。
 *
 * 为什么需要：动作组是 `Row(spacedBy(8dp))` 里的一串图标，直接按布尔值 `if` 替换时，
 * 标题、按钮位次、按钮数量会在同一帧全部换掉，观感就是"生硬地跳一下"（OS4 里没有这种跳变）。
 *
 * 动画全部取 MIUIX 自己的曲线，不自造数值：
 * - 淡入/淡出 + 缩放：官方控件显隐的写法（`miuix-ui/basic/SearchBar.kt:190-193`
 *   的清除按钮 `fadeIn() + scaleIn()` / `fadeOut() + scaleOut()`；
 *   `example/component/SuperSearchBar.kt:196-201` 同款）；
 * - 曲线取 [GlassMotion.scaleIn] / [GlassMotion.scaleOut]（0.6/0.35、0.75/0.2 的 Folme 弹簧）
 *   与 [GlassMotion.barExpand] / [GlassMotion.barCollapse]（顶栏自己的展开/收起弹簧，
 *   `miuix-ui/basic/TopAppBar.kt:757-766` 用小标题进出场就是这两个）；
 * - 宽度变化交给 `SizeTransform`，用同一个 barExpand 弹簧，于是按钮组是"推挤着长出来"，
 *   而不是先跳宽再动画。
 *
 * `AnimatedContent` 也是 MIUIX 自己在用的容器（`miuix-glass/.../GlassNavigationRail.kt:1062`）。
 *
 * @param selectionMode 当前是否多选态（切换的唯一 key）。
 * @param normal 普通态的动作。
 * @param selection 多选态的动作。
 */
@Composable
fun AnimatedTopBarActions(
    selectionMode: Boolean,
    normal: @Composable RowScope.() -> Unit,
    selection: @Composable RowScope.() -> Unit,
) {
    AnimatedContent(
        targetState = selectionMode,
        transitionSpec = {
            (
                fadeIn(GlassMotion.barExpand()) +
                    scaleIn(GlassMotion.scaleIn(), initialScale = 0.92f)
                ) togetherWith (
                fadeOut(GlassMotion.barCollapse()) +
                    scaleOut(GlassMotion.scaleOut(), targetScale = 0.92f)
                ) using SizeTransform(
                clip = false,
                sizeAnimationSpec = { _, _ -> GlassMotion.springOf<IntSize>(1f, 0.3f) },
            )
        },
        contentAlignment = Alignment.CenterEnd,
        label = "topBarActions",
    ) { inSelection ->
        // 官方动作间距：`example/GlassPage.kt:363` 用 `Arrangement.spacedBy(8.dp)`。
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (inSelection) selection() else normal()
        }
    }
}

/**
 * 页面内容内边距。
 *
 * 形态与官方 `example/utils/PageUtils.kt:209-245` 的 `pageContentPadding` 一致：返回**惰性**
 * [PaddingValues]，`calculate*Padding()` 在布局阶段才读取安全区与侧栏让位，因此侧栏 300ms morph
 * 只重新布局、不重组整页。
 *
 * - start/end = `max(安全区, 侧栏让位, 宿主 padding) + 页面横向留白`
 * - top = Scaffold 给的顶栏高度 + extraTop
 * - bottom = Scaffold 给的底栏高度 + 末尾留白 + extraBottom
 *
 * **横向留白是"相加"不是"取大"**：官方页面把 12dp 卡片外边距挂在每张卡片上
 * （`example/MainPage.kt:349`、`IconPage.kt:193` 的 `Modifier.padding(horizontal = 12.dp)`），
 * 而 `pageContentPadding` 只管安全区与侧栏让位，于是"侧栏让位 262dp + 卡片外边距 12dp"叠出来的
 * 是 274dp。早先 Glacier 把它写成 `max(让位, 12dp)`，窄屏看不出问题，一旦展开侧栏（让位 262dp）
 * 那 12dp 就被吞掉，卡片直接贴住玻璃侧栏 —— 这是自造的写法，已按官方语义改成相加
 * （实测：展开侧栏时卡片左边距从 262dp 变回官方同款 274dp）。
 *
 * 不要再包一层 `remember`：MIUIX Scaffold 的 contentPadding 是"实例不变、内部 holder 变化"的
 * [PaddingValues]，缓存它会拿到第一帧（顶栏还没量出来）的旧值。
 */
@Composable
fun pageContentPadding(
    innerPadding: PaddingValues,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    horizontal: Dp = PageMetrics.Horizontal,
    extraTop: Dp = 0.dp,
    extraStart: Dp = 0.dp,
    extraEnd: Dp = 0.dp,
    extraBottom: Dp = 0.dp,
): PaddingValues {
    val insets = LocalAppPageInsets.current.contentInsets
    val density = LocalDensity.current
    val pagePadding = remember(insets, density) { insets.asPaddingValues(density) }
    return remember(innerPadding, outerPadding, pagePadding, horizontal, extraTop, extraStart, extraEnd, extraBottom) {
        object : PaddingValues {
            override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = maxOf(
                innerPadding.calculateLeftPadding(layoutDirection),
                outerPadding.calculateLeftPadding(layoutDirection),
                pagePadding.calculateLeftPadding(layoutDirection),
            ) + (if (layoutDirection == LayoutDirection.Ltr) horizontal else 0.dp) +
                if (layoutDirection == LayoutDirection.Ltr) extraStart else extraEnd

            override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = maxOf(
                innerPadding.calculateRightPadding(layoutDirection),
                outerPadding.calculateRightPadding(layoutDirection),
                pagePadding.calculateRightPadding(layoutDirection),
            ) + (if (layoutDirection == LayoutDirection.Rtl) horizontal else 0.dp) +
                if (layoutDirection == LayoutDirection.Rtl) extraStart else extraEnd

            override fun calculateTopPadding(): Dp =
                maxOf(
                    innerPadding.calculateTopPadding(),
                    outerPadding.calculateTopPadding(),
                    pagePadding.calculateTopPadding(),
                ) + extraTop

            override fun calculateBottomPadding(): Dp =
                maxOf(
                    innerPadding.calculateBottomPadding(),
                    outerPadding.calculateBottomPadding(),
                    pagePadding.calculateBottomPadding(),
                ) + PageMetrics.BottomGap + extraBottom
        }
    }
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
 * 官方 `example/utils/PageUtils.kt:191-206` 的 `pageScrollModifiers`，签名与实现逐条对齐。
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
    enableScrollEndHaptic: Boolean = true,
    showTopAppBar: Boolean = true,
    topAppBarScrollBehavior: ScrollBehavior,
): Modifier {
    val state = topAppBarScrollBehavior.state
    val onOffsetChanged: (Float) -> Unit = remember(state) {
        // 官方同款：`example/utils/PageUtils.kt:198-200`。
        { offset -> state.overscrollOffset = offset }
    }
    return this
        .then(if (enableScrollEndHaptic) Modifier.scrollEndHaptic() else Modifier)
        .overScrollVertical(onOffsetChanged = if (showTopAppBar) onOffsetChanged else null)
        .then(if (showTopAppBar) Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection) else Modifier)
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
