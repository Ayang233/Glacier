# Glacier 第二轮：MIUIX OS4 / OS4 Glass 全量审计（只审计，不含整改）

- 审计日期：2026-11-19
- 审计对象：`Hail/Glacier`（`com.ayang.glacier`），`app/src/main/kotlin/com/ayang/glacier/**` + `app/src/main/res/**` + `AndroidManifest.xml` + Gradle 配置
- 对照基线：`Hail/miuix`，HEAD `58b0a3d`（`library, example: animate glass tab row material transitions`），`LIBRARY_VERSION = 0.9.5`（`miuix/build-logic/src/main/kotlin/BuildConfig.kt:5`）；composite build（`Glacier/settings.gradle.kts:28-41`）
- 证据来源：
  1. 逐行阅读 Glacier 全部 UI 源码（未运行 Gradle、未修改任何源码）；
  2. 逐行阅读 MIUIX `miuix-ui` / `miuix-glass` / `miuix-preference` / `miuix-nav` / `miuix-squircle` 实际源码与 `example/shared` 官方示例；
  3. 对 `docs/verification/miuix-os4-r2/*.png`（1080×2400 / 2400×1080，density 2.625）做像素测量交叉验证。
- 不采信上一轮文档结论：`docs/audit/miuix-os4-audit.md`（旧版）、`docs/audit/miuix-os4-glass-audit.md`、`docs/architecture/glass-contract.md` 的具体判断都在本文重新对照源码复核，结论与旧文档不一致处在对应条目里写明。

状态定义：**未整改** = 本轮目标尚未达标；**已整改（待复验）** = 上一轮已改、源码上成立但仍有残留问题；**已验证（L2）** = 源码 + 本轮截图一致，只能证明挂载/绘制/交互，不是 L3。

---

## 一、结论摘要（先看这一节）

用户提出的三点问题全部能在源码中定位到确切原因，且**都不是"风格问题"，而是结构/数值错误**：

1. **"一级页面底栏的宽距与 MIUIX OS4 完全不同"** —— 成立。
   `GlacierShell.kt:224-231` 把 `GlassNavigationBar` 直接放进 `Box(align = BottomCenter)`，**没有**官方 example 的 `.fillMaxWidth().padding(horizontal = 24.dp)`（`miuix/example/shared/src/commonMain/kotlin/AppContent.kt:772-774`）。底栏被拉满整屏：411dp 屏上官方胶囊宽 363dp、Glacier 宽 411dp；每个目的地的可点区域官方 115.7dp、Glacier 131.7dp（差 16dp/项）。像素实测（`01-home-light.png`）三个图标中心 x = **73.9 / 205.1 / 337.3dp**，官方公式应为 **89.9 / 205.7 / 321.5dp**：首项左移 16dp、末项右移 16dp。

2. **"排版、布局、尺寸、间距、动画完善程度都有很大问题"** —— 成立，且集中在三处系统性错误：
   - **手动让位**：`PageFrame.kt:266-280` 的 `pageContentPadding` 把 `Scaffold` 已给的下边距、`LocalNavReserve`（`GlacierShell.kt:147-148` = 54+navInset+32）和写死的 `+24.dp` **相加**；官方是 `Scaffold` 用 `bottomBar` 自动让位、页面只做 `maxOf` 合并（`AppContent.kt:648-683`、`utils/PageUtils.kt:185-222`）。竖屏手势导航下页面底部内边距 **158dp vs 官方 90dp（多 68dp）**，三键导航下 **206dp vs 102dp（多 104dp）**。
   - **FAB**：`PageFrame.kt:139-146` 的 `padding(bottom = reserve - 20.dp)` 让 FAB 底边距屏幕底 **126dp**（实测 127dp），官方 compact 布局是 `bottomBar 高度(90) + FabSpacing(12) = 102dp`（`miuix-ui/.../basic/Scaffold.kt:261-267`、`465`）—— FAB 高了 24dp，正好是导航栏 inset 被重复计入一次。
   - **动画缺失**：所有 `GlassDialog` / `GlassBottomSheet` 都是"条件组合 + `show = true`"（例 `HomeScreen.kt:254-263/293-319/321-332`），关闭时组件直接从组合树移除，官方组件的 300~400ms 退出动画根本没有机会播放；官方 example 是常驻组合、`show` 跟随状态（`GlassPage.kt:405-437`、`684-694`）。搜索浮层同理（见第六节）。

3. **"主页搜索关闭按钮 BUG"** —— **当前源码中"点 × 打开标签编辑"这一具体 BUG 已不复现**，但有明确的脆弱点与两个残留问题。
   - 旧根因（上一轮文档所述）与官方 `Scaffold` 绘制顺序一致：`Scaffold.kt:354-379` 依次 `place(body → topBar → snackbar → bottomBar → fab → popup)`，旧实现把搜索浮层写在 `body` 槽里，顶栏盖在上面且 `graphicsLayer{alpha=0}` 不影响命中测试，× 与顶栏第 3 个动作（编辑标签）坐标重合。
   - 当前实现（`PageFrame.kt:113-166`）把 `overlay()` 放在 `Scaffold` **之后**、同为 `Box` 子节点，绘制与命中都在顶栏之上；`HomeScreen.kt:215-231` 的 `onDismissRequest = { searchOpen = false }` 与 `tagEditorOpen`（`HomeScreen.kt:107/174-176`）是两个独立状态、两个独立回调，没有回调复用。
   - 读 `GlassSearchOverlay.kt` 源码确认：**清空 × 与取消 × 是两个不同按钮、两个不同语义**——清空按钮 `onClick = { query = ""; focusRequester.requestFocus() }`（`GlassSearchOverlay.kt:444-459`），取消按钮 `onClick = onDismissRequest`（`GlassSearchOverlay.kt:490-503`）。Glacier 没有把两者接错。
   - **残留问题 A**：`if (searchOpen) { GlassSearchOverlay(expanded = true, …) }`（`HomeScreen.kt:216-231`、`AppsScreen.kt:97-112`）只支持"展开"，不支持"收起动画"；官方是常驻组合 + `expanded = searchExpanded`（`GlassPage.kt:684-694`、`IconPage.kt:503-544`），`GlassSearchOverlay.kt:215-245` 的 400ms 几何动画 / 150ms 取消按钮动画在 Glacier 永远看不到，关闭是瞬间消失。
   - **残留问题 B（契约脆弱）**：修复依赖 `PageFrame` 把浮层放在 `Scaffold` 外的结构约束（`PageFrame.kt:62-83` 注释已写明）。一旦后续实现把它挪回 `Scaffold { }` 的 body 槽，BUG 立即复发；且顶栏在搜索态只是视觉淡出 + `clearAndSetSemantics`（`PageFrame.kt:118-124`），并没有从命中测试树中移除，靠浮层自身全屏 `clickable` 拦截（`GlassSearchOverlay.kt:261-266`）。

**P0 共 5 项**（详见第十一节）：底栏宽度、手动让位/FAB 位置、宽屏侧栏与顶栏结构、搜索浮层组合语义、Apps 空态未应用 padding。

---

## 二、审计方法与证据等级

| 项 | 说明 |
|---|---|
| 源码证据 | 所有"官方对应实现"都给出 `miuix/**` 的 `file:line`，不是 README 或旧文档 |
| 数值来源 | ① 源码默认值；② 源码与调用处参数推算；③ 截图像素测量（density 2.625，`docs/verification/miuix-os4-r2/`） |
| L1 | 源码接线/采样源/兄弟关系正确 |
| L2 | 本轮截图 / 交互路径可证明组件挂载、绘制、点击成立（截图与当前源码几何一致，见 3.2 实测） |
| L3 | 真实 GPU 下的折射、模糊半径、边缘光 —— **未验证**（模拟器 SwiftShader；且 `PageFrame.kt:188/192` 在模拟器上显式关闭了 `progressiveBlur`） |
| 未验证项 | 大字体（未截图）、RTL（未截图）、真机 L3、Release 包本轮未重跑 |

---

## 三、全局骨架：Shell / PageFrame / 录制层

### 3.1 当前结构与官方结构对照

```
Glacier 当前（GlacierShell.kt:157-233 + ui/components/PageFrame.kt:113-166）
Scaffold(Transparent, insets=0)                     ← Shell：只做 popup 宿主
└ Box(fillMaxSize)
   ├ Box().layerBackdrop(R)                         ★R：整屏录制层（drawRect(surface)+drawContent）
   │  └ NavDisplay(Modifier.railStartInset(railInset))   ← 宽屏时整页右移 262dp+系统 inset
   │     └ HorizontalPager(beyondViewportPageCount = 0)
   │        └ PageFrame
   │           Box
   │           ├ Scaffold(containerColor = surface)
   │           │  ├ topBar  → GlassTopAppBar(P)      ← 与 P 同父
   │           │  │            └ bottomContent → GlassTabRow
   │           │  ├ fab     → Box(padding(bottom = reserve-20)) { GlassFAB(P) }
   │           │  └ body    → Box().layerBackdrop(P)  ★P：页面正文录制层
   │           └ overlay()                            ← 搜索/标签编辑/对话框（实际弹层由根 Scaffold popup 宿主绘制）
   ├ GlassNavigationBar(R).align(BottomCenter)      ← 全宽，无 24dp 内边距
   └ GlassNavigationRail(R).align(CenterStart)      ← 常驻展开
```

```
MIUIX 官方 example（AppContent.kt:637-683, 686-829, 975-1017；MainPage.kt:201-347）
Scaffold(Transparent, insets=0)
└ Box
   ├ Row
   │  └ Box(weight(1f))                              ← 页面列
   │     ├ Box(layerBackdrop(sidebarBackdrop)) → NavDisplay
   │     └ GlassNavigationRail（仅宽屏 glass 侧栏，overlay 在页面内）
   ├ CompactScreenLayout: Scaffold(
   │     contentWindowInsets = safeInsets,
   │     bottomBar = { GlassNavigationBar(fillMaxWidth().padding(horizontal = 24.dp)) },   ← ★底栏在 Scaffold.bottomBar
   │     floatingActionButton = { GlassFAB }
   │  ) { innerPadding → Box(layerBackdrop) { pagerContent(innerPadding) } }
   └ 页面自身：Scaffold(topBar = BlurredBar{AdaptiveTopAppBar})
              └ Box(layerBackdrop(pageBackdrop)) { LazyColumn(contentPadding = pageContentPadding(inner, outer)) }
```

| 项 | 当前实现（file:line） | MIUIX 官方（file:line） | 差异（数值/结构） | 整改方案 | 状态 |
|---|---|---|---|---|---|
| 根 Scaffold 层级 | `GlacierShell.kt:157-161`，`containerColor=Transparent`、`contentWindowInsets=WindowInsets(0)`，无 bottomBar | `AppContent.kt:308-311` 同款（弹层宿主）；compact 的底栏/ FAB 在 `AppContent.kt:648-683` | 官方根 Scaffold 只做 overlay 宿主；**导航组件放在页面级 compact Scaffold 的 bottomBar** | 把底栏/ FAB 的让位交给页面级 Scaffold 的 bottomBar 槽 | 未整改 |
| 双重 Scaffold | Shell Scaffold（`GlacierShell.kt:157`）+ 每页 PageFrame Scaffold（`PageFrame.kt:114`） | 官方同样两层（`AppContent.kt:308` + `MainPage.kt:202`），但**内层 compact Scaffold 有 bottomBar** | 层数相同，**职责分配错误**：Glacier 内层 Scaffold 的 bottomBar 恒为空 | 见 P0-2 | 未整改 |
| 双重录制层 | R（Shell，`GlacierShell.kt:163-167`）+ P（页面正文，`PageFrame.kt:148-153`） | 官方 compact backdrop（`AppContent.kt:644-647,676`）+ 页面 pageBackdrop/content backdrop（`MainPage.kt:95-96,203,278`） | 数量相同；官方 pageBackdrop 记录**整个 Scaffold（含顶栏）**，Glacier 的 P **只录正文**，导致弹层/下拉采 R 而不是"页面+栏"（见第十节） | 弹层 backdrop 改为"页面正文 + 顶栏/底栏"的合成层，或与官方一样在 Scaffold 外层记录 | 未整改 |
| 顶栏位置 | PageFrame `topBar` 槽，`GlassTopAppBar(backdrop = P)`（`PageFrame.kt:126-136,181-198`） | `MainPage.kt:204-275` + `utils/PageUtils.kt:225-291`（`BlurredBar` + `AdaptiveTopAppBar`，`defaultWindowInsetsPadding=false` + `appPageTopBarInsets()`） | Glacier 未传 `defaultWindowInsetsPadding=false`、未用 `appPageTopBarInsets()`（见 4.3 宽屏双重 inset） | 换成 `appPageTopBarInsets` 语义：横向安全区 + 侧栏让位只算一次 | 未整改 |
| FAB 位置 | `PageFrame.kt:139-146`（`padding(bottom = reserve-20)`）+ `HomeScreen.kt:192-214` | `AppContent.kt:658-665`（Scaffold FAB 槽）+ `Scaffold.kt:261-267` | 官方 FAB 底距 = bottomBar 高 + `FabSpacing(12dp)`；Glacier 手算 `reserve-20`，竖屏高 24dp、三键导航高 60dp | 删除手算 padding，把底栏放进 bottomBar 槽 | 未整改 |
| overlay 层级 | `PageFrame.kt:165` `overlay()` 在 `Scaffold` 之后（同一 Box） | `GlassPage.kt:405-437` 弹层与 Scaffold 同级之后；`AppContent.kt:378` `TopLayerHost` 在根 Scaffold 内 | **正确**（搜索 × BUG 的结构修复）；但 `if(searchOpen)` 条件组合导致退出动画丢失（P0-4） | 保持位置；改为常驻组合 + `expanded` 状态 | 已整改（待复验） |
| topBarBottom 槽 | `PageFrame.kt:99,133` → `GlassTopAppBar(bottomContent=…)`，Home 在这里放 `GlassTabRow`（`HomeScreen.kt:181-191`） | `GlassPage.kt:394-401`（`bottomContent = { tabRows(Modifier.padding(horizontal=12).padding(top=6,bottom=12))) }`） | **采样安全**：顶栏是 P 的兄弟，`GlassTabRow` 又从 `LocalGlassTopAppBarContext` 取顶栏 backdrop（`GlassTabRow.kt:309-312`），不是自采样；差异只在 padding（缺 top 6、bottom 8 vs 12） | 保留位置，改 padding | 已整改（待复验） |
| overlay 内弹层的宿主 | `GlassDialog/GlassBottomSheet` 默认 `renderInRootScaffold=true`（`Glacier` 未覆盖），实际由 Shell 根 Scaffold 的 popup 宿主绘制 | 官方同默认（`GlassDialog.kt:55`、`GlassBottomSheet.kt:59`） | **正确**：弹层在 R 之外，采样 R 是兄弟关系（L1） | 保留 | 已验证（L1/L2） |
| 页面切换 | `GlacierShell.kt:88-99,181-184`：`HorizontalPager` + `animateScrollToPage`，`beyondViewportPageCount = 0` | `AppContent.kt:975-1017`：`PagerNavigationSpringSpec`（`utils/PagerGestureUtils.kt:75-80`，stiffness 322.2 / dampingRatio≈0.9）+ `beyondViewportPageCount = 4` | 官方翻页更慢更阻尼；相邻页预组合 4 页用于 backdrop 连续采样，Glacier 为 0 → 滑动中玻璃/背景断层 | 接入 `PagerNavigationSpringSpec`，`beyondViewportPageCount` 改 ≥1 | 未整改 |
| 二级页转场 | `GlacierShell.kt:168-206`，`NavDisplay` 全默认（`NavDisplay.kt:974-980`、`NavDisplayEffects.kt:60-67`） | `AppContent.kt:257-305`：`NavDisplayEffects(cornerClipRadius = rememberNavSystemCornerRadius(), dimAmount = 0.5, backdropColor = surface)` + `NavTransitions.MiuixDefault` | Glacier 缺系统屏幕圆角裁剪、缺 `backdropColor`（卡片式缩放时露出未填充区域），`swipeDismiss` 未显式传 | 传入 `NavDisplayEffects` 与 `swipeDismiss` | 未整改 |

