package com.ayang.glacier.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

/** 用户是否想要玻璃（设置项）。 */
val LocalGlassRequested = staticCompositionLocalOf { true }

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
 * 页面正文录制层。
 *
 * **页面内的玻璃组件只能用它**：页面内容本身就在根录制层里，
 * 如果页面内的消费者去采根录制层，消费者就在自己采样源的子树里 —— 自采样会直接闪退。
 */
val LocalPageBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** 侧栏展开时内容需要让出的起始内边距。函数形式，避免组合期解包逐帧动画值。 */
val LocalRailInset = staticCompositionLocalOf<() -> Dp> { { 0.dp } }

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
