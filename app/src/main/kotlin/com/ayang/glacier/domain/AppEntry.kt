package com.ayang.glacier.domain

/**
 * 列表渲染用的应用快照条目。
 *
 * 名称、图标、冻结状态都是"某一时刻"的读取结果，随 [com.ayang.glacier.data.GlacierRepository] 刷新。
 */
data class AppEntry(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val isInstalled: Boolean,
    val frozen: Boolean,
    val pinned: Boolean,
    val whitelisted: Boolean,
    val tagIds: List<Int>,
    val installTime: Long,
    val updateTime: Long,
    val search: SearchKeys,
)

/** 预计算的搜索键，避免每次输入都重新做拼音转换。 */
data class SearchKeys(
    val lower: String,
    val pinyin: String,
    val initials: String,
    val t9: String,
)

/** 一次刷新得到的完整视图数据。 */
data class AppSnapshot(
    val all: List<AppEntry> = emptyList(),
    val home: List<AppEntry> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val revision: Int = 0,
)
