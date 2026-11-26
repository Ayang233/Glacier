package com.ayang.glacier.platform

import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * 预测性返回动画的系统级开关。
 *
 * `android:enableOnBackInvokedCallback` 是 **manifest / 进程级**开关：manifest 里常开，
 * 运行时只能改进程内的 [ApplicationInfo] 标志。它决定系统是否把返回手势的逐帧进度
 * 派发给应用（即「手指拖到哪，页面预览到哪」），并且只在窗口创建时读取。
 *
 * 关闭它 **不会** 关掉应用自己的返回动画：系统只是不再下发预测性返回进度，
 * 返回手势仍会走 `OnBackPressedDispatcher`，MIUIX `NavDisplay` 照常播 pop 转场。
 * 这也正是「关闭预测性返回」这个设置项应有的语义。
 *
 * 官方 MIUIX example 用同一套 `HiddenApiBypass + 反射`。只在 API 34+ 执行；
 * 失败只记警告，不影响启动（OEM 可能屏蔽隐藏 API，此时退回系统默认行为）。
 *
 * 因为标志只在窗口创建期读取，设置页切换 **不在运行时重建 Activity**：
 * `recreate()` 会带来整窗 `windowBackground` 空白帧（R23 录屏确认），
 * 改为写入偏好 + 提示「重启应用后生效」，进程下次启动时由
 * `GlacierApplication.onCreate` 应用。
 */
object PredictiveBack {

    private const val TAG = "GlacierPredictiveBack"

    /** 与官方 example 一致：只在 Android 14+（UPSIDE_DOWN_CAKE）改这个标志。 */
    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    /**
     * 应用返回手势的预测性预览开关。
     *
     * 只能在 Activity 创建窗口前生效，因此调用点是进程入口
     * [com.ayang.glacier.GlacierApplication.onCreate]。
     */
    fun apply(application: Application, enabled: Boolean) {
        if (!isSupported) return
        runCatching {
            HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/content/pm/ApplicationInfo;->setEnableOnBackInvokedCallback",
            )
            setEnableOnBackInvokedCallback(application.applicationInfo, enabled)
        }.onFailure { error ->
            Log.w(TAG, "无法应用 enableOnBackInvokedCallback=$enabled，退回系统默认行为", error)
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
