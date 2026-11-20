# R4 专项 · Shell / 导航 / 转场（part-shell）

> 负责人：shell-nav（task-3）
> 写作范围（实际改动）：`ui/components/PageFrame.kt`、`ui/GlacierShell.kt`。`ui/GlassEnvironment.kt`、`ui/MainActivity.kt`、`ui/Routes.kt` 复核后**无需改动**。
> 未新增字符串资源（`BackNavigationAction` 复用页面已有的 `contentDescription`）。
> 约束：未运行 gradle / 构建 / 安装 / adb / git；所有结论来自本地 MIUIX 源码逐行对照 + Compose foundation 1.10.0 字节码核对，运行时效果待 lead 统一构建与截图验证。
> 对应审计：`docs/audit/miuix-os4-audit.md` §17.5（P1-9 / P1-11）；R3 遗留见 `docs/plan/r3-change-list.md`。

---

## 0. 结论摘要

| # | 项 | 结论 | 改动 |
|---|---|---|---|
| 1 | P1-9 滚动修饰 | **已落地**。PageFrame 在正文 wrapper 上统一挂官方 `pageScrollModifiers` 等价链：`scrollEndHaptic()` + `overScrollVertical(onOffsetChanged = 写回 scrollBehavior.state.overscrollOffset)` + `nestedScroll(顶栏连接)` + `fillMaxHeight()`；页面**不需要**改签名/接入 | `ui/components/PageFrame.kt:236-252`（wrapper）、`:471-501`（`Modifier.pageScrollModifiers` 公开扩展） |
| 2 | P1-9 滚动条 | **能力已就绪，非"库默认开启"**。官方 ScrollBar 是**示例宿主层**接线（每页显式加），不是库默认；PageFrame 新增可选 `scrollBarAdapter`，页面传 `rememberScrollBarAdapter(state)` 即可 | `PageFrame.kt:173-181`（参数）、`:253-266`（绘制）；Apps 已接（`AppsScreen.kt:115,130`），Home/About/Settings 待各页接线（见 §1.6） |
| 3 | P1-11 二级页转场 | **已接入官方 NavDisplayEffects**：系统圆角裁剪（窄屏跟设备 RoundedCorner，宽屏 0dp + pane clip）、`dimAmount = 0.5f`、`cornerClipMode = Leading`、`backdropColor = surface`；`transition = NavTransitions.MiuixDefault` 显式化 | `ui/GlacierShell.kt:177-200`（effects/方向）、`:255-311`（NavDisplay + entries） |
| 4 | P1-11 滑动返回 | 官方示例默认**关闭**（`AppState.kt:40 enableSwipeBack=false`，是设置项）。Glacier 无设置项：二级页（About/Licenses）启用官方交互式滑动返回并按 RTL 镜像；一级页显式 `None`（横向手势归 HorizontalPager） | `GlacierShell.kt:196-202`、`:271-310` |
| 5 | Shell 级微交互 | **全部已是官方库能力**：GlassNavigationBar 按压/选中指示器动画、GlassNavigationRail morph/按压、GlassFloatingActionButton 按压缩放、GlassIconButton 按压 alpha/叠层。Glacier 没有自绘替代层 → 无需修 | 无（结论 + 证据见 §3） |
| 6 | 600–839dp 断点 | **已与官方一致，不需要修**。`isWideLayout()` 与官方 `shouldShowSplitPane()` 公式逐字相同（`>=840dp || (>=600dp && h/w < 1.2)`）。600–839dp 只在横向/接近方形时走侧栏，竖屏仍走底栏——官方同样如此 | 无（结论见 §3.2） |
| 7 | RTL / 返回箭头镜像 | 转场、侧栏、底栏位置已由库/`Alignment.CenterStart`/`placeRelative` 自动镜像；**返回箭头此前未镜像**（与官方 `BackNavigationIcon.kt:36-41` 不一致）。新增 `BackNavigationAction` 自动镜像；调用点替换待页面 owner 执行 | `PageFrame.kt:503-543`；`GlacierShell.kt:142-150`（侧栏 start inset 改 `calculateStartPadding`） |
| 8 | 顶栏标题透明度（lead 追加） | 新增可选 `topBarTitleAlpha: (() -> Float)? = null`：与 Shell 侧栏 `LocalTopBarTitleAlpha` **相乘**（官方 `PageUtils.kt:264` 同乘）；玻璃档走 `GlassTopAppBar(titleAlpha=)`，非玻璃档走 `titleColor/largeTitleColor/subtitleColor` 的 alpha | `PageFrame.kt:150-159`（参数）、`:193-197`（合并）、`:309-329`（非玻璃档） |

