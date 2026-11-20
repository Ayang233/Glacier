# MIUIX OS4 / OS4 Glass 全面审计与基线升级（R5）

- 审计对象：Glacier（`com.ayang.glacier`），Android 13+ 应用冻结工具
- 审计时间：2026-11-20
- 基线变更：MIUIX `58b0a3dd` → `66248bac`（`v0.9.5-beta02`）
- 证据分级：**L1 源码接线**（可读源码判定）／**L2 运行时能力**（进程存活、组件真实挂载绘制）／**L3 像素光学**（真机硬件 GPU 才可判定）

> 本文件只回答三个问题：**用了什么、还差什么、这次改了什么**。
> 基线数值、升级流程、定制边界见仓库根目录 [MIUIX_BASELINE.md](../../MIUIX_BASELINE.md)。
> 历史轮次（R1–R4）的审计结论保留在 `miuix-os4-audit.md`、`miuix-os4-glass-audit.md`、`miuix-os4-r4-audit.md`。

---

## 1. 审计方法

1. 逐文件读 `app/src/main/kotlin`（46 个文件 / 7.9k 行），按"页面 → 组件 → 实现来源"建映射。
2. 对每个 UI 单元问四个问题：
   - 它是不是 MIUIX 官方组件？
   - 不是的话，官方**库**有没有等价能力？（注意区分"官方 example 有"和"官方库有"）
   - 官方能力与当前实现是否重叠？重叠的是底层能力还是业务组合？
   - 参数是不是官方默认值？有没有重复声明？
3. 用官方 `miuix/example/shared` 的同类页面做逐项对照（`GlassPage.kt`、`PageUtils.kt`、`AppContent.kt`、`IconPage.kt`）。
4. 用新基线实际编译一次，收集 `@Deprecated` 与行为变化，而不是靠猜。

---

## 2. 项目状态（审计时）

| 项 | 结论 |
|---|---|
| 接入方式 | composite build 源码引入 `../miuix`（`settings.gradle.kts`），**不是** Maven 坐标，也不是 fork |
| 页面规模 | 一级页 3 个（首页 / 应用 / 设置，HorizontalPager）+ 二级页 2 个（关于 / 许可证）+ 独立窗口 API 页 |
| UI 代码占比 | `ui/` + `feature/` ≈ 3.4k 行；`data/` `domain/` `system/` `platform/` ≈ 4.5k 行（业务，与 MIUIX 无关） |
| 主题 | 全部走 `MiuixTheme` + `ThemeController`，无自建色板 |
| Material3 | 未引入（`app/build.gradle.kts` 只依赖 compose foundation/ui/runtime） |

---

## 3. 组件映射表

判定口径：
**原生使用** = 直接用官方组件；**参数不规范** = 用了官方组件但重复声明默认值/绕开官方参数；
**重复封装** = 官方有库 API，项目又写了一层等价物；**业务实现** = 官方无对应能力。

