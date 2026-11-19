# Glacier MIUIX OS4 整改 · 实施规范（R3）

本文件是实现成员的**唯一执行口径**。先读：

1. `docs/reference/miuix-os4-contract.md`（官方事实，全部带 file:line）
2. `docs/reference/reference-projects.md`（参考项目与许可证）
3. `docs/audit/miuix-os4-audit.md`（差异清单与 P0/P1/P2）

工作区：`C:\Users\10487\WorkBuddy\Hail`；Glacier 工程：`Glacier/`；MIUIX 源码：`miuix/`（HEAD 58b0a3d / 0.9.5）。
**禁止运行 gradle 构建**（由 lead 统一构建）；**禁止修改业务层**（`data/`、`domain/`、`system/`、`app/`、`platform/` 中除 `AppIcons.kt` 与 `UiFeedback.kt` 外的文件）；**禁止新增依赖**。

---

## 0. 三条不可违背的红线

1. **浮层（搜索、Dialog、BottomSheet、操作面板）不允许用 `if (show)` 包裹组合**。官方组件用 `show` 参数播放退出动画；`if` 包裹会砍掉退出动画、query 清理、键盘收起、返回键处理。必须“常驻组合 + show/expanded 驱动”，并保留最后一次内容用于退场：
   ```kotlin
   var last by remember { mutableStateOf<T?>(null) }
   if (state != null) last = state
   GlassDialog(show = state != null, ...) { last?.let { ... } }
   ```
2. **禁止自造 MIUIX 已有能力**：列表行用 `BasicComponent`、分组卡用 `Card`、标题用 `SmallTitle`、设置行用 Preference、弹层用 `GlassDialog`/`GlassBottomSheet`、下拉用 `GlassDropdownPreference`、菜单用官方 menu、动画用官方 `MiuixScrollBehavior`/`GlassMotion`。搜索/标签编辑/操作面板等状态与回调必须互相独立。
3. **禁止随手写 dp/sp/颜色**。只能用：官方组件默认值、官方 token（`MiuixTheme.textStyles.*`、`MiuixTheme.colorScheme.*`、`*Defaults.*`）、或本文件第 3 节的 `PageMetrics` 常量。新增常量必须先在本文件登记理由。

---

## 1. 目标骨架（lead 负责实现，其他成员按此 API 调用）

### 1.1 Shell（`ui/GlacierShell.kt`）

```
Scaffold(containerColor = Transparent, contentWindowInsets = WindowInsets(0))
├ contentWindowInsets = 0  → 系统 inset 由各页 Scaffold / 顶栏自己处理
├ bottomBar（窄屏）= 官方 GlassNavigationBar
│   Column(fillMaxWidth + 官方 24dp 横向内边距 + 20dp 顶部间距 + 底部安全区 layout)
│   └ GlassNavigationBar(defaultWindowInsetsPadding = false, backdrop = rootBackdrop)
│   实测高度 → LocalShellBottomBarHeight（页面用它在自己的 Scaffold 里让位、FAB 自动上浮）
├ 宽屏：GlassNavigationRail 覆盖在起始侧（无 bottomBar）
└ Box(fillMaxSize).layerBackdrop(rootBackdrop) → NavDisplay（录制层满宽，内容让位只发生在内容层）
```

- 底栏必须：`fillMaxWidth().padding(horizontal = 24.dp)`（官方 example `AppContent.kt:774`、`GlassPage.kt:277`），**绝不加第二层自定义宽限**。
- 页面不再使用 `LocalNavReserve` 这种“猜高度”的让位；改用 `LocalShellBottomBarHeight`（实测值）。
- 页面切换动画：分页器用官方 `PagerNavigationSpringSpec` fling + 官方 `MiuixScrollBehavior`/`pageScrollModifiers` 语义；`beyondViewportPageCount` 保持现有值即可。

### 1.2 PageFrame（`ui/components/PageFrame.kt`，lead 负责）

