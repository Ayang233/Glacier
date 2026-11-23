// Copyright 2025, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0

package com.ayang.glacier.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.interfaces.HoldDownInteraction

/**
 * 把 [holdDownState] 映射成一次持续的 `HoldDown` 交互，离开时补一个 `Release`。
 *
 * 用途：菜弹层打开期间让被长按的单元保持按下态。库里的同款实现是 `HoldDownObserver`
 * （`miuix-ui/.../interfaces/HoldDownInteraction.kt:69-83`），但它是 `internal`，
 * **在库外无法调用**，而 `Card` / `BasicComponent` 的 `holdDownState` 形参只存在于组件内部。
 *
 * 这里只发官方公开的 [HoldDownInteraction]（类型、语义、释放时机都与官方一致），
 * 叠层仍然由主题里的 `MiuixIndication` 画（`HOLD_DOWN_ALPHA_DELTA = 0.10`），
 * 没有自绘高亮、没有第二套视觉。逻辑逐行对齐官方实现，保留 Apache-2.0 头。
 */
@Composable
fun HoldDownEffect(
    holdDownState: Boolean,
    interactionSource: MutableInteractionSource,
) {
    if (!holdDownState) return
    LaunchedEffect(interactionSource) {
        val hold = HoldDownInteraction.HoldDown()
        interactionSource.emit(hold)
        try {
            awaitCancellation()
        } finally {
            withContext(NonCancellable) { interactionSource.emit(HoldDownInteraction.Release(hold)) }
        }
    }
}
