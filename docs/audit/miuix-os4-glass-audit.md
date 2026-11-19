# Glacier 第二轮：MIUIX OS4 Glass 架构审计

- 日期：2026-11-19
- 基线：MIUIX `58b0a3d`（`miuix-glass` / `miuix-blur` / `miuix-material` 源码）
- 证据等级：L1 = 源码接线正确；L2 = 运行时组件挂载、绘制、交互正常；L3 = 真实 GPU 下的光学观感
- 本轮结论：**L1、L2 通过；L3 未验证**（模拟器 SwiftShader）

---

## 一、录制层结构（R3 整改后）

```
Scaffold(Transparent, contentWindowInsets = 0)                  ← Shell：弹层宿主（MiuixPopupHost）
├ bottomBar（窄屏）→ GlassNavigationBar(R)                      ★R 的兄弟：胶囊在 Scaffold 槽位里
│   24dp 横向 + 20dp 上间距 + max(24dp, safeBottom+8dp) 底安全区
└ Box(fillMaxSize)
   ├ Box(fillMaxSize).layerBackdrop(R)                          ★R：满屏录制层，先 drawRect(surface)
   │  └ NavDisplay
   │     └ railStartInset { … }                                 ← 让位只作用于内容
   │        └ Route.Main → HorizontalPager
   │           └ PageFrame
   │              Box                                             ← 浮层宿主（在 Scaffold 之后）
   │              ├ Scaffold(containerColor = surface, insets = 0)
   │              │  ├ topBar   → GlassTopAppBar(P)               ← 与 P 同级
   │              │  │            └ bottomContent → GlassTabRow  ← 由顶栏上下文给出 backdrop
   │              │  ├ bottomBar → Spacer(LocalShellBottomBarHeight)   ← 透明占位，让正文/FAB 正确让位
   │              │  ├ fab      → GlassFloatingActionButton(P)
   │              │  └ body     → Box(fillMaxSize).layerBackdrop(P)  ★P：页面录制层
   │              │                └ 列表 / 网格
   │              └ overlay()                                    ← 搜索浮层、弹层，画在 Scaffold 之上
   └ GlassNavigationRail(R)（宽屏）                              ← R 的兄弟
```

关键点：

1. **R 在分页容器之外**，坐标空间与最外层 Scaffold 一致；底栏、侧栏、弹层都采 R。
2. **底栏进入 Scaffold.bottomBar 只是布局变化**：它仍然是 R 的兄弟，不采样自身，L1 结论不变；换来的是正文/FAB 让位由 Scaffold 按实测底栏高度计算，不再手写魔法值。
3. **P 只录页面正文**，顶栏、FAB 是 P 的兄弟（同一个 Scaffold 的不同槽），因此可以安全地采 P。
4. **搜索浮层在 Scaffold 之后组合**，是顶栏的兄弟而非子节点，同时压在顶栏之上，点击不会穿透；搜索浮层本身常驻组合，只由 `expanded` 驱动进出动画。
5. 搜索浮层盖不住 Shell 的悬浮底栏（底栏是 Shell 的兄弟、画在页面之上），因此搜索结果列表底部额外让出 `LocalShellBottomBarHeight`，避免最后几条压在胶囊下方。

---

## 二、每个消费者的落点

| 消费者 | 位置 | 采样源 | 结论 |
|---|---|---|---|
| `GlassTopAppBar` | 页面 `Scaffold.topBar` | P（`LocalPageBackdrop`） | 兄弟，安全 |
| `GlassTabRow` | 顶栏 `bottomContent` | 顶栏上下文（`LocalGlassTopAppBarContext.backdrop`） | 官方推荐位置，安全 |
| `GlassIconButton`（顶栏动作） | 顶栏子树 | 继承顶栏材质 | 安全 |
| `GlassFloatingActionButton` | 页面 `Scaffold.floatingActionButton` | P | 兄弟，安全 |
| `GlassNavigationBar` | Shell `Scaffold.bottomBar`（窄屏） | R | R 的兄弟（布局在槽位、绘制在 R 之后），安全 |
| `GlassNavigationRail` | Shell `Box` 覆盖层（宽屏） | R | 兄弟，安全 |
| `GlassDialog` / `GlassBottomSheet` | 页面 `overlay` 槽 | R（`renderInRootScaffold = true`，由根 Scaffold 的 popup host 绘制） | 在 R 之外，安全 |
| `GlassDropdownPreference` 的弹层 | 设置行内触发 | R | 由根 Scaffold 绘制，安全 |
| `GlassSearchOverlay` | 页面 `overlay` 槽 | **自建内部录制层**（`rememberLayerBackdrop`，只录搜索结果） | 自包含，不采外部层 |
| `GlassSearchOverlay` 的搜索条 | 浮层内部 | 同一内部录制层，但消费者在录制层子树之外（`BasicTextField.decorationBox` 由外层 Box 绘制） | 官方组件内部已处理，安全 |