### 3.2 底栏/让位数值推导（可直接用于验收）

以下按 Android 16 模拟器实测环境：屏宽 411dp（1080px / 2.625）、手势导航 `navigationBars.bottom = 24dp`；三键导航取值 48dp。

| 量 | Glacier 推算 | Glacier 实测 | 官方 example 推算 | 差值 |
|---|---|---|---|---|
| 底栏胶囊宽度 | `fillMaxWidth()` = 411dp（`GlassNavigationBar.kt:326-328`） | 全宽（截图逐列检测，图标中心 73.9/205.1/337.3dp 与全宽模型一致） | 411−2×24 = 363dp（`AppContent.kt:774`） | 每侧 24dp，共 48dp |
| 单个目的地宽 | (411−2×8)/3 = 131.7dp（`ContentPaddingHorizontal=8`，`GlassNavigationBar.kt:143`） | — | (363−16)/3 = 115.7dp | +16dp/项 |
| 底栏总高 | 54 + BottomPadding(12+24)=90dp（`GlassNavigationBar.kt:305-324`） | 矩形区域 36→90dp（截图） | 同 90dp | 0（底栏本体正确） |
| 页面底部内边距 | 24 + LocalNavReserve(54+24+32=110) + 24 = **158dp**（`PageFrame.kt:278`） | — | Scaffold contentPadding.bottom = bottomBar 高 = **90dp**（`Scaffold.kt:338-343`） | **+68dp** |
| 三键导航页面底部 | 48 + (54+48+32=134) + 24 = **206dp** | — | 54+48 = **102dp** | **+104dp** |
| FAB 底边距屏幕底 | (54+24+32−20) + 12 + 24 = **126dp** | **127dp**（蓝色 FAB 像素 bbox y=1922..2066，屏高 2400） | bottomBar 90 + FabSpacing 12 = **102dp** | **+24dp** |
| 三键导航 FAB 底距 | (54+48+32−20)+12+48 = **174dp** | — | 102+12 = **114dp** | **+60dp** |

结论：底栏本体高度正确，**宽度与让位全部错误**，且错误量恰好等于"导航栏 inset 被重复计入"。

---

## 四、全局导航：NavigationBar / NavigationRail / 顶栏 / Insets

### 4.1 底栏（窄屏）

| 项 | 当前实现（file:line） | MIUIX 官方（file:line） | 具体差异 | 整改方案 | 状态 |
|---|---|---|---|---|---|
| 组件 | `GlassNavigationBar`（`GlacierShell.kt:224-231`） | 同左（`AppContent.kt:767-775`） | 都是官方组件 | 保留组件 | 已验证（L2） |
| 宽度/水平内边距 | `Modifier.align(Alignment.BottomCenter)`，无 padding（`GlacierShell.kt:230`） | `Modifier.fillMaxWidth().padding(horizontal = 24.dp)`（`AppContent.kt:772-774`） | 胶囊宽 411 vs 363dp；目的地 131.7 vs 115.7dp；图标中心实测 73.9/205.1/337.3 vs 89.9/205.7/321.5dp | 调用处补 `.fillMaxWidth().padding(horizontal = 24.dp)`；最终方案是移进 Scaffold.bottomBar | 未整改 |
| 让位 | 手算 `LocalNavReserve`（`GlacierShell.kt:147-148`）+ `pageContentPadding`（`PageFrame.kt:266-280`） | `Scaffold.bottomBar` 自动让位（`AppContent.kt:651-657`、`Scaffold.kt:338-343`） | 底部多 68~104dp；FAB 高 24~60dp；`reserve-20.dp`/`+32.dp`/`+24.dp` 三个魔法值 | 删除 `LocalNavReserve` 与加法，改 bottomBar 槽 | 未整改 |
| 隐藏逻辑 | `visible = backStack.size == 1`（`GlacierShell.kt:209,229`）；但 `LocalNavReserve` 仍为 110dp（`GlacierShell.kt:155`） | 官方底栏在 compact Scaffold，二级页整页替换（`AppContent.kt:417-454`） | 二级页（About/Licenses）底栏隐藏后仍保留 110+24dp 空白 | 让位量随可见性归零，或改 bottomBar | 未整改 |
| 目的地图标/文字 | 官方默认（IconSize 25dp、LabelSize 11dp、fontScale≥1.6 时 16dp，`GlassNavigationBar.kt:125-131,243-245`） | 同左 | 一致，库内处理大字体 | 保留 | 已验证（L2） |
| 选中指示器/材质 | 官方默认（`GlassNavigationBarDefaults.material()` `GlassMaterials.puredThinGlass`，`GlassNavigationBar.kt:158-163`） | 同左 | 一致 | 保留 | 已验证（L2，L3 未验证） |

### 4.2 侧栏（宽屏）

| 项 | 当前实现（file:line） | MIUIX 官方（file:line） | 具体差异 | 整改方案 | 状态 |
|---|---|---|---|---|---|
| 断点 | `isWideLayout()` = `screenWidthDp >= 840`（`GlassEnvironment.kt:47-52`） | `shouldShowSplitPane()` = `width >= 840` **或** `(width >= 600 && height/width < 1.2)`（`utils/AdaptiveUtils.kt:13-22`） | 600–839dp 的横屏/折叠屏/平板上官方会显示侧栏，Glacier 显示底栏 | 对齐官方断点 | 未整改 |
| 展开策略 | 常驻展开：`showToggle = false`（`GlacierShell.kt:210-222`），`LaunchedEffect(wide){ railState.expanded = wide }`（`:104`） | 仅 `width >= 1200` 才默认展开（`utils/AdaptiveUtils.kt:35-42`），其余是**顶部折叠胶囊**（官方默认形态） | 914dp 横屏下官方是折叠胶囊（44dp 高），Glacier 是 250dp 宽常驻面板，遮挡 1/4 屏 | 恢复折叠形态；若保留常驻，必须重新设计顶栏与胶囊关系并写入审计 | 未整改（保留需说明） |
| 让位计算 | `railInset = systemStartInset + (Margin 12 + ExpandedWidth 250) × progress`（`GlacierShell.kt:116-126`） | 官方 `rememberAppPageInsets(overlayStart = (Margin+ExpandedWidth)×progress)`，由页面 `pageContentPadding` 消费（`AppContent.kt:229-246`、`PageUtils.kt:104-114`） | 数值公式一致；但 Glacier 让位作用在 **NavDisplay 整个页面**（`GlacierShell.kt:173-175`），官方只让位内容/顶栏内容 | 改为官方布局：页面表面满宽、内容按 `contentInsets` 让位 | 未整改 |
| 侧栏 backdrop 内容 | 页面被整体右移 262dp，R 在侧栏区域只有 `drawRect(surface)`（`GlacierShell.kt:129-132,163-175`） | 官方页面满宽铺在侧栏下方，`GlassNavigationRail` 采样到页面内容（`AppContent.kt:367-375`） | Glacier 侧栏玻璃后面是纯色 surface → 玻璃退化；截图 `18-landscape-rail.png` 中面板内无内容透出 | 页面满宽（P0-3） | 未整改 |
| 宽屏二级页 | 侧栏 `selectedIndex = tab` 恒定（`GlacierShell.kt:213`），无 `secondaryItems` | 官方二级路由用 `selectedIndex = -1` + `secondaryItems`（`AppContent.kt:518-562`） | About/Licenses 打开时侧栏仍高亮一级 tab，且无返回二级入口 | 补 secondary items / 传 -1 | 未整改 |
| 侧栏内边距 | 依赖 `defaultWindowInsetsPadding = true`（`GlassNavigationRail.kt:386-400`） | 官方 `windowInsetsPadding(LocalAppPageInsets.windowInsets)` + `defaultWindowInsetsPadding=false`（`AppContent.kt:538,558-561`） | 数值等价（均含 systemBars/captionBar/displayCutout） | 保留 | 已验证（L2） |

### 4.3 顶栏（TopAppBar）

| 项 | 当前实现（file:line） | MIUIX 官方（file:line） | 具体差异 | 整改方案 | 状态 |
|---|---|---|---|---|---|
| 玻璃档组件 | `GlassTopAppBar`（`PageFrame.kt:181-198`） | `AdaptiveTopAppBar` → `GlassTopAppBar`（`PageUtils.kt:247-272`） | 组件一致 | 保留 | 已验证（L2） |
| 非玻璃档 | `TopAppBar`（`PageFrame.kt:199-208`） | 同左（`PageUtils.kt:273-290`） | 官方非玻璃档传 `color=Color.Transparent` + 外层 `background(color)`；Glacier 用默认 surface | 保持等价即可，可对齐写法 | 未整改（低） |
| 横向 Insets | **未传** `defaultWindowInsetsPadding=false`，库默认 `true`（`GlassTopAppBar.kt:290`），库内会再加 `displayCutout.only(Horizontal) + navigationBars.only(Horizontal)`（`TopAppBar.kt:880-883`） | `defaultWindowInsetsPadding = false` + `contentModifier = … .appPageTopBarInsets()`（`PageUtils.kt:243,257,266`），横向只算 `contentInsets`（= safe + rail overlay）一次 | **宽屏/横屏双重横向 inset**：NavDisplay 已整体右移 48+262，顶栏又加 48 → 实测 `18-landscape-rail.png` 标题左缘 389.7dp、卡片左缘 333.7dp（官方关系是标题 26dp vs 卡片 12dp，即 14dp；实际相差 56dp） | 传 `defaultWindowInsetsPadding = false`，横向用 `appPageTopBarInsets` 语义只算一次 | 未整改 |
| 折叠行为 | 从不传 `expandable`，默认 `true`（`GlassTopAppBar.kt:272`） | compact 默认 `expandable = !isWideScreen`；About 显式 `expandable = false`（`PageUtils.kt:231`、`AboutPage.kt:139`） | 横屏 411dp 高度下仍显示 32sp 大标题（顶栏约 114dp，官方宽屏 52dp 小标题）；About 页官方不可展开，Glacier 可展开 | PageFrame 增加 `expandable` 参数：`!isWideLayout()`，About 传 false | 未整改 |
| 搜索态淡出 | `graphicsLayer{alpha}` + `rememberGlassSearchBarAlpha` + `glassSearchBarTransition`（`PageFrame.kt:111,118-124,189-194`） | `GlassPage.kt:204,268,339` 同款 API | 组件/动画 API 正确；Glacier 只淡出顶栏（更精确），且模拟器上 `blurEnabled=false` | 保留（理由：只淡出 chrome） | 已整改（待复验） |
| 顶栏动作图标 | 24dp（`PageFrame.kt:223,232`；`TopBarAction`） | 玻璃档 28dp（`MainPage.kt:226/256/370`、`component/BackNavigationIcon.kt:37`） | 小 4dp | 玻璃档 28dp，非玻璃 24dp（与官方一致） | 未整改 |
| 动作间距 | 无（5 个 `GlassIconButton` 直接排列，`HomeScreen.kt:136-177`） | 动作之间 `Spacer(Modifier.width(8.dp))`（`MainPage.kt:242`） | 多选态 5×44dp 紧密排列占 220dp，窄屏大字体会挤压大标题 | 加 8dp spacer；多选动作数量重新设计 | 未整改 |
| 返回图标 | `TopBarAction(ChevronBackward)` 24dp（`AboutScreen.kt:63-65`、`LicensesScreen.kt:74-76`） | `BackNavigationIcon`：玻璃 28dp、非玻璃 24dp，**RTL 时 `scaleX=-1` 镜像**（`component/BackNavigationIcon.kt:34-44`） | 缺 RTL 镜像；玻璃档小 4dp | 复用/对齐官方 back 组件语义 | 未整改 |
| 顶栏动作无障碍 | 仅 `contentDescription`（`PageFrame.kt:220-236`） | 官方示例用 `TooltipBox(text=…)` 包裹（`MainPage.kt:213,243`） | 长按无 tooltip | 补 Tooltip | 未整改（低） |
| 滚动折叠 | `PageFrame.kt:157` 只挂 `nestedScroll(scrollBehavior.nestedScrollConnection)` | `pageScrollModifiers()` = `scrollEndHaptic() + overScrollVertical(onOffsetChanged) + nestedScroll()`（`PageUtils.kt:168-183`，`MainPage.kt:281-285`） | 缺滚动末端触感、缺弹性 overscroll（`GlassTopAppBar` 的 `scrollBehavior.state.overscrollOffset` 永远为 0，`GlassTopAppBar.kt:386-391` 的弹性材质进度失效） | 接入官方 `pageScrollModifiers` | 未整改 |

---

## 五、逐页 UI 清单

### 5.1 Home 主页（`feature/home/HomeScreen.kt`）

