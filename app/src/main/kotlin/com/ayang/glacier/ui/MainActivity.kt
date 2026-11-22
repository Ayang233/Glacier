package com.ayang.glacier.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.ayang.glacier.GlacierApplication
import com.ayang.glacier.ui.theme.GlacierTheme
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Glacier 主界面。
 *
 * 只用 Compose 承载，没有 XML 布局、没有第二套设计体系。
 * 主题取官方 [GlacierTheme]，Glass 开关由根容器统一决定。
 */
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as GlacierApplication

        setContent {
            val scope = rememberCoroutineScope()
            // requireUnlock 是直接读 SharedPreferences 的普通属性，不是快照状态。
            // 订阅 revision 并作为 remember key，设置页开关改完返回后锁屏才能立即生效/解除。
            val requireUnlock = remember(app.preferences.revision.intValue) {
                app.preferences.requireUnlock
            }
            GlacierTheme(mode = app.preferences.themeMode) {
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