---

## 1. P1-9 页面滚动修饰

### 1.1 官方实现与调用位置（逐行）

- 实现：`miuix/example/shared/src/commonMain/kotlin/utils/PageUtils.kt:168-183`

  ```kotlin
  @Composable
  fun Modifier.pageScrollModifiers(
      enableScrollEndHaptic: Boolean,
      showTopAppBar: Boolean,
      topAppBarScrollBehavior: ScrollBehavior,
  ): Modifier {
      val state = topAppBarScrollBehavior.state
      val onOffsetChanged: (Float) -> Unit = remember(state) { { state.overscrollOffset = it } }
      return this
          .then(if (enableScrollEndHaptic) Modifier.scrollEndHaptic() else Modifier)
          .overScrollVertical(onOffsetChanged = if (showTopAppBar) onOffsetChanged else null)
          .then(if (showTopAppBar) Modifier.nestedScroll(topAppBarScrollBehavior.nestedScrollConnection) else Modifier)
          .fillMaxHeight()
  }
  ```

- 调用点（都是**页面自己的 LazyColumn**）：`MainPage.kt:279-287`、`AboutPage.kt:308-315`、`SettingsPage.kt:142-145`、`IconPage.kt:355-362`、`LicensePage.kt:104-110`、`ComponentPage.kt:163-169`、`PullToRefreshPage.kt:160-170`、`TextStylePage.kt:102-108`、`ColorPage.kt:92-98`、`NavigateTestPage.kt:89-95`、`NestedNavTestPage.kt:101-107`、`MultiScaffoldTestPage.kt:84-90`。
- `PullToRefreshPage.kt:156` 明确注释：`pageScrollModifiers owns the app bar connection; do not feed it twice.`（**不能双份**）。

### 1.2 库能力 vs 示例宿主层能力（关键判断）

| 能力 | 归属 | 依据 |
|---|---|---|
| 弹性 overscroll（视觉伸缩 + 回弹） | **库能力**：`MiuixTheme` 给 Compose 注入 `LocalOverscrollFactory`，所有官方滚动容器默认就有同一套物理 | `miuix-ui/theme/MiuixTheme.kt:40`、`:68`；`miuix-ui/utils/OverscrollFactory.kt:40-46`、`:52`（`MiuixOverscrollEffect`）、`:220-303`（`applyToScroll`）、`:305-349`（`applyToFling`） |
| `Modifier.overScrollVertical(onOffsetChanged)` | **库组件**（同一套 `SpringMath` 物理的 modifier 版），示例用它把偏移回写给顶栏 | `miuix-ui/utils/Overscroll.kt:48-55`、`:314-431` |
| 顶栏弹性材质进度 | **库组件 + 宿主接线**：`GlassTopAppBar` 读 `scrollBehavior.state.overscrollOffset` 决定 AGSL 弹性进度，而 `overscrollOffset` 只有宿主把 `onOffsetChanged` 接上才会变 | `miuix-ui/basic/TopAppBar.kt:339-348`（`overscrollOffset` / `visualContentOffset` KDocs 明确写"由 `overScrollVertical(onOffsetChanged=...)` 更新"）；`miuix-glass/glass/GlassTopAppBar.kt:386-391`（`elasticProgress`） |
| 滚动末端触感 | **库组件**（`Modifier.scrollEndHaptic()`），开关是**示例设置项**（默认 `true`） | `miuix-ui/utils/ScrollEndHaptic.kt:124-126`；`example/AppState.kt:35` `enableScrollEndHaptic: Boolean = true` |
| 滚动条 | **库组件**，但**不是默认开启**：官方每页显式加 `VerticalScrollBar`，并传 `trackPadding = contentPadding` | `miuix-ui/basic/ScrollBar.kt:134-146`；`example/MainPage.kt:341-345`、`SettingsPage.kt:506-507`、`AboutPage.kt:447-449` 等 |

结论：R3 审计说的"缺弹性 overscroll"更准确的说法是——**视觉弹性已由库主题提供**，缺的是 `overscrollOffset` 的宿主接线（顶栏弹性材质失效）；滚动末端触感与滚动条则确实完全没接。

