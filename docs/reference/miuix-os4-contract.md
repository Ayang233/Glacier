# MIUIX OS4 / OS4 Glass 官方契约（源码实证版）

- 研究对象：本地 MIUIX 源码 `Hail/miuix`（HEAD `58b0a3d`，`LIBRARY_VERSION 0.9.5`），含 `example/shared` 与 `docs/demo`
- 本文用途：Glacier 第二轮整改的**唯一官方事实依据**。所有结论均来自实际代码，不含 README/KDoc 之外的推断。
- 证据格式：`短路径:行号`。短路径前缀见下表，均相对仓库根 `C:\Users\10487\WorkBuddy\Hail\`。

| 短前缀 | 实际路径 |
|---|---|
| `ui/` | `miuix/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/` |
| `glass/` | `miuix/miuix-glass/src/commonMain/kotlin/top/yukonga/miuix/kmp/glass/` |
| `blur/` | `miuix/miuix-blur/src/commonMain/kotlin/top/yukonga/miuix/kmp/blur/` |
| `pref/` | `miuix/miuix-preference/src/commonMain/kotlin/top/yukonga/miuix/kmp/` |
| `nav/` | `miuix/miuix-nav/src/commonMain/kotlin/top/yukonga/miuix/kmp/nav/` |
| `squircle/` | `miuix/miuix-squircle/src/commonMain/kotlin/top/yukonga/miuix/kmp/squircle/` |
| `core/` | `miuix/miuix-core/src/commonMain/kotlin/top/yukonga/miuix/kmp/` |
| `icons/` | `miuix/miuix-icons/src/commonMain/kotlin/top/yukonga/miuix/kmp/icon/extended/` |
| `glassIcons/` | `miuix/miuix-glass-icons/src/commonMain/kotlin/top/yukonga/miuix/kmp/icon/glass/` |
| `example/` | `miuix/example/shared/src/commonMain/kotlin/` |
| `docsDemo/` | `miuix/docs/demo/src/commonMain/kotlin/` |
| `glacier/` | `Glacier/app/src/main/kotlin/com/ayang/glacier/` |

> 注：本版 MIUIX 是 Compose Multiplatform 库（包名 `top.yukonga.miuix.kmp.*`）。Glacier 的 composite build 引用的就是这份源码，
> 所以下面的默认值 = 运行时默认值。

> **R6 更新（2026-11-22）**：本文的 HEAD（`58b0a3d`）是第二轮审计时的快照，行号会随基线漂移。
> 宿主让位 / 内容内边距已在 R6 按官方 `example/utils/PageUtils.kt` 落到 Glacier 的
> `app/src/main/kotlin/com/ayang/glacier/ui/PageInsets.kt`（`AppPageInsets` + `appPageHorizontalInsets()` +
> `appPageTopBarInsets()` + `rememberAppSafePadding()`），本文 §A.5/§A.6 的结论仍然有效，
> 但**实现位置以 `ui/PageInsets.kt` 为准**。

---

## 0. 一句话结论（先给整改负责人）

1. **页面骨架只有一个官方答案**：`Scaffold { content(PaddingValues) }`，`topBar`/`bottomBar` 负责自己的 insets，
   正文只依赖 `PaddingValues`；**导航栏应放进 `bottomBar` 槽**（GlassPage 的做法），而不是浮在 Scaffold 外面再手算让位。
2. **Glass 不是"Scaffold 的玻璃版"**：官方没有 GlassScaffold。Glass 是**每个组件自己接收 `Backdrop?`**，
   传 `null` 自动退回不透明填充。谁采样哪一层由**树结构**决定，不由组件决定。
3. **所有页面横向留白 = 12dp**（官方 example 里卡片统一 `padding(horizontal = 12.dp)`），
   卡片/页面之间的纵向间距 = 12dp；`SmallTitle` 自带 `28dp/8dp` 内边距。
4. **GlassNavigationBar 在官方 example 中从不贴边**：要么放进 `Scaffold.bottomBar` 并额外 `padding(horizontal = 24.dp)`，
   要么在 bottomBar 容器里 `padding(start = 24.dp, end = 24.dp, top = 20.dp)`。
5. **搜索浮层的 × 分为两个完全不同的按钮**：输入框内的是"清空输入"（`contentDescription = "Clear"`，只清 `query`），
   浮层右侧的是"取消搜索"（`contentDescription = "Cancel"`，只调 `onDismissRequest`）。二者**不共用一个回调**。

---

## A. 页面骨架：Scaffold / insets / 内容 padding

### A.1 官方 Scaffold 的插槽与参数

`ui/basic/Scaffold.kt:78-92`：

```kotlin
fun Scaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    floatingToolbar: @Composable () -> Unit = {},
    floatingToolbarPosition: ToolbarPosition = ToolbarPosition.BottomCenter,
    snackbarHost: @Composable () -> Unit = {},
    popupHost: @Composable () -> Unit = { MiuixPopupHost() },
    containerColor: Color = MiuixTheme.colorScheme.surface,
    contentWindowInsets: WindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
    content: @Composable (PaddingValues) -> Unit,
)
```

- **默认 `contentWindowInsets`** = `systemBars ∪ displayCutout`（`ui/basic/Scaffold.kt:90`）。
- **`popupHost` 默认就是 `MiuixPopupHost()`**（`ui/basic/Scaffold.kt:88`）。弹层（Dialog/BottomSheet/Popup）
  默认渲染在**最外层 Scaffold** 的 popup 槽里，不需要页面自己写宿主。
- Scaffold 内部会把 `LocalPopupStates/LocalDialogStates/LocalRootPopupStates/LocalRootDialogStates` 注入子树，
  根宿主会向上查找：`rootDialogStates = parentRootDialogStates ?: dialogStates`（`ui/basic/Scaffold.kt:102-107`）。

### A.2 摆放顺序（决定 draw order 与命中测试顺序）

`ui/basic/Scaffold.kt:354-380` 的 `layout{}` 顺序：

```
body(356) → topBar(358) → snackbar(360) → bottomBar(369) → floatingToolbar(371) → fab(375) → popup(379)
```

后画的盖住先画的，**命中测试也按这个 z 序从后往前**。这条事实直接决定了：

- 顶栏一定盖在正文之上（正文里的浮层会被顶栏吃掉点击）——见 E 节与第 10 节的搜索 × BUG。
- popup 槽在最顶层，所以 `GlassDialog`/`GlassBottomSheet`/下拉菜单是天然"浮在一切之上"的。

### A.3 insets 语义（这是官方最容易被误读的一条）

KDoc 写死了行为（`ui/basic/Scaffold.kt:68-76`）：

> Scaffold will take the insets into account from the top/bottom **only if** the topBar/bottomBar
> are not present, as the scaffold expect topBar/bottomBar to handle insets instead.

实现见 `ui/basic/Scaffold.kt:328-346`：

```kotlin
contentPadding.paddingHolder = PaddingValues(
    top = if (topBarPlaceable.width == 0 && topBarPlaceable.height == 0) insets.calculateTopPadding()
          else topBarPlaceable.height.toDp(),
    bottom = if (isBottomBarEmpty) insets.calculateBottomPadding()
             else bottomBarPlaceable.height.toDp(),
    start = insets.calculateStartPadding(layoutDirection),
    end = insets.calculateEndPadding(layoutDirection),
)
```

结论：

- **只要传了 topBar，正文顶部 padding 就只是顶栏高度**，不含状态栏高度 → 顶栏必须自己处理系统状态栏 inset。
- **只要传了 bottomBar，正文底部 padding 就只是底栏高度** → 底栏必须自己处理导航栏/手势区 inset。
- **左右 padding 永远是 `contentWindowInsets` 的 start/end**（横屏刘海、侧栏让位都从这一路走）。
- 因此"页面内容再加一次状态栏高度""导航栏在 Scaffold 外再手动让位"都会重复计算。

### A.4 FAB 摆放（与 bottomBar 的关系）

`ui/basic/Scaffold.kt:261-269`：

```kotlin
val fabOffsetFromBottom = fabPlacement?.let {
    if (isBottomBarEmpty || floatingActionButtonPosition == FabPosition.EndOverlay) {
        it.height + FabSpacing + contentWindowInsets.getBottom(...)
    } else {
        bottomBarPlaceholder.height + it.height + FabSpacing   // 底栏存在时叠在底栏之上
    }
}
```

- `FabSpacing = 12.dp`（`ui/basic/Scaffold.kt:465`）。
- FAB 默认 `FabPosition.End`，即右下、距右边缘 12dp + end inset（`ui/basic/Scaffold.kt:237-248`），
  距底边 = 底栏高度 + FAB 高度 + 12dp（`ui/basic/Scaffold.kt:375-377`）。
- **FAB 不占用 content padding**：FAB 是浮在正文上的，正文尾部要给 FAB 留空间需要页面自己处理。

### A.5 官方 example 的页面结构（三种，按宽度分）

官方 example 的宿主是 `example/AppContent.kt`：

1. **最外层宿主 Scaffold（弹层宿主）**——`example/AppContent.kt:308-313`：

```kotlin
Scaffold(
    containerColor = Color.Transparent,
    contentWindowInsets = WindowInsets(0),
) { ... }
```

   这一层只负责：弹层宿主（popupHost 默认）、Glass 侧栏让位、`TopLayerHost`。
   它把 `contentWindowInsets` 归零，把 insets 交给内层页面自己算。

2. **宽屏页面（无底部导航）**——`example/AppContent.kt:600-633`：

```kotlin
Scaffold(
    contentWindowInsets = LocalAppPageInsets.current.contentInsets,
) { padding ->
    // Pages own horizontal safe areas; the pager stays full width for backdrop recording.
    pagerContent(pagePadding, Modifier.imePadding())   // 只取 top/bottom
}
```

   页面 `pagePadding` 只保留 top/bottom（`example/AppContent.kt:620-627`），横向安全区由**每个页面自己**
   用 `pageContentPadding(...)` 处理（见 A.6）。注释明确说明：**分页器必须满宽，录制备份层需要横向像素**。

3. **窄屏页面（底部导航进 bottomBar）**——`example/AppContent.kt:644-682`：

```kotlin
val backdrop = rememberLayerBackdrop { drawRect(surfaceColor); drawContent() }  // 644-647
Scaffold(
    contentWindowInsets = LocalAppPageInsets.current.safeInsets,
    bottomBar = { NavigationBar(..., backdrop = backdrop) },                    // 651-657
    floatingActionButton = { FloatingActionButton(..., backdrop = backdrop) },  // 658-664
) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)) {             // 676
        pagerContent(innerPadding, Modifier.imePadding())
    }
}
```

   **这是窄屏的标准结构**：底部导航 + FAB 在 Scaffold 槽位；正文 Box 是录制层；正文用 `innerPadding`（已含底栏高度）。

### A.6 `pageContentPadding`：官方页面内容 padding 的唯一出处

`example/utils/PageUtils.kt:186-222`：

```kotlin
fun pageContentPadding(
    innerPadding: PaddingValues,
    outerPadding: PaddingValues = PaddingValues(0.dp),
    extraTop: Dp = 0.dp, extraStart: Dp = 0.dp, extraEnd: Dp = 0.dp, extraBottom: Dp = 0.dp,
): PaddingValues {
    val pagePadding = LocalAppPageInsets.current.contentInsets.asPaddingValues()
    return ... {
        left  = maxOf(inner, outer, pagePadding.left)  + extraStart
        right = maxOf(inner, outer, pagePadding.right) + extraEnd
        top   = maxOf(inner, outer, pagePadding.top)   + extraTop
        bottom= maxOf(inner, outer, pagePadding.bottom)+ extraBottom
    }
}
```

关键点：

- **取 `maxOf`，不是相加**。因为 `innerPadding` 已含顶栏/底栏高度，`pagePadding` 已含系统 safe area + 覆盖式侧栏让位，
  两者语义重叠时取大者，避免双算。
- `pagePadding` 来自 `LocalAppPageInsets.current.contentInsets`（`example/utils/PageUtils.kt:194`），
  该值由 `rememberAppPageInsets(...)` 计算（`example/utils/PageUtils.kt:76-117`）：
  `windowInsets = systemBars ∪ captionBar ∪ displayCutout ∪ hostPadding`（83、98），
  `safeInsets`（普通栏）可选去掉起始侧（99-103），
  `contentInsets` = `safeInsets + overlayStart`（覆盖式玻璃侧栏的让位，105-114）。
- **页面横向安全区归页面自己管**（`appPageHorizontalInsets()` / `pageContentPadding`），
  顶栏用 `appPageTopBarInsets()`（`example/utils/PageUtils.kt:127-129`），
  搜索浮层用 `appPageSearchInsets()`（`example/utils/PageUtils.kt:133-142`）。
  原因：录制层必须满宽，横向让位只能发生在内容层（`example/AppContent.kt:628` 注释）。

### A.7 `Modifier.layerBackdrop` 放在哪一层

官方有三种放法，按"消费者与录制层不能是父子"这条红线选择：

| 场景 | 录制层挂载点 | 证据 |
|---|---|---|
| 页面正文录制层（顶栏/FAB/页签采样它） | 挂在 **Scaffold body 内的满屏 Box** 上 | `example/AppContent.kt:676`、`example/GlassPage.kt:447`、`example/MainPage.kt:278` |
| 整页录制层（弹层/Dialog 采样它，需要包含顶栏像素） | 直接挂在 **Scaffold 根** 上 | `example/GlassPage.kt:265-271` |
| 弹层宿主 | 由根 Scaffold 的 popup 槽提供，天然在页面录制层之外 | `example/GlassPage.kt:698-706` |

`example/GlassPage.kt:698-706` 的注释是决定性证据：

```kotlin
/** Keeps the root popup host outside the page recording used by the dialog. */
private fun GlassPageHost(...) {
    Scaffold(modifier = modifier, contentWindowInsets = WindowInsets(0)) {
        Box(modifier = Modifier.fillMaxSize(), content = content)
    }
}
```

**官方页面自己又套了一层 `Scaffold(contentWindowInsets = 0)`**，目的就是让 Dialog/BottomSheet 的宿主（popupHost）
落在页面录制层之外。Glacier 只有一个根 Scaffold，且根录制层只包住 `NavDisplay`（`glacier/ui/GlacierShell.kt:162-167`），
弹层由根 Scaffold 的 popup 槽渲染 → 结构上等价，是安全的（见 J 节）。

### A.8 内容 padding 与 topBar / bottomBar / FAB 的关系（结论表）

| 元素 | 谁负责 insets | 正文要让位多少 | 证据 |
|---|---|---|---|
| topBar | 自己（`windowInsetsPadding(systemBars.only(Top))`） | 顶栏高度（Scaffold 自动给） | `ui/basic/Scaffold.kt:328-337`、`ui/basic/TopAppBar.kt:884-890` |
| bottomBar | 自己（`NavigationBar` 内加 `navigationBars` bottom） | 底栏高度（Scaffold 自动给） | `ui/basic/Scaffold.kt:338-343`、`ui/basic/NavigationBar.kt:109-128` |
| FAB | Scaffold 摆位 | 不占 padding，正文尾部自行留白 | `ui/basic/Scaffold.kt:261-269`、`ui/basic/Scaffold.kt:465` |
| 覆盖式玻璃侧栏 | 页面 `contentInsets.overlayStart` | `pageContentPadding` 里的 start 增量 | `example/utils/PageUtils.kt:104-114`、`example/utils/PageUtils.kt:197-207` |

---

## B. 底部导航：NavigationBar / GlassNavigationBar

### B.1 普通 `NavigationBar` 的默认值

`ui/basic/NavigationBar.kt:74-80`（签名）+ `:434-476`（Defaults）：

| 值 | 数值 | 证据 |
|---|---|---|
| 背景色 | `MiuixTheme.colorScheme.surface` | `ui/basic/NavigationBar.kt:76` |
| 顶部分割线 | `showDivider = true`，0.75dp `dividerLine` | `ui/basic/NavigationBar.kt:77`、`ui/basic/Divider.kt:72`、`ui/basic/Divider.kt:77` |
| insets | `defaultWindowInsetsPadding = true`，加 `navigationBars.bottom`（iOS 10dp） | `ui/basic/NavigationBar.kt:78`、`ui/basic/NavigationBar.kt:109-128` |
| 显示模式 | `IconAndText` | `ui/basic/NavigationBar.kt:79`、`:532-546` |
| item 高度 | **64dp** | `ui/basic/NavigationBar.kt:436` |
| 图标 | **26dp** | `ui/basic/NavigationBar.kt:439` |
| 文字 | **12sp**，选中 Bold | `ui/basic/NavigationBar.kt:442`、`:160` |
| 图标上内边距 / 文字下内边距 | 8dp / 8dp | `ui/basic/NavigationBar.kt:445`、`:448` |
| 未选中透明度 | 0.4；按下 0.6；选中按下 0.5 | `ui/basic/NavigationBar.kt:451-457`、`:490-500` |
| 内容色 | `onSurfaceContainer` | `ui/basic/NavigationBar.kt:468-469` |

item 是等分 `weight(1f)`（`ui/basic/NavigationBar.kt:163-166`），`Arrangement.SpaceBetween` + selectableGroup（`:98-107`）。

### B.2 `GlassNavigationBar` 全量默认值（必须背下来）

`glass/GlassNavigationBar.kt:218-238`（签名）+ `:116-183`（Defaults）：

| 参数 | 默认值 | 证据 |
|---|---|---|
| `height`（最小高度） | **54.dp** | `glass/GlassNavigationBar.kt:119` |
| `IconSize` | **25.dp** | `glass/GlassNavigationBar.kt:125` |
| `LabelSize` | **11.dp**（按 sp 解释）；fontScale ≥ 1.6 时用 16.dp | `glass/GlassNavigationBar.kt:128`、`:131`、`:243-245` |
| 图标/文字上下内边距 | 6.dp | `glass/GlassNavigationBar.kt:134` |
| 图标与文字间距 | 0.dp | `glass/GlassNavigationBar.kt:137` |
| 指示器上下内缩 | 3.dp | `glass/GlassNavigationBar.kt:140` |
| 条自身横向内边距 | **8.dp** | `glass/GlassNavigationBar.kt:143`、`:486-491` |
| 指示器左右伸出量 | **5.dp** | `glass/GlassNavigationBar.kt:146` |
| 按下透明度 | 0.6 | `glass/GlassNavigationBar.kt:149` |
| 尾部独立动作间距 | **12.dp** | `glass/GlassNavigationBar.kt:122`、`glass/GlassNavigationBarLayout.kt:41` |
| 形状 | 四角 50% 胶囊 | `glass/GlassNavigationBar.kt:226` |
| 选中/未选中色 | 动态色 primary / onBackground；否则 onBackground | `glass/GlassNavigationBar.kt:235-236` |

**insets 处理**（`glass/GlassNavigationBar.kt:305-324`）：

```kotlin
val bottomPadding = if (defaultWindowInsetsPadding) {
    systemBottom = max(navigationBars.bottom, animatedCaptionBar.bottom)
    when {
        iOS -> 24.dp
        systemBottom <= 16.dp -> 20.dp
        systemBottom >= 28.dp -> systemBottom
        else -> 12.dp + systemBottom
    }
} else 0.dp
```

- 这个底部值挂在**整个 group** 上（`.padding(bottom = bottomPadding)` `glass/GlassNavigationBar.kt:328`），
  即胶囊与 trailing action 一起上移。
- **高度会增长**：`height` 是 `heightIn(min = ...)`（`glass/GlassNavigationBar.kt:357`），
  caption 折成两行或大字号时整条变高（KDoc `:118-119`、label `maxLines = 2` `:566-571`）。
- 隐藏动画：`visible = false` 时同时 scale→0.6、alpha→0、blur→18dp（`glass/GlassNavigationBar.kt:329-338`、
  `glass/GlassMotion.kt:360-367`），可见失败兜底是 `SurfaceContainer` 填充（`:367-370`）。

### B.3 `GlassNavigationBarLayout`（胶囊 + 尾随动作）

`glass/GlassNavigationBarLayout.kt:39-44`：布局参数 `spacing = GlassNavigationBarDefaults.ActionSpacing = 12.dp`。
`:82-108`：先量尾部动作，按进度动画预留宽度（`actionWidth = action.width * fraction`），
再加 12dp gap 量胶囊，最后**整组居中**（`start = (width - groupWidth) / 2`）。
所以 **trailingAction 会把胶囊整体挤窄并居中，而不是把胶囊拉宽**。用法示例：搜索按钮
`example/GlassPage.kt:310-332`（`GlassIconButton(size = GlassNavigationBarDefaults.Height = 54.dp)` + 28dp 图标）。

### B.4 官方 example 的两处调用（数值必须抄）

**（1）GlassPage：放进 `Scaffold.bottomBar`，24dp 横向内边距**（`example/GlassPage.kt:272-334`）：

```kotlin
bottomBar = {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .appPageHorizontalInsets()
            .padding(start = 24.dp, end = 24.dp, top = 20.dp)   // ← 24dp 横向 + 20dp 上方
            .layout { ... bottom = max(24.dp, safeBottom + 8.dp, hostBottom + 8.dp) ... },  // 278-285
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlassNavigationBar(
            items = NavItems,
            selectedIndex = navIndex,
            onSelect = { navIndex = it },
            backdrop = backdrop,
            defaultWindowInsetsPadding = false,               // ← 自己处理 insets
            modifier = Modifier.layout { ... 宽屏限宽 344.dp + action ... },   // 295-305
            trailingAction = { GlassIconButton(...) },        // 310-332
        )
    }
}
```

注意：`bottomBar` 槽的高度会被 Scaffold 计入正文 `PaddingValues.bottom`（A.3），
所以**页面正文不需要再为导航胶囊加任何高度**。

**（2）AppContent：悬浮玻璃导航（FloatingNavigationBar 的 glass 模式），24dp 横向**（`example/AppContent.kt:767-775`）：

```kotlin
GlassNavigationBar(
    items = glassItems,
    selectedIndex = page,
    onSelect = { mainPagerState.animateToPage(it) },
    backdrop = if (blurActive) backdrop else null,
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 24.dp),      // ← 官方唯一一次出现在调用侧的横向常量
)
```

这一处没有传 `defaultWindowInsetsPadding = false`，即 **24dp 横向 + 官方默认底部 insets** 的组合。

### B.5 「导航栏作为 Scaffold bottomBar 时内容如何让位」——正确接入代码（等价说明）

```kotlin
// 1) 录制备份层：必须满宽，先垫底色再录内容（J 节红线）
val backdrop = rememberLayerBackdrop { drawRect(MiuixTheme.colorScheme.surface); drawContent() }

