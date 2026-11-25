package com.ayang.glacier.platform

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.os.Build
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * 预测性返回动画开关（系统返回手势的「跟手预览」）。
 *
 * 实现照官方 MIUIX example（`example/android/.../MainActivity.kt`）：
 * `android:enableOnBackInvokedCallback` 是 manifest 级开关，运行时只能改进程内的
 * [ApplicationInfo] 标志，而且必须赶在 Activity 创建前生效（窗口注册返回回调时读它），
 * 所以设置页切换后要重建 Activity —— 官方同样 `recreateWithoutTransition()`。
 *
 * **关闭它不等于关掉返回动画**：系统只是不再向应用派发逐帧的返回进度
 * （`OnBackInvokedCallback` 不再被调起），返回手势仍会走 `OnBackPressedDispatcher`，
 * MIUIX `NavDisplay` 照常播放自己的 pop 转场。少了的是「手指拖动时页面跟着预览」。
 */
object PredictiveBack {

    /** 与官方 example 一致：只在 Android 14+（UPSIDE_DOWN_CAKE）改这个标志。 */
    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    /**
     * 应用返回动画开关。必须在 `Activity.onCreate` 的 `super.onCreate` **之前**调用，
     * 因为 Activity 在创建窗口时就决定了是否走预测性返回。
     */
    fun apply(application: Application, enabled: Boolean) {
        if (!isSupported) return
        runCatching {
            HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/content/pm/ApplicationInfo;->setEnableOnBackInvokedCallback",
            )
            setEnableOnBackInvokedCallback(application.applicationInfo, enabled)
        }
    }

    @SuppressLint("PrivateApi")
    private fun setEnableOnBackInvokedCallback(info: ApplicationInfo, enabled: Boolean) {
        val method = ApplicationInfo::class.java.getDeclaredMethod(
            "setEnableOnBackInvokedCallback",
            Boolean::class.javaPrimitiveType,
        )
        method.isAccessible = true
        method.invoke(info, enabled)
    }
}

/**
 * 重建 Activity 但不播系统转场。官方 example 的 `recreateWithoutTransition()`：
 * 设置页切换预测性返回后重建，避免看到一次系统窗口动画。
 */
@Suppress("DEPRECATION")
fun Activity.recreateWithoutTransition() {
    overridePendingTransition(0, 0)
    recreate()
    overridePendingTransition(0, 0)
}

/** 从 Compose 的 `LocalContext` 里找到宿主 Activity（Compose 的 context 可能被包了多层）。 */
fun Context.findActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
