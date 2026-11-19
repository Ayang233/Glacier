package com.ayang.glacier.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.ayang.glacier.platform.Platform
import com.ayang.glacier.ui.LocalGlassActive
import com.ayang.glacier.ui.LocalNavReserve
import com.ayang.glacier.ui.LocalPageBackdrop
import com.ayang.glacier.ui.LocalRailInset
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
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 页面骨架。
 *
 * 结构固定为：页面 Scaffold → 正文录制层（满宽，先垫底色）→ 内容。
 * 顶栏在 `topBar` 槽里，因此与录制层是兄弟，不会自采样；
 * 弹层写在 [overlay] 槽里，位于录制层之外，采样源应传一级页整屏层。
 */
@Composable
fun PageFrame(
    title: String,
    scrollBehavior: ScrollBehavior,
    modifier: Modifier = Modifier,
    subtitle: String = "",
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
    bottomBar: @Composable () -> Unit = {},
    overlay: @Composable () -> Unit = {},
    /**
     * 顶栏透明度。搜索浮层是整屏内容，而 Scaffold 先画正文再画顶栏，
     * 所以搜索展开时必须把页面顶栏隐掉，否则大标题会压在搜索结果上。
     * 用透明度而不是移除，避免 contentPadding 变化导致列表跳动。
     */
    topBarAlpha: Float = 1f,
    content: @Composable (PaddingValues) -> Unit,
) {
    val surface = MiuixTheme.colorScheme.surface
    val glassActive = LocalGlassActive.current
    val backdrop = rememberSurfaceBackdrop(surface)
    val railInset = LocalRailInset.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = surface,
        topBar = {
            Box(
                Modifier.graphicsLayer { alpha = topBarAlpha },
            ) {
                PageTopBar(
                    title = title,
                    subtitle = subtitle,
                    backdrop = backdrop.takeIf { glassActive },
                    scrollBehavior = scrollBehavior,
                    navigationIcon = navigationIcon,
                    actions = topBarActions,
                    bottomContent = { topBarBottom(backdrop.takeIf { glassActive }) },
                )
            }
        },
        bottomBar = bottomBar,
        floatingActionButton = {
            // FAB 由 Scaffold 摆在自己底边之上；悬浮导航胶囊是 Shell 叠在页面之上的，
            // Scaffold 并不知道它，所以这里显式再让出一段，避免 FAB 被胶囊压住。
            val reserve = LocalNavReserve.current
            Box(Modifier.padding(bottom = (reserve - 20.dp).coerceAtLeast(0.dp))) {
                floatingActionButton(backdrop.takeIf { glassActive })
            }
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
            overlay()
        }
    }
}

/** 顶栏的两档实现：玻璃档用官方 [GlassTopAppBar]，非玻璃档用普通 [TopAppBar]。 */
@Composable
fun PageTopBar(
    title: String,
    backdrop: LayerBackdrop?,
    scrollBehavior: ScrollBehavior,
    subtitle: String = "",
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable () -> Unit = {},
) {
    if (LocalGlassActive.current) {
        GlassTopAppBar(
            title = title,
            subtitle = subtitle,
            backdrop = backdrop,
            scrollBehavior = scrollBehavior,
            // AGSL 渐进模糊在模拟器上产出的像素不可信，交给库的 band 渐变路径。
            progressiveBlur = !Platform.isEmulator,
            navigationIcon = navigationIcon,
            actions = actions,
            bottomContent = bottomContent,
        )
    } else {
        TopAppBar(
            title = title,
            subtitle = subtitle,
            scrollBehavior = scrollBehavior,
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
                modifier = Modifier.size(24.dp),
                tint = MiuixTheme.colorScheme.onSurface,
            )
        }
    } else {
        IconButton(onClick = onClick) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(24.dp),
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

/** 页面内容内边距：统一把顶栏、导航胶囊、手势区都算进去。 */
@Composable
fun pageContentPadding(
    scaffoldPadding: PaddingValues,
    horizontal: Dp = 16.dp,
    extraTop: Dp = 0.dp,
    extraBottom: Dp = 0.dp,
): PaddingValues {
    val navReserve = LocalNavReserve.current
    return PaddingValues(
        start = horizontal,
        end = horizontal,
        top = scaffoldPadding.calculateTopPadding() + extraTop,
        bottom = scaffoldPadding.calculateBottomPadding() + navReserve + extraBottom + 24.dp,
    )
}

/** 设置页与列表页共用的分组卡片容器。 */
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

/** 页面底部留白，给悬浮导航胶囊与手势区。 */
@Composable
fun NavigationReserve() {
    val navBars = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Spacer(Modifier.height(navBars + LocalNavReserve.current))
}
