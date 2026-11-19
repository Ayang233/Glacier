package com.ayang.glacier

import android.app.Application
import android.app.UiModeManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.getSystemService
import com.ayang.glacier.data.GlacierDataStore
import com.ayang.glacier.data.GlacierPreferences
import com.ayang.glacier.data.GlacierRepository
import com.ayang.glacier.domain.ThemeMode
import com.ayang.glacier.platform.Notifications
import com.ayang.glacier.platform.auto.AutoFreezeScheduler
import com.ayang.glacier.platform.auto.AutoFreezeService
import com.ayang.glacier.system.FreezeController

/**
 * Glacier 的进程入口。
 *
 * 只做四件事：建通知渠道、装配数据/系统层、恢复深色模式、同步自动冻结服务。
 * 任何 UI 逻辑都不允许写进这里。
 */
class GlacierApplication : Application() {

    lateinit var preferences: GlacierPreferences
        private set

    lateinit var store: GlacierDataStore
        private set

    lateinit var freeze: FreezeController
        private set

    lateinit var repository: GlacierRepository
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        Notifications.ensureChannels(this)
        preferences = GlacierPreferences(this)
        preferences.onThemeChanged = { mode -> applyNightMode(mode) }
        store = GlacierDataStore(this)
        freeze = FreezeController(this, preferences)
        freeze.initialize()
        repository = GlacierRepository(this, preferences, store, freeze)

        applyNightMode(preferences.themeMode)
        AutoFreezeScheduler.syncService(this)
    }

    fun applyNightMode(mode: ThemeMode = preferences.themeMode) {
        getSystemService<UiModeManager>()?.setApplicationNightMode(
            when (mode) {
                ThemeMode.Light -> UiModeManager.MODE_NIGHT_NO
                ThemeMode.Dark -> UiModeManager.MODE_NIGHT_YES
                ThemeMode.FollowSystem -> UiModeManager.MODE_NIGHT_AUTO
            },
        )
    }

    /**
     * 自动冻结服务只在"确实有待冻结的应用"时存在，
     * 避免常驻通知在无事可做时打扰用户。
     */
    fun syncAutoFreezeService() = AutoFreezeScheduler.syncService(this)

    fun setAutoFreezeServiceComponentEnabled(enabled: Boolean) {
        packageManager.setComponentEnabledSetting(
            ComponentName(this, AutoFreezeService::class.java),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
    }

    companion object {
        lateinit var instance: GlacierApplication
            private set

        val context: Context get() = instance.applicationContext
    }
}
