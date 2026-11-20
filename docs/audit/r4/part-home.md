# R4 整改 · Home 专项（P0-1 正方形叠层 / P0-2 标签黑色选中底 / P1 §9 图标-文字一致性）

- 日期：2026-11-19
- 负责范围：`Glacier/app/src/main/kotlin/com/ayang/glacier/feature/home/HomeScreen.kt`、
  `.../feature/common/AppComponents.kt`、新建 `res/values{,-zh-rCN}/strings_home_r4.xml`
- 官方依据：本地 MIUIX 源码（`miuix/`，0.9.5 快照）与 `miuix/example/shared`；
  成熟实现参考 `upstream/Mishka`（同作者生产应用）。全部行号为本地文件行号。
- 本轮**未**运行 Gradle / 未安装 / 未截图；所有结论来自源码推导 + lead 提供的 R3 release 实拍
  （`docs/verification/miuix-os4-r4/before/`）。待验证项集中在本文件最后一节。

---

## P0-1 主页长按/按压出现"正方形叠层"

### 现象

按下 Home 网格里任一应用（整格：图标 + 应用名）时，整格被一块**方角灰底**覆盖；
松手后该方底按 spring 淡出（`before/01-longpress-square-overlay-fading.png`）。
叠层覆盖范围 = 整格宽度、从图标顶部一直到标签行底部。

### 根因（逐层核验，只有一条绘制源）

1. **叠层是 indication 画的，不是自定义背景/选择态**：
   - `HomeAppItem` 的 `Column`（`fillMaxWidth()`，含图标 + 标签）挂 `combinedClickable`
     （整改前 `HomeScreen.kt:485-490`），没有传 `indication`，因此用 `LocalIndication.current`。
   - MIUIX 主题把 `LocalIndication` 设成 `MiuixIndication`：
     `miuix/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/theme/MiuixTheme.kt:33`、`:37`、`:63`、`:67`
     （`miuixIndication = MiuixIndication(color = miuixColors.onBackground)`）。
   - `MiuixIndication` 的绘制是**整块 `drawRect`、无圆角**：
     `miuix/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/utils/MiuixIndication.kt:129-135`
     （`drawRect(color = color, alpha = alpha, size = size)`）；
     KDoc 第 36 行原文 "draws a rectangular overlay"；按压 alpha 0.10、HoldDown alpha 0.10（`:27-28`）。
   - 热区是整格（宽度 = 格宽、高度 = 图标+6dp+标签），所以方块自然覆盖到标签行。
     （`combinedClickable` 不会发 `HoldDownInteraction`，本项只有 0.10 的 Press 叠层，没有 0.20 双层叠加。）
2. **没有第二来源**：`AppIconView` 选中态只画 `squircleBorder` 描边（无底色）；没有长按覆盖层、
   没有 `background(...)`、没有 ripple；`LazyVerticalGrid` / item 外没有额外 clip。
   所以"方底"完全来自 `MiuixIndication.drawRect` 没有被任何 clip 约束。
3. **对比参照**：官方图标网格的单元是**先裁 squircle 再 clickable**：
   `miuix/example/shared/src/commonMain/kotlin/IconPage.kt:403-411`
   （`.squircleClip(12.dp).clickable{...}.padding(horizontal=4.dp, vertical=10.dp)`，裁切在 `clickable` 之前）；
   MIUIX 自己的 `TabRow` 同款顺序：`basic/TabRow.kt:410-418`
   （注释 "Clip only when an indication is supplied"，`squircleClip(cornerRadius)` 在带 indication 的
   `selectable` 之前）。官方组件里的行/卡片类按压反馈另走 `pressFeedbackType`（Sink 缩放）且默认
   `showIndication = false`（`basic/Card.kt:92-93`、`:119`），不会画矩形。

### 整改（`HomeScreen.kt:509-540`）

```kotlin
Column(
    modifier = Modifier
        .fillMaxWidth()
        .squircleClip(HomeGridMetrics.CellCorner)   // 先裁，再 clickable
        .combinedClickable(onClick = onClick, onLongClick = onLongClick),
)
```