`GlassSearchOverlay` 自带的录制层只覆盖搜索结果列表，搜索条与取消按钮采样它 —— 这正是官方 example 的用法，本轮没有改动其内部结构，只改了它在外层树里的位置。

---

## 三、本轮解决的 Glass / 层级问题

### 1. 搜索浮层被顶栏盖住（真实交互 BUG）

**现象**：主页点搜索 → 点搜索条右侧 × → 搜索不关闭，弹出「编辑标签」。

**证据链**：

1. 官方 `Scaffold.kt` 的 `layout { }` 依次 `place`：`bodyContentPlaceable → topBarPlaceable → snackbar → bottomBar → floatingToolbar → fab → popup`。
2. 旧实现在 `Scaffold` 的 body 槽里调用 `overlay()`，所以浮层先于顶栏绘制，且命中测试顺序同样靠后。
3. 旧实现用 `topBarAlpha = 0f` 隐藏顶栏。`graphicsLayer { alpha = 0f }` 只影响绘制，**不影响命中测试**。
4. 搜索条的取消按钮是 `IconButton(size = BarHeight, onClick = onDismissRequest)`，位于搜索条最右侧；主页顶栏第 3 个动作是「编辑标签」，位于顶栏最右侧。两者在同一屏幕坐标。
5. 结果：点击命中的是顶栏的「编辑标签」。

**修复**：`PageFrame` 改为 `Box { Scaffold(...) ; overlay() }`，浮层成为 Scaffold 的兄弟并画在其后；同时顶栏淡出改用官方 `rememberGlassSearchBarAlpha` + `glassSearchBarTransition`，并在搜索态 `clearAndSetSemantics { }`，不再依赖 alpha=0 来"假装"顶栏不存在。

**验证（R3 重跑）**：`docs/verification/miuix-os4-r3/` 的 `07-search-open` → `08-search-results`（输入 glac 出结果，条内 `Clear` ×，条外 `Cancel` ×）→ `09-search-closed`：搜索关闭并播放退出动画，主页恢复，**标签编辑未打开**。另外把 `if (searchOpen)` 条件组合改成常驻组合（官方要求），确保关闭动画、query 清空、键盘收起、返回键都由 `GlassSearchOverlay` 内部处理（`GlassSearchOverlay.kt:229-245`）。

### 2. 标签页签的采样层级

旧实现把 `TabRow` 放在 `LazyVerticalGrid` 的 item 里：它是 P 的后代，若再采 P 就是自采样（历史崩溃点）。本轮按官方 example 的做法，把它移到顶栏的 `bottomContent`：

- 位置：`GlassTopAppBar(bottomContent = { GlassTabRow(...) })`。
- 采样：`GlassTabRow` 读 `LocalGlassTopAppBarContext.current.backdrop`，即顶栏自己的 backdrop（P 的兄弟），并继承顶栏的 `underlayMaterial`，视觉上与被折叠的顶栏是同一块玻璃。
- 非玻璃档：`glassActive = false` 时换回官方 `TabRow`，不强行玻璃化。

### 3. 侧栏让位与 morph 进度

旧实现用 `Boolean`（是否展开）算让位量，侧栏折叠时内容不会回缩；且 `rememberSaveable` 让状态在旋转后与布局方向不一致。

现在（R3 与官方 example 对齐）：

```kotlin
val wide = isWideLayout()                 // >=840dp 或 (>=600dp 且高/宽<1.2)
val expandedRail = isExtraWideLayout()    // >=1200dp 展开面板，否则折叠胶囊
val railState = rememberGlassNavigationRailState(initialExpanded = expandedRail)
LaunchedEffect(expandedRail) { railState.expanded = expandedRail }
val railInset: () -> Dp = {
    if (wide) systemStartInset + (Margin + ExpandedWidth) * railState.progress.coerceIn(0f, 1f) else 0.dp
}
// NavDisplay(modifier = Modifier.railStartInset(railInset))
// 顶栏标题：LocalTopBarTitleAlpha = { if (wide) progress else 1f }（折叠胶囊盖住标题时淡出）
// 搜索浮层：LocalSearchTopInset = { (CollapsedTop + CollapsedHeight + 4.dp) * (1 - progress) }
```

`railStartInset` 在 `layout { }` 里读 `inset()`，所以 morph 的每一帧只触发重新测量，不重组整页 —— 符合 Glass 契约第三条红线。`if (wide)` 保证窄屏不会带着上一状态的进度把内容挤到右边。

另外两点：

- 让位量必须加上系统的起始 inset（`systemBars ∪ captionBar ∪ displayCutout`）。侧栏自己带
  `defaultWindowInsetsPadding = true`，横屏刘海会把面板整体右移，只算 `Margin + ExpandedWidth`
  会让内容被面板压住一列字（实测现象见本轮记录）。
