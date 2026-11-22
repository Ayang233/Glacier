# R8 · 宽屏（横屏）侧栏与内容间距自查 + 剩余"自定值"清单

> 起因：横屏展开玻璃侧栏时，应用列表卡片几乎贴住面板（面板右缘 686px、卡片左缘 688px，肉眼≈0）。
> 目标：先把这一处修到与官方实测一致，再把项目里"自己定/自己猜"的尺寸与间距逐项摊开。
> 测量环境：`emulator-5554`，`wm size 2400x1080 / wm density 420`（914dp 横屏，侧栏折叠态手动展开）
> 与 `wm size 2000x1200 / wm density 240`（1333dp，侧栏自动展开）；截图见 `docs/verification/miuix-os4-r8/`。

---

## 1. 根因：`pageContentPadding` 把"页面横向留白"写成了取大而不是相加

### 官方语义
官方 `pageContentPadding`（`example/utils/PageUtils.kt:209-245`）**只管安全区与侧栏让位**，
没有任何页面留白参数：

```kotlin
maxOf(innerPadding, outerPadding, pagePadding).calculateLeftPadding(...) + extraStart
```

页面的 12dp 卡片外边距是**挂在每张卡片上**的：`MainPage.kt:349`、`IconPage.kt:193`
的 `Card(Modifier.padding(horizontal = 12.dp))`。所以侧栏展开时实际是
`让位 262dp + 卡片外边距 12dp = 274dp`（相加）。

### Glacier 原来的写法（自造）
`PageFrame.pageContentPadding(..., horizontal = 12.dp)` 把页面留白塞进了 `maxOf` 的第 4 项：

```kotlin
maxOf(inner, outer, pagePadding, horizontal)   // ← 错
```

窄屏 `pagePadding = 0` 时结果仍是 12dp，看不出问题；一旦侧栏展开成 262dp，
那 12dp 就被吞掉，卡片直接贴住玻璃面板。

### 修复与实测（同一 914dp 窗口）
| | 面板右缘 | 卡片左缘 | 间距 |
|---|---|---|---|
| 官方示例 APK | 686px | 720px | 34px ≈ 13dp |
| Glacier（修复前） | 686px | 688px | 2px ≈ 0.8dp |
| Glacier（修复后） | 686px | 721px | 35px ≈ 13dp |

改动：`pageContentPadding` 改为 `max(inner, outer, pagePadding) + horizontal`，
并补了"为什么是相加"的注释。`SettingsScreen` / `LicensesScreen` 传 `horizontal = 0.dp`
并各自给卡片加 12dp（与官方写法一致），因此不受影响。

### 修复后的第二次翻车：左右留白被写成了方向性的
第一版把 `horizontal` 和 `extraStart/extraEnd` 混在同一个三元表达式里：

```kotlin
+ (if (layoutDirection == LayoutDirection.Ltr) horizontal else 0.dp) +   // ← 右缘在 LTR 下变 0
```

结果左边有 12dp、右边贴屏幕边（竖屏、横屏都一样），用户一眼看出"左右边距不同"。
`horizontal` 是**两侧都要加**的页面留白，只有 `extraStart/extraEnd` 才随书写方向换边：

```kotlin
left  = maxOf(inner, outer, pagePadding) + horizontal + (if (Ltr) extraStart else extraEnd)
right = maxOf(inner, outer, pagePadding) + horizontal + (if (Rtl) extraStart else extraEnd)
```

实测（修复后）：竖屏 1080×2400 卡片 32px … 1048px（左右各 12.2dp）；
914dp 横屏卡片 721px … 2367px（左右各 12.6dp，左侧距面板同为 12.6dp）。


---

## 2. 同类问题：宽屏断点量的是"屏幕"而不是"窗口"

官方 `shouldShowSplitPane` / `shouldExpandNavigationRail`
（`example/utils/AdaptiveUtils.kt:17-31`、`:39-46`）读的是
`LocalWindowInfo.current.containerSize` 换算出的 dp，**不是** `LocalConfiguration.screenWidthDp`。

Glacier 原来用 `screenWidthDp`：分屏、自由窗口、桌面模式下"窗口很窄但屏幕很宽"，
会误判成宽屏 → 把 250dp 侧栏塞进窄窗口、内容被挤到边上。已改成与官方同一 API
（`LocalWindowInfo.current.containerSize` + `LocalDensity`），断点数值不变（840 / 600+1.2 / 1200）。

## 3. 顺带对齐：侧栏 inset 契约

官方给侧栏显式 `windowInsetsPadding(pageInsets.windowInsets)` +
`defaultWindowInsetsPadding = false`（`AppContent.kt:585-589`），即"inset 由宿主算，组件不再算一次"。
Glacier 原来是靠组件默认值（组件内部自己取 `systemBars ∪ captionBar ∪ cutout`）。
今天两者结果等价（宿主 padding 为 0），但一旦引入宿主 padding 就会分叉，已按官方契约改写。