- `HomeGridMetrics.CellCorner = PageMetrics.GridAppIcon(56dp) * AppIconCornerRatio(0.22) = 12.32dp`
  （`HomeScreen.kt:119-126`）：叠层轮廓与图标**同一条 squircle 曲线**，不引入新圆角数值
  （官方单元用 12dp，`IconPage.kt:406`）。
- **为什么不把叠层缩到图标上 / 为什么不 `indication = null`**：
  - 整格（图标+标签）是官方网格单元的交互范围（`IconPage.kt:403-411` 对含标签的
    `Column` 整格 clickable + 裁切），标签区域仍应可点；
  - `indication = null` 会丢掉 MIUIX 唯一的按下反馈（0.10 叠层），与列表页 `Card` 的按压反馈不一致；
  - `clip`/`squircleClip` 只裁绘制、不裁命中测试，热区不变，也不会引入缩放/位移。
- 没有改成自绘缩放：`PressFeedback`（Sink/Tilt）只在 `Card`/`Pressable` 里对"卡片"使用，
  启动器网格套缩放会在多选进入/退出时抖动，收益低于风险。

### 修改前后

| 项 | 修改前 | 修改后 |
|---|---|---|
| 叠层形状 | 整块 `drawRect`，方角，覆盖整格含标签 | 裁到 12.32dp squircle，与图标轮廓同曲率 |
| 命中测试 | 整格 | 不变（clip 不裁命中） |
| 图标尺寸/位置 | 不变 | 不变（无缩放、无 padding） |
| 选中态 | `squircleBorder` 2dp + `padding(2.dp)` → 图标内容 56→52dp 跳动 | 描边叠加、不占布局，56dp 恒定（见 P1） |

### 仍需构建后验证

- 按住不放的中间帧（`01` 那张的时间点）应只有圆角灰底、四角不露方角；
- 快速连点/长按进入多选：无闪烁、无 4dp 跳动（P1 的描边改动同时影响这里）。

---

## P0-2 主页标签默认选中出现"黑色底"

### 现象与定位

实拍 `before/03-tab-selected-black-light.png`（浅色）：选中标签是 `#383838` 近黑胶囊 + 白字，
未选中是白色胶囊 + 黑字，右侧还有胶囊投影。

**先纠正一个容易误判的前提**：该截图不是普通 `TabRow`，而是 **Glass 开启档的 `GlassTabRow`**。

- `isRuntimeShaderSupported()` 在 Android 上等于 `Build.VERSION.SDK_INT >= TIRAMISU`
  （`miuix/miuix-material/src/androidMain/kotlin/top/yukonga/miuix/kmp/material/RuntimeShader.android.kt:18`），
  模拟器 API 36 → true；
- `GlacierShell.kt:98` `val glassActive = preferences.glassEnabled && isRuntimeShaderSupported()`，
  Glass 默认开（`data/GlacierPreferences.kt:55`）→ `LocalGlassActive = true`（`GlacierShell.kt:185`）；
- 于是 `HomeScreen.kt` 的 `TagRow` 走进玻璃分支 `GlassTabRow(colors = GlassTabRowDefaults.neutralColors())`
  （整改前 `HomeScreen.kt:615-624`）。
- `neutralColors()` 的选中填充就是近黑：浅色 `Color(0xFF383838)`、深色 `Color(0xFFE6E6E6)`，
  选中文字分别是白 / 黑（`miuix/miuix-glass/src/commonMain/kotlin/top/yukonga/miuix/kmp/glass/GlassTabRow.kt:236-250`）；
  未选中静止填充是"黑 6% 叠 surface"（`:225-235`），文字 `onSurface`（黑），
  与截图"白胶囊 + 黑字"一致。

### 官方行为结论：颜色值是官方 neutral 语义，但**档位用错**

