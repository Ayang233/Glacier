package com.ayang.glacier.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ayang.glacier.domain.AppFilter
import com.ayang.glacier.domain.AppSort
import com.ayang.glacier.domain.QuickAction
import com.ayang.glacier.domain.ThemeMode
import com.ayang.glacier.domain.WorkingMode

/**
 * Glacier 自己的设置存储。
 *
 * 包名与 Hail 不同，因此这是**独立**的偏好文件；不做静默的跨应用读取，
 * 历史数据迁移走导出/导入，而不是共享私有目录。
 */
class GlacierPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    // ---- 工作模式 ----
    var workingMode: WorkingMode
        get() = WorkingMode.fromKey(prefs.getString(KEY_WORKING_MODE, null))
        set(value) = prefs.edit().putString(KEY_WORKING_MODE, value.key).apply()

    // ---- 外观 ----
    /**
     * 主题模式。根界面在组合期读取，所以必须可观察；
     * [updateThemeMode] 同时负责落盘与通知 Application 切换系统夜间模式。
     */
    var themeMode: ThemeMode by mutableStateOf(
        ThemeMode.fromKey(prefs.getString(KEY_THEME, null)),
    )
        private set

    var onThemeChanged: ((ThemeMode) -> Unit)? = null

    fun updateThemeMode(mode: ThemeMode) {
        if (themeMode == mode) return
        themeMode = mode
        prefs.edit().putString(KEY_THEME, mode.key).apply()
        onThemeChanged?.invoke(mode)
        revision.intValue++
    }

    /**
     * Glass 开关。根界面在**组合期**读这个值决定录制层是否挂载，
     * 所以必须同时是可观察状态；[updateGlass] 负责同步落盘与状态。
     */
    var glassEnabled by mutableStateOf(prefs.getBoolean(KEY_GLASS, true))
        private set

    fun updateGlass(enabled: Boolean) {
        if (glassEnabled == enabled) return
        glassEnabled = enabled
        prefs.edit().putBoolean(KEY_GLASS, enabled).apply()
        revision.intValue++
    }

    var iconPack: String?
        get() = prefs.getString(KEY_ICON_PACK, null)
        set(value) = prefs.edit().putString(KEY_ICON_PACK, value).apply()

    var grayscaleFrozenIcons: Boolean
        get() = prefs.getBoolean(KEY_GRAYSCALE, true)
        set(value) = prefs.edit().putBoolean(KEY_GRAYSCALE, value).apply()

    var compactGrid: Boolean
        get() = prefs.getBoolean(KEY_COMPACT, false)
        set(value) = prefs.edit().putBoolean(KEY_COMPACT, value).apply()

    var synthesizeAdaptiveIcons: Boolean
        get() = prefs.getBoolean(KEY_SYNTHESIZE, false)
        set(value) = prefs.edit().putBoolean(KEY_SYNTHESIZE, value).apply()

    var homeLabelSize: Float
        get() = prefs.getFloat(KEY_LABEL_SIZE, 14f)
        set(value) = prefs.edit().putFloat(KEY_LABEL_SIZE, value).apply()

    /**
     * 主页是否隐藏应用名称（只影响网格排版，不改数据）。
     *
     * 隐藏时标签仍然参与布局（占位不变），所以网格的 16dp 行距 / 6dp 图文间距
     * 仍是同一套 MIUIX token，图标位置不会因为开关而跳动。
     */
    var hideHomeLabels: Boolean
        get() = prefs.getBoolean(KEY_HIDE_HOME_LABELS, false)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_HOME_LABELS, value).apply()

    /**
     * 预测性返回动画（系统返回手势的跟手预览）。
     *
     * 由系统级 `enableOnBackInvokedCallback` 控制（照 MIUIX 官方 example）：
     * 关闭后系统不再下发跟手进度，但返回仍走 OnBackPressedDispatcher，
     * MIUIX `NavDisplay` 的官方 pop 转场照常播放，**页面动画不会消失**。
     * 该标志只在窗口创建期读取，切换后需重启应用生效；不重建 Activity
     * （recreate 会出现整窗 windowBackground 空白帧）。
     */
    var predictiveBackEnabled: Boolean
        get() = prefs.getBoolean(KEY_PREDICTIVE_BACK, true)
        set(value) = prefs.edit().putBoolean(KEY_PREDICTIVE_BACK, value).apply()

    // ---- 搜索 ----
    var fuzzySearch: Boolean
        get() = prefs.getBoolean(KEY_FUZZY, false)
        set(value) = prefs.edit().putBoolean(KEY_FUZZY, value).apply()

    var nineKeySearch: Boolean
        get() = prefs.getBoolean(KEY_NINE_KEY, false)
        set(value) = prefs.edit().putBoolean(KEY_NINE_KEY, value).apply()

    // ---- 安全 ----
    var requireUnlock: Boolean
        get() = prefs.getBoolean(KEY_REQUIRE_UNLOCK, false)
        set(value) = prefs.edit().putBoolean(KEY_REQUIRE_UNLOCK, value).apply()

    // ---- 自动化 ----
    var autoFreezeEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_FREEZE, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_FREEZE, value).apply()

    var autoFreezeDelayMinutes: Long
        get() = prefs.getFloat(KEY_AUTO_FREEZE_DELAY, 0f).toLong()
        set(value) = prefs.edit().putFloat(KEY_AUTO_FREEZE_DELAY, value.toFloat()).apply()

    var skipWhileCharging: Boolean
        get() = prefs.getBoolean(KEY_SKIP_CHARGING, false)
        set(value) = prefs.edit().putBoolean(KEY_SKIP_CHARGING, value).apply()

    var skipForegroundApp: Boolean
        get() = prefs.getBoolean(KEY_SKIP_FOREGROUND, false)
        set(value) = prefs.edit().putBoolean(KEY_SKIP_FOREGROUND, value).apply()

    var skipNotifyingApp: Boolean
        get() = prefs.getBoolean(KEY_SKIP_NOTIFYING, false)
        set(value) = prefs.edit().putBoolean(KEY_SKIP_NOTIFYING, value).apply()

    var tileAction: QuickAction
        get() = QuickAction.fromKey(prefs.getString(KEY_TILE_ACTION, null))
        set(value) = prefs.edit().putString(KEY_TILE_ACTION, value.key).apply()

    var shortcutAction: QuickAction
        get() = QuickAction.fromKey(prefs.getString(KEY_SHORTCUT_ACTION, null))
        set(value) = prefs.edit().putString(KEY_SHORTCUT_ACTION, value.key).apply()

    // ---- 应用列表 ----
    var appSort: AppSort
        get() = AppSort.fromKey(prefs.getString(KEY_SORT, null))
        set(value) = prefs.edit().putString(KEY_SORT, value.key).apply()

    fun filterEnabled(filter: AppFilter): Boolean =
        prefs.getBoolean(filter.key, filter.default)

    fun setFilter(filter: AppFilter, enabled: Boolean) {
        prefs.edit().putBoolean(filter.key, enabled).apply()
        revision.intValue++
    }

    /**
     * 全局数据版本号：标签/应用条目是普通集合，不参与 Compose 快照。
     * 任何持久化变更 bump 一次，UI 读它即可重组。
     */
    val revision = mutableIntStateOf(0)

    fun bumpRevision() {
        revision.intValue++
    }

    companion object {
        const val PREF_FILE = "glacier_preferences"

        private const val KEY_WORKING_MODE = "working_mode"
        private const val KEY_THEME = "app_theme"
        private const val KEY_GLASS = "glass_effect"
        private const val KEY_ICON_PACK = "icon_pack"
        private const val KEY_GRAYSCALE = "grayscale_icon"
        private const val KEY_COMPACT = "compact_icon"
        private const val KEY_SYNTHESIZE = "synthesize_adaptive_icons"
        private const val KEY_LABEL_SIZE = "home_font_size_f"
        private const val KEY_HIDE_HOME_LABELS = "hide_home_labels"
        private const val KEY_PREDICTIVE_BACK = "predictive_back_animation"
        private const val KEY_FUZZY = "fuzzy_search"
        private const val KEY_NINE_KEY = "nine_key"
        private const val KEY_REQUIRE_UNLOCK = "biometric_login"
        private const val KEY_AUTO_FREEZE = "auto_freeze_after_lock"
        private const val KEY_AUTO_FREEZE_DELAY = "auto_freeze_delay_f"
        private const val KEY_SKIP_CHARGING = "skip_while_charging"
        private const val KEY_SKIP_FOREGROUND = "skip_foreground_app"
        private const val KEY_SKIP_NOTIFYING = "skip_notifying_app"
        private const val KEY_TILE_ACTION = "tile_action"
        private const val KEY_SHORTCUT_ACTION = "dynamic_shortcut_action"
        private const val KEY_SORT = "sort_by"
    }
}