| 场景 | 当前实现 | MIUIX 官方 | 判定 | 动作 |
|---|---|---|---|---|
| 根容器 | `Scaffold(Transparent, insets=0)` | `basic.Scaffold` | 原生使用 | 保留 |
| 一级/二级页骨架 | `PageFrame`（组合官方 Scaffold + 顶栏 + 底栏占位） | 库无 `GlassPage`，仅 example 有 | 业务实现（页面组合） | 保留 |
| 顶栏（玻璃） | `GlassTopAppBar` | `glass.GlassTopAppBar` | 原生使用 | **本轮新增 `centerObstructionStart` 接入** |
| 顶栏（非玻璃） | `TopAppBar` | `basic.TopAppBar` | 原生使用 | **本轮新增 `centerObstructionStart`** |
| 顶栏滚动行为 | `MiuixScrollBehavior` + `nestedScroll` | 官方 | 原生使用 | 保留 |
| 顶栏动作按钮 | `GlassIconButton` / `IconButton` | 官方 | 原生使用 | 保留 |
| 底部导航 | `GlassNavigationBar` | 官方 | 原生使用 | 保留（非玻璃档靠 `backdrop=null` 回退，不另起一套 `NavigationBar`） |
| 侧栏 | `GlassNavigationRail` + `rememberGlassNavigationRailState` | 官方 | 原生使用 | 保留 |
| 页签 | `GlassSegmentedTabRow` / `TabRow` | 官方 | 原生使用 | 保留 |
| 搜索 | `GlassSearchOverlay` + `rememberGlassSearchBarAlpha` + `glassSearchBarTransition` | 官方 | 原生使用 | 保留 |
| 文本输入 | `TextField(value/onValueChange)` + `GlassDialog` | **`TextFieldState` 新版 + `GlassTextField`** | **过时 API + 组件选型不符** | **本轮迁移** |
| 对话框 | `GlassDialog` + `GlassDialogButton` | 官方 | 原生使用 | 保留 |
| 底部面板 | `GlassBottomSheet` | 官方 | 原生使用 | 保留 |
| 下拉选择 | `GlassDropdownPreference` | 官方 | 原生使用 | 保留 |
| 设置行 | `SwitchPreference` / `ArrowPreference` / `SliderPreference` / `RadioButtonPreference` | 官方 | 原生使用 | 保留 |
| 卡片 | `Card` + `CardDefaults` | 官方 | 原生使用 | 保留（`GroupCard` 只固定 `insideMargin=0`，不重画底色/圆角） |
| 列表行 | `BasicComponent` + `BasicComponentDefaults` | 官方 | 原生使用 | 保留 |
| 滚动条 | `VerticalScrollBar` + `rememberScrollBarAdapter` | 官方 | 原生使用 | 保留（新基线把触摸目标收到 24dp，未覆盖默认值） |
| FAB | `GlassFloatingActionButton` | 官方 | 原生使用 | 保留 |
| 图标 | `MiuixIcons.Glass.*` / `MiuixIcons.Back` | 官方 | 原生使用 | 保留 |
| 应用图标形状 | `squircleClip` / `squircleBorder` / `squircleBackground` | 官方 | 原生使用 | 保留 |
| 玻璃录制/采样 | `rememberLayerBackdrop` / `layerBackdrop` / `Backdrop` | 官方 | 原生使用 | 保留 |
| 渐进模糊 | `GlassTopAppBar` 的 `progressiveBlur` | 官方管线 | **被项目按运行环境强制关闭** | **本轮删除该分支，恢复官方管线** |
| 动画 | `scrollEndHaptic` / `overScrollVertical` / `PagerNavigationSpringSpec` / `springAnimateToPage` | 官方 | 原生使用 | 保留 |
| Insets | `WindowInsets.*` + Scaffold 槽位 | 官方 insets 体系 | 参数不规范（宽屏 start 侧重复让位） | **本轮修正顶栏** |
| 主题/动态色 | `ThemeController` 固定浅/深色 | 官方 `ColorSchemeMode.MonetSystem` 可用 | 未接入（产品选择） | 记录，待确认 |
| 图标加载 | 自写 `AppIconSource`（图标包 / 自适应图标不套遮罩 / LRU） | **无对应能力** | 业务实现 | 保留 |

---

## 4. 自造轮子专项（源码级）

**结论：没有自建的 Glass 能力。** 逐项排查结果：

| 检查项 | 结果 | 证据 |
|---|---|---|
| 自建 `GlassSurface` | 无 | 全仓无此类型；玻璃表面全部来自 `glass.*` 组件 |
| 自写模糊管线 | 无 | 无 `RenderEffect` / `BlurEffect` / `Modifier.blur`；模糊只经 `miuix-blur` 的 `Backdrop` |
| 自实现背景采样 | 无 | `drawRect(surface) + drawContent()` 是官方 `rememberLayerBackdrop` 的标准录制写法（官方示例同款），不是自建采样 |
| 自模拟玻璃背景 | 无 | 无手绘高光/噪声/渐变叠加 |
| 自写玻璃导航动画 | 无 | 底栏/侧栏/FAB/弹层动画均由组件内部驱动 |
| 与官方重复的玻璃按钮 | 无 | 动作按钮是 `GlassIconButton`，未自绘 |
| 自维护硬件白名单 | 无 | 能力判断只调官方 `isRuntimeShaderSupported()` |
| 重复声明的官方默认值 | 仅一处语义化常量 | `PageMetrics.CardCorner = CardDefaults.CornerRadius`（引用而非复制数值），其余数值来自官方 example（12dp 页边距等），属页面排版而非组件默认值 |

**保留的薄封装（不是轮子）：**

