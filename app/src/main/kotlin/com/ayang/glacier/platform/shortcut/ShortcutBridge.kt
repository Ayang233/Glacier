package com.ayang.glacier.platform.shortcut

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.ayang.glacier.GlacierApplication
import com.ayang.glacier.R
import com.ayang.glacier.app.GlacierApi
import com.ayang.glacier.domain.QuickAction
import com.ayang.glacier.platform.AppIconSource
import com.ayang.glacier.platform.PackageTools

/**
 * 桌面快捷方式。
 *
 * 动态快捷方式用 `glacier.` 前缀区分于固定快捷方式；
 * 固定快捷方式走系统请求，用户不确认就不会创建。
 */
object ShortcutBridge {

    private const val DYNAMIC_PACKAGE_PREFIX = "glacier.launch."
    private const val DYNAMIC_ACTION_ID = "glacier.action"

    fun pushAppShortcut(context: Context, packageName: String) {
        val app = context.applicationContext as? GlacierApplication ?: return
        if (app.preferences.requireUnlock) return

        val label = PackageTools.labelOf(context, packageName).toString()
        val icon = iconFor(context, packageName)
        val shortcut = ShortcutInfoCompat.Builder(context, DYNAMIC_PACKAGE_PREFIX + packageName)
            .setShortLabel(label)
            .setIcon(icon)
            .setIntent(GlacierApi.forPackage(GlacierApi.ACTION_LAUNCH, packageName))
            .build()
        runCatching {
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        }
    }

    fun pushActionShortcut(context: Context) {
        val app = context.applicationContext as? GlacierApplication ?: return
        val action = app.preferences.shortcutAction
        if (action == QuickAction.None) {
            runCatching { ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(DYNAMIC_ACTION_ID)) }
            return
        }
        val res = when (action) {
            QuickAction.FreezeAll -> R.string.action_freeze_all
            QuickAction.UnfreezeAll -> R.string.action_unfreeze_all
            QuickAction.FreezeNonWhitelisted -> R.string.action_freeze_non_whitelisted
            QuickAction.Lock -> R.string.action_lock
            QuickAction.LockFreeze -> R.string.action_lock_freeze
            else -> R.string.action_none
        }
        val intentAction = when (action) {
            QuickAction.FreezeAll -> GlacierApi.ACTION_FREEZE_ALL
            QuickAction.UnfreezeAll -> GlacierApi.ACTION_UNFREEZE_ALL
            QuickAction.FreezeNonWhitelisted -> GlacierApi.ACTION_FREEZE_NON_WHITELISTED
            QuickAction.Lock -> GlacierApi.ACTION_LOCK
            QuickAction.LockFreeze -> GlacierApi.ACTION_LOCK_FREEZE
            else -> return
        }
        val shortcut = ShortcutInfoCompat.Builder(context, DYNAMIC_ACTION_ID)
            .setShortLabel(context.getString(res))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_tile_frozen))
            .setIntent(Intent(intentAction).setClass(context, Class.forName(API_ACTIVITY)))
            .build()
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(context, shortcut) }
    }

    fun requestPin(context: Context, packageName: String) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
            com.ayang.glacier.platform.UiFeedback.toast(
                context,
                R.string.operation_failed,
                context.getString(R.string.action_add_pin_shortcut),
            )
            return
        }
        val label = PackageTools.labelOf(context, packageName).toString()
        val shortcut = ShortcutInfoCompat.Builder(context, "glacier.pin." + packageName)
            .setShortLabel(label)
            .setIcon(iconFor(context, packageName))
            .setIntent(GlacierApi.forPackage(GlacierApi.ACTION_LAUNCH, packageName))
            .build()
        runCatching { ShortcutManagerCompat.requestPinShortcut(context, shortcut, null) }
    }

    fun clearDynamic(context: Context) {
        runCatching { ShortcutManagerCompat.removeAllDynamicShortcuts(context) }
    }

    private fun iconFor(context: Context, packageName: String): IconCompat {
        val app = context.applicationContext as? GlacierApplication
        val size = (context.resources.displayMetrics.density * 48).toInt().coerceAtLeast(48)
        val bitmap = app?.let {
            AppIconSource.get(context, it.preferences.iconPack, it.preferences.synthesizeAdaptiveIcons)
                // 桌面快捷方式用彩色档：桌面图标不表达冻结状态，图标保持原色。
                .load(packageName, size, grayscale = false)
        } ?: drawableToBitmap(context, PackageTools.applicationInfoOrNull(context, packageName)?.loadIcon(context.packageManager))
        return if (bitmap != null) IconCompat.createWithBitmap(bitmap)
        else IconCompat.createWithResource(context, R.mipmap.ic_launcher)
    }

    private fun drawableToBitmap(context: Context, drawable: android.graphics.drawable.Drawable?): Bitmap? {
        val source = drawable ?: return null
        val size = (context.resources.displayMetrics.density * 48).toInt().coerceAtLeast(48)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        source.setBounds(0, 0, size, size)
        source.draw(canvas)
        return bitmap
    }

    private const val API_ACTIVITY = "com.ayang.glacier.feature.api.ApiActivity"
}