### 1.3 现状（改动前）

- `ui/components/PageFrame.kt:157`（R3，改前）：正文 wrapper 只有 `Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)` → 顶栏折叠可用，但：
  - 没有 `overScrollVertical` → `TopAppBarState.overscrollOffset` 恒 0（`TopAppBar.kt:344`），`GlassTopAppBar.kt:388` 的 `elasticProgress` 恒 0；
  - 没有 `scrollEndHaptic()` → 滚动到边界无触感；
  - 没有滚动条（全项目 grep 无 `VerticalScrollBar`）。
- 页面列表各自持有 `LazyColumn`/`LazyVerticalGrid`（如 `SettingsScreen.kt:107`、`AboutScreen.kt:153`、`HomeScreen.kt:444`、`AppsScreen.kt:194` 改前），PageFrame 无法把 modifier 精确挂到滚动节点上，但**可以挂在它的祖先节点**。

### 1.4 改动

1. `PageFrame.kt:471-501` 新增公开扩展（官方等价实现，参数命名对官方做了可读化重排）：

   ```kotlin
   @Composable
   fun Modifier.pageScrollModifiers(
       scrollBehavior: ScrollBehavior,
       showTopAppBar: Boolean = true,
       enableScrollEndHaptic: Boolean = true,
   ): Modifier
   ```

2. `PageFrame.kt:236-252` 在正文 wrapper 上**统一**调用该链（页面零改动）。为什么挂在祖先节点也成立（已用 foundation 1.10.0 字节码核对，非猜测）：
   - Compose `Scrollable` 的 `scrollByWithOverscroll` 把 `applyToScroll` 包在 `performScroll` 外层（`ScrollingLogic$nestedScrollScope$1`）；
   - `performScroll` 内部先 `dispatchPostScroll` 给祖先，再由滚动容器自己的 `OverscrollEffect` 计算 `scrollRemaining`；
   - 因此祖先 `overScrollVertical` 在 post-scroll 阶段拿到未消费的越界位移并消费，本次拖动只会有**一个**弹性所有者（不会双重拉伸）；顶栏 `overscrollOffset` 也随之被写入。
   - 这与官方"挂在 LazyColumn 自身"的物理一致，只是位移对象从"列表"变成"正文 wrapper"（对 Home/Apps/Settings/Licenses 等价；About 的差异见 §1.6 风险）。
3. 开关默认值：`scrollEndHaptic = true`，依据官方 `AppState.kt:35` 的默认值（Glacier 无该设置项，选择"按官方默认开启"，并保留可选参数方便以后接设置）。
4. `PageFrame.kt:165-172` 保留 `pageScrollOnContent: Boolean = false` 逃生口：页面若必须精确挂到自己的列表上（官方位置），传 `true` 并在列表 modifier 上调用 `Modifier.pageScrollModifiers(...)`；两处同时挂会双份消费（官方 `PullToRefreshPage.kt:156` 警告），所以用参数互斥。

### 1.5 滚动条

- 官方能力判定：**非默认开启**，是示例宿主逐页接线（见 §1.2 表）。
- `PageFrame.kt:173-181` 新增 `scrollBarAdapter: ScrollBarAdapter? = null`（`@OptIn(ExperimentalScrollBarApi::class)`，库要求：`miuix-ui/interfaces/ExperimentalScrollBarApi.kt`）；`PageFrame.kt:253-266` 在正文录制层内、滚动 wrapper **之外**画 `VerticalScrollBar`（`trackPadding` 用 Scaffold 实测的 top/bottom，避免顶栏/底栏区域出现滑动块）。
- 层序与官方一致：`MainPage.kt:278-346` 的滚动条也在 `layerBackdrop` Box 内（不参与玻璃采样冲突）。
- 已接：Apps（`AppsScreen.kt:115` 新建 `listState`、`:130` 传 `rememberScrollBarAdapter(listState)`）。
- 未接（属各页 owner）：Home（`HomeScreen.kt:189` 已有 `gridState`，只差一行 `scrollBarAdapter = rememberScrollBarAdapter(gridState)`）、About（`AboutScreen.kt:91` 已有 `lazyListState`，同样一行）、Licenses/Settings（需要先 `rememberLazyListState()` 并传给 LazyColumn）。**这三个页面不改也能工作，只是没有滚动条。**

### 1.6 未完成 / 风险

