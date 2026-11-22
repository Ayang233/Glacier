# Glass 契约：三条红线与 Glacier 的落点

本文件是 Glacier 的 Glass 结构约束。任何新增玻璃组件都必须先在这里找到它属于哪一类，
并在"落点"一栏写明它采样哪一层。

## 红线

1. **消费者不能是录制层的后代。**
   录制层在绘制时会把子树的绘制命令录进去；子节点再去采样这个 layer 就是递归，
   Android 侧表现是 `libhwui` 的 `prepareTreeImpl` 栈溢出、`Fatal signal 11`。

2. **采样源必须在消费者的整个绘制区域里都有像素。**
   页面录制层必须"先 `drawRect(页面真实底色)`，再 `drawContent()`"；
   否则空白区域是透明的，模糊透明等于透明，玻璃上会出现脏斑。

3. **尺寸与坐标只在量测/布局/绘制阶段读取。**
   逐帧动画值（如侧栏 morph 进度）不能在组合期解包成 `Dp` 再 `padding`，
   否则 300ms 展开过程中整页重组。

## Glacier 的树

```
Scaffold(containerColor = Transparent, contentWindowInsets = 0)     ← 弹层宿主
├ bottomBar（窄屏）→ GlassNavigationBar(rootBackdrop)              ← R 的兄弟，24dp 横向内边距
└ Box(fillMaxSize)
   ├ Box(fillMaxSize).layerBackdrop(rootBackdrop)                   ★R 满屏录制层
   │  │   (rootBackdrop 先 drawRect(surface))
   │  └ NavDisplay(Modifier.fillMaxSize())                          ← 容器满宽，让位只在内容层
   │     ├ Route.Main → HorizontalPager
   │     │   └ 页面 PageFrame
   │     │      Box                                                  ← 浮层宿主
   │     │      ├ Scaffold(containerColor = surface, insets = 0)
   │     │      │  ├ topBar  → GlassTopAppBar(pageBackdrop)          ← 顶栏与 P 同级
   │     │      │  ├ bottomBar → Spacer(LocalShellBottomBarHeight)   ← 透明占位：正文/FAB 让位
   │     │      │  ├ fab     → GlassFloatingActionButton(pageBackdrop)
   │     │      │  └ body    → Box.layerBackdrop(pageBackdrop)       ★P 页面录制层
   │     │      │              └ 列表 / 网格
   │     │      └ overlay()                                          ← 浮层，画在顶栏之上
   │     └ Route.About / Route.Licenses（同上）
   └ GlassNavigationRail(backdrop = rootBackdrop)（宽屏）            ← R 的兄弟
```

弹层（`GlassDialog` / `GlassBottomSheet` / `GlassDropdownPreference`）通过
`renderInRootScaffold = true` 由最外层 Scaffold 的 `MiuixPopupHost` 绘制，
因此天然在 R 之外，采样 R 就一定是兄弟关系。

## 每个消费者的落点

| 消费者 | 位置 | 采样源 | 兄弟关系 |
|---|---|---|---|
| `GlassTopAppBar` | 页面 `Scaffold.topBar` | P | 与 P 同父（Scaffold 的两个子槽） |
| `GlassFloatingActionButton` | 页面 `Scaffold.floatingActionButton` | P | 与 P 同父 |
| `GlassNavigationBar` | Shell `Scaffold.bottomBar`（窄屏） | R | 与 R 同父（槽位在 R 之后绘制） |
| `GlassNavigationRail` | Shell `Box` 的第二个子节点 | R | 与 R 同父 |
| `GlassDialog` / `GlassBottomSheet` | 页面 `overlay` 槽（实际渲染在根 Scaffold） | R | 在 R 之外 |
| `GlassSearchOverlay` | 页面 `overlay` 槽（在 `Scaffold` 之后组合） | 自建内部录制层 | 组件自包含，不采外部层；位置在顶栏之上 |
| `GlassTabRow`（Home 标签） | 顶栏 `bottomContent` | 顶栏上下文（`LocalGlassTopAppBarContext`） | 与正文 P 无关，安全 |
| `GlassIconButton`（顶栏内） | 顶栏子树 | 继承顶栏 | 由库的 `LocalGlassTopAppBarContext` 处理 |

## 两个已知陷阱（本项目实际踩过）

### 1. 页签不能塞进正文列表

第一版把 `GlassTabRow` 放在 `LazyVerticalGrid` 的第一个 item 里，采样 `pageBackdrop`。
它自己就在 P 的子树里 → 首次渲染即 `prepareTreeImpl` 递归崩溃。

官方 example 对这一点的注释是决定性的：

```kotlin
// List tabs sample the wallpaper to avoid sampling their own recording layer.
val tabBackdrop = if (tabsInTopBar) backdrop else wallpaperBackdrop
```

**第二轮的修正**：本版 MIUIX 的 `GlassTopAppBar.bottomContent` 已经稳定（`GlassTabRow`
会从 `LocalGlassTopAppBarContext` 取顶栏的 backdrop 与 `underlayMaterial`），
因此标签栏搬到顶栏 `bottomContent`，玻璃档直接用 `GlassTabRow`，非玻璃档用普通 `TabRow`。
这既是官方 example 的用法，也不再需要在正文列表里放导航控件。

### 2. 让位不能在组合期解包，也不能挪动容器

官方 Glass 迁移指南 §4.2 与 `AppContent.kt:330-370` 是同一条要求：
**`NavDisplay` 保持 `fillMaxSize()`，侧栏宽度只通过内容让位下发**。
早期版本用 `Modifier.layout` 把整个 `NavDisplay` 右移（`railStartInset`），
背景与顶栏模糊带会被硬截断，R6 已删除。

现在的落点是 `ui/PageInsets.kt`（官方 `utils/PageUtils.kt` 的逐字节移植）：

```
AppPageInsets(overlayStart = { (rail.Margin + rail.ExpandedWidth) * progress })
  ├ contentInsets   → Modifier.appPageHorizontalInsets() / pageContentPadding(...)
  └ safeInsets      → Modifier.appPageTopBarInsets() / rememberAppSafePadding()
```

两个仍然有效的坑：

- 让位必须带 `if (wide)`。窄屏没有侧栏，若还读 `railState.progress`（旋转前留下的 1.0），
  竖屏内容会被挤到屏幕右侧（实测现象：整页缩到右侧、标题折行）。
- 侧栏状态用 `rememberGlassNavigationRailState` 内部是 `rememberSaveable`，
  竖屏时的 `false` 会被带到横屏 → 侧栏以折叠胶囊出现在顶部。必须
  `LaunchedEffect(expandedRail) { railState.expanded = expandedRail }` 同步。

`contentInsets` / `safeInsets` 内部的 `overlayStart()` / `searchTop()` 只能在
量测 / 布局 / 绘制阶段读取：`pageContentPadding` 返回惰性 `PaddingValues`，
顶栏与搜索浮层把 modifier 交给布局，组合期不解包成 `Dp`。

## 回退策略

- **唯一决策点**：`LocalGlassActive = 用户开关 && isRuntimeShaderSupported()`。
  页面里不再出现第二个 `isRuntimeShaderSupported()` 判断。
- **每个组件都能 `backdrop = null` 工作**：官方组件自身走不透明回退；
  `GlassTabRow` 在非玻璃档换回官方普通 `TabRow`。
- **模拟器**：`Platform.isEmulator` 为真时顶栏关闭 `progressiveBlur`，
  走库自带的 band 渐变，不自绘替代视觉。
