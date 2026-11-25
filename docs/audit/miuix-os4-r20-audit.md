# R20 · 预测性返回开关不再闪一下 + dependent-option 行用官方动画

## 一、主题设置里的「预测性返回动画」开关会闪一下

### 现象（用户真机录屏逐帧分析）

真机录屏 `1200×2608`：连续开关 5 次，每次**恰好 1 帧**异常。把这一帧和相邻正常帧对比：

| 测量 | 结果 |
|---|---|
| 异常帧持续时间 | 1 帧（录屏约 60–90fps，即 11–16ms） |
| 整页（顶栏 + 所有行）的位移 | **向上 144px**，5 次开关完全一致 |
| 144px 对应 | 该机状态栏高度（48dp × 3.0） |
| 差值图 | 同一页面的所有文字/开关都出现两份，垂直错开 144px |

也就是说：重建后的**第一帧把整页按「没有状态栏 inset」排了一次版**，下一帧才跳回正确位置。
冷启动时这一帧被启动画面盖住；而 `recreate()` 没有启动画面，用户看到的就是「闪一下」。

### 原因

预测性返回开关是 manifest 级标志 `android:enableOnBackInvokedCallback`，运行时只能改进程内
`ApplicationInfo` 标志，并且必须赶在窗口创建前生效 —— 所以官方 MIUIX example 的做法就是
「改标志 + `recreateWithoutTransition()`」，Glacier 沿用的是同一套（`platform/PredictiveBack.kt`）。

问题不在这个机制，而在**重建后新窗口的第一帧**：新窗口的 `WindowInsets` 往往要到第一次
layout 之后才下发，Compose 首帧拿到的 `systemBars` 是 0，于是 `PageFrame` 的顶栏
`appPageTopBarInsets()` 与 `pageContentPadding()` 都按 0 计算 —— 整页上移一个状态栏高度。
下一帧 inset 到达、重新布局，页面跳回原位。

### 修法

`ui/MainActivity.kt` 新增 `RecreatedInsetsGate`（只在 `savedInstanceState != null`，即重建场景生效）：

1. **内容照常组合**：`rememberSaveable` 的恢复、MIUIX `NavDisplay` 的返回栈都在原位完成，
   重建后仍停在被切换的那一页；
2. 只把首帧的**绘制透明度压到 0**（`.graphicsLayer { alpha = 0f }`），等 `systemBars` 的
   top/bottom 真正非 0 后再显示；系统栏被隐藏时 inset 本来就是 0，所以最多等 3 帧兜底；
3. 同色兜底：等待期间画的是 `MiuixTheme.colorScheme.surface`（与 `windowBackground`
   `#F7F7F9` 同系），视觉上就是同一个空页面，不会看到第二套颜色。

另外去掉重建前的 `preferences.bumpRevision()`：旧窗口少一次无用的全量重组，重建前后更干净。

**踩到的坑**：第一版把内容放进 `if (ready)` 分支（等待期间换成占位 Box），重建后返回栈会
**掉回首页** —— `rememberSaveable` 的 key 由组合位置派生，重建时多一层 `Box` 会让
`NavDisplay` 的返回栈对不上 key。所以现在两条路径的组合结构完全一致，只切换透明度。

### 验证

- `:app:testDebugUnitTest`、`:app:assembleDebug` 通过；
- 模拟器上开关预测性返回：页面停在原页（主题设置），不崩溃，设置值正确落盘；
- 跟手预览语义不变：开 → 慢滑返回时页面跟手右移、后页压暗（`frames/07-preview-on.png`）；
  关 → 慢滑时页面不动、只有系统返回指示箭头，松手后仍播 MIUIX pop 转场（`frames/09-preview-off.png`）。

### 限制

模拟器上**复现不出**这一帧：本机重建后第一次 draw 时 `systemBars.top` 已经是 128px
（做了临时探针确认），所以修复效果以「用户录屏帧分析 + 机制」为依据，仍需要真机确认。
真机若仍有 1 帧空白（同色背景），说明该机 inset 下发晚于 3 帧兜底，需要再放宽兜底帧数。

## 二、「主页标签字号」这一行的收起/展开动画

### 原因

「隐藏应用名称」是父开关，`主页标签字号` 是依赖它的子项。Glacier 用
`CapturedAnimatedVisibility(visible = ...)` 包住子项，但**没有传 `enter` / `exit`**：

```kotlin
CapturedAnimatedVisibility(visible = !hideHomeLabels) { SliderPreference(...) }
```

官方 MIUIX 的 dependent-option 范式（`example/shared/.../ThemeSettingsPage.kt:247-251`）是显式给：

```kotlin
CapturedAnimatedVisibility(
    visible = isMonet,
    enter = fadeIn() + expandVertically(),
    exit = fadeOut() + shrinkVertically(),
) { ... }
```

少了 `expandVertically()/shrinkVertically()`，行高就不会跟着过渡，行是「淡一下然后硬跳」——
和 MIUIX OS4 的观感不一致。这就是自造参数、没照官方写法的代价。

### 修法

`feature/settings/SettingsPages.kt` 三处 dependent-option 行全部补官方 enter/exit：
- 图标包（`iconPacks.isNotEmpty()`）
- 主页标签字号（`!hideHomeLabels`）
- 自动冻结的整组子项（`autoFreezeEnabled`）

### 验证

逐帧截图：收起（`frames/04-label-row-collapse.png`）与展开（`frames/05-label-row-expand.png`）
都能看到行高与透明度一起过渡，滑块在中间帧被裁切，不再硬跳。

## 文件

- `app/src/main/kotlin/com/ayang/glacier/ui/MainActivity.kt`：`RecreatedInsetsGate`
- `app/src/main/kotlin/com/ayang/glacier/feature/settings/SettingsPages.kt`：dependent-option enter/exit
- 截图：`docs/verification/miuix-os4-r20/frames/`
