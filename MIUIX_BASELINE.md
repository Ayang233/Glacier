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
| 官方 tag | `v0.9.5-beta03`（`3526cfe2`，基线在其之上还有 18 个 main 提交） |
| **MIUIX commit** | `44f1d31580bd127360d79bdb0bb869bd6c5af025` |
| commit 时间 | 2026-11-22 16:55:20 +0800 |
| commit 标题 | `build: Add googleapis maven-central for ci` |
| 官方 main 追踪 | 与基线相同（`origin/main`，落后 0 个提交） |
| 本地补丁 | 已应用（`tools/miuix-composite-build.patch`） |
| 核对日期 | 2026-11-22（第七轮：从 `9128921c` 升到 `44f1d315`，跨 2 个官方提交） |

## 3. 工具链

| 项 | Glacier | 说明 |
|---|---|---|
| Gradle | 9.9.1（wrapper） | MIUIX 自身用 9.9.2，composite 下以 Glacier 的 wrapper 为准 |
| Kotlin | 2.4.21 | 与 MIUIX 一致 |
| AGP | 9.5.2 | 与 MIUIX 一致；composite build 中两端 AGP 版本必须相同，否则依赖解析报 `AgpVersionCompatibilityRule` |
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
| 一级页/二级页骨架 | `GlassTopAppBar` / `TopAppBar` + `Scaffold` + `Card` + `BasicComponent` | 玻璃档/非玻璃档两档都由官方组件构成 |
| 底部导航 | `GlassNavigationBar` | 24dp 横向内边距 + 20dp 顶距，对齐官方 example |
| 侧栏 | `GlassNavigationRail` + `rememberGlassNavigationRailState` | 折叠胶囊由组件自带 |
| 页签 | `GlassSegmentedTabRow` / `TabRow` | 2–4 个标签走分段轨道，更多退回 TabRow |
| 搜索 | `GlassSearchOverlay` + `rememberGlassSearchBarAlpha` + `glassSearchBarTransition` | 搜索条转玻璃、400ms 进出动画都由官方驱动 |
| 文本输入 | `GlassTextField`（玻璃档）/ `TextField`（非玻璃档），均为 `TextFieldState` 新版 API | 旧的 `value/onValueChange` 重载已废弃 |
| 弹层 | `GlassDialog` / `GlassBottomSheet` / `GlassDropdownPreference` / `GlassDialogButton` | `renderInRootScaffold` 保持默认 true |
| 弹层内的分组卡片 | `GlassCard` | 0.9.5 main 新增；专用于玻璃表面上的内容卡，替代原先在 `GlassBottomSheet` 里复用的普通 `Card` |
| 按钮 | `GlassIconButton` / `GlassFloatingActionButton` | 顶栏动作、返回、FAB |
| 宿主让位 | `AppPageInsets` + `LocalAppPageInsets` + `appPageHorizontalInsets()` / `appPageTopBarInsets()`（`ui/PageInsets.kt`，官方 `utils/PageUtils.kt:62-152` 的逐字节移植） | 侧栏宽度、系统安全区只作用于内容；`NavDisplay` 恒满宽，背景与模糊带不被裁断 |
| 内容内边距 | `pageContentPadding`（官方惰性 `PaddingValues`）+ `rememberAppSafePadding()` | 组合期只建对象，`calculate*` 在布局阶段读，侧栏 morph 不触发重组 |
| 滚动 | `VerticalScrollBar` + `rememberScrollBarAdapter`，`trackPadding = contentPadding`、`topAppBarScrollBehavior = 顶栏行为` | 与官方 `MainPage.kt:359-364` 同参；轨道贴着内容区，顶栏收起时跟着收起 |
| 形状 | `squircleClip` / `squircleBorder` / `squircleBackground` | 应用图标、品牌图标 |
| 模糊 | `rememberLayerBackdrop` / `layerBackdrop` / `Backdrop` | 无自建模糊管线、无自建背景采样 |
| 关于页动态背景 | `ui/effect/`（官方 example 的 `BgEffect` 组件逐字节移植）+ `textureBlur` | 官方 AboutPage 的双 backdrop 范式：独立背景层录 BgEffect，品牌名/信息卡作为兄弟采样；混色 token 取自官方 `ColorBlendToken` |
| 主题 | `MiuixTheme` + `ThemeController` | 固定浅/深色板，见 §7 |
| 动效 | `scrollEndHaptic` / `overScrollVertical` / `PagerNavigationSpringSpec` / `springAnimateToPage` / `GlassMotion.default()` / 弹层 `DialogMotion.Glass` | 官方原语组合；选中环弹簧走官方 Folme 参数；弹层进出动画自基线 `44f1d315` 起由 `DialogMotion` 统一 |
| 状态派生 | `computedStateOf` / `CapturedAnimatedVisibility` | Compose 1.13 官方 API（官方 example 已全量使用），替代 `derivedStateOf` / `AnimatedVisibility` |

