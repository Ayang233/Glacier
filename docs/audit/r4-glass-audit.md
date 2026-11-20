# Glacier R4 · Glass 专项审计（五层分级 + R3 完成度证据复核）

- 角色：Glass 审计负责人（**只读审计**，未修改任何源码/资源/其他文档）
- 日期：2026-11-19（快照时间见 §2；**末次更新 2026-11-19 23:59+**，见 §2 的 T2 快照与 §8.1 修复状态）
- 项目：`C:\Users\10487\WorkBuddy\Hail\Glacier`（`com.ayang.glacier`）
- MIUIX 基线：`C:\Users\10487\WorkBuddy\Hail\miuix` @ `58b0a3d`（与 R3 审计同一版本，含 `miuix-glass` / `miuix-blur` / `miuix-material` / `example` 源码）
- 写作范围：本文件（`docs/audit/r4-glass-audit.md`）为唯一被修改文件
- 约束遵守：未运行 gradle / 构建 / 安装 / adb；未改 `docs/audit/miuix-os4-r4-audit.md`；未改任何源码

**更新记录**

| 时间 | 变化 |
|---|---|
| 23:52（首版） | 基于 T0/T1 快照输出五层判定、缺陷清单、R3 完成度复核；L2 = 未复验、L3 = 未验证 |
| **23:59+（本次更新）** | 依据 lead 的最终 APK（23:44 构建 / 23:58 安装）与 `docs/verification/miuix-os4-r4/README.md`（52 张截图 + 转场拆帧 + logcat 0 FATAL/ANR），把 L2 改为"**R4 新构建已实测**"；§8 增加"R4 修复后状态"列；复核 P2-3 并给出建议补丁（未改代码）；新增 §6.7「宽屏折叠态标题不可见」结论；新增 P1-1 的**连带项**（顶栏/搜索的 start inset 仍会双算，需 delta 复核）。**L3 仍为未验证，未动结论。** |


---

## 0. 结论摘要（先看这一节）

**Glass 五层结论（R4 本轮，更新至最终 APK）：**

| 层 | 结论 | 依据 |
|---|---|---|
| 1. 是否接入官方 Glass 组件 | **通过** | 任务清单的 11 个消费点（顶栏/标签栏/底栏/侧栏/折叠胶囊/FAB/Dialog/BottomSheet/Dropdown/SearchOverlay/ApiActivity）全部使用官方 `miuix-glass` 组件，无自绘玻璃（§4） |
| 2. backdrop 使用是否正确（采样源层级） | **通过（静默期修正 2 条已完成：ApiActivity 窗口不透明、宽屏 start 预留语义；均待 delta 复核）** | 顶栏/FAB/标签栏采 P，底栏/侧栏/弹层采 R，搜索浮层自采内部层；ApiActivity 见 §6.1；宽屏见 §6.5 |
| 3. 采样层级是否安全（父/子自采样） | **通过** | 全部消费者都在其采样源的兄弟/子树之外；`LocalPageBackdrop` 陷阱已删除；无 `prepareTreeImpl` 递归风险（§5.3） |
| 4. 实际运行效果（L2） | **R4 新构建已实测（模拟器；`docs/verification/miuix-os4-r4/README.md`）** | 52 张截图覆盖 12 组场景 + 转场 `screenrecord` 拆帧 + 整轮 logcat 0 FATAL / 0 ANR。**注意分两个 APK**：52 张截图是 23:35 构建版本；最终 APK（23:44 构建 / 23:58 安装）只差 P1-1/P1-2 的 delta 复核（§7.2、§8.1） |
| 5. 真实 GPU 光学（L3） | **未验证，不得标通过** | 模拟器软件 GPU；`progressiveBlur` 在模拟器被主动关闭（`PageFrame.kt:298`）（§7.3） |

**缺陷处置状态（详见 §8）：**

- **已修并进入最终 APK**：P1-1（宽屏 start 双算）、P1-2（ApiActivity 半透明主题）、P2-1（`LocalPageBackdrop` 陷阱，已删除）、P2-2（死 CompositionLocal，已删除）、P2-6（`HomeScreen` 未使用的 `LocalRootBackdrop`，已删除）、P2-5（宽屏页签裁切疑点，R4 复测未复现）。
- **仍保留/记录性偏差**：P1-3（弹层 backdrop 不含 Shell bars，L3-only，记录）；**P2-3 仍在**（`ApiActivity` 的 `GlassDialog(backdrop = LocalRootBackdrop.current)` 未 `takeIf`，见 §8.1 建议补丁）；P2-4（宽屏断点用屏幕尺寸）；P2-7（模拟器 progressiveBlur 关闭）；P2-8（`GlassNavigationBar(visible=false)` 不释放布局高度）。
- **新发现（P1-4，P1-1 的连带项）**：P1-1 的修复只改了 `pageContentPadding`；**顶栏与搜索浮层/结果列表仍各自让一次 `displayCutout`**，而 pane 已经整体右移过一次 → 宽屏 + 刘海设备上，顶栏标题/页签与搜索条/结果会比正文再右移一个 cutout（正文已回到官方口径）。见 §6.5「连带项」与 §8。

**L3 门槛（硬性）**：真机（`ro.hardware.egl != emulation`）、`isRuntimeShaderSupported()==true`、`Platform.isEmulator==false`（否则 `progressiveBlur` 仍被关）；必须覆盖浅/深色、有壁纸背景、横向展开/折叠、底部弹层压底栏、搜索条滚动转玻璃、二级页转场 6 类场景。**在满足这些前置条件前，本轮不得出现"光学已通过"的表述。**

---

## 1. 审计方法与证据分级

### 1.1 证据分级（沿用 R3，未改定义）

| 等级 | 定义 | 可证明什么 | 不可证明什么 |
|---|---|---|---|
| **L1** | 源码接线正确：官方组件、组合树位置、backdrop 来源、兄弟关系、无自采样 | 结构/层级正确 | 运行时不崩、像素正确 |
| **L2** | 模拟器运行：组件挂载、绘制、交互、动画路径可跑通（截图/录屏/uiautomator） | 路径活着、可见 | 真实光学、真实 GPU 闪烁 |
| **L3** | 真实 GPU 光学：折射、模糊半径、边缘光、深色亮度、滚动中的实时采样 | 光学观感 | — |

补充证据类型（本文件用于 §9 的 R3 复核）：

- **【仅源码证据】**：只有 `file:line` 与官方对照，无运行时材料。
- **【有截图证据】**：有对应场景截图，但无测量数值/操作步骤/动画过程。
- **【已实测可复现】**：有截图 + 操作步骤或测量数值（uiautomator bounds 等），别人能按文档重做。

> 注意：**"截图存在"≠"声称的结论成立"**。静态截图不能证明退场动画、动画过程录制层、光学效果；R3 §17 里若干"已实测"实际只有源码或单帧截图证据（§9 逐项重判）。

### 1.2 Glass 五层（不得混为一谈）

| # | 层次 | 判定对象 | 典型错误 |
|---|---|---|---|
| ① | 接入 | 是否用官方 Glass 组件、`backdrop` 形参是否传对（玻璃档/非玻璃档回退） | 自绘玻璃、给内容控件强套玻璃 |
| ② | backdrop 正确 | 采样源是哪一层、该层是否包含消费者背后的真实像素 | 采样错层（拿弹层宿主/遮罩当源） |
| ③ | 采样层级安全 | 消费者是否在采样源的子树里（自采样） | 父录子采 → `libhwui` 递归崩溃 |
| ④ | 运行效果（L2） | 挂载、绘制、动画、交互正常 | 用"能截图"代替"动画正常" |
| ⑤ | 光学（L3） | 真实 GPU 下的折射/模糊/边缘光/亮度 | 用 SwiftShader 截图宣称光学通过 |

本文件中：§4 表逐项给 ①～③ + ④⑤ 的证据位；§5 给三层（②③④）独立矩阵；§7 给 L1/L2/L3 总结。

---

## 2. 代码快照与引用口径（重要）

| 时间点 | 状态 | 说明 |
|---|---|---|
| **T0 = 2026-11-19 23:15** | 工作区干净，HEAD `9457df1`（`git status` 为空） | 与 R3 验收构建（`3394e88` release / `b3309fe` ui）代码一致；本文件早期 `file:line` 基于 T0 阅读 |
| **T1 = 2026-11-19 23:36** | 工作区 18 个文件被 R4 并行改动（未提交） | §4 的表曾按 T1 复读 `GlacierShell.kt` / `PageFrame.kt` / `ApiActivity.kt` / `HomeScreen.kt` / `SettingsScreen.kt` / `AppsScreen.kt` / `AppActions.kt` |
| **T2 = 2026-11-19 23:59+**（本次更新的口径） | 最终 APK 已构建安装（lead：**23:44 构建 / 23:58 安装**，release）；工作区仍是上述未提交改动 + lead 的 5 项修复（`GlassEnvironment.kt` / `GlacierShell.kt` / `PageFrame.kt` / `values*/themes.xml` / `AndroidManifest.xml` / `HomeScreen.kt`） | **本文件当前所有 `file:line` 已按 T2 复读**；T2 的 Glass 修复清单与证据见 §8.1 |

T2 时与 Glass 相关的代码变化（相对 T1）：

| 项 | T2 变化 |
|---|---|
| 宽屏 start 双算 | 新增 `LocalPageStartReserved`（`GlassEnvironment.kt:34`）；Shell `provides wide`（`GlacierShell.kt:223`）；`pageContentPadding` 在 reserved 时 `start = horizontal`（`PageFrame.kt:421-429`） |
| ApiActivity 窗口 | `Theme.Glacier.Translucent` → `Theme.Glacier.Api`（`windowIsTranslucent=false`，`values/themes.xml:16-23`、`values-night/themes.xml:16-23`）；`AndroidManifest.xml` ApiActivity 改用该主题；`ApiActivity.kt:367` 保持 `containerColor = surface` |
| 自采样陷阱/死 API | `LocalPageBackdrop`、`LocalRailInset`、`LocalGlassRequested` 已删除（`GlassEnvironment.kt` 现仅保留 `:18/24/34/42/50/57` 六个 local）；`PageFrame.kt:229-231` 注释说明为何不再提供 |
| Home 未使用 local | `HomeScreen.kt` 中 `val backdrop = LocalRootBackdrop.current` 已删除（T2 起 `HomeScreen.kt:186` 是 `glassActive`） |

**引用口径**：

- 本文件当前所有 `file:line` = **T2** 工作区代码；`ApiActivity.kt:124` 等少数未变行在 T1/T2 相同。
- 仍标 `[T0]`/`[T1]` 的地方只用于说明"当时现场"，不用于现行代码定位。
- T2 **不对未来提交做承诺**：最终 APK 已冻结，但工作区仍可能被继续改动；delta 复核以 APK 行为为准。
- 本轮 L2 证据 = `docs/verification/miuix-os4-r4/README.md`（52 张截图 + 转场拆帧 + logcat）；**L3 仍无证据**。

---

## 3. 录制层与消费者树（T2 最终结构）

