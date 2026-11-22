package com.ayang.glacier.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import com.ayang.glacier.GlacierApplication
import com.ayang.glacier.R
import com.ayang.glacier.feature.about.AboutScreen
import com.ayang.glacier.feature.about.LicensesScreen
import com.ayang.glacier.feature.apps.AppsScreen
import com.ayang.glacier.feature.home.HomeScreen
import com.ayang.glacier.feature.settings.SettingsScreen
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassNavigationBar
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.glass.GlassNavigationRail
import top.yukonga.miuix.kmp.glass.GlassNavigationRailDefaults
import top.yukonga.miuix.kmp.glass.rememberGlassNavigationRailState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.GridView
import top.yukonga.miuix.kmp.icon.glass.HomeOutline
import top.yukonga.miuix.kmp.icon.glass.SettingsOutline
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PagerNavigationSpringSpec
import top.yukonga.miuix.kmp.utils.springAnimateToPage

/**
 * Glacier 的根容器。
 *
 * 结构对照 MIUIX 官方 example 的窄屏 `CompactScreenLayout`（`example/AppContent.kt:644-683`）
 * 与 `GlassPage`（`example/GlassPage.kt:264-341`）：
 *
 * ```
 * Scaffold(Transparent, insets = 0)              ← 弹层宿主（最外层）
 * ├ bottomBar（窄屏、仅一级页）= 官方 GlassNavigationBar
 * │  24dp 横向内边距 + 20dp 顶部间距 + 底部安全区（官方 example 原样数值）
 * │  实测高度 → LocalShellBottomBarHeight；二级页整层淡出（官方底栏本就属于一级目的地）
 * └ Box(fillMaxSize)
 *    ├ Box(fillMaxSize).layerBackdrop(root)      ★录制层 R（满屏，录在分页容器外）
 *    │  └ NavDisplay(Modifier.fillMaxSize()) → 一级页 pager / 二级页
 *    └ GlassNavigationRail(backdrop = root)      ← R 的兄弟（宽屏覆盖式侧栏）
 * ```
 *
 * **底栏的归属**：官方 example 的底栏装在一级目的地自己的 `Scaffold(bottomBar = ...)` 里
 * （`example/AppContent.kt` 的 `Home()`），进二级页时随整页离场，所以二级页没有底栏。
 * Glacier 的底栏必须留在宿主壳里——它采样的是满屏录制层 R，进了 `NavDisplay` 就成了
 * 记录层的后代（自采样红线）。因此这里用透明淡出表达同一件事：二级页里看不到底栏、
 * 也不缩放不位移，回到一级页再淡入。
 *
 * **侧栏让位不改容器**：`NavDisplay` 恒为 `fillMaxSize()`，侧栏占用的宽度通过
 * [AppPageInsets.contentInsets] 下发给内容（官方 `rememberAppPageInsets(overlayStart = ...)`）。
 * 早先那版用 `Modifier.layout` 把整个 `NavDisplay` 右移，背景与顶栏模糊带会被硬截断，
 * 官方 Glass 迁移指南 §4.2 明确禁止（`docs/miuix-glass-migration.md`）。
 *
 * 底栏进 Scaffold 槽位后，正文与 FAB 的让位全部由 Scaffold 计算，
 * 页面用 `LocalShellBottomBarHeight` 做透明占位，不再手写 `Height + 32dp` 之类的魔法值。
 */
