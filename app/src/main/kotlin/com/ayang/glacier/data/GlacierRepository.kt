package com.ayang.glacier.data

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ayang.glacier.R
import com.ayang.glacier.domain.AppEntry
import com.ayang.glacier.domain.AppFilter
import com.ayang.glacier.domain.AppSnapshot
import com.ayang.glacier.domain.AppSort
import com.ayang.glacier.domain.DEFAULT_TAG_ID
import com.ayang.glacier.domain.Tag
import com.ayang.glacier.domain.WorkingMode
import com.ayang.glacier.platform.PackageTools
import com.ayang.glacier.platform.UiFeedback
import com.ayang.glacier.system.FreezeController
import com.ayang.glacier.system.FreezeOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.Collator
import java.util.Locale

/**
 * 业务门面。
 *
 * 页面只依赖它：读 [snapshot]，调 [setFrozen] / [addToHome] 这类业务动作。
 * 它负责把"用户意图"（DataStore）与"系统真实状态"（FreezeController）拼成一份可渲染快照。
 */
class GlacierRepository(
    private val context: Context,
    val preferences: GlacierPreferences,
    val store: GlacierDataStore,
    val freeze: FreezeController,
) {

    var snapshot by mutableStateOf(AppSnapshot())
        private set

    var refreshing by mutableStateOf(false)
        private set

    init {
        store.onChanged = { preferences.bumpRevision() }
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        if (refreshing) return@withContext
        refreshing = true
        try {
            store.load()
            val tags = store.tags.toList()
            val all = PackageTools.installedApplications(context)
                .asSequence()
                .filter { it.packageName != context.packageName }
                .mapNotNull { info -> buildEntry(info.packageName) }
                .toList()

            val homeOrder = store.apps.mapIndexed { index, app -> app.packageName to index }.toMap()
            val home = all.filter { it.packageName in homeOrder }
                .sortedWith(
                    compareBy<AppEntry> { if (it.pinned) 0 else 1 }
                        .thenBy { homeOrder[it.packageName] ?: Int.MAX_VALUE },
                )

            snapshot = AppSnapshot(
                all = all,
                home = home,
                tags = tags,
                revision = preferences.revision.intValue,
            )
        } catch (t: Throwable) {
            Log.e(TAG, "refresh failed", t)
        } finally {
            refreshing = false
        }
    }

    private fun buildEntry(packageName: String): AppEntry? {
        val info = PackageTools.applicationInfoOrNull(context, packageName) ?: return null
        val label = runCatching { info.loadLabel(context.packageManager).toString() }
            .getOrDefault(packageName)
        val packageInfo = PackageTools.packageInfoOrNull(context, packageName)
        val managed = store.findApp(packageName)
        return AppEntry(
            packageName = packageName,
            label = label,
            isSystem = info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0,
            isInstalled = PackageTools.isInstalled(context, packageName),
            frozen = freeze.isFrozen(packageName),
            pinned = managed?.pinned ?: false,
            whitelisted = managed?.whitelisted ?: false,
            tagIds = managed?.tagIds?.toList() ?: emptyList(),
            installTime = packageInfo?.firstInstallTime ?: 0L,
            updateTime = packageInfo?.lastUpdateTime ?: 0L,
            search = TextIndex.build(label),
        )
    }

    // ---- 查询 ----

    fun homeApps(tagId: Int, query: String = ""): List<AppEntry> {
        val fuzzy = preferences.fuzzySearch
        val nineKey = preferences.nineKeySearch
        return snapshot.home.filter { entry ->
            (tagId == DEFAULT_TAG_ID || tagId in entry.tagIds) &&
                TextIndex.matches(entry.search, query, fuzzy, nineKey)
        }.sortedWith(
            compareBy<AppEntry> { if (it.pinned) 0 else 1 }
                .thenComparator { a, b -> labelComparator().compare(a.label, b.label) },
        )
    }

    fun apps(query: String = "", sort: AppSort = preferences.appSort): List<AppEntry> {
        val fuzzy = preferences.fuzzySearch
        val nineKey = preferences.nineKeySearch
        val filters = AppFilter.entries.filter { preferences.filterEnabled(it) }
        val filtered = snapshot.all.filter { entry ->
            val typeOk = (entry.isSystem && AppFilter.SystemApps in filters) ||
                (!entry.isSystem && AppFilter.UserApps in filters)
            val stateOk = (entry.frozen && AppFilter.FrozenApps in filters) ||
                (!entry.frozen && AppFilter.UnfrozenApps in filters)
            typeOk && stateOk && TextIndex.matches(entry.search, query, fuzzy, nineKey)
        }
        val comparator = labelComparator()
        return when (sort) {
            AppSort.Name -> filtered.sortedWith { a, b -> comparator.compare(a.label, b.label) }
            AppSort.Install -> filtered.sortedByDescending { it.installTime }
            AppSort.Update -> filtered.sortedByDescending { it.updateTime }
        }
    }

    fun entryOf(packageName: String): AppEntry? = snapshot.all.firstOrNull { it.packageName == packageName }

    fun tagLabel(tag: Tag): String =
        if (tag.id == DEFAULT_TAG_ID) context.getString(R.string.label_default) else tag.name

    private fun labelComparator(): Collator = Collator.getInstance(Locale.getDefault())

    // ---- 主页归属 ----

    suspend fun addToHome(packageNames: Collection<String>, tagId: Int = DEFAULT_TAG_ID) {
        store.addApps(packageNames, tagId)
        refresh()
    }

    suspend fun removeFromHome(packageNames: Collection<String>) {
        store.removeApps(packageNames)
        refresh()
    }

    suspend fun setPinned(packageName: String, pinned: Boolean) {
        store.setPinned(packageName, pinned)
        refresh()
    }

    suspend fun setWhitelisted(packageName: String, whitelisted: Boolean) {
        store.setWhitelisted(packageName, whitelisted)
        refresh()
    }

    suspend fun setTags(packageName: String, tagIds: List<Int>) {
        store.setTags(packageName, tagIds)
        refresh()
    }

    suspend fun addTag(name: String): Tag? {
        if (name.isBlank()) return null
        val tag = store.addTag(name)
        refresh()
        return tag
    }

    suspend fun renameTag(tagId: Int, name: String) {
        if (name.isBlank()) return
        store.renameTag(tagId, name)
        refresh()
    }

    suspend fun deleteTag(tagId: Int) {
        store.deleteTag(tagId)
        refresh()
    }

    // ---- 冻结 ----

    suspend fun setFrozen(packageName: String, frozen: Boolean): Boolean {
        val ok = freeze.setFrozen(packageName, frozen)
        if (ok) refresh()
        return ok
    }

    suspend fun setFrozenBatch(packageNames: Collection<String>, frozen: Boolean): FreezeOutcome {
        val outcome = freeze.setListFrozen(packageNames, frozen)
        if (outcome is FreezeOutcome.Applied && outcome.count > 0) refresh()
        return outcome
    }

    suspend fun freezeAll(): FreezeOutcome =
        setFrozenBatch(snapshot.home.map { it.packageName }, true)

    suspend fun unfreezeAll(): FreezeOutcome =
        setFrozenBatch(snapshot.home.map { it.packageName }, false)

    suspend fun freezeNonWhitelisted(): FreezeOutcome =
        setFrozenBatch(snapshot.home.filter { !it.whitelisted }.map { it.packageName }, true)

    fun lockScreen(): Boolean = freeze.lockScreen()

    // ---- 系统动作 ----

    fun launch(packageName: String): Boolean {
        val intent = PackageTools.launchIntent(context, packageName) ?: return false
        return runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }

    fun openAppInfo(packageName: String) {
        UiFeedback.startSystemActivity(
            context,
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            PackageTools.packageUri(packageName),
        )
    }

    suspend fun uninstall(packageName: String): Boolean {
        val done = freeze.uninstall(packageName)
        refresh()
        return done
    }

    suspend fun reinstall(packageName: String): Boolean {
        val done = freeze.reinstall(packageName)
        if (done) refresh()
        return done
    }

    /** 把 base.apk 复制到应用外部缓存供用户取用。 */
    suspend fun extractApk(packageName: String): File? = withContext(Dispatchers.IO) {
        runCatching {
            val info = PackageTools.applicationInfoOrNull(context, packageName) ?: return@runCatching null
            val source = File(info.sourceDir)
            if (!source.exists()) return@runCatching null
            val dir = (context.getExternalFilesDir("apk") ?: File(context.filesDir, "apk")).apply { mkdirs() }
            val target = File(dir, "$packageName.apk")
            source.copyTo(target, overwrite = true)
            target
        }.getOrNull()
    }

    // ---- 能力 ----

    fun selectedMode(): WorkingMode = freeze.mode

    fun autoFreezeCandidates(): List<String> = snapshot.home
        .filter { !it.whitelisted && !it.frozen }
        .map { it.packageName }

    fun deviceSummary(): String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    private companion object {
        const val TAG = "GlacierRepository"
    }
}