```
Scaffold(Transparent, contentWindowInsets = 0)                     [GlacierShell.kt:228-248]  弹层宿主（MiuixPopupHost 在 popup 槽）
├ bottomBar（窄屏）→ GlacierBottomNavigation                       [GlacierShell.kt:232-248]
│    └ Box(padding 24/20/max(24,safeBottom+8)) → GlassNavigationBar(backdrop = R)   [GlacierShell.kt:341-390, :380-389]
└ Box(fillMaxSize)                                                 [GlacierShell.kt:250]
   ├ Box(fillMaxSize).layerBackdrop(R)                             [GlacierShell.kt:251-254]  ★R 满屏录制层
   │  │   R = rememberLayerBackdrop { drawRect(surface); drawContent() }  [GlacierShell.kt:172-175]
   │  └ NavDisplay(.railStartInset(railInset), 宽屏 .clipToBounds()) [GlacierShell.kt:256-265]
   │     └ 一级页 HorizontalPager → PageFrame                       [GlacierShell.kt:278-293]
   │        └ Box                                                    [PageFrame.kt:199]
   │           ├ Scaffold(containerColor = surface, insets = 0)      [PageFrame.kt:200-205]
   │           │  ├ topBar   → PageTopBar → GlassTopAppBar(backdrop = P)  [PageFrame.kt:207-220, :291-309]
   │           │  ├ bottomBar → Spacer(LocalShellBottomBarHeight)     [PageFrame.kt:221-225]
   │           │  ├ fab      → GlassFloatingActionButton(P)           [PageFrame.kt:226-228；HomeScreen.kt:291-304]
   │           │  └ body     → Box.layerBackdrop(P)                  [PageFrame.kt:232-236]
   │           │                P = rememberSurfaceBackdrop(surface) [PageFrame.kt:186；GlassEnvironment.kt:85-88]
   │           ├ overlay()（搜索浮层 / 弹层调用点）                    [PageFrame.kt:272]
   │           └ （T2 起不再 provider `LocalPageBackdrop`）             [PageFrame.kt:229-231 注释]
   └ GlassNavigationRail(backdrop = R, showToggle = true)            [GlacierShell.kt:316-327]  R 的兄弟
宽屏 pane 预留：LocalPageStartReserved provides wide                 [GlacierShell.kt:223；GlassEnvironment.kt:34]
弹层实际绘制：最外层 Scaffold 的 popup 槽 / MiuixPopupHost           [Scaffold.kt:88, :199-204, :379；MiuixPopupUtils.kt:544-583]
ApiActivity：自己的整窗录制层 R'（不透明 surface + 内容）             [ApiActivity.kt:356-378]
```

对照官方（证明 Glacier 结构是官方范式的本地化，而不是自创）：

| 官方 | 结构 | Glacier 对应 |
|---|---|---|
| `example/AppContent.kt:644-682`（窄屏） | 根 `Scaffold.bottomBar` 的 `GlassNavigationBar` 采样根内容层 `Box.layerBackdrop(backdrop)` | `GlacierShell.kt:232-248` + `:172-175, :251-254` |
| `example/AppContent.kt:308-381`（宽屏玻璃侧栏） | 根内容 `Box.layerBackdrop(sidebarBackdrop)`，侧栏是它的**后续兄弟**（`:367-375`），页面靠 `overlayStart` 让位（`:229-246`） | `GlacierShell.kt:251-254` + `:316-327` + `railStartInset` + `LocalPageStartReserved` |
| `example/PageUtils.kt:99-103`（`startAlreadyReserved`） | 侧栏/宿主已让出 start 时 `safeInsets` 只保留 End + Vertical | `LocalPageStartReserved`（`GlassEnvironment.kt:26-34`）+ `PageFrame.kt:421-429` |
| `example/GlassPage.kt:264-271` | 整页 `Scaffold.layerBackdrop(pageBackdrop)`（**含**顶栏/底栏），供弹层采样 | Glacier **没有**这一层（弹层退而采 R，见 §6.2） |
| `example/IconPage.kt:173-176, :327` | 页面正文 `Box.layerBackdrop(backdrop)`（**不含** bars），顶栏/FAB/页签采它 | `PageFrame.kt:186, :232-236` + 顶栏/FAB 采 P |
| `example/IconPage.kt:448-450` | `GlassDialog(backdrop = 页面内容层)` | Glacier 弹层采 R（更宽，但同样不含 Shell bars） |
| `example/IconPage.kt:499-544` | 搜索浮层是 `Scaffold` 兄弟、自建内部 layer | `PageFrame.kt:272` + `GlassSearchOverlay` 内部层 |

---

## 4. 逐消费点判定表

> 列说明：**②** backdrop 来源与层级；**③** 是否可能自采样；**④** 采样语义是否正确（采到的是"消费者背后的真实像素"）；**判定** 给出缺陷编号（§8）+ R4 处置状态。

| 消费者 | 是否官方 Glass 组件 | ② backdrop 来源与层级 | ③ 自采样风险 | ④ 采样语义 | file:line（T2） | 判定与缺陷 |
|---|---|---|---|---|---|---|
| **顶栏** `GlassTopAppBar` | 是（`GlassTopAppBar.kt:268`，玻璃档）/ 非玻璃档官方 `TopAppBar`（`PageFrame.kt:310-330` 分支） | P（页面正文层），由 `backdrop` 参数显式接收；T2 起不再经 `LocalPageBackdrop` 传递 | **无**：顶栏在 `Scaffold.topBar` 槽，P 只录 body（`PageFrame.kt:232-236`） | **正确**：与官方 `IconPage.kt:252,327` 同构（顶栏采正文层）；滚动内容会从顶栏下方经过（`PageFrame.kt:238-253`），采样有真实像素 | `PageFrame.kt:186,207-220,291-309`；`GlacierShell.kt:145-158`（宽屏 inset） | ②③④通过；**P1-1 已修**（pane start 预留）；**P1-4 连带项待改**（顶栏仍自带 `displayCutout` 内边距，宽屏会与正文错位一个 cutout）；**P2-7**（模拟器 `progressiveBlur=false`，只影响 L3 证据） |
| **标签栏** `GlassSegmentedTabRow` | 是（home-p0 本轮从 `GlassTabRow` 换成官方 `GlassSegmentedTabRow`，对齐 `IconPage.kt:274-285`；非玻璃档官方 `TabRow`） | 顶栏 `bottomContent`，`backdrop` 形参 = P；组件内部优先取 `LocalGlassTopAppBarContext.backdrop`（`GlassTabRow.kt:582-607`，`:603-605` 解析顺序）→ 仍是 P | **无**：位于 `topBar` 槽（`PageFrame.kt:207-220`），不在 P 子树 | **正确**：采样语义 = 顶栏下滚动内容；`surfaceAlpha`（`HomeScreen.kt:282` 计算、`:661` 传入）与官方 `IconPage.kt:283` 同义 | `HomeScreen.kt:275-294, 636-673（GlassSegmentedTabRow :656）` | ②③通过；**P2-5 已复测消失**（R4 `07`–`10` 四态标签栏正常，圆角/对比度无异常）；黑底选中属组件选型/颜色语义（已按官方 IconPage 换掉）；**P1-4 连带项**会随顶栏一起错位 |
| **底栏** `GlassNavigationBar` | 是（`GlassNavigationBar.kt:218`） | R（根录制层）。在 Shell `Scaffold.bottomBar` 槽，绘制顺序在 body 之后（`Scaffold.kt:356,369`）；R 只录 content（`GlacierShell.kt:251-254`） | **无**：R 的兄弟（槽位在 R 之后绘制），不采自身 | **正确**：官方窄屏同构（`AppContent.kt:648-682` 底栏采根内容层）；`24dp` 横向内边距/`max(24dp,safeBottom+8dp)` 底安全区与 `GlassPage.kt:272-334` 一致；R4 `32`/`50` 截图可证运行时正常 | `GlacierShell.kt:232-248, 341-390` | ②③④通过 |
| **侧栏** `GlassNavigationRail` | 是（`GlassNavigationRail.kt:324`） | R；`Box` 第二子节点，绘制在 R 之后（`GlacierShell.kt:250-254` → `:316-327`） | **无** | **正确但需说明**：展开态面板区域在 R 里只有 `drawRect(surface)`（页面被 `railStartInset` 整体右移，`GlacierShell.kt:256-265`），因此面板玻璃采到的是纯 surface；这与官方宽屏玻璃侧栏**完全一致**（`AppContent.kt:335-353`），**不是缺陷，不要为此把 R 改成跟着内容走** | `GlacierShell.kt:316-327`；`GlassNavigationRail.kt:386-399`（面板自身 insets） | ②③④通过；**P1-1 已修**；R4 `41`–`45` 截图可证两态正常（布局数值 delta 待复核） |
| **折叠胶囊**（`GlassNavigationRail` 内置，`showToggle=true`） | 是（同一组件） | R | **无** | **正确**：`LocalSearchTopInset`（`GlacierShell.kt:160-167`）、`LocalTopBarTitleAlpha`（`:169`）与官方 `AppContent.kt:217-223,239-246` 一一对应；R4 `40`/`44`/`45` 截图：折叠态只剩胶囊、标题不可见 —— **这是官方语义，见 §6.7** | `GlacierShell.kt:160-169, 316-327`；`GlassNavigationRail.kt:300-349` | ②③通过；④ 见 §6.7（结论：官方语义，非 over-apply） |
| **FAB** `GlassFloatingActionButton` | 是（`GlassFloatingActionButton.kt:76`） | P（`PageFrame` 把 `backdrop` 传给 fab 槽，`PageFrame.kt:226-228`；Home 使用 `HomeScreen.kt:291-304`） | **无**：FAB 在 `Scaffold.floatingActionButton` 槽，P 只录 body | **正确**：FAB 背后就是正文；`backdrop=null` 时组件回退 `primary` 不透明填充（`GlassFloatingActionButton.kt:59-73`） | `PageFrame.kt:226-228`；`HomeScreen.kt:291-304` | ②③④通过 |
| **Dialog** `GlassDialog` | 是（`GlassDialog.kt:48`） | R（`LocalRootBackdrop`）；实际绘制在最外层 `Scaffold` 的 popup 槽（`Scaffold.kt:199-204,379`），不在 R 子树 | **无**：popup 是 content 的兄弟且最后绘制 | **基本正确**：R 含页面 + 顶栏 + FAB，**不含 Shell 底栏/侧栏**（§6.2）；与官方 `IconPage.kt:448-450` 实践一致，与 `GlassDialog.kt:20` KDoc 有偏差（L3 才可见） | `HomeScreen.kt:771-774, 816-819`；`ApiActivity.kt:121-124` | ②③通过；**P1-3 保留（记录性偏差）**；ApiActivity 另见 **P2-3**（未 `takeIf`，§8.1 建议补丁） |
| **BottomSheet** `GlassBottomSheet` | 是（`GlassBottomSheet.kt:50`） | R（同上） | **无** | **基本正确**：R3 `13-action-sheet.png` 与 R4 `32`/`33` 可证运行时弹层正常、面板背后内容被玻璃采样；最下缘采不到底栏胶囊（同 P1-3）；`insideMargin` 由组件默认提供，未叠第二层 padding（`AppActions.kt:202-212`） | `AppActions.kt:202-212`；`HomeScreen.kt:692-696, 856-859`；`AppsScreen.kt:262-265, 312-315` | ②③通过；**P1-3 保留** |
| **Dropdown** `GlassDropdownPreference` | 是（`GlassDropdownPreference.kt:63`） | R；`renderInRootScaffold` 默认 true（`GlassDropdownPreference.kt:72,154`）→ 同样挂在最外层 popup 槽 | **无** | **基本正确**（同弹层语义）；`backdrop` 为 null 时官方回退不透明（`GlassDropdownPreference.kt:51`） | `SettingsScreen.kt:72, 149-172, 190-198, 217-227, 371-389` | ②③通过；**P1-3 保留** |
| **SearchOverlay** `GlassSearchOverlay` | 是（`GlassSearchOverlay.kt:196`） | **自建内部录制层** `contentBackdrop = rememberLayerBackdrop()`（`:268`），录搜索结果列表（`:296-302`，列表自带 `.background(surface)` 兜底）；搜索条/取消按钮在层**外**（`:349-365, :464-504`） | **无自采样**：消费者（band/pill/cancel）都不在 `contentBackdrop` 子树；搜索浮层整体在 R 内（`PageFrame.kt:272`），内部再录一层属于官方同款嵌套录制（`IconPage.kt:499-544`） | **正确**：`blurEnabled = glassActive`（`HomeScreen.kt:341`、`AppsScreen.kt:157`）；`blurEnabled=false` 时 `contentBackdrop=null` 走不透明回退（`GlassSearchOverlay.kt:267-268, :328-346`）；R4 `17`–`22` 实测开/结果/关均正常、无残留遮罩/键盘 | `PageFrame.kt:272`；`HomeScreen.kt:324-353`；`AppsScreen.kt:137-157` | ②③通过；④ L2 已实测通过；**P1-4 连带项**：宽屏+cutout 时搜索条/结果会再让一次 cutout |
| **ApiActivity 独立窗口** | 是（`GlassDialog`/`GlassBottomSheet` 复用） | 自带整窗层 R'：`rememberLayerBackdrop { drawRect(surface); drawContent() }`（`ApiActivity.kt:356-359`）；弹层在根 popup 槽（`:365-378`） | **无** | **已修并进入最终 APK**：窗口改为不透明 `Theme.Glacier.Api`（`windowIsTranslucent=false`，`values/themes.xml:16-23` / `values-night/themes.xml:16-23`；`AndroidManifest.xml` ApiActivity `android:theme="@style/Theme.Glacier.Api"`），`Scaffold.containerColor = surface`（`ApiActivity.kt:367`）→ 可见像素 = 录制像素。R4 `50`/`51` 实测：独立窗口操作单/错误弹层正常、背景纯灰不透底（`50` 是旧半透明 APK 行为；最终 APK 需 delta 复核） | `ApiActivity.kt:356-378`；`values/themes.xml:16-23` | ②③通过；**P1-2 已修**（最终 APK）；**P2-3 仍在**（见 §8.1） |

