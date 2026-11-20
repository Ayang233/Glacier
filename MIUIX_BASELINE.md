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
| 官方 tag | `v0.9.5-beta02` |
| **MIUIX commit** | `66248bacc7be29ae6bdc0957de9458b42ac67732` |
| commit 时间 | 2026-11-20 13:49:04 +0800 |
| commit 标题 | `chore(deps): update gradle to v9.9.2 (#488)` |
| 官方 main 追踪 | `f3829d3d162eb267a792d43b3c8541e64effc28d`（2026-11-20 17:44，比基线多 3 个提交，见 §7） |
| 本地补丁 | 已应用（`tools/miuix-composite-build.patch`） |
| 核对日期 | 2026-11-20（第五轮：从 `58b0a3dd` 升到 `66248bac`，跨 21 个官方提交） |

## 3. 工具链

| 项 | Glacier | 说明 |
|---|---|---|
| Gradle | 9.9.1（wrapper） | MIUIX 自身用 9.9.2，composite 下以 Glacier 的 wrapper 为准 |
| Kotlin | 2.4.21 | 与 MIUIX 一致 |
| AGP | 9.5.1 | 与 MIUIX 一致 |
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
| 按钮 | `GlassIconButton` / `GlassFloatingActionButton` | 顶栏动作、返回、FAB |
| 滚动 | `VerticalScrollBar` + `rememberScrollBarAdapter` | 只用作适配器，不接管轨道绘制 |
| 形状 | `squircleClip` / `squircleBorder` / `squircleBackground` | 应用图标、品牌图标 |
| 模糊 | `rememberLayerBackdrop` / `layerBackdrop` / `Backdrop` | 无自建模糊管线、无自建背景采样 |
| 主题 | `MiuixTheme` + `ThemeController` | 固定浅/深色板，见 §7 |
| 动效 | `scrollEndHaptic` / `overScrollVertical` / `PagerNavigationSpringSpec` / `springAnimateToPage` | 官方原语组合 |

**结论：不存在自建的 GlassSurface、模糊管线、背景采样、玻璃按钮或导航动画。**
自写的部分全部是"页面级组合/布局"，官方只在 example 里给样例，不提供库 API。

## 6. 定制代码边界

| 文件 | 性质 | 官方是否有库 API |
|---|---|---|
| `ui/components/PageFrame.kt`（PageFrame / PageTopBar / GroupCard / EmptyState / pageContentPadding） | 页面骨架组合 | 否（官方只有 `example/GlassPage.kt`） |
| `PageMetrics` | 尺寸常量，全部取自官方 example 或 `CardDefaults` | 否 |
| `Modifier.pageScrollModifiers` | 官方 `example/utils/PageUtils.kt` 的同义落地，由官方 primitives 组合 | 否（只有 primitives） |
| `Modifier.railStartInset` | 在 layout 期读侧栏宽度，避免每帧重组；语义等同官方 `overlayStart` 让位 | 否 |
| `ui/GlassEnvironment.kt` 的 `Local*` | 宿主职责：玻璃开关、根录制层、pane 已让位、底栏高度、标题透明度、标题避让 | 否（官方 example 同款做法） |
| `ui/GlacierShell.kt` | 一级/二级页路由 + 三态导航（底栏/折叠胶囊/展开侧栏） | 否 |
| `platform/AppIcons.kt` | 应用图标加载（图标包 / 自适应图标不套遮罩 / LRU） | 否，属业务能力 |
| `data/` `domain/` `system/` `platform/auto/` | 冻结业务本体 | 否 |

> 原则：薄封装可以留，但封装内**不得重新实现官方底层能力**。当前审计未发现违反项。

## 7. 已知差异与主动不做的事

| 项 | 现状 | 原因 |
|---|---|---|
| 官方 main 领先 3 个提交 | 未纳入基线：`f3829d3d` 侧栏性能、`c16dfa12` example 滚动复位、`b31c60f2` 顶栏负向 overscroll 修复 | 基线锚定在发布 tag，便于复现；下次升级时一并评估（`b31c60f2` 与顶栏相关，优先看） |
| Monet 动态取色 | 未接入。官方 `ColorSchemeMode.MonetSystem` 可用，Glacier 固定浅/深色板 | 品牌一致性；如需要可作为「主题」第三档，属于产品决策 |
| 宽屏 + 刘海 | 正文按官方 `startAlreadyReserved` 语义只让一次 cutout；顶栏用 `defaultWindowInsetsPadding=false` + End-only inset，避免第二次让位 | 官方 example 在 pane 内由 `appPageTopBarInsets()` 做同一件事 |
| 真机玻璃光学（L3） | 未验证。模拟器是宿主 GPU（GLES 3.1），能产出真实模糊像素，但不等于真机光学表现 | 需要一台真实设备补验 |
| 冻结业务链路 | Shizuku / Root / Dhizuku / DeviceOwner / Island / 特权 六条后端只有源码级证据 | 模拟器无可用后端 |

## 8. 升级流程（每次照做）

```bash
# 1. 拉官方最新 main 与 tag
tools/miuix-baseline.sh fetch

# 2. 看差距：落后多少提交、有没有新 tag、LIBRARY_VERSION 是否变了
tools/miuix-baseline.sh status

# 3. 读官方变更（release notes + 逐条 commit）
#    https://github.com/compose-miuix-ui/miuix/releases
#    重点看：@Deprecated 新增、公开签名变化、默认值变化、Compose/Kotlin 版本要求

# 4. 切到目标 tag（会顺带打本地补丁）
tools/miuix-baseline.sh pin v0.9.5-beta02

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