- **未构建验证**。编译风险点：`ExperimentalScrollBarApi` 的 opt-in 只在 `PageFrame` 内声明，页面传参处（Apps）已自行 `@OptIn`；`ScrollBarAdapter` 作为可选参数默认 `null`，未接页面不触发 opt-in。
- About 页的差异：官方只给 `LazyColumn` 挂 modifier，品牌头图是兄弟节点、弹性时不动（`AboutPage.kt:225-315`）；Glacier 在 wrapper 上挂，弹性时**品牌头图会与列表一起位移**。视觉上更像"整页回弹"，但不是官方的逐像素行为。需要 100% 对齐时，About 可改用 `pageScrollOnContent = true` + 自挂 modifier。
- 非玻璃档（`glassEnabled = false`）的顶栏没有 AGSL 弹性材质，`overscrollOffset` 只影响弹性回调链路，无副作用。
- 滚动条是否显示属于视觉取舍：官方 example 全部列表页都有；Glacier 目前只有 Apps 有，属于"已提供入口、逐页推广"的中间态。

### 1.7 大字体与滚动位置恢复（结论）

- 大字体：本轮改动**不改变**任何布局测量（modifier 只加 nested scroll/haptic/overscroll 与可选滚动条），滚动区域大小仍由页面列表的 `fillMaxSize` + `pageContentPadding` 决定；2.0x 字体下是否出现"操作单/弹层被裁切"属于各页范围（Apps 已在本轮改 LazyColumn）。唯一新增风险是滚动条 thumb 在超大字体下仍按 `ScrollBarDefaults` 尺寸绘制，不影响可滚动性。
- 页面切换滚动位置恢复：一级页 pager 的每页 `rememberLazyListState / rememberLazyGridState` 内部是 `rememberSaveable`（foundation 实现），pager 的 saveable state 按页索引保留；二级页返回时 `NavDisplay` 的可见窗口仍保留被覆盖的 `Main` entry（`opaqueDepth=1f`，`NavDisplay.kt:651-687` 的 visible window 规则），`rememberNavBackStack` 也带 saver。因此**本轮不改变** scroll position 的恢复语义；滚动条只读取同一 state，不创建第二个所有者。

---

## 2. P1-11 二级页转场

### 2.1 官方实现（逐行）

- 官方宿主 `example/AppContent.kt:256-305`：
  - `:257` `val navCornerRadius = if (isWideScreen) 0.dp else rememberNavSystemCornerRadius()`（分栏没有物理屏幕角）
  - `:261-285` `NavDisplayEffects(enableCornerClip, cornerClipRadius = navCornerRadius, cornerClipMode = Leading, dimAmount = 0.5f, blockInputDuringTransition, backdropColor = surface)`
  - `:287-292` 滑动返回方向是**物理方向**：RTL 用 `RightToLeft`，LTR 用 `LeftToRight`
  - `:301-305` 转场：默认 `NavTransitions.MiuixDefault`
  - `:342` 宽屏 `Modifier.clipToBounds()`（分栏规则）
- 官方 `NavDisplay` 接入：`AppContent.kt:410-455`（`transition = …`、`effects = …`，每个 `entry(swipeDismiss = swipeBackDirection)`）。
- 库定义：
  - `NavDisplayEffects` 默认 `enableCornerClip=true`、`cornerClipRadius=0.dp`、`cornerClipMode=Leading`、`dimAmount=0.5f`、`blockInputDuringTransition=false`、`backdropColor=Unspecified`：`miuix-nav/.../core/NavDisplayEffects.kt:63-69`；按深度生效的辅助函数 `:82-101`。
  - `backdropColor` 的绘制点：`NavDisplay.kt:643-649`（"card-style 转场把下层缩小后露出的区域用页面底色填充"）。
  - 系统圆角读取：`NavSystemCornerRadius.kt:17-18`（Android 读 `RoundedCorner` insets API，skiko 平台返回 0dp）。
  - `NavTransitions.MiuixDefault`：`transition/NavTransitions.kt:43-57`（满宽滑入 + 下层 1/4 视差 + 轻微 alpha；RTL 内部自动镜像；圆角/遮罩明确**不**烘焙在转场里）。
  - `NavDisplay` 默认值本来就是 `MiuixDefault` + `NavDisplayEffects()`：`NavDisplay.kt:974-991`。
  - 官方多窗格规则：`miuix/docs/guide/miuix-nav.md:343-349`（clip pane / pane 内圆角清零 / 不再重复让位 insets）。
  - 滑动返回是 opt-in：`miuix/docs/guide/miuix-nav.md:90-104`；库内 `NavSwipeDirection` 为物理方向、不随 layoutDirection 镜像。

