package com.ayang.glacier.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
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
 * 底栏实测总高度（含 20dp 顶部间距 + 胶囊 + 底部安全区）。
 *
 * 由 Shell 的 bottomBar 实测后写入；页面在自己的 Scaffold 里用它做透明占位，
 * 让正文底部 padding 与 FAB 位置都由 Scaffold 计算，不再手动猜。
 *
 * 只在**一级页**有值：二级页没有底栏（官方底栏属于一级目的地），Shell 会把它归零，
 * 页面于是退回按系统手势区让位（见 `PageFrame` 的 `bottomReserve`）。
 *
 * 这是**宿主自己的**底栏尺寸，MIUIX 没有对应组件（官方 example 的底栏由宿主 Scaffold
 * 的 padding 传入）；系统安全区与侧栏让位不在这里，见 [AppPageInsets]。
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
 * 顶栏居中标题要避开的左界（官方 `LocalTopAppBarCenterObstructionStart`）。
 *
 * 二级页在宽屏折叠态下标题不再淡出，而是由官方
 * `TopAppBar` / `GlassTopAppBar` 的 `centerObstructionStart` 把居中标题整体右移，
 * 避开顶部胶囊。一级页保持官方的"标题随侧栏 morph 淡出"，因此传
 * [Dp.Unspecified]（不避让）。
 *
 * 与官方 `LocalTopAppBarCenterObstructionStart` 一致用 compositionLocalOf 而非
 * staticCompositionLocalOf：胶囊宽度变化只通知读取它的标题，不触发整树重组。
 */
val LocalTopBarObstructionStart = compositionLocalOf { Dp.Unspecified }

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