Scaffold(
    // 2) 页面 insets：横屏刘海、覆盖式侧栏都从这里进来
    contentWindowInsets = LocalAppPageInsets.current.safeInsets,
    bottomBar = {
        Column(
            Modifier
                .fillMaxWidth()
                .appPageHorizontalInsets()
                .padding(start = 24.dp, end = 24.dp, top = 20.dp)
                .layout { m, c ->
                    val bottom = maxOf(24.dp, /*safeBottom + */ 8.dp, 8.dp).roundToPx()
                    val p = m.measure(c.offset(vertical = -bottom))
                    layout(p.width, p.height + bottom) { p.placeRelative(0, 0) }
                },
        ) {
            GlassNavigationBar(
                items = items,
                selectedIndex = tab,
                onSelect = { tab = it; pager.animateScrollToPage(it) },
                backdrop = backdrop,
                defaultWindowInsetsPadding = false,   // insets 已被外部 padding/layout 处理
            )
        }
    },
    floatingActionButton = { GlassFloatingActionButton(onClick = ..., backdrop = backdrop) { ... } },
    floatingActionButtonPosition = FabPosition.End,
) { innerPadding ->
    // 3) 正文只拿 innerPadding（已含顶栏/底栏高度），横向再叠加页面 insets
    LazyColumn(
        modifier = Modifier.fillMaxSize().layerBackdrop(backdrop)   // 录制层挂正文根节点
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentPadding = pageContentPadding(innerPadding, outerPadding = pageInsetsPadding),
    ) { ... }
}
```

**禁止**：把 `GlassNavigationBar` 挂在 Scaffold 之外再用 `LocalNavReserve = Height + navigationBars + 32.dp`
去给正文补 padding（Glacier 当前做法，见第 12 节冲突 2/3）——那样既会双算 insets，也会让 Scaffold 的 FAB 摆放不知道底栏存在。

---

## C. 顶栏：TopAppBar / GlassTopAppBar

### C.1 `TopAppBar` 的尺寸与排版

签名 `ui/basic/TopAppBar.kt:108-126`；Defaults `ui/basic/TopAppBar.kt:415-436`：

| 值 | 数值 | 证据 |
|---|---|---|
| 折叠高度 | **52.dp**（`CollapsedHeight`） | `ui/basic/TopAppBar.kt:426` |
| 标题横向 padding | **26.dp**（`TitlePadding`） | `ui/basic/TopAppBar.kt:417` |
| 导航图标起始 padding | **16.dp** | `ui/basic/TopAppBar.kt:420` |
| 动作图标结束 padding | **16.dp** | `ui/basic/TopAppBar.kt:423` |
| largeTitle 下边距（无 subtitle） | 4.dp | `ui/basic/TopAppBar.kt:432` |
| subtitle 下边距 | 8.dp | `ui/basic/TopAppBar.kt:435` |
| 折叠态标题 | `title3` = 20sp，Medium | `ui/basic/TopAppBar.kt:784-785` |
| 展开态大标题 | `title1` = 32sp，Normal | `ui/basic/TopAppBar.kt:832-833` |
| subtitle | `body2` = 14sp | `ui/basic/TopAppBar.kt:839` |
| 标题宽度上限 | 可用宽度的 **90%**（`TITLE_WIDTH_FRACTION = 0.9f`） | `ui/basic/TopAppBar.kt:912-917`、`:1266` |
| bottomContent | 钉在标题区之下，参与总高 | `ui/basic/TopAppBar.kt:940-943`、`:970-976`、`:1030-1031` |
| 颜色动画 | 50ms tween | `ui/basic/TopAppBar.kt:738-749` |

**insets**：`defaultWindowInsetsPadding` **只控制横向**（displayCutout + navigationBars 的 horizontal，
`ui/basic/TopAppBar.kt:879-887`）；**纵向状态栏 inset 永远加**：

```kotlin
.windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))   // ui/basic/TopAppBar.kt:890
```

所以页面若已经在外层用 `appPageTopBarInsets()` 把 top safe area 消费掉，必须传
`defaultWindowInsetsPadding = false`，否则会双加（官方 example 就是这么做的，见 C.4）。

### C.2 滚动折叠行为：`TopAppBarState` 各字段语义

`ui/basic/TopAppBar.kt:299-385`：

| 字段 | 语义 | 证据 |
|---|---|---|
| `heightOffsetLimit` | 允许折叠的像素上限（= -大标题测量高度） | `:312`；写入见 `:683-705`、`:826` |
| `heightOffset` | 当前折叠位移（0 = 完全展开，limit = 完全折叠），自动 coerce | `:320-325` |
| `contentOffset` | 内容在顶栏下方滚过的总位移（负值=向上滚），用于计算 `overlappedFraction` | `:337`、`:514-537` |
| `overscrollOffset` | 弹性回弹的临时位移，只影响视觉，不影响折叠与保存位置 | `:344`、`:347-348` |
| `collapsedFraction` | `heightOffset / heightOffsetLimit`，0=展开 1=折叠 | `:358-364` |
| `overlappedFraction` | 顶栏区域与滚动内容重叠比例 | `:373-385` |
| `visualContentOffset` | `contentOffset + overscrollOffset`，给 Glass 的 band/阴影用 | `:346-348` |

动画细节（务必照抄思路，不要自己写）：

- 大标题淡出用**折叠比例**驱动：`(collapsedFraction * 3f).coerceIn(0f, 1f)`（`ui/basic/TopAppBar.kt:672-678`），
  即折叠到 1/3 时大标题完全消失；大标题还带 blur（`titleBlurRadius`，Glass 默认 8dp，见 C.3）。
- 小标题出现时机：`collapsedFraction * 3f >= 1f`（`ui/basic/TopAppBar.kt:708-722`），
  用 `folmeSpring(damping = 1f, response = 0.3f)` 进、`response = 0.15f` 出，并伴随 `translationY: 20dp → 0`
  （`ui/basic/TopAppBar.kt:723-736`）。
- `MiuixScrollBehavior` 的 snap spring = `spring(stiffness = 2500f)`（`ui/basic/TopAppBar.kt:256-268`），
  back/fling 见 `:568-615`。
- `expandable = false` 会把 bar 钉死并清空 offset（`ui/basic/TopAppBar.kt:686-691`、`:195-203`）——
  **二级页面（About/Licenses/ApiActivity）必须 `expandable = false`**，官方 AboutPage 就这么用（`example/AboutPage.kt:139`）。

### C.3 `GlassTopAppBar` 全量默认值

签名 `glass/GlassTopAppBar.kt:268-334`；Defaults `glass/GlassTopAppBar.kt:76-167`：

| 参数 | 默认值 | 证据 |
|---|---|---|
| 动作/导航按钮横向 padding | **12.dp**（`HorizontalPadding`，与页面卡片、Tabs 对齐） | `glass/GlassTopAppBar.kt:79`、`:520-521` |
| 按钮直径 | **44.dp**（`ButtonSize`） | `glass/GlassTopAppBar.kt:91` |
| 标题折叠时最大 blur | 8.dp（`TitleBlurRadius`） | `glass/GlassTopAppBar.kt:88`、`:281` |
| 渐进模糊最大半径 | **80.dp** | `glass/GlassTopAppBar.kt:94` |
| 渐进模糊渐入距离 | 48.dp | `glass/GlassTopAppBar.kt:97` |
| 背景 band 向下探出 | **36.dp**（`BandOverhang`） | `glass/GlassTopAppBar.kt:163` |
| band 最大不透明度 | 0.90f | `glass/GlassTopAppBar.kt:194` |
| 按下内容 alpha | 0.6 | `glass/GlassTopAppBar.kt:100` |
| 材质/描边/阴影 | `puredThinGlass` 动态材质 + 小号 bloom stroke + Regular 阴影 | `glass/GlassTopAppBar.kt:133-145`、`:289` |
| 未滚动时 | 按钮是"实心 cap"；滚动后浮起为玻璃 | `glass/GlassTopAppBar.kt:377-394` |
| 折叠 ramp | `(-visualContentOffset - 0) / 32dp` clamp 0..1 | `glass/GlassTopAppBar.kt:107-121` |

两个重载：

- `GlassTopAppBar(..., scrollBehavior = ...)`：自己从 `scrollBehavior.state.contentOffset < 0f` 推 `isContentScrolled`
  （`glass/GlassTopAppBar.kt:297-303`）。
- `GlassTopAppBar(..., isContentScrolled = ...)`：页面已有准确重叠状态时用（KDoc `:336-344`），
  官方 example 用 `listState.canScrollBackward`（`example/GlassPage.kt:172-175`、`:342`）。

**bottomContent 的采样保证**：`GlassTopAppBar` 通过 `LocalGlassTopAppBarContext` 把
`backdrop / material / underlayMaterial / size / shape …` 传给子组件（`glass/GlassTopAppBar.kt:213-233`、`:488-507`），
`GlassTabRow` / `GlassIconButton` 会自动继承顶栏的 backdrop 与材质（见 D、F）。

**insets**：Glass 版同样继承 `TopAppBarLayout` 的行为——状态栏 top **始终**加，
`defaultWindowInsetsPadding` 只控制横向（`glass/GlassTopAppBar.kt:256`、`ui/basic/TopAppBar.kt:879-890`）。
官方 example 统一传 `defaultWindowInsetsPadding = false` 并把页面横向/纵向 safe area 交给
`contentModifier = Modifier.glassSearchBarTransition(...).appPageTopBarInsets()`
（`example/GlassPage.kt:339-341`、`example/utils/PageUtils.kt:254-272`）。

### C.4 `AdaptiveTopAppBar`：官方 example 的 insets 接入（可照抄）

`example/utils/PageUtils.kt:225-291`：

```kotlin
val contentInsetModifier = Modifier.appPageTopBarInsets()          // 243
if (!showTopAppBar) return
if (appState.topAppBarStyle == 1) {
    GlassTopAppBar(
        contentModifier = contentModifier.then(contentInsetModifier),   // 257
        defaultWindowInsetsPadding = false,                              // 266
        ...
    )
} else {
    TopAppBar(
        modifier = Modifier.background(color).then(contentModifier).then(contentInsetModifier), // 276-279
        defaultWindowInsetsPadding = false,                                                    // 285
        ...
    )
}
```

`appPageTopBarInsets()` = 横向 contentInsets + 顶部 safeInsets（`example/utils/PageUtils.kt:127-129`）。

### C.5 搜索态：`rememberGlassSearchBarAlpha` 与 `glassSearchBarTransition`

两个不同职责，官方 example **同时**使用：

| API | 作用对象 | 数值/语义 | 证据 |
|---|---|---|---|
| `rememberGlassSearchBarAlpha(expanded)` | **整页内容**透明度（`Scaffold.modifier.graphicsLayer { alpha }`） | 展开 1→0，150ms tween；进入 `CubicBezierEasing(0,0,0.67,1)`，退出 `(0.26,0,0.6,0.2)` | `glass/GlassSearchOverlay.kt:549-557`；用法 `example/GlassPage.kt:204`、`:266-271` |
| `Modifier.glassSearchBarTransition(expanded, blurEnabled, barAlpha)` | **顶栏内容**（作为 `contentModifier`） | 缩放 1→0.9（400ms）+ 模糊 0→50px（进 150ms / 出 120ms）；`barAlpha` 为绘制期透明度 | `glass/GlassSearchOverlay.kt:561-604`；用法 `example/GlassPage.kt:339-340` |

官方 page 侧写法（`example/GlassPage.kt:264-271`）：

```kotlin
Scaffold(
    modifier = Modifier
        .fillMaxSize()
        .graphicsLayer { this.alpha = searchBarAlpha.value }     // 整页淡出
        .layerBackdrop(pageBackdrop)
        .then(if (searchExpanded) Modifier.clearAndSetSemantics { } else Modifier),
    ...
)
```

**重要事实**：`graphicsLayer { alpha = 0f }` / `clearAndSetSemantics` **不会让顶栏失去命中测试**；
真正阻断点击的是 `GlassSearchOverlay` 自己铺的满屏 scrim（`glass/GlassSearchOverlay.kt:260-266`，见 E.3）。
所以浮层**必须画在顶栏之上**（Scaffold 之外/之后），否则点 × 会落到顶栏的动作按钮上。

---

## D. Tab：TabRow / GlassTabRow

### D.1 普通 `TabRow`

签名 `ui/basic/TabRow.kt:87-102`；Defaults `ui/basic/TabRow.kt:551-610`：

| 值 | 数值 | 证据 |
|---|---|---|
| 高度 | **42.dp** | `ui/basic/TabRow.kt:556` |
| 圆角 | **12.dp** | `ui/basic/TabRow.kt:566` |
| 单个 tab 最小/最大宽 | **76.dp / 98.dp** | `ui/basic/TabRow.kt:576`、`:586` |
| tab 间距 | **9.dp**（`itemSpacing` 默认值写在签名里） | `ui/basic/TabRow.kt:97` |
| 背景 | `surface`；选中 `surfaceContainer` | `ui/basic/TabRow.kt:598-601` |
| 文字 | `body1` = 16sp，选中 Bold | `ui/basic/TabRow.kt:434` |
| tab 内横向 padding | 12.dp | `ui/basic/TabRow.kt:427` |
| 选中项自动居中 | `LaunchedEffect(selectedTabIndex, availableWidth, ...)` 里 scroll/animate 到中间 | `ui/basic/TabRow.kt:127-136` |

宽度自适应逻辑 `calculateTabWidth(...)`：先等分，再夹在 min/max 之间（`ui/basic/TabRow.kt:518-549`）。
**普通 TabRow 是自包含的普通组件，没有 backdrop 概念**——非玻璃档直接用它。

`TabRowWithContour`：高度 45dp、圆角 8dp、min/max 62/84dp、间距 5dp、文字 body2（`ui/basic/TabRow.kt:561-591`、`:477-484`）。

### D.2 `GlassTabRow`

签名 `glass/GlassTabRow.kt:289-305`；Defaults `glass/GlassTabRow.kt:114-263`：

| 值 | 数值 | 证据 |
|---|---|---|
| 高度 | **40.dp**（`NeutralHeight` 配合 neutral 配色时为 35.dp） | `glass/GlassTabRow.kt:117`、`:120` |
| tab 间距 | **12.dp** | `glass/GlassTabRow.kt:123` |
| tab 内横向/纵向 padding | **16.dp / 6.dp** | `glass/GlassTabRow.kt:126`、`:129` |
| 形状 | 每 tab 独立胶囊，半径 = height/2，smoothing=0 | `glass/GlassTabRow.kt:136-138` |
| 文字 | 选中 `subtitle`(14sp Bold)，未选中 `body2`(14sp)，maxLines=1 | `glass/GlassTabRow.kt:456-467` |
| 等分 | 每个 tab `weight(1f)` | `glass/GlassTabRow.kt:367-369` |
| material 渐入 | **450ms linear**（`surfaceAlpha` 0→1） | `glass/GlassTabRow.kt:314-318`、`:475` |
| 两套配色 | `primaryColors()`（主题色）/ `neutralColors()`（中性深色） | `glass/GlassTabRow.kt:159-262` |

**backdrop 解析优先级（本条决定 TabRow 能否放进顶栏）**：

```kotlin
val topBarContext = LocalGlassTopAppBarContext.current
val resolvedBackdrop = (topBarContext?.backdrop ?: backdrop)?.takeIf { isRuntimeShaderSupported() }  // 309-310
val underlayMaterial = topBarContext?.underlayMaterial                                               // 311
val resolvedMaterial = material?.withUnderlayBlur(underlayMaterial)                                  // 312
```

`glass/GlassTabRow.kt:275-277` KDoc 原文：

> Inside [GlassTopAppBar] the bar's own backdrop and effective blur take precedence.
> `null` outside a bar uses the resting fills.

**结论**：

- 放在顶栏 `bottomContent` 里：**传什么 backdrop 都不影响采样源**，组件自动用顶栏的 backdrop + underlay 材质 +
  顶栏的 `materialProgress`（`glass/GlassTabRow.kt:309-312`、`:388-408` 的 `glassOnActionBar`）。
- 放在正文列表里：**必须传一个"不是它自己所在录制层"的 backdrop**，否则就是自采样（见 J 节红线）。
  官方 example 为此专门准备了 `wallpaperBackdrop`：

```kotlin
// List tabs sample the wallpaper to avoid sampling their own recording layer.
val tabBackdrop = if (tabsInTopBar) backdrop else wallpaperBackdrop   // example/GlassPage.kt:221-223
```

`example/GlassPage.kt:394-401` 是顶栏内用法的完整写法（`padding(horizontal = 12.dp).padding(top = 6.dp, bottom = 12.dp)`）：

```kotlin
bottomContent = {
    if (tabsInTopBar) {
        tabRows(Modifier.padding(horizontal = horizontalPadding)  // = GlassTopAppBarDefaults.HorizontalPadding = 12dp
            .padding(top = 6.dp, bottom = 12.dp))
    }
}
```

`GlassSegmentedTabRow`（连体轨道）：高度 40dp、轨道内缩 3dp、tab 间距 8dp、tab 内横向 16dp
（`glass/GlassTabRow.kt:478-496`），同样优先用顶栏上下文 backdrop（`:600-606`）。

---

## E. 搜索：SearchBar / GlassSearchOverlay

### E.1 普通 `SearchBar`（页内可折叠搜索）

`ui/basic/SearchBar.kt:80-129`：`SearchBar` 只是外壳，`inputField` 由调用方提供；
`outsideEndAction` 在展开时横向展开出现（`:106-112`）；返回键在展开时等价
`onExpandedChange(false)`（`:122-128`）。

`InputField`（`ui/basic/SearchBar.kt:157-315`）：

- 胶囊形状 `CircleShape`、背景 `surfaceContainerHigh`（`:168`、`:177`、`:262-265`）。
- 默认 leading 图标 = `MiuixIcons.Basic.Search`，padding start 16/end 8（`:179-186`、`:328-332`）。
- 默认 trailing = **`MiuixIcons.Basic.SearchCleanup`，点击 `onQueryChange("")`**（`:188-211`）——
  这是"清空输入"，不是关闭搜索。
- `onSearch` 由 IME `ImeAction.Search` 触发（`:256-258`）。
- 默认参数见 `SearchBarDefaults`（`:317-339`）：`InsideMargin = DpSize(12.dp, 0.dp)`、
  `InputFieldMinHeight = 45.dp`、`InputFieldFontSize = 17.sp`。
- 收起时会清空 query 并清焦点（`LaunchedEffect(expanded)`，`:299-314`）。

> **注意**：`SearchBar` 是页内条，不是全屏浮层。官方的 OS4 Glass 搜索浮层是 `GlassSearchOverlay`，两者不要混用。

### E.2 `GlassSearchOverlay` 默认值与动画

签名 `glass/GlassSearchOverlay.kt:196-214`；Defaults `glass/GlassSearchOverlay.kt:129-142`：

| 值 | 数值 | 证据 |
|---|---|---|
| 搜索条高度 | **44.dp** | `glass/GlassSearchOverlay.kt:130` |
| 横向 padding | **12.dp**（= `GlassTopAppBarDefaults.HorizontalPadding`） | `glass/GlassSearchOverlay.kt:131` |
| 纵向 padding | 4.dp | `glass/GlassSearchOverlay.kt:132` |
| 顶部渐进模糊探出 | 24.dp | `glass/GlassSearchOverlay.kt:133` |
| 展开几何动画 | **400ms**，`SearchModeEasing`（模拟 1 秒 spring 映射到 400ms） | `glass/GlassSearchOverlay.kt:215-219`、`:112-126` |
| 取消按钮材质淡入 | 150ms，进入时 delay 100ms | `glass/GlassSearchOverlay.kt:220-223` |
| 搜索图标 | 28.dp | `glass/GlassSearchOverlay.kt:434-439` |
| 清空按钮 | 24.dp 容器 / 图标 ≈18.7dp | `glass/GlassSearchOverlay.kt:444-459` |
| 取消按钮容器 | 44.dp（= 条高），图标 28.dp | `glass/GlassSearchOverlay.kt:490-503` |

**生命周期（官方要求浮层常驻组合）**：

```kotlin
val active = expanded || transition.currentState || transition.isRunning   // 229
LaunchedEffect(expanded) {
    query = ""
    if (expanded) { focusRequester.requestFocus(); keyboard?.show() }
    else if (active) { focusManager.clearFocus(); keyboard?.hide() }
}                                                                          // 230-239
if (!active) return                                                        // 240
```

即：**调用方必须始终组合这个组件、只改 `expanded`**，否则：
- 退出动画不会播放（`transition.isRunning` 永远等不到）；
- `query` 不会回到空串、焦点/键盘不会清理（`LaunchedEffect(expanded)` 根本不会执行）；
- `NavigationBackHandler`（`:241-245`）不会注册，返回键行为丢失。

### E.3 两个按钮的语义（搜索 × BUG 的官方答案）

**（1）输入框内的清空按钮**（`glass/GlassSearchOverlay.kt:444-459`）：

```kotlin
if (query.isNotEmpty()) {
    IconButton(
        onClick = { query = ""; focusRequester.requestFocus() },      // 只清输入，不关闭
        modifier = Modifier.size(24.dp),
    ) { Icon(closeIcon, contentDescription = "Clear", modifier = Modifier.size(16.dp * (7f / 6f)), tint = mutedColor) }
}
```

**（2）浮层右侧的取消按钮**（`glass/GlassSearchOverlay.kt:464-504`）：

```kotlin
Box(Modifier.align(Alignment.CenterEnd).graphicsLayer { ... translationX = 66.dp * (1 - fraction) ... }) {
    IconButton(
        onClick = onDismissRequest,                                   // 只关闭搜索
        enabled = expanded,
        modifier = Modifier.size(GlassSearchOverlayDefaults.BarHeight),  // 44dp
    ) { Icon(closeIcon, contentDescription = "Cancel", modifier = Modifier.size(28.dp), tint = colors.onSurface) }
}
```

铁律：

- **只有 `contentDescription = "Cancel"` 的按钮调用 `onDismissRequest`**；
- **只有 `contentDescription = "Clear"` 的按钮清空查询**；
- 二者共用同一个 `closeIcon`（视觉都是 ×），**极易在页面层错误合并回调**——这正是"点 × 弹出编辑标签"类 BUG 的根源形态。
- `onDismissRequest` **不负责**清 query：query 是组件内部状态（`:224`），由 `LaunchedEffect(expanded)` 在 `expanded` 变 false 时清空（`:230-239`）。
  页面无法从外部清空它，页面也不应该去清。

**（3）浮层必须自己挡住下面的点击**（`glass/GlassSearchOverlay.kt:260-266`）：

```kotlin
// Block input through the faded bar and its padding until exit completes.
Box(
    modifier = Modifier.matchParentSize()
        .drawBehind { drawRect(colors.surface.copy(alpha = progress.value.coerceIn(0f, 1f))) }
        .clickable(interactionSource = null, indication = null) { keyboard?.hide() },
)
```

这层 scrim 是浮层能"盖住顶栏点击"的唯一原因。它必须位于顶栏之上 → 浮层必须在 `Scaffold` 之后/之外组合。

### E.4 浮层应该放在哪一层（官方两处用法）

**（1）官方 example GlassPage**：`GlassPageHost { ... Scaffold{...}; GlassSearchOverlay(...) }`
——浮层与页面 `Scaffold` 是**同一个 `Box` 的两个子节点，浮层在后**（`example/GlassPage.kt:264-271`、`:684-694`）：

```kotlin
GlassSearchOverlay(
    expanded = searchExpanded,
    onDismissRequest = { searchExpanded = false },
    scrollMaterialThreshold = 0.dp,
    topInsets = pageInsets.safeInsets.only(WindowInsetsSides.Top),   // 顶部 safe area
    searchTopPadding = pageInsets.searchTop,                         // 折叠导航胶囊下移
    blurEnabled = appState.enableBlur,
    horizontalInsetsModifier = Modifier.appPageHorizontalInsets(),   // 横向页面安全区
    searchIcon = MiuixIcons.Glass.Search,
    closeIcon = MiuixIcons.Glass.Close,
) { query -> /* 搜索结果列表 */ }
```

**（2）docs demo（最小可读范例）**：卡片内 `Box { Scaffold(...) ; GlassSearchOverlay(...) }`
（`docsDemo/GlassSearchOverlayDemo.kt:98-204`），页面 `Scaffold` 的 `modifier` 统一加
`graphicsLayer { alpha = searchBarAlpha.value }`（`:100-104`），顶栏 `contentModifier = glassSearchBarTransition(expanded) { 1f }`（`:106-111`），
搜索结果列表的 `contentPadding.top` 手算 =
`topInset + BarHeight + VerticalPadding*2 + searchResultTopPadding`（`:182-191`）。

**结论**：正确层级 = `Box { Scaffold(...); GlassSearchOverlay(...) }`。
写进 `Scaffold` 的 `content` 槽 = 错（被 topBar 盖住，命中测试先到 topBar）。

### E.5 `SearchStatus` 不是库 API

`example/utils/SearchStatus.kt:15-56` 是 **example 自己的状态机工具**（4 态 `EXPANDED/EXPANDING/COLLAPSED/COLLAPSING` +
`ResultStatus`），官方库里没有 `SearchStatus` 这个类型。Glacier 若要复用这种四态，需要把文件拷进自己工程（Apache-2.0）。
`GlassSearchOverlay` 本身只认一个 `expanded: Boolean`。

---

## F. 弹层：Dialog / BottomSheet / Popup / FAB

### F.1 宿主关系（先记结论）

- 所有 `Overlay*` / `GlassDialog` / `GlassBottomSheet` / 下拉菜单都通过
  `MiuixPopupUtils.Companion.DialogLayout / PopupLayout` 注册到 **CompositionLocal 列表**里，
  由 **`MiuixPopupHost()`** 统一绘制，而它默认就是 `Scaffold` 的 `popupHost`（`ui/basic/Scaffold.kt:88`）。
- `renderInRootScaffold` 默认 **`true`**：往最外层 Scaffold 的根列表注册（`ui/utils/MiuixPopupUtils.kt:184-188`、`:265-269`）。
- popup 槽在 Scaffold 中**最后放置**（`ui/basic/Scaffold.kt:379`）→ 弹层永远在顶栏/FAB/底栏之上。
- 弹层列表在 Scaffold 销毁时会清掉未显示的项（`ui/utils/MiuixPopupUtils.kt:571-582`）。

### F.2 `OverlayDialog` / `OverlayBottomSheet` 默认值

`OverlayDialog` 签名 `ui/overlay/OverlayDialog.kt:52-72`；默认值 `ui/layout/DialogContentLayout.kt:450-502`：

| 值 | 数值 | 证据 |
|---|---|---|
| 最大宽度 | **420.dp** | `ui/layout/DialogContentLayout.kt:486` |
| 圆角（居中态） | **32.dp** | `ui/layout/DialogContentLayout.kt:492` |
| outsideMargin | `DpSize(12.dp, 12.dp)` | `ui/layout/DialogContentLayout.kt:497` |
| insideMargin | `DpSize(24.dp, 24.dp)` | `ui/layout/DialogContentLayout.kt:502` |
| `enableWindowDim` | true，`windowDimming` 遮罩 | `ui/overlay/OverlayDialog.kt:60`、`ui/theme/Colors.kt:391`/`:501` |
| `renderInRootScaffold` | true | `ui/overlay/OverlayDialog.kt:66` |

`OverlayBottomSheet` 签名 `ui/overlay/OverlayBottomSheet.kt:52-73`；`ui/layout/BottomSheetContentLayout.kt:902-934`：

| 值 | 数值 | 证据 |
|---|---|---|
| 圆角 | **28.dp**（仅上角） | `ui/layout/BottomSheetContentLayout.kt:919` |
| 最大宽度 | **640.dp** | `ui/layout/BottomSheetContentLayout.kt:924` |
| outsideMargin | `DpSize(0.dp, 0.dp)` | `ui/layout/BottomSheetContentLayout.kt:929` |
| insideMargin | `DpSize(24.dp, 0.dp)` | `ui/layout/BottomSheetContentLayout.kt:934` |
| 拖拽 | 150dp 位移 / 800dp·s⁻¹ 速度阈值 | `ui/layout/BottomSheetContentLayout.kt:431-436` |
| `renderInRootScaffold` | true | `ui/overlay/OverlayBottomSheet.kt:70` |

弹层动画（`ui/utils/MiuixPopupUtils.kt:93-145`）：

- Dialog：大屏（宽≥840dp 且高≥480dp，`:587-593`）用 fade + scale 0.8，spring(0.9, 438.6)；
  小屏用 `slideInVertically` + spring(0.88, 450)；退出统一 200ms `DecelerateEasing`。
- 遮罩：Dialog fade 300ms in / 250ms out；Popup fade 300ms in / 150ms out。
- Popup：fade 200ms in / 150ms out。

### F.3 `GlassDialog` / `GlassBottomSheet`

- `GlassDialog` 是 `OverlayDialog` 的薄包装，只把 `surface` 换成 Glass 表面（`glass/GlassDialog.kt:72-93`）；
  默认 `containerColor = background`、`ScrimColor = 0x33000000`（`glass/GlassDialogSurface.kt:25-49`）。
- **backdrop 契约（KDoc 原文）**：`glass/GlassDialog.kt:20-21`
  > The backdrop must include the page and bars but exclude the dialog host and its scrim.
- `GlassBottomSheet` 同理（`glass/GlassBottomSheet.kt:75-97`），KDoc `:20-23` 同上。
- 因为宿主在根 Scaffold 的 popup 槽里，**弹层天然在页面录制层之外**，你只要传"页面 + bars 的录制层"即可（见 J）。

### F.4 下拉菜单（preference / glass）

- `GlassDropdownPreference` 内部直接 `PopupLayout(renderInRootScaffold = true)`（`glass/GlassDropdownPreference.kt:148-155`），
  弹层内容 `GlassDropdownPopupContent`（`glass/GlassDropdownPopup.kt:91-203`），
  展开面板圆角 **24.dp**、起始圆角 4.dp（`glass/GlassDropdownPopup.kt:31-38`）。
- KDoc 明确：`backdrop must exclude the popup host to avoid self-sampling`（`glass/GlassDropdownPreference.kt:41`）。
- 普通版（非玻璃）：`pref/preference/OverlayDropdownPreference.kt`、`pref/popup/OverlayDropdownPopup.kt`，
  同样走 `PopupLayout`（同一套宿主）；`Window*` 系列是桌面窗口版，Android 不用。
- 玻璃 icon 菜单/变形菜单：`GlassIconDropdownMenu`、`GlassTransformPopup`、`GlassSecondaryPopup`。
  官方 example 在"页面之上还有 chrome（如悬浮玻璃侧栏）"时，用 `TopLayer { ... }` 把菜单**抬到全窗口层**
  （`example/MainPage.kt:348-370`、`example/GlassPage.kt:590-682`；`example/utils/TopLayer.kt:22-67` 的 KDoc 解释原因：
  glass popup 是画在页面内的窗口坐标，会被 chrome 盖住）。**没有侧栏等覆盖物时不需要 TopLayer。**

### F.5 `GlassFloatingActionButton` 尺寸与位置

- 直径 **56.dp**（`glass/GlassFloatingActionButton.kt:39`），语义按钮、点击涟漪为 null（`:112-118`）。
- 材质 = 顶栏按钮材质 + primary 0.92 叠加（`glass/GlassFloatingActionButton.kt:43-49`），
  fallback fill = primary（`:52-53`）。
- 按下缩放 0.9，spring `(damping=1, response=0.15/0.3)`（`:92-96`、`glass/GlassMotion.kt:145-151`）。
- 内容色自动 `onPrimary`（`glass/GlassFloatingActionButton.kt:124-127`）；官方 example 里图标 24dp（`example/AppContent.kt:846-851`）。
- 位置由 Scaffold 决定：默认 `FabPosition.End`，距底 = 底栏高度 + FAB 高度 + 12dp（A.4）。

---

## G. 列表与行：BasicComponent / Card / SmallTitle / Button / Preference

### G.1 `BasicComponent`（所有设置行、信息行的官方底座）

`ui/basic/Component.kt:124-285`：

| 项 | 值 | 证据 |
|---|---|---|
| 行最小高度 | **56.dp** | `ui/basic/Component.kt:171` |
| 内边距 | `PaddingValues(16.dp)`（`BasicComponentDefaults.InsideMargin`） | `ui/basic/Component.kt:292`、`:175` |
| 图标/文字与中间区间距 | **8.dp** | `ui/basic/Component.kt:211`、`:225`、`:237` |
| endActions 最大宽度 | 剩余宽度的 **60%**（`* 6 / 10`） | `ui/basic/Component.kt:230-232` |
| 标题 | `headline1` = 17sp，Medium；`onBackground` | `ui/basic/Component.kt:90-95`、`:299` |
| 摘要 | `body2` = 14sp；`onSurfaceVariantSummary` | `ui/basic/Component.kt:97-102`、`:313` |
| 底部动作 | 与正文间距 **8.dp** | `ui/basic/Component.kt:280-283` |
| 点击 | `clickable(onClickLabel, role)`，无按压缩放 | `ui/basic/Component.kt:148-160` |
| 自定义布局 | **官方明确要求不要换成 `Row + weight`**（内部 2:5:3 分配 + intrinsic 测量，见 miuix `AGENTS.md`） | 代码 `ui/basic/Component.kt:185-277` |

### G.2 `Card`

`ui/basic/Card.kt:50-67`（无交互版）/ `:87-154`（可点击版）；Defaults `ui/basic/Card.kt:187-212`：

| 项 | 值 | 证据 |
|---|---|---|
| 圆角 | **16.dp** | `ui/basic/Card.kt:192` |
| 内部 padding | **`PaddingValues(0.dp)`（默认 0，不是 16dp）** | `ui/basic/Card.kt:197` |
| 背景 | `surfaceContainer` | `ui/basic/Card.kt:204` |
| 内容色 | `onSurfaceContainer` | `ui/basic/Card.kt:205` |
| 形状实现 | `squircleSurface(color, cornerRadius)` | `ui/basic/Card.kt:179` |
| 按压反馈 | `pressFeedbackType = PressFeedbackType.None` 默认，可选 `Sink` / `Tilt` | `ui/basic/Card.kt:92`、`:110-116` |
| 点击 | `combinedClickable`（支持长按），`indication` 默认关 | `ui/basic/Card.kt:121-136`、`:119` |

**注意**：`CardDefaults.InsideMargin` 在 0.9.5 是 `0.dp`。所以"卡片内 16dp 留白"不是 Card 给的，
而是行组件（`BasicComponent`）自己给 16dp；写 `Card(insideMargin = 16.dp)` 再放 `BasicComponent` 会变成 32dp。

### G.3 `SmallTitle`

`ui/basic/SmallTitle.kt:25-43`：

| 项 | 值 | 证据 |
|---|---|---|
| 文本样式 | `subtitle` = 14sp Bold | `ui/basic/SmallTitle.kt:34`、`ui/theme/TextStyles.kt:201-206` |
| 颜色 | `onBackgroundVariant` | `ui/basic/SmallTitle.kt:28` |
| 内边距 | **`PaddingValues(28.dp, 8.dp)`**（横向 28、纵向 8） | `ui/basic/SmallTitle.kt:42` |

### G.4 `Button`

`ui/basic/Button.kt:50-93`；Defaults `ui/basic/Button.kt:152-174`：

| 项 | 值 | 证据 |
|---|---|---|
| 最小宽/高 | **58.dp / 40.dp** | `ui/basic/Button.kt:158`、`:164` |
| 圆角 | **16.dp** | `ui/basic/Button.kt:169` |
| 内边距 | `PaddingValues(horizontal = 16.dp, vertical = 13.dp)` | `ui/basic/Button.kt:174` |
| 默认容器色 | `secondaryVariant`（浅灰），内容色 `onSecondaryVariant` | `ui/basic/Button.kt:181-184` |
| 主按钮 | `ButtonDefaults.buttonColorsPrimary()` → primary / onPrimary | `ui/basic/Button.kt:198-210` |
| 形状 | `squircleSurface` | `ui/basic/Button.kt:76` |

### G.5 Preference 组件

全部基于 `BasicComponent`，因此**行高 56dp、insideMargin 16dp、标题 17sp Medium、摘要 14sp** 是统一值：

| 组件 | 证据 | insideMargin 默认 |
|---|---|---|
| `ArrowPreference` | `pref/preference/ArrowPreference.kt:47-64` | `BasicComponentDefaults.InsideMargin` = 16dp |
| `SwitchPreference` | `pref/preference/SwitchPreference.kt:43-63` | 同上 |
| `SliderPreference` | `pref/preference/SliderPreference.kt:81-116` | 同上 |
| `RangeSliderPreference` | `pref/preference/SliderPreference.kt:209-243` | 同上 |
| `CheckboxPreference` | `pref/preference/CheckboxPreference.kt:47-69` | 同上 |
| `RadioButtonPreference` | `pref/preference/RadioButtonPreference.kt:50-73` | 同上 |
| `GlassDropdownPreference` | `glass/GlassDropdownPreference.kt:78`、`:107-141` | 同上 |

- `ArrowPreference` 的尾部箭头颜色 = `onSurfaceVariantActions`（`pref/preference/ArrowPreference.kt:112-135`）。
- 可点击行 vs 只读信息行：**官方区分方式就是 ArrowPreference（有箭头=可点）/ BasicComponent（无箭头=信息）**，
  这也是 Globals 的 `enabled=false` 表达（`BasicComponent(enabled = false)` 会把色切到 disabled，`ui/basic/Component.kt:298-320`）。

### G.6 Divider

`ui/basic/Divider.kt`：`HorizontalDivider / VerticalDivider`，默认厚度 **0.75.dp**（`ui/basic/Divider.kt:72`），
颜色 `dividerLine`（`ui/basic/Divider.kt:77`）。列表行之间官方一般**不用分割线**
（行靠 16dp 内边距 + 卡片分组），只在特殊分组用（`example/GlassPage.kt:562`、`:664-670`）。

### G.7 分组卡片（多行一张卡）

官方 example 的 `GroupedCardItems` 用 `squircleSurface` 给首/中/末行不同的圆角，拼出"一张卡里多行"的效果：
`example/component/GroupedCardItems.kt:42`。这是 MIUIX 卡片分组的标准做法（Card 本身不带分割线）。

---

## H. 排印与颜色

### H.1 `TextStyles` 全表（默认值，`ui/theme/TextStyles.kt:146-230`）

| Token | 字号 | 字重 | 行号 |
|---|---|---|---|
| `main` | **17sp** | 默认 | `:146-150` |
| `paragraph` | 17sp | 默认，`lineHeight = 1.2em` | `:152-157` |
| `body1` | **16sp** | 默认 | `:159-163` |
| `body2` | **14sp** | 默认 | `:165-169` |
| `button` | **17sp** | 默认 | `:171-175` |
| `footnote1` | **13sp** | 默认 | `:177-181` |
| `footnote2` | **11sp** | 默认 | `:183-187` |
| `headline1` | **17sp** | 默认（行组件里配 Medium） | `:189-193` |
| `headline2` | 16sp | 默认 | `:195-199` |
| `subtitle` | **14sp** | **Bold** | `:201-206` |
| `title1` | **32sp** | 默认 | `:208-212` |
| `title2` | 24sp | 默认 | `:214-218` |
| `title3` | **20sp** | 默认（折叠标题配 Medium） | `:220-224` |
| `title4` | 18sp | 默认 | `:226-230` |

典型用法（官方组件内部的实际选择，直接照抄）：

| 场景 | Token / 值 | 证据 |
|---|---|---|
| 页面展开大标题 | `title1` 32sp Normal | `ui/basic/TopAppBar.kt:832-833` |
| 页面折叠标题 | `title3` 20sp Medium | `ui/basic/TopAppBar.kt:784-785` |
| 顶栏 subtitle | `body2` 14sp | `ui/basic/TopAppBar.kt:839` |
| 行标题 | `headline1` 17sp Medium | `ui/basic/Component.kt:92-93` |
| 行摘要 | `body2` 14sp | `ui/basic/Component.kt:100` |
| 分节标题 | `subtitle` 14sp Bold | `ui/basic/SmallTitle.kt:34` |
| 按钮文字 | `button` 17sp | `ui/basic/Button.kt:147` |
| 底部导航文字 | 12sp（普通）/ 11dp→sp（玻璃） | `ui/basic/NavigationBar.kt:442`、`glass/GlassNavigationBar.kt:243-245` |
| Tab 文字 | `body1`（普通 TabRow）/ `body2`+`subtitle`（玻璃） | `ui/basic/TabRow.kt:434`、`glass/GlassTabRow.kt:456-467` |
| 搜索输入文字 | `body1` 16sp | `glass/GlassSearchOverlay.kt:392` |

### H.2 `Colors`：层级与浅/深色默认值

`ui/theme/Colors.kt:68-227`（字段）/ `:341-449`（light）/ `:451-...`（dark）。层级语义：

- **主色层**：`primary` / `onPrimary` / `primaryVariant` / `onPrimaryVariant` / `primaryContainer` / `onPrimaryContainer`
- **次色层**：`secondary` / `onSecondary` / `secondaryVariant` / `onSecondaryVariant` / `secondaryContainer` / `onSecondaryContainer`
- **背景层**：`background` / `onBackground` / `onBackgroundVariant`
- **表面层（卡片/行/面板）**：`surface` / `onSurface` / `surfaceVariant` / `surfaceContainer` / `onSurfaceContainer` /
  `surfaceContainerHigh` / `onSurfaceContainerHigh` / `surfaceContainerHighest` / `onSurfaceContainerHighest`
- **文字降级色**：`onSurfaceSecondary`(0xCC) → `onSurfaceVariantSummary`(0x99) → `onSurfaceVariantActions`(0x66)
- **线/遮罩**：`outline` / `dividerLine` / `windowDimming`
- **禁用**：`disabledOnSurface` / `disabledOnSecondaryVariant` / `disabledPrimary*`
- **滑块**：`sliderKeyPoint` / `sliderKeyPointForeground` / `sliderBackground`

关键默认值（浅色 / 深色）：

| Token | 浅色 | 深色 | 证据 |
|---|---|---|---|
| `primary` | `0xFF3482FF` | `0xFF277AF7` | `ui/theme/Colors.kt:342` / `:452` |
| `background` | `White` | `0xFF242424` | `:372` / `:482` |
| `surface` | `0xFFF7F7F7` | `Black` | `:375` / `:485` |
| `onBackground` | `Black` | `0xE6FFFFFF` | `:373` / `:483` |
| `surfaceContainer` | `White` | `0xFF242424` | `:382` / `:492` |
| `onSurfaceContainer` | `Black` | `0xE6FFFFFF` | `:383` / `:493` |
| `onSurfaceVariantSummary` | `0x99000000` | `0x80FFFFFF` | `:379` / `:489` |
| `onSurfaceVariantActions` | `0x66000000` | `0x66FFFFFF` | `:380` / `:490` |
| `outline` | `0xFFD9D9D9` | `0xFF404040` | `:389` / `:499` |
| `dividerLine` | `0xFFE0E0E0` | `0xFF393939` | `:390` / `:500` |
| `windowDimming` | `Black 0.3` | `Black 0.6` | `:391` / `:501` |

### H.3 `MiuixTheme` 的层级

`ui/theme/MiuixTheme.kt:23-97`：

- `MiuixTheme(controller|colors, textStyles) { ... }` 注入 `LocalColors / LocalTextStyles / LocalIndication / LocalContentColor / LocalOverscrollFactory`（`:34-44`）。
- 读取入口：`MiuixTheme.colorScheme`、`MiuixTheme.textStyles`、`MiuixTheme.isDynamicColor`（`:74-97`）。
- `isDynamicColor` 只在 `Monet*` 模式下为 true（`:87-96`），玻璃组件的 tint 选择大量依赖它（如 `glass/GlassNavigationBar.kt:178-182`）。
- **官方要求：颜色永远用 `MiuixTheme.colorScheme.*`，文字永远用 `MiuixTheme.textStyles.*`，不要硬编码**（miuix `AGENTS.md`）。

---

## I. 动画

### I.1 基础 easing / spring 工具（`ui/anim/`、`ui/utils/SpringUtils.kt`）

- `folmeSpring(damping, response, visibilityThreshold)`：`stiffness = (2π / response)²`（`ui/anim/MiuixEasing.kt:15-21`）。
  `damping = 1f` 无回弹；`response` 秒，越小越快。
- `DecelerateEasing(factor)` / `AccelerateEasing(factor)` / `SinOutEasing`（`ui/anim/DecelerateEasing.kt`、`AccelerateEasing.kt`、`SinOutEasing.kt`）。
- 弹簧物理常量：`CRITICAL_DAMPING_RATIO = 1.0f`、`STANDARD_SPRING_PERIOD = 0.4f`、高速时 0.55f
  （`ui/utils/SpringUtils.kt:15-24`）。

### I.2 `GlassMotion` 全家桶（`glass/GlassMotion.kt`）

| 用途 | 值 | 证据 |
|---|---|---|
| 导航指示器前缘 | spring(0.7, 0.4) | `:52-53` |
| 导航指示器后缘 | spring(0.75, 0.5) | `:56-57` |
| 导航拖拽跟随 | spring(1.0, 0.15) | `:60-61` |
| 导航显示/隐藏 | spring(1.0, 0.3) | `:80-81` |
| 默认（segmented 指示器） | spring(0.95, 0.35) | `:84-85` |
| popup 进入/退出 | spring(0.8, 0.28) / spring(0.95, 0.2) | `:130-135` |
| dialog 进入/退出 | spring(0.95, 0.35) / spring(0.95, 0.15) | `:138-143` |
| 按下/释放 | spring(0.99, 0.15) / spring(0.99, 0.3) | `:146-151` |
| scaleIn / scaleOut | spring(0.6, 0.35) / spring(0.75, 0.2) | `:154-159` |
| 导航内容色/数值 | **150ms** `CubicBezierEasing(0.33, 0, 0.67, 1)` | `:165-176` |
| 顶栏按钮浮起 | **350ms** linear | `:119-123` |
| 顶栏遮罩 | **100ms** linear | `:126-127` |
| popup 淡入/淡出 | **80ms / 150ms** linear | `:182-187` |
| 隐藏常量 | `NAV_HIDE_SCALE = 0.6`、`NAV_HIDE_BLUR_DP = 18`、`NAV_SHOW_DELAY_MS = 100` | `:360-367` |
| 按压常量 | `PRESS_SCALE_MIN = 0.9`、`PRESS_INSET_DP = 10` | `:354-358` |
| 搜索转场专属 | 400ms / 150ms+100ms delay（见 C.5、E.2） | `glass/GlassSearchOverlay.kt:215-223` |

### I.3 页面切换：`miuix-nav`

- `NavDriverSpec`（`nav/runtime/NavDriver.kt:72-122`）：`DAMPING_RATIO = 1f`（临界阻尼，无回弹）、
  `STIFFNESS = 146f`、`VISIBILITY_THRESHOLD = 0.0025f`、`PROGRAMMATIC_DURATION_MILLIS = 500`、
  `COMMIT_POSITION_THRESHOLD = 0.5f`、`MAX_FINGER_PROGRESS = 0.999f`。
- `NavMotion`：`commit/cancel` 默认物理 spring（同上），`programmatic` 默认 **500ms tween**
  （`nav/transition/NavMotion.kt:20-88`）。
- `NavTransitions.MiuixDefault`（`nav/transition/NavTransitions.kt:43-57`）：
  - 进入页：`translationX = (-d) * width`（从尾部整页滑入，**按整像素取整**防抖动）；
  - 被覆盖页：`translationX = -coverProgress * width * 0.25`（视差 1/4 宽），`alpha = 1 - 0.1 * coverProgress`。
  - 圆角裁剪 + 暗色 scrim **不在 transition 里**，由 `NavDisplayEffects(enableCornerClip, dimAmount)` 单独控制
    （`example/AppContent.kt:270-284`：小屏 dim 0.5，跨 Activity 风格深色 0.8 / 浅色 0.2）。
- `NavTransitions.Modal`：从底部整页滑入，`opaqueDepth = 2f`（`nav/transition/NavTransitions.kt:66-73`）。
- 分页器切换：官方 example 用 Compose `HorizontalPager` + 自定义 `PagerNavigationSpringSpec`
  （`example/AppContent.kt:975-1017`）——**库不提供分页器**。

### I.4 弹层动画

见 F.2（`ui/utils/MiuixPopupUtils.kt:93-145`）。Glass 下拉菜单另有 `arcBounds/arcPosition/arcFade/arcBlur`
（`glass/GlassMotion.kt:305-346`），`GlassTransformPopup` 有 `transformBounds/transformCenter`（`:225-242`）。

---

## J. Glass 架构：录制层、消费者、官方组件清单

### J.1 录制机制（源码级）

- `Modifier.layerBackdrop(backdrop)` 的 draw 顺序：**先 `drawContent()`，再 `recordLayer(backdrop.graphicsLayer)`**
  （`blur/LayerBackdropModifier.kt:58-61`）。`recordLayer` 把该节点子树重录进一个 `GraphicsLayer`（`blur/internal/LayerRecorder.kt:16-31`）。
- `rememberLayerBackdrop(graphicsLayer) { onDraw }`：`onDraw` 的默认实现是 `drawContent()`，
  可以定制成"先垫底色再画内容"（`blur/LayerBackdrop.kt:38-47`）。
- `LayerBackdrop.drawBackdrop(...)` 用布局坐标把录制层对齐到消费者位置（`blur/LayerBackdrop.kt:71-110`）。
- **红线（官方 KDoc 原文）**：`glass/Glass.kt:49-50`
  > Content behind the glass. This modifier must be **outside the subtree recorded by the backdrop**;
  > placing it inside creates a **sampling cycle**.
  即：消费者不能是录制层的后代（否则录制时又去采自己，Android 侧表现为 `libhwui prepareTreeImpl` 递归/崩溃）。
- **录制层必须有像素**：官方 `rememberBlurBackdrop()` 一律先 `drawRect(surfaceColor)` 再 `drawContent()`
  （`example/utils/PageUtils.kt:293-302`）——否则空白区透明，模糊透明=透明，玻璃上会出"脏斑"。

### J.2 官方 example 的录制层布局（照着搭）

**（1）窄屏宿主（AppContent.CompactScreenLayout，`example/AppContent.kt:636-683`）**

```
Scaffold(contentWindowInsets = safeInsets)
├ bottomBar  → NavigationBar(backdrop = B)        ← B 的兄弟（Scaffold 槽，非 B 子树）
├ FAB        → FloatingActionButton(backdrop = B)← B 的兄弟
└ body
  └ Box(layerBackdrop(B))                        ← B = rememberLayerBackdrop { drawRect(surface); drawContent() }
     └ pager → pages