| 项 | 当前实现（file:line） | MIUIX 官方（file:line） | 具体差异 | 整改方案 | 状态 |
|---|---|---|---|---|---|
| 页面骨架 | `PageFrame`（`HomeScreen.kt:129-134`） | `Scaffold(topBar=…){ Box(layerBackdrop){ LazyColumn(pageScrollModifiers) } }`（`MainPage.kt:201-347`） | 见第三节；缺 pageScrollModifiers / VerticalScrollBar | 骨架整改（P0-2/P1） | 未整改 |
| 顶栏标题/副标题 | `title = "Glacier"`，`subtitle = "标签 · N 个应用"`（`HomeScreen.kt:130-132`） | 官方 MainPage 仅 title（`MainPage.kt:207`）；副标题 API 存在（`GlassTopAppBar.kt:276`） | 用法合法，文案自定义 | 保留 | 已验证（L2） |
| 顶栏动作（普通态） | 搜索/多选/编辑标签 3 个 `TopBarAction`（`HomeScreen.kt:167-177`） | 官方 2 个动作 + 8dp spacer（`MainPage.kt:212-272`） | 图标 24dp、无 spacer（见 4.3） | 对齐 28dp + spacer | 未整改 |
| 多选态顶栏 | 冻结/解冻/标签/更多/关闭 5 个动作（`HomeScreen.kt:136-166`） | 官方无"多选态"范式 | 5×44dp=220dp 动作区，411dp 屏仅余 191dp 给 32sp 标题；大字体会挤爆 | 减少动作（合并进 ⋮ 弹层）或缩小到顶栏可容纳 | 未整改（自定义需说明） |
| 标签 TabRow | `GlassTabRow`（`GlassTabRow.kt:290-304`）/ 非玻璃 `TabRow`（`HomeScreen.kt:435-449`），外层 `Column.padding(start/end=12, bottom=8)`（`:430-434`） | 顶栏 `bottomContent`：`padding(horizontal = 12).padding(top = 6, bottom = 12)`（`GlassPage.kt:394-401`） | 缺 top 6dp；bottom 8 vs 12（少 4dp）；玻璃 40dp vs 非玻璃 42dp（`GlassTabRow.kt:117` vs `TabRow.kt:556`）→ 切玻璃开关时顶栏高度变 2dp、内容跳 2~6dp | padding 改 `top=6,bottom=12`；非玻璃档固定同高容器 | 未整改 |
| 应用网格 | `LazyVerticalGrid` 4/5 列，`spacedBy(6.dp, 18.dp)`，contentPadding 横向 16dp（`HomeScreen.kt:334-341`） | MIUIX 无启动器网格；官方列表页横向基准 12dp（`MainPage.kt:312`、`GroupedCardItems.kt:33`） | 横向 16 vs 12dp；网格间距 6/18dp 为自定义值 | 横向改 12dp；间距写入 `PageMetrics` 并注明来源 | 未整改 |
| 图标单元 | `HomeAppItem`：`clip(RoundedCornerShape(18.dp))` + `combinedClickable` + `padding(vertical=4,horizontal=2)`（`HomeScreen.kt:387-393`），图标 56dp，标签 `footnote1` + 强制 `fontSize=labelSize.sp`（`:396-410`） | 无对应组件；MIUIX 卡片圆角 16dp（`Card.kt:192`），About 品牌块 88dp/24dp（`AboutPage.kt:242-254`） | ① 点击裁剪形状是 `RoundedCornerShape(18dp)`，与图标 squircle(12.32dp) **不是同一形状体系**；② 标签 style(footnote1=13sp) 与显式 14sp 双源；③ 按压反馈不是 MIUIX `Sink/Highlight`（Card/BasicComponent 才有 `pressFeedbackType`，`Card.kt:110`） | 去掉 clip（或用官方 squircle 同形），标签用 `style.copy(fontSize=…)` 保留行高 | 未整改 |
| 图标选中环 | `AppIconView(selected)`：`squircleBorder(radius+2).padding(2.dp)` 然后 `squircleClip(radius)`（`AppComponents.kt:51-76`） | 官方 `squircleBorder` / `squircleClip`（`miuix-squircle/.../SquircleBorder.kt:30`、`SquircleBackground.kt:96`） | 选中时内容实际缩到 52dp（padding 在内层），描边半径 14.32dp@56 与裁剪半径 12.32dp@52 比例不一致（0.256 vs 0.237）→ 选中/取消选中图标 4dp 跳动、描边与轮廓不平行（代码注释称"不会跳动"，与实际修饰符顺序不符） | 描边改用同尺寸外框叠加（`drawBehind` 不参与布局），不要用 padding | 未整改 |
| FAB | `GlassFloatingActionButton` 56dp，24dp 图标（`HomeScreen.kt:192-214`） | `GlassFloatingActionButtonDefaults.Size = 56.dp`（`GlassFloatingActionButton.kt:39`）；官方 FAB 内容 `Icon` 默认 24dp（`AppContent.kt:845-850`） | 尺寸/图标一致；**位置错**（见 3.2） | 位置整改 | 已整改（待复验，位置未整改） |
| 搜索浮层 | `GlassSearchOverlay(expanded=true, onDismissRequest={searchOpen=false}, label, blurEnabled)`（`HomeScreen.kt:215-231`） | `GlassSearchOverlay(expanded=searchExpanded, onDismissRequest=…, scrollMaterialThreshold, topInsets, searchTopPadding, horizontalInsetsModifier, searchIcon/closeIcon)`（`IconPage.kt:503-544`、`GlassPage.kt:684-694`） | 缺 5 个参数：滚动材质阈值、`scrollOffset`、`topInsets/searchTopPadding`（宽屏折叠胶囊让位）、`horizontalInsetsModifier`（宽屏侧栏让位）、Glass 图标族；且 `expanded` 恒 true（P0-4） | 常驻组合 + 参数补齐（P0-4、P1） | 未整改 |
| 搜索结果行 | 自绘 `Row + foundation clickable + padding(4dp,10dp)`，图标 40dp、间距 14dp、`take(50)`（`AppSearchResults.kt:65-110`） | 官方搜索结果：`Card(padding(horizontal=12, vertical=SearchResultVerticalPadding))` + `BasicComponent`（`IconPage.kt:221-244`），图标 24/28dp | 结构（无卡片/无官方行）、内边距（4 vs 16）、图标（40 vs 28）、行高（60 vs 56+）全部不一致；`take(50)` 截断 | 改用 `Card` + `BasicComponent`；分页/不限 50 | 未整改 |
| 标签编辑 BottomSheet | `GlassBottomSheet(show=true)` + `BasicComponent` 行 + 48dp `IconButton`/20dp 图标（`HomeScreen.kt:453-496`） | `GlassBottomSheet`（`GlassBottomSheet.kt:50-74`，`insideMargin=(24,0)`，`cornerRadius=28`）；内容范式 `SmallTitle(insideMargin=(16,8)) + Card + 行间距 12`（`component/BottomSheetSection.kt:209-267`） | ① `show=true` 条件组合 → 无退出动画；② `onDelete` 参数声明（`:459`）但**从未使用** → 删除标签入口不可达；③ 无分组 Card；④ IconButton 48dp vs 官方默认 40dp（`IconButton.kt:84-89`） | 删除入口补回（长按/尾部菜单）；常驻组合；行嵌套 Card | 未整改 |
| 标签名 Dialog | `GlassDialog(show=true)` + `TextField` + 两个 `fillMaxWidth` 按钮（`HomeScreen.kt:498-542`） | 官方 Dialog 动作：`Row(spacedBy(12))` + `weight(1f)`（`GlassPage.kt:422-436`）或 `Column(spacedBy(12))`（`component/DialogSection.kt:350-366`） | 两个按钮**垂直 0dp 间距**紧贴（`DialogContentLayout.kt:423-446` 的 Column 无 Arrangement）；Confirm 在文本为空时无反馈；`show=true` 无退出动画 | 按钮加 12dp 间距/改横向 weight；blank 时禁用或提示 | 未整改 |
| 删除标签确认 Dialog | `GlassDialog(show=true)` + 2 个全宽按钮（`HomeScreen.kt:293-319`） | 同上 | 0dp 间距；无退出动画；因为 onDelete 不可达，整个 Dialog 是死代码 | 与标签编辑一起整改 | 未整改 |
| 延时冻结弹层 | `GlassBottomSheet(show=true)` + 4 个 `BasicComponent`（同一 `QuietMode` 图标）（`HomeScreen.kt:544-567`） | 官方弹层范式同 `BottomSheetSection.kt:209-267`；单选语义应体现选中态 | 4 行同一图标、无当前选中态、无取消行；`show=true` 无退出动画 | 用 `GlassPopup`/`RadioButtonPreference` 语义或标选中态 | 未整改 |
| 空态 | `EmptyState`（`PageFrame.kt:302-321`）在网格 item 内 | 官方无空态组件 | 无图标/插画，仅 48/24 padding 文本；可接受 | 保留（需说明） | 已核对 |
| 多选交互 | 长按进入选择（`HomeScreen.kt:363-370`） | 官方无多选范式 | 长按同时 toggle 选中项；FAB 隐藏、底栏仍显示；顶栏 5 动作 | 保留（需说明），但动作/底栏需收口 | 未整改 |

### 5.2 Apps 应用列表（`feature/apps/AppsScreen.kt`）

| 项 | 当前实现（file:line） | MIUIX 官方（file:line） | 具体差异 | 整改方案 | 状态 |
|---|---|---|---|---|---|
| 顶栏 | `PageFrame(title=应用, subtitle=N 个应用)` + 搜索/排序/筛选（`AppsScreen.kt:86-95`） | 官方列表页范式 `MainPage.kt:201-275` | 图标 24dp、无 spacer（同 4.3） | 同 4.3 | 未整改 |
| 列表容器 | `LazyColumn` contentPadding 横向 **16dp**、`spacedBy(8.dp)`（`AppsScreen.kt:153-157`） | 官方卡片横向 12dp（`MainPage.kt:312`、`LicensePage.kt:118`），分组卡片间距 6dp（`GroupedCardItems.kt:70-72`） | 横向 16 vs 12；行间距 8 vs 6/12 | 统一 12 + 6 | 未整改 |
| 列表卡片 | `Card(onClick, pressFeedbackType = Sink, insideMargin = 0, 默认 colors)`（`AppsScreen.kt:177-183`） | `Card`（`Card.kt:187-199`：CornerRadius 16dp、InsideMargin 0、surfaceContainer） | 官方卡片参数正确；`insideMargin = PaddingValues(0.dp)` 是默认值冗余 | 保留（可删除冗余参数） | 已验证（L2） |
| 列表行 | `AppRow` = `BasicComponent(title, summary, startAction=40dp 图标, endActions)`（`AppComponents.kt:122-139`） | `BasicComponent`（`Component.kt:287-292` InsideMargin 16dp，start/center 8dp spacer，`Component.kt:211`） | 行内边距/图文间距走官方；图标 40dp 为自定义（MIUIX 无应用行组件），合理 | 保留 | 已验证（L2） |
| 行副标题拼接 | `packageName + " · 已置顶/白名单/不在主页"` 字符串拼接（`AppsScreen.kt:186-192`） | 官方 summary 单行（`BasicComponent` summary） | 411dp 下长包名 + 3 个徽标会截断，无省略号策略控制（BasicComponent 内部 maxLines？） | 拆徽标为尾部图标/Badge，或分两行 | 未整改（低） |
| 排序弹层 | `GlassBottomSheet(show=true)` + 选中项 `SheetActionRow(Ok)` + 未选中 `BasicComponent`（`AppsScreen.kt:207-242`） | 官方离散选项用 `GlassDropdownPreference`/单选行（`GlassDropdownPreference.kt:63-82`）；弹层范式 `BottomSheetSection.kt:209-267` | ① `show=true` 无退出动画；② 选中/未选中行结构不同（一有图标一无）导致文字左缩进不一致（选中行多 24+8dp）；③ 无分组 Card | 选中态改为尾部 `Ok`（`endActions`），保持文字基线一致 | 未整改 |
| 筛选弹层 | `GlassBottomSheet(show=true)` + 4 个 `SwitchPreference`（`AppsScreen.kt:244-274`） | 官方设置行 `SwitchPreference`；弹层范式同（`BottomSheetSection.kt:219-237` 用 Card 分组） | 行直接铺在弹层里未分组；`show=true` 无退出动画 | 加 Card 分组；常驻组合 | 未整改 |
| 搜索 | 同 Home（`AppsScreen.kt:96-112`） | 同 Home | 与 Home 同一套缺失（P0-4） | 统一修 | 未整改 |
| 搜索结果点击 | 打开操作单（`AppsScreen.kt:106-109`） | 官方搜索结果是"执行/跳转"（`IconPage.kt` 无对应） | 与 Home 搜索结果点击=启动 不一致 | 统一语义（建议都打开操作单或都启动） | 未整改 |
| 多选 | **无**（无选择状态、无长按） | 官方无多选范式 | 与 Home 多选能力不一致（同一应用列表两页能力不同） | 产品确认后补 | 未整改 |
| 空态 | `if (entries.isEmpty()) EmptyState(...)`，**content lambda 的 `padding` 参数完全未使用**（`AppsScreen.kt:149-151`） | 官方空态也应消费 `pageContentPadding` | 空态文本从 y≈0 开始排版、被顶栏覆盖（EmptyState 只有 48dp vertical padding，顶栏高 ≈76dp） | 用 `Box(Modifier.padding(pageContentPadding(padding)))` 包裹 | 未整改（P0） |

### 5.3 Settings（`feature/settings/SettingsScreen.kt`）

