package com.ayang.glacier.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ayang.glacier.GlacierApplication
import com.ayang.glacier.platform.PredictiveBack
import com.ayang.glacier.ui.theme.GlacierTheme
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Glacier 主界面。
 *
 * 只用 Compose 承载，没有 XML 布局、没有第二套设计体系。
 * 主题取官方 [GlacierTheme]，Glass 开关由根容器统一决定。
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 预测性返回动画开关必须在 super.onCreate 之前落到进程的 ApplicationInfo 上：
        // Activity 创建窗口时就决定是否走 OnBackInvokedCallback（官方 example 同样位置）。
        // 关闭它只去掉系统的跟手预览，返回手势与 NavDisplay 的转场动画不受影响。
        val app = application as GlacierApplication
        PredictiveBack.apply(app, app.preferences.predictiveBackEnabled)

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val scope = rememberCoroutineScope()
            // requireUnlock 是直接读 SharedPreferences 的普通属性，不是快照状态。
            // 订阅 revision 并作为 remember key，设置页开关改完返回后锁屏才能立即生效/解除。
            val requireUnlock = remember(app.preferences.revision.intValue) {
                app.preferences.requireUnlock
            }
            GlacierTheme(mode = app.preferences.themeMode) {
                // Activity 重建（预测性返回开关）后，新窗口的第一帧常常还没拿到系统栏 inset，
                // 页面会先按"没有状态栏"排一帧再跳回正确位置 —— 就是开关后闪的那一下。
                RecreatedInsetsGate(recreated = savedInstanceState != null) {
                    SessionLock(requireUnlock = requireUnlock) {
                        LaunchedEffect(Unit) {
                            app.repository.refresh()
                            app.freeze.initialize()
                        }
                        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                            scope.launch { app.repository.refresh() }
                        }
                        GlacierShell()
                    }
                }
            }
        }
    }
}

/**
 * Activity 重建后的首帧保护。
 *
 * `recreate()` 出来的新窗口在首帧里经常拿不到 `WindowInsets`（系统栏 inset 仍是 0），
 * Compose 于是先按"没有状态栏"排一次版，下一帧才跳回正确位置。冷启动时这帧被启动画面
 * 盖住，而 `recreate()`（预测性返回开关）没有启动画面，用户看到的就是"闪一下"。
 *
 * 修法**不动组合结构**：内容照常组合（`rememberSaveable` 的恢复、NavDisplay 的返回栈都在
 * 原位完成，返回栈不会因为等待而丢失），只把第一帧的绘制透明度压到 0，等系统栏 inset
 * 真正下发后再显示。系统栏被隐藏时 inset 本来就是 0，因此最多等 3 帧兜底。
 * 首次冷启动（[recreated] 为 false）不加任何等待。
 */
@Composable
private fun RecreatedInsetsGate(
    recreated: Boolean,
    content: @Composable () -> Unit,
) {
    val systemBars = WindowInsets.systemBars
    val density = LocalDensity.current
    var visible by remember { mutableStateOf(!recreated) }
    if (recreated) {
        LaunchedEffect(Unit) {
            for (i in 0 until 3) {
                withFrameNanos { }
                if (systemBars.getTop(density) > 0 || systemBars.getBottom(density) > 0) break
            }
            visible = true
        }
    }
    // 组合结构在冷启动与重建两条路径上必须完全一致：`rememberSaveable` 的 key 由组合位置
    // 派生，换一层包裹（例如重建时多包一个 Box）会让 NavDisplay 的返回栈在恢复时对不上 key，
    // 结果就是重建后掉回首页。因此这里恒定包一层 Box，只切换绘制透明度。
    Box(
        Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.surface)
            // 只压绘制透明度：布局照常发生，inset 到达后的修正帧不会带着错误位置显示出来。
            .graphicsLayer { alpha = if (visible) 1f else 0f },
    ) {
        content()
    }
}
