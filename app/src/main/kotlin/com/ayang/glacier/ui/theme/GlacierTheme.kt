package com.ayang.glacier.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.ayang.glacier.domain.ThemeMode
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * 唯一主题入口。
 *
 * 所有颜色、字号都来自 MIUIX 官方主题；Glacier 不自建色板，也不逐页写死颜色。
 * 深浅色切换通过官方 [ThemeController] 完成，动态取色走官方支持的模式。
 */
@Composable
fun GlacierTheme(
    mode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val controller = remember(mode) {
        ThemeController(
            colorSchemeMode = when (mode) {
                ThemeMode.FollowSystem -> ColorSchemeMode.System
                ThemeMode.Light -> ColorSchemeMode.Light
                ThemeMode.Dark -> ColorSchemeMode.Dark
            },
        )
    }
    MiuixTheme(controller = controller, content = content)
}
