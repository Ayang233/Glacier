package com.ayang.glacier.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.utils.getRoundedCorner

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

/**
 * 底部弹层（[top.yukonga.miuix.kmp.glass.GlassBottomSheet]）内容该留多少底部空白。
 *
 * 官方把这块交给调用方：`glassbottomsheet.md` 写「主体导航栏内边距由调用方提供」，
 * `insideMargin` 默认底部 `0.dp`，官方示例也只 `navigationBarsPadding() + 16.dp`。
 * 但那只覆盖「导航栏 inset 有值」的机器，两种真实设备会漏：
 *
 * 1. **隐藏了手势提示线**：`navigationBars` 底部 inset 会变成 0，
 *    而屏幕底边那一条手势区仍会抢触摸（系统用 `mandatorySystemGestures` 报它），
 *    只按导航栏留白时弹层最后一行既贴底又被手势区占掉；
 * 2. **大 R 角屏幕**：物理圆角不属于任何 `WindowInsets`，窗口内容画进去会被裁掉。
 *    库没有自动避让，只提供 `getRoundedCorner()` 这个原语（`miuix-core/.../utils/Utils.kt`：
 *    Android 上读 `RoundedCorner.POSITION_BOTTOM_LEFT`，读不到退回
 *    `android:rounded_corner_radius_bottom`，桌面/其它平台 0dp；官方只有
 *    `DialogContentLayout` 拿它推对话框的同心理圆角，没有组件拿它做避让）。
 *
 * 所以取「安全区底部 ∪ 手势区底部 ∪ 屏幕圆角」的较大值，再加调用方自己的额外留白
 * （例如 [com.ayang.glacier.ui.components.PageMetrics.SectionGap]）。
 * 圆角为 0、导航栏有值的设备（含模拟器）结果与只留导航栏一致或略大。
 *
 * @param extraBottom 额外留白，通常是分节间距；加在最大安全值之后，不相加系统值。
 */
@Composable
fun rememberSheetBottomPadding(extraBottom: Dp = 0.dp): Dp {
    val safeBottom = rememberAppSafePadding().calculateBottomPadding()
    val gestureBottom = with(LocalDensity.current) {
        WindowInsets.mandatorySystemGestures.getBottom(this).toDp()
    }
    val screenCorner = getRoundedCorner()
    return sheetBottomPadding(safeBottom, gestureBottom, screenCorner, extraBottom)
}

/** [rememberSheetBottomPadding] 的纯函数部分（单测覆盖取值规则）。 */
internal fun sheetBottomPadding(
    safeBottom: Dp,
    gestureBottom: Dp,
    screenCorner: Dp,
    extraBottom: Dp = 0.dp,
): Dp = maxOf(safeBottom, gestureBottom, screenCorner) + extraBottom