| 项 | 当前实现（file:line） | MIUIX 官方（file:line） | 具体差异 | 整改方案 | 状态 |
|---|---|---|---|---|---|
| 页面骨架 | PageFrame + LazyColumn（`SettingsScreen.kt:107-116`） | `Scaffold(topBar=…){ Box(layerBackdrop){ LazyColumn(pageScrollModifiers, pageContentPadding) } }`（`SettingsPage.kt:165-186`） | 缺 pageScrollModifiers / ScrollBar（见 4.3） | 补齐 | 未整改 |
| 分组结构 | `SmallTitle` + `GroupCard(Card(insideMargin=0))`（`SettingsScreen.kt:117-149` 等；`PageFrame.kt:288-300`） | `groupedCardItems`：`SmallTitle(28,8)` + `Card` 每段 `outerHorizontalPadding = 12`（`GroupedCardItems.kt:24-89`、`SettingsPage.kt:165-202`） | 结构等价；但**分节标题实际左边距 = 内容 padding(12) + SmallTitle 28 = 40dp**，官方 = 28dp（多 12dp）；`SectionGap=6` + SmallTitle 8 → 标题到卡片 14dp，官方 8dp | 分节标题使用绝对 28dp（不叠加内容 padding），或把内容 padding 只给卡片 | 未整改 |
| Preference 行 | `SwitchPreference` / `ArrowPreference` / `SliderPreference` 官方组件（`SettingsScreen.kt:138-147,164-226,284-310,337-381`） | 同名组件（`miuix-preference/.../SwitchPreference.kt` 等） | 使用正确；`float` 滑杆 `valueText = "14 sp"` 自定义文案（`:223`）与官方 `valueText` 范式一致 | 保留 | 已验证（L2） |
| 玻璃下拉 | `GlassDropdownPreference(backdrop = LocalRootBackdrop)`（`SettingsScreen.kt:120-137,154-163,173-184,311-330`） | `GlassDropdownPreference(backdrop = page backdrop)`（`SettingsPage.kt:192-206`），`renderInRootScaffold=true`（`GlassDropdownPreference.kt:72,148-155`） | 弹层宿主正确（根 Scaffold popup，在 R 之外，采样 R 安全，L1）；但 R 不含底栏，弹层玻璃底部区域采样缺底栏像素（L3 偏差） | 改用"页面+栏"合成 backdrop | 已整改（待复验，L3 未验证） |
| 状态管理 | 每个设置项用 `var x by remember(revision){ mutableStateOf(pref) }` 镜像（`SettingsScreen.kt:93-105`） | 官方直接读 appState | 双份状态源；`preferences.revision` 变更才刷新，切换玻璃开关后当前页状态可能滞后一帧 | 直接用 preference 的可观察属性 | 未整改（低） |
| 自动化开关禁用态 | `enabled = autoFreezeEnabled`（`SettingsScreen.kt:282,291,300,309`） | 官方 `SwitchPreference(enabled=…)`（`SwitchPreference.kt`） | 正确 | 保留 | 已验证（L2） |
| 系统权限入口 | 3 个 `ArrowPreference` 跳系统设置（`SettingsScreen.kt:346-381`） | 官方无权限引导组件 | 无 MIUIX 权限说明弹窗；设备管理员项用复制命令 + Toast（`:373-380`） | 可加 `GlassDialog` 说明（P2） | 未整改（低） |
| 页面底部 | `Spacer(24.dp)`（`SettingsScreen.kt:395`） | 官方 `VerticalScrollBar` + 列表底部动态 padding | 与 navReserve 叠加后底部空白 182dp | P0-2 一并删除 | 未整改 |

### 5.4 About（`feature/about/AboutScreen.kt`）

| 项 | 当前实现（file:line） | MIUIX 官方（file:line） | 具体差异 | 整改方案 | 状态 |
|---|---|---|---|---|---|
| 顶栏 | 与一级页同一 `PageFrame`，大标题可展开 + 24dp 返回图标（`AboutScreen.kt:60-65`） | `AdaptiveTopAppBar(expandable = false)` + `BackNavigationIcon` 28dp（`AboutPage.kt:135-160`、`BackNavigationIcon.kt:34-44`） | About 官方不可展开、无 32sp 大标题；返回图标小 4dp、无 RTL 镜像 | PageFrame 增加 `expandable=false`；返回图标对齐 | 未整改 |
| 品牌区 | 自绘 `Column` + `Box(size=80dp).squircleBackground(Color.White, 18dp)` + `Image(ic_brand_mark, 56dp)` + `title1` + `footnote1`（`AboutScreen.kt:150-183`） | `Box(size=88dp).graphicsLayer{ shape=RoundedCornerShape(24dp) }` + `Image(74dp)` + 35sp Bold 标题 + 14sp 版本（`AboutPage.kt:242-304`），并随滚动淡出/缩放 | 容器 80 vs 88dp（−8）；圆角 18 vs 24dp（−6）；图片 56 vs 74dp（−18）；标题 title1(32sp) vs 35sp Bold；**品牌标记本身在 108 viewport 只占 46.8dp（43%）**，56dp 图片里实际徽标仅约 24dp 宽（截图 `15-about-dark.png` 可见偏小）；官方滚动淡出动画完全缺失 | 容器/图片尺寸对齐官方；图标加 padding 或让内容铺满 108 视口；补滚动动画 | 未整改 |
| 信息卡 | `SmallTitle + GroupCard` + `BasicComponent`（只读）/`ArrowPreference`（跳转）（`AboutScreen.kt:76-125`） | `Card(RoundedCornerShape(16dp))` + `ArrowPreference`（`AboutPage.kt:341-437`） | 结构接近官方；官方全部用 `ArrowPreference`（带箭头），Glacier 区分"只读 BasicComponent/可点 ArrowPreference"（合理但不同） | 保留（需说明） | 部分已整改（待复验） |
| 版本行 | `BasicComponent(版本, "1.0.0-debug (1)")`（`AboutScreen.kt:79-86`） | 官方版本在品牌区下方 `v1.0.0 (1)`（`AboutPage.kt:292-304`） | 位置不同；且 debug 版本号出现在正式关于页 | 版本文本收敛到品牌区或保留（需产品决定） | 未整改（低） |
| 说明文字 | `Text(footnote1, 居中, padding top/start/end 12)`（`AboutScreen.kt:127-137`） | 官方无对应段落（用卡片行） | 合法但 12dp 三向 padding 是魔法值 | 收口到常量 | 未整改（低） |
| 品牌图标资源 | `res/drawable/ic_launcher_background.xml` 纯白 `#FFFFFFFF`；`ic_launcher_foreground.xml` 黑色 `#FF000000` 描边 4~5px；`ic_brand_mark.xml` 同前景（`res/drawable/*.xml`） | MIUIX 无品牌图标规范；官方 example 品牌块为 `Color.White` + `Image(74dp)`（`AboutPage.kt:242-261`） | 白底黑主体 ✔ 符合本轮要求；但前景内容只覆盖 108 视口中间 43%，导致各尺寸下"看起来偏小"；monochrome 复用同一前景（`mipmap-anydpi-v26/ic_launcher.xml:5`）可用 | 保持配色，重新按 72dp 安全区铺满构图 | 未整改 |

### 5.5 Licenses（`feature/about/LicensesScreen.kt`）

| 项 | 当前实现（file:line） | MIUIX 官方（file:line） | 具体差异 | 整改方案 | 状态 |
|---|---|---|---|---|---|
| 列表结构 | `LazyColumn(contentPadding=12dp, spacedBy=6dp)` + 每组件一张 `Card(insideMargin=16dp)`（`LicensesScreen.kt:78-117`） | `LazyColumn(pageContentPadding)` + 每库 `Card(padding(horizontal=12,top=12))` + `ArrowPreference`（`LicensePage.kt:105-131`） | 卡片横向 12 ✔；官方每行是 `ArrowPreference`（可点跳转源站），Glacier 是纯文本三段；官方有 `VerticalScrollBar` | 补链接/省略号；补 ScrollBar | 未整改（低） |
| GPL 正文 | 整段文本 `footnote2`（`LicensesScreen.kt:118-128`） | 官方只列库名/版本/许可证 + 链接（`LicensePage.kt:114-132`） | Glacier 渲染完整 GPL 文本（合规要求，合理），但每段 `padding(vertical=4)` 与分节间距不一致 | 保留（合规）；间距收口 | 已核对 |
| 顶部说明 | `Text(body2, padding vertical 8)`（`LicensesScreen.kt:83-90`） | 官方无 | 合法 | 保留 | 已核对 |
| 空/加载态 | 加载中显示空列表（`produceState` 初值 emptyList，`:60-69`） | 官方 `libraries ?: null` 时不渲染（`LicensePage.kt:68-78,114`） | 加载中无 `ProgressIndicator`；LICENSE.txt 读取失败时静默空列表 | 补加载态/错误态 | 未整改（低） |

### 5.6 ApiActivity（`feature/api/ApiActivity.kt`）

| 项 | 当前实现（file:line） | MIUIX 官方（file:line） | 具体差异 | 整改方案 | 状态 |
|---|---|---|---|---|---|
| 独立窗口 | `Theme.Glacier.Translucent`（`AndroidManifest.xml:60-65`）+ 自建 `ApiRoot`：`Scaffold(Transparent, insets=0)` + `Box(background(surface.copy(alpha=0.92f))).layerBackdrop(R)`（`ApiActivity.kt:296-325`） | 无官方"半透明独立窗口"范式 | 录制层内先 `drawRect(surface)`，而窗口外层是 92% 透明 surface → **玻璃采样到的是不透明 surface，不是透过窗口的真实下层**（L1 采样源错误，L3 必然偏差） | 背投改为窗口级真实内容（或接受并记录为 L3 不可达） | 未整改 |
| 操作单 | `AppActionSheet(show = entry != null)`（`ApiActivity.kt:71-92`） | 同 Home 的 `AppActionSheet` | 行/尺寸正确；`show` 条件组合 → 无退出动画；dismiss 直接 `finish()` | 常驻组合；关闭动画后再 finish | 未整改 |
| 错误 Dialog | `GlassDialog(show=true)` + 单按钮（`ApiActivity.kt:94-110`） | 官方 `GlassDialog`（`GlassDialog.kt:47-93`） | 参数正确（`backdrop = LocalRootBackdrop` 为本窗口 R）；`show=true` 无退出动画 | 常驻组合 | 未整改 |
| 无顶栏/标题 | 无 | 官方二级窗口无范式 | 可以接受，但窗口没有标题/返回 affordance | 视产品需要 | 已核对 |
| Toast | `UiFeedback.toast`（`UiFeedback.kt:15-27`） | MIUIX `Snackbar`（`miuix-ui/.../basic/Snackbar.kt`；Scaffold `snackbarHost` 槽 `Scaffold.kt:87`） | 系统 Toast，不跟主题 | 见 6.1 | 未整改 |

---

## 六、全局组件与状态

### 6.1 Toast / Snackbar

| 项 | 当前实现 | MIUIX 官方 | 差异 | 方案 | 状态 |
|---|---|---|---|---|---|
| 轻反馈 | `android.widget.Toast`（`platform/UiFeedback.kt:15-27`），全项目 20+ 处调用（Home/Apps/Settings/Api/Tile/Worker） | `Scaffold(snackbarHost = { SnackbarHost(state) })` + `SnackbarHostState.showSnackbar`（`example/.../ComponentPage.kt:66-73`、`component/SnackbarSection.kt`） | 系统 Toast 不跟主题、无 MIUIX 外观、无法在玻璃层上正确显示 | 页面内反馈改 `SnackbarHost`；Service/Worker/Tile 等无 Compose 上下文保留 Toast（写明原因） | 未整改 |

### 6.2 空状态 / 加载状态 / 错误状态

| 项 | 当前实现 | MIUIX 官方 | 差异 | 方案 | 状态 |
|---|---|---|---|---|---|
| 空状态 | 自绘 `Box+Text`（`PageFrame.kt:302-321`） | 无官方空态组件 | 可保留；但 Apps 空态未应用页面 padding（P0） | 修 Apps；其余保留 | 未整改 |
| 加载状态 | 无（数据同步读取，`repository.refresh()` 异步但无 loading UI） | `ProgressIndicator`（`miuix-ui`）可用 | 首次进入/刷新期间首页显示空态而非加载态；无法区分"空"与"未加载" | 引入仓库 loading 标志 + 官方 `ProgressIndicator` | 未整改 |
| 错误状态 | 仅 ApiActivity 用 `GlassDialog` 报错（`ApiActivity.kt:55,94-110`）；其它页面失败只 Toast | 官方无统一错误组件 | 冻结失败/后端不可用无页面级错误 UI | 统一错误出口（Snackbar/Dialog） | 未整改 |

### 6.3 启动与权限提示

| 项 | 当前实现 | MIUIX 官方 | 差异 | 方案 | 状态 |
|---|---|---|---|---|---|
| 启动校验 | `SessionLock`：`Column(padding 32dp)` + 40dp 图标 + `Button`（`SessionLock.kt:108-135`） | 官方无对应组件；`Button` 是官方（`miuix-ui/basic/Button.kt`） | 无品牌背景、无 MIUIX 卡片/插画；32/16/40dp 硬编码；生物识别失败只显示系统 errString | 可用官方 `Card`/`Text` 排一版；尺寸收口 | 未整改（低） |
| 系统权限缺失提示 | 一律 Toast（`HomeScreen.kt:582-593`、`AppActions.kt:127-135`、`SettingsScreen.kt:342-344`） | 无官方权限组件 | 用户不知道"哪种后端缺哪个权限、怎么修"；Settings 里工作模式提示也只是复制命令 | 增加 `GlassDialog` 权限说明 + 跳转 | 未整改（低） |

### 6.4 深浅色 / 字体 / 横竖屏 / Insets

