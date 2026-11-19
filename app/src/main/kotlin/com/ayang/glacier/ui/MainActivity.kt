package com.ayang.glacier.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
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
            GlacierTheme(mode = app.preferences.themeMode) {
                SessionLock(requireUnlock = app.preferences.requireUnlock) {
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
