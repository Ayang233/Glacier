# R22 · 关闭预测性返回后，手势返回闪一帧

## 现象

用户在真机（Android 17 / MIUIX OS4）录屏：设置里关闭「预测性返回动画」后，
用系统返回手势时整页会**闪一下**。模拟器不易复现；验收交由用户。

## 定位

### 1. 录屏逐帧：闪的是一帧「系统预测性返回窗口预览」

录屏 1200×2608、约 90 fps。用逐帧差值和「单帧毛刺」检测（前一帧与后一帧几乎相同、
中间帧差异很大）在 941 帧里找到 5 个完全同构的异常帧：

| 帧号 | 前后帧 | 异常帧 |
|---|---|---|
| 101 | 同一张静止的设置页 | 整页向左偏移约 0.3 屏、压暗 |
| 281 / 431 / 568 / 718 | 同上 | 同上 |

异常帧只持续 1/90 s，前后都是**一级页**（Main / Settings tab），期间没有二级页。
这与 R20 的「开关重建首帧 inset 位移」不同：那次是整页**纵向**位移一个状态栏高度，
这次是**横向**位移 + 压暗，并且发生在返回手势开始时。

### 2. 代码层：R21 的「关闭」只关了应用内的跟手转场，没有关系统预览

R21 的实现是：

- manifest `android:enableOnBackInvokedCallback="true"` 常开；
- 设置项只把 `NavDisplay` 的 `transition` 从 `MiuixDefault` 换成自造的
  `NoPredictiveBackTransition`（`predictivePop` 忽略手指进度）。

也就是说，进程的 `ApplicationInfo.enableOnBackInvokedCallback` 始终是 true。
系统仍然把返回手势当成预测性返回，并在一级页（应用没有可 pop 的栈）画自己的
**返回窗口预览**。这个预览由系统绘制，应用侧换什么 `NavTransition` 都拦不住，
所以「关闭预览」在一级页等于没关，手势一起手就闪。

模拟器日志可以验证 R21 的运行时状态：

```
D/CoreBackPreview: Window{... MainActivity}: Setting back callback
  OnBackInvokedCallbackInfo{..., mIsAnimationCallback=false, ...}
```

`mIsAnimationCallback=false` 只说明「当前页面没有注册动画型回调」，
不代表应用退出了预测性返回；一级页没有 `NavDisplay` 的 predictive handler，
系统照样可以画自己的窗口预览。真正决定要不要进预测性返回的是应用级
`enableOnBackInvokedCallback`。

### 3. 自造转场的第二个风险

`NoPredictiveBackTransition` 在 `graphicsLayer` 里用 `relativeDepth - gesture.progress`
反推一个「不跟手」的进度。它必须和 `miuix-nav` 的共享 depth 驱动、角色切换
（Incoming → Outgoing → Covered）、scrim 曲线逐帧对齐；只要手势起点/终点附近有
一帧边界不同，就会看到整页错位。R21 的注释里也承认这是和驱动「各算一套」。
即使没有系统预览，这条路径也不应该继续保留。

## 修法

回到 MIUIX 官方 example / 用户参考实现的链路，只做三件事：

1. **页面转场只用官方预设**：`GlacierShell` 固定传 `NavTransitions.MiuixDefault`，
   删除 `ui/NoPredictiveBackTransition.kt`。不再按设置项换转场。
2. **系统级开关**：`PredictiveBack.apply()` 用 `HiddenApiBypass` 豁免 + 反射
   `ApplicationInfo.setEnableOnBackInvokedCallback(Boolean)`，只在 API 34+ 执行；
   失败只 `Log.w`，退回系统默认。`GlacierApplication.onCreate` 在进程启动时按偏好应用一次。
3. **切换后重启生效**（R23 修正）：设置页只写偏好 + 提示「重启应用后生效」，
   不再 `recreate()`。R22 最初用 `recreateWithoutTransition()` 立即生效，但重建会带来
   整窗 `windowBackground` 空白帧，详见 `docs/audit/miuix-os4-r23-audit.md`。

另外按参考实现补一级页返回权：

```kotlin
BackHandler(enabled = backStack.size <= 1 && tab != 0) { tab = 0 }
```

栈底、非第一个 tab 时先回主页；tab 0 交还系统。栈深 > 1 时该 `BackHandler` disabled，
唯一消费者是 `NavDisplay` 自己的预测性返回处理器，避免两个 `OnBackInvokedCallback`
抢同一个手势。

关闭后的语义：

| 设置 | 页面转场 | 系统跟手预览 | 生效 |
|---|---|---|---|
| 开 | `MiuixDefault` | 跟随系统 | 立即 |
| 关 | `MiuixDefault`（不变） | 关闭（`enableOnBackInvokedCallback=false`） | 重启应用后 |

## 验证

- `:app:compileDebugKotlin`、`:app:assembleDebug`、`:app:testDebugUnitTest` 通过。
- 模拟器 Android 16（API 36）安装 debug 包：
  - 关闭开关后 `adb logcat` 出现
    `W/WindowOnBackDispatcher: OnBackInvokedCallback is not enabled for the application.`，
    系统不再注册动画型返回回调；
  - 同一状态下在二级页做边缘滑动，logcat：
    `startBackNavigation ... mIsAnimationCallback=false`、
    `ShellBackPreview ... mAnimationCallback=false`，页面仍正常 pop 回上一级；
  - 打开开关后重启，logcat 恢复 `mIsAnimationCallback=true`，且页面停在主题设置页。
- 一级页新行为：Settings tab 按返回 → 回主页；主页再按返回 → 退出应用（回到上一个 app）。

## 真机待验收

- 关闭开关后，系统返回手势不再出现任何窗口预览/闪帧；
- 二级页返回仍有 MIUIX 的 pop 滑回动画；
- 开关切换时页面不闪、不掉回首页。

模拟器无法复现系统窗口预览那一帧，最终以用户真机录屏为准。