**额外消费者（不在任务清单但属 Glass 体系）**

| 消费者 | 结论 |
|---|---|
| 顶栏 `GlassIconButton`（`PageFrame.kt:341`，T2 同） | 官方组件，采样继承顶栏材质/`LocalGlassTopAppBarContext`（`GlassTopAppBar.kt:488-508`）；安全 |
| `GlassDialogButton`（各处 Dialog 按钮） | 官方组件，属于弹层内部材质，不自采；安全 |
| `BackNavigationAction`（R4 新增，`PageFrame.kt:520-540`） | 玻璃档 `GlassIconButton` + `Glass ChevronBackward`；非玻璃档官方 `MiuixIcons.Back`；**不参与 backdrop 采样**，安全 |

---

## 5. 五个层次分开判定

### 5.1 ① 接入层（是否官方组件、玻璃档/非玻璃档回退）

| 消费点 | 玻璃档组件 | 非玻璃档回退 | 判定 |
|---|---|---|---|
| 顶栏 | `GlassTopAppBar`（`PageFrame.kt:291-309`） | 官方 `TopAppBar`（`PageFrame.kt:310-330`） | 通过 |
| 标签栏 | `GlassSegmentedTabRow`（本轮） | 官方 `TabRow`（`HomeScreen.kt:664-672`） | 通过 |
| 底栏/侧栏/折叠胶囊 | `GlassNavigationBar` / `GlassNavigationRail` | `backdrop=null` 时官方组件内部不透明回退（`GlassNavigationBar.kt:194-195`、`GlassNavigationRail.kt:309-310`） | 通过 |
| FAB | `GlassFloatingActionButton` | `backdrop=null` → `fill=primary`（`GlassFloatingActionButton.kt:61-64,84-88`） | 通过 |
| Dialog/Sheet/Dropdown | 官方组件 | `backdrop=null` → `containerColor` 不透明（`GlassDialog.kt:21-22`、`GlassBottomSheet.kt:20-21`、`GlassDropdownPreference.kt:51`） | 通过 |
| 搜索浮层 | `GlassSearchOverlay` | `blurEnabled=glassActive`；false 时 `contentBackdrop=null` 走不透明回退 | 通过 |
| ApiActivity | 同上（复用） | `LocalRootBackdrop` 提供 null | 通过 |

**唯一决策点**：`LocalGlassActive = preferences.glassEnabled && isRuntimeShaderSupported()`（`GlacierShell.kt:107`、`ApiActivity.kt:354`）。页面里没有第二个能力判断 → 与 `glass-contract.md:94` 一致。**通过。**

### 5.2 ② backdrop 正确（采样源与真实背景的对应）

| 消费点 | 采样源 | 消费者背后的真实像素在源里吗 | 判定 |
|---|---|---|---|
| 顶栏 / FAB / 标签栏 | P（正文层） | 在：正文滚动内容 + 页面 surface（P 先 `drawRect(surface)` 再录内容，`GlassEnvironment.kt:85-88`），不存在"空白→玻璃脏斑" | 通过 |
| 底栏 / 侧栏 / 折叠胶囊 | R（整页内容层） | 在：页面内容随 NavDisplay 滚动/分页；R 先垫 surface | 通过 |
| Dialog / BottomSheet / Dropdown | R | 页面/顶栏/FAB 在；**Shell 底栏与侧栏不在**（§6.2） | 条件通过（L3 可见偏差，P1-3 保留） |
| 搜索浮层 | 内部 `contentBackdrop` | 在：搜索结果列表自身；`contentBackdrop` 录的是列表（带 surface 底） | 通过 |
| ApiActivity 弹层 | 自带 R' | 在（最终 APK：窗口不透明 + 录制层垫 surface，录制像素 = 可见像素） | 通过（delta 复核窗口进出） |

### 5.3 ③ 采样层级安全（父/子自采样）

逐条核对"消费者是否在其采样源子树内"：

1. 顶栏/FAB/标签栏 → 采 P；P 只挂在 `Scaffold` 的 content 槽（`PageFrame.kt:232-236`），顶栏/FAB 在 topBar/fab 槽 → **兄弟**，安全。
2. 底栏/侧栏 → 采 R；R 挂在 `Shell` content 内层 Box（`GlacierShell.kt:251-254`），底栏在 Shell `Scaffold.bottomBar`（`:232-248`）、侧栏是 R 的后续兄弟（`:316-327`）→ **兄弟**，安全。
3. 弹层 → 采 R；实际绘制在最外层 `Scaffold` 的 popup 槽（`Scaffold.kt:199-204,379`），popup 与 content 是不同子槽 → **不在 R 子树**，安全。`renderInRootScaffold=true` 通过 `LocalRootDialogStates/LocalRootPopupStates` 把内容上提到最外层（`Scaffold.kt:102-117`、`MiuixPopupUtils.kt:184-188,544-583`），页面内调用点（如 `HomeScreen.kt` 的 Dialog/Sheet）只是"声明"，绘制点在根 popup。
4. 搜索浮层 → 采自己的 `contentBackdrop`；搜索条/取消按钮/band 都在该层之外（`GlassSearchOverlay.kt:296-302` 后、`:312-327,349-365,464-504`）→ 安全。搜索浮层整体在 R 内，属于**嵌套录制**（外层 R 记录内部层绘制），官方 `IconPage.kt:499-544` 同构，非自采样。
5. ApiActivity → 弹层采 R'，R' 挂在 content（`ApiActivity.kt:370-373`），弹层在根 popup（`:365-378`）→ 安全。
6. **未发现**任何"消费者在其采样源子树内"的接线；本轮没有 `prepareTreeImpl` 递归风险。R4 实测：52 张截图 + 整轮 logcat **0 条 `FATAL EXCEPTION` / 0 条 ANR**（`docs/verification/miuix-os4-r4/README.md` §12），与 L1 判定一致。

**雷区已拆除（T2）**：原 `LocalPageBackdrop` 的 provider 位于 P 层子树内部，页面正文组件读它必然自采样；**T2 已删除该 local 与 provider**（`GlassEnvironment.kt` 不再定义；`PageFrame.kt:229-231` 留注释说明原因），同时删除无消费者的 `LocalRailInset` / `LocalGlassRequested`。这一层级的自采样风险归零。

### 5.4 ④ 运行效果（L2）— **R4 新构建已实测（模拟器）**

**证据来源**：`docs/verification/miuix-os4-r4/README.md`（verifier 产出，2026-11-19 23:38–23:57）。

- 被测包：`com.ayang.glacier` **release 1.1.0**（versionCode 2），设备 Android 16 模拟器 `emulator-5554`（1080×2400 / 420dpi）。
- 产出 **52 张截图**（`01`–`52`，12 组场景）+ 首页/About/K 长按转场的 `screenrecord` 拆帧（30fps，`23`–`26`、`52`）。
- 崩溃/ANR：整轮 `logcat -b crash` **0 行**；主 logcat 过滤 `FATAL EXCEPTION|ANR in|am_anr|E AndroidRuntime` **0 命中**；dropbox 无本轮 `ayang` 条目（README §12）。
- **Glass 生效性**：模拟器 API 36 满足 `isRuntimeShaderSupported()`（MIUIX 实现即 `SDK_INT>=33`），因此 **RuntimeShader 实时模糊路径确实被执行**（README「Glass 生效说明」）；但这仍是软件 GPU，L3 不可由它证明。

**Glass 相关场景证据（R4 实测）**：

| 证据 | 场景 | Glass 结论 |
|---|---|---|
| `07`–`10` 四态标签栏 | 浅/深 × Glass 开/关 | 玻璃档 `GlassSegmentedTabRow` 轨道/选中胶囊完整，非玻璃档官方 `TabRow` 回退正确；无文字截断/胶囊越界（**原 P2-5 宽屏裁切疑点未复现**） |
| `17`–`22` 搜索（Home/Apps） | 开 → 结果 → 关 | 搜索条/结果列表/关闭正常，无残留遮罩、无残留键盘（`mInputShown=false`）；官方常驻组合 + 退出路径工作 |
| `23`–`26` 转场拆帧 | push/pop 中间帧 | 系统圆角裁剪（约 16dp 观感）、scrim 压暗、无白闪/黑帧；**转场层没有画到玻璃侧栏下面**（R4 `clipToBounds` 生效）；`26` 可见底栏玻璃把身后「关于」行模糊掉（**玻璃采样的直接 L2 证据**） |
| `29`–`31` 滚动条 | Licenses/About | 官方 `VerticalScrollBar` 出现/隐藏正常；顶栏玻璃模糊其下滚过内容 |
| `32`–`33` 操作单 | 含 2.0x 字体 | BottomSheet 正常、行完整、无被底栏遮挡 |
| `34`–`39` 大字体 | 1.3x/2.0x Home/Apps/Settings | 无溢出/重叠；2.0x 下 Settings 内容可滚到悬浮底栏之下属正常叠层 |
| `40`–`45` 横屏宽屏 | 折叠/展开两态 | 胶囊/侧栏/让位两态成立；**当前 APK（23:35 版）复现 start 双算**（`44`/`45` 左缘 288/256px），最终 APK 已修，delta 待复核 |
| `46`–`49` 深色 | Home/Apps/Settings/About | 深色玻璃材质正常、无黑底黑字 |
| `50`–`51` ApiActivity | SHOW_APP_INFO / 错误 Dialog | 独立窗口弹层挂载正常；`50` 记录的是旧半透明主题窗口（背景纯灰、未透出宿主），最终 APK 改为 `Theme.Glacier.Api` 后需复核"不透明页面 + Glass 弹层" |
| `52` 长按过渡拆帧 | 进入选择态中间帧 | 网格上移是平滑动画（约 5 帧/0.17s），无闪白/重影 |

**仍不能由本轮 L2 证明的**：真实 GPU 光学（L3）；系统级圆角/刘海遮罩在截图中的像素表现（README「无法验证」§2 明确说明 `screencap` 不含显示层遮罩）；真机触感/高刷；RTL；三键导航安全区。

**APK 版本口径（重要）**：README 的 52 张截图采集于 **lead 23:35 构建版本**；lead 说明最终 APK 为 **23:44 构建 / 23:58 安装**，其中包含 P1-1（start 预留）与 P1-2（ApiActivity 主题）修复。因此：**L2 = 已实测（覆盖 R4 全部 Glass 改动）**，但 **P1-1/P1-2 的 delta 复核（横屏左缘 ≈160px、ApiActivity 窗口不透底）仍需在 23:58 安装的 APK 上补一次**，见 §8.1。

### 5.5 ⑤ 光学（L3）

**未验证。不得标通过。** 原因链：