### 2.2 现状（改动前）

- `ui/GlacierShell.kt:220-230`（R3，改前）：`NavDisplay(backStack, modifier = Modifier.fillMaxSize().railStartInset(railInset), onBack = …)` —— 只用默认值：
  - `effects` 默认 `cornerClipRadius = 0.dp` → **圆角裁剪实际无效果**（审计 P1-11 的"缺系统圆角裁剪"成立）；
  - `backdropColor = Unspecified` → 宿主底色未填充（slide 转场下不可见，但卡片风格/未来换转场会露底）；
  - `swipeDismiss` 未显式传 → 默认 `None`（与官方默认一致，但审计要求显式化）；
  - 宽屏分栏没有 `clipToBounds` → 下层视差/滑入层可能画到玻璃侧栏区域（被侧栏盖住，但多绘一层）。
- `ui/GlacierShell.kt:131-139`（R3，改前）：侧栏 start inset 用 `WindowInsets.getLeft(density, layoutDirection)`；语义正确性依赖该 API 的 direction-aware 行为，不如 `PaddingValues.calculateStartPadding` 直白。

### 2.3 改动

- `GlacierShell.kt:177-200`：按官方值构造 `NavDisplayEffects`（窄屏 `rememberNavSystemCornerRadius()`、宽屏 `0.dp`；`Leading`；`dimAmount=0.5f`；`backdropColor = MiuixTheme.colorScheme.surface`；`blockInputDuringTransition=false`），并显式 `transition = NavTransitions.MiuixDefault`。
- `GlacierShell.kt:255-266`：`modifier = fillMaxSize().railStartInset(railInset).then(if (wide) clipToBounds() else Modifier)`。`clipToBounds` 在 `railStartInset` 内层 → 裁剪边界正好是"侧栏让位后的内容窗格"，与官方 `AppContent.kt:342` 的 pane clip 等价；录制层（外层 Box）仍然满宽，R3 的"录制层不裁"结论保持不变。
- `GlacierShell.kt:196-202`、`:271-310`：滑动返回方向按物理方向镜像；一级页 `NavSwipeDirection.None`（与 HorizontalPager 的横向手势解耦），二级页 About/Licenses 用 `backSwipe`。
- `GlacierShell.kt:142-150`：侧栏 start inset 改 `asPaddingValues().calculateStartPadding(layoutDirection)`，RTL 下取物理右侧 inset，与 `Alignment.CenterStart` 的 rail 对齐方向一致。

### 2.4 滑动返回：为什么一级页关闭、二级页开启

- 官方默认是**关闭**的（`AppState.kt:40 enableSwipeBack = false`），由设置项开启；开启后官方对**所有** entry（包括主页）都传同一个方向（`AppContent.kt:417-453`）。
- Glacier 没有这个设置项。一级页的横向拖拽属于 `HorizontalPager`（切 tab），若也开滑动返回会竞争同一手势；二级页（About/Licenses）没有横向滚动容器，开启后与 MIUI 平台"边缘滑动返回"一致，且实现完全走库的 `navSwipeDismissImpl`（`NavDisplay.kt:627-641`：子内容先拒绝、手势期间 1:1 跟手、松手速度优先）。
- 风险：属于"比官方默认多开的行为"，无法构建验证；若真机出现误触发，把三处 `swipeDismiss = backSwipe` 改成 `NavSwipeDirection.None` 即可回退（1 行/页）。

---

## 3. Shell 级微交互复查

### 3.1 组件按压 / 选中 / 禁用（现状 → 官方依据 → 结论）

