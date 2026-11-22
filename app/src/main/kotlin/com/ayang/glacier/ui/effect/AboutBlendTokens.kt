// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
//
// 取自 MIUIX 官方 example/shared `component/blend/ColorBlendToken.kt`（Apache-2.0），
// 只保留关于页动态背景需要的两个 token。数值逐字节照搬官方，不自创。

package com.ayang.glacier.ui.effect

import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode

/** 官方 `Pured_Regular_Light`：浅色档信息卡的混色 token。 */
internal val AboutCardBlendLight = listOf(
    BlendColorEntry(Color(0x340034F9), BlurBlendMode.Overlay),
    BlendColorEntry(Color(0xB3FFFFFF), BlurBlendMode.HardLight),
)

/** 官方 `Overlay_Thin_Light`：深色档信息卡的混色 token。 */
internal val AboutCardBlendDark = listOf(
    BlendColorEntry(Color(0x4DA9A9A9), BlurBlendMode.Luminosity),
    BlendColorEntry(Color(0x1A9C9C9C), BlurBlendMode.PlusDarker),
)

/** 官方 AboutPage.kt:210-223 的品牌名 textureBlur 混色 token（按深浅分档）。 */
internal fun aboutLogoBlend(dark: Boolean): List<BlendColorEntry> = if (dark) {
    listOf(
        BlendColorEntry(Color(0xe6a1a1a1), BlurBlendMode.ColorDodge),
        BlendColorEntry(Color(0x4de6e6e6), BlurBlendMode.LinearLight),
        BlendColorEntry(Color(0xff1af500), BlurBlendMode.Lab),
    )
} else {
    listOf(
        BlendColorEntry(Color(0xcc4a4a4a), BlurBlendMode.ColorBurn),
        BlendColorEntry(Color(0xff4f4f4f), BlurBlendMode.LinearLight),
        BlendColorEntry(Color(0xff1af200), BlurBlendMode.Lab),
    )
}