1. 模拟器 `ro.hardware.egl = emulation`（R3 README §二）→ RenderEffect/RuntimeShader 产出的像素不可信。
2. 顶栏 `progressiveBlur = !Platform.isEmulator`（`PageFrame.kt:298`）：模拟器上**主动关闭** AGSL 渐进模糊，走库的 band 渐变（`GlassTopAppBar.kt:463-487`）——截图里的顶栏根本不是真实玻璃路径。
3. `GlassTopAppBar` 内部的 `progressiveBackdrop`（`GlassTopAppBar.kt:403-411`）同理只在 progressive 分支启用。
4. `isRuntimeShaderSupported()` 在模拟器可能返回 true，但这只代表"shader 能编译"，不代表像素正确（R3 已如此表述）。

---

## 6. 重点专项复核（R3 声称已修 / 需再验证项 + R4 新发现）

### 6.1 ApiActivity 采样语义 — **已修并进入最终 APK（待 delta 复核）**

- **R3 状态**：整窗录制层 `drawRect(surface) + drawContent()` 录的是**不透明** surface；窗口 Theme 是 `Translucent` + 透明 windowBackground，外层又叠 `surface.copy(alpha=0.92f)`。屏幕上可见的是"92% surface + 8% 宿主"，录制层里是"100% surface" → 玻璃采样源与可见像素不一致（R3 `miuix-os4-audit.md:395` 已指出，R3 玻璃审计没修，只描述了"坐标隔离"）。
- **R4 修复（最终 APK）**：
  1. `ApiActivity.kt:350-378`：`Scaffold(containerColor = surface)`（`:367`），删除 0.92 alpha 背景；录制层 `:356-359` 仍是 `drawRect(surface)+drawContent()` → **可见像素 = 录制像素**。
  2. 窗口主题：原来的 `Theme.Glacier.Translucent`（`windowIsTranslucent=true` + 透明 windowBackground）替换为 **`Theme.Glacier.Api`**（`values/themes.xml:16-23`、`values-night/themes.xml:16-23`：`windowIsTranslucent=false`、`windowBackground=@color/glacier_window_background`、`windowNoTitle=true`、`backgroundDimEnabled=false`）；`AndroidManifest.xml` 的 ApiActivity 已改为 `android:theme="@style/Theme.Glacier.Api"`。主题注释里直接写明了"旧版 windowIsTranslucent + 92% 透明底色会让可见像素 ≠ 录制像素"。
- **判定**：**P1-2 已修**（源码层面，T2 复核确认；最终 APK 已包含）。
- **仍需 delta 复核（1 项）**：R4 `50-api-show-app-info.png` 采自旧半透明 APK（背景纯灰、未透出桌面）；最终 APK 需重拍确认"窗口不透明 + Glass 弹层背景完整 + 无首帧透底/白闪"。复核命令：`am start -n com.ayang.glacier/com.ayang.glacier.feature.api.ApiActivity -a android.intent.action.SHOW_APP_INFO --es android.intent.extra.PACKAGE_NAME com.rosan.installer.x.revived`。

### 6.2 弹层 backdrop 是否完整（Dialog / BottomSheet / Dropdown）— 记录性偏差

- **事实**：三类弹层都采 R（`LocalRootBackdrop`）。R = 页面内容 + 顶栏 + FAB，**不含** Shell 悬浮底栏（`Scaffold.bottomBar`，`GlacierShell.kt:232-248`）与宽屏侧栏（`:316-327`），因为它们是 R 的兄弟。
- **与官方对比（关键，避免误判为缺陷）**：
  - 官方 `GlassPage.kt:264-271,405-408`：弹层采**含 bars 的整页层**（该 demo 是单页应用，页面 Scaffold 就是整窗）。
  - 官方 `IconPage.kt:173-176,327,448-450`：弹层采**页面内容层**（不含 bars）——**Glacier 的实践与这一种一致**，而且 R 比 IconPage 的页面层还多了顶栏/FAB。
  - 组件 KDoc 要求 "must include the page and bars"（`GlassDialog.kt:20`、`GlassBottomSheet.kt:20`）→ Glacier 不满足其中的 "bars"。
- **可观测影响（仅 L3）**：底部弹层（`AppActions.kt`、`SortSheet`、`FilterSheet`、`TagEditorSheet`、`DeferredSheet`）面板向下延伸到屏幕底缘，视觉上会盖住 Shell 底栏胶囊所在区域；R 里该区域只有页面 surface，所以玻璃不会把"底栏胶囊"的像素带进来。R3 `13-action-sheet.png` 中面板底部是干净的浅色，与此一致。
- **结论/建议**：**R4 不重构**。理由：(a) 与官方 IconPage 实践一致；(b) 只有 L3 能判，目前无真机；(c) 修复需要"再录一层 bars 并合成 Backdrop"（官方 `GlassPage.kt:152-168` 的 `secondaryBackdrop` 思路），会动 Shell 骨架与弹层 API，风险大于收益。**列为已知偏差，若 lead 要求 100% 对齐 KDoc，再单独立项。**

### 6.3 页面内容 / 顶栏 / 导航栏的采样关系 — L1 通过

- 顶栏/FAB/标签栏采 **P**（正文层，`PageFrame.kt:186,232-236`）；底栏/侧栏采 **R**（整页层，`GlacierShell.kt:172-175,251-254`）；弹层采 **R**（根 popup 外）。
- **两层都在录制前垫 surface**：R（`GlacierShell.kt:172-175`）、P（`GlassEnvironment.kt:85-88`）。这满足 `glass-contract.md:12-14` 的第二条红线（采样源在消费者整个绘制区域都有像素），且比官方 IconPage 的裸 `rememberLayerBackdrop()`（`IconPage.kt:173-176` 有垫底，但页面 body 层 `:327` 的 backdrop 没垫）**更严格**。
- **页面内容从顶栏下经过**：`Scaffold` 的 body 铺满全屏（`Scaffold.kt:348-356` 用 `looseConstraints` 测量并 `place(0,0)`），列表 `contentPadding.top = 顶栏高度`（`PageFrame.kt:410-437`），滚动项会进入顶栏区域 → 顶栏玻璃采样有真实内容（R3 `06-settings-bottom.png`、R4 `29`/`30` 可证）。
- **底栏采样的是正文层而非"底栏自身"**：底栏在 R 之后绘制（`Scaffold.kt:369`），R 又只录 content → 不自采样。
- **唯一不正确的地方**是宽屏 pane 的**几何让位量**（P1-1），不是采样层级；该几何问题已在最终 APK 修复（§6.5）。

### 6.4 动画过程中录制层是否正常 — 源码推理通过 + R4 转场/搜索拆帧已实测

R4 实测补充（`docs/verification/miuix-os4-r4/README.md` §4、§5）：搜索开/关（`17`–`22`）无残留遮罩/键盘；push/pop 转场用 `screenrecord` 30fps 拆帧（`23`–`26`），无白闪/黑帧、圆角裁剪正常、底栏玻璃在动画尾段正确模糊其下内容。**仍未被拆帧覆盖**：侧栏 300ms morph 过程（`40`–`45` 只有两态静态帧）。

逐个动画路径看"录制层是否在动画期间仍被正确更新/是否被 alpha/裁剪污染"：

| 动画 | 录制层影响 | 风险判定 |
|---|---|---|
| 顶栏大标题折叠（`GlassTopAppBar` collapseRamp + `MiuixScrollBehavior`） | 顶栏在 P 的兄弟槽；P 由 body 绘制顺序先录 → 顶栏采样到同帧内容 | 无风险；R3/R4 截图可证折叠态 |
| 搜索进/出（页 `graphicsLayer{alpha=searchBarAlpha}`、`glassSearchBarTransition`） | P 录的是原始内容（外层 alpha 不影响录制），顶栏也在同层 alpha 下 → 采样与显示同步淡出 | **R4 已实测**（`17`–`22` 交互 + 无残留；退场动画未见拆帧，但交互路径已验证） |
| 侧栏 morph 300ms（`railState.progress`） | `railStartInset` 在 `layout{}` 里读 `inset()`（`PageFrame.kt:367-385`），只触发重测量；R 满屏不变；侧栏每帧采样 R | 结构无风险；P1-1 已在最终 APK 修复（不再每帧叠加双 inset）；**morph 过程仍建议录屏 delta 复核** |
| 弹层进/出（常驻组合 + `show`，`MiuixPopupUtils` 动画） | R 在弹层**之前**录制完成；弹层是独立 popup 槽叠加 | 结构无风险；R4 `32`/`33` 静态通过，退场动画未见拆帧 |
| 分页滑动（`HorizontalPager` + spring） | 每页有自己的 P；pager 在 R 内 → R 每帧录当前可见页；顶栏随页切换 | 无风险（`beyondViewportPageCount=0`，`GlacierShell.kt:283`） |
| 二级页转场（R4 新增 `NavDisplayEffects`，`GlacierShell.kt:177-200,265-266`） | 转场层在 R 内绘制；圆角裁剪 `cornerClipMode=Leading` + 宽屏 `clipToBounds`（`:265`） | **R4 已实测（拆帧）**：无白闪/黑帧、无撕裂；滚入页没有画到玻璃侧栏下面（`clipToBounds` 生效）；截图不含系统级圆角遮罩（见 README「无法验证」§2） |

**结论**：从源码看，没有任何动画会让"消费者画进自己的采样源"；录制/采样顺序在每帧都是 body→bars/popup。R4 已用拆帧覆盖搜索与转场；**侧栏 morph 与弹层退场仍只有静态证据**，属 delta 复核建议项（不阻断 L2 结论）。

### 6.5 宽屏 / 折叠态 / 搜索浮层互相遮挡 + start 预留（P1-1 已修 + 连带项 P1-4）

**互相遮挡的结论（与 R3 一致，源码可证）**：

| 关系 | 现状 | 判定 |
|---|---|---|
| 搜索浮层 vs 折叠胶囊 | 搜索条 `searchTopPadding = LocalSearchTopInset`（`GlacierShell.kt:160-167` → `GlassSearchOverlay` 的 `searchTopPadding`），折叠时下移 `(CollapsedTop+CollapsedHeight+4dp)`；结果列表也吃同一值（`AppSearchResults.kt:62,92`） | 不遮挡，与官方 `AppContent.kt:239-246` 一致 |
| 搜索浮层 vs 展开侧栏 | 页面整体被 `railStartInset` 右移（`GlacierShell.kt:256-265`），搜索浮层在页面内 → 横向自动让开面板 | 不遮挡 |
| 搜索浮层 vs Shell 底栏 | 底栏是 Shell 兄弟、画在页面（含搜索浮层）之上；结果列表底部加 `LocalShellBottomBarHeight`（`AppSearchResults.kt:93`） | 底栏压住搜索浮层底部是**已知架构取舍**（R3 已记录）；列表已让位，不压行 |
| 折叠胶囊 vs 页面标题 | 标题 `LocalTopBarTitleAlpha` 跟随 `progress`（`GlacierShell.kt:169`），`PageFrame` 与页面级 titleAlpha 相乘（`PageFrame.kt:194-198`，官方 `PageUtils.kt:264` 同义） | 不遮挡；折叠态标题不可见是官方语义（§6.7） |

**P1-1（宽屏 start 双算）— 已修并进入最终 APK（待 delta 复核）**