| 组件 | Glacier 现状 | 官方实现（均为库组件，无自绘层） | 结论 |
|---|---|---|---|
| 底栏 `GlassNavigationBar` | `GlacierShell.kt:379-386` 只传 items/selected/onSelect/backdrop/`visible` | 按压：`pressedIndex` + 胶囊指示器 spring（`GlassNavigationBar.kt:253-302`、`:387-445`）；图标/文字 tint 与按压 alpha 动画（`:495-526`）；RTL 用 `logicalX` 镜像（`:387`）；隐藏时 `showScale` + `interactionEnabled`（`:257-266`） | 无需改；`visible = navVisible` 已接到库的显示/隐藏动画 |
| 侧栏 `GlassNavigationRail` | `GlacierShell.kt:315-325`：items/selected（二级页 -1）/onSelect/backdrop/`showToggle`/`state` | 展开/折叠 morph（`GlassNavigationRail.kt:554-568`）、按压指示器（`:892-916`）、RTL `layoutDirection`（`:529`） | 无需改 |
| 折叠胶囊 | 由 `GlassNavigationRail(showToggle = true)` 自带；展开断点 `isExtraWideLayout()`（>=1200dp） | 官方 `shouldExpandNavigationRail()`（`AdaptiveUtils.kt:34-42`）同断点 | 与官方一致 |
| FAB | Home 用官方 `GlassFloatingActionButton(backdrop = fabBackdrop)`（`HomeScreen.kt:286-299`）；PageFrame 只提供 Scaffold 槽 | 按压缩放 `0.9f` spring（`GlassFloatingActionButton.kt:90-94`） | 无需改 |
| 顶栏动作 | `TopBarAction` → 玻璃档 `GlassIconButton`、非玻璃 `IconButton`（`PageFrame.kt:333-358`） | 玻璃档按压：内容 alpha + 按压叠层（`GlassIconButton.kt:176-189`），并继承顶栏材质上下文（`:81-110`） | 无需改 |
| 禁用态 | 三个导航项都没有 enabled 概念；顶栏动作也没有 disabled 参数 | 官方 example 同样不给导航项/顶栏动作传 disabled（`GlassNavigationBar.kt` 无 enabled 形参；`GlassPage.kt:351-388` 直接 Icon） | 无"看起来可点但无效"的入口；不需要补禁用态 |

### 3.2 600–839dp 断点：官方 vs Glacier

- Glacier `isWideLayout()`：`ui/GlassEnvironment.kt:67-73`，`width >= 840 || (width >= 600 && height/width < 1.2)`。
- 官方 `shouldShowSplitPane()`：`example/utils/AdaptiveUtils.kt:12-22`，**公式逐字相同**。
- 官方宽屏侧栏展开断点：`shouldExpandNavigationRail()` `>= 1200dp`（`AdaptiveUtils.kt:34-42`）= Glacier `isExtraWideLayout()`（`GlassEnvironment.kt:79-80`）。
- 结论：**不存在"官方显示侧栏、Glacier 显示底栏"的区间**。600–839dp 竖屏（h/w >= 1.2）两边都是底栏；600–839dp 横向/近方形（h/w < 1.2）两边都是侧栏。**不改**。
- 唯一实现差异：官方用 `LocalWindowInfo.containerSize`（多窗口/分屏容器），Glacier 用 `LocalConfiguration.screenWidthDp`（Activity 窗口配置）。Android 上二者都随窗口尺寸变化，行为等价；无需为分屏另做一套。
- 官方还受 `appState.showNavigationBar` / `navigationRailStyle` 设置项影响，Glacier 没有这些设置项，固定 Glass 风格 → 不属于断点偏差。

### 3.3 RTL 与返回箭头

- 已自动正确：
  - 转场滑入方向：`NavTransitions.MiuixDefault` 内部按 `scope.layoutDirection` 镜像（`NavTransitions.kt:46-56`）；前缘圆角 `cornerClipMode = Leading` 同样按 LTR/RTL 解释（`NavDisplayEffects.kt:19-31`）。
  - 侧栏位置：`Modifier.align(Alignment.CenterStart)`（`GlacierShell.kt:324`）+ `railStartInset` 用 `placeRelative`，RTL 下自动到右侧；本轮把 start inset 改成 `calculateStartPadding`（`GlacierShell.kt:142-150`），语义更直白。
  - 底栏：左右对称 24dp padding + `windowInsetsPadding(Horizontal)`（`GlacierShell.kt:355-363`），无方向性。
  - 滑动返回：物理方向按 RTL 显式镜像（`GlacierShell.kt:196-202`，同官方 `AppContent.kt:287-292`）。
