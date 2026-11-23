# R9 · 按压反馈与多选指示：哪些是官方的，哪些是自造的

> 起因：实拍两张截图提问——
> ①顶栏功能按钮按下时的圆形反馈，是不是 MIUIX OS4 官方的按钮反馈？
> ②如果是，应用（图标单元）按下时的反馈能不能也换成官方的那套？
> 方法：**不靠推断，先在设备上做对照实验**。官方 example APK（`top.yukonga.miuix.uitest.debug`）
> 与 Glacier debug 包同时装在同一台模拟器（`emulator-5554`，1080×2400 @420dpi），
> 用 `input motionevent DOWN/UP` 真实按住再 `screencap`，逐像素比对。
> 截图见 `docs/verification/miuix-os4-r9/`。

---

## 1. 顶栏那圈圆形：是官方的（Glass 档 = `GlassIconButton`）

### 实测
按住「排序」按钮（`input motionevent DOWN 851 198`）→ 截图
（`01-topbar-press-glass-zoom.png`）：

- 出现直径 **117px ≈ 44.6dp** 的圆，颜色 `#DEDEDE`；
- 图标本身变淡（纯黑 → `#595959`）。

### 三个数值全部来自库默认值，没有一处是我们写的

| 观测量 | 实测 | 官方来源 |
|---|---|---|
| 圆的直径 | 117px / 2.625 ≈ 44.6dp | `GlassTopAppBarDefaults.ButtonSize = 44.dp`（`GlassTopAppBar.kt:94`）；`GlassIconButton` 在顶栏上下文里默认取它 |
| 叠层颜色 | 表面 `#F7F7F7` → `#DEDEDE`，反解 alpha = 0.101 | `GlassTopAppBarDefaults.buttonPressedOverlay()` 浅色 = `Black.copy(alpha = 0.10f)`（`:156-160`） |
| 图标变淡 | 0.65 | `PressedContentAlpha = 0.6f`（`:103`），由 `GlassMotion.pressDown/pressUp` 驱动 |

调用侧只有 `PageFrame.kt` 的 `TopBarAction`：玻璃档 `GlassIconButton { Icon(28dp) }`，
非玻璃档 `IconButton { Icon(28dp) }`——这是官方示例的同款分工，没有自绘背景、没有自写 ripple。

### 非玻璃档同样官方
关掉「玻璃效果」再按住同一按钮（`02-topbar-press-noglass-zoom.png`）：
圆变成 40dp、叠层仍是 0.10、图标**不变淡**。这正是不带玻璃的 `IconButton`
（`miuix-ui/.../basic/IconButton.kt:87-101`：`defaultMinSize(40.dp)` +
`squircleSurface(cornerRadius = 40.dp)` 先裁再 `clickable`，叠层是 `MiuixIndication`
的 `PRESS_ALPHA_DELTA = 0.10`，`MiuixIndication.kt:27`）。

**结论①：两档都是官方组件的行为，不需要改。**

---

## 2. 图标单元按下那层灰底：也是官方的（整格 squircle）

### 官方实验
在官方 example APK 的 Icon 页按住一个图标单元（`Add`）→ `03-official-example-cell-press-zoom.png`：
叠层是**覆盖「图标 + 下方标签」的整格圆角矩形**，不是只套图标的小方块，也不是圆形。

对应源码：`example/IconPage.kt:403-411`

```kotlin
Column(Modifier.weight(1f)
    .squircleClip(12.dp)      // 先裁
    .clickable { ... }        // 再 clickable → indication 被裁成 12dp squircle
    .padding(horizontal = 4.dp, vertical = 10.dp))
```

### Glacier 侧
`HomeScreen.HomeAppItem` 是完全同构的写法（`squircleClip(CellCorner = 56 × 0.22 = 12.32dp)`
→ `combinedClickable` → 内容），实拍 `04-home-cell-press-zoom.png` 与官方叠层同级。
`AppListCardRow` 则是官方 `Card(showIndication = true, pressFeedbackType = None)`。

**结论②：按压叠层本身已经是官方形态，不需要替换。**
（顺带排除了"叠层方角/溢出"的旧问题：R7 前用无裁剪的 `clickable`，叠层才会糊成方角。）

---

## 3. 真正自造的那一处：图标外围的 2dp 蓝色描边

第二张截图里，除了灰色按压叠层，还有一个**贴着图标的细蓝色圆角描边**。这是我们自己画的：

```kotlin
// 旧 AppComponents.kt
val ringWidth by animateDpAsState(if (selected) 2.dp else 0.dp, GlassMotion.default())
Box(Modifier.matchParentSize().squircleBorder(width = { ringWidth }, color = { primary }, radius))
```

