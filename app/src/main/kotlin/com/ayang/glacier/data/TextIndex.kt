package com.ayang.glacier.data

import android.icu.text.Transliterator
import com.ayang.glacier.domain.SearchKeys
import java.text.Normalizer

/**
 * 搜索键构建与匹配。
 *
 * 拼音来自 Android 内置 ICU 的 `Han-Latin` 转写，而不是第三方拼音库：
 * 少一个 GPL-2.0-only 依赖，也就少一个与 GPL-3.0 的许可证冲突点。
 * 转写不可用时自动退化为"仅按名称匹配"，搜索不会因此失效。
 *
 * 三种输入形态各建一个键：
 * - [SearchKeys.pinyin] 全拼（`jisuanqi`）
 * - [SearchKeys.initials] 首字母（`jsq`）
 * - [SearchKeys.t9] 九宫格数字（`547826`）
 */
object TextIndex {

    private val hanLatin: Transliterator? by lazy {
        runCatching { Transliterator.getInstance("Han-Latin") }.getOrNull()
    }

    fun build(label: String): SearchKeys {
        val romanized = runCatching { hanLatin?.transliterate(label) }.getOrNull() ?: label
        val plain = stripMarks(romanized).lowercase()

        // Han-Latin 用空格分隔音节；非汉字部分原样保留。
        val syllables = plain.split(' ', '\'', '-', '\u2019')
            .map { it.filter { char -> char.isLetterOrDigit() } }
            .filter { it.isNotEmpty() }

        val pinyin = syllables.joinToString("")
        val initials = buildString {
            syllables.forEach { syllable -> syllable.firstOrNull()?.let(::append) }
        }

        return SearchKeys(
            lower = label.lowercase(),
            pinyin = pinyin.ifEmpty { label.lowercase() },
            initials = initials.ifEmpty { label.lowercase() },
            t9 = toNineKey(pinyin),
        )
    }

    fun matches(keys: SearchKeys, query: String, fuzzy: Boolean, nineKey: Boolean): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        if (keys.lower.contains(q)) return true
        if (keys.pinyin.contains(q)) return true
        if (keys.initials.contains(q)) return true
        if (nineKey && q.all { it.isDigit() } && keys.t9.contains(q)) return true
        if (fuzzy) {
            if (levenshteinWithin(keys.lower, q)) return true
            if (containsInOrder(keys.lower, q)) return true
            if (containsInOrder(keys.pinyin, q)) return true
        }
        return false
    }

    private fun stripMarks(text: String): String {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
        val builder = StringBuilder(normalized.length)
        normalized.forEach { char ->
            if (Character.getType(char) != Character.NON_SPACING_MARK.toInt()) builder.append(char)
        }
        return builder.toString()
    }

    /** 差异小于原串长度，且按顺序包含查询的每个字符。 */
    private fun levenshteinWithin(raw: String, query: String): Boolean {
        if (query.length > raw.length) return false
        return levenshtein(raw, query) < raw.length
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(
                    current[j - 1] + 1,
                    previous[j] + 1,
                    previous[j - 1] + cost,
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    private fun containsInOrder(raw: String, query: String): Boolean {
        var index = 0
        for (char in query) {
            val found = raw.indexOf(char, index)
            if (found < 0) return false
            index = found + 1
        }
        return true
    }

    private fun toNineKey(raw: String): String = buildString {
        for (char in raw) {
            append(
                when (char.lowercaseChar()) {
                    in 'a'..'c' -> '2'
                    in 'd'..'f' -> '3'
                    in 'g'..'i' -> '4'
                    in 'j'..'l' -> '5'
                    in 'm'..'o' -> '6'
                    in 'p'..'s' -> '7'
                    in 't'..'v' -> '8'
                    in 'w'..'z' -> '9'
                    else -> char
                },
            )
        }
    }
}
