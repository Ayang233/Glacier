package com.ayang.glacier.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * 宿主 → 页面的 inset 契约。
 *
 * **逐字节移植自 MIUIX 官方 `example/shared/src/commonMain/kotlin/utils/PageUtils.kt:62-140`**
 * （Apache-2.0）。官方只在 example 里提供这套契约，库本身不导出，因此按 `ui/effect/`
 * 同样的方式在本仓库落一份，而不是自己另写一套。
 *
 * 为什么不再用「把整页 `.layout` 右移」的做法（旧的 `railStartInset`）：
 * 官方 Glass 迁移指南明确要求 **`NavDisplay` 保持 `fillMaxSize()` 全屏铺满，只让内容避让**，
 * 否则背景与顶栏的模糊层会被硬截断（`docs/miuix-glass-migration.md` §4.2，
 * 官方 `AppContent.kt:330-370` 也是"背景铺满 + `contentInsets` 让位"）。
 *
 * 三层语义：
 * - [windowInsets]：整窗安全区（系统栏 ∪ host padding）；
 * - [safeInsets]：当前 pane 剩余的安全区（宿主已让位 start 时只剩 End + Vertical）；
 * - [contentInsets]：安全区 + 悬浮侧栏让位，**只作用于内容**，不移动录制层与背景。
 *
 * 动画值（`overlayStart` / `searchTop`）由布局阶段读取，不在组合期解包 ——
 * 侧栏 morph 时只触发重新布局，不重组整页。
 */
@Stable
class AppPageInsets(
    val windowInsets: WindowInsets,
    val safeInsets: WindowInsets,
    private val overlayStart: () -> Dp,
    val searchTop: () -> Dp,
) {
    val contentInsets: WindowInsets = object : WindowInsets {
        override fun getLeft(density: Density, layoutDirection: LayoutDirection): Int =
            safeInsets.getLeft(density, layoutDirection) +
                if (layoutDirection == LayoutDirection.Ltr) with(density) { overlayStart().roundToPx() } else 0

        override fun getRight(density: Density, layoutDirection: LayoutDirection): Int =
            safeInsets.getRight(density, layoutDirection) +
                if (layoutDirection == LayoutDirection.Rtl) with(density) { overlayStart().roundToPx() } else 0

        override fun getTop(density: Density): Int = safeInsets.getTop(density)

        override fun getBottom(density: Density): Int = safeInsets.getBottom(density)
    }
}

val LocalAppPageInsets = staticCompositionLocalOf {
    AppPageInsets(WindowInsets(0), WindowInsets(0), { 0.dp }, { 0.dp })
}

/**
 * 只有宿主（[GlacierShell]）组合平台安全区、外部 padding 与导航让位。
 *
 * @param hostPadding 宿主 Scaffold 给出的 padding（Glacier 的底栏就在宿主 Scaffold 里，
 *   因此这一项按官方用法传给页面而不是再抄一份）。
 * @param startAlreadyReserved 宿主是否已经把 start 侧让给了**非悬浮**的导航列。
 *   Glacier 只有玻璃悬浮侧栏，恒为 `false`（让位走 [AppPageInsets.contentInsets]）。
 * @param overlayStart 悬浮侧栏当前占用的宽度（随 morph 变化）。
 * @param searchTop 折叠胶囊给搜索浮层让出的顶部高度。
 */
@Composable
fun rememberAppPageInsets(
    hostPadding: PaddingValues,
    startAlreadyReserved: Boolean,
    overlayStart: () -> Dp,
    searchTop: () -> Dp,
): AppPageInsets {
    val systemInsets = WindowInsets.systemBars
        .union(WindowInsets.captionBar)
        .union(WindowInsets.displayCutout)
    return remember(systemInsets, hostPadding, startAlreadyReserved, overlayStart, searchTop) {
        val hostInsets = object : WindowInsets {
            override fun getLeft(density: Density, layoutDirection: LayoutDirection): Int = with(density) {
                hostPadding.calculateLeftPadding(layoutDirection).roundToPx()
            }

            override fun getRight(density: Density, layoutDirection: LayoutDirection): Int = with(density) {
                hostPadding.calculateRightPadding(layoutDirection).roundToPx()
            }

            override fun getTop(density: Density): Int = with(density) { hostPadding.calculateTopPadding().roundToPx() }

            override fun getBottom(density: Density): Int = with(density) { hostPadding.calculateBottomPadding().roundToPx() }
        }
        val windowInsets = systemInsets.union(hostInsets)
        val safeInsets = if (startAlreadyReserved) {
            windowInsets.only(WindowInsetsSides.End + WindowInsetsSides.Vertical)
        } else {
            windowInsets
        }
        AppPageInsets(windowInsets, safeInsets, overlayStart, searchTop)
    }
}

/** 只让横向内容避让，不动背景与录制层（官方 `appPageHorizontalInsets`）。 */
@Composable
fun Modifier.appPageHorizontalInsets(): Modifier = windowInsetsPadding(
    LocalAppPageInsets.current.contentInsets.only(WindowInsetsSides.Horizontal),
)

/** 顶栏：横向让位 + 剩余顶部安全区；顶栏内部不再自己让一次（官方 `appPageTopBarInsets`）。 */
@Composable
fun Modifier.appPageTopBarInsets(): Modifier = appPageHorizontalInsets().windowInsetsPadding(
    LocalAppPageInsets.current.safeInsets.only(WindowInsetsSides.Top),
)

/** 只有系统 / 宿主安全区，不含侧栏与折叠胶囊（官方 `rememberAppSafePadding`）。 */
@Composable
fun rememberAppSafePadding(): PaddingValues = LocalAppPageInsets.current.safeInsets.asPaddingValues()

/**
 * 搜索结果 / 悬浮控件用的底部留白（官方 `pageBottomPadding`）。
 *
 * 取宿主与系统安全区的**较大值**再加额外留白：宿主底栏自己已经含了手势区，
 * 相加会把列表末尾顶高一整条导航栏。
 */
@Composable
fun pageBottomPadding(
    outerPadding: PaddingValues = PaddingValues(0.dp),
    extraBottom: Dp = 0.dp,
): PaddingValues {
    val safePadding = rememberAppSafePadding()
    return remember(outerPadding, safePadding, extraBottom) {
        object : PaddingValues {
            override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = 0.dp

            override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = 0.dp

            override fun calculateTopPadding(): Dp = 0.dp

            override fun calculateBottomPadding(): Dp =
                maxOf(outerPadding.calculateBottomPadding(), safePadding.calculateBottomPadding()) + extraBottom
        }
    }
}