- 此前缺失：**返回箭头不镜像**。`AboutScreen.kt:71`、`LicensesScreen.kt:82` 直接 `TopBarAction(MiuixIcons.Glass.ChevronBackward, …)`，没有任何 RTL 处理；官方 `example/component/BackNavigationIcon.kt:36-43` 明确在 RTL 下 `graphicsLayer { scaleX = -1f }`。
- 最小修复（已加，调用点待换）：
  - `PageFrame.kt:503-543` 新增 `BackNavigationAction(onClick, contentDescription)`：玻璃档 28dp `ChevronBackward`（onSurface），非玻璃档 24dp `MiuixIcons.Back`（onBackground），RTL 统一 `scaleX = -1f`，与官方 `BackNavigationIcon.kt` 逐项对齐。
  - 调用点替换（**不属于我的写作范围，交给 lead**）：`AboutScreen.kt:70-72`、`LicensesScreen.kt:81-83` 的 `TopBarAction(...)` → `BackNavigationAction(contentDescription = stringResource(R.string.close), onClick = onBack)`。
  - 非方向性图标（Search/Sort/Filter/MultiSelect/Edit）继续用 `TopBarAction`，不镜像——官方也只镜像返回箭头。

---

## 4. 顶栏标题透明度（lead 追加需求）

- 需求来源：官方 `AboutPage.kt:127` `titleAlpha = ((scrollProgress - 0.35f) / 0.65f).coerceIn(0f, 1f)`；R3 的 `PageFrame` 只把 `LocalTopBarTitleAlpha` 给玻璃档，非玻璃档完全忽略。
- 改动：
  - `PageFrame.kt:150-159` 新增 `topBarTitleAlpha: (() -> Float)? = null`（`null` = 不接管，完全保持现有行为）。
  - `PageFrame.kt:193-197`：`merged = (页面曲线 ?: 1f) * chromeTitleAlpha`，**相乘**。依据：官方 `PageUtils.kt:264` 就是 `titleAlpha = { titleAlpha() * chromeTitleAlpha() }`；两个遮挡来源（宽屏折叠胶囊盖住标题、页面滚动淡出）任一为 0 都必须不可见，相乘是官方语义。
  - 玻璃档（`PageFrame.kt:289-308`）：`GlassTopAppBar(titleAlpha = mergedTitleAlpha)`，lambda 在绘制期读取，滚动不触发整页重组。
  - 非玻璃档（`PageFrame.kt:309-329`）：`TopAppBar(titleColor = onSurface.copy(alpha = merged), largeTitleColor = 同, subtitleColor = onSurfaceVariantSummary.copy(alpha = merged))`。`alpha == 1f` 时与原来的默认值等价（不改未传参页面）。大标题与紧凑标题都处理了。
- 风险：非玻璃档 alpha 是组合期参数，滚动淡出会让顶栏文字逐帧重组（二级页顶栏只有标题/副标题，成本可接受）；玻璃档无此问题。
- 接线：About 由 lead 统一把 `topBarTitleAlpha = { ((scrollProgress.value - 0.35f) / 0.65f).coerceIn(0f, 1f) }` 传给 PageFrame（`AboutScreen.kt` 不在我的范围）。

---

## 5. 逐项"是否真的符合官方"

| 项 | 符合官方？ | 说明 |
|---|---|---|
| 滚动末端触感 | ✅ | 官方组件 + 官方默认开关值（无设置项，硬编码默认 `true`，可传参覆盖） |
| 弹性 overscroll 视觉 | ✅（原本就有） | 库主题默认提供；本轮把顶栏 `overscrollOffset` 接线补齐，弹性只有一个所有者 |
| 顶栏弹性材质进度 | ✅ | `overScrollVertical → overscrollOffset → GlassTopAppBar.elasticProgress` 全链路接通 |
| 滚动条 | ◐ 部分 | 官方能力已提供入口并进 PageFrame；只有 Apps 接线，Home/About/Settings 未接（owner 待办） |
| 系统圆角裁剪 | ✅ | 窄屏设备圆角、宽屏 0dp（与官方同策略） |
| 遮罩色 dim | ✅ | `dimAmount = 0.5f`（官方 slide 默认上限；曲线由转场 `scrimFraction` 决定） |
| backdropColor | ✅ | 传 `surface`，同官方 |
| 进退场动画 | ✅ | `NavTransitions.MiuixDefault`（库默认，本轮显式声明） |
| 宽屏 pane clip | ✅ | `clipToBounds`（官方多窗格规则） |
| 滑动返回 | ◐ 有意偏离 | 官方默认关闭、由设置项开启；Glacier 二级页默认开启，一级页关闭 |
| 返回箭头 RTL 镜像 | ✅ 能力已加 / ◐ 调用点未换 | `BackNavigationAction` 已实现，About/Licenses 替换属页面 owner |
| 600–839dp 断点 | ✅ | 与官方公式一致，无需改 |

