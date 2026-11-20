package com.ayang.glacier.data

import com.ayang.glacier.domain.SearchKeys
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 搜索键与匹配的回归测试。
 *
 * 只在 JVM 上跑，所以**不覆盖拼音转写**：`android.icu.text.Transliterator` 在单元测试里不可用，
 * `TextIndex` 会按设计退化为"仅按名称匹配"（`runCatching` 兜底）。中文全拼/首字母/九宫格
 * 需要真机或 instrumentation 测试。
 *
 * 这里守的是最容易改坏的三件事：空查询、拉丁名称的大小写/包含、模糊与九宫格的开关语义。
 */
class TextIndexTest {

    private fun keysOf(label: String): SearchKeys = TextIndex.build(label)

    @Test
    fun `blank query matches everything`() {
        val keys = keysOf("Glacier")
        assertTrue(TextIndex.matches(keys, "", fuzzy = false, nineKey = false))
        assertTrue(TextIndex.matches(keys, "   ", fuzzy = false, nineKey = false))
    }

    @Test
    fun `name match is case insensitive and substring based`() {
        val keys = keysOf("Glacier")
        assertTrue(TextIndex.matches(keys, "gla", fuzzy = false, nineKey = false))
        assertTrue(TextIndex.matches(keys, "ACI", fuzzy = false, nineKey = false))
        assertFalse(TextIndex.matches(keys, "zzz", fuzzy = false, nineKey = false))
    }

    @Test
    fun `nine key only applies when enabled and query is all digits`() {
        // 拉丁名称在 JVM 上退化为 label 本身，九宫格键仍会按字母映射出来（g→4, l→5 …）。
        val keys = keysOf("abc")
        assertTrue(TextIndex.matches(keys, "222", fuzzy = false, nineKey = true))
        assertFalse(TextIndex.matches(keys, "222", fuzzy = false, nineKey = false))
        // 含非数字时不走九宫格分支
        assertFalse(TextIndex.matches(keys, "22a", fuzzy = false, nineKey = true))
    }

    @Test
    fun `fuzzy tolerates one missing character in order`() {
        val keys = keysOf("glacier")
        // 顺序包含：g-l-c-r
        assertTrue(TextIndex.matches(keys, "glcr", fuzzy = true, nineKey = false))
        assertFalse(TextIndex.matches(keys, "glcr", fuzzy = false, nineKey = false))
    }

    @Test
    fun `punctuation and spaces do not break the key`() {
        val keys = TextIndex.build("My App!")
        assertTrue(keys.lower.contains("my app"))
        assertFalse(keys.pinyin.contains(" "))
    }
}
