# R16 · 设置顺序回退、预测性返回开关、对称雪花图标、隐藏应用名称

本轮四件事：设置项顺序改回去、加「预测性返回动画」开关、品牌图标改成对称雪花、
主页新增「隐藏应用名称」。每一条都先查 MIUIX 官方有没有现成能力，结论写在下面。

## 1. 设置项顺序改回「合成自适应图标 → 主页标签字号」

R14 按用户要求对调过一次，本轮按要求对调回来：外观组现在是
`主题 → 玻璃效果 → 冻结图标去色 → 紧凑主页网格 → 合成自适应图标 → 主页标签字号 → 隐藏应用名称`。
只改顺序，没有动任何组件与默认值。

## 2. 预测性返回动画：官方实现方式是「改进程标志 + 重建 Activity」

**MIUIX 官方 example 的做法**（`example/android/.../MainActivity.kt:30-66`）：

1. `HiddenApiBypass.addHiddenApiExemptions("Landroid/content/pm/ApplicationInfo;->setEnableOnBackInvokedCallback")`，
   再反射调用 `ApplicationInfo.setEnableOnBackInvokedCallback(boolean)`；
2. 必须赶在 `super.onCreate()` **之前**执行（窗口注册返回回调时就定了走不走预测性返回）；
3. 切换后 `overridePendingTransition(0,0); recreate(); overridePendingTransition(0,0)` 立即重建，
   否则新标志要等下次冷启动才生效。

Glacier 的落法（`platform/PredictiveBack.kt`、`ui/MainActivity.kt`、`SettingsScreen.kt`）：

- `PredictiveBack.apply(application, enabled)` 在 `MainActivity.onCreate` 的 `super` 之前调用；
- 设置页开关写 `SharedPreferences` 后调 `findActivity()?.recreateWithoutTransition()`；
- 只在 Android 14+（`UPSIDE_DOWN_CAKE`）显示该开关，与官方一致（API 33 上隐藏）。

**关掉它不等于关掉返回动画**：MIUIX `NavDisplay` 的预测性返回由
`PredictiveBackHandlerWithSessions`（`miuix-nav/.../NavDisplay.kt:510-620`）驱动，
系统关掉 `OnBackInvokedCallback` 后只是不再派发逐帧进度，于是**没有跟手预览**；
返回手势仍会走 `OnBackPressedDispatcher` → `NavDisplay.onBack` → 普通 pop 转场动画。

实测（模拟器 Android 16，`docs/verification/miuix-os4-r16/`）：

| 状态 | 返回手势进行中 |
|---|---|
| 开 | about 页跟着手指右移，露出下面一页（`04-predictive-back-on-preview.png`） |
| 关 | about 页原地不动，松手后才播放 pop 转场（`05-predictive-back-off-no-preview.png`） |

两种状态都保留了返回动画，符合"只是不预测了"的要求。

## 3. 品牌图标：MIUIX 图标库没有雪花，改为自绘对称雪花

先在 MIUIX 全部图标源里搜过 `Snow`（`miuix-icons`、`miuix-glass-icons` 的 Kotlin
`imageVector` 全集），没有雪花/霜一类图标，所以按"否则自绘"处理。

旧图标是手描路径：3 条主轴本身是对称的（竖直 + 两条 30° 对角线），但 6 条臂上的分叉是
手工点的坐标，左右两撇的角度与长度不一致，放大看能看出"歪"。

新图标（`res/drawable/ic_launcher_foreground.xml`，生成脚本思路见审计）
用同一套参数对 6 条臂生成：主轴半长 `r`、分叉起点 `0.63r` / `0.41r`、
分叉与臂成 ±60°、长度 `0.31r` / `0.19r`，因此六向完全对称；
主体整体按 **0.90** 缩放（原来顶得比较满，黑主体略缩小），保持黑主体 + 白底
（`ic_launcher_background` 仍是纯白）。

同源文件一并重画：`ic_brand_mark.xml`（About 页，108 画布坐标 −18 裁到 72 安全区）、
`ic_tile_frozen.xml`（快捷设置磁贴，24dp 画布）。

## 4. 隐藏应用名称：MIUIX 没有官方 API，Glacier 自己实现但保持官方间距

**查证结果：没有官方 API。**MIUIX 是组件库，没有桌面/应用网格组件，
`miuix-ui`、`miuix-glass` 里没有"图标网格 + 名称"这种组件，文档里也没有隐藏名称的开关；
官方 example 的 `IconPage` 网格是示例代码（`example/shared/.../IconPage.kt:430-495`），
名称是它自己拼的 `Column { Icon; Spacer(6.dp); Text }`，不属于库 API。

Glacier 的实现（`GlacierPreferences.hideHomeLabels` + `HomeScreen.HomeAppItem`）：

- 新增设置项「隐藏应用名称」，默认关；
- 隐藏时**标签节点保留**、只画成透明（`Modifier.alpha(0f)`），所以：
  - 标签仍量出同样的行高，网格行距（`HomeGridMetrics.VerticalSpacing = 16dp`）、
    图文间距（`IconLabelGap = 6dp`）、列宽公式全部还是原来的 MIUIX token；
  - 图标位置与开关前完全一致，切换时不会跳动，也不会出现"行距变大/变挤"；
- 可访问性：标签语义清掉，改由整格 `contentDescription = 应用名`，读屏仍念得出名字；
- 「主页标签字号」是隐藏名称的子项，隐藏时按官方 dependent-option 范式
  （`CapturedAnimatedVisibility`，同 `SettingsPage.kt:257-265`）收起，避免出现"调了没反应"的滑块。

## 验证

- `:app:compileDebugKotlin`、`:app:testDebugUnitTest`、`:app:assembleRelease` 通过。
- 模拟器（1080×2400 / 420dpi / Android 16 / 宿主 GPU）：
  - 外观组顺序与三个开关见 `01-settings-navigation-and-appearance.png`；
  - 名称隐藏/显示同位置对比见 `02` / `03`；
  - 预测性返回开/关的手势中间帧见 `04` / `05`；
  - 雪花图标见 `06`。
- 仍需真机确认：预测性返回开关在 OEM 手势导航上的实际观感（模拟器用的是 AOSP 手势）。