| 封装 | 为什么留 | 官方是否提供等价库 API |
|---|---|---|
| `PageFrame` / `PageTopBar` | 把 Scaffold 槽位、录制层、搜索淡出、滚动条轨道组合成"一个页面" | 否（example 的 `GlassPage` 不是库 API） |
| `Modifier.pageScrollModifiers` | 官方 `PageUtils.kt` 的同义落地，内部只串官方 primitives | 否 |
| `Modifier.railStartInset` | 侧栏让位在 layout 期读取，避免 300ms morph 期间整页重组 | 否 |
| `GlassEnvironment` 的 `Local*` | 宿主职责：玻璃开关、根录制层、pane 让位、底栏高度、标题透明度/避让 | 否（官方 example 用同名 CompositionLocal 做同一件事） |
| `rememberSurfaceBackdrop` | `rememberLayerBackdrop` 的两行包装 | 否 |

---

## 5. 过时 / 变更 API（本轮实际处理）

| API | 变更 | Glacier 处理 |
|---|---|---|
| `TextField(value, onValueChange)` | `@Deprecated`，改用 `TextFieldState` | 迁移到 `rememberTextFieldState` + `state.text`；玻璃档改 `GlassTextField(state=...)` |
| `GlassTextField` | 0.9.5 新增；旧重载同时废弃 | 在 `GlassDialog` 内启用 |
| `ScrollBar` | 重构为 `ScrollIndicatorState` 驱动；`reverseLayout` 参数废弃；`ScrollBarDefaults.TouchTargetWidth` 24dp | Glacier 只用 `rememberScrollBarAdapter` + `VerticalScrollBar`，**无代码改动**；不覆盖触摸目标 |
| `TopAppBar` | 新增 `centerObstructionStart`；标题对齐逻辑重写 | 接入 `centerObstructionStart`（二级页）+ 顶栏 insets 修正 |
| `GlassTopAppBar` | 新增 `centerObstructionStart`；渐进模糊改由 `maskProgress` 驱动；顶栏顶部 inset 由 `statusBars` 改 `systemBars` | 接入新参数；渐进展宽交由官方，删除自有开关 |
| `Scaffold` | 内部新增 `TopAppBarInputTracker`（鼠标滚轮/按压判定） | 无代码改动，自动获得 |
| `MiuixWindowProperties` | 窗口回调移出 properties | Glacier 未引用 `miuix-window`，无影响 |
| Compose Multiplatform | 1.12.2 → 1.13.0-rc01（`computedStateOf`、`ScrollIndicatorState`） | BOM `2025.12.01`(1.10.0) → `2026.11.01`(1.13.0)，避免被 MIUIX 约束隐式抬升 |
| 官方 `main` 领先 3 个提交 | 侧栏性能、example 滚动复位、顶栏负向 overscroll 修复 | 未纳入本次基线，记录在 `MIUIX_BASELINE.md` §7 |

---

## 6. Glass / 渐进模糊专项

| 检查项 | 结论 |
|---|---|
| 是否使用官方渐进模糊 | **是**（本轮起）。此前 `Platform.isEmulator` 为真时把 `progressiveBlur` 与搜索转场 `blurEnabled` 强制关掉 |
| 是否保留旧版模糊实现 | 否 |
| 是否自维护模糊降级逻辑 | **曾有一处**：按 `Build.HARDWARE/PRODUCT/MODEL` 判断模拟器 → 已删除，全仓不再有设备白名单 |
| 是否重复背景采样 | 否。录制层共三层：根层 R（满屏，录在分页容器外）、页面层 P（先垫 surface 再录内容）、弹层内部层（官方组件自带） |
| 是否额外 GPU 开销 | 未发现自建 pass；`progressiveBlur` 的带宽/图层复用由库在 0.9.5 优化（`reuse cascade[0]`、噪声并入末次 blur、空转不录顶栏按钮 backdrop） |
| 是否有模拟器专用关闭逻辑 | **已全部删除**（`Platform.isEmulator` 及其调用点） |
| 能否直接接入官方能力 | 能。模拟器为宿主 GPU（GLES 3.1），AGSL 路径可产出真实模糊；见验证记录 |

**采样层级（未变，仍成立）**：消费者不是录制层后代；采样源在消费者整个绘制区域都有像素；尺寸/坐标只在量测、布局、绘制期读取。

---

## 7. 适配程度评估（分项，不给单一百分比）