---

## 4. 项目里其余"自己定"的值：逐项摊开

### 4.1 已核对为官方数值（可直接对照）

| 值 | 用在哪 | 官方出处 |
|---|---|---|
| 12dp | 卡片外边距 / 分节间距 / 列表末尾 | `MainPage.kt:312/349`、`AboutPage.kt:399/442`、`MainPage.kt:339` |
| 28dp | 顶栏动作图标 | `GlassPage.kt:341/375/388` |
| 8dp | 顶栏动作之间的间距 | `GlassPage.kt:381` 的 `Arrangement.spacedBy(8.dp)` |
| 6dp + 12dp | 页签行上下内边距 | `IconPage.kt:266/296` 的 `padding(top = 6.dp, bottom = 12.dp)` |
| 6dp | 网格图标与标签间距 | `IconPage.kt:390` 的 `Spacer(height(6.dp))` |
| 80dp | 网格单元目标宽度 | `IconPage.kt:347-350` 的 `((maxWidth - 24.dp) / 80.dp)` |
| 12dp | 网格单元的 squircle 裁剪半径 | `IconPage.kt:403-411` 的 `squircleClip(12.dp)` |
| 12dp | 对话框按钮横向间距 | `GlassPage.kt:422-436` 的 `Row(spacedBy(12.dp))` |
| 4dp | 搜索结果行上下间距 | `IconPage.kt:104/221` 的 `SearchResultVerticalPadding` |
| 300/52/126/88/74/5dp | 关于页品牌区 | `AboutPage.kt:223` 等（逐个在代码注释里标了行号） |

### 4.2 业务自定（MIUIX 没有对应组件，不是"猜官方"，但也没有官方依据）

| 值 | 用在哪 | 说明 |
|---|---|---|
| 56dp | 主页网格应用图标 | 启动器网格是业务形态；MIUIX 的 `IconPage` 是图标画廊（28dp 矢量图标），没有应用网格组件 |
| 68dp | 紧凑模式网格单元宽度 | 设置项自定义的紧凑档 |
| 8dp / 16dp | 主页网格水平 / 垂直间距 | 同上，官方画廊用"整行背景 + 单元内 padding"，没有网格间距概念 |
| 40dp | 列表行应用图标 | 列表行没有官方组件（官方搜索行是 28dp 矢量图标） |
| 0.22 | 应用图标圆角比例（56dp → 12.32dp） | 参照 Mishka / InstallerX 的 16/72；与官方 `squircleClip(12.dp)` 同量级 |
| 48dp / 24dp | 空态纵向 / 横向留白 | 空态是业务自绘，无官方组件 |
| 32dp | 锁屏页整体内边距 | 业务自绘 |

这一类如果可以接受，就保持；如果希望"更 OS4"，需要产品侧给目标值，而不是继续从官方 example 里凑。

### 4.3 仍需留意的写法（本轮未改，风险低）

- `HomeScreen` 的 `BoxWithConstraints` 在组合期读 `contentPadding.calculateLeftPadding`
  算列数：值本身来自惰性 `PaddingValues`，侧栏 morph 时会重组网格（官方 IconPage 用
  `BoxWithConstraints(maxWidth)` 的同一手法，行为一致）。
- `PageMetrics.BottomGap = 12.dp` 追加在内容底部：官方页面用 `Spacer(height(12.dp))` 作为列表最后一项，
  数值一致、写法不同。

---

## 5. 涉及文件

```
app/src/main/kotlin/com/ayang/glacier/ui/components/PageFrame.kt    pageContentPadding 相加语义
app/src/main/kotlin/com/ayang/glacier/ui/GlassEnvironment.kt       宽屏断点改读窗口尺寸
app/src/main/kotlin/com/ayang/glacier/ui/GlacierShell.kt           侧栏 inset 交给宿主（官方契约）
```

## 6. 验证

- `assembleDebug` + `testDebugUnitTest` 通过。
- 914dp 横屏（手动展开侧栏）：`v2-914-apps-expanded.png`，卡片左缘 721px vs 官方 720px；
  `g-914-apps-fixed.png` 为同轮中间态；`g-914-expanded.png` / `g-914-expanded-fixed.png` 为修复前后对照。
- 1333dp 横屏（自动展开）：`ex-wide-home.png` / `g-wide-home.png` 同一窗口下双方内容起点均为 411px。
- 窄屏回归：`n-apps.png` / `final-narrow-home.png`，卡片仍为 12dp 外边距，未出现双层留白。
- 验收后已 `wm size reset` / `wm density reset`。
