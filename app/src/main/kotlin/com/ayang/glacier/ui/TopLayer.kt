package com.ayang.glacier.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * 页面级浮层的「窗口最上层」宿主。
 *
 * **逐字节移植自 MIUIX 官方 `example/shared/src/commonMain/kotlin/utils/TopLayer.kt`**
 * （Apache-2.0），与 `ui/PageInsets.kt` / `ui/effect/` 同一处理方式：官方只在 example
 * 里提供该能力，库不导出。
 *
 * 为什么需要它：`GlassTransformPopup` 是「就地绘制」的浮层（不依赖 `MiuixPopupHost`），
 * 按窗口坐标摆放。Glacier 的玻璃侧栏 / 导航栏画在页面之上，如果弹层留在页面子树里，
 * 宽屏展开侧栏时会被侧栏盖住；同时它也不能留在页面 backdrop 的录制层内（自采样红线）。
 * 官方 example 的做法就是：页面调用 [TopLayer] 把浮层提升到根部的 [TopLayerHost]，
 * 那里在录制层和 chrome 之外，既不会被侧栏遮挡，也不进入自己的采样源。
 *
 * 页面用法：
 * ```
 * PageFrame(overlay = { backdrop ->
 *     TopLayer {
 *         GlassTransformPopup(anchor = ..., backdrop = backdrop, ...)
 *     }
 * })
 * ```
 */
@Stable
class TopLayerState {
    internal val entries = mutableStateListOf<TopLayerEntry>()
}

@Stable
internal class TopLayerEntry {
    var content: @Composable BoxScope.() -> Unit by mutableStateOf({})
}

val LocalTopLayer = staticCompositionLocalOf<TopLayerState?> { null }

/** 把 [content] 画到最近的 [TopLayerHost]；没有宿主时就地画。 */
@Composable
fun TopLayer(content: @Composable BoxScope.() -> Unit) {
    val layer = LocalTopLayer.current
    if (layer == null) {
        Box(modifier = Modifier.fillMaxSize(), content = content)
        return
    }
    val entry = remember { TopLayerEntry() }
    SideEffect { entry.content = content }
    DisposableEffect(layer, entry) {
        layer.entries += entry
        onDispose { layer.entries -= entry }
    }
}

/** 承载 [TopLayer] 提升上来的内容。必须放在需要盖住的所有 chrome 之后。 */
@Composable
fun TopLayerHost(
    state: TopLayerState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        state.entries.forEach { entry ->
            key(entry) { entry.content(this) }
        }
    }
}