| 维度 | 状态 | 依据 |
|---|---|---|
| MIUIX 基础体系（主题/字体/颜色/形状/insets） | **完整** | 无自建色板；形状走 squircle；insets 走 Scaffold 槽位 + 官方 insets |
| 原生组件覆盖 | **完整** | 页面骨架涉及的组件全部为官方组件（§3 表） |
| Glass 体系 | **完整** | 15 个 `glass.*` 组件 + 官方录制/采样/转场；无自建玻璃 |
| 渐进模糊 | **完整（本轮修复）** | 删除环境判断后走官方管线，模拟器实测生效 |
| 自造轮子数量 | **0** 个底层能力 | §4 |
| 过时 API 数量 | **0**（升级前 1 处 `TextField` 重载） | §5 |
| 不必要的重复封装 | **0** | §4 薄封装均有官方缺失的依据 |
| 未适配页面 | 无 | 5 个页面 + 1 个独立窗口全部走同一套 PageFrame |
| 保留的业务定制 | 图标加载、标签体系、多选、冻结后端、自动冻结 | 官方无对应能力 |
| 未纳入的官方能力 | Monet 动态取色（产品决策）、`miuix-window`（Android 单窗口不需要） | §7 |

---

## 8. 本轮改动清单

| 文件 | 改动 | 理由 |
|---|---|---|
| `gradle/libs.versions.toml` | `composeBom` 2025.12.01 → 2026.11.01（Compose 1.10.0 → 1.13.0） | 与 MIUIX 0.9.5 的 Compose 1.13 线统一，消除隐式抬升 |
| `app/src/main/kotlin/.../ui/components/PageFrame.kt` | 删 `Platform.isEmulator` 分支；`PageTopBar` 增加 `modifier` / `defaultWindowInsetsPadding` / `centerObstructionStart`；宽屏顶栏改 End-only inset | 恢复官方渐进模糊；修宽屏 start 侧 cutout 双算 |
| `app/src/main/kotlin/.../ui/GlassEnvironment.kt` | 新增 `LocalTopBarObstructionStart` | 官方 `centerObstructionStart` 的宿主传值 |
| `app/src/main/kotlin/.../ui/GlacierShell.kt` | 标题透明度/避让改为按路由提供：一级页淡出、二级页保持可见并避让胶囊 | 对齐官方 `AppContent.kt:221-234 / 432-435` |
| `app/src/main/kotlin/.../feature/home/HomeScreen.kt` | `TextField` → `TextFieldState`；玻璃档 `GlassTextField`；标签条条件简化 | 迁移废弃 API；用对口组件 |
| `app/src/main/kotlin/.../platform/Platform.kt` | 删除 `isEmulator` | 不再维护设备白名单 |
| `app/build.gradle.kts` | versionCode 4 / versionName 1.3.0；新增 `testImplementation(junit)` | 版本推进；补最小自动化测试能力 |
| `app/src/test/kotlin/.../TextIndexTest.kt` | 新增 5 个用例（空查询/大小写包含/九宫格开关/模糊顺序匹配/标点键） | 项目此前**没有任何自动化测试**；先守住最易回归的搜索匹配语义 |
| `MIUIX_BASELINE.md` | 新增 | 长期基线记录 |
| `tools/miuix-baseline.sh` | 新增 | 基线 status / fetch / pin / verify |
| `tools/miuix-composite-build.patch` | 新增 | 把 2 行本地补丁固化，换机器可复现 |
| `MIUIX_BASELINE.md` §9.1 | 新增自动化检测建议 | CI 未入库：当前 GitHub token 无 `workflow` scope，含 `.github/workflows/` 的推送会被 404 拒绝 |

---

## 9. 未完成 / 未验证（如实列出）

| 项 | 状态 | 说明 |
|---|---|---|
| 真机玻璃光学（L3） | **未验证** | 模拟器能产出真实模糊像素（宿主 GPU），但不等于真机光学；折射/边缘光/能耗必须真机判定 |
| 冻结业务六条后端 | **仅 L1** | 模拟器无 Shizuku/Root/DeviceOwner/Island，写操作无法运行时验证 |
| 自动冻结链路 | **仅 L1** | 同上，`setFrozenBatch` 返回 `Denied` |
| 通知使用权/使用情况访问 | **未实测** | 模拟器未授权 |
| 宽屏 + 刘海 cutout | **源码级修正，未实测** | 模拟器无刘海，无法复现双算场景 |
| Monet 动态取色 | **未接入** | 需要产品决定是否加第三档主题 |
| 官方 main 新增 3 提交 | **未纳入** | 见基线文档 §7 |
| 大字体 2.0x / 横屏操作面板 | **未复测** | R4 记录过的遗留项，本轮未触碰相关代码 |