```kotlin
@Composable
fun PageFrame(
    title: String,
    scrollBehavior: ScrollBehavior,
    modifier: Modifier = Modifier,
    subtitle: String = "",
    expandable: Boolean = true,                    // 宽屏二级页传 false（官方 AdaptiveTopAppBar）
    navigationIcon: @Composable () -> Unit = {},
    topBarActions: @Composable RowScope.() -> Unit = {},
    topBarBottom: @Composable (LayerBackdrop?) -> Unit = {},
    floatingActionButton: @Composable (LayerBackdrop?) -> Unit = {},
    searchExpanded: Boolean = false,
    overlay: @Composable () -> Unit = {},          // 常驻组合；PageFrame 把它放在 Scaffold 之后
    content: @Composable (PaddingValues) -> Unit,
)
// 新增（供页面/浮层使用）
val LocalShellBottomBarHeight: Dp    // Shell 底栏实测总高（含安全区）
val LocalTopBarTitleAlpha: () -> Float // 宽屏侧栏展开时顶栏标题淡出，PageFrame 内部已接
val LocalSearchTopInset: () -> Dp    // 宽屏折叠胶囊为搜索浮层让出的顶部高度（窄屏恒 0）
fun pageContentPadding(
    scaffoldPadding: PaddingValues,
    horizontal: Dp = PageMetrics.Horizontal,
    extraTop: Dp = 0.dp,
    extraBottom: Dp = 0.dp,
): PaddingValues
```

- PageFrame 的 Scaffold：`contentWindowInsets = WindowInsets(0)`；`bottomBar = { Spacer(Modifier.height(LocalShellBottomBarHeight.current)) }`（透明占位：正文底部 padding = 底栏真实高度，FAB 由 Scaffold 自动上浮 12dp，**删除 `reserve - 20.dp` 之类魔法值**）。
- `pageContentPadding` 语义 = 官方“取 maxOf 不是相加”：
  - start/end = `max(horizontal, 页面横向安全区)`（刘海/侧栏）
  - top = `scaffoldPadding.top + extraTop`
  - bottom = `scaffoldPadding.bottom + PageMetrics.BottomGap + extraBottom`
- 顶栏：`expandable` 下传；玻璃档 `GlassTopAppBar`，非玻璃档 `TopAppBar`；搜索态用官方 `rememberGlassSearchBarAlpha` + `glassSearchBarTransition`；顶栏动作图标 **28dp**（官方 example），`TopBarAction` 玻璃档用 `GlassIconButton`。
- 顶栏 `bottomContent` 用于页签（Home 的 TagRow），保持“顶栏子级”的采样关系（GlassTabRow 自动继承顶栏 backdrop）。
- `overlay` 永远在 Scaffold 之后组合（绘制/命中都在顶栏之上）。**不要**把浮层放回 body 槽。

### 1.3 页面统一结构

每页 = `PageFrame(...) { padding -> LazyColumn/LazyVerticalGrid(modifier = fillMaxSize(), contentPadding = pageContentPadding(padding), verticalArrangement = spacedBy(PageMetrics.SectionGap)) {...} }`
- 内容左右留白统一 12dp（`PageMetrics.Horizontal`），**不再出现 16dp 硬编码**。
- 卡片之间间距统一 12dp（`PageMetrics.SectionGap`），不再 6dp/8dp/18dp 混用。
- 顶栏大标题在宽屏自动折叠（`expandable = !wide`，由 lead 在 Shell/PageFrame 侧处理）。
- 空状态必须吃 `padding`（不能从 y=0 起排）。

---

## 2. 页面级整改要点（按成员分工）