**结论：不存在自建的 GlassSurface、模糊管线、背景采样、玻璃按钮或导航动画。**
自写的部分全部是"页面级组合/布局"，官方只在 example 里给样例，不提供库 API。

## 6. 定制代码边界

| 文件 | 性质 | 官方是否有库 API |
|---|---|---|
| `ui/PageInsets.kt`（`AppPageInsets` / `LocalAppPageInsets` / `rememberAppPageInsets` / `appPageHorizontalInsets` / `appPageTopBarInsets` / `rememberAppSafePadding` / `pageBottomPadding`） | 官方 `example/utils/PageUtils.kt` 的逐字节移植（Apache-2.0），与 `ui/effect/` 同一处理方式 | 否（官方只在 example 里给，库不导出） |
| `ui/components/PageFrame.kt`（PageFrame / PageTopBar / GroupCard / EmptyState / pageContentPadding / pageScrollModifiers） | 页面骨架组合 + 官方 `pageContentPadding` / `pageScrollModifiers` 的同参落地 | 否（官方只有 `example/GlassPage.kt` 与 `PageUtils.kt`） |
| `PageMetrics` | 尺寸常量，全部取自官方 example 或 `CardDefaults` | 否 |
| `ui/GlassEnvironment.kt` 的 `Local*` | 宿主职责：玻璃开关、根录制层、底栏实测高度、标题透明度、标题避让 | 否（官方 example 同款做法） |
| `ui/GlacierShell.kt` | 一级/二级页路由 + 三态导航（底栏/折叠胶囊/展开侧栏） | 否 |
| `platform/AppIcons.kt` | 应用图标加载（图标包 / 自适应图标不套遮罩 / LRU） | 否，属业务能力 |
| `data/` `domain/` `system/` `platform/auto/` | 冻结业务本体 | 否 |

> 原则：薄封装可以留，但封装内**不得重新实现官方底层能力**。当前审计未发现违反项。

## 7. 已知差异与主动不做的事

| 项 | 现状 | 原因 |
|---|---|---|
| 官方 tag 落后基线 18 个提交 | 基线取 `origin/main` 最新提交 `44f1d315`，而最新 tag 仍是 `v0.9.5-beta03` | 需要 `GlassCard`（#495）与弹层 `DialogMotion`（#494），两者都只在 main；提交已 pin，复现无歧义 |
| `UiFeedback.toast()` | 仍用 Android `Toast` | 调用点包含 Worker / Receiver / 快捷方式等非 UI 上下文，MIUIX `Snackbar` 需要宿主 Scaffold，不能整体替换；属系统反馈而非自造 UI |
| 二级页切 Tab 的转场 | 仍走 `NavTransitions.MiuixDefault`（官方 example 在二级页点导航时切到私有的 `InstantNavTransition`，`AppContent.kt:320/1155`） | 该 helper 是 example 私有实现而非库 API；当前表现为"退回一级页时播放 pop 转场"，与官方观感有差异，但语义正确。需要完全一致时再移植 |
| 关于页背景效果调试面板 | 未移植（官方 AboutPage `:456-495` 的 `Background Effect` BottomSheet） | 官方 example 用来现场调 BgEffect 参数（OS2/OS3、动态背景、全屏）的调试面板，无产品意义；动态背景本体已完整对齐 |
| Monet 动态取色 | 未接入。官方 `ColorSchemeMode.MonetSystem` 可用，Glacier 固定浅/深色板 | 品牌一致性；如需要可作为「主题」第三档，属于产品决策 |
| 宽屏 + 刘海 | 顶栏 `defaultWindowInsetsPadding = false` + `appPageTopBarInsets()`；正文的横向让位只由内容 padding 提供一次 | 与官方 `AdaptiveTopAppBar`（`PageUtils.kt:265-304`）同构，不再有 End-only inset 补丁 |
| 真机玻璃光学（L3） | 未验证。模拟器是宿主 GPU（GLES 3.1），能产出真实模糊像素，但不等于真机光学表现 | 需要一台真实设备补验 |
| 冻结业务链路 | Shizuku / Root / Dhizuku / DeviceOwner / Island / 特权 六条后端只有源码级证据 | 模拟器无可用后端 |

## 8. 升级流程（每次照做）

```bash
# 1. 拉官方最新 main 与 tag（直连 github 失败会自动退回镜像，见下）
tools/miuix-baseline.sh fetch

# 2. 看差距：落后多少提交、有没有新 tag、LIBRARY_VERSION 是否变了
tools/miuix-baseline.sh status

# 3. 读官方变更（release notes + 逐条 commit）
#    https://github.com/compose-miuix-ui/miuix/releases
#    重点看：@Deprecated 新增、公开签名变化、默认值变化、Compose/Kotlin 版本要求

# 4. 切到目标 tag / commit（会顺带打本地补丁）；当前基线含尚未进 tag 的 GlassCard 与 DialogMotion
tools/miuix-baseline.sh pin 44f1d31580bd127360d79bdb0bb869bd6c5af025

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