1. `neutralColors()` 是官方 API，黑色不是 Glacier 硬编码；但它的官方定位是
   "页面内容已经使用强调色时"的**单色档**，并且明确绑定较矮高度：
   - KDoc：`GlassTabRow.kt:210-217`（"The neutral set: the selected tab is a strong neutral
     rather than the theme's colour, for a page whose content already owns the accent."）、
     `:268-270`（"two sets ... not interchangeable"）；
   - `NeutralHeight = 35.dp`，注释 "The shorter height that [neutralColors] goes with"
     （`GlassTabRow.kt:119-120`）；Glacier 用的是默认 `Height = 40.dp`（`GlassTabRow.kt:117`），
     配对的 35dp 被丢掉了；
   - 官方文档同样把 neutral 与 35dp 成对给出：
     `miuix/docs/zh_CN/components/glass/glasstabrow.md:44-58`、`:143`、`:155-156`。
2. 官方的**页面级标签**范式不是 neutral：
   - 图标网格页（与 Home 同构：顶栏 `bottomContent` + 网格）玻璃档用
     `GlassSegmentedTabRow`：`miuix/example/shared/src/commonMain/kotlin/IconPage.kt:274-285`
     → 相连轨道 + 滑动指示器，选中是 6%（浅）/13.3%（深）半透明指示器
     （`GlassTabRow.kt:516-548`）；
   - 同页非玻璃档用普通 `TabRow`：`IconPage.kt:308-315`；
   - 同作者生产应用 Mishka 的页面标签也只有 `GlassSegmentedTabRow`（无 `GlassTabRow`）：
     `upstream/Mishka/app/src/main/kotlin/top/yukonga/mishka/ui/screen/provider/ProviderScreen.kt:133-146`，
     且外层同样是 `padding(horizontal=12).padding(top=6,bottom=12)`、`surfaceAlpha` 按内容滚动给。
   - 官方唯一用 `GlassTabRow(neutralColors(), height = NeutralHeight)` 的地方是**组件演示页**
     （`GlassPage.kt:237-249`，同页 :226-236 是 primary 档、:250-260 是 segmented 档），
     属于"两个配色档并列展示"，不是页面范式。
3. 结论：黑色不是官方推荐的页面标签效果；Glacier 用错档位（把组件演示页的单色档 + 40dp
   当成页面默认），并且和 Glass 关档的轻指示器完全割裂。

### 整改（`HomeScreen.kt:612-670`）

- **Glass 开**：`GlassSegmentedTabRow`（官方 `IconPage.kt:274-285` 同款）：
  - `surfaceAlpha` 由网格是否滚入顶栏决定（`HomeScreen.kt:189` 新增 `rememberLazyGridState()`、
    `:272-280` 传入 `TagRow`、`:444-452` 传给网格），语义对齐官方
    `IconPage.kt:283` 的 `if (blurActive && contentUnderTopBar) 1f else 0f`；
  - 不再传 `colors`，用组件默认（`GlassTabRow.kt:597-598`：选中文字 `onSurface`（非动态取色时）、
    轨道/指示器全部来自 `GlassSegmentedTabRowDefaults`）。
- **Glass 关**：官方 `TabRow`（`IconPage.kt:308-315` 非玻璃分支），仍不传 `colors`
  （`TabRowDefaults.tabRowColors()`：`basic/TabRow.kt:596-609`）。
- 兜底：Glass 开但 `backdrop == null`、或标签数 > 4（分段轨道要求有界宽度 + 互不相同标签，
  `glasstabrow.md:72`，标签多时每格放不下）时退回 `TabRow`，避免挤压/崩溃。

### 四个状态的官方取值（源码依据）与结论