### 2.1 Home（`feature/home/HomeScreen.kt`）
- 标签页签：`TabRow`/`GlassTabRow` 官方组件（保持），但**外层 padding 只留 12dp**，与页面留白一致；行高/间距用官方默认。
- 应用网格：`LazyVerticalGrid` + `GridCells.Fixed(4/5)`，**单元尺寸/间距按 12dp 网格**：图标 56dp、图标与标签 6dp、水平 spacing 8dp、垂直 spacing 16dp；标签用 `footnote1`，`maxLines=1`，`textAlign=center`。选中态用官方 squircle 描边（沿用 `AppIconView(selected=)`）。
- 图标不再用 `clip(RoundedCornerShape(18.dp))` 手搓按压缩放；按压反馈用 `combinedClickable` 即可（保留长按多选），但点击热区必须覆盖整个单元格（含标签）。
- 搜索：`GlassSearchOverlay` **常驻组合**，`expanded = searchOpen`；补 `horizontalInsetsModifier`、`searchTopPadding`（宽屏让开侧栏）、`scrollMaterialThreshold`/`scrollOffset`（结果列表滚动时搜索条转玻璃，官方 `IconPage.kt:499-544`）；`onDismissRequest` 只关闭搜索，**不得触达标签编辑**。
- 标签编辑 BottomSheet / 标签名 Dialog / 删除确认 Dialog / 延时冻结 BottomSheet：全部改“常驻组合 + show + 保留最后一次内容”，行统一 `BasicComponent`/`SheetActionRow`，删除 24dp 外层 padding 与自造 Row。
- FAB：`GlassFloatingActionButton(backdrop = pageBackdrop)`；不再手动 `padding(bottom = reserve-20)`（PageFrame 已处理）。
- 多选态顶栏：5 个 `TopBarAction`（28dp 图标）。

### 2.2 Apps（`feature/apps/AppsScreen.kt`）+ 操作面板（`feature/common/AppActions.kt`）
- 列表：官方 `Card(onClick, pressFeedbackType = Sink)` + `BasicComponent` 行；卡片名 `AppRow`（`AppComponents.kt`）由本成员负责；行内图标 40dp；副标题 footnote；尾部“已冻结”放 `endActions`。
- 空状态吃 padding。
- 搜索：同 Home（常驻、官方参数、结果列表用 `BasicComponent` 行）。
- 排序弹层：全部 `BasicComponent`（选中项 endActions 用 `Ok` 图标），不要混两种行组件。
- 筛选弹层：`SwitchPreference` 用默认 `insideMargin`，不覆盖。
- 操作面板：`GlassBottomSheet` 常驻 + 保留最后 entry；行 `SheetActionRow`（24dp 图标 + `BasicComponent`）；包名脚注与底部 spacing 用 12dp。
- `AppRow`、`SheetActionRow` 在 `feature/common/AppComponents.kt`：`AppIconView` 归图标成员，其余归本成员（见 5 节文件归属）。

### 2.3 Settings / About / Licenses / ApiActivity
- Settings：保持 `SmallTitle` + `GroupCard`；`GlassDropdownPreference` 用官方默认（backdrop 传 `LocalRootBackdrop`）；行 spacing 12dp；`ArrowPreference` 只用于可跳转项；禁用态交给组件；页面 `pageContentPadding` 默认。
- About：**照 MIUIX 官方 `example/AboutPage.kt` 结构**重写品牌区：
  - 图标容器 **88dp** + `graphicsLayer{shape=RoundedCornerShape(24.dp);clip=true}` + 白底，内部图标 **74dp**；
  - 应用名 **35sp Bold**（`onBackground`），版本 **14sp** `onSurfaceVariantSummary`，居中；
  - 品牌区上边距 40dp，名称上方 12dp、下方 5dp；
  - 卡片 `Modifier.padding(horizontal = 12.dp).padding(top = 12.dp)`，`CardDefaults.defaultColors()`，行用 `ArrowPreference`（可点）/`BasicComponent`（只读）；
  - 滚动/折叠用官方 `MiuixScrollBehavior`；二级页 `expandable = false`。
- Licenses：同 About 的卡片结构；组件卡用 `Card(insideMargin = 16.dp)` 或分组行，正文 `body2`/`footnote1`，节间距 12dp；入口行 `ArrowPreference`。
- ApiActivity：独立窗口录制层保持不变；`GlassDialog` 常驻 + show；操作面板共用 `AppActionSheet`；窗口 Scaffold `contentWindowInsets = WindowInsets(0)` + 页面 inset 自管。

