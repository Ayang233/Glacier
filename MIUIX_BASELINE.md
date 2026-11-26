# MIUIX 基线（Glacier）

本文件是 Glacier 跟随 [compose-miuix-ui/miuix](https://github.com/compose-miuix-ui/miuix) 的**唯一事实来源**。
换机器、换人或换 AI 助手接手时，先读这里，再动代码。

> 机器可读校验：`tools/miuix-baseline.sh verify`
> 差距检查：`tools/miuix-baseline.sh status`

## 1. 接入方式

| 项 | 值 |
|---|---|
| 方式 | **composite build 源码引入**（不是 Maven 坐标） |
| 声明位置 | `settings.gradle.kts` 的 `includeBuild(miuixDir)` |
| 源码目录 | 默认 `../miuix`（相对仓库根），可用环境变量 `MIUIX_DIR` 覆盖 |
| 为什么 | MIUIX 自述 experimental，主干默认值仍在快速变化；源码引入能保证"读到的默认值就是运行时用的默认值"，也便于逐行核对官方实现 |
| 代价 | 构建依赖本地 MIUIX 源码；版本号不出现在 `libs.versions.toml`，而是记录在本文件 + git commit |

**本地补丁**：`tools/miuix-composite-build.patch`

官方 `build-logic/src/main/kotlin/BuildConfig.kt` 里的 `getGitVersionCode()/getGitHashShort()` 用
`providers.exec("git", ...)` 但不指定工作目录，composite build 下会因工作目录不是 MIUIX 仓库而 `exit 128`。
补丁只做一件事：给两处 `exec` 加 `workingDir = rootDir`。**除这 2 行外，MIUIX 源码保持官方原样。**

## 2. 本次基线

| 项 | 值 |
|---|---|
| MIUIX 版本 | `0.9.5`（`build-logic/src/main/kotlin/BuildConfig.kt` 的 `LIBRARY_VERSION`） |
| 官方 tag | `v0.9.5-rc01`（tag 指向 `542463a0`，即上一轮基线） |
| **MIUIX commit** | `e7c108ee469349dcb6ab6d81ca4c0ab9d25813bb`（**main 最新**，比 `v0.9.5-rc01` 多 10 个提交） |
| commit 时间 | 2026-11-26 16:42:07 +0800 |
| commit 标题 | `example: Add new icon for iOS (#499)` |
| 官方 main 追踪 | `origin/main` = 本基线本身（R13 起按用户要求跟 main 最新） |
| 本地补丁 | 已应用（`tools/miuix-composite-build.patch`） |
| 核对日期 | 2026-11-26（第十轮：`542463a0` → main `e7c108ee`） |

> **R21 升到 main 最新 `e7c108ee`（官方 tag `v0.9.5-rc01` 仍是 `542463a0`，即上一轮基线）。**
> 这 10 个提交集中在 Glass：`GlassTopAppBar` 渐进模糊带重写（`BandOverhang` 32→10dp、
> `ProgressiveBlurRadius` 80→32dp、`bottomContent` 起始比例）、`GlassBottomSheet` 的
> `progressiveBlur` 默认改为 `false`、新增官方 `GlassScrollableTabRow`、下拉弹层非交互滚动指示器、
> AGP 9.5.3。下一轮官方发正式 0.9.5 后回到"钉 tag"策略。
>
> **注意：官方在 2026-11-24 重写过 main 历史。** 上一轮基线 `44f1d315` 已不在 `origin/main` 上，
> 因此 R11 之前"tag + N 个 main 提交"的表达不再可复算。基线只认 commit sha，不跟 `origin/main` 漂移。


## 3. 工具链

| 项 | Glacier | 说明 |
|---|---|---|
| Gradle | 9.9.1（wrapper） | MIUIX 自身用 9.9.2，composite 下以 Glacier 的 wrapper 为准 |
| Kotlin | 2.4.21 | 与 MIUIX 一致 |
| AGP | 9.5.3 | 与 MIUIX 一致；composite build 中两端 AGP 版本必须相同，否则依赖解析报 `AgpVersionCompatibilityRule` |
| Compose | **BOM `2026.11.01` → androidx.compose 1.13.0** | MIUIX 0.9.5 用 Compose Multiplatform 1.13.0-rc01；BOM 必须选到同一个 1.13 线，否则会被 MIUIX 的约束悄悄抬升（旧 BOM 2025.12.01 = 1.10.0 → 1.13.0 的隐式升级） |
| compileSdk | 37 + `minorApiLevel = 2` | MIUIX 要求 37.2 |
| buildTools | 37.0.0 | |
| JDK | 21 | |
| minSdk / targetSdk | 33 / 36 | 33 是 `miuix-blur` 的 RuntimeShader 门槛 |

## 4. 引用的 MIUIX 模块

```
miuix-ui            基础组件 / MiuixTheme / Scaffold / TopAppBar / TabRow / Card / TextField / ScrollBar
miuix-preference    设置行：SwitchPreference / ArrowPreference / SliderPreference / RadioButtonPreference
miuix-icons         扩展图标族（MiuixIcons.Back 等）
miuix-material      GlassDefaults / GlassStyle / isRuntimeShaderSupported
miuix-blur          Backdrop / rememberLayerBackdrop / layerBackdrop / overScrollVertical
miuix-glass         全部官方 Glass 组件（见 §5）
miuix-glass-icons   MiuixIcons.Glass.*（Regular 字重）
miuix-nav           NavDisplay / NavBackStack / NavDisplayEffects / 预测性返回
miuix-squircle      squircleClip / squircleBorder / squircleBackground
```

`miuix-glass` 已经 `api` 传递 `blur` / `material` / `ui`，显式声明只为可读性与可追溯。

## 5. 官方组件使用情况（当前）

| 场景 | 使用组件 | 备注 |
|---|---|---|
| 根容器 | `Scaffold`（透明、insets=0）+ `NavDisplay` + `HorizontalPager` | 弹层宿主在最外层 |
| 一级页/二级页骨架 | `GlassTopAppBar` / `TopAppBar` + `Scaffold` + `Card` + `BasicComponent` | 玻璃档/非玻璃档两档都由官方组件构成；非玻璃档照官方 `AdaptiveTopAppBar`（`PageUtils.kt:297-314`）把 `surface` 底色挂在 `appPageTopBarInsets()` **外层**、`TopAppBar` 传 `Color.Transparent`，状态栏安全区才不会被滚动内容透出（R25） |
| 底部导航 | `GlassNavigationBar` | 24dp 横向内边距 + 20dp 顶距，对齐官方 example；**装在一级目的地里**（官方 `Home()` 的 `Scaffold(bottomBar)` 同构），二级页没有底栏；不使用 `visible` |
| 侧栏 | `GlassNavigationRail` + `rememberGlassNavigationRailState` | 折叠胶囊由组件自带 |
| 页签 | `GlassSegmentedTabRow` / `GlassScrollableTabRow` / `TabRow` | 2–4 个标签走分段轨道；更多玻璃档走 R21 新增的官方横向滚动 `GlassScrollableTabRow`，非玻璃档仍用 `TabRow` |
| 搜索 | `GlassSearchOverlay` + `rememberGlassSearchBarAlpha` + `glassSearchBarTransition` | 搜索条转玻璃、400ms 进出动画都由官方驱动 |
| 文本输入 | `GlassTextField`（玻璃档）/ `TextField`（非玻璃档），均为 `TextFieldState` 新版 API | 旧的 `value/onValueChange` 重载已废弃 |
| 弹层 | `GlassDialog` / `GlassBottomSheet` / `GlassDropdownPreference` / `GlassDialogButton` | `renderInRootScaffold` 保持默认 true；`GlassBottomSheet` 的内容槽是 `(PaddingValues) -> Unit`（标题栏覆盖在内容上，内容必须让出实测标题高度，R13 已全部接好） |
| 弹层内的分组卡片 | `GlassCard` | 0.9.5 main 新增；专用于玻璃表面上的内容卡，替代原先在 `GlassBottomSheet` / `GlassDialog` 里复用的普通 `Card`；R13 起关于页信息卡也从自绘 textureBlur 换成它 |
| 长按菜单 | `GlassTransformPopup` + `GlassPopupItem` + `GlassPopupAnchor`（`rememberGlassPopupAnchor` / `glassPopupAnchor` / `glassPopupAnchorContent`） | **R13 接回，R14 补齐官方契约**：主页网格与应用页列表行的长按菜单都从被按的那一格/图标原地展开（官方锚点浮层），不再用底部弹层；经 `TopLayer` 提升到根部，避免被侧栏盖住 + 避免自采样。R14：面板宽度交回官方内容驱动（`GlassPopupDefaults.MinWidth 200dp` / `MaxWidth 288dp`，不再写死 `width(320/280)`）；浮层改为常驻组合（官方"退场期间仍须保留调用"），首次长按也有展开动画 |
| 页面级浮层宿主 | `TopLayer` / `TopLayerHost`（`ui/TopLayer.kt`，官方 `example/utils/TopLayer.kt` 逐字节移植） | 官方 example 私有能力，库不导出；浮层画在录制层与玻璃侧栏之外。R14：宿主按侧栏让位宽度内缩，`GlassPopupSurface` 的"夹在宿主边界内"规则因此自动让开侧栏（官方契约："放在带 inset 内边距或侧栏偏移的面板中也能对齐"） |
| 按钮 | `GlassIconButton` / `GlassFloatingActionButton` | 顶栏动作、返回、FAB |
| 宿主让位 | `AppPageInsets` + `LocalAppPageInsets` + `appPageHorizontalInsets()` / `appPageTopBarInsets()`（`ui/PageInsets.kt`，官方 `utils/PageUtils.kt:62-152` 的逐字节移植） | 侧栏宽度、系统安全区只作用于内容；`NavDisplay` 恒满宽，背景与模糊带不被裁断 |
| 内容内边距 | `pageContentPadding`（官方惰性 `PaddingValues`）+ `rememberAppSafePadding()` | 组合期只建对象，`calculate*` 在布局阶段读，侧栏 morph 不触发重组 |
| 滚动 | `VerticalScrollBar` + `rememberScrollBarAdapter`，`trackPadding = contentPadding`、`topAppBarScrollBehavior = 顶栏行为`；页面级 `Lazy*` 一律 `overscrollEffect = null` | 与官方 `MainPage.kt:359-364` 同参；弹性 overscroll 由 `pageScrollModifiers` 统一消费，列表再开一份会双份回弹（官方所有列表页都传 null） |
| 形状 | `squircleClip` / `squircleBackground` | 应用图标、品牌图标 |
| 多选指示 | `Checkbox`（官方 26dp，`ToggleableState`） | 列表/搜索结果行放 `endActions`（与库作者自己的 Mishka `AppProxyScreen.kt:415-441` 同构），网格选中时叠图标右上角作角标；库里**没有**"选中描边"这种语言，所以自绘的 2dp 蓝色 squircle 描边已在 R9 删除 |
| 模糊 | `rememberLayerBackdrop` / `layerBackdrop` / `Backdrop` | 无自建模糊管线、无自建背景采样 |
| 关于页动态背景 | `ui/effect/`（官方 example 的 `BgEffect` 组件逐字节移植）+ 品牌名 `textureBlur` | 官方 AboutPage 的双 backdrop 范式：独立背景层录 BgEffect，品牌名作为兄弟采样；R13 起信息卡用官方 `GlassCard`（不再自己 textureBlur/混色）；**R24 起上下滑结构也对齐官方 `AboutPage.kt:312-413`**：`pageScrollModifiers` 挂在 LazyColumn、正文用 `Spacer(fillParentMaxHeight())` 撑到一屏，背景/品牌区固定不动 |
| 主题 | `MiuixTheme` + `ThemeController` | 固定浅/深色板，见 §7 |
| 动效 | `scrollEndHaptic` / `overScrollVertical` / `PagerNavigationSpringSpec` / `springAnimateToPage` / `GlassMotion.*` / 弹层 `DialogMotion.Glass` | 官方原语组合；按压反馈全部走组件的内建实现（玻璃档 `GlassIconButton` 的 `buttonPressedOverlay` + `PressedContentAlpha`，非玻璃档 `IconButton` 的 `MiuixIndication`），弹层进出动画自基线 `44f1d315` 起由 `DialogMotion` 统一 |
| 顶栏动作区 | 单态 `Row(spacedBy(8.dp))` + `TopBarAction`（官方 `example/GlassPage.kt:363` 的间距） | R10 追加：多选不再换成一排批量按钮，`AnimatedTopBarActions` 组件已从 `PageFrame.kt` 删除；批量动作的出口收敛到 FAB |
| 顶栏标题 | `TopAppBar` / `GlassTopAppBar` 的 `title` + `subtitle` 两行 | R11：多选进度写进 `subtitle`，删掉 `titleFontScale`（0.83 字号补偿）与整条顶栏的 `AnimatedContent`——`TopAppBar` 只有两行，第三行/换字号都是自造 |
| 局部显隐（FAB / 页签行） | `CapturedAnimatedVisibility` + `fadeIn/scaleIn`、`expandVertically/shrinkVertically` | 官方顶栏子内容 / 悬浮件显隐的同一写法（`AppContent.kt:720-728`） |
| 多选态返回 | `BackHandler`（activity-compose，注册到 NavigationEventDispatcher 栈顶） | 多选态先退模式，不结束 Activity |
| 状态派生 | `computedStateOf` / `CapturedAnimatedVisibility` | Compose 1.13 官方 API（官方 example 已全量使用），替代 `derivedStateOf` / `AnimatedVisibility` |

**结论：不存在自建的 GlassSurface、模糊管线、背景采样、玻璃按钮或导航动画。**
自写的部分全部是"页面级组合/布局"，官方只在 example 里给样例，不提供库 API。

## 6. 定制代码边界

| 文件 | 性质 | 官方是否有库 API |
|---|---|---|
| `ui/PageInsets.kt`（`AppPageInsets` / `LocalAppPageInsets` / `rememberAppPageInsets` / `appPageHorizontalInsets` / `appPageTopBarInsets` / `rememberAppSafePadding` / `pageBottomPadding`） | 官方 `example/utils/PageUtils.kt` 的逐字节移植（Apache-2.0），与 `ui/effect/` 同一处理方式 | 否（官方只在 example 里给，库不导出） |
| `ui/TopLayer.kt`（`TopLayerState` / `LocalTopLayer` / `TopLayer` / `TopLayerHost`） | 官方 `example/utils/TopLayer.kt` 的逐字节移植（Apache-2.0）；长按锚点浮层提升到根部用 | 否（官方只在 example 里给，库不导出） |
| `ui/components/PageFrame.kt`（PageFrame / PageTopBar / TopBarAction / GroupCard / EmptyState / pageContentPadding / pageScrollModifiers） | 页面骨架组合 + 官方 `pageContentPadding` / `pageScrollModifiers` 的同参落地；标题只用官方 `title` / `subtitle` 两行，不再有任何字号缩放或顶栏级动画容器 | 否（官方只有 `example/GlassPage.kt` 与 `PageUtils.kt`） |
| `PageMetrics` | 尺寸常量：横向留白直接引用官方 `GlassTopAppBarDefaults.HorizontalPadding`，其余取自官方 example 或 `CardDefaults` | 否 |
| `ui/GlassEnvironment.kt` 的 `Local*` | 宿主职责：玻璃开关、根录制层、底栏实测高度、标题透明度、标题避让 | 否（官方 example 同款做法） |
| `ui/GlacierShell.kt` | 一级/二级页路由 + 三态导航（底栏/折叠胶囊/展开侧栏）；底栏在一级目的地 `entry<Route.Main>` 内部（官方结构），采样页内录制层 | 否 |
| `platform/AppIcons.kt` | 应用图标加载（图标包 / 自适应图标不套遮罩 / LRU） | 否，属业务能力 |
| `data/` `domain/` `system/` `platform/auto/` | 冻结业务本体 | 否 |

> 原则：薄封装可以留，但封装内**不得重新实现官方底层能力**。当前审计未发现违反项。

## 7. 已知差异与主动不做的事

| 项 | 现状 | 原因 |
|---|---|---|
| 基线位置 | 钉在 main 具体提交 `542463a0`（比最新 tag `v0.9.5-beta04` 多 12 个提交） | R13 用户要求升到最新：这 12 个提交是 `GlassTransformPopup` 关闭回弹、`GlassBottomSheet` 渐进模糊标题栏、`GlassTopAppBar` 模糊带与动态色对比度等 Glass 修复，直接对应本轮要修的锚点浮层 / 弹层观感。下次官方发 tag 后回到"钉 tag"策略 |
| `UiFeedback.toast()` | 仍用 Android `Toast`（R11 评估后**确认保留**） | ① 调用点包含 Worker / Receiver / 快捷方式等无 Composition 的上下文，`Snackbar` 需要宿主；② 页面级浮层（`GlassSearchOverlay` / `GlassBottomSheet` / 锚点菜单）画在 Scaffold 之上，会在"搜索结果里点一行加入/移出主页"这类高频路径上把 Snackbar 盖住，反馈直接丢失；③ `Toast` 是系统反馈件而非自造 UI，HyperOS 上由系统按 OS 风格绘制 |
| 二级页切 Tab 的转场 | 仍走 `NavTransitions.MiuixDefault`（官方 example 在二级页点导航时切到私有的 `InstantNavTransition`，`AppContent.kt:320/1155`） | 该 helper 是 example 私有实现而非库 API；当前表现为"退回一级页时播放 pop 转场"，与官方观感有差异，但语义正确。需要完全一致时再移植 |
| 主页大标题 | 恒为应用名，多选进度写在副标题 | 之前用"换大标题 + 0.83 字号补偿 + 整条顶栏 `AnimatedContent`"表达「已选 N 个」，属自造排版层（`title1` 被临时缩成 26.5sp）；R11 删除，回到官方 `title` + `subtitle` 两行 |
| 预测性返回开关 | **R22 回归官方系统级开关**：manifest `enableOnBackInvokedCallback` 常开，`GlacierApplication.onCreate` 把进程内 `ApplicationInfo` 标志设为偏好值（`HiddenApiBypass + 反射`，API 34+）。**R23 起切换后不重建，提示重启应用生效** | R21 只切 `predictivePop` 转场，系统标志仍为 true：真机录屏逐帧确认，关闭预览后在一级页做返回手势，系统仍会画自己的预测性返回窗口预览（整页一帧左移，`CoreBackPreview` 的 `mIsAnimationCallback` 仍为 true），这就是「关掉后手势返回还闪一下」。自造 `NoPredictiveBackTransition` 还会和共享 depth 驱动各算一套进度，手势起止时多一帧错位。R22 删除该文件，页面转场永远用官方 `NavTransitions.MiuixDefault`；关闭时系统不再下发跟手进度，返回走 `OnBackPressedDispatcher`，pop 动画照常。R22 最初用 `recreateWithoutTransition()` 立即生效，但 R23 录屏确认 `recreate()` 会露出整窗 `windowBackground` 空白帧（约 67ms），因此改为只写偏好 + toast「重启应用后生效」 |
| 主页隐藏应用名称 | Glacier 自实现（R16）。MIUIX **没有**相关官方 API：库没有桌面/图标网格组件，文档也没有隐藏名称的能力 | 实现时保留标签节点只做 `alpha(0)`，标签仍量出同样行高，行距（16dp）/图文间距（6dp）/列宽公式还是原 MIUIX token；可访问名移到整格 `contentDescription`，读屏不受影响 |
| 品牌图标 | 自绘（R16）。MIUIX 图标库搜不到雪花图标 | 6 条臂用同一半径/角度生成，完全对称；主体 0.90 缩放；黑主体白底，`ic_launcher_foreground` / `ic_brand_mark` / `ic_tile_frozen` 同源重画 |
| 锚点浮层（`GlassPopup` / `GlassTransformPopup`） | **R13 已接回并实测通过，R14 补齐宽度/动画/侧栏让位**：主页网格与应用页列表行的长按菜单都从被按的那一格/图标原地展开；经 `TopLayer` 提升到根部 | R12 的 RenderThread 递归崩溃只在软件渲染（SwiftShader）下出现；本轮模拟器接入宿主 GPU（AMD Radeon，GLES 3.1 硬件 RenderEffect）后正常。上游 beta04→main 的关闭回弹修复也一并生效。详见 `docs/audit/miuix-os4-r13-audit.md`、`docs/audit/miuix-os4-r14-audit.md` |
| 弹出菜单行数上限 | 标题 + 分割线 + 6 行后，其余行在弹层内滚动 | 上游 `GlassPopupColumn` 的固有尺寸探测只采样前 8 个子项（`POPUP_PROBE_COUNT = 8`），超过 6 行时面板高度按 6 行算。主页 10 行菜单实测可滚动到「从主页移除」，文案完整；不通过改库绕过 |
| 弹层首次组合时机 | 浮层常驻组合，开合只由 `show` 控制；用列表第一项预热菜单内容 | 官方文档明确要求"关闭时仍须保留组件调用，直到离场动画完成；不要使用 `if (show)` 包裹调用"，否则首次长按 `updateTransition` 初始值就是 `true`，展开动画不播（R14 修复）。同时上游 `GlassPopupSurface` 的固有尺寸缓存会记住"空菜单"首帧量到的极小尺寸，所以先用列表首项预热完整菜单，等真正长按时同帧替换成被按条目 |
| 底部导航蓝色模糊块 | 复核为非缺陷 | 设置页底栏左侧的蓝色块是「主页标签字号」滑块的蓝色轨道透过玻璃的正常采样，滚动离开即消失，切页签后不残留；1.5.0 报告里的"残影"本轮未再复现 |
| 弹层与屏幕圆角 / 隐藏手势提示线 | 已修（R15）：`rememberSheetBottomPadding()` 把 `mandatorySystemGestures` 底部与官方 `getRoundedCorner()` 并入 `GlassBottomSheet` 的底部留白 | 官方没有自动避让圆角的机制：圆角不属于任何 `WindowInsets`，库只提供 `getRoundedCorner()` 原语（内部仅 `DialogContentLayout` 拿来推对话框同心圆）；`GlassBottomSheet` 文档写明底部导航栏内边距由调用方提供、底角保持直角。隐藏手势提示线时 `navigationBars` 底部会变 0，旧写法只剩 12dp 分节间距，最后一行落进物理圆角。面板本身仍贴底、底角直角，物理圆角会裁掉面板底部两角，改库不在范围。详见 `docs/audit/miuix-os4-r15-audit.md` |
| 关于页背景效果调试面板 | 未移植（官方 AboutPage `:456-495` 的 `Background Effect` BottomSheet） | 官方 example 用来现场调 BgEffect 参数（OS2/OS3、动态背景、全屏）的调试面板，无产品意义；动态背景本体已完整对齐 |
| Monet 动态取色 | 未接入。官方 `ColorSchemeMode.MonetSystem` 可用，Glacier 固定浅/深色板 | 品牌一致性；如需要可作为「主题」第三档，属于产品决策 |
| 宽屏 + 刘海 | 顶栏 `defaultWindowInsetsPadding = false` + `appPageTopBarInsets()`；正文的横向让位只由内容 padding 提供一次 | 与官方 `AdaptiveTopAppBar`（`PageUtils.kt:265-304`）同构，不再有 End-only inset 补丁 |
| 真机玻璃光学（L3） | 未验证。模拟器已接宿主 GPU（AMD Radeon），能产出真实模糊像素，但不等于真机材质/描边/阴影的实际观感 | 需要一台真实设备补验 |
| 冻结业务链路 | Shizuku / Root / Dhizuku / DeviceOwner / Island / 特权 六条后端只有源码级证据 | 模拟器只有 Root 链路实测过；其余后端仍需真机验证 |

## 8. 升级流程（每次照做）

```bash
# 1. 拉官方最新 main 与 tag（直连 github 失败会自动退回镜像，见下）
tools/miuix-baseline.sh fetch

# 2. 看差距：落后多少提交、有没有新 tag、LIBRARY_VERSION 是否变了
tools/miuix-baseline.sh status

# 3. 读官方变更（release notes + 逐条 commit）
#    https://github.com/compose-miuix-ui/miuix/releases
#    重点看：@Deprecated 新增、公开签名变化、默认值变化、Compose/Kotlin 版本要求

# 4. 切到目标 tag / commit（会顺带打本地补丁）
#    R13 基线：main 最新 commit（当前 542463a0）；官方发新 tag 后改回钉 tag
tools/miuix-baseline.sh pin 542463a0

# 5. 先让 MIUIX 自己编过，再编 app
./gradlew :app:assembleDebug

# 6. 修 API 迁移，跑测试
./gradlew :app:assembleDebug --warning-mode all     # 看 deprecation
./gradlew :app:testDebugUnitTest

# 7. 装机走一遍回归清单
#    docs/verification/acceptance.md

# 8. 更新本文件 + docs/audit 的新一轮审计 + release notes
tools/miuix-baseline.sh verify
```

**不要**只改版本号；**不要**为了截图一致恢复已被官方淘汰的自造实现。

> **R13 升级时踩到的两个上游行为**（后续升级先看这里）：
> 1. `GlassBottomSheet` 的 content 槽从 `() -> Unit` 变成 `(PaddingValues) -> Unit`，标题栏变成覆盖式
>    （`progressiveBlur` 默认 true）。所有内容必须用实测标题高度做 `contentPadding.top`，否则首屏会压到标题下面。
> 2. `GlassPopupColumn` 的固有尺寸只探测前 8 个子项（`POPUP_PROBE_COUNT`），且 `GlassPopupSurface`
>    按 content lambda 缓存固有尺寸。菜单超过 6 行时面板会滚动；锚点浮层必须在"有完整菜单内容"时
>    才第一次组合（Glacier 用 `if (lastActionEntry != null)` 门控）。

> 网络：本机 git 直连 `github.com` 会被代理拒绝（`CONNECT tunnel failed, response 502`），
> 脚本在直连失败时改用 `MIUIX_GIT_MIRROR`（默认 `https://ghfast.top/https://github.com/compose-miuix-ui/miuix.git`）
> 拉取，并照样写回 `refs/remotes/origin/main`。基线一致性只认 commit sha，与拉取链路无关。

## 9. 常用命令

```bash
./gradlew :app:assembleDebug                  # 产物 app/build/outputs/apk/debug/Glacier-debug.apk
./gradlew :app:assembleRelease                # 需要 keystore/ + signing.properties
./gradlew :app:testDebugUnitTest              # 单元测试
./gradlew :app:lintDebug                      # lint
adb install -r app/build/outputs/apk/debug/Glacier-debug.apk
```

## 9.1 自动化检测（建议，尚未启用）

升级的痛点是"官方发了新版，我不知道哪一处默认值变了"。可以分三步落地，**都不应该自动合入**：

1. **依赖漂移检测**：定时任务跑 `tools/miuix-baseline.sh status`，把"落后 main 的提交 + 新 tag"
   作为 issue 内容。这一步只读，零风险。
2. **CI 构建**：在 `.github/workflows/build.yml` 里按基线 commit clone MIUIX、`git apply` 本地补丁、
   跑 `testDebugUnitTest` + `assembleDebug`。
   > 注意：推送含 `.github/workflows/` 的提交需要 GitHub token 带 `workflow` scope
   > （否则 Git Data API 会返回 404）。本机当前 token 没有该 scope，所以这条流水线**没有入库**，
   > 需要时先 `gh auth refresh -s workflow` 再添加。
3. **升级 PR**：把"切基线 + 跑构建 + 记录差异"做成人工触发的 workflow，产出**草稿 PR**，
   由人确认后再合并。破坏性版本（API 破坏、Compose/Kotlin 大版本）不允许自动合并。

## 10. 基线变更记录

| 日期 | 从 | 到 | 主要变化 |
|---|---|---|---|
| 2026-11-19 | — | `58b0a3d`（0.9.5 主干） | 首次接入 MIUIX OS4 + Glass |
| 2026-11-20 | `58b0a3d` | `66248bac`（`v0.9.5-beta02`） | ScrollBar 重构（`ScrollIndicatorState`，`reverseLayout` 废弃、触摸目标 24dp）、TopAppBar 标题对齐与 `centerObstructionStart`、`GlassTopAppBar` 渐进模糊改由 `maskProgress` 驱动、`TextField` 改 `TextFieldState`、`GlassTextField` 新增、`MiuixWindowProperties` 回调外移（Glacier 未用 miuix-window）、Compose 1.13.0 |
| 2026-11-22 | `66248bac`（`v0.9.5-beta02`） | `9128921c`（main，`v0.9.5-beta03` + 16） | 按 Glass 迁移指南替换剩余非官方 API：`derivedStateOf`→`computedStateOf`、`AnimatedVisibility`→`CapturedAnimatedVisibility`、选中环用 `GlassMotion.default()`、`LocalTopBarObstructionStart` 改 `compositionLocalOf`、页签 `surfaceAlpha` 叠加 `overscrollOffset < -1f`；玻璃弹层内的 `Card` 换成官方新增 `GlassCard`；AGP 9.5.1→9.5.2 与 MIUIX 对齐 |
| 2026-11-22（同基线） | — | — | 关于页完整对齐官方 `AboutPage`：移植官方 `BgEffect` 动态背景（`ui/effect/`，Apache-2.0），建独立背景 backdrop + 品牌名/信息卡 `textureBlur`（官方混色 token），背景 alpha 随滚动淡出；修复设置项不可观察导致的 7 处 UI 不刷新（订阅 `preferences.revision`） |
| 2026-11-22 | `9128921c` | `44f1d315`（main，`v0.9.5-beta03` + 18） | 全项目清查自造 UI 轮子（审计 R6）：删除 `railStartInset` / `LocalPageStartReserved` / `LocalSearchTopInset` / `pageSafeBottom`，改用官方 `AppPageInsets` 契约（`ui/PageInsets.kt`）；`pageContentPadding` / `pageScrollModifiers` 换成官方同参实现（惰性读取，侧栏 morph 不重组）；滚动条补 `trackPadding` 与 `topAppBarScrollBehavior`；删除 Home 页第二份搜索结果列表，统一复用 `AppSearchResults`；顶栏 / 底栏 / 搜索浮层 / 弹层的安全区全部走官方契约；弹层动画随基线切到 `DialogMotion.Glass` |
| 2026-11-22（同基线） | — | — | 交互与转场对齐 OS4（审计 R7）：底栏按官方结构挪进 `entry<Route.Main>`（官方 `Home()` 的 `Scaffold(bottomBar)` 同构），二级页没有底栏、转场时底栏随一级页一起走（不再用 `visible` 的 0.6 倍缩小退场）；Apps 行改用官方 `Card(onClick, onLongPress, holdDownState)`，点按=选中（新增多选 + 顶栏批量动作）、长按=操作单，搜索行同语义，多选态 `BackHandler` 先退模式；顶栏动作区两态切换改用 `AnimatedContent` + `GlassMotion` 曲线（`AnimatedTopBarActions`），FAB / 页签行改用 `CapturedAnimatedVisibility`。详见 `docs/audit/miuix-os4-r7-audit.md` |
| 2026-11-23（同基线） | — | — | 按压反馈核对 + 删除最后一处自绘选中态（审计 R9）：实机对照官方 example APK 确认，顶栏圆形反馈＝`GlassIconButton`（44dp / 0.10 叠层 / 图标 0.6）与 `IconButton`（40dp）两档的官方内建行为，图标单元灰底＝官方 `squircleClip(12dp) + clickable` 整格叠层，两者都不需要改；真正自造的是"选中＝图标 2dp 蓝色描边"，已改为官方 `Checkbox`（列表行尾 / 网格选中角标）。详见 `docs/audit/miuix-os4-r9-audit.md` |
| 2026-11-23（同基线） | — | — | 主页/应用页职责拆分（审计 R10）：应用页改成"挑应用"（点 = 移进/移出主页，行尾官方 `Checkbox` 表达归属，删掉多选/计数/批量动作），长按弹管理向 3 行菜单并去掉弹层底部包名脚注；主页长按改为使用向 9/10 行菜单（冻结/解冻语境行、`ShortcutBridge.requestPin` 的"添加到主屏幕"、"解冻并从主页移除"仅在已冻结时出现），多选入口收敛到顶栏按钮；`AppActionSheet` 改为"调用方给动作清单"（`appsMenuActions`/`homeMenuActions`/`apiMenuActions`），删除 `inHome`/`unsupportedActions` 补丁参数与卸载/重装动作；图标按下态用官方 `HoldDownInteraction`（`HoldDownObserver` 为 internal，按官方实现移植 `ui/components/HoldDownEffect.kt`）。详见 `docs/audit/miuix-os4-r10-audit.md` |
| 2026-11-24 | `44f1d315`（main，`v0.9.5-beta03` + 18） | `3c560868`（**tag `v0.9.5-beta04`**） | 基线改为对齐 tag（官方重写 main，旧基线已不在 main 上）。官方变更：弹层动效统一到 `SheetMotion` / 精修 `DialogMotion`、`MiuixIndication` 按压层改 20/255 并加 200ms 释放保持、`Slider` 支持点按定位、搜索清除按钮样式统一、`GlassPopup` 支持水平对齐。Glacier 侧无需改 API（编译零改动通过），实际收益是弹层/按压的手感直接跟随官方。详见 `docs/audit/miuix-os4-r11-audit.md` |
| 2026-11-25（同基线） | — | — | 长按菜单交互（R12）：多选态长按**已选中**项 → 批量菜单（标题「已选择：N」+ 冻结/解冻/标签分组/导出/移除/解冻并移除），长按未选中项仍是单应用菜单；删除 `HoldDownEffect`（按下层不再人工保持），菜单打开的那一格改用零透明度官方 `MiuixIndication` 让叠层立即退场；新增 `SelectionActionRunner`、`setTagForApps`（批量打标签）、`TagPickerSheet`（官方三态 `Checkbox`）。详见 `docs/audit/miuix-os4-r12-audit.md` |
| 2026-11-25（同基线） | — | — | 自造轮子清查 R11 + 冻结交互重构：删掉"已选 N 个"的自造排版层（`titleFontScale` 0.83 字号补偿 + 整条顶栏 `AnimatedContent` + 逐状态 `MiuixTheme` 覆盖），多选进度改回官方副标题；主页 FAB 改为**只冻结**（不再在"全冻"时翻成解冻；FAB 与多选入口**常驻原位**，确实无可冻目标时点击提示「已全部冻结」，不做自造禁用态）；`refresh()` 由"正在刷新就丢弃"改成 Mutex 串行（修"冻结后图标/文案不刷新"）；应用图标灰度档进 LRU 并支持同步 `peek`，冻结切换不再闪空占位 |
| 2026-11-25 | `3c560868`（**tag `v0.9.5-beta04`**） | `542463a0`（**main 最新**，beta04 + 12） | 用户要求升级到最新。官方变更集中在 Glass：`GlassTransformPopup` 关闭回弹（`preserve closing rebound on anchor button`）、`GlassBottomSheet` 渐进模糊标题栏 + `headerBottomContent` + 内容槽改 `PaddingValues`、`GlassTopAppBar` 渐进模糊顶纱与 `BandOverhang` 36→32dp、`GlassIconButton` 头部上下文统一为 `GlassHeaderContext`、`GlassCard`/`GlassDialogButton` 动态色对比度、`GlassTopAppBar` `heightOffset` 夹紧、预测性返回状态复位、底栏上沿延伸。Glacier 侧：接回锚点长按菜单（`GlassTransformPopup` + `TopLayer`）、全部 `GlassBottomSheet` 内容让出标题栏、关于页信息卡换 `GlassCard`、页面列表补 `overscrollEffect = null`、`PageMetrics.Horizontal` 改用官方 `GlassTopAppBarDefaults.HorizontalPadding`。详见 `docs/audit/miuix-os4-r13-audit.md` |
| 2026-11-25（同基线） | — | — | 锚点浮层按官方契约收尾（R14）：① 面板宽度从写死的 `width(320/280.dp)` 交回官方内容驱动（`MinWidth 200dp` / `MaxWidth 288dp`），不再被强行顶到 MaxWidth；② 浮层改为**常驻组合**（官方"退场期间仍须保留调用"），首次长按也有展开动画；用列表首项预热菜单内容，避开上游固有尺寸缓存对"空菜单首帧"的记忆；③ 横屏展开侧栏时 `TopLayerHost` 按侧栏让位宽度内缩，`GlassPopupSurface` 的宿主边界夹紧规则自动让开侧栏；④ 设置项文案「禁用」改「停用」，并调换「主页标签字号」/「合成自适应图标」顺序。详见 `docs/audit/miuix-os4-r14-audit.md` |
| 2026-11-25（同基线） | — | — | 底部弹层避开屏幕大 R 角 / 隐藏手势提示线（R15）：新增 `rememberSheetBottomPadding()`（`ui/PageInsets.kt`），底部留白 = `max(安全区底部, mandatorySystemGestures 底部, getRoundedCorner()) + 分节间距`；替换 6 个 `GlassBottomSheet` 调用点（主页标签分组/标签管理/延时冻结、应用页排序/筛选、API 应用操作单）。`getRoundedCorner()` 是官方唯一提供的圆角原语，库内只有对话框拿它推同心圆，`GlassBottomSheet` 的底部安全区按官方契约交给调用方；隐藏手势提示线时 `navigationBars` 为 0，旧写法底部只剩 12dp。模拟器实测排序/筛选弹层底部留白 36dp → 44dp。详见 `docs/audit/miuix-os4-r15-audit.md` |
| 2026-11-25（同基线） | — | — | 设置顺序回退 + 预测性返回开关 + 对称雪花图标 + 隐藏应用名称（R16）：① 外观组回到「合成自适应图标 → 主页标签字号」；② 按官方 example 的做法（`HiddenApiBypass` 豁免 + 反射 `ApplicationInfo.setEnableOnBackInvokedCallback` + `recreateWithoutTransition`）加「预测性返回动画」开关，关闭只去掉系统跟手预览，`NavDisplay` 的 pop 转场照常；③ MIUIX 图标库无雪花，重绘对称雪花（6 臂同参数生成、主体 0.90 缩放、黑主体白底），同步 `ic_brand_mark` / `ic_tile_frozen`；④ MIUIX 无「隐藏应用名称」官方 API（库无桌面网格组件），Glacier 自己实现时**保留标签节点只做透明**，行距/图文间距仍是原 MIUIX token，图标不位移。详见 `docs/audit/miuix-os4-r16-audit.md` |
| 2026-11-25（同基线） | — | — | 冻结按钮雪花 + 设置页分级（R17）：① 主页 FAB 从 `MiuixIcons.Glass.QuietMode` 换成自绘 `SnowflakeIcon`（MIUIX 无雪花图标，24dp ImageVector，3 主轴 + 6 臂对称分叉）；② 设置首页去掉全部 `SmallTitle`，只保留通用两项 + 主题设置/搜索/自动化/系统四个 `ArrowPreference` 入口 + 关于；外观全部设置项（含上轮加的预测性返回动画）移到「主题设置」二级页，四个二级页统一走 `SettingsSubPage()`（官方 `expandable = false` + `BackNavigationAction`）；新增 4 个 `Route` 与 `NavDisplay` entry（`swipeDismiss = backSwipe`）。详见 `docs/audit/miuix-os4-r17-audit.md` |
| 2026-11-25（同基线） | — | — | 冷启动首帧显示主页应用（R18）：`GlacierRepository.primeSnapshot()` 在 `Application.onCreate` 同步用磁盘上的主页列表填快照（只读 apps/tags + 几项 PackageManager 信息，不查冻结态、不枚举全部应用），随后后台 `refresh()` 补全全部应用与冻结态（与 Activity 的 refresh 经 `refreshLock` 串行）。主页不再先闪一帧「0 个应用」空状态。详见 `docs/audit/miuix-os4-r18-audit.md` |
| 2026-11-25（同基线） | — | — | 冻结状态跟首帧一起到位（R19）：`primeSnapshot()` 改用 `FreezeController.isFrozen(info: ApplicationInfo)` 直接读刚拿到的 `ApplicationInfo` 位算冻结态（不再写死 `false`、也不额外查 `PackageManager`），首位快照即带正确冻结态；`refresh()` 拆成 `publishHome()`（只读主页包，实测 1~2ms）+ `refreshSnapshot()`（全量），主页任何一次刷新都毫秒级先到位；全量刷新复用枚举到的 `ApplicationInfo`（`entryFor(info)`），252 个包 593ms → 480ms。详见 `docs/audit/miuix-os4-r19-audit.md` |
| 2026-11-25（同基线） | — | — | 预测性返回开关不闪 + dependent-option 官方动画（R20）：① `recreate()` 后新窗口首帧常拿不到 `WindowInsets`，整页按「没有状态栏」排一帧再跳回（真机录屏实测位移恒为一个状态栏高度 144px）。官方 MIUIX 的 recreate 机制保留，在 `MainActivity` 加 `RecreatedInsetsGate`：重建时内容照常组合（返回栈/key 不变），只把首帧绘制 alpha 压 0，等 `systemBars` inset 下发再显示（最多 3 帧兜底，底色与 `windowBackground` 同色）；组合结构两条路径必须一致，否则 `rememberSaveable` key 变化会让 NavDisplay 返回栈掉回首页。② dependent-option 行（图标包 / 主页标签字号 / 自动冻结子项）改用官方 `CapturedAnimatedVisibility(enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically())`（同 `ThemeSettingsPage.kt:247-251`），不再用默认参数导致「淡出后高度硬跳」。详见 `docs/audit/miuix-os4-r20-audit.md` |
| 2026-11-26 | `542463a0`（main，`v0.9.5-rc01`） | `e7c108ee`（**main 最新**，rc01 + 10） | 升级最新基线（R21）：`GlassTopAppBar` 渐进模糊带重写（默认 `BandOverhang` 32→10dp、`ProgressiveBlurRadius` 80→32dp、`bottomContent` 起始比例自动计算）、`GlassBottomSheet` 的 `progressiveBlur` 默认 `false`（标题栏不再覆盖正文，`content` 的 `PaddingValues` 为 0）、新增官方 `GlassScrollableTabRow`、下拉弹层滚动指示器、AGP 9.5.3。Glacier 侧：主页标签 >4 个的玻璃档从普通 `TabRow` 换成官方 `GlassScrollableTabRow`；AGP 同步 9.5.3。详见 `docs/audit/miuix-os4-r21-audit.md` |
