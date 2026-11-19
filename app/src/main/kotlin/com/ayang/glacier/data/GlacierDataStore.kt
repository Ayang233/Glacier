package com.ayang.glacier.data

import android.content.Context
import android.util.Log
import com.ayang.glacier.domain.DEFAULT_TAG_ID
import com.ayang.glacier.domain.ManagedApp
import com.ayang.glacier.domain.Tag
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 主页列表与标签的持久化。
 *
 * 数据格式沿用 Hail 已验证的结构（apps.json / tags.json，键名一致），
 * 这样未来的导入导出可以复用同一套解析逻辑；但目录与文件属于 Glacier 自己。
 */
class GlacierDataStore(context: Context) {

    private val dir = File(context.filesDir, "v1").apply { mkdirs() }
    private val appsFile = File(dir, "apps.json")
    private val tagsFile = File(dir, "tags.json")

    private val loaded = AtomicBoolean(false)

    val apps: MutableList<ManagedApp> = mutableListOf()
    val tags: MutableList<Tag> = mutableListOf()

    /** 首次访问磁盘。Repository 在刷新前显式调用，保证标签与列表同一时刻可用。 */
    fun load() = ensureLoaded()

    private fun ensureLoaded() {
        if (!loaded.compareAndSet(false, true)) return
        loadApps()
        loadTags()
    }

    // ---- 读 ----

