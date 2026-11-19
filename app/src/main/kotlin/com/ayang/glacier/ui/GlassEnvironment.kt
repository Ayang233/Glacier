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
 * 一级页整屏录制层。所有弹层、底栏、侧栏都采样这一层，
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
 * 页面底部需要为悬浮导航胶囊让出的高度。
 *
 * 由 Shell 统一计算（含手势区），页面不要自己猜。
 */
val LocalNavReserve = staticCompositionLocalOf { 0.dp }

/** 宽屏断点：与 MIUIX 官方 example 的侧栏阈值一致。 */
@Composable
fun isWideLayout(): Boolean {
    val width = LocalConfiguration.current.screenWidthDp
    return width >= 840
}

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