- 折叠态遵循官方行为：840–1200dp 时侧栏折叠成**顶部居中胶囊**并由胶囊自带的 toggle 展开；标题按
  `progress` 淡出、搜索浮层按 `LocalSearchTopInset` 让位，因此不会出现"胶囊盖标题/搜索条压胶囊"。
  横屏模拟器（914dp）实测见 `docs/verification/miuix-os4-r3/23-landscape-collapsed.png` 与
  `24-landscape-rail-expanded.png`。

### 4. 弹层的内边距叠加

`GlassBottomSheet` 自带 `insideMargin`（`BottomSheetDefaults.insideMargin`）。旧实现又在内容里加了 `padding(horizontal = 24.dp)`，实际左右留白约 40dp，与设置页 16dp 的行内边距完全不是一套。本轮删除外层 padding，行统一用 `BasicComponent`（16dp 行内边距）。

---

## 四、哪些组件用 Glass，哪些用普通 MIUIX

| 组件 | 选择 | 依据 |
|---|---|---|
| 顶栏 | Glass（`GlassTopAppBar`） | OS4 的顶栏就是玻璃表面；官方 example 同款 |
| 标签页签 | Glass（`GlassTabRow`，仅玻璃档） | 官方 example 把它放在顶栏 `bottomContent` 并用玻璃；无玻璃时用 `TabRow` |
| 底部导航 / 侧栏 | Glass | OS4 导航组件本身是玻璃 |
| FAB | Glass（`GlassFloatingActionButton`） | 官方同名组件 |
| 弹层（Dialog / BottomSheet / Dropdown） | Glass | 官方同名组件；`backdrop = null` 时库内自动走不透明回退 |
| 搜索浮层 | Glass | `GlassSearchOverlay`；`blurEnabled = glassActive` |
| 设置行 / 列表行 | 普通 MIUIX（`BasicComponent` / `*Preference`） | 设置页是信息密度单位，整行玻璃会让文字对比度不稳；官方 example 的设置页也是普通 Card + Preference |
| 应用网格单元 | 普通（无卡片底色） | 启动器式布局，图标本身就是视觉主体；不套玻璃 |
| 卡片 | 普通 `Card`（`surfaceContainer`） | 官方 `Card` 默认即 OS4 的卡片表面 |

结论：没有为了"看起来像玻璃"而给内容控件加玻璃。

---

## 五、证据等级

> R3 验收（本轮重新构建 + 模拟器）的完整记录见 `docs/verification/miuix-os4-r3/README.md`。

| 项 | 等级 | 证据 |
|---|---|---|
| 所有 Glass 组件的接线、backdrop 传递、兄弟关系 | **L1** | 源码 + 本文第二节的落点表 |
| 组件实际挂载、绘制、交互（点击、折叠、滚动、开关玻璃开关） | **L2** | 模拟器截图 `docs/verification/miuix-os4-r3/`：`01`–`19`、`23`–`27`；搜索开/关、标签弹层、操作单、侧栏折叠/展开、横竖屏往返、大字体均实测 |
| 真实折射、模糊半径、边缘光、深色下的玻璃亮度 | **L3 未验证** | 模拟器 `ro.hardware.egl = emulation`（软件/模拟渲染）；`isRuntimeShaderSupported()` 返回 true 不代表像素可信 |

模拟器上顶栏的 `progressiveBlur` 被显式关闭（`!Platform.isEmulator`），走库的 band 渐变路径；这一项必须在真机上复验。

### R3 与 Glass 相关的结构变化

1. **底栏从 Shell 最外层 Box 移入 Shell `Scaffold.bottomBar`**：仍是 R 的兄弟（不采样自身），但让位改由 Scaffold 计算（`LocalShellBottomBarHeight`），并补上官方 24dp 横向内边距。
2. **页面 `Scaffold.bottomBar` 增加透明 `Spacer(LocalShellBottomBarHeight)`**：让正文底部 padding 与 FAB 位置都由 Scaffold 语义决定，删除 `reserve - 20.dp` 魔法值。
3. **搜索浮层常驻组合**：只由 `expanded` 驱动，退出动画/query 清空/键盘/返回键由官方组件处理；结果列表底部让出悬浮底栏高度。
4. **宽屏侧栏改为官方自适应**：≥1200dp 常驻展开，840–1200dp 折叠成顶部胶囊；标题与搜索浮层分别用 `LocalTopBarTitleAlpha` / `LocalSearchTopInset` 让开。
5. **弹层全部常驻 + `show`**：Dialog/BottomSheet 的退出动画不再被 `if` 砍掉；`backdrop` 继续取根录制层 R，弹层由根 Scaffold 的 popup host 绘制，天然在 R 之外。
6. **L3 仍然未验证**：本轮没有真实 GPU 环境，任何"折射/边缘光观感"的结论都不成立。