- **原缺陷**：`railInset`（`GlacierShell.kt:151-158`）= `systemStartInset + (Margin + ExpandedWidth) * progress`，同时 `pageContentPadding` 又 `start = max(horizontal, displayCutout 的 start)`、顶栏又默认加 `displayCutout` 横向内边距 → **同一个 cutout 被算两次**。verifier 在 23:35 APK 上实测横屏折叠态：内容卡片左缘 **288px**（Apps 256px）、右缘 2368px（右边距仅 32px≈12dp），左右明显不对称；量级≈刘海 128px inset 被应用两次（`docs/verification/miuix-os4-r4/README.md` §9 的 P1 条目、截图 `44`/`45`）。
- **修复（final APK，lead 说明 + T2 源码核对）**：新增 `LocalPageStartReserved`（`GlassEnvironment.kt:26-34`，"宿主已让出 start 侧安全区"），Shell 宽屏时 `provides wide`（`GlacierShell.kt:223`），`pageContentPadding` 在 reserved 时 `start = horizontal`（`PageFrame.kt:421-429`），不再叠加 start 侧 cutout。语义对齐官方 `PageUtils.kt:99-103` 的 `startAlreadyReserved`（true 时 `safeInsets` 只保留 End + Vertical）。
- **delta 复核口径（待 verifier 在 23:58 安装的 APK 上执行）**：横屏折叠态内容左缘应回到 `128px(cutout) + 12dp×2.625≈31.5px ≈ 160px` 量级；展开态左缘应 ≈ `1888−688` 同口径一致；左右边距应基本对称（右缘 2368 保持不变）。
  ```powershell
  # 复核 P1-1（先取 uiautomator bounds，再点坐标，不要靠截图猜）
  adb shell settings put system accelerometer_rotation 0
  adb shell settings put system user_rotation 1
  adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml
  # 期望：折叠态内容行左缘 ≈160px（原 288/256），右缘 2368 不变
  ```

**P1-4（P1-1 的连带项，仍未改）— 顶栏与搜索仍各自让一次 cutout**

- **事实（T2 源码核对）**：
  - `PageTopBar` 调用 `GlassTopAppBar`（`PageFrame.kt:291-309`）与 `TopAppBar`（`:318-330`）时**都没有传 `defaultWindowInsetsPadding`**，默认 true；而该参数就是"apply horizontal display-cutout and navigation-bar insets"（`GlassTopAppBar.kt:256,290`；实现在 `miuix-ui/.../basic/TopAppBar.kt:880-890`，其中 `systemBars.only(Top)` 的顶部内边距是**无条件**加的，`displayCutout/navigationBars` 横向内边距只在 true 时加）。
  - 搜索浮层的 `horizontalInsetsModifier`（`HomeScreen.kt:336-338`、`AppsScreen.kt:143-145`）与搜索结果列表 `AppSearchResults.kt:114-115` 也各自 `windowInsetsPadding(displayCutout.only(Horizontal))`。搜索浮层同样在 pane 内（`PageFrame.kt:272`）。
- **后果**：pane 已被 `railStartInset` 右移了 `systemStartInset`，而顶栏标题/页签与搜索条/结果又各自再加一次 cutout → 宽屏 + 刘海设备上，**它们比正文内容多右移一个 cutout（模拟器 128px≈49dp）**。修复前正文也双算所以"看起来对齐"；P1-1 修好后正文回到官方口径，这层错位会暴露出来。窄屏（`startReserved=false`）不受影响。
- **建议补丁（lead 决定，本审计不改进代码）**：
  1. `PageFrame.kt` 的 `PageTopBar` 两个分支各加一行：
     ```kotlin
     // 玻璃档
     GlassTopAppBar(
         ...,
         defaultWindowInsetsPadding = !LocalPageStartReserved.current, // 顶部状态栏内边距不受影响（TopAppBar.kt:890 无条件）
     )
     // 非玻璃档
     TopAppBar(
         ...,
         defaultWindowInsetsPadding = !LocalPageStartReserved.current,
     )
     ```
  2. 搜索侧引入同一语义（可放 `PageFrame.kt` 附近的新 helper，页面只改 import）：
     ```kotlin
     /** 宽屏 pane 已由宿主让出 start 侧系统 inset 时，不再重复加 cutout 横向内边距。 */
     @Composable
     fun Modifier.pageHorizontalCutoutInsets(): Modifier =
         if (LocalPageStartReserved.current) Modifier
         else Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
     ```
     然后替换 `HomeScreen.kt:336-338`、`HomeScreen.kt:575`、`AppsScreen.kt:143-145`、`AppSearchResults.kt:114-115`。归 **shell-nav（PageFrame 模板）+ home-p0/apps-micro（替换调用点）**。
  3. 官方依据：官方顶栏用 `defaultWindowInsetsPadding=false` + `appPageTopBarInsets()`（`PageUtils.kt:127-129,243,257,266`），搜索用 `appPageSearchInsets()`（`PageUtils.kt:131-141`），两者都读**同一份** `contentInsets`（`safeInsets.left + overlayStart`），所以官方顶栏/搜索/正文三者对齐。

### 6.6 补充：宽屏断点实现与官方不一致（P2-4）

`isWideLayout` / `isExtraWideLayout`（`GlassEnvironment.kt:64-77`）用 `LocalConfiguration.screenWidthDp/screenHeightDp`，官方 `AdaptiveUtils.kt:13-23`（`shouldShowSplitPane`）/`:35-42`（`shouldExpandNavigationRail`）用 `LocalWindowInfo.current.containerSize`（**窗口**尺寸，而非屏幕尺寸）。断点数值一致，但：

- 分屏/自由窗口/折叠屏半开时，"屏幕宽 900dp + 窗口宽 600dp" 会被判成 wide → 显示玻璃侧栏，而窗口里根本没有空间；
- 横屏手机 `height/width<1.2` 判定也用屏幕值，窗口化时同样失真。

**归 shell-nav**（`GlassEnvironment.kt` 在 shell-nav 写作范围内）。建议直接改成官方 `containerSize` 语义，并在模拟器用 `adb shell wm size`/分屏复测（属 L2，需构建后）。

### 6.7 宽屏折叠态"页面标题整体不可见"是否官方语义 — **官方语义，不是 over-apply**

verifier 记录（`docs/verification/miuix-os4-r4/README.md` §9 P2 观察项、截图 `40`/`44`/`45`）：横屏折叠态下 Home「Glacier / 默认·4 个应用」、Apps「应用 / 8 个应用」、Settings「设置 / Android 16」的标题都不可见，只剩顶部导航胶囊；展开侧栏后才出现。

**结论：这是 MIUIX 官方 example 的既定语义，Glacier 没有 over-apply。**

依据链：

1. **官方同样按 progress 把标题淡到 0**：`example/AppContent.kt:217-223`
   ```kotlin
   val topAppBarTitleAlpha: () -> Float = remember(useGlassSidebar, sidebarState) {
       if (useGlassSidebar) { { sidebarState.progress } } else { { 1f } }
   }
   ```
   折叠态 `progress = 0` → chrome 淡出系数 0。
2. **官方把该系数乘进每个页面的顶栏标题**：`example/utils/PageUtils.kt:254-266`，`titleAlpha = { titleAlpha() * chromeTitleAlpha() }`（Glacier `PageFrame.kt:194-198` 逐字同义）。
3. **淡出的动因是遮挡**：折叠胶囊由 `GlassNavigationRail` 顶部居中渲染（`CollapsedTop`/`CollapsedHeight`），位置正好覆盖顶栏标题区；标题不淡出就会被胶囊压住。官方 example 在 840–1200dp 就是这个形态。
4. **R3 起 Glacier 一直是同一实现**：`GlacierShell.kt:169` 的 `titleAlpha = { if (wide) railProgress() else 1f }`，R3 `23-landscape-collapsed.png` 里同样没有标题；R4 只是把页面级 titleAlpha 与它相乘（`PageFrame.kt:194-198`），系数逻辑未变。
5. **不是"标题被错误隐藏"的旁证**：Actions（搜索/排序/筛选）不参与 `titleAlpha`（`GlassTopAppBar` 的 `titleAlpha` 只作用于标题容器），折叠态顶栏右侧动作仍可见；展开态 `progress→1` 标题恢复。R4 `40`–`45` 五张横屏截图与此一致。

**唯一可改进的是表述**：`PageFrame.kt` 的注释"宽屏时主页面也会自动折叠成紧凑标题"容易被读成"仍显示紧凑标题"，verifier 的"与注释不一致"观察即来自此。建议把注释改成"宽屏折叠态由 Shell 的侧栏胶囊占据标题位，标题 alpha 随 rail progress 归 0；展开后恢复"，**代码无需改**。用户侧若希望折叠态仍显示标题，需要产品决策（放弃官方行为），本审计不建议在 R4 动。

---

## 7. L1 / L2 / L3 结论

### 7.1 L1：**通过**（T2 起仅剩 1 条记录性偏差 + 1 条连带项）

| 检查项 | 结论 | 证据 |
|---|---|---|
| 全部消费点使用官方 Glass 组件 | 通过 | §4 表 |
| backdrop 传递正确（玻璃档/非玻璃档） | 通过 | §5.1；所有 `backdrop=null` 路径都有官方回退 |
| 无父/子自采样 | 通过 | §5.3 逐条；`LocalPageBackdrop` 陷阱已删除；R4 52 张截图 + logcat **0 FATAL/ANR** |
| 采样源有完整像素（先垫 surface） | 通过 | R `GlacierShell.kt:172-175`；P `GlassEnvironment.kt:85-88` |
| 弹层宿主在录制层之外 | 通过 | `Scaffold.kt:199-204,379`；`MiuixPopupUtils.kt:544-583` |
| 采样语义 | **通过**（ApiActivity 已修；弹层 bars 为记录性偏差 P1-3） | §6.1、§6.2 |
| 几何（宽屏 start 预留） | **通过（P1-1 已修，delta 复核待做）** | §6.5；`LocalPageStartReserved`（`GlassEnvironment.kt:26-34`）+ `PageFrame.kt:421-429` |
| 几何（连带项） | **待改 → P1-4**（顶栏/搜索仍各加一次 cutout） | §6.5 连带项 |
| 死 API/地雷 | **已清零**（`LocalPageBackdrop` / `LocalRailInset` / `LocalGlassRequested` 已删；`HomeScreen` 未使用 local 已删） | §8.1 |

### 7.2 L2：**R4 新构建已实测（模拟器，`docs/verification/miuix-os4-r4/README.md`）**

- **证据**：52 张截图（12 组场景）+ 转场 `screenrecord` 30fps 拆帧（`23`–`26`、`52`）+ 整轮 `logcat -b crash` **0 行** / 主 logcat `FATAL EXCEPTION|ANR in|am_anr|E AndroidRuntime` **0 命中**（README §12）。
- **APK 口径**：52 张截图 = **23:35 构建版本**（含 R4 全部 Glass 相关代码改动：`clipToBounds`、`NavDisplayEffects`、`pageScrollModifiers`/`VerticalScrollBar`、`titleAlpha` 合并、`LocalPageStartReserved` 前的宽屏让位、ApiActivity 不透明 `containerColor`）。最终 APK = **23:44 构建 / 23:58 安装**，在其上只追加了 P1-1（start 预留）与 P1-2（ApiActivity 主题）两项修复，**这两项需要 delta 复核**（§8.1）。
- **与 R3 的差异**：R3 的 L2 结论（2026-11-19 21:5x 构建）不能继承到 R4；R4 已在新构建上重拍并覆盖：标签栏四态、搜索 Home/Apps、转场拆帧、滚动条、操作单（含 2.0x）、大字体 1.3x/2.0x、横屏两态、深色四页、ApiActivity。
- **L2 仍未覆盖**：侧栏 300ms morph 的逐帧过程、弹层退场动画的逐帧过程（README 只给了静态帧/交互结果）；不阻断 L2 结论，列为 delta 建议项。
- **L3 不受影响**：模拟器 Software GPU，L2 通过**不等于** L3 通过（§7.3）。

### 7.3 L3：**未验证，不得标通过**

**前置条件（全部满足才可开 L3 验收）**：

1. **真机 GPU**：`ro.hardware.egl != emulation`（最好 HyperOS/OS4 真机），且 `isRuntimeShaderSupported() == true`。
2. **`Platform.isEmulator == false`**：否则顶栏仍走 band 渐变（`PageFrame.kt:298`），`progressiveBlur`/内部 `progressiveBackdrop` 不会启用（`GlassTopAppBar.kt:403-411,463-487`）。
3. **场景矩阵**：浅/深色 × 有内容滚动/空页 × 手机窄屏/横屏折叠胶囊/展开面板 × 底栏在上/搜索在开 × 二级页转场 × ApiActivity。
4. **可判读的证据**：截图 + 高帧率录屏；顶栏「内容从下方滚过时」的 blur 梯度、边缘光（EdgeStroke）在深色下的亮度、折射偏移、底栏胶囊边缘。
5. 不接受"模拟器截图里看起来像玻璃"作为 L3 证据。