---

## 6. 需要 lead 统一验证的截图 / 录屏清单

1. **转场中间帧**（最关键）：Settings → About push，录屏后截 30% / 60% 两帧，确认：进入页前缘两角被设备圆角裁剪、下层页 1/4 视差、遮罩 0.5 且随进度变浅、进入页从右侧满宽滑入（RTL 时从左侧）。
2. **pop 中间帧**：About → 返回，截 30% / 60%，确认反向动画与遮罩曲线对称；系统返回手势（predictive back）跟手帧。
3. **滑动返回**：About 页左缘右滑（RTL 为右缘左滑）30% / 70% 中间帧，确认 1:1 跟手、松手提交/取消；确认一级页（Home/Apps/Settings）右滑不会误返回、HorizontalPager 切页正常。
4. **横屏 / 宽屏**：840dp+ 窗口（折叠屏展开或分屏）下：侧栏覆盖式显示、转场时进入页**不画到侧栏区域**（pane clip 生效）、侧栏折叠胶囊盖住标题时标题淡出（`LocalTopBarTitleAlpha`）与（接线后）About 滚动淡出相乘。
5. **弹性滚动**：Home 网格滚到顶继续下拉，确认内容回弹 + 顶栏弹性放大/模糊（AGSL 材质进度）；到底上拉回弹；快速甩到边界时手感知觉反馈（真机）。
6. **滚动条**：Apps 列表滚动时滚动条出现/淡出、可拖动；Home/About 若已接线同样验证；横屏下轨道不被顶栏/底栏遮住。
7. **RTL**：系统设置强制 RTL 后：底栏/侧栏位置、返回箭头方向、转场滑入方向、滑动返回方向（二级页）、About 标题、`ChevronBackward` 镜像。
8. **玻璃关闭档**（设置→玻璃效果关闭）：非玻璃 `TopAppBar` 的 About 标题滚动淡出（需 lead 接线后）与正常页面滚动。
9. **大字体 2.0x**：滚动区域、转场、顶栏高度折叠；确认无裁切。
10. **600–839dp**：竖屏 700×1000（应显示底栏）与横向 700×500（应显示侧栏）各一帧，确认与官方断点一致。

---

## 7. 改动文件与未完成项

### 改动
- `app/src/main/kotlin/com/ayang/glacier/ui/components/PageFrame.kt`
  - 新增参数：`topBarTitleAlpha`、`scrollEndHaptic`、`pageScrollOnContent`、`scrollBarAdapter`（全部带默认值，**现有调用点签名/语义不变**）。
  - 正文 wrapper 接入官方 `pageScrollModifiers` 等价链；新增公开扩展 `Modifier.pageScrollModifiers`；新增 `VerticalScrollBar` 可选绘制；非玻璃档顶栏标题 alpha；新增 `BackNavigationAction`。
- `app/src/main/kotlin/com/ayang/glacier/ui/GlacierShell.kt`
  - `NavDisplayEffects` + `NavTransitions.MiuixDefault` + 宽屏 pane `clipToBounds`；二级页 `swipeDismiss`（RTL 镜像）、一级页显式 `None`；侧栏 start inset 改 `calculateStartPadding`。
- `app/src/main/res/values*/strings_shell_r4.xml`：**未创建**（无新增字符串）。

### 未完成 / 交接
1. **About 接线**：`topBarTitleAlpha = { ((scrollProgress.value - 0.35f) / 0.65f).coerceIn(0f, 1f) }`（lead 统一做）。
2. **返回箭头调用点**：`AboutScreen.kt:70-72`、`LicensesScreen.kt:81-83` 换 `BackNavigationAction`（页面 owner/lead）。
3. **滚动条接线**：Home (`gridState`)、About (`lazyListState`)、Licenses/Settings（需新建 state）各一行；Apps 已完成。
4. **About 弹性位移范围**：如需与官方逐像素一致（头图不跟随弹性），About 改 `pageScrollOnContent = true` 并自挂 `pageScrollModifiers`。
5. 全部运行时效果（尤其转场圆角、滑动返回冲突、RTL、600–839dp）需一次真机构建验证；本轮未运行 gradle，编译风险点已在 §1.6 / §2.4 标注。