    private fun loadApps() {
        runCatching {
            val array = JSONArray(appsFile.readText())
            val seen = HashSet<String>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val packageName = item.optString(KEY_PACKAGE).takeIf { it.isNotEmpty() } ?: continue
                val tagIds = item.optJSONArray(KEY_TAGS)?.let { ids ->
                    MutableList(ids.length()) { index -> ids.getInt(index) }
                } ?: mutableListOf(item.optInt(KEY_TAG, DEFAULT_TAG_ID))
                // 历史数据可能重复；列表 key 必须唯一。
                if (!seen.add(packageName)) continue
                apps += ManagedApp(
                    packageName = packageName,
                    pinned = item.optBoolean(KEY_PINNED, false),
                    whitelisted = item.optBoolean(KEY_WHITELISTED, false),
                    tagIds = tagIds,
                )
            }
        }.onFailure { Log.w(TAG, "read apps failed", it) }
    }

    private fun loadTags() {
        runCatching {
            val array = JSONArray(tagsFile.readText())
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val name = item.optString(KEY_TAG).takeIf { it.isNotEmpty() } ?: continue
                tags += Tag(name, item.optInt(KEY_ID, -1))
            }
        }.onFailure { Log.w(TAG, "read tags failed", it) }
        // 默认标签始终存在，且排在第一位。
        if (tags.none { it.id == DEFAULT_TAG_ID }) {
            tags.add(0, Tag(DEFAULT_LABEL, DEFAULT_TAG_ID))
        }
    }

    fun findApp(packageName: String): ManagedApp? {
        ensureLoaded()
        return apps.firstOrNull { it.packageName == packageName }
    }

    fun isManaged(packageName: String): Boolean = findApp(packageName) != null

    fun tagName(tagId: Int): String {
        ensureLoaded()
        return tags.firstOrNull { it.id == tagId }?.name ?: DEFAULT_LABEL
    }

    fun nextTagId(): Int {
        ensureLoaded()
        return (tags.maxOfOrNull { it.id } ?: DEFAULT_TAG_ID) + 1
    }

    // ---- 写 ----

    fun addApp(packageName: String, tagId: Int = DEFAULT_TAG_ID): Boolean {
        ensureLoaded()
        if (apps.any { it.packageName == packageName }) return false
        apps += ManagedApp(packageName = packageName, tagIds = mutableListOf(tagId))
        persistApps()
        return true
    }

    fun addApps(packageNames: Collection<String>, tagId: Int = DEFAULT_TAG_ID): Int {
        ensureLoaded()
        var added = 0
        packageNames.forEach { packageName ->
            if (apps.none { it.packageName == packageName }) {
                apps += ManagedApp(packageName = packageName, tagIds = mutableListOf(tagId))
                added++
            } else if (tagId != DEFAULT_TAG_ID) {
                val app = apps.first { it.packageName == packageName }
                if (tagId !in app.tagIds) app.tagIds += tagId
            }
        }
        if (added > 0) persistApps() else bump()
        return added
    }

    fun removeApps(packageNames: Collection<String>): Int {
        ensureLoaded()
        val before = apps.size
        apps.removeAll { it.packageName in packageNames }
        val removed = before - apps.size
        if (removed > 0) persistApps()
        return removed
    }

    fun removeFromTag(packageName: String, tagId: Int) {
        ensureLoaded()
        val app = findApp(packageName) ?: return
        if (tagId == DEFAULT_TAG_ID) return
        app.tagIds.remove(tagId)
        if (app.tagIds.isEmpty()) app.tagIds += DEFAULT_TAG_ID
        persistApps()
    }

    fun setPinned(packageName: String, pinned: Boolean) {
        ensureLoaded()
        findApp(packageName)?.pinned = pinned
        persistApps()
    }

    fun setWhitelisted(packageName: String, whitelisted: Boolean) {
        ensureLoaded()
        findApp(packageName)?.whitelisted = whitelisted
        persistApps()
    }

    fun setTags(packageName: String, tagIds: List<Int>) {
        ensureLoaded()
        val app = findApp(packageName) ?: return
        app.tagIds.clear()
        app.tagIds += (if (tagIds.isEmpty()) listOf(DEFAULT_TAG_ID) else tagIds).distinct()
        persistApps()
    }

    fun addTag(name: String): Tag {
        ensureLoaded()
        val tag = Tag(name.trim(), nextTagId())
        tags += tag
        persistTags()
        return tag
    }

    fun renameTag(tagId: Int, name: String) {
        ensureLoaded()
        val index = tags.indexOfFirst { it.id == tagId }
        if (index < 0) return
        tags[index] = tags[index].copy(name = name.trim())
        persistTags()
    }

    /** 删除标签：其下应用回到默认标签，避免出现"孤儿归属"。 */
    fun deleteTag(tagId: Int) {
        ensureLoaded()
        if (tagId == DEFAULT_TAG_ID) return
        tags.removeAll { it.id == tagId }
        apps.forEach { app ->
            app.tagIds.remove(tagId)
            if (app.tagIds.isEmpty()) app.tagIds += DEFAULT_TAG_ID
        }
        persistTags()
        persistApps()
    }

    // ---- 落盘 ----

    private fun persistApps() {
        val payload = JSONArray().apply {
            apps.forEach { app ->
                put(
                    JSONObject()
                        .put(KEY_PACKAGE, app.packageName)
                        .put(KEY_PINNED, app.pinned)
                        .put(KEY_WHITELISTED, app.whitelisted)
                        .put(KEY_TAGS, JSONArray(app.tagIds)),
                )
            }
        }.toString()
        writeAtomically(appsFile, payload)
        bump()
    }

    private fun persistTags() {
        val payload = JSONArray().apply {
            tags.forEach { tag ->
                put(JSONObject().put(KEY_TAG, tag.name).put(KEY_ID, tag.id))
            }
        }.toString()
        writeAtomically(tagsFile, payload)
        bump()
    }

    private fun bump() = onChanged?.invoke()

    /** 数据变更回调，由 Repository 注入以 bump 全局 revision。 */
    var onChanged: (() -> Unit)? = null

    private fun writeAtomically(target: File, text: String) {
        runCatching {
            val temp = File(target.parentFile, "${target.name}.tmp")
            temp.writeText(text)
            if (!temp.renameTo(target)) {
                target.writeText(text)
                temp.delete()
            }
        }.onFailure { Log.e(TAG, "write ${target.name} failed", it) }
    }

    companion object {
        private const val TAG = "GlacierDataStore"
        private const val KEY_PACKAGE = "package"
        private const val KEY_PINNED = "pinned"
        private const val KEY_WHITELISTED = "whitelisted"
        private const val KEY_TAGS = "tags"
        private const val KEY_TAG = "tag"
        private const val KEY_ID = "id"

        const val DEFAULT_LABEL = "Default"
    }
}