| 状态 | 组件 | 选中指示器 | 未选中 | 文字 | 结论 |
|---|---|---|---|---|---|
| Glass 开 · 浅色 | `GlassSegmentedTabRow` | 黑 6%（`GlassTabRow.kt:533-535`、`:546`） | 轨道 `White`（`:527`）+ 白 13.3% 指示器/无 | `onSurface` 黑（`:597-598`） | 无黑块 |
| Glass 开 · 深色 | 同上 | 白 13.3%（`:534`） | 轨道 = 白 13.3% 叠 surface（`:521-523`） | `onSurface` #F2F2F2（`Colors.kt:486`） | 无白块（低对比但为官方值） |
| Glass 关 · 浅色 | `TabRow` | `surfaceContainer` = `#FFFFFF` 白胶囊（`TabRow.kt:600`、`Colors.kt:382`） | 行底色 `surface` `#F7F7F7`（`TabRow.kt:598`、`Colors.kt:375`）+ 1dp `outline` 描边 | 选中 `onBackground` 黑 / 未选中 `onSurfaceVariantSummary` 60% 黑（`Colors.kt:373/379`） | 无黑块 |
| Glass 关 · 深色 | `TabRow` | `surfaceContainer` = `#242424`（`Colors.kt:492`） | 行底色 `surface` = 黑（`Colors.kt:485`）+ `outline` #404040 | 选中 `onBackground` 90% 白（`Colors.kt:483`） | 无白块/黑块，对比度偏低但为官方值 |

说明：整改前 Glass 关档（`TabRow`）**不会**出现黑块——浅色选中是白胶囊。
所以实拍里的黑胶囊只可能来自 Glass 开档的 neutral 档；两档整改后都不再有强对比实心胶囊。

### 备选方案（若 lead 更希望保留"强调色选中"）

`GlassTabRow(colors = GlassTabRowDefaults.primaryColors())` 也是官方档（`GlassTabRow.kt:154-208`，
选中 = 主题 primary + onPrimary），官方文档 `glasstabrow.md:191-195` 还给了 `.copy(...)` 的定制写法。
但它会让 Glass 开 = 蓝色胶囊、Glass 关 = 白色胶囊，两档落差比 segmented 更大，故未采用。

### 仍需构建后验证

- 四种组合（Glass 开/关 × 浅/深）各一张截图，确认无黑/白实心胶囊、指示器与文字可读；
- 静止（未滚动）时 Glass 开档 `surfaceAlpha = 0`：轨道是官方静止底色（浅色为白、深色为 13.3% 白），
  在 `#F7F7F7` 页面上对比度较弱——需截图确认"看得出是标签行"；若过淡，把
  `surfaceAlpha` 固定为 1f（组件默认，`GlassTabRow.kt:298`）即可，改动一行。
- 标签数 > 4 时退回 `TabRow` 的观感。

---

## P1 §9 应用图标与应用名称一致性（Home 网格 / Apps 列表 / 搜索结果）

### 已确认的一致项（源码级）

| 维度 | 结论 |
|---|---|
| 图标轮廓 | 三处都走 `AppIconView`（同一 `squircleClip` + `AppIconCornerRatio = 0.22`，`AppComponents.kt:32-33`、`:73-102`），只有尺寸差异（56/40dp，允许） |
| 图标占比/取图 | 同一 `rememberAppIcon` 管线（去色、图标包、自适应合成），`tint = Color.Unspecified` 保持原色 |
| 行结构 | 搜索结果整改前是手搓 `Card + BasicComponent`，现改为共享 `AppRow`（`HomeScreen.kt:591-605`），与 Apps 列表、Apps 搜索结果（`AppSearchResults.kt:103`）同一行实现 |
| 圆角/描边 | 选中态统一为 squircle 描边（网格与列表同一 API），无矩形描边 |
| 截断 | 网格标签 `maxLines = 1` + `Ellipsis`；列表行沿用官方 `BasicComponent` 单行语义 |

### 本轮修的两处真实不一致

1. **选中时图标缩小 4dp / 网格跳动**（`AppComponents.kt:35-103`）：
   旧实现把 `squircleBorder(...) + padding(2.dp)` 放在 `size` 内层，选中时图标内容 56→52dp，
   且描边半径从 12.32 变 14.32，与裁切轮廓不平行（`docs/audit/miuix-os4-audit.md:177` 记为未整改）。
   新结构：固定尺寸 `Box` + 图标 `fillMaxSize()` + 独立描边层 `matchParentSize()`；
   描边用官方延迟读取版 `squircleBorder(width={ringWidth}, color={ringColor}, cornerRadius=radius)`
   （`miuix-squircle/.../SquircleBorder.kt:79-111`），宽度 0→2dp 由 `animateDpAsState` 过渡。
   结果：图标尺寸恒定、描边外缘与图标轮廓重合、选中态平滑出现。