它虽然借了官方 `squircleBorder` + `GlassMotion`，但**"选中 = 图标描边"这套语言 MIUIX 里不存在**：
`Card` / `GlassCard` 没有 `selected` 形参（`Card.kt:87-154`、`GlassCard.kt:75-99`），
全库也没有任何"选中描边"的 token。这就是自造轮子。

### MIUIX 官方的"选中"语言是 `Checkbox`
库作者自己的应用 Mishka 里，应用级多选列表（`AppProxyScreen.kt:415-441`）就是：

```kotlin
BasicComponent(
    startAction = { AppIcon(...) },
    endActions = { Checkbox(state = if (isSelected) On else Off, onClick = onToggle) },
    onClick = onToggle,
)
```

所以按官方范式改造：

| 位置 | 改法 | 依据 |
|---|---|---|
| 应用列表 / 搜索结果行 | 行尾 `endActions` 放官方 `Checkbox`（选中 `On` / 未选 `Off`），行内不再描边 | Mishka 同构写法；`Checkbox` 是官方组件（26dp 圆、`primary` 填充、`secondary` 未选底、自带勾划动画 200–300ms） |
| 主页网格单元 | 已选中时在图标右上角叠一个官方 `Checkbox(On)` 作角标 | 网格没有"行尾列"，角标是唯一等价位置 |
| 未选中的网格单元 | **不画任何东西** | 见下 |

### 一个实测出来的取舍（值得记住）
第一版给网格单元也画了未选中态，实拍 `08-rejected-grid-unchecked-zoom.png`：
官方 Checkbox 未选中态是不透明的 `secondary` 圆，叠在 56dp 图标上就是**一块灰饼**，
把图标右下角糊掉。列表行有独立的右侧尾巴列，空圈还能当"可勾选"提示；网格没有这个位置。
所以网格只在选中时画角标（`06-home-selection-badge-zoom.png`），列表行保留完整两态
（`05-apps-selection-checkbox.png`）。

### a11y
勾选框 `onClick = null`：整格/整行的点按已经由 `combinedClickable` / `Card` 承担，
再挂一层会变成"热区里套热区"。`Checkbox(state = ...)` 自己会写 `toggleableState` 语义，
读屏仍能读到勾选状态。

---

## 4. 全项目复查（除上面这处，没有别的自绘原语）

```
grep "\.clickable|RoundedCornerShape|Color(0x|tween\(|spring\(|animate[A-Za-z]*AsState|\.background\("
  app/src/main/kotlin  （排除 ui/effect/ 的官方 BgEffect 移植）
```

只命中一类：`AppIconView` 里那个 `animateDpAsState` 的选中环（本次删除）。
其余 `RoundedCornerShape(16.dp)` 出现在 `AboutScreen`，是官方 `AboutPage` 的品牌容器/纹理模糊形状
（`AboutPage.kt:242-254`、`:274-290` 的逐字节移植），保留。

复查后仍然"看起来像自造、实际是官方契约"的部分（前几轮已定案，本轮不变）：

- `ui/PageInsets.kt`、`PageFrame.pageContentPadding/pageScrollModifiers`：官方 `example/utils/PageUtils.kt` 的逐字节移植，库不导出。
- `PageFrame.AnimatedTopBarActions`：`AnimatedContent + SizeTransform + GlassMotion.*` 的组合，容器与曲线都是官方的。
- `HomeGridMetrics` / `PageMetrics` 的尺寸：官方值（80dp 单元、12dp 外边距/间距、28dp 顶栏图标、40dp 行图标）与
  业务值（56dp 网格图标、68dp 紧凑单元、0.22 图标圆角）分开登记在 R8 审计 §4，业务值没有官方对应物。
- `UiFeedback.toast`：系统 Toast，不是 UI 轮子。MIUIX 有 `Snackbar`/`SnackbarHost`，若要统一观感可后续迁移（未做）。

---

## 5. 变更清单

| 文件 | 改动 |
|---|---|
| `feature/common/AppComponents.kt` | `AppIconView` 删除 `selected` 形参与 2dp 描边（连带 `animateDpAsState` / `GlassMotion` / `squircleBorder` 依赖）；`AppRow` 的 `selected: Boolean` → `selectionState: ToggleableState?`，行尾渲染官方 `Checkbox` |
| `feature/common/AppSearchResults.kt` | `AppListCardRow` 传 `selectionState`；`AppSearchResults` 新增 `selectionMode` 参数（默认 false，主页搜索不受影响） |
| `feature/apps/AppsScreen.kt` | 列表与搜索浮层按 `inSelection` 传 `ToggleableState(...)` / `selectionMode` |
| `feature/home/HomeScreen.kt` | `HomeAppItem` 新增 `selectionMode`，选中时叠官方 `Checkbox(On)` 角标；删除描边调用 |

验证：`assembleDebug` + `testDebugUnitTest` 通过；模拟器实拍（玻璃开/关两档）
列表多选、网格多选、两档按压反馈均符合预期，无 FATAL。
