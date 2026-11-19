package com.ayang.glacier.platform

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.getSystemService

/**
 * 与 Android 版本/设备能力相关的判断集中在这里。
 *
 * Glacier 的 minSdk 是 33，所以"版本分支"很少；真正需要分支的是
 * 隐藏 API 的签名变化与 OEM 差异，这些都只允许出现在本文件或 system 层。
 */
object Platform {

    val isApi34: Boolean = Build.VERSION.SDK_INT >= 34
    val isApi35: Boolean = Build.VERSION.SDK_INT >= 35

    fun isInteractive(context: Context): Boolean =
        context.getSystemService<PowerManager>()?.isInteractive == true

    fun isCharging(context: Context): Boolean {
        val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }

    fun hasUsageStatsAccess(context: Context): Boolean {
        val ops = context.getSystemService<AppOpsManager>() ?: return false
        val mode = ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** 最近 [windowMillis] 内最后一次被使用的包名，用于"跳过前台应用"。 */
    fun lastForegroundPackage(context: Context, windowMillis: Long): String? {
        if (!hasUsageStatsAccess(context)) return null
        val manager = context.getSystemService<UsageStatsManager>() ?: return null
        val now = System.currentTimeMillis()
        return manager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, now - windowMillis, now)
            ?.filter { it.lastTimeUsed > 0 }
            ?.maxByOrNull { it.lastTimeUsed }
            ?.packageName
    }

    /**
     * 模拟器探测。
     *
     * 只用于一个决策：顶栏是否走 `progressiveBlur`（AGSL 路径）。
     * AVD（无论 `swiftshader_indirect` 还是 host GPU）上 AGSL 的产出一律不可信，
     * 走库自带的 band 渐变而不是自绘替代视觉。
     */
    val isEmulator: Boolean by lazy {
        Build.HARDWARE.contains("ranchu", true) ||
            Build.HARDWARE.contains("goldfish", true) ||
            Build.PRODUCT.startsWith("sdk_") ||
            Build.FINGERPRINT.contains("generic", true) ||
            Build.MODEL.contains("sdk_gphone", true) ||
            Build.MODEL.contains("Emulator", true)
    }
}
