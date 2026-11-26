package com.ayang.glacier.platform

import android.os.Build

/**
 * 预测性返回动画的**能力判断**。
 *
 * R21 起不再在这里改进程的 `ApplicationInfo.enableOnBackInvokedCallback`：
 * manifest 的 `android:enableOnBackInvokedCallback="true"` 常开，系统始终向
 * `NavDisplay` 派发返回进度；设置页开关只切换 `predictivePop` 转场
 * （`NoPredictiveBackTransition`），与 InstallerX-Revived / Mishka 一致。
 *
 * 这样去掉了一整条 `HiddenApiBypass + 反射 + recreate()` 路径：
 * - 不再依赖隐藏 API 豁免能否成功（OEM ROM 上不一定）；
 * - 切换开关不重建 Activity，也就不会闪一帧。
 *
 * 仍然只在 Android 14+（UPSIDE_DOWN_CAKE）显示这个开关：API 33 只有
 * manifest 开关、系统手势进度下发不完整。
 */
object PredictiveBack {

    val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
}