2. **标签字重/字号分叉**（`AppComponents.kt:105-117`、`HomeScreen.kt:526-536`）：
   列表行由官方 `BasicComponent` 排版成 `headline1(17sp) + Medium`（`basic/Component.kt:89-95`），
   网格标签是 `footnote1(13sp)` 且默认字重。新增 `AppLabelTextStyle(fontSize)` 作为唯一入口，
   网格标签用 `footnote1 + Medium`，只保留字号层级差异（可被"主页图标字号"设置覆盖）。
   若后续认为启动器标签应保持 Regular，只需改这一处。

### 附带修（apps-micro 交接项）

`SheetActionRow` 禁用态图标未弱化：`BasicComponent` 只把 title 换成 disabledColor
（`Component.kt:89-103`），`startAction` 是自己传的子树。已改为
`tint = if (enabled) color else MiuixTheme.colorScheme.disabledOnSecondaryVariant`
（`AppComponents.kt:132-154`），与官方 `BasicComponentDefaults.titleColor` 的 disabledColor 同 token
（`Component.kt:298-306`）。

`AppRow` 新增向后兼容参数 `selected: Boolean = false`（`AppComponents.kt:156-198`），
转发给 `AppIconView(selected=)`，供 Apps 列表将来接多选时复用 Home 的选中语言；现有调用点零改动。

---

## 文案接线（lead 追加项）

`HomeScreen.kt:428-432` 延时冻结成功提示原来复用菜单项标题 `R.string.action_deferred_freeze`
（读起来是"延时冻结"）。改为新键 `home_deferred_freeze_scheduled`（带分钟数），
新增文件 `res/values/strings_home_r4.xml`（`"Deferred freeze scheduled to run in %1$d minutes"`）与
`res/values-zh-rCN/strings_home_r4.xml`（`"已安排 %1$d 分钟后冻结"`），未改任何既有 `strings*.xml`。

---

## 仍未解决 / 风险 / 待构建验证

| 项 | 说明 |
|---|---|
| 4 状态截图 | Glass 开/关 × 浅/深 各一张；断言：标签行无实心黑/白胶囊、无方角叠层。需 ≥2 个标签才会渲染标签行（`HomeScreen.kt:258` 条件）。 |
| 按压中间帧 | 按住网格单元截图，确认叠层四角为圆角、不覆盖到相邻格；快速连点 10 次无闪烁。 |
| 选中态 | 长按进入多选：蓝色 squircle 描边不改变图标尺寸（对照 `before/02-selection-mode-r3.png`）。 |
| Glass 开静止态对比度 | segmented 轨道在 `surfaceAlpha=0` 时接近页面底色（官方值），若观感过淡需固定 `surfaceAlpha = 1f`（一行）。 |
| 标签数 > 4 | 已退回 `TabRow`；观感需截图（官方 segmented 也不适合过多标签）。 |
| 同名标签 | `GlassTabRow/GlassSegmentedTabRow` 内部用 `key(label)`，同名标签重复 key 是官方组件既有约束（整改前 `GlassTabRow` 同样如此），本轮未处理；`TabRow` 分支不受影响。 |
| 大字体 2.0x | 网格标签走 sp + 单行省略，理论可行；未实拍。 |
| Apps 搜索行字体 | `AppSearchResults.kt` 同行实现已统一为 `AppRow`，但其页面骨架/间距由 apps-micro 负责，本轮未复核。 |
| 真机 | 只推导了模拟器（API36）路径；`squircleClip` 在 API<33 会退回 `RoundedCornerShape`，裁剪仍生效（`miuix-squircle/.../SquircleBackground.kt:118-121`），未实测。 |