### 2.4 图标（`platform/AppIcons.kt`、`feature/common/AppComponents.kt` 的 `AppIconView`、`res/drawable*`）
- **自适应图标**：不能直接 `draw(background/foreground)` 铺满 108dp 画布（当前实现会让图标内容偏小）。按启动器遮罩语义放大 1.5×：先创建 `sizePx` 画布，把 `background`/`foreground` bounds 设为 `(-sizePx/4, -sizePx/4, sizePx*5/4, sizePx*5/4)`（即 108dp 画布 → 72dp 遮罩区铺满），再输出；非自适应图标保留 `AppIconLoader` 路径。
- 轮廓：`squircleClip(size * 0.22f)`（保持），选中环 `squircleBorder` + 2dp 内缩（保持）。
- 品牌图标：白底 + 黑色主体；`ic_launcher_background/foreground/monochrome` 与 `ic_brand_mark.xml` 已是黑白，检查圆角/安全区即可；About 使用 74dp 渲染。
- Home 网格与 Apps 列表共用 `AppIconView` 同一实现与同一圆角比例。

---

## 3. 全局尺寸 token（`ui/components/PageFrame.kt` 的 `PageMetrics`）

| token | 值 | 依据 |
|---|---|---|
| `Horizontal` | 12.dp | 官方卡片外边距 `MainPage.kt:312`、`AboutPage.kt:348` |
| `SectionGap` | 12.dp | 官方卡片间距 `AboutPage.kt:399/442` |
| `BottomGap` | 12.dp | 官方列表末尾 `Spacer(12.dp)` `MainPage.kt:339` |
| 底栏横向内边距 | 24.dp | 官方 example 调用侧 `AppContent.kt:774` |
| 底栏顶部间距 | 20.dp | `GlassPage.kt:277` |
| 顶栏动作图标 | 28.dp | `GlassPage.kt:357/370/388` |
| 行图标（列表/操作单） | 24.dp（BasicComponent 内）/ 40.dp（应用图标） | `GlassPage.kt`、Mishka |
| 品牌图标容器 / 图标 | 88dp / 74dp / RoundedCornerShape(24.dp) | `AboutPage.kt:242-261` |

---

## 4. 验收要求（实现成员自检，lead 负责构建与真机验收）

- 编译层面：不新增未使用 import；不引用已删除的 API（如 `LocalNavReserve`）。
- 交互层面：搜索 × 只关闭搜索；清空按钮只清空输入；标签编辑独立；弹层反复开关无残留。
- 视觉层面：同一控件在全应用同一尺寸；页面左右 12dp；卡片间距 12dp；无 16dp/6dp/18dp 混用；无自造行。
- 证据层面：改完在答复中列出「文件 → 改动 → 官方依据（file:line）」，并如实标注未验证项（大字体、RTL、真机 L3）。
- 多语言：所有新增用户可见文本必须走字符串资源；为避免多人编辑同一文件，**每个成员新增自己的资源文件**：
  - Home → `res/values/strings_home_r3.xml` + `res/values-zh-rCN/strings_home_r3.xml`
  - Apps/Actions → `strings_apps_r3.xml`
  - Settings/About/Licenses/Api → `strings_pages_r3.xml`
  - 图标 → 无
  文件名不同，Android 允许同目录多份 strings 资源文件。

---

## 5. 文件归属（写作用域，互不重叠）

| 成员 | 文件 |
|---|---|
| lead | `ui/GlacierShell.kt`、`ui/components/PageFrame.kt`、`ui/GlassEnvironment.kt`、`ui/theme/GlacierTheme.kt`、`docs/**`、`res/values/strings.xml`（仅必要的通用新增，尽量不动） |
| home | `feature/home/HomeScreen.kt`、`res/values/strings_home_r3.xml`、`res/values-zh-rCN/strings_home_r3.xml` |
| apps | `feature/apps/AppsScreen.kt`、`feature/common/AppActions.kt`、`feature/common/AppSearchResults.kt`、`feature/common/AppComponents.kt`（AppRow/SheetActionRow，**不动 AppIconView**）、`res/values/strings_apps_r3.xml` + zh |
| pages | `feature/settings/SettingsScreen.kt`、`feature/about/AboutScreen.kt`、`feature/about/LicensesScreen.kt`、`feature/api/ApiActivity.kt`、`res/values/strings_pages_r3.xml` + zh |
| icons | `platform/AppIcons.kt`、`feature/common/AppComponents.kt` 的 `AppIconView` 部分（与 apps 成员协调：只改 `AppIconView` 函数体）、`res/drawable/ic_launcher_*.xml`、`res/drawable/ic_brand_mark.xml`、`res/mipmap-anydpi-v26/ic_launcher.xml` |