@Composable
fun GlacierShell() {
    val context = LocalContext.current
    val app = context.applicationContext as GlacierApplication
    val preferences = app.preferences

    val glassActive = preferences.glassEnabled && isRuntimeShaderSupported()
    val wide = isWideLayout()
    val expandedRail = isExtraWideLayout()

    val backStack = rememberNavBackStack<Route>(Route.Main)

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(initialPage = tab) { 3 }
    val pagerFlingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        snapAnimationSpec = PagerNavigationSpringSpec,
    )
    LaunchedEffect(tab) {
        if (pagerState.currentPage != tab) pagerState.springAnimateToPage(tab)
    }
    // 只用水手滑动后真正停稳的 settledPage 回写 tab。
    // 不能用 currentPage：动画过程中 currentPage 会在两页之间来回跳，回写成 tab 会重启
    // 上面的 LaunchedEffect，把正在进行的 spring 取消在半路（表现为两页各露一半）。
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { settled ->
            if (tab != settled) tab = settled
        }
    }
    // 旋转 / 分屏改变宽度后，分页器可能保留按旧页宽算出的偏移，出现"两页各露一半"。
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    LaunchedEffect(screenWidthDp) {
        if (pagerState.currentPageOffsetFraction != 0f) pagerState.scrollToPage(tab)
    }

    // 侧栏展开状态由宽度决定：≥1200dp 常驻展开，840–1200dp 折叠成顶部胶囊（官方 example 行为）。
    val railState = rememberGlassNavigationRailState(initialExpanded = expandedRail)
    LaunchedEffect(expandedRail) { railState.expanded = expandedRail }

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val railProgress = { railState.progress.coerceIn(0f, 1f) }
    // 悬浮侧栏占用的宽度：只下发给内容，背景与录制层保持满宽
    // （官方 `AppContent.kt:237-245` 的 `overlayStart`，数值取 GlassNavigationRailDefaults）。
    val overlayStart: () -> Dp = {
        if (wide) {
            (GlassNavigationRailDefaults.Margin + GlassNavigationRailDefaults.ExpandedWidth) * railProgress()
        } else {
            0.dp
        }
    }
    // 折叠胶囊在顶部居中，搜索浮层要下移让开它（官方 rememberAppPageInsets 的 searchTop）。
    val searchTop: () -> Dp = {
        if (wide) {
            (GlassNavigationRailDefaults.CollapsedTop + GlassNavigationRailDefaults.CollapsedHeight + 4.dp) *
                (1f - railProgress())
        } else {
            0.dp
        }
    }
    // 官方契约的宿主侧输入：Glacier 只有玻璃悬浮侧栏，因此 start 侧永远由 overlayStart 表达，
    // 不存在"宿主已让位"的情况（官方 startAlreadyReserved 只在非悬浮导航列时为 true）。
    val hostPadding = PaddingValues(0.dp)
    val pageInsets = rememberAppPageInsets(
        hostPadding = hostPadding,
        startAlreadyReserved = false,
        overlayStart = overlayStart,
        searchTop = searchTop,
    )
    // 折叠胶囊会盖住页面标题：一级页标题透明度跟随侧栏 morph（官方 LocalTopAppBarTitleAlpha）。
    val titleAlpha: () -> Float = { if (wide) railProgress() else 1f }
    // 二级页不淡出标题，改为由官方 centerObstructionStart 把居中标题右移，避开顶部胶囊
    // （官方 AppContent.kt:228-234 / GlassPage.kt:347）。胶囊还没量出宽度时为 Unspecified。
    val obstructionStart: Dp = if (wide && !railState.expanded && railState.collapsedWidth > 0.dp) {
        railState.collapsedStart
    } else {
        Dp.Unspecified
    }

    val surface = MiuixTheme.colorScheme.surface
    val rootBackdrop = rememberLayerBackdrop {
        drawRect(surface)
        drawContent()
    }

    // 二级页转场特效（官方 AppContent.kt:256-305）：
    // - 系统屏幕圆角裁剪：窄屏跟随设备 RoundedCorner；宽屏是"分栏里的 pane"，按官方多窗格规则
    //   圆角清零（miuix/docs/guide/miuix-nav.md:346）并在 NavDisplay 上 clipToBounds 裁 pane。
    // - cornerClipMode = Leading：slide 风格只裁前缘（NavDisplayEffects.kt:19-31）。
    // - dimAmount = 0.5f：官方 slide 风格默认上限（AppContent.kt:274-281）。
    // - backdropColor = surface：官方同样传主题 surface（AppContent.kt:259、:283），
    //   卡片式缩放把下层缩小时露出的宿主底色与页面读起来是同一张背景。
    val navCornerRadius = if (wide) 0.dp else rememberNavSystemCornerRadius()
    val navEffects = remember(navCornerRadius, surface) {
        NavDisplayEffects(
            enableCornerClip = true,
            cornerClipRadius = navCornerRadius,
            cornerClipMode = NavCornerClipMode.Leading,
            dimAmount = 0.5f,
            blockInputDuringTransition = false,
            backdropColor = surface,
        )
    }
    // 滑动返回方向是**物理方向**，RTL 必须镜像（官方 AppContent.kt:287-292）。
    val backSwipe = if (layoutDirection == LayoutDirection.Rtl) {
        NavSwipeDirection.RightToLeft
    } else {
        NavSwipeDirection.LeftToRight
    }

    val navItems = listOf(
        GlassNavigationItem(MiuixIcons.Glass.HomeOutline, stringResource(R.string.nav_home)),
        GlassNavigationItem(MiuixIcons.Glass.GridView, stringResource(R.string.nav_apps)),
        GlassNavigationItem(MiuixIcons.Glass.SettingsOutline, stringResource(R.string.nav_settings)),
    )

    val onSelectTab: (Int) -> Unit = { index ->
        if (backStack.size > 1) {
            while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        }
        tab = index
    }

    // 底栏只属于一级目的地（见 `entry<Route.Main>`）：这里只留侧栏要用的「有没有对应目的地」。
    val navVisible = backStack.size == 1

    CompositionLocalProvider(
        LocalGlassActive provides glassActive,
        LocalRootBackdrop provides rootBackdrop.takeIf { glassActive },
        // 官方宿主→页面 inset 契约：系统安全区 + 侧栏让位 + 搜索让位都在这一处组合，
        // 页面与顶栏只消费 appPageHorizontalInsets / appPageTopBarInsets / searchTop。
        LocalAppPageInsets provides pageInsets,
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0),
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (glassActive) Modifier.layerBackdrop(rootBackdrop) else Modifier),
                ) {
                    NavDisplay(
                        backStack = backStack,
                        // 让位只发生在内容层：录制层（外层 Box）保持满宽，
                        // 否则侧栏位置没有像素可采，玻璃会失去背后内容。
                        // 宽屏是 pane：官方要求裁掉跑出 pane 的转场层（AppContent.kt:342、
                        // miuix-nav.md:345 "Clip the pane"），否则滑入页会画到玻璃侧栏下面。
                        // 满屏铺满：让位在内容层（官方 Glass 迁移指南 §4.2）。
                        // 宽屏是 pane，仍按官方要求裁掉跑出 pane 的转场层。
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (wide) Modifier.clipToBounds() else Modifier),
                        transition = NavTransitions.MiuixDefault,
                        effects = navEffects,
                        onBack = {
                            if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                        },
                    ) {
                        entry<Route.Main>(
                            // 一级页横向手势归 HorizontalPager（切页），不抢返回手势；
                            // 官方示例的 swipeBack 默认也是关闭的（AppState.kt:40 enableSwipeBack=false），
                            // 这里显式关掉避免与分页器争横向拖拽。
                            swipeDismiss = NavSwipeDirection.None,
                        ) {
                            // 底栏是**一级目的地自己的** bottomBar，照搬官方 example：
                            // `Home()` → `CompactScreenLayout(bottomBar = { NavigationBar(...) })`
                            // （官方 APK 的 dex 亦可验证：`AppContentKt.Home$lambda$1` →
                            // `CompactScreenLayout` → `NavigationBar` → `GlassNavigationBar`）。
                            // 因此二级页里根本没有底栏，进二级页时底栏随一级页一起转场离场，
                            // 既不需要 `visible` 的 0.6 倍缩小退场，也不会有任何缩放。
                            //
                            // 与官方唯一的差别：底栏采样的 backdrop 必须是**一级页内部**的录制层。
                            // 宿主壳那层 `rootBackdrop` 录的是整个 NavDisplay，底栏若进到 NavDisplay 里
                            // 再采它就是记录层的后代（自采样红线，Glass 审计 R4-P2）。
                            // 录制方式照官方 `rememberBlurBackdrop()`：先垫主题 surface 再画内容，
                            // 玻璃下方永远有不透明底，不会采到空洞。
                            val pageBackdrop = rememberSurfaceBackdrop(surface)
                            var barHeight by remember { mutableStateOf(0.dp) }
                            CompositionLocalProvider(
                                LocalTopBarTitleAlpha provides titleAlpha,
                                // 一级页标题随侧栏淡出（官方 primaryTitleAlpha），不参与避让。
                                LocalTopBarObstructionStart provides Dp.Unspecified,
                                // 底栏高度由一级页自己measured后下发给页面正文（官方由 Scaffold 的
                                // innerPadding 给；Glacier 的页面在分页器里，用这个 Local 表达同一件事）。
                                LocalShellBottomBarHeight provides if (wide) 0.dp else barHeight,
                            ) {
                                Box(Modifier.fillMaxSize()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .then(
                                                if (glassActive) {
                                                    Modifier.layerBackdrop(pageBackdrop)
                                                } else {
                                                    Modifier
                                                },
                                            ),
                                    ) {
                                        HorizontalPager(
                                            state = pagerState,
                                            modifier = Modifier.fillMaxSize(),
                                            flingBehavior = pagerFlingBehavior,
                                            verticalAlignment = Alignment.Top,
                                            beyondViewportPageCount = 0,
                                        ) { page ->
                                            when (page) {
                                                0 -> HomeScreen()
                                                1 -> AppsScreen()
                                                else -> SettingsScreen(
                                                    onOpenAbout = { backStack.add(Route.About) },
                                                )
                                            }
                                        }
                                    }
                                    if (!wide) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .fillMaxWidth()
                                                .onSizeChanged {
                                                    barHeight = with(density) { it.height.toDp() }
                                                },
                                        ) {
                                            GlacierBottomNavigation(
                                                items = navItems,
                                                selectedIndex = tab,
                                                onSelect = onSelectTab,
                                                backdrop = pageBackdrop.takeIf { glassActive },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        entry<Route.About>(
                            // 二级页启用官方交互式滑动返回（方向按 RTL 镜像）。
                            // 官方 example 把它放在设置项后面；Glacier 无此设置项，
                            // 这里只在没有横向滚动容器的二级页默认开启。
                            swipeDismiss = backSwipe,
                        ) {
                            CompositionLocalProvider(
                                // 二级页标题恒为 1：宽屏折叠态由官方 centerObstructionStart 让位，
                                // 不再整条标题消失（官方 AppContent.kt:432-435 对二级页的同一处理）。
                                LocalTopBarTitleAlpha provides { 1f },
                                LocalTopBarObstructionStart provides obstructionStart,
                            ) {
                                AboutScreen(
                                    onBack = { backStack.removeAt(backStack.lastIndex) },
                                    onOpenLicenses = { backStack.add(Route.Licenses) },
                                )
                            }
                        }
                        entry<Route.Licenses>(
                            swipeDismiss = backSwipe,
                        ) {
                            CompositionLocalProvider(
                                LocalTopBarTitleAlpha provides { 1f },
                                LocalTopBarObstructionStart provides obstructionStart,
                            ) {
                                LicensesScreen(
                                    onBack = { backStack.removeAt(backStack.lastIndex) },
                                )
                            }
                        }
                    }
                }

                if (wide) {
                    GlassNavigationRail(
                        items = navItems,
                        // 二级页没有对应的一级目的地：官方 example 用 -1 表示"无选中"。
                        selectedIndex = if (navVisible) tab else -1,
                        onSelect = onSelectTab,
                        backdrop = rootBackdrop.takeIf { glassActive },
                        state = railState,
                        // 官方行为：≥1200dp 展开面板；否则折叠成顶部胶囊，由胶囊上的按钮展开。
                        showToggle = true,
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                }
            }
        }
    }
}