| 项 | 当前实现 | MIUIX 官方 | 差异 | 方案 | 状态 |
|---|---|---|---|---|---|
| 深浅色 | `ThemeController(colorSchemeMode)`（`GlacierTheme.kt:17-31`）+ `UiModeManager.setApplicationNightMode`（`GlacierApplication.kt:54-62`）+ `values-night` 资源 | `MiuixTheme(controller)`（`example/ui/Theme.kt`） | Compose 侧正确；`windowBackground` 写死 `#FFF7F7F9` / `#FF000000`（`res/values/colors.xml:3`、`values-night/colors.xml:3`），不是 MIUIX surface token（冷启动/窗口背景可能与 Compose 首帧色不同） | 窗口背景改透明或对齐 MiuixTheme surface | 未整改（低） |
| 主题按钮栏图标 | 依赖 `values-night/themes.xml` 的 `windowLightStatusBar` | 无 | `setApplicationNightMode` 会切 app 级 night 资源，因此与 App 主题一致（已核对，不是 BUG） | 保留 | 已核对 |
| 大字体 | 库内组件处理（导航栏 `fontScale>=1.6` → LabelSize 16dp `GlassNavigationBar.kt:243-245`）；Glacier 自身风险：① Home 标签用 `fontSize = labelSize.sp`(+14sp) 覆盖 style；② 操作单 10 行不可滚动；③ 多选顶栏 5 个动作 + 32sp 标题 | 官方 `pageScrollModifiers` + `LazyColumn`；弹层内容 `LazyColumn`（`BottomSheetSection.kt:209`） | 大字体下未做任何实测；操作单可能被裁切（`AppActions.kt:161-239` 是普通 Column） | 弹层改 `LazyColumn`；标签字号用 `style.copy`；补 1.3/2.0 字体截图 | 未整改 |
| 横屏/宽屏 | 侧栏常驻 + 页面整体右移；顶栏双重横向 inset；仍显示 32sp 大标题；搜索结果/搜索条无侧栏让位 | `shouldShowSplitPane`（840 或 600+ratio）、`shouldExpandNavigationRail`（1200）、`appPageSearchInsets`（`PageUtils.kt:131-142`） | 见 4.2/4.3 与 7.3；横屏可用但布局不符合官方宽屏范式 | P0-3 | 未整改 |
| 搜索浮层宽屏 | 未传 `horizontalInsetsModifier`（`HomeScreen.kt:216-221`） | 官方 `Modifier.appPageHorizontalInsets()`（`IconPage.kt:518`、`GlassPage.kt:691`） | 宽屏展开侧栏后搜索条/结果被侧栏覆盖（侧栏在 Shell Box 中后绘制，z 序在 overlay 之上） | 传官方 inset modifier | 未整改 |
| Insets 汇总 | 状态栏：顶栏库内处理 ✔；导航栏：底栏库内处理 ✔；手势区：`pageContentPadding` 里额外 +24dp；IME：搜索浮层库内 `imePadding()`（`GlassSearchOverlay.kt:293`）✔ | 官方 `safeInsets/contentInsets` 统一 contract（`PageUtils.kt:63-117`） | 缺少统一 insets contract，各页面各自叠加 | 引入官方 `AppPageInsets` 等价物 | 未整改 |

### 6.5 动画

| 动画项 | 当前实现 | MIUIX 官方 | 差异 | 方案 | 状态 |
|---|---|---|---|---|---|
| Dialog 出现/消失 | 条件组合 + `show=true`（`HomeScreen.kt:265-319`、`ApiActivity.kt:94-110`） | `show` 驱动，300ms 退出（`DialogContentLayout.kt`） | **无退出动画**（组件立即卸载） | 常驻组合 + `show` 状态 | 未整改 |
| BottomSheet 出现/消失 | 同上（`HomeScreen.kt:254-263,321-332`、`AppsScreen.kt:114-130,207-274`、`AppActions.kt:155-241`） | 同上 | **无退出动画** | 同上 | 未整改 |
| 搜索展开/关闭 | 展开有（库内 400ms）；关闭瞬间消失（`HomeScreen.kt:216`） | 常驻组件，展开/收起都走 400ms（`GlassSearchOverlay.kt:215-245`） | 关闭动画丢失；顶栏淡出与浮层消失不同步（顶栏 400ms 淡出时浮层已没了） | 常驻组合 | 未整改 |
| 页面切换 | Compose 默认 pager spring（`GlacierShell.kt:92`） | `PagerNavigationSpringSpec` stiffness 322.2 / damping≈0.9（`PagerGestureUtils.kt:75-80`） | 曲线/时长不同；相邻页未预组合（`beyondViewportPageCount=0` vs 官方 4） | 接入官方 spring + 预组合 | 未整改 |
| 二级页转场 | `NavDisplay` 默认（dim 0.5，无圆角裁剪、无 backdropColor） | 官方 `NavDisplayEffects(cornerClipRadius=系统圆角, backdropColor=surface)`（`AppContent.kt:257-305`） | 缺屏幕圆角裁剪与背景填充 | 传官方 effects | 未整改 |
| 按压缩放 | 顶栏 `GlassIconButton` / FAB / 页签均官方组件自带 | 同左 | 一致 | 保留 | 已验证（L2） |
| 列表按压反馈 | Home 网格/搜索结果行用 foundation `clickable/combinedClickable`（`HomeScreen.kt:391`、`AppSearchResults.kt:79`） | MIUIX `Card(pressFeedbackType=Sink)` / `BasicComponent` 自带按压反馈（`Card.kt:110`、`Component.kt:138-163`） | 网格/搜索结果按压反馈不是 MIUIX 语义 | 改用官方组件或 `pressFeedbackType` 等价实现 | 未整改 |
| 滚动折叠 | `nestedScroll` 已接通（顶栏会收起大标题） | `pageScrollModifiers`（含 `overScrollVertical`、`scrollEndHaptic`） | 缺弹性 overscroll 与末端触感；`overscrollOffset` 恒 0 | 接入官方 modifier | 未整改 |
| 侧栏展开/收起 | `railStartInset` 在量测期读取（`PageFrame.kt:245-263`），不重组 | 官方同思路（`overlayStart` 由 layout 消费，`PageUtils.kt:104-114`） | 实现正确；但宽屏默认展开（无动画入口） | 保留实现 | 已验证（L1/L2） |

---

## 七、专项 1：搜索 × BUG 复核（读 `GlassSearchOverlay.kt` 源码）

### 7.1 官方组件的三个动作是分离的（源码证据）

| 动作 | 位置 | 回调/语义 |
|---|---|---|
| 输入框内容清空（条内 ×，仅 query 非空时出现） | `GlassSearchOverlay.kt:444-459` | `onClick = { query = ""; focusRequester.requestFocus() }`，图标 `contentDescription = "Clear"`，尺寸 24dp/图标 18.67dp |
| 取消搜索（条外 ×，胶囊 44dp） | `GlassSearchOverlay.kt:490-503` | `onClick = onDismissRequest`，`enabled = expanded`，图标 28dp |
| 系统返回 / Esc | `GlassSearchOverlay.kt:241-245`（`NavigationBackHandler`）、`:251-258`（Esc） | 均调 `onDismissRequest` |
| 点击浮层空白 | `GlassSearchOverlay.kt:261-266` | 只 `keyboard?.hide()`，**不关闭** |
| 组件生命周期 | `GlassSearchOverlay.kt:229-240` | `active = expanded \|\| transition.currentState \|\| transition.isRunning`；`if (!active) return` → **官方要求调用方常驻组合**，由 `expanded` 控制收起动画 |

### 7.2 Glacier 接线核对

| 检查点 | 结论 |
|---|---|
| 是否存在"关闭 × 打开标签编辑" | **当前代码不会**。`onDismissRequest`（`HomeScreen.kt:219`）只改 `searchOpen`；标签编辑由 `tagEditorOpen`（`:107`）与顶栏第 3 个动作（`:174-176`）驱动，两者无共享状态/回调 |
| 命中测试重叠 | 搜索浮层在 `PageFrame.kt:165` 位于 `Scaffold` 之后的 `Box` 子节点；`GlassSearchOverlay` 全屏且自带全屏拦截层（`GlassSearchOverlay.kt:261-266`）；顶栏在浮层下方，**不会**接收点击 |
| 是否有回调复用/状态耦合 | 无。Home 与 Apps 各自独立 `searchOpen`；清空/取消由库内部区分，调用方只提供 `onDismissRequest` + content |
| 历史根因是否仍可复现 | 若把 `overlay()` 移回 `Scaffold { }` body 槽（旧实现），因 `Scaffold.kt:354-379` 的 `body → topBar` 绘制/命中顺序，BUG 立即复发；当前结构约束只写在注释（`PageFrame.kt:74-83`），无编译期保护 |
| 残留问题 1 | `if (搜索态) GlassSearchOverlay(expanded = true)` → 关闭瞬间组件被移除，**退出动画被截断**；顶栏仍在 400ms 淡出，出现"顶栏淡出而浮层已消失"的跳变（P0-4） |
| 残留问题 2 | 搜索态顶栏只做 `graphicsLayer{alpha}` 淡出 + `clearAndSetSemantics`（`PageFrame.kt:118-124`），没有 `Modifier.pointerInput` 禁用；实际能工作是因为浮层盖住全屏。若将来浮层不满屏（例如分屏/内嵌），穿透会复现 |
| 残留问题 3 | `GlassSearchOverlay` 未传 `topInsets/searchTopPadding/horizontalInsetsModifier`（`IconPage.kt:515-518`、`GlassPage.kt:688-691`），宽屏侧栏会盖住搜索条 |

### 7.3 修复/验收建议（供实现成员直接执行）

```kotlin
// 1) 常驻组合，交给 expanded 驱动进出动画
GlassSearchOverlay(
    expanded = searchOpen,                 // 不要 if (searchOpen) 包裹
    onDismissRequest = { searchOpen = false },
    searchTopPadding = { LocalAppPageInsets.searchTop() },
    horizontalInsetsModifier = Modifier.appPageHorizontalInsets(),
    scrollMaterialThreshold = GlassSearchOverlayDefaults.VerticalPadding + 4.dp,
    scrollOffset = { searchListState.firstVisibleItemScrollOffset.toFloat() },
    searchIcon = MiuixIcons.Glass.Search,
    closeIcon = MiuixIcons.Glass.Close,
) { query -> … }

// 2) 页面骨架：overlay 保持在 Scaffold 之后（当前正确），并在文件头标注"不得移入 body 槽"的契约
```

验收步骤（可复现）：搜索→点条外 ×（关闭且不打开标签编辑）→ 再开→输入→点条内 ×（只清空、不关闭）→ 系统返回关闭 → 切到 Apps 再搜索 → 横屏搜索（不被侧栏遮挡）→ 旋转后关闭再开。

---

## 八、专项 2：图标系统

| 项 | 当前实现（file:line） | MIUIX/官方对应 | 具体差异 | 方案 | 状态 |
|---|---|---|---|---|---|
| 第三方应用图标获取 | `AppIconSource.load`（`AppIcons.kt:44-58`）：图标包 → 系统图标 | 无官方应用图标组件 | 图标包 `BitmapFactory.decodeResource` 无采样（`AppIcons.kt:130`），大图标包可能 OOM；缓存按 `package@size` 但解码用原始尺寸 | 加 `inSampleSize`/`inScaled=false` 与尺寸上限 | 未整改（低） |
| Adaptive Icon 去遮罩 | `renderUnmasked`：直接把 `background/foreground` 画到 sizePx 画布（`AppIcons.kt:60-93`） | Android `AdaptiveIconDrawable` 默认 `draw()` 会套系统遮罩（AOSP 圆形），所以不能用；MIUIX 无对应实现 | 思路正确：保留 108/72 安全区比例，再由 UI 套 squircle（旧"圆形图标"根因已消除）；但 `foreground.setBounds(0,0,size,size)` 对 `AdaptiveIconDrawable` 内部 layer 的 inset 语义依赖实现，未在多个 OEM 图标上验证 | 保留；补 3 种系统图标 + 图标包实测 | 已整改（待复验） |
| squircle 轮廓 | `AppIconCornerRatio = 0.22f`（`AppComponents.kt:25`），`squircleClip(size*ratio)`（`:73`） | `Modifier.squircleClip(cornerRadius, extension = SquircleDefaults.Extension=1.1f)`（`SquircleBackground.kt:96-99`、`SquirclePath.kt:17`）；无 shader 时自动退回 `RoundedCornerShape`（`SquircleBackground.kt:88-99`） | API 用法正确；0.22 是自定义常量（MIUIX 无 app icon token）；与 About 品牌块 18/80=0.225 一致 | 保留并集中常量 | 已整改（待复验） |
| Home 网格 vs Apps 列表一致性 | Home：56dp 图标/`squircleClip`；Apps：40dp `AppRow`；搜索：40dp（`HomeScreen.kt:396`、`AppComponents.kt:135`、`AppSearchResults.kt:84`） | 无官方应用行 | 同一图标渲染路径（`AppIconView`）✔，圆角比例一致 ✔；但标签排版不一致：Home `footnote1`+14sp 覆盖（13sp style），Apps 行 `body1` 16sp（BasicComponent title） | 统一文字层级（Home 用 footnote1 或统一 14sp；Apps 保持一致） | 未整改（低） |
| 品牌图标（Glacier 自身） | `ic_launcher_background.xml` 白 `#FFFFFFFF`；`ic_launcher_foreground.xml` 黑 `#FF000000`；`ic_brand_mark.xml` 同前景；monochrome 复用前景（`mipmap-anydpi-v26/ic_launcher.xml:5`） | 本轮要求"白底黑主体" | 配色 ✔；但构图只占 108 视口 46.8dp（43%），各尺寸显示偏小（About 56dp 图片内徽标约 24dp）；monochrome 用描边 path 可用但建议改为实心路径更稳 | 重绘构图铺满安全区；monochrome 改实心 | 未整改 |
| 图标着色 | `Icon(bitmap, tint = Color.Unspecified)`（`AppComponents.kt:66-76`） | 官方 `Icon(painter, tint=null)` 表示不染色（`Icon.kt:170-191`） | 正确保留第三方原色 | 保留 | 已验证（L2） |
| 占位图 | `squircleBackground(surfaceContainerHigh, radius)`（`AppComponents.kt:77-84`） | `squircleBackground`（`SquircleBackground.kt:51-55`） | 正确 | 保留 | 已验证（L2） |
| 选中环尺寸 | 见 5.1 `HomeAppItem` 行 | — | 选中时图标内容 56→52dp 跳变 | 改用外描边不改布局 | 未整改 |

---

## 九、专项 3：Glass 架构（录制层 / 采样源 / 消费者）

### 9.1 录制层结构（当前真实树）

```
Root: Scaffold(Transparent, insets=0)                       [GlacierShell.kt:157-161]  弹层宿主
└ Box
  ├ Box.layerBackdrop(R)  R = drawRect(surface)+drawContent  [GlacierShell.kt:129-132,163-167]
  │ └ NavDisplay.railStartInset                                [GlacierShell.kt:168-206]
  │   └ HorizontalPager(beyondViewportPageCount=0)             [GlacierShell.kt:181-184]
  │     └ PageFrame                                             [PageFrame.kt:113-166]
  │       ├ Scaffold.topBar → GlassTopAppBar(backdrop=P)       [PageFrame.kt:117-137]
  │       │  └ bottomContent → GlassTabRow（取顶栏上下文 P）    [HomeScreen.kt:420-451]
  │       ├ Scaffold.fab → GlassFAB(backdrop=P)                [PageFrame.kt:139-146]
  │       ├ Scaffold.body → Box.layerBackdrop(P)               [PageFrame.kt:148-153]
  │       └ overlay()（搜索/弹层调用点）                        [PageFrame.kt:165]
  ├ GlassNavigationBar(backdrop=R)                             [GlacierShell.kt:224-231]
  └ GlassNavigationRail(backdrop=R)                            [GlacierShell.kt:211-222]
弹层实际绘制：根 Scaffold 的 MiuixPopupHost（renderInRootScaffold=true）
ApiActivity：自己的 R'（drawRect(surface)+content），窗口背景 0.92 alpha 在外侧  [ApiActivity.kt:296-325]
```

