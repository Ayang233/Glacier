package com.ayang.glacier.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

/**
 * 玻璃是否**真的**生效：用户开关 && 设备能力。
 *
 * 这是全项目唯一的决策点。页面里不允许再出现 `isRuntimeShaderSupported()` 判断，
 * 否则会出现"顶栏有玻璃、底栏没有"的割裂。
 */
val LocalGlassActive = staticCompositionLocalOf { false }

/**
 * 一级页整屏录制层。弹层、底栏、侧栏都采样这一层，
 * 因为只有它录在分页容器外面，坐标空间与最外层 Scaffold 一致。
 */
val LocalRootBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/**
 * 页面所在的 pane 是否已经由宿主让出了 **start 侧**安全区。
 *
 * 宽屏时 Shell 的 `railStartInset` 已经把「侧栏宽度 + start 侧系统 inset」从 pane 左缘扣除，
 * 页面内再让一次同一块 cutout 就是双算（官方用 `startAlreadyReserved` 精确表达同一语义：
 * `example/utils/PageUtils.kt:99-103`：`startAlreadyReserved` 为 true 时 `safeInsets` 只保留
 * End + Vertical）。窄屏（底栏档）为 false，页面照常自己让开切角/横屏导航条。
 */
val LocalPageStartReserved = staticCompositionLocalOf { false }

/**
 * 底栏实测总高度（含 20dp 顶部间距 + 胶囊 + 底部安全区）。
 *
 * 由 Shell 的 bottomBar 实测后写入；页面在自己的 Scaffold 里用它做透明占位，
 * 让正文底部 padding 与 FAB 位置都由 Scaffold 计算，不再手动猜。
 */
val LocalShellBottomBarHeight = staticCompositionLocalOf { 0.dp }

/**
 * 顶栏标题透明度。
 *
 * 宽屏玻璃侧栏折叠成顶部胶囊时会盖住页面标题，官方 example 用
 * `LocalTopAppBarTitleAlpha` 让标题跟随侧栏 morph 进度淡出；窄屏恒为 1。
 */
val LocalTopBarTitleAlpha = staticCompositionLocalOf<() -> Float> { { 1f } }

/**
 * 宽屏折叠胶囊为搜索浮层让出的顶部高度（官方 `searchTop`）。
 *
 * 侧栏展开时让位为 0；折叠成胶囊时浮层要下移，避免搜索条与胶囊重叠。
 */
val LocalSearchTopInset = staticCompositionLocalOf<() -> Dp> { { 0.dp } }

/**
 * 顶栏居中标题要避开的左界（官方 `LocalTopAppBarCenterObstructionStart`）。
 *
 * 二级页在宽屏折叠态下标题不再淡出，而是由官方
 * `TopAppBar` / `GlassTopAppBar` 的 `centerObstructionStart` 把居中标题整体右移，
 * 避开顶部胶囊。一级页保持官方的"标题随侧栏 morph 淡出"，因此传
 * [Dp.Unspecified]（不避让）。
 */
val LocalTopBarObstructionStart = staticCompositionLocalOf { Dp.Unspecified }

/**
 * 宽屏断点：与 MIUIX 官方 example 的 `shouldShowSplitPane` 一致。
 * 宽度 ≥ 840dp，或 ≥ 600dp 且横向（高/宽 < 1.2）。
 */
@Composable
fun isWideLayout(): Boolean {
    val configuration = LocalConfiguration.current
    val width = configuration.screenWidthDp
    val height = configuration.screenHeightDp
    return width >= 840 || (width >= 600 && height.toFloat() / width < 1.2f)
}

/**
 * 侧栏展开断点：与 MIUIX 官方 example 的 `shouldExpandNavigationRail` 一致。
 * 宽度 < 1200dp 时玻璃侧栏折叠成顶部胶囊，由用户点开。
 */
@Composable
fun isExtraWideLayout(): Boolean = LocalConfiguration.current.screenWidthDp >= 1200

/**
 * 页面正文录制层：先垫页面真实底色，再录内容。
 *
 * 顺序不可交换——裸录制只录内容，空白处透明，模糊透明等于透明，
 * 玻璃上就会出现"脏斑"。
 */
@Composable
fun rememberSurfaceBackdrop(color: Color): LayerBackdrop = rememberLayerBackdrop {
    drawRect(color)
    drawContent()
}