```

**（2）玻璃示范页（GlassPage，`example/GlassPage.kt`）—— 多层录制**

```
GlassPageHost = Scaffold(contentWindowInsets = 0)   // 只做根弹层宿主（698-706）
└ Box
  ├ Scaffold(layerBackdrop(pageBackdrop))           // 270：整页录制层（含顶栏/底栏）
  │  ├ topBar   → GlassTopAppBar(backdrop = backdrop)      // 337-343：采样"正文层"，不是 pageBackdrop
  │  ├ bottomBar→ GlassNavigationBar(backdrop = backdrop)  // 289-333：同上
  │  └ body → Box(layerBackdrop(backdrop))                  // 447：正文录制层
  │            └ Box(layerBackdrop(wallpaperBackdrop))      // 451：壁纸层（给"列表里的 TabRow"用）
  ├ GlassDialog(backdrop = pageBackdrop)            // 405-437：采样整页层
  ├ GlassBottomSheet(backdrop = pageBackdrop)       // 439-445
  ├ TopLayer { 菜单 }                                // 590-682：抬到 chrome 之上
  └ GlassSearchOverlay(...)                          // 684-694：最后组合 → 最上层
```

三层的分工（官方注释 `example/GlassPage.kt:148-151`）：

- `backdrop`：**正文层** → 给顶栏、底栏、正文里的 TabRow 采样（顶栏/底栏与正文 Box 是兄弟，安全）。
- `pageBackdrop`：**整页层**（Scaffold 级）→ 给 Dialog/BottomSheet 采样（它们由更外层宿主绘制，安全）。
- `wallpaperBackdrop`：**壁纸层** → 给"放在正文列表里的 TabRow"采样（`example/GlassPage.kt:221-223` 注释：
  "List tabs sample the wallpaper to avoid sampling their own recording layer"）。

**（3）主页（MainPage，`example/MainPage.kt`）**

```
Box
├ Scaffold(layerBackdrop(pageBackdrop))          // 202-203：给顶栏菜单采样
│  ├ topBar → BlurredBar(backdrop) → AdaptiveTopAppBar
│  └ body → Box(layerBackdrop(backdrop))          // 278：正文层
└ TopLayer { GlassIconDropdownMenu(backdrop = pageBackdrop) }   // 348-370
```

### J.3 采样关系判定法（给整改用的判据）

对每个 Glass 消费者，按下列顺序判断：

1. 它是不是某个 `layerBackdrop` 节点的**后代**？
2. 它采样的是不是那个节点？→ 是则**必然递归崩溃**，必须换层或换位置。
3. 它采样层里是否有完整像素（含页面底色）？→ 否，先修录制层的 `onDraw`。
4. 它是否会随组件被重组销毁？→ 弹层/浮层要保持常驻组合，否则动画与状态清理丢失（E.2）。

官方提供的"安全位"：

| 消费者 | 官方位置 | 采样层 | 证据 |
|---|---|---|---|
| `GlassTopAppBar` | `Scaffold.topBar` | 正文层（与 body 同级） | `example/GlassPage.kt:337-343` |
| `GlassFloatingActionButton` | `Scaffold.floatingActionButton` | 正文层 | `example/AppContent.kt:658-664` |
| `GlassNavigationBar` | `Scaffold.bottomBar` | 正文层 | `example/GlassPage.kt:289-294` |
| `GlassTabRow` | 顶栏 `bottomContent` | 自动取顶栏 backdrop | `glass/GlassTabRow.kt:309-313` |
| `GlassTabRow` | 正文列表内 | **换一个层**（壁纸层） | `example/GlassPage.kt:221-223` |
| `GlassIconButton` | 顶栏 actions / navigationIcon | 自动取顶栏 backdrop + material | `glass/GlassIconButton.kt:89-103` |
| `GlassDialog` / `GlassBottomSheet` | 页面内任意位置（渲染到根宿主） | 整页层 | `glass/GlassDialog.kt:20-21`、`example/GlassPage.kt:405-445` |
| `GlassDropdownPreference` | 设置行内（渲染到根宿主） | 整页层（**必须排除 popup 宿主**） | `glass/GlassDropdownPreference.kt:39-41` |
| `GlassSearchOverlay` | 页面 Scaffold 之后（兄弟） | **自建内部录制层**，不采外部层 | `glass/GlassSearchOverlay.kt:267-268`、`:298` |

### J.4 `backdrop = null` 的官方回退（必须逐组件守住）

| 组件 | 回退 | 证据 |
|---|---|---|
| `GlassNavigationBar` | `surfaceContainer` 填充 | `glass/GlassNavigationBar.kt:367-370` |
| `GlassTabRow` | 静态填充色（resting colors），无玻璃 | `glass/GlassTabRow.kt:313-321`、`:353-364` |
| `GlassSegmentedTabRow` | `trackColor`（restingTrackColor） | `glass/GlassTabRow.kt:607-615`、`:694` |
| `GlassTopAppBar` | 按钮用 `buttonFill()`（dynamic → surfaceContainerHigh；深色 0xFF2C2C2C；浅色 White） | `glass/GlassTopAppBar.kt:123-129` |
| `GlassIconButton` | 同上 fill + 无 backdrop 时用 `CompositingStrategy.Offscreen` 画 stroke | `glass/GlassIconButton.kt:226-245` |
| `GlassFloatingActionButton` | `primary` 填充 | `glass/GlassFloatingActionButton.kt:52-53` |
| `GlassDialog` | `containerColor`（默认 background） | `glass/GlassDialog.kt:67`、`glass/GlassDialogSurface.kt:49` |
| `GlassBottomSheet` | `containerColor` | `glass/GlassBottomSheet.kt:70` |
| `GlassSearchOverlay` | 搜索条 `surfaceContainerHigh` 胶囊（无 blur band，改渐变） | `glass/GlassSearchOverlay.kt:328-346`、`:367` |
| `GlassDropdownPopup` | 不透明填充 | `glass/GlassDropdownPopup.kt:49-50` |

另外所有 Glass 路径都受 `isRuntimeShaderSupported()` 门控（Android 侧 `SDK_INT >= 33`，
见 `miuix-material` 的 `RuntimeShaderCompat`；本项目 verify 记录写的是 `SDK_INT >= 33`）。

### J.5 官方 Glass 组件清单（`miuix-glass` 模块的文件 = 能力清单）

**有 Glass 版本的组件**（文件名即公开 API）：

`GlassTopAppBar`、`GlassNavigationBar`、`GlassNavigationRail`、`GlassTabRow`、`GlassSegmentedTabRow`、
`GlassFloatingActionButton`、`GlassFloatingToolbar`、`GlassIconButton`、`GlassDialog`、`GlassDialogButton`、
`GlassBottomSheet`、`GlassSearchOverlay`、`GlassTextField`、`GlassDropdownPreference`、`GlassDropdownPopup`、
`GlassIconDropdownMenu`、`GlassTransformPopup`、`GlassSecondaryPopup`、`GlassPopup`/`GlassPopupColumn`/`GlassPopupSurface`、
`GlassPanel`、`GlassSurface`、`GlassPress`（按压缩放工具）。

**没有 Glass 版本、只能用普通版的组件**（官方无）：

- `Scaffold`（无 GlassScaffold）
- `Card`（没有 GlassCard；玻璃卡片在官方 example 里是 `Card` + `textureBlur` 手写，`example/AboutPage.kt:347-371`）
- `Button` / `TextButton`
- `Switch` / `Checkbox` / `RadioButton` / `Slider`（有 `SliderPreference` 等普通版）
- `TopAppBar` 以外的文本类组件、`Snackbar`、`ProgressIndicator`、`Badge`
- 列表网格、空状态、加载态、Toast：**官方无**

---

## K. About / Licenses：官方页面范例

### K.1 `AboutPage.kt`（`example/AboutPage.kt`）

**整体结构**（`example/AboutPage.kt:86-165`）：

```kotlin
Scaffold(
    topBar = {
        Box(Modifier.layerBackdrop(barBackdrop))          // 顶栏单独录制（133）
            BlurredBar(backdrop, blurActive) {
                AdaptiveTopAppBar(
                    title = "About",
                    expandable = false,                    // ← 二级页不折叠（139）
                    scrollBehavior = topAppBarScrollBehavior,
                    titleAlpha = titleAlpha,               // 随滚动 0.35→1 淡入（127）
                    navigationIcon = { BackNavigationIcon() },
                )
            }
    },
) { innerPadding ->
    Box(Modifier.layerBackdrop(backdrop)) {               // 154 正文录制层
        AboutContent(
            scrollPadding = pageContentPadding(innerPadding, padding),          // 156
            logoPadding  = pageContentPadding(innerPadding, padding, extraTop = 40.dp),  // 157
            ...
        )
    }
}
```

**品牌区（`example/AboutPage.kt:233-305`）**：

| 元素 | 数值 | 证据 |
|---|---|---|
| 顶部留白 | `extraTop = 40.dp` + logo 容器下方 52dp | `:157`、`:180-185` |
| 图标容器 | **88.dp**，`RoundedCornerShape(24.dp)`，**白底**（`background(Color.White)`） | `:242-254` |
| 图标位图 | **74.dp** | `:256-260` |
| 应用名 | 35sp **Bold**，`onBackground`，上 12dp 下 5dp | `:262-290` |
| 版本 | 14sp，`onSurfaceVariantSummary`，居中 | `:292-304` |
| 滚动联动 | 图标/标题/版本在不同进度淡出 + 缩到 0.95 | `:246-269`、`:295-299` |

**卡片分组（`example/AboutPage.kt:341-445`）**：

- 每张 `Card`：`Modifier.padding(horizontal = 12.dp)`，第二张另加 `.padding(top = 12.dp)`（`:347-348`、`:396-399`）
  → **页面横向 12dp、卡间距 12dp**。
- 卡片内部放 `ArrowPreference`（`:373-441`），行内边距由 `BasicComponent` 给 16dp。
- 两组：`View Source / Join Group`、`License / Third Party Licenses`。
- 末尾 `Spacer(Modifier.height(12.dp))`（`:442`）。
- 背景特效 Sheet 用 `GlassBottomSheet`，`insideMargin = DpSize(0.dp, 0.dp)` + `LazyColumn` 内自己加
  导航栏底部 spacer（`:456-495`，尤其 `:463`、`:492-493`）。

### K.2 `LicensePage.kt`（`example/LicensePage.kt`）

**结构**（`:46-143`）：

```kotlin
Scaffold(topBar = { BlurredBar(backdrop, blurActive) { AdaptiveTopAppBar("Third Party Licenses", ...) } }) { innerPadding ->
    Box(Modifier.layerBackdrop(backdrop)) {
        LazyColumn(contentPadding = pageContentPadding(innerPadding, padding)) {   // :100-113
            libraries?.let { libs ->
                items(libs, key = { it.uniqueId }) { library ->
                    Card(
                        modifier = Modifier.padding(horizontal = 12.dp).padding(top = 12.dp),   // :117-119
                    ) {
                        ArrowPreference(
                            title = library.name,
                            summary = "${library.artifactVersion}, ${library.licenses.firstOrNull()}", // :121-123
                            onClick = { library.website?.let { uriHandler.openUri(it) } },            // :124-128
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }        // :133-135
        }
        VerticalScrollBar(                                  // :137-141
            adapter = rememberScrollBarAdapter(lazyListState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            trackPadding = contentPadding,
        )
    }
}
```

要点：

- 许可证数据由 **example 自己**读 `aboutlibraries.json` 并用 `SimpleJsonParser` 解析
  （`example/LicensePage.kt:66-78`；`example/utils/AboutLibrariesLoader.kt`、`SimpleJsonParser.kt`）——
  **AboutLibraries 不是 MIUIX 的能力**（官方无）。Glacier 自建许可证清单在同构位置用同样布局即可。
- 每库一张卡 + 一行 `ArrowPreference`，**不要自绘文本列表**。
- 长列表配 `VerticalScrollBar`（`ui/basic/ScrollBar.kt`），`trackPadding = contentPadding`。

### K.3 简单版 About（`SmallTitle` + `Card` + 行）也合法

如果不想做 BgEffect 大背景，用 `SettingsPage` 的语法即可（`example/SettingsPage.kt` 全篇）：
`SmallTitle` 分组标题 → `Card`（横向 12dp，底 12dp）→ `BasicComponent`/`ArrowPreference` 若干。
这也是 Glacier 当前 About 的结构（`glacier/feature/about/AboutScreen.kt:60-141`），方向没错；
差距在**品牌区尺寸/字号**、**卡间距 12dp**、**二级页 `expandable = false`**、**页面顶部大留白**（见第 12 节）。

---

## L. 图标与 squircle（含"官方有没有应用图标组件"）

### L.1 图标画布尺寸（官方事实）

| 图标族 | 画布 | 证据 |
|---|---|---|
| `MiuixIcons`（extended，常规） | **24.dp** | `icons/Settings.kt:23-24`（`defaultWidth = 24.0f.dp`）；`ui/basic/Icon.kt:204`（无内建尺寸时 `Modifier.size(24.dp)`） |
| `MiuixIcons.Glass`（玻璃图标族） | **28.dp** | `core/MiuixIcons.kt:6-18`（"Icons use a 28 dp canvas"）；`glassIcons/SettingsOutline.kt:23-24` |
| 顶栏/底栏/玻璃组件里的实际用法 | 顶栏 28dp、玻璃图标按钮内容 28dp、FAB 24dp、底部导航 25dp | `example/GlassPage.kt:357`/`:370`、`glass/GlassNavigationBar.kt:125`、`example/AppContent.kt:846-851` |
| 普通 `NavigationBar` 图标 | 26dp | `ui/basic/NavigationBar.kt:439` |

> 官方**没有**"行内图标尺寸"token：`BasicComponent.startAction` 是任意 composable，尺寸由调用方给。
> 常见取 24dp（`example/MainPage.kt:234-238` 的菜单触发图标）或与所在组件对齐。

### L.2 `miuix-squircle` API 与默认值

`miuix-squircle` 是纯形状库（无图标组件）：

| API | 语义 | 证据 |
|---|---|---|
| `Modifier.squircleBackground(color, cornerRadius, extension)` | **只填充，不裁剪子树** | `squircle/SquircleBackground.kt:39-55` |
| `Modifier.squircleClip(cornerRadius, extension)` | 裁剪子树到 squircle 轮廓 | `squircle/SquircleBackground.kt:85-99` |
| `Modifier.squircleSurface(color, cornerRadius, extension)` | 填充 + 裁剪（`clip(RoundedCornerShape)+background` 的 drop-in） | `squircle/SquircleBackground.kt:124-138` |
| `Modifier.squircleBorder(width, color, cornerRadius, extension)` | 描边（按半线宽内缩，和同半径的 fill 对齐） | `squircle/SquircleBorder.kt:17-65` |
| 四角独立重载 | `squircleBackground/squircleClip/squircleSurface` 都有 `topStart/topEnd/bottomEnd/bottomStart` 版本 | `squircle/SquircleBackground.kt:68-83`、`:111-122`、`:151-159`；`squircle/GroupedCard` 用法 `example/component/GroupedCardItems.kt:42` |
| `SquircleDefaults.Extension` | **1.1f**（1.0 = 圆弧；范围 1..2） | `squircle/SquirclePath.kt:14-23` |
| 启用条件 | `isSquircleEnabled() = LocalSquircleEnabled.current && isRuntimeShaderSupported()`；false 时全部回退 `RoundedCornerShape` | `squircle/LocalSquircleEnabled.kt:12-27` |
| Card/TabRow/Button/NavigationBar 内部形状 | 全部走 squircle（`squircleSurface` / `squircleBackground` / `squircleBorder`） | `ui/basic/Card.kt:179`、`ui/basic/TabRow.kt:175/317/329/405`、`ui/basic/Button.kt:76`、`ui/basic/NavigationBar.kt:343` |

官方 example 的图标网格用法（半径参照）：

- 图标网格容器：`squircleClip(16.dp)`（首/末行才给圆角）+ `background(surfaceContainer)`（`example/IconPage.kt:383-398`），
  单元自身 `squircleClip(12.dp)`（`:403-411`），图标 24dp/28dp（`:414-419`）。

### L.3 应用图标：官方**无**组件

- MIUIX **没有** `AppIcon` / 应用图标组件，也没有 `AdaptiveIconDrawable` 遮罩处理、没有"应用图标圆角半径"token（全库 grep `AppIcon` 仅命中无相关组件）。
- 官方能提供的是 **squircle 形状 API**（L.2）与圆角矩形视觉语言；第三方应用图标的
  `background/foreground` 分层、系统遮罩规避、图标包解码都必须由应用自己实现。
- 官方 example 里唯一的"应用图标"是 About 页的 launcher 图标，处理方式是
  `Box(88.dp).graphicsLayer { clip = true; shape = RoundedCornerShape(24.dp) }.background(Color.White)` + `Image(74.dp)`
  （`example/AboutPage.kt:242-261`）——**白底 + 圆角矩形 + 居中位图**，不是圆形裁切。

---

## 12. 与 Glacier 当前实现的明显冲突（研究结论，供整改参考）

> 只列静态阅读能确认的冲突，全部带双方证据。整改时按"官方 example 优先"处理。

### 冲突 1：`pageContentPadding` 用"相加"而非官方"取 max"，且把导航胶囊高度加了两遍

- Glacier：`glacier/ui/components/PageFrame.kt:265-280`
  ```kotlin
  PaddingValues(
      start = horizontal, end = horizontal,
      top = scaffoldPadding.calculateTopPadding() + extraTop,
      bottom = scaffoldPadding.calculateBottomPadding() + navReserve + extraBottom + 24.dp,  // 相加
  )
  ```
  而 `navReserve`（Shell 计算）= `GlassNavigationBarDefaults.Height + navigationBars.bottom + 32.dp`
  （`glacier/ui/GlacierShell.kt:147-148`），`GlassNavigationBar` 自己默认还会再加底部 insets
  （`glass/GlassNavigationBar.kt:305-324`，Glacier 调用处没传 `defaultWindowInsetsPadding = false`，
  `glacier/ui/GlacierShell.kt:224-231`）→ **底栏实际高度被重复预留**。
- 官方：`pageContentPadding` 取 `maxOf(innerPadding, outerPadding, pageInsets)`（`example/utils/PageUtils.kt:186-222`），
  且底栏在 `Scaffold.bottomBar` 时 `innerPadding.bottom` 已等于底栏高度（`ui/basic/Scaffold.kt:338-343`）。

### 冲突 2：底部玻璃导航不在 `Scaffold.bottomBar`，而是浮在 Shell 最外层

- Glacier：`glacier/ui/GlacierShell.kt:209-232` 把 `GlassNavigationBar` / `GlassNavigationRail` 放在
  根 `Box` 里 `align(BottomCenter)`；页面完全不知道它的高度，只能靠 `LocalNavReserve` 手工补
  （`glacier/ui/GlacierShell.kt:147-155`、`glacier/ui/components/PageFrame.kt:139-146` 还给 FAB 额外补了 `reserve - 20.dp`）。
- 官方：GlassPage 把 `GlassNavigationBar` 放进 `Scaffold.bottomBar`（`example/GlassPage.kt:272-334`），
  FAB 由 Scaffold 自动摆在底栏之上（`ui/basic/Scaffold.kt:261-269`）。
- 影响：正文底部留白、FAB 位置、横竖屏切换三者都要手工对齐，且容易双算。**建议把导航胶囊下沉到每个页面
  PageFrame 的 `bottomBar` 槽**（录制层仍可用根层），或至少让 `PageFrame` 接收真实底栏高度而不是自己猜。

### 冲突 3：窄屏玻璃导航**没有 24dp 横向内边距**

- Glacier：`glacier/ui/GlacierShell.kt:224-231` 只传 `Modifier.align(Alignment.BottomCenter)`，没有横向 padding。
- 官方：`GlassNavigationBar` 的两次官方调用都带 24dp：
  `.padding(horizontal = 24.dp)`（`example/AppContent.kt:770-774`）或
  bottomBar 容器 `padding(start = 24.dp, end = 24.dp, top = 20.dp)`（`example/GlassPage.kt:277`）。
  `GlassNavigationBar` 自身只有 8dp 的内部横向 padding（`glass/GlassNavigationBar.kt:143`）。

### 冲突 4：`GlassSearchOverlay` 用 `if (searchOpen)` 条件组合

- Glacier：`glacier/feature/home/HomeScreen.kt:215-231` 与 `glacier/feature/apps/AppsScreen.kt:96-112`
  都是 `if (searchOpen) { GlassSearchOverlay(expanded = true, ...) }`。
- 官方：浮层**常驻组合**，`expanded` 控制展开；`query` 清空、焦点/键盘清理、返回键、退出动画都依赖
  `LaunchedEffect(expanded)` 与 `if (!active) return` 的生命周期
  （`glass/GlassSearchOverlay.kt:229-240`）。条件组合会让"关闭动画、键盘收起、query 归零"全部丢失，
  且每次打开是新实例（状态归零看着正常，但退出是瞬断）。
- 另外：Glacier 没有传 `topInsets` / `horizontalInsetsModifier` / `searchTopPadding`
  （HomeScreen 调用只传了 label/blurEnabled），官方 example 三个都传
  （`example/GlassPage.kt:687-692`）——顶部 safe area 与折叠侧栏让位会不准。

### 冲突 5：搜索态只淡出顶栏，且"整页 alpha"和"顶栏转场"叠了两层

- Glacier：`PageFrame` 只给 topBar 套 `graphicsLayer { alpha = searchBarAlpha.value }`
  （`glacier/ui/components/PageFrame.kt:117-136`），同时 `PageTopBar` 内部又用
  `glassSearchBarTransition(...)`（`:190-194`）→ 同一次转场两个 alpha 相乘。
- 官方：整页 `Scaffold.modifier` 上 `graphicsLayer { alpha = searchBarAlpha.value }` + `clearAndSetSemantics`
  （`example/GlassPage.kt:266-271`），顶栏只负责 `glassSearchBarTransition`（`:339-340`），分工不重叠。
- 关于 "× 点到标签编辑"：当前 Glacier 的浮层已经是 `Scaffold` 的兄弟（`PageFrame.kt:113-166`），
  静态阅读下 `onDismissRequest` 路径正确（`HomeScreen.kt:219`）、浮层 scrim 也会挡点击
  （`glass/GlassSearchOverlay.kt:260-266`），所以**这个 BUG 很可能已随层级修正消失**；
  但条件组合（冲突 4）会让"点 × 后关闭不干净"的观感复现。必须按任务书第五阶段跑真机验证。

### 冲突 6：顶栏玻璃按钮里的图标用 24dp，官方 example 用 28dp

- Glacier：`glacier/ui/components/PageFrame.kt:219-235`（`TopBarAction`）里 `Icon(modifier = Modifier.size(24.dp))`。
- 官方：玻璃顶栏按钮内一律 28dp（`example/GlassPage.kt:357`、`:370`、`:388`；`docsDemo/GlassSearchOverlayDemo.kt:121-124`），
  图标画布本身就是 28dp（`core/MiuixIcons.kt:9-10`）。24dp 会把玻璃图标族裁小一圈。

### 冲突 7：分节间距 6dp，官方是 12dp

- Glacier：`PageMetrics.SectionGap = 6.dp`（`glacier/ui/components/PageFrame.kt:55-56`），About 页用它做
  `verticalArrangement.spacedBy(...)`（`glacier/feature/about/AboutScreen.kt:70`）。
- 官方：卡片之间 **12dp**（`example/MainPage.kt:312`、`:331`、`:339`；`example/AboutPage.kt:348`、`:399`、`:442`；
  `example/LicensePage.kt:118-120`、`:134`）。`SmallTitle` 另有 8dp 上下内边距（`ui/basic/SmallTitle.kt:42`）。

### 冲突 8：About 品牌区尺寸/字号偏小，且二级页仍可折叠大标题

- Glacier：图标容器 80dp / 图标 56dp / 圆角 18dp，应用名 `title1` 32sp，副标题 `footnote1` 13sp
  （`glacier/feature/about/AboutScreen.kt:159-181`）；`PageFrame` 没有暴露 `expandable`
  （`glacier/ui/components/PageFrame.kt:171-209`），`GlassTopAppBar` 默认 `expandable = true`
  （`glass/GlassTopAppBar.kt:272`）→ About/Licenses 会展开 32sp 大标题并与品牌区重复。
- 官方：图标容器 88dp / 图标 74dp / 圆角 24dp / 白底，应用名 35sp Bold，版本 14sp
  （`example/AboutPage.kt:242-304`）；二级页 **`expandable = false`**（`example/AboutPage.kt:139`，
  非玻璃档 `SmallTopAppBar` 的钉死行为见 `ui/basic/TopAppBar.kt:195-203`）。

### 冲突 9：`PageFrame` 的 `GroupCard` 传了 `insideMargin = 0.dp`，但 About 里再套 `BasicComponent`

- Glacier：`GroupCard` 固定 `insideMargin = PaddingValues(0.dp)`（`glacier/ui/components/PageFrame.kt:288-300`），
  行组件自带 16dp（`ui/basic/Component.kt:175`）→ 这个组合其实是**对的**。
- 但 Licenses 页历史写法 `Card(insideMargin = 16.dp)` + 自绘文本（上一轮审计记录）会产生 16+16；
  核对规则：**卡片内只允许一层 16dp（行组件给），Card 自身 insideMargin 保持 0**（`ui/basic/Card.kt:197`）。

### 冲突 10：其它散点

- FAB 图标 24dp 是对的（`example/AppContent.kt:846-851`）；但 Glacier 的 FAB 额外 `reserve - 20.dp` 补底
  （`glacier/ui/components/PageFrame.kt:139-146`）——冲突 2 的补丁，应随导航下沉一并删除。
- 玻璃侧栏折叠形态与页面玻璃顶栏重叠：Glacier 用 `showToggle = false` 常驻展开规避
  （`glacier/ui/GlacierShell.kt:211-222`）。官方 example 的折叠形态设计为"变成顶部胶囊即导航栏本身"
  （`example/AppContent.kt:215-247` 用 `sidebarState.progress` 驱动顶栏标题透明度；
  `glass/GlassNavigationRail.kt` 的 KDoc 描述折叠态），
  两套导航同时存在时确实会打架——**Glacier 的取舍可以保留，但要在审计文档写明"官方支持、本项目不用"**。
- `progressiveBlur = !Platform.isEmulator`（`glacier/ui/components/PageFrame.kt:188`）是证据分级取舍，
  官方无此判断（`glass/GlassTopAppBar.kt:403-411` 只在 shader 不支持时退化），保留但必须在 L3 证据里标注"模拟器不可信"。

---

## 13. Glacier 最容易踩的 10 个官方约束（背诵版）

1. **导航胶囊永远带 24dp 横向内边距**，且它自身内部只有 8dp；贴边调用不是官方用法
   （`example/AppContent.kt:774`、`example/GlassPage.kt:277`、`glass/GlassNavigationBar.kt:143`）。
2. **导航栏要放进 `Scaffold.bottomBar`**，正文 `innerPadding.bottom` 才会自动等于底栏高度；
   浮在 Scaffold 外再手算 = 双算 insets + FAB 位置错（`ui/basic/Scaffold.kt:338-343`、`:261-269`）。
3. **浮层必须画在 `Scaffold` 之后/之外**（`Box { Scaffold(...); Overlay(...) }`）。
   Scaffold 的 z 序是 `body → topBar → snackbar → bottomBar → fab → popup`，写进 body 的浮层一定在顶栏下面
   （`ui/basic/Scaffold.kt:354-380`）。
4. **顶栏淡出（alpha=0）仍然参与命中测试**：`graphicsLayer{alpha=0}` 不改变 hit test。
   靠浮层自己的满屏 scrim 挡点击（`glass/GlassSearchOverlay.kt:260-266`），别指望"透明了就点不到"。
5. **录制层与消费者不能是父子**：`Modifier.glass` 的 KDoc 原文就是 "must be outside the subtree recorded by the backdrop;
   inside creates a sampling cycle"（`glass/Glass.kt:49-50`）。
   TabRow 放进正文列表时，必须换一个层（壁纸层），不能采样它自己所在的那层（`example/GlassPage.kt:221-223`）。
6. **录制层必须满宽、且先垫底色再录内容**：`rememberLayerBackdrop { drawRect(surface); drawContent() }`；
   内容层可以做横向 inset，录制层不行（`example/utils/PageUtils.kt:293-302`、`example/AppContent.kt:628`）。
7. **弹层/浮层要常驻组合**：`expanded` 控制显隐，不要 `if (show)` 直接销毁；否则退出动画、键盘收起、
   query 清空、返回键注册全部丢失（`glass/GlassSearchOverlay.kt:229-240`、`ui/utils/MiuixPopupUtils.kt:216-231`）。
8. **搜索的"清空"与"取消"是两个回调**：Clear 只清 query，Cancel 只调 onDismissRequest，绝不合并
   （`glass/GlassSearchOverlay.kt:444-459` vs `:490-503`）。
9. **页面横向 12dp、卡间距 12dp、Card 自身 insideMargin 是 0、行组件 insideMargin 16dp、行高 56dp**：
   只允许一层 16dp，多一层就是 32dp（`ui/basic/Card.kt:197`、`ui/basic/Component.kt:171`、`:292`、
   `example/LicensePage.kt:118-120`）。
10. **二级页顶栏 `expandable = false`；玻璃顶栏内部图标 28dp；状态栏 top inset 顶栏永远自己加**：
    （`example/AboutPage.kt:139`、`ui/basic/TopAppBar.kt:890`、`core/MiuixIcons.kt:9-10`）。

---

## 14. 附：官方"没有"的能力清单（不要浪费时间找）

| 能力 | 状态 | 依据 |
|---|---|---|
| GlassScaffold | **官方无** | `miuix-ui/basic/Scaffold.kt` 只有一个 Scaffold；Glass 靠组件 `backdrop` 参数 |
| GlassCard | **官方无** | `miuix-glass` 无 Card；官方 example 用 `Card` + `textureBlur` 手写（`example/AboutPage.kt:347-371`） |
| AppIcon / 应用图标组件 / 自适应图标遮罩处理 | **官方无** | 全库无该组件；只有 squircle 形状（L.2/L.3） |
| 页面级 spacing token（page padding / section gap 常量） | **官方无** | example 里直接写 12dp；各组件有自己的 Defaults |
| 空状态 / 加载态 / 错误态组件 | **官方无** | 只在 example 里用 `Box+Text`；`ProgressIndicator` 是基础组件 |
| 启动器网格 / 应用列表组件 | **官方无** | example 用 Compose `LazyVerticalGrid`（Mishka 等外部项目自建） |
| 分页器组件 | **官方无** | example 用 `HorizontalPager` + 自定义 spring（`example/AppContent.kt:975-1017`） |
| 搜索查询状态提升 API | **官方无** | `GlassSearchOverlay` 的 `query` 是内部状态，页面只能读 `content(query)`，不能外部清空 |
| AboutLibraries 集成 | **官方无** | example 自己解析 `aboutlibraries.json`（`example/LicensePage.kt:66-78`） |
| Snackbar 的 Glass 版本 | **官方无** | 只有普通 `Snackbar` + `SnackbarHost` |
| 导航栏/顶栏的"第二套自定义尺寸参数" | **官方无** | 所有尺寸都在各 `*Defaults`；不要在调用侧另造一套 |
