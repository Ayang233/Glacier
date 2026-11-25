package com.ayang.glacier.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [sheetBottomPadding] 的取值规则。
 *
 * 规则：底部弹层留白 = max(导航栏安全区, 手势区, 屏幕圆角) + 额外分节间距。
 * 三个系统值取大而不是相加——相加会把弹层内容顶高一整条导航栏。
 */
class PageInsetsTest {

    @Test
    fun `normal device with visible navigation bar keeps navigation bar value`() {
        // 模拟器实测：导航栏 24dp、手势区 32dp、圆角 10.7dp。
        assertEquals(44.dp, sheetBottomPadding(24.dp, 32.dp, 10.7.dp, 12.dp))
    }

    @Test
    fun `hidden gesture hint makes navigation bars zero and gesture strip wins`() {
        // 隐藏手势提示线：navigationBars = 0，只剩 mandatorySystemGestures。
        assertEquals(36.dp, sheetBottomPadding(0.dp, 24.dp, 0.dp, 12.dp))
    }

    @Test
    fun `large screen corner wins over small system insets`() {
        // 大 R 角设备：圆角比导航栏/手势区都大，内容要抬到圆角之外。
        assertEquals(64.dp, sheetBottomPadding(0.dp, 8.dp, 52.dp, 12.dp))
    }

    @Test
    fun `no insets and no rounded corner leaves only extra gap`() {
        assertEquals(12.dp, sheetBottomPadding(0.dp, 0.dp, 0.dp, 12.dp))
    }
}