---

## 8. 缺陷清单（P0/P1/P2）+ R4 修复后状态

> 说明：**P0 = 崩溃/自采样/层级错误**（当前无）。**R4 修复后状态**四类：**已修（最终 APK）** / **待 delta 复核**（已修但需在 23:58 安装的 APK 上量一次）/ **保留（记录性偏差）** / **待改（本轮未做）**。

### 8.0 状态总览

| 编号 | 原问题 | R4 修复后状态 |
|---|---|---|
| P1-1 | 宽屏 start 双算 | **已修（最终 APK）**，待 delta 复核（左缘 ≈160px） |
| P1-2 | ApiActivity 半透明主题 | **已修（最终 APK）**，待 delta 复核（窗口不透底/无首帧闪） |
| P1-3 | 弹层 backdrop 不含 Shell bars | **保留（记录性偏差）**：与官方 IconPage 一致，L3-only |
| P1-4 | （新）顶栏/搜索仍各加一次 cutout | **待改**：P1-1 的连带项，建议本轮/下一轮补 |
| P2-1 | `LocalPageBackdrop` 自采样陷阱 | **已修（已删除）** |
| P2-2 | 死 CompositionLocal（`LocalRailInset`/`LocalGlassRequested`） | **已修（已删除）** |
| P2-3 | ApiActivity `GlassDialog(backdrop=...)` 未 `takeIf` | **待改**（等效安全，防回归；补丁见 §8.1） |
| P2-4 | 宽屏断点用屏幕尺寸 | **保留（待改）**（分屏/折叠屏；非 Glass 采样层） |
| P2-5 | 宽屏页签左缘疑似被裁 | **已复测消失**（R4 `07`–`10`） |
| P2-6 | `HomeScreen` 未使用 `LocalRootBackdrop` | **已修（已删除）** |
| P2-7 | 模拟器 progressiveBlur 关闭 / 真机判定 | **保留**（L3 前置条件说明） |
| P2-8 | `GlassNavigationBar(visible=false)` 不释放布局高度 | **保留**（低优先记录） |

### P0（无）

| # | 问题 | 结论 |
|---|---|---|
| — | 自采样 / `prepareTreeImpl` 递归 / 采样源透明 | **未发现**。全部消费点经 §5.3 核对安全；R/P 均先垫 surface；R4 52 张截图 + logcat 0 FATAL/ANR 佐证。 |

### P1

| # | 问题 | 证据 file:line | 影响 | R4 修复后状态 | 责任/建议 |
|---|---|---|---|---|---|
| **P1-1** | 宽屏 `railInset` 与页面/顶栏的 start 安全区**双算** | 原：`GlacierShell.kt:151-158,264` + `PageFrame.kt:408-429`；修后：`GlassEnvironment.kt:26-34`、`GlacierShell.kt:223`、`PageFrame.kt:421-429`；官方 `PageUtils.kt:99-103` | 横屏/刘海设备所有页面可见（verifier 实测左缘 288/256px，右缘 2368px，不对称） | **已修（最终 APK）**；delta 复核 = 折叠态内容左缘是否回到 ≈160px、左右是否对称 | shell-nav（已做）；verifier delta |
| **P1-2** | ApiActivity 半透明主题 + 92% alpha 导致"可见像素 ≠ 录制像素" | `ApiActivity.kt:356-359,367`；`values/themes.xml:16-23`、`values-night/themes.xml:16-23`；`AndroidManifest.xml` ApiActivity `@style/Theme.Glacier.Api` | 玻璃采样源与屏幕观感不一致；首帧/窗口动画透出宿主 | **已修（最终 APK）**；delta 复核 = `50` 场景重拍（不透明页面 + 弹层背景完整、不透底） | settings-micro（已做）；verifier delta |
| **P1-3**（记录性） | 弹层 backdrop 不含 Shell 底栏/侧栏像素（与 KDoc "page and bars" 偏差） | `GlassDialog.kt:20`、`GlassBottomSheet.kt:20`；`GlacierShell.kt:232-248,316-327`；官方 `GlassPage.kt:264-271,405-408` vs `IconPage.kt:448-450` | 仅 L3：底部弹层最下缘采不到底栏胶囊 | **保留（记录性偏差）**：与官方 IconPage 实践一致，需 L3 才有判据 | lead 决策（建议不改结构） |
| **P1-4**（新） | **P1-1 的连带项**：P1-1 只修了正文 padding；顶栏（`defaultWindowInsetsPadding` 默认 true）与搜索浮层/结果列表仍各自再加一次 `displayCutout`，而 pane 已整体右移 → 宽屏+刘海下，顶栏标题/页签、搜索条/结果比正文多右移一个 cutout | `PageFrame.kt:291-309`（玻璃档）、`:318-330`（非玻璃档，均未传 `defaultWindowInsetsPadding`）；`miuix-ui/.../TopAppBar.kt:880-890`（横向内边距仅在 true 时加，顶部状态栏内边距无条件）；`HomeScreen.kt:336-338,575`；`AppsScreen.kt:143-145`；`AppSearchResults.kt:114-115`；官方 `PageUtils.kt:127-129`（`appPageTopBarInsets`）、`:131-141`（`appPageSearchInsets`） | 宽屏+刘海设备：顶栏/搜索与正文横向错位（预测 ~128px/49dp）；窄屏不受影响 | **待改**（补丁见 §6.5 连带项） | shell-nav（PageFrame helper）+ home-p0 / apps-micro（替换调用点）；需先 delta 量一次确认可见 |

### P2

| # | 问题 | 证据 file:line | R4 修复后状态 | 责任/建议 |
|---|---|---|---|---|
| **P2-1** | **`LocalPageBackdrop` 是自采样陷阱** | 原：`GlassEnvironment.kt:29-35`、`PageFrame.kt:231-236` | **已修**：local 与 provider 均已删除；`PageFrame.kt:229-231` 留注释说明"provider 在录制层子树内，使用即自采样" | shell-nav（已做） |
| **P2-2** | 死 CompositionLocal：`LocalRailInset`、`LocalGlassRequested` 无消费者 | 原：`GlassEnvironment.kt:13,38`；`GlacierShell.kt:219,222` | **已修**：两个 local 定义与 Shell 的两个 provide 均已删除 | shell-nav（已做） |
| **P2-3** | ApiActivity `GlassDialog(backdrop = LocalRootBackdrop.current)` 未 `takeIf { LocalGlassActive.current }` | `ApiActivity.kt:121-124`（T2 仍是此写法）；对比 `HomeScreen.kt:771-774`、`SettingsScreen.kt:72` 都有 `takeIf` | **待改（等效安全，防回归）**：当前 `ApiRoot` 在 `!glassActive` 时 provide null（`ApiActivity.kt:361-363`），所以行为等价；但若以后 ApiRoot 改成"始终 provide"或换宿主，这里会在玻璃关档错误启用玻璃。补丁见 §8.1 | settings-micro（一行 + 无新 import，`LocalGlassActive` 已 import 于 `:36`） |
| **P2-4** | 宽屏断点用 `LocalConfiguration.screenWidthDp/HeightDp`，官方用 `LocalWindowInfo.containerSize` | `GlassEnvironment.kt:64-77`；官方 `AdaptiveUtils.kt:13-23,35-42` | **保留（待改）**：分屏/自由窗口/折叠屏误判；不影响当前模拟器与手机竖横屏验收 | shell-nav |
| **P2-5** | 宽屏页签左缘疑似被裁 | R3 `23/24`；R4 `07`–`10`（`HomeScreen.kt:636-673` 现为 `GlassSegmentedTabRow`） | **已复测消失**：R4 四态标签栏胶囊圆角/对比度正常（README §2） | home-p0（已完成） |
| **P2-6** | `HomeScreen` 未使用的 `val backdrop = LocalRootBackdrop.current`（P2-1 诱饵） | 原：`HomeScreen.kt:187` | **已修（已删除）** | home-p0（已做） |
| **P2-7** | 模拟器上顶栏 `progressiveBlur` 主动关闭；`Platform.isEmulator` 依赖 Build 指纹 | `PageFrame.kt:298`；`Platform.kt:62-69` | **保留**：是 L3 前置条件，不是缺陷；真机验收时建议 logcat 打一条"progressiveBlur 实际值"确认 | shell-nav（真机验收项） |
| **P2-8** | `GlassNavigationBar(visible=false)`（二级页）只缩放/淡出，不释放布局高度 | `GlacierShell.kt:216,226,232-248`；`GlassNavigationBar.kt:194-208,257-268` | **保留（低优先）**：页面因 `LocalShellBottomBarHeight=0` 不受影响；仅根 bottomBar 槽占位 | shell-nav |

### 8.1 最终 APK 修复证据与 delta 复核清单

**已修证据（源码 T2 复核 + R4 实测）**

| 项 | 源码证据（T2 已核对） | 运行证据 |
|---|---|---|
| P1-1 start 预留 | `GlassEnvironment.kt:26-34`（`LocalPageStartReserved`）；`GlacierShell.kt:223` 提供 wide；`PageFrame.kt:421-429` reserved 时 `start = horizontal`，对齐官方 `PageUtils.kt:99-103` | 23:35 APK 实测到缺陷（`44`/`45` 左缘 288/256px）；最终 APK 待量左缘 ≈160px |
| P1-2 ApiActivity 主题 | `values/themes.xml:16-23`、`values-night/themes.xml:16-23`（`windowIsTranslucent=false`、`windowBackground=@color/glacier_window_background`）；`AndroidManifest.xml` 改用 `Theme.Glacier.Api`；`ApiActivity.kt:367` `containerColor=surface` | R4 `50`/`51` 为旧半透明 APK；最终 APK 需重拍 |
| P2-1 / P2-2 / P2-6 | `GlassEnvironment.kt` 现仅保留 6 个 local（无 `LocalPageBackdrop`/`LocalRailInset`/`LocalGlassRequested`）；`PageFrame.kt:229-231` 注释；`HomeScreen.kt` 无未使用 local | R4 全轮 logcat 0 FATAL/ANR（自采样若发生必有崩溃） |
| P2-5 | `HomeScreen.kt:636-673` 用 `GlassSegmentedTabRow` | R4 `07`–`10` 四态正常 |

**delta 复核清单（verifier 在 23:58 安装的 APK 上执行；不通过则回退对应项）**

1. **P1-1**：横屏折叠态 `uiautomator dump` 量 Apps/Settings 行左缘，期望 ≈160px（原 256/288px）；右缘保持 2368px；展开态按同一口径一致。顺带量顶栏标题/页签 bounds（验证 P1-4）。
2. **P1-2**：重跑 `SHOW_APP_INFO` + `FREEZE` 两个 ApiActivity 场景；要求窗口背景不透明、弹层玻璃背景完整、进出窗口无透底/白闪。
3. **P1-4（可选，若本轮不修）**：横屏展开态分别 dump 正文行左缘、顶栏标题/页签左缘、搜索条左缘；若三者差 ≈128px（一个 cutout），则确认连带项存在，按 §6.5 补丁修。
4. **P2-3（建议顺手）**：见下方补丁。

**P2-3 建议补丁（本审计不代改，lead 决定）**

当前代码（`ApiActivity.kt:119-126`）：

```kotlin
GlassDialog(
    onDismissRequest = { errorMessage = null },
    show = errorMessage != null,
    backdrop = LocalRootBackdrop.current,
    title = stringResource(R.string.app_name),
    summary = lastError,
    // 官方参数：等退出动画结束再 finish()，否则窗口立即销毁、动画被砍掉。
    onDismissFinished = { finish() },
) {
```