/**
 * 窄屏底部导航胶囊。
 *
 * 数值照官方 `example/GlassPage.kt:292-320`：
 * 横向让位用官方 `appPageHorizontalInsets()`，24dp 内边距、20dp 顶部间距、
 * 底部 `max(24dp, safeBottom + 8dp)` 由外层 layout 统一处理，
 * `defaultWindowInsetsPadding = false` 避免组件再让一次系统 inset。
 *
 * 只在一级目的地上有这一层：官方 example 的底栏属于一级页自己的 `Scaffold`，
 * 二级页（About / Licenses）里没有底栏。`visible` 参数因此**不再使用**——
 * 它的语义是 0.6 倍缩小 + 模糊退场（`GlassMotion.NAV_HIDE_SCALE`），不是"二级页不显示"。
 */
@Composable
private fun GlacierBottomNavigation(
    items: List<GlassNavigationItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop?,
) {
    // 只取系统底部安全区：宿主自己的底栏高度不能参与，否则会把胶囊再抬高一次。
    // Glacier 的 AppPageInsets 不带 hostPadding（底栏在宿主 Scaffold 里单独占位），
    // 因此 safeInsets.bottom 就是系统手势区（官方 GlassPage 用 safePadding 的同一语义）。
    val safeBottom = rememberAppSafePadding().calculateBottomPadding()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // 官方写法：横向让位复用页面同一套 contentInsets（窄屏 = 刘海/横屏导航条）。
            .then(Modifier.appPageHorizontalInsets())
            .padding(start = 24.dp, end = 24.dp, top = 20.dp)
            .layout { measurable, constraints ->
                val bottom = maxOf(24.dp, safeBottom + 8.dp)
                    .roundToPx()
                    .coerceIn(0, constraints.maxHeight)
                val placeable = measurable.measure(constraints.offset(vertical = -bottom))
                layout(placeable.width, placeable.height + bottom) {
                    placeable.placeRelative(0, 0)
                }
            },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GlassNavigationBar(
                items = items,
                selectedIndex = selectedIndex,
                onSelect = onSelect,
                backdrop = backdrop,
                defaultWindowInsetsPadding = false,
            )
        }
    }
}
