# R23 · 开关预测性返回动画不再闪一帧空白

## 现象

R22 验收通过后，用户反馈：切换「预测性返回动画」开关时，页面会闪一下。
模拟器可直接复现，不依赖真机。

## 录屏定位

模拟器 `screenrecord`（1080×2400）录开关一次，逐帧结果：

```
主题设置页（开关 off）
→ 整窗 windowBackground 空白帧（约 67 ms）
→ 主题设置页（开关 on）
```

证据图：`.workbuddy/toggle-flash.png`（before / blank / after）。

- 空白帧像素均匀 245~246，对应主题 `windowBackground` `#F7F7F9`；
  状态栏、手势条仍在，说明不是 Activity 被完全销毁，而是新窗口只画了底色。
- 开关前后页面顶部 300px 区域差异仅 0.13，**没有** R20 那种纵向 inset 位移。
- logcat：`Schedule relaunch activity` → `finishDrawing of relaunch ... 176 ms`。

## 根因

1. `enableOnBackInvokedCallback` 是 Activity/窗口创建期读取的进程标志，活窗口上改不了。
2. R22 为了「切换立即生效」，在 `onCheckedChange` 里调用了
   `recreateWithoutTransition()`。
3. `recreate()` 销毁旧窗口到新窗口首帧之间，系统只能画 `windowBackground`；
   `RecreatedInsetsGate` 又把内容 `alpha` 压 0 等 inset，无法盖住窗口空档，
   反而把空白帧保留下来（真机上 inset 首帧为 0 时会等满 3 帧）。
4. 结论：只要用 `recreate()` 换系统标志，就必然有一段窗口空白；
   R21 不重建所以没有这个闪，R22 重新引入重建后带出。

## 修法（用户选定 A）

按官方参考实现，切换只写偏好、**不重建 Activity**，提示「重启应用后生效」：

- `SettingsPages.ThemeSettingsScreen`：`onCheckedChange` 只写
  `preferences.predictiveBackEnabled`，`UiFeedback.toast(R.string.predictive_back_restart)`。
- 删除 `recreateWithoutTransition()` / `findActivity()` / `RecreatedInsetsGate`；
  `MainActivity` 回到最简结构。
- `GlacierApplication.onCreate` 在进程启动时按偏好应用系统标志：
  下一次冷启动生效，不再有窗口空档。
- 设置项 summary 与 toast 都明确写「切换后需重启应用生效」。

## 验证

- `:app:compileDebugKotlin`、`:app:assembleDebug`、`:app:testDebugUnitTest` 通过。
- 模拟器开关一次：logcat **没有** `Schedule relaunch activity`；
  screencap/录屏帧全部是页面内容，没有 25KB 级空白帧；
  toast 显示「已保存，重启应用后生效」。
- 关闭后强制停止并重启：`CoreBackPreview ... mIsAnimationCallback=false`，
  返回手势无系统预览；二级页 pop 动画正常。
- 一级页行为保持 R22：Settings tab 返回 → 主页；主页返回 → 退出。

## 取舍

开关不再即时生效，需要重启应用。这是官方参考实现的语义，也是避免重建空白的
唯一简单可靠方式；设置项文案和 toast 已明确告知。