建议改为（一行）：

```kotlin
GlassDialog(
    onDismissRequest = { errorMessage = null },
    show = errorMessage != null,
    backdrop = LocalRootBackdrop.current.takeIf { LocalGlassActive.current },   // ← 与 Home/Settings 调用点一致
    title = stringResource(R.string.app_name),
    summary = lastError,
    onDismissFinished = { finish() },
) {
```

- `LocalGlassActive` 已在 `ApiActivity.kt:36` import，无需新增 import；`ApiRoot` 的 `glassActive` 与 `LocalGlassActive` 同源（`ApiActivity.kt:354,362`），**行为当前完全等价**，属防回归/一致性，不是现网缺陷。
- 若 lead 想连 `glassActive` 判断也一并统一，可把 `ApiActivity.kt:354` 的 `app.preferences.glassEnabled && isRuntimeShaderSupported()` 保持原样（它是本窗口的唯一决策点，与 `glass-contract.md:94` 一致），不要新增第二个判断。

---

## 9. R3 完成度复核表（`docs/audit/miuix-os4-audit.md` §17 逐项重判）

> 分类：**【仅源码证据】/【有截图证据】/【已实测可复现】**。**"证据不足"= 声称"已整改/已实测"但当前只有源码或单帧截图。**
> R3 原文位置：`miuix-os4-audit.md:622-682`。
> **编号说明：本节 `P0-x / P1-x / P2-x` 是 R3 审计的编号，与 §8 的 R4 缺陷编号无关**（R4 的 P1-4、P2-3 等见 §8）。

### 9.1 P0 结果（§17.1）

| 编号 | R3 声称 | R3 证据 | 本轮重判 | 证据不足？ |
|---|---|---|---|---|
| P0-1 底栏 24dp / 宽度 | 已整改 / **已实测** | uiautomator bounds + `01-home-light.png`（README §四） | **【已实测可复现】**（有数值 + 命令，见 README §八）；但**本轮未复跑** | 否（历史实测记录） |
| P0-2 让位/FAB 魔法值 | 已整改 / **已实测** | 只有算账文字（"FAB 位于胶囊上方 12dp"）；无 FAB bounds、无设计值 dump | **【仅源码证据】+ 单帧截图**；"12dp"未实测 | **是** |
| P0-3 宽屏（折叠胶囊/展开/搜索让位/二级页高亮） | 已整改 / **已实测（模拟器横屏）** | `23/24/25` 三张静态截图 | **【有截图证据】**（两态静态）；但"点击胶囊展开"无步骤记录；且 23/24 暴露出 **P1-1 双算**（R3 将其当作正确让位） | **是**（"已实测"过强；且有未识别缺陷） |
| P0-4 搜索浮层条件组合 → 退场动画 | 已整改 / **已实测** | README §五 步骤 + `07/08/09` | **【已实测可复现】交互路径**（步骤明确）；**退场动画过程只有源码证据**（`GlassSearchOverlay.kt:229-239` + 常驻组合） | **部分**（动画过程） |
| P0-5 Apps/Home 空态吃 padding | 已整改 | 无空态截图 | **【仅源码证据】** | **是** |

### 9.2 P1 结果（§17.2）

| 编号 | R3 声称 | 本轮重判 | 证据不足？ |
|---|---|---|---|
| P1-1 弹层退出动画（常驻组合 + show） | 已整改 | **【仅源码证据】**：所有调用点确为 `show` 驱动（`AppActions.kt:202-212` 等）；静态截图不能证明退场动画 | **是** |
| P1-2 页签 padding/高度 | 已整改 | **【有截图证据】**（01/02/23/24）；无尺寸测量；且本轮 home-p0 又换了页签组件 → 需重判（见 §9.4） | **是**（数值未测） |
| P1-3 顶栏动作 28dp / 间距 8dp | 已整改 | **【有截图证据】**（01/12）；尺寸未 dump | **是**（数值未测） |
| P1-4 分节标题/页面留白/卡片间距 12dp | 已整改 | **【仅源码证据】**（PageMetrics 收敛） | **是** |
| P1-5 搜索结果行 Card+BasicComponent | 已整改 | **【有截图证据】**（08/32） | 否 |
| P1-6 弹层行结构 / 可滚动 | 部分整改 | 与 R3 自述一致（操作面板仍 `Column`）；本轮 apps-micro 正在改 | — |
| P1-7 Dialog 按钮间距/空输入反馈 | 已整改 | **【有截图证据】**（11：保存禁用 + 并排按钮） | 否 |
| P1-8 标签删除入口 | 已整改 | **【有截图证据】**（10：每行编辑/删除） | 否 |
| P1-9 页面滚动修饰 | 未整改（保留） | R4 由 shell-nav 接手（`PageFrame.kt` 新增 `pageScrollModifiers`/`VerticalScrollBar`）；**新实现 L2 未验证** | 待验证 |
| P1-10 分页 spring/预组合页数 | 已整改 | **【仅源码证据】+ 单帧 `25`**；动画曲线无法从截图判断 | **是** |
| P1-11 二级页转场特效 | 未整改 | R4 shell-nav 已新增 `NavDisplayEffects`（`GlacierShell.kt:177-200,265-266`）；**未构建未验证** | 待验证 |
| P1-12 About 品牌区尺寸 | 已整改 / 已截图 | **【有截图证据】**（16） | 否 |
| P1-13 Toast 而非 Snackbar | 保留（有理由） | 理由仍成立（跨窗口/无界面调用）；**【仅源码证据】** | 否（属保留项） |
| P1-14 图标选中环尺寸 | 保留（规范要求） | **【有截图证据】**（12）；无尺寸测量 | 否（保留项） |
| P1-15 Apps 多选 | 未整改（范围外） | 本轮 apps-micro 评估中 | — |

### 9.3 P2 关键项（§17.3）与 17.4/17.5

| 项 | R3 声称 | 本轮重判 |
|---|---|---|
| 图标（P2-10 自适应图标/squircle） | 已整改 | **【有截图证据】**（03/04 的第三方图标占比/圆角）；真机 HyperOS 遮罩未验证（R3 §17.5 自认） |
| 硬编码收敛（PageMetrics） | 已整改 | **【仅源码证据】+ grep 可复现**（命令在 R3 §17.3）；本轮 shell/apps 又在改页面，需重跑 grep |
| 文案资源化 | 已整改 | **【仅源码证据】**；R4 copy agent 正在二次精简 |
| About 版本去重（P2-11） | 已整改 | **【有截图证据】**（16） |
| 17.4 保留自定义实现 | 保留 6 项 | 见 §11（本审计判断：**合理**，但 `isWideLayout` 的取数方式需改成窗口尺寸，见 P2-4） |
| 17.5 仍未完成/未验证 | 如实列出 | 与 R3 一致；本轮新增/变化：L3 仍未验证；P1-9/P1-11 由 R4 接手（未验证）；About 滚动动画由 about-anim 本轮实现（未验证） |

### 9.4 证据不足项汇总（R4 审计输入）+ R4 实测覆盖情况

**R3 声称"已整改/已实测"但证据不足的项，及 R4 实测是否已覆盖：**

| R3 证据不足项 | R4 `README.md` 覆盖情况 |
|---|---|
| **P0-2**（FAB 位置 / 让位数值） | **仍未直接量 FAB bounds**；但 R4 `32`/`33` 操作单 + 底栏截图可旁证；建议 delta 补 `uiautomator dump` FAB bounds（低优先） |
| **P0-5**（空态 padding） | **仍未覆盖**（R4 数据非空：Home 4 应用 / Apps 8 应用）；需清数据后补空态截图 |
| **P0-3**（宽屏） | **已覆盖**（`40`–`45` 两态 + 折叠/展开操作步骤 + 左右 bounds）；并实测发现 start 双算（P1-1），最终 APK 待 delta |
| **P0-4 / P1-1**（退出/退场动画） | **部分覆盖**：搜索开/关交互+cancel 路径 `17`–`22`；转场拆帧 `23`–`26`；**弹层退场动画仍无拆帧** |
| **P1-4 / P1-10**（间距 token、分页 spring） | **部分覆盖**：间距可从 `01`–`10` 与 `47`–`49` 旁证；分页 spring 仍无逐帧；R4 `25` 转回竖屏吸附 |
| **P1-2**（页签） | **已覆盖且换代**：R4 `07`–`10` 四态（`GlassSegmentedTabRow` / `TabRow`），原裁切疑点未复现 |
| **P1-9 / P1-11**（R4 新接手项） | **已覆盖**：滚动条 `29`–`31`；转场 `23`–`26`（拆帧） |
| **大字体 1.3x/2.0x** | **已覆盖**：Home/Apps/Settings + 操作单（`33`–`39`） |
| **深色** | **已覆盖**：Home/Apps/Settings/About（`46`–`49`） |

> 备注：R3 的 **L1 结论仍然成立**（采样层级 §5.3 未变，且 T2 已拆除 `LocalPageBackdrop` 陷阱）；R3 的 **L2"通过"不能自动继承**（R4 已改 Shell/PageFrame 绘制路径），但 R4 已在新构建上重做并覆盖主要场景（§7.2）。R3 的 **L3 未验证**结论正确，本轮维持。

---

## 10. 非 Glass 官方组件保留清单及理由（复核）

> 判据：**是否为官方组件 / 是否有官方等价物 / 是否在 Glass 层级上占位**。原则：不为"全玻璃化"牺牲官方层级与可读性。

| 保留项 | 位置 | R3 理由 | 本审计判断 |
|---|---|---|---|
| 设置行 / 列表行 | `BasicComponent`、`SwitchPreference`、`ArrowPreference` 等 | 设置页是信息密度单位，整行玻璃会让文字对比不稳；官方 example 设置页也是普通 Card + Preference | **合理**。补充：官方 `SettingsPage.kt:100,122,166…` 确认设置页只有下拉弹层用 Glass，行本体不是玻璃 → 与官方一致 |
| 应用网格单元 | `LazyVerticalGrid` + `HomeAppItem`（无卡片底色） | MIUIX 是组件库，不含启动器网格 | **合理**。官方 `IconPage.kt` 才有同类网格；Glacier 自建启动器网格属业务必要，且只使用官方 squircle/图标渲染 |
| 卡片 | 官方 `Card`（`surfaceContainer`） | 官方 Card 默认即 OS4 卡片表面 | **合理**，不额外套玻璃 |
| 空状态 `EmptyState` | `PageFrame.kt:460`（T2） | MIUIX 无空状态组件；只用官方 `Text` 排版 | **合理**（R4 未覆盖空态场景，见 §9.4） |
| `PageMetrics` | `PageFrame.kt:79-100`（T2） | 官方无页面级 spacing token；仅 5 个常量，圆角取 `CardDefaults.CornerRadius` | **基本合理**。风险：常量与官方 `GlassTopAppBarDefaults.HorizontalPadding`(12dp) 等存在"两套数值源"，后续官方 token 变更时不会自动跟随；建议注释里保留出处（R3 已做） |
| 系统 Toast（`UiFeedback`） | `platform/UiFeedback.kt` | 跨窗口/无界面调用需要统一出口 | **合理**（官方 Snackbar 依赖 Compose 宿主，磁贴/服务/权限回调无法用） |
| 第三方图标取图管线 | `platform/AppIcons.kt` | 业务能力（去色/图标包/自适应合成），只改"如何得到位图" | **合理**；不涉及 Glass 层级 |
| `isWideLayout` / `isExtraWideLayout` | `GlassEnvironment.kt:64-77` | 复刻官方 `AdaptiveUtils` 断点语义 | **数值合理，取数方式需改**（屏幕尺寸 → 窗口尺寸，P2-4） |
| 非玻璃档顶栏/页签 | `TopAppBar` / `TabRow` | 与官方两档一致 | **合理**：玻璃关档用官方非玻璃组件，不是自绘替代 |
| 折叠胶囊/侧栏 | 官方 `GlassNavigationRail` | — | **合理**：没有自绘胶囊 |