### 9.2 消费者与采样源（逐项判定）

| 消费者 | 位置 | 采样源 | 采样源是否包含消费者 | 结论 |
|---|---|---|---|---|
| `GlassTopAppBar` | 页面 Scaffold.topBar | P | 否（P 与顶栏同父） | 安全（L1） |
| `GlassTabRow` | 顶栏 bottomContent | 顶栏上下文 → P（`GlassTabRow.kt:309-312`） | 否 | 安全（L1）；官方推荐位置 |
| 顶栏 `GlassIconButton` | 顶栏子树 | 顶栏材质上下文（`GlassIconButton.kt:70-76,81-96`） | 否 | 安全 |
| `GlassFloatingActionButton` | 页面 Scaffold.fab | P | 否（同父） | 安全（L1）；位置数值错（P0-2） |
| `GlassNavigationBar` | Shell Box 第二子节点 | R | 否（R 的兄弟） | 安全（L1）；宽度/让位错（P0-1/2） |
| `GlassNavigationRail` | Shell Box 第三子节点 | R | 否 | 安全（L1）；页面整体位移导致 R 在侧栏区域只有 surface（P0-3） |
| `GlassDialog` / `GlassBottomSheet` | 页面 overlay 调用点，实际根 popup 宿主 | R（`LocalRootBackdrop`） | 否（popup 在 R 之外） | 结构安全；**采样内容不完整**：官方要求 backdrop "include the page and bars"，R 不含底栏/顶栏？顶栏在 NavDisplay 内所以 R 含顶栏；R 不含底栏（底栏是 Shell Box 的兄弟）→ 弹层底部区域采不到底栏像素（L3 偏差） |
| `GlassDropdownPreference` 弹层 | 设置行内触发，实际根 popup 宿主 | R（`SettingsScreen.kt:66,120`） | 否 | 结构安全；同样缺底栏像素（L3） |
| `GlassSearchOverlay` | 页面 overlay（在 R 子树内，但自建 layer） | 自建 `contentBackdrop`（`GlassSearchOverlay.kt:268,298`） | 否（搜索条在 content layer 外） | 安全（L1/L2）；宽屏未被侧栏让位 |
| ApiActivity 弹层 | 本窗口 R' | R'（`ApiActivity.kt:301-309`） | 否 | 结构安全；但 R' 录的是不透明 surface，窗口是 92% 透明 → 与实际视觉不符（L1 采样语义错） |

### 9.3 父/子采样风险清单

1. **`topBarBottom` 放 `GlassTabRow` 是安全的**，原因与旧文档一致但证据要写清：`PageFrame` 的 P 只录 Scaffold **body**，顶栏与 P 是不同子槽（兄弟）；`GlassTabRow` 再从 `LocalGlassTopAppBarContext` 取顶栏 backdrop（`GlassTabRow.kt:309-312`），消费者不在 P 的子树里。**反例**：官方 `MainPage` 把 `pageBackdrop` 录在整个 Scaffold 上（`MainPage.kt:203`）时，顶栏用另一个 `backdrop` 层（`MainPage.kt:205`）以避免自采样——若 Glacier 将来把 `layerBackdrop` 移到 PageFrame 的 Scaffold 上，必须同时换掉顶栏/FAB 的采样源，否则 `prepareTreeImpl` 递归崩溃会复发。
2. **页面 overlay 在 R 的子树里**：当前只有非 Glass 内容（搜索用自建 layer、弹层走根 popup），所以安全；若将来在 overlay 里直接放一个采样 R 的玻璃组件，就是自采样。
3. **宽屏：侧栏绘制在页面之上**（Shell Box 后序），页面 overlay（搜索）在侧栏之下 → 搜索条被侧栏覆盖/吃掉点击（P0-3）。
4. **R 的坐标空间**：R 在分页容器外、Shell Scaffold 内，与根 Scaffold 同坐标；弹层弹窗宿主在根 Scaffold 的 popup 槽，坐标一致 → 无"跨页差一页宽"问题（旧文档此结论成立，源码可证：`GlacierShell.kt:163-175` 与 `Scaffold.kt:379`）。
5. **Rail 让位在量测期读取**：`railStartInset`（`PageFrame.kt:245-263`）符合官方 contract 第三条，保留。
6. **模拟器上 `progressiveBlur=false`**（`PageFrame.kt:188,192`）：截图里的顶栏是 band 渐变而非 progressive blur，任何"观感已对齐"的结论都只能算 L2。

### 9.4 L1/L2/L3 结论

| 项 | 等级 | 说明 |
|---|---|---|
| 采样源/兄弟关系 | L1 通过（除 ApiActivity 窗口采样语义、弹层 backdrop 不含底栏） | 见 9.2 |
| 组件挂载/绘制/交互 | L2 通过（截图 01–23 + 交互路径） | 但布局/尺寸/动画问题不影响 L2 判定 |
| 真实光学（折射/模糊/边缘光/深色亮度） | **L3 未验证** | SwiftShader；`progressiveBlur` 关闭；无真机证据 |

---

## 十、硬编码清单（文件:行:值 → 官方可替换 token）

### 10.1 尺寸类

| 文件:行 | 值 | 官方对应 / 建议 |
|---|---|---|
| `ui/components/PageFrame.kt:53` | `Horizontal = 12.dp` | 与官方卡片 12dp 一致，保留为唯一常量；Home/Apps 的 16dp 要改回它 |
| `PageFrame.kt:56` | `SectionGap = 6.dp` | 官方 `GroupedCardItems.outerBottomPadding = 6.dp`（`GroupedCardItems.kt:70`） |
| `PageFrame.kt:59` | `CardCorner = CardDefaults.CornerRadius` | 正确用法（但**该常量全项目未使用**） |
| `PageFrame.kt:143` | `(reserve - 20.dp)` | 魔法值，官方无；删除（P0-2） |
| `PageFrame.kt:223,232` | 顶栏图标 `24.dp` | 玻璃档官方 28dp（`MainPage.kt:226`、`BackNavigationIcon.kt:37`） |
| `PageFrame.kt:270-278` | `+ 24.dp`、`navReserve` 相加 | 官方无此加法；官方 `maxOf` 合并（`PageUtils.kt:185-222`） |
| `PageFrame.kt:311` | `EmptyState` padding 48/24 | 官方无空态组件；保留但收口常量 |
| `GlacierShell.kt:148` | `+ 32.dp` | 官方无；删除（P0-2） |
| `GlacierShell.kt:124` | `0.dp` | 默认值，可留 |
| `HomeScreen.kt:338` | 横向 `16.dp` | 官方 12dp（`MainPage.kt:312`） |
| `HomeScreen.kt:339-340` | `spacedBy(6, 18)` | 官方无网格；自定义，写注释 |
| `HomeScreen.kt:390` | `RoundedCornerShape(18.dp)` | 应为官方 squircle 或去掉 |
| `HomeScreen.kt:392` | `padding(vertical=4, horizontal=2)` | 自定义；收口 |
| `HomeScreen.kt:396` | 图标 `56.dp` | 自定义（无官方 token） |
| `HomeScreen.kt:400` | `Spacer(6.dp)` | 自定义 |
| `HomeScreen.kt:406` | `fontSize = labelSize.sp` | 应 `style.copy(fontSize=…)` |
| `HomeScreen.kt:433` | `padding(12,12, bottom=8)` | 官方 `padding(horizontal=12).padding(top=6,bottom=12)`（`GlassPage.kt:398`） |
| `HomeScreen.kt:476,481` | `48.dp` / `20.dp` | `IconButton` 默认 40dp（`IconButton.kt:84-89`） |
| `HomeScreen.kt:493,527,564` | `Spacer(8/12.dp)` | 官方弹层内容间距 12dp（`BottomSheetSection.kt:220`） |
| `AppsScreen.kt:155` | 横向 `16.dp` | 12dp |
| `AppsScreen.kt:156` | `spacedBy(8.dp)` | 6dp（分组）/12dp（官方弹层） |
| `AppsScreen.kt:179` | `insideMargin = PaddingValues(0.dp)` | 默认值，冗余 |
| `AppsScreen.kt:239,271` | `Spacer(12.dp)` | 12dp 合规 |
| `SettingsScreen.kt:223,279` | `"${…} sp"` / `"${…} min"` 值文案 | 官方 `valueText` 允许；可留 |
| `SettingsScreen.kt:395` | `Spacer(24.dp)` | P0-2 后删除 |
| `AboutScreen.kt:135` | `padding(12,12,top=12)` | 收口 |
| `AboutScreen.kt:139` | `Spacer(24.dp)` | 收口 |
| `AboutScreen.kt:155` | `padding(top=16,bottom=8)` | 收口 |
| `AboutScreen.kt:157` | `spacedBy(2.dp)` | 收口 |
| `AboutScreen.kt:161-162` | `size(80.dp)` / `squircleBackground(Color.White, 18.dp)` | 官方 88dp / `RoundedCornerShape(24.dp)`（`AboutPage.kt:245-249`） |
| `AboutScreen.kt:168` | `size(56.dp)` | 官方 74dp（`AboutPage.kt:257`） |
| `AboutScreen.kt:175` | `padding(top=12.dp)` | 官方 `top=12,bottom=5`（`AboutPage.kt:263`） |
| `LicensesScreen.kt:88,114,126` | `8/4/4.dp` | 收口到 `PageMetrics` |
| `LicensesScreen.kt:97` | `PaddingValues(16.dp)` | 与 `BasicComponentDefaults.InsideMargin`（`Component.kt:292`）一致，建议直接引用 |
| `AppActions.kt:231,236,238` | `8/16/12.dp` | 收口 |
| `AppComponents.kt:25` | `AppIconCornerRatio = 0.22f` | 无官方 token；集中常量（与 About 18/80 比值一致） |
| `AppComponents.kt:57-61` | 选中环 `2.dp`/`radius+2` | 改为外描边不改布局 |
| `AppComponents.kt:112` | 操作单图标 `24.dp` | 与官方 `BasicComponent` 行图标 24dp 一致 |
| `AppComponents.kt:135` | 列表图标 `40.dp` | 自定义 |
| `AppSearchResults.kt:68-84` | `+8 / 16 / 16 / 32 / 2 / 4,10 / 14 / 40.dp` | 官方 `Card+BasicComponent` + `pageBottomPadding`（`IconPage.kt:221-244,537-540`） |
| `GlassEnvironment.kt:38` | `LocalRailInset` 默认 0 | **未被消费**（Shell 直接传 lambda），死代码 |
| `GlassEnvironment.kt:45` | `LocalNavReserve` 默认 0 | P0-2 后删除 |
| `SessionLock.kt:114,115,120` | `16/32/40.dp` | 收口 |
| `ApiActivity.kt:318` | `surface.copy(alpha = 0.92f)` | 自定义窗口透明度；收口并记录 L3 偏差 |

### 10.2 颜色 / 字体

| 文件:行 | 值 | 说明 |
|---|---|---|
| `ui/components/PageFrame.kt:59`、`HomeScreen.kt`、`AppsScreen.kt` 等 | 全部用 `MiuixTheme.colorScheme.*` | ✅ 没有硬编码主题色（除下列） |
| `feature/about/AboutScreen.kt:162` | `Color.White` | 品牌块白底（本轮要求，合理；建议写常量注释） |
| `ApiActivity.kt:312,318` | `Color.Transparent` / `0.92f` | 窗口透明/半透明 |
| `GlacierShell.kt:159` | `Color.Transparent` | 根 Scaffold 透明容器 |
| `res/values/colors.xml:3` | `#FFF7F7F9` | 建议改透明或对齐 MIUIX surface |
| `res/values-night/colors.xml:3` | `#FF000000` | 同上 |
| `HomeScreen.kt:406` | `fontSize = 14.sp`（默认值来自 `GlacierPreferences.kt:82`） | 与 `footnote1`(13sp) 双源 |
| `res/drawable/ic_brand_mark.xml:11,16,20…`、`ic_launcher_foreground.xml` | `#FF000000` | 品牌黑，合规 |
| `AboutPage.kt`（官方参考） | 品牌标题 35sp Bold、版本 14sp（`AboutPage.kt:263-303`） | Glacier 用 `title1`(32sp)/`footnote1`(13sp)，可对齐 |

### 10.3 动画时长（Glacier 自己写的）

| 文件:行 | 值 | 说明 |
|---|---|---|
| `PageFrame.kt:190-194` | `glassSearchBarTransition(blurEnabled=!isEmulator)` | 官方时长由库固定（400/150ms，`GlassSearchOverlay.kt:215-223`） |
| 其余 | **Project 内没有任何自写时长**，动画全部来自官方组件或默认 | 问题不是"时长写错"，而是"退出动画根本没播放"（条件组合） |

---

## 十一、自造实现 / 普通 Compose 模拟 MIUIX 清单

