package com.ayang.glacier.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ayang.glacier.GlacierApplication
import com.ayang.glacier.R
import com.ayang.glacier.feature.about.AboutScreen
import com.ayang.glacier.feature.about.LicensesScreen
import com.ayang.glacier.feature.apps.AppsScreen
import com.ayang.glacier.feature.home.HomeScreen
import com.ayang.glacier.feature.settings.SettingsScreen
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassNavigationBar
import top.yukonga.miuix.kmp.glass.GlassNavigationBarDefaults
import top.yukonga.miuix.kmp.glass.GlassNavigationItem
import top.yukonga.miuix.kmp.glass.GlassNavigationRail
import top.yukonga.miuix.kmp.glass.GlassNavigationRailDefaults
import top.yukonga.miuix.kmp.glass.rememberGlassNavigationRailState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.material.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.glass.GridView
import top.yukonga.miuix.kmp.icon.glass.HomeOutline
import top.yukonga.miuix.kmp.icon.glass.SettingsOutline

/**
 * Glacier 的根容器。
 *
 * Glass 结构（与知识库结构 A 同构）：
 *
 * ```
 * Scaffold(Transparent, insets=0)              ← 弹层宿主（最外层）
 * └ Box(fillMaxSize)
 *    ├ Box(fillMaxSize).layerBackdrop(root)    ★录制层 R（满屏，录在分页容器外）
 *    │  └ NavDisplay → 一级页 pager / 二级页
 *    ├ GlassNavigationBar(backdrop = root)     ← R 的兄弟（窄屏）
 *    └ GlassNavigationRail(backdrop = root)    ← R 的兄弟（宽屏）
 * ```
 *
 * 弹层由最外层 Scaffold 绘制，天然位于 R 之外，采样 R 即可避免"跨分页差一页宽"的坐标问题。
 */
@Composable
fun GlacierShell() {
    val context = LocalContext.current
    val app = context.applicationContext as GlacierApplication
    val preferences = app.preferences

    val glassActive = preferences.glassEnabled && isRuntimeShaderSupported()
    val wide = isWideLayout()

    val backStack = rememberNavBackStack<Route>(Route.Main)
    val scope = rememberCoroutineScope()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val pagerState = rememberPagerState(initialPage = tab) { 3 }
    LaunchedEffect(pagerState.currentPage) { tab = pagerState.currentPage }
    LaunchedEffect(tab) {
        if (pagerState.currentPage != tab) pagerState.animateScrollToPage(tab)
    }

    // 宽屏默认展开：库自身的默认值就是展开，平板/横屏上直接给出完整导航更符合预期。
    var railExpanded by rememberSaveable { mutableStateOf(wide) }
    val railState = rememberGlassNavigationRailState(initialExpanded = railExpanded)
    LaunchedEffect(railExpanded) { railState.expanded = railExpanded }
    // 让位量取"是否展开"这个布尔状态，而不是逐帧的 morph.progress：
    // progress 在组合期读到 0 会让整页永远不让位，而按布尔切换只多一次整页测量。
    val railInsetDp = if (wide && railExpanded) {
        GlassNavigationRailDefaults.Margin + GlassNavigationRailDefaults.ExpandedWidth
    } else {
        0.dp
    }
    val railInset: () -> androidx.compose.ui.unit.Dp = { railInsetDp }

    val surface = MiuixTheme.colorScheme.surface
    val rootBackdrop = rememberLayerBackdrop {
        drawRect(surface)
        drawContent()
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

    val navReserve = GlassNavigationBarDefaults.Height +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 32.dp

    CompositionLocalProvider(
        LocalGlassRequested provides preferences.glassEnabled,
        LocalGlassActive provides glassActive,
        LocalRootBackdrop provides rootBackdrop.takeIf { glassActive },
        LocalRailInset provides railInset,
        LocalNavReserve provides if (wide) 0.dp else navReserve,
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
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = railInsetDp),
                        onBack = {
                            if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                        },
                    ) {
                        entry<Route.Main> {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize(),
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
                        entry<Route.About> {
                            AboutScreen(
                                onBack = { backStack.removeAt(backStack.lastIndex) },
                                onOpenLicenses = { backStack.add(Route.Licenses) },
                            )
                        }
                        entry<Route.Licenses> {
                            LicensesScreen(
                                onBack = { backStack.removeAt(backStack.lastIndex) },
                            )
                        }
                    }
                }

                val navVisible = backStack.size == 1
                if (wide) {
                    GlassNavigationRail(
                        items = navItems,
                        selectedIndex = tab,
                        onSelect = onSelectTab,
                        backdrop = rootBackdrop.takeIf { glassActive },
                        state = railState,
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                } else {
                    GlassNavigationBar(
                        items = navItems,
                        selectedIndex = tab,
                        onSelect = onSelectTab,
                        backdrop = rootBackdrop.takeIf { glassActive },
                        visible = navVisible,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}