**需要特别说明的"不要动"**：

- **不要把 R 改成跟着 `railStartInset` 走**（有人会想"侧栏背后要有内容"）。官方宽屏玻璃侧栏的面板区域在录制层里同样只有 surface（`AppContent.kt:335-353` + `overlayStart` 让位），这是设计而非缺陷；R 满屏才能保证底栏/弹层有完整像素（`GlacierShell.kt:257-260` 的注释正确）。
- **不要在页面正文里放任何采样 P 的玻璃组件**（历史崩溃点，`glass-contract.md:62-77`）。T2 已删除 `LocalPageBackdrop`（其 provider 位于 P 子树内，读它必自采样），也没有替代 local —— 需要 backdrop 的页面内玻璃组件应改为"由宿主显式传参"，与官方一致；不要再引入第二个 local。
- **不要给 `GlassSearchOverlay` 外层再套 `layerBackdrop`**：它自己录内容层，外层套会让排序/层级变复杂，且它已在 R 内。
- **宽屏新增任何"让位/inset"逻辑前先看 `LocalPageStartReserved`**：pane 已由宿主让出 start 侧系统 inset，页面内不要再叠加同一个 `displayCutout`（P1-4 就是没叠这一层导致的）。

---

## 11. 本轮未覆盖 / 下一轮前置条件

### 11.1 本轮未覆盖 / 已覆盖边界（禁止把"未验证"表述为"已通过"）

| 项 | 状态 | 原因 |
|---|---|---|
| L2 运行效果 | **R4 已实测（模拟器，52 截图 + 拆帧 + 0 FATAL/ANR）** | 见 §7.2；delta（P1-1/P1-2）仍需在 23:58 安装的 APK 上复核 |
| L3 光学 | **未验证** | 无真机 GPU；模拟器 `progressiveBlur` 被关 |
| 侧栏 300ms morph 过程 | 未验证 | R4 只有两态静态帧（`40`–`45`）；需 `screenrecord` 逐帧 |
| 弹层退场动画过程 | 未验证 | R4 交互路径通过，但无拆帧 |
| 二级页转场 + 玻璃层级 | **R4 已实测（拆帧）** | `23`–`26`：无白闪/黑帧、圆角裁剪、底栏玻璃正确采样 |
| 大字体 1.3x/2.0x × Glass | **R4 已实测（Home/Apps/Settings/操作单）** | `34`–`39`、`33` |
| RTL × Glass | 未验证 | 未切 RTL 实测；shell-nav 本轮加了 RTL 镜像（`BackNavigationAction`），需验证 |
| 折叠屏/分屏/600–839dp | 未验证 | 需窗口尺寸断点（P2-4）先修 |
| 三键导航底部安全区 | 未验证 | 模拟器手势导航 |
| 系统级圆角/刘海遮罩像素 | 未验证 | `screencap` 不含显示层遮罩（R4 README「无法验证」§2）；刘海 inset 的**布局行为**已可用 bounds 验证 |

### 11.2 L2 场景清单 — R4 已覆盖情况 + delta 待补

R4 已覆盖（`docs/verification/miuix-os4-r4/README.md` §1–§12，52 张截图）：

1. Home 长按/选择态（`01`–`06`、`52` 拆帧）。
2. 标签栏四态：浅/深 × Glass 开/关（`07`–`10`）。
3. About 滚动联动 6 阶段（`11`–`16`）。
4. 搜索 Home/Apps：开/结果/关（`17`–`22`）。
5. 页面转场 push/pop 拆帧（`23`–`26`）。
6. 二级页 About/Licenses + 滚动条（`27`–`31`）。
7. 操作单（含 2.0x）（`32`–`33`）。
8. 大字体 1.3x/2.0x 三页（`34`–`39`）。
9. 横屏两态（`40`–`45`）。
10. 深色四页（`46`–`49`）。
11. ApiActivity 两场景（`50`–`51`）。
12. 崩溃/ANR 汇总（README §12：0 FATAL / 0 ANR）。

**delta 待补（最终 APK 23:58 安装后）**：

1. **P1-1**：横屏折叠态内容左缘 ≈160px（原 288/256px），左右对称；展开态同口径。
2. **P1-2**：ApiActivity 重拍（不透明窗口、弹层背景完整、进出无透底/闪）。
3. **P1-4（可选）**：横屏展开态同时 dump 正文行 / 顶栏标题 / 搜索条左缘，确认是否相差一个 cutout。
4. **P2-3（可选）**：按 §8.1 补丁统一 `takeIf`。
5. 空态（P0-5 遗留）与 FAB bounds（P0-2 遗留）：低优先，可清数据后补。

### 11.3 L3 前置条件

见 §7.3（真机 GPU、`Platform.isEmulator==false`、场景矩阵、截图+高帧率录屏）。

---

## 附录 A：证据文件清单

| 证据 | 路径 | 用途 |
|---|---|---|
| R3 结构审计 | `docs/audit/miuix-os4-audit.md` §3/§9/§17 | 基线结构、R3 整改声称 |
| R3 Glass 审计 | `docs/audit/miuix-os4-glass-audit.md` | R3 的落点表与 L1/L2/L3 结论 |
| Glass 契约 | `docs/architecture/glass-contract.md` | 三条红线 |
| R3 验收 | `docs/verification/miuix-os4-r3/README.md` + 36 张截图 | L2 历史证据、复现命令 |
| R4 before 截图 | `docs/verification/miuix-os4-r4/before/01..05` | R3 同代 Glass 现状（抓取 23:14–23:18） |
| **R4 正式实测（本轮 L2 唯一来源）** | `docs/verification/miuix-os4-r4/README.md` + `01`–`52` 截图 | 12 组场景、转场拆帧、logcat 0 FATAL/ANR；被测包 = 23:35 构建（最终 APK 23:44 构建 delta 待补） |
| 官方对照 | `miuix/example/shared/.../AppContent.kt`、`GlassPage.kt`、`IconPage.kt`、`utils/PageUtils.kt`、`utils/AdaptiveUtils.kt` | 结构/断点/inset 依据 |
| 官方组件源码 | `miuix/miuix-glass/.../Glass{TopAppBar,TabRow,NavigationBar,NavigationRail,SearchOverlay,Dialog,BottomSheet,DropdownPreference,FloatingActionButton}.kt`、`miuix-blur/.../LayerBackdrop*.kt`、`miuix-ui/.../basic/Scaffold.kt`、`utils/MiuixPopupUtils.kt` | 采样/绘制顺序/回退依据 |

## 附录 B：关键源码引用速查（T2 行号）

| 事实 | 位置 |
|---|---|
| 根 Scaffold（透明、insets=0、popup 宿主） | `GlacierShell.kt:228-248` |
| R 录制层（drawRect(surface)+drawContent） | `GlacierShell.kt:172-175` |
| R 挂载点（content 内层 Box） | `GlacierShell.kt:251-254` |
| 宽屏 railInset / searchTop / titleAlpha | `GlacierShell.kt:145-169` |
| 宽屏 pane start 预留（P1-1 修复） | `GlassEnvironment.kt:26-34`；`GlacierShell.kt:223`；`PageFrame.kt:421-429` |
| 侧栏（R 的兄弟，showToggle） | `GlacierShell.kt:316-327` |
| 底栏（Scaffold.bottomBar → GlassNavigationBar） | `GlacierShell.kt:232-248, 341-390` |
| P 录制层 | `PageFrame.kt:186, 232-236`；`GlassEnvironment.kt:85-88` |
| 顶栏玻璃档 / 非玻璃档（P1-4 补丁点） | `PageFrame.kt:291-309` / `:310-330` |
| FAB 槽 | `PageFrame.kt:226-228` |
| overlay（搜索/弹层） | `PageFrame.kt:272` |
| 页面横向内边距（reserved 时不再叠加 cutout） | `PageFrame.kt:410-437` |
| railStartInset（layout 期读取） | `PageFrame.kt:367-385` |
| 搜索浮层内部录制层 | `GlassSearchOverlay.kt:267-268, 296-302`；消费点 `:312-327,349-365,464-504` |
| 弹层根宿主 + 绘制顺序 | `Scaffold.kt:88,102-117,199-204,348-380`；`MiuixPopupUtils.kt:544-583` |
| 官方弹层 KDoc "page and bars" | `GlassDialog.kt:20`；`GlassBottomSheet.kt:20` |
| ApiActivity 整窗层 + 不透明窗口（P1-2 修复） | `ApiActivity.kt:356-378`；主题 `values/themes.xml:16-23`、`values-night/themes.xml:16-23`；manifest `@style/Theme.Glacier.Api` |
| ApiActivity P2-3 未 `takeIf` 点 | `ApiActivity.kt:121-124` |
| 模拟器关 progressiveBlur | `PageFrame.kt:298`；`Platform.kt:62-69` |
| 宽屏断点（屏幕尺寸 vs 官方窗口尺寸） | `GlassEnvironment.kt:64-77`；官方 `AdaptiveUtils.kt:13-23,35-42` |
| 自采样陷阱（已拆除） | 原 `LocalPageBackdrop`（`GlassEnvironment.kt` 已无定义）；`PageFrame.kt:229-231` 注释 |

## 附录 C：与官方 example 的结构差异一览（供 lead 判断"要不要改"）

| 差异 | Glacier | 官方 | 处置建议 |
|---|---|---|---|
| 弹层采样源 | R（页面内容+顶栏+FAB，无 Shell bars） | `GlassPage`：整页含 bars；`IconPage`：页面内容层 | 保持现状（与 IconPage 一致；仅 L3 偏差，P1-3） |
| 宽屏让位实现 | `railStartInset` 平移整个 NavDisplay + `LocalPageStartReserved` 声明"start 已预留" | `startAlreadyReserved` + `contentInsets = safeInsets + overlayStart`（`PageUtils.kt:99-114`） | **已修（P1-1）**；但顶栏/搜索尚未用同一预留语义（P1-4 待改） |
| 顶栏 insets | 默认 `defaultWindowInsetsPadding=true`（未用 `startReserved`） | `false` + `appPageTopBarInsets()`（`PageUtils.kt:127-129`） | **P1-4**：宽屏+cutout 下会与正文错位一个 cutout；建议传 `!LocalPageStartReserved.current` |
| 搜索 insets | `windowInsetsPadding(displayCutout)` 独立加（`HomeScreen/AppsScreen/AppSearchResults`） | `appPageSearchInsets()`（`PageUtils.kt:131-141`，读同一 `contentInsets`） | **P1-4**：同上，建议抽 helper 统一 |
| 二级页转场 | R4 起 `NavDisplayEffects(Leading clip, dim 0.5)` | `AppContent.kt:256-305`（含 cross-activity 分支） | 已对齐基础项，**R4 拆帧已实测**（`23`–`26`） |
| 页面滚动修饰 | R4 起 `PageFrame.pageScrollModifiers` | `PageUtils.kt:168-183` | 已对齐，R4 已出滚动条/滚动截图（`29`–`31`） |
| 侧栏展开时 FAB | 仍由页面 Scaffold 放置 | 官方宽屏 FAB 由根 Scaffold 放（`AppContent.kt:604-610`） | 结构安全（FAB 采 P），不影响 Glass |

---

*本审计为只读审计：只修改了本文件；所有结论基于 §2 标注的快照代码与 `docs/verification/miuix-os4-r4/README.md` 的实测记录；未运行构建与 adb。**L2 = R4 已实测（模拟器）**，**L3 = 未验证**，以 §7 为准。*

**本次更新的边界**：只按 lead 提供的修复信息核对源码与证据并更新本文件；未修改任何源码/资源/其他文档；P2-3 只给建议补丁（§8.1），未落地；P1-4 为本次新识别的连带项，未要求本轮必修，但**如果最终 APK 只做了 P1-1 而未做 P1-4，横屏刘海设备上顶栏/搜索与正文会错位一个 cutout**，建议 delta 复核时一并量。