| 自造实现 | 位置 | 为什么算"手工模拟" | 官方/更优实现 | 建议 |
|---|---|---|---|---|
| Home 网格单元 | `HomeScreen.kt:378-412` | `Column + clip(RoundedCornerShape) + combinedClickable + 手写 padding` | MIUIX 无启动器网格；但按压反馈/形状应用官方 squircle + 统一 press feedback | 保留布局，换 press feedback 与形状（P1） |
| 搜索结果行 | `AppSearchResults.kt:39-111` | `Row + foundation clickable + 手写 padding/spacing` | 官方结果为 `Card + BasicComponent`（`IconPage.kt:221-244`） | 直接替换（P1） |
| 标签编辑弹层行 | `HomeScreen.kt:469-495` | `BasicComponent + endActions IconButton` 自组合，且删除入口缺失 | 官方弹层用 `SmallTitle + Card + 行`（`BottomSheetSection.kt:209-267`） | 补 Card 分组与删除入口（P1） |
| 排序弹层行 | `AppsScreen.kt:219-238` | 选中/未选中两种行结构 | 官方离散选项用 `GlassDropdownPreference` 或统一行 + 尾部 Ok | 统一行结构（P1） |
| 操作单 | `AppActions.kt:155-241` | 10 行 `BasicComponent` 平铺，无分组/无滚动 | 官方弹层 = `LazyColumn + SmallTitle + Card`（`BottomSheetSection.kt:209-267`） | 分组 + `LazyColumn`（P1） |
| 空状态 | `PageFrame.kt:302-321` | 自绘 Box+Text | 无官方组件 | 保留（合理） |
| About 品牌区 | `AboutScreen.kt:150-183` | 自绘 Column + squircleBackground | 官方 About 用 `Box+graphicsLayer clip+Image` 且带滚动淡出（`AboutPage.kt:233-305`） | 尺寸对齐 + 动画（P1） |
| 启动校验页 | `SessionLock.kt:108-135` | 自绘锁屏 | 无官方组件 | 可用官方 Card/Text 重排（P2） |
| ApiActivity 根容器 | `ApiActivity.kt:296-325` | 自绘 Scaffold+Box+alpha 背景 | 无官方"半透明窗口"范式 | 保留，但修正采样语义（P2） |
| 页面骨架 `PageFrame` | `PageFrame.kt:84-167` | 自造"页面骨架 + 顶部槽 + FAB 让位 + overlay" | 官方页面骨架是 `Scaffold(topBar) + Box(layerBackdrop) + LazyColumn(pageScrollModifiers)`，底栏/FAB 在 compact Scaffold（`AppContent.kt:637-683`） | 重构骨架（P0-2） |
| `pageContentPadding` | `PageFrame.kt:266-280` | 与官方同名函数语义不同（相加 vs maxOf） | `PageUtils.kt:185-222` | 换成官方语义（P0-2） |
| `GroupCard` | `PageFrame.kt:288-300` | 官方 `Card` 默认 `insideMargin=0`，`insideMargin=0.dp` 是冗余；结构本身正确 | `Card`（`Card.kt:187-199`） | 保留，删冗余参数 |

**结论**：项目没有引入 Material3（`app/build.gradle.kts` 依赖里明确不含 material3），这是加分项；"手工模拟"集中在**列表行/搜索结果/弹层/网格/页面骨架**五类，都能用官方组件或官方示例范式替换。

---

## 十二、页面之间同控件不一致清单

| 控件 | 页面 A | 页面 B | 不一致 | 建议 |
|---|---|---|---|---|
| 页面横向留白 | Home 16dp（`HomeScreen.kt:338`）、Apps 16dp（`AppsScreen.kt:155`） | Settings/About/Licenses 12dp（`PageFrame.kt:269` 默认） | 差 4dp | 全部 12dp |
| 分节标题左边距 | Settings/About：12+28=40dp | 官方 28dp | +12dp | 分节标题不叠内容 padding |
| 卡片间距 | Apps 8dp；Settings/About/Licenses `SectionGap=6dp`；官方 MainPage 卡片 bottom 12dp | — | 6/8/12 三种 | 统一 6（分组内）/12（分节） |
| 标签页签高度 | 玻璃 40dp（`GlassTabRow.kt:117`） | 非玻璃 42dp（`TabRow.kt:556`） | 切换玻璃开关时 2dp 跳动 | 固定同高容器 |
| 顶栏动作图标 | 玻璃 24dp（`PageFrame.kt:223`） | 官方 28dp | 小 4dp | 28dp |
| 应用图标 | Home 56dp | Apps/搜索 40dp | 尺寸不同（可接受），但标签字体 13/14 vs 16sp 不一致 | 统一文字层级 |
| 应用行点击语义 | Home 网格点击=启动 | Apps 行点击=操作单；搜索行 Home=启动、Apps=操作单 | 同一图标控件三种点击语义 | 产品统一 |
| 弹层按钮布局 | Dialog 按钮垂直 0dp（Home） | 官方横向 12dp（`GlassPage.kt:422-436`） | 0 vs 12dp | 统一 12dp |
| 底栏让位 | 窄屏手算 110dp | 宽屏 `LocalNavReserve=0`（`GlacierShell.kt:155`） | 两个分支两套规则 | 统一 Scaffold bottomBar |

---

## 十三、保留自定义实现及理由（需在整改后保留此节）

| 实现 | 位置 | 保留理由（成立/待补理由） |
|---|---|---|
| `EmptyState` | `PageFrame.kt:302-321` | MIUIX 无空状态组件；只用到官方 textStyle/color，没有自造设计 token。**但间距需收口** |
| 应用网格 | `HomeScreen.kt:334-375` | MIUIX 是组件库不是启动器；`LazyVerticalGrid` 是 Compose 官方能力，图标轮廓已换官方 squircle |
| 多选模式 | `HomeScreen.kt:127,136-166,352-370` | MIUIX 无多选范式；但顶栏动作数/底栏行为需要重新设计，不能算"符合 OS4" |
| `AppIconSource` 图标加载/去色/图标包 | `platform/AppIcons.kt` | 业务能力（设置项），不属于 MIUIX 组件范畴；只替换"如何得到位图" |
| ApiActivity 独立窗口 | `feature/api/ApiActivity.kt` | 外部 API 入口需要独立窗口；结构上自建录制层与弹层宿主，但**采样语义要修** |
| `PageMetrics` | `PageFrame.kt:51-60` | 官方无页面级 spacing token；目前 3 个常量，其中 `CardCorner` 未使用、`Horizontal` 未被 Home/Apps 遵守 |

---

## 十四、按优先级排序的整改清单（P0/P1/P2）

> 领取方式：按编号顺序做；每个 P0 完成后必须回到本文对应行更新状态，并在 `docs/verification/miuix-os4-r2/` 之外新建本轮截图目录。

### P0（阻塞"MIUIX OS4 对齐"结论，必须先做）

| # | 问题 | 涉及文件 | 动作 | 验收 |
|---|---|---|---|---|
| P0-1 | 底栏全宽、无 24dp 水平内边距；目的地宽 131.7 vs 115.7dp | `ui/GlacierShell.kt:224-231` | 方案 A：把 `GlassNavigationBar` 移入页面级 `Scaffold.bottomBar`（官方 `AppContent.kt:648-683`），并加 `.fillMaxWidth().padding(horizontal = 24.dp)`；方案 B（过渡）：先在调用处补 `.padding(horizontal=24.dp)` | 截图测图标中心 = 89.9/205.7/321.5dp（411dp 屏） |
| P0-2 | 手动让位：页面底部多 68/104dp；FAB 高 24/60dp；`reserve-20`/`+32`/`+24` 魔法值 | `ui/components/PageFrame.kt:139-146,266-280`、`ui/GlacierShell.kt:147-148,155`、`ui/GlassEnvironment.kt:45` | 删除 `LocalNavReserve` 与加法；改 Scaffold `bottomBar` + 官方 `pageContentPadding`（maxOf 语义）；删除 `+24.dp` | 页面底部 padding = 90dp（手势）/102dp（三键）；FAB 底距 = 102/114dp |
| P0-3 | 宽屏：侧栏常驻展开、页面整体右移导致 rail 采样空 surface；顶栏双重横向 inset（横屏实测标题左缘 389.7dp / 卡片左缘 333.7dp，官方关系应为 26dp vs 12dp，多出约 42~48dp ≈ 一个横向 displayCutout inset）；宽屏仍是 32sp 大标题；搜索条无侧栏让位；二级页侧栏高亮错误 | `ui/GlacierShell.kt:103-126,168-232`、`ui/components/PageFrame.kt:181-198`、`ui/GlassEnvironment.kt:47-52`、`feature/home/HomeScreen.kt:216-221`、`feature/apps/AppsScreen.kt:97-102` | ① 断点对齐 `AdaptiveUtils.kt:13-22/35-42`；② 页面表面满宽，仅内容/顶栏内容让位（`AppPageInsets` 语义）；③ 顶栏 `defaultWindowInsetsPadding=false` + 横向只算一次；④ `expandable=!isWideLayout()`；⑤ 搜索传 `horizontalInsetsModifier`；⑥ 侧栏 `secondaryItems`/`selectedIndex=-1` | 横屏：标题左缘=卡片左缘+14dp；rail 后能看到页面内容；1200dp 以下为折叠胶囊 |
| P0-4 | 搜索浮层条件组合 + `expanded=true` → 无收起动画；顶栏淡出与浮层消失不同步 | `feature/home/HomeScreen.kt:215-231`、`feature/apps/AppsScreen.kt:96-112`、`ui/components/PageFrame.kt:165` | 常驻组合 `GlassSearchOverlay(expanded = searchOpen, …)`；补 `scrollMaterialThreshold/scrollOffset/topInsets/searchTopPadding/horizontalInsetsModifier/Glass 图标` | 关闭时 400ms 收起 + 顶栏同步淡入；点 × 不打开标签编辑；横屏不被侧栏挡 |
| P0-5 | Apps 空态未应用 `padding` → 文本被顶栏覆盖 | `feature/apps/AppsScreen.kt:149-151` | `EmptyState` 放进 `Box(Modifier.padding(pageContentPadding(padding)))` 或重建 LazyColumn | 空列表截图文本垂直居中/不被顶栏压住 |

### P1（观感/交互一致性，P0 后立刻做）

| # | 问题 | 涉及文件 | 动作 |
|---|---|---|---|
| P1-1 | 玻璃/非玻璃弹层、Dialog、BottomSheet 全部无退出动画（`show=true` 条件组合） | `HomeScreen.kt:254-263,265-319,321-332,453-496`、`AppsScreen.kt:114-130,207-274`、`AppActions.kt:155-241`、`ApiActivity.kt:71-110` | 改为常驻组合 + `show` 状态（官方模式 `GlassPage.kt:405-437`） |
| P1-2 | 页签 padding 缺 top 6/bottom 12；玻璃 40 vs 非玻璃 42dp → 切玻璃开关跳 2~6dp | `HomeScreen.kt:420-451` | 对齐 `GlassPage.kt:394-401`；非玻璃档固定 40dp 容器 |
| P1-3 | 顶栏动作 24dp（官方 28dp）、无 8dp spacer、返回图标无 RTL 镜像 | `PageFrame.kt:211-237`、`AboutScreen.kt:63-65`、`LicensesScreen.kt:74-76` | 对齐 `MainPage.kt:212-272`、`BackNavigationIcon.kt:34-44`；提取 `BackAction` |
| P1-4 | 分节标题 40dp vs 官方 28dp；Home/Apps 16dp vs 12dp；卡片间距 6/8/12 混用 | `PageFrame.kt:53,269`、`HomeScreen.kt:338`、`AppsScreen.kt:155-156`、`SettingsScreen.kt:115`、`AboutScreen.kt:135`、`LicensesScreen.kt:81` | 统一 `PageMetrics.Horizontal=12`，分节标题绝对 28dp；`SectionGap` 与官方 6/12 二档 |
| P1-5 | 搜索结果行自绘（无卡片、4dp 内边距、40dp 图标、take(50)） | `feature/common/AppSearchResults.kt:39-111` | 换官方 `Card + BasicComponent`（`IconPage.kt:221-244`）；补 `pageBottomPadding` |
| P1-6 | 排序/操作单/标签编辑弹层行结构不一致、无分组、无滚动（大字体溢出） | `AppsScreen.kt:207-274`、`AppActions.kt:155-241`、`HomeScreen.kt:453-496` | 统一 `LazyColumn + SmallTitle + Card + BasicComponent`；操作单加滚动 |
| P1-7 | Dialog 按钮垂直 0dp 间距、空白输入无反馈 | `HomeScreen.kt:293-319,498-542` | 按钮 `Row(spacedBy(12))` + `weight(1f)` 或 `Column(spacedBy(12))`；空输入禁用确认 |
| P1-8 | 标签删除入口不可达（`onDelete` 死参数） | `HomeScreen.kt:254-263,453-496`（声明 `:459`） | 每行补删除动作（尾部 IconButton/长按菜单），或改 `GlassDialog` 二次确认 |
| P1-9 | 缺 `pageScrollModifiers`（overscroll 弹性 / scrollEndHaptic）；缺 `VerticalScrollBar` | `PageFrame.kt:157`、全部列表页 | 接 `PageUtils.kt:168-183` 等价实现；按官方示例补 ScrollBar |
| P1-10 | 页面切换 spring 与预组合页数不符 | `GlacierShell.kt:88-99,181-184` | 接 `PagerNavigationSpringSpec`（`PagerGestureUtils.kt:75-80`），`beyondViewportPageCount ≥ 1` |
| P1-11 | 二级页转场缺系统圆角裁剪/`backdropColor` | `GlacierShell.kt:168-206` | 传 `NavDisplayEffects`（`AppContent.kt:257-305`）、`swipeDismiss` |
| P1-12 | About 品牌区 80/18/56dp vs 官方 88/24/74dp，标题 32sp vs 35sp，缺滚动淡出动画；About 顶栏应不可展开 | `AboutScreen.kt:60-65,150-183` | 尺寸对齐 `AboutPage.kt:233-305`；PageFrame 加 `expandable` |
| P1-13 | Toast 而非官方 Snackbar | `platform/UiFeedback.kt:15-27` + 各页面 | 页面内改 `SnackbarHost`；后台上下文保留 Toast 并注释 |
| P1-14 | 图标选中环导致 56→52dp 跳变、描边与轮廓不平行 | `feature/common/AppComponents.kt:51-76` | 描边改 `drawBehind` 外扩，不改变内容尺寸 |
| P1-15 | Apps 无多选、与 Home 能力不一致；Home 多选顶栏 5×44dp 过挤 | `AppsScreen.kt` 全局、`HomeScreen.kt:136-166` | 产品确认后补 Apps 多选；多选动作合并进 ⋮ 弹层 |

### P2（收口 / 一致性 / 稳健性）

