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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.unit.dp
import com.ayang.glacier.platform.Platform
import com.ayang.glacier.ui.LocalGlassActive
import com.ayang.glacier.ui.LocalPageBackdrop
import com.ayang.glacier.ui.LocalShellBottomBarHeight
import com.ayang.glacier.ui.LocalTopBarTitleAlpha
import com.ayang.glacier.ui.isWideLayout
import com.ayang.glacier.ui.rememberSurfaceBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.glass.GlassIconButton
import top.yukonga.miuix.kmp.glass.GlassTopAppBar
import top.yukonga.miuix.kmp.glass.glassSearchBarTransition
import top.yukonga.miuix.kmp.glass.rememberGlassSearchBarAlpha
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
@Composable
fun PageFrame(
    title: String,
    scrollBehavior: ScrollBehavior,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    /**
     * 是否允许大标题折叠。二级页传 `false`（官方 `AdaptiveTopAppBar(expandable=...)`）；
     * 宽屏时主页面也会自动折叠成紧凑标题。
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
    val titleAlpha = LocalTopBarTitleAlpha.current

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
                    titleAlpha = titleAlpha,
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
            CompositionLocalProvider(LocalPageBackdrop provides backdrop.takeIf { glassActive }) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (glassActive) Modifier.layerBackdrop(backdrop) else Modifier),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                    ) {
                        content(padding)
                    }
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
        TopAppBar(
            title = title,
            subtitle = subtitle,
            scrollBehavior = scrollBehavior,
            expandable = expandable,
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
 * 横向安全区只算刘海/横屏导航条：侧栏让位已经在 Shell 的 `railStartInset` 做过，
 * 页面再让一次就会双算。
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
    // 不能 remember：MIUIX Scaffold 的 contentPadding 是一个实例不变、
    // 内部 paddingHolder 变化的 PaddingValues。用 remember(scaffoldPadding) 会缓存第一帧
    // （顶栏底栏高度还没量出来）的旧值，导致正文永远按错误的顶栏高度起排。
    // 直接读 calculate*Padding() 在组合期订阅其内部状态，高度变化时自动重组。
    return PaddingValues(
        start = maxOf(horizontal, horizontalInsets.calculateLeftPadding(layoutDirection)),
        end = maxOf(horizontal, horizontalInsets.calculateRightPadding(layoutDirection)),
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