| # | 问题 | 涉及文件 | 动作 |
|---|---|---|---|
| P2-1 | 硬编码 dp 全面收口（第十节表格） | 第十节全部行 | 抽 `PageMetrics`；能引用官方 Defaults 的直接引用 |
| P2-2 | 死代码 / 死接线：`LocalPageBackdrop`（provided 未消费）、`LocalGlassRequested`（同）、`LocalRailInset`（同）、`PageMetrics.CardCorner`（未用）、`HomeScreen` 的 `onDelete`、`AppActions.lockNow`（未调用） | `GlassEnvironment.kt:13,35,38`、`PageFrame.kt:59,148`、`HomeScreen.kt:459`、`AppActions.kt:244` | 删除或真正接入；同步更新 `docs/architecture/glass-contract.md` |
| P2-3 | ApiActivity 窗口玻璃采样不透明 surface vs 92% 透明窗口 | `ApiActivity.kt:296-325` | 修正采样语义或记录 L3 不可达 |
| P2-4 | 弹层 backdrop（R）不含底栏 → 玻璃底部缺像素 | `HomeScreen.kt:297,462,553`、`AppsScreen.kt:216,251`、`AppActions.kt:153` | 用"页面+栏"合成 backdrop（参考 `AboutPage.kt` 的 `rememberCombinedBackdrop` 思路） |
| P2-5 | 主题窗口背景硬编码 `#F7F7F9/#000000` | `res/values/colors.xml:3`、`res/values-night/colors.xml:3` | 改透明或对齐 MiuixTheme surface |
| P2-6 | 大字体未验证；Home 标签 fontSize 双源 | `HomeScreen.kt:406`、`GlacierPreferences.kt:82` | 用 `style.copy(fontSize)`；补 1.3/2.0 字体截图 |
| P2-7 | RTL 未适配：返回箭头、`railStartInset` 只处理 LTR 方向（`PageFrame.kt:245-263` 使用 `placeRelative(extra,0)`，RTL 需镜像） | `PageFrame.kt:245-263`、顶栏返回图标 | 用 `layoutDirection` 判断左右；RTL 截图验收 |
| P2-8 | 加载/错误状态缺失；Toast 权限提示无说明 | `GlacierRepository` 调用点、`SettingsScreen.kt:342-344` | 补 loading 标志 + `ProgressIndicator`；权限说明 `GlassDialog` |
| P2-9 | 图标包解码无采样、缓存策略粗放 | `AppIcons.kt:130,157-178` | `inSampleSize` / 尺寸上限 |
| P2-10 | 品牌前景构图只占 43% viewport，各尺寸偏小；monochrome 用描边 | `res/drawable/ic_launcher_foreground.xml`、`ic_brand_mark.xml` | 重绘铺满 72dp 安全区；monochrome 实心 |
| P2-11 | About 版本号显示 `debug`；About 页版本与 Settings/About 重复 | `AboutScreen.kt:79-94` | 品牌区显示版本，信息卡去重 |
| P2-12 | 设置页每个偏好双份状态（`remember(revision)` 镜像） | `SettingsScreen.kt:93-105` | 直接读可观察属性 |
| P2-13 | 顶栏图标按钮无 Tooltip | `PageFrame.kt:211-237` | 参考 `MainPage.kt:213,243` 加 `TooltipBox` |

---

## 十五、明确未验证 / 未完成（禁止用"基本符合"表述）

| 项 | 状态 | 原因 |
|---|---|---|
| Debug/Release 构建 | **本轮未运行**（任务禁止 Gradle） | 上一轮记录不可作为本轮证据 |
| L3 光学 | **未验证** | 模拟器 SwiftShader；`progressiveBlur` 在模拟器被关（`PageFrame.kt:188`） |
| 大字体（1.3x / 2.0x） | **未验证** | 无截图；风险点见 P2-6、P1-6 |
| RTL | **未验证** | 无截图；返回箭头未镜像、railStartInset 未处理 RTL |
| 真机横竖屏 | **未验证** | 仅模拟器；宽屏断点/折叠胶囊行为需真机复核 |
| 第三方图标包 | **未验证** | 未安装图标包实测；解码路径有 OOM 风险 |
| 折叠屏/平板 600–839dp | **未验证** | 官方宽度断点在该区间显示侧栏，Glacier 显示底栏 |
| Android 14/15 兼容 | **未验证** | 仅在 Android 16 (API 36) 运行记录 |

---

## 十六、一句话结论（给实现成员的验收口径）

> 这不是"再调几个 padding"的问题：**底栏宽度、让位/FAB 数值、宽屏骨架、浮层/弹层的组合与动画语义**四处是结构性错误，必须按 P0 重做骨架与数值；其余 P1/P2 是官方示例已有范式、直接替换即可。全部整改完成后，用本文第十一节的数值表逐项复测，并把 `docs/verification/miuix-os4-r2/` 的旧截图全部作废重拍。

---

## 十七、R3 实施结果（2026-11-19 本轮已整改，覆盖前文的"现状"描述）

> 说明：本文第二～十六节记录的是**本轮整改前**的审计现状与差异（作为对照基线）。
> 本轮已按第九节 P0/P1 重做骨架与数值，并完成 Debug/Release 构建 + 模拟器运行验收。
> 逐项改动与官方依据见 `docs/plan/r3-change-list.md`；截图与实测数据见
> `docs/verification/miuix-os4-r3/README.md`。

### 17.1 P0 结果

| 编号 | 问题 | 状态 | 整改与实测证据 |
|---|---|---|---|
| P0-1 | 底栏没有 24dp 水平内边距，整屏拉满 | **已整改 / 已实测** | 底栏移入 Shell `Scaffold.bottomBar` 并 `padding(horizontal = 24.dp)`。实测胶囊 954px = **363.4dp**（411dp 屏 − 48dp），与官方 example `AppContent.kt:772-774` 一致；`uiautomator` 目的地 bounds：主页 `[97,2190][375,2300]`、应用 `[401,2190][679,2300]`、设置 `[705,2190][983,2300]` |
| P0-2 | 手动让位/FAB 魔法值（`reserve-20`、`+32`、`+24`） | **已整改 / 已实测** | 删除 `LocalNavReserve`；底栏实测高度经 `LocalShellBottomBarHeight` 下发，页面用透明 `bottomBar` 占位，由 Scaffold 计算正文底部 padding 与 FAB 上浮（`Scaffold.kt:261-269`）。Home FAB 现在位于胶囊上方 12dp，页面底部让位 = 胶囊总高 106.3dp + 12dp 末尾留白（上一轮为 158dp） |
| P0-3 | 宽屏：侧栏常驻展开、内容整体右移、顶栏双重 inset、搜索条无让位、二级页高亮错误 | **已整改 / 已实测（模拟器横屏）** | 按官方 `AdaptiveUtils.kt` 分档：≥1200dp 展开面板，840–1200dp 折叠成顶部居中胶囊（可点开）；标题透明度跟随 `railState.progress`；搜索浮层通过 `LocalSearchTopInset` 让开胶囊；二级页侧栏 `selectedIndex = -1`。截图 `23-landscape-collapsed.png`、`24-landscape-rail-expanded.png`、`25-portrait-restored.png` |
| P0-4 | 搜索浮层 `if (searchOpen)` 条件组合 → 无退出动画 | **已整改 / 已实测** | Home / Apps 的 `GlassSearchOverlay` 改为常驻组合 + `expanded` 驱动；关闭时播放官方 400ms 几何动画 + 150ms Cancel 动画，query 清空/键盘收起/返回键由组件内部处理（`GlassSearchOverlay.kt:229-245`）。截图 `07`→`08`→`09` |
| P0-5 | Apps 空态不吃 padding，被顶栏覆盖 | **已整改** | `Box(padding(pageContentPadding(padding)))` 包住 `EmptyState`；Home 空态同理（网格 item 在 contentPadding 内） |

### 17.2 P1 结果

| 编号 | 问题 | 状态 | 整改 |
|---|---|---|---|
| P1-1 | 玻璃/非玻璃弹层、Dialog、BottomSheet 无退出动画 | **已整改** | 全部改"常驻组合 + `show` + 保留最后一次内容"（搜索、标签编辑、标签名、删除确认、延时冻结、操作面板、排序、筛选、ApiActivity 错误弹层） |
| P1-2 | 页签 padding/高度与玻璃档不一致 | **已整改** | 页签在顶栏 `bottomContent`，外层 `padding(horizontal = 12dp, top = 6dp, bottom = 12dp)`；两档都使用官方组件（`GlassTabRow` / `TabRow`），高度由组件决定 |
| P1-3 | 顶栏动作 24dp、无动作间距 | **已整改** | `TopBarAction` 图标统一 28dp（`PageMetrics.TopBarActionIcon`）；多选动作 `Row(spacedBy(8.dp))` |
| P1-4 | 分节标题/页面留白/卡片间距不一致 | **已整改** | `PageMetrics.Horizontal = SectionGap = BottomGap = 12.dp`；全局不再出现 16dp/6dp/18dp 混用 |
| P1-5 | 搜索结果行自绘、take(50) | **已整改** | Home / Apps 搜索结果统一 `Card + BasicComponent`（40dp 图标、标题、包名、冻结徽标），去掉截断 |
| P1-6 | 弹层行结构不一致、无滚动 | **部分整改** | 排序/筛选/操作面板/标签编辑行统一官方 `BasicComponent`/`SheetActionRow`/`SwitchPreference`；操作面板仍用 `Column`（10 行，未改 LazyColumn，大字体溢出风险见 17.5） |
| P1-7 | Dialog 按钮间距/空输入反馈 | **已整改** | 按钮 `Row(spacedBy(12.dp)) + weight(1f)`；空文本时保存 `enabled = false` |
| P1-8 | 标签删除入口不可达（`onDelete` 死参数） | **已整改** | 标签编辑每行补删除动作，接上删除确认（截图 `10-tag-editor.png`） |
| P1-9 | 缺官方滚动修饰（overscroll / scrollEndHaptic / 滚动条） | **未整改（保留）** | 页面使用官方 `MiuixScrollBehavior` + `nestedScroll`；`pageScrollModifiers` 属于 official example 的宿主层能力，未移植（不影响布局/交互正确性）。列为剩余项 |
| P1-10 | 分页 spring 与预组合页数 | **已整改** | `PagerNavigationSpringSpec` + `springAnimateToPage`；tab 只回写 `settledPage`，修掉"两页各露一半"的竞态（`25-portrait-restored.png` 验证旋转后吸附） |
| P1-11 | 二级页转场缺系统圆角裁剪/`backdropColor` | **未整改（保留）** | 需要把 `NavDisplayEffects` 接到 Shell；本轮只保证转场功能与玻璃层级正确。列为剩余项 |
| P1-12 | About 品牌区尺寸/字号/顶栏可展开 | **已整改 / 已截图** | 88dp 容器 + `RoundedCornerShape(24.dp)` + 白底 + 74dp 图 + 35sp Bold + 14sp 版本 + 40dp 上边距；`expandable = false`（`16-about-light.png`） |
| P1-13 | Toast 而非官方 Snackbar | **保留（有理由）** | 大量调用点发生在无 Compose 界面的上下文（磁贴、快捷方式、权限回调、服务），系统 Toast 是唯一一致出口；页面内 Snackbar 会与跨窗口提示分裂。列为保留自定义项 |
| P1-14 | 图标选中环导致尺寸跳变 | **保留（规范要求）** | 选中环仍用 `squircleBorder` + 2dp 内缩；已通过 `size` 在最外层保证不撑大图标。视觉可用，见 `12-selection-mode.png` |
| P1-15 | Apps 无多选 | **未整改（范围外）** | Apps 页本轮只做 OS4 视觉/交互一致性；多选属于功能增量，任务书未列入必须项。列为剩余项 |

### 17.3 P2 关键项

- **图标（P2-10）**：自适应图标按 72dp 安全区 1.5× 渲染后再 squircle 裁切（`platform/AppIcons.kt`），第三方图标恢复原色与正确占比；品牌图标白底黑主体，`ic_brand_mark` 裁到 72dp 安全区构图。
- **硬编码（第十节）**：页面级 padding/spacing 收敛到 `PageMetrics`；新增 dp 仅来自官方常量或已登记 token；`rg "padding\(16\.dp\)|RoundedCornerShape\(16|spacedBy\(6\.dp|spacedBy\(18\.dp\)"` 无残留。
- **文案**：Settings/About/Licenses 的硬编码英文与字符串拼接全部资源化；新增文案按页面拆到独立 `strings_*_r3.xml`（避免资源文件并发冲突）。
- **About 版本去重（P2-11）**：版本只出现在品牌区 + 信息卡值位，不再「版本 版本 …」。

### 17.4 保留的自定义实现（连同理由）

| 实现 | 位置 | 保留理由 |
|---|---|---|
| 空状态 `EmptyState` | `ui/components/PageFrame.kt` | MIUIX 无空状态组件；只用官方 `Text` 排版，无自造设计 |
| 应用网格 `LazyVerticalGrid` + `HomeAppItem` | `feature/home/HomeScreen.kt` | MIUIX 是组件库，不含启动器网格；列数按官方图标网格公式自适应，图标轮廓用官方 squircle |
| `PageMetrics` | `ui/components/PageFrame.kt` | 官方无页面级 spacing token；仅 5 个常量，圆角取 `CardDefaults.CornerRadius`，不是新 UI 框架 |
| 系统 Toast（`UiFeedback`） | `platform/UiFeedback.kt` | 跨窗口/无界面调用需要统一出口，见 P1-13 |
| 第三方图标取图管线 | `platform/AppIcons.kt` | 属于业务能力（去色/图标包/自适应合成），只改"如何得到位图" |
| `isWideLayout` / `isExtraWideLayout` | `ui/GlassEnvironment.kt` | 复刻官方 `AdaptiveUtils` 的断点语义（原生 Compose 配置读取），非自造设计 |

### 17.5 仍未完成 / 未验证（如实）

| 项 | 状态 | 说明 |
|---|---|---|
| L3 光学（真实折射/边缘光） | **未验证** | 模拟器 `ro.hardware.egl = emulation`，只能证明 L2 渲染路径 |
| About 滚动淡出/缩放动画 | **未实现** | 官方 AboutPage 的 logo 滚动淡出属增强项；当前只有顶栏滚动折叠 |
| P1-9 页面滚动修饰 | **未整改** | `pageScrollModifiers`（overscroll 弹性/scrollEndHaptic/滚动条）未移植 |
| P1-11 二级页转场特效 | **未整改** | `NavDisplayEffects`（系统圆角/遮罩色）未接入 |
| P1-15 Apps 多选 | **未实现** | 功能增量，非本轮 UI 整改必须项 |
| 操作面板大字体滚动 | **未验证** | 面板仍是 `Column`，2.0x 字体下 10 行可能溢出（Home/Apps 截图已覆盖 2.0x 网格，弹层未逐页） |
| RTL | **未验证** | 使用 `start/end` 与 `placeRelative` 语义，未切换 RTL 实测；返回箭头未镜像 |
| 真机 HyperOS 遮罩 | **未验证** | 自适应图标 1.5× + squircle 逻辑未在真机桌面复核 |
| 三键导航底部安全区 | **未验证** | 模拟器为手势导航；底部公式取自官方 example |
