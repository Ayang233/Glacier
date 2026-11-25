# R13 · 基线升到 main 最新 + 全项目自造轮子清查 + 长按锚点浮层接回

> 状态：**已实施并在 GPU 模拟器验证**
> 起因（用户）：① 按已知问题清单一次性修复；② 整项目扫描 UI / 界面 / 动画 / 尺寸 / 间距 / 边距的
> 自造轮子，全部换成 MIUIX OS4 / OS4 Glass 官方内容；③ 升级 MIUIX 最新基线；
> ④ 模拟器已接入宿主 GPU，可当"真机"用，问题修完直接在模拟器看。

---

## 1. 基线变更

| 项 | R12 | R13 |
|---|---|---|
| 官方 tag | `v0.9.5-beta04` | `v0.9.5-beta04`（tag 未变） |
| **实际 commit** | `3c560868`（tag） | **`542463a0`（main 最新，tag 之后 12 个提交）** |
| `LIBRARY_VERSION` | `0.9.5` | `0.9.5`（未变） |
| Compose / Kotlin / AGP | 1.13.0 / 2.4.21 / 9.5.2 | 未变（官方这批提交没有动版本目录） |

官方这 12 个提交（`git log 3c560868..542463a0`）：

```
542463a0 library: update baseline profiles and cover all pager tabs
9c256923 example: fix GlassPage scroll-end haptic and bottom bar padding
c217b445 fix(glass): smooth progressive blur top scrim in GlassTopAppBar and GlassBottomSheet
d2e52284 fix(glass): clamp heightOffset in GlassTopAppBarLayout progressive blur
48bd11c7 example: improve icon search ranking, scroll reset, and top bar collapse
a09d4f32 fix(glass): improve dynamic color contrast for glass compoment
c262ff7c library: Add progressive blur header and actions to GlassBottomSheet
6bf1a4b0 example: set GlassBottomSheet card spacing to 12.dp
ae3be934 fix(glass): reduce GlassTopAppBar default BandOverhang to 32.dp
0f2f05a9 fix(ui): extend bottom sheet surface directly on upward overscroll
b5d2d698 fix(ui): reset predictive back state on dialog and sheet open/close
03d15786 fix(glass): preserve closing rebound on anchor button
```

基线策略说明：R11/R12 钉 tag；本轮按用户要求跟到 main 最新（tag 之后全是 Glass 渲染修复，
没有新 tag 可用）。仍然只认 commit sha，不跟 `origin/main` 漂移。详见 `MIUIX_BASELINE.md` §2。

---

## 2. 自造轮子清查（本轮实际替换的）

清查范围：`app/src/main/kotlin` 全部 UI 代码，对照 `miuix/example/shared` 与 `miuix-*/src`。
R6/R9/R10/R11 已清掉的自造件不再重复列举。

### A. 尺寸 / 间距 / 边距

| # | 位置 | 自造内容 | 官方替代 |
|---|---|---|---|
| A1 | `ui/components/PageFrame.kt` `PageMetrics.Horizontal` | 自己写 `12.dp` | `GlassTopAppBarDefaults.HorizontalPadding`（KDoc 明确 "aligned with the page's cards and tabs"；官方 `GlassPage.kt:162/427/563` 同源） |
| A2 | `PageFrame.kt` `PageMetrics.CardCorner` / `GroupCard(insideMargin = PaddingValues(0.dp))` | 死常量 + 与默认值重复的形参 | 删除 `CardCorner`；`GroupCard` 不再传 `insideMargin`（`CardDefaults.InsideMargin` 已是 0） |
| A3 | `feature/common/AppComponents.kt`、`feature/apps/AppsScreen.kt` | 两处写死 `40.dp` 应用图标 | 统一 `PageMetrics.ListAppIcon` |

### B. 滚动 / 动画

| # | 位置 | 自造内容 | 官方替代 |
|---|---|---|---|
| B1 | Home 网格、Apps 列表、Settings、About、Licenses 的 `Lazy*` | 默认弹性 overscroll 与 PageFrame 的 `pageScrollModifiers`（`overScrollVertical`）双份 | 全部补 `overscrollEffect = null`（官方 `GlassPage.kt:537`、`IconPage.kt:411`、`SettingsPage.kt:106`、`LicensePage.kt:117`、`AboutPage.kt:317` 同参） |
| B2 | 6 处 `GlassBottomSheet` 内容 | 自己挂 `scrollEndHaptic().overScrollVertical()` | 只留 `scrollEndHaptic()`；嵌套滚动与上沿延伸交给新版 `GlassBottomSheet`（官方 `BottomSheetSection.kt:211` 同做法） |

### C. 组件 / 观感

| # | 位置 | 自造内容 | 官方替代 |
|---|---|---|---|
| C1 | `feature/about/AboutScreen.kt` `AboutCard` | `Card` + 自己 `textureBlur` + 自建混色 token | 官方 `GlassCard`（最新官方 `AboutPage.kt:352-400` 已这样用；圆角 20dp、半透明填充由 `GlassCardDefaults` 决定） |
| C2 | 6 处 `GlassBottomSheet` 内容 | 不带标题栏内边距（新基线标题栏变成覆盖式，首屏会压到标题下） | 用 content 槽给的 `PaddingValues.calculateTopPadding()` 做首屏 `contentPadding.top`（官方 `glassbottomsheet.md` 示例同做法） |
| C3 | 长按菜单（主页网格 + 应用页行） | `GlassBottomSheet` 底部弹层（顶边贴住被长按的图标；窗口吃掉 UP，按下层可能残留） | 官方 `GlassTransformPopup` + `GlassPopupItem`：从被长按的格子/图标原地展开。锚点由长按回调直接给出（`rememberGlassPopupAnchor` / `glassPopupAnchor` / `glassPopupAnchorContent`），不按包名查表 |
| C4 | 长按浮层的宿主 | 直接画在页面 overlay 里 | 官方 example 的 `TopLayer` / `TopLayerHost`（`ui/TopLayer.kt` 逐字节移植）：浮层在页面录制层与玻璃侧栏之外，**不自采样、不被侧栏盖住** |

### D. 代码/注释一致性

- `HomeScreen` 里"锚点浮层将来真机再接"的过时注释、`PageMetrics` 死常量、关于页卡片混色 token
  （`AboutCardBlendLight/Dark`）已随替换删除。
- `MIUIX_BASELINE.md` §7 里 `UiFeedback.toast()` 重复两行的问题一并清掉。

### 保留的自写部分（官方只有 example、没有库 API）

`ui/PageInsets.kt`、`ui/TopLayer.kt`、`ui/effect/`、`pageContentPadding` / `pageScrollModifiers` /
`rememberSurfaceBackdrop`、`PageFrame` 页面骨架、`EmptyState`、`PageMetrics` / `HomeGridMetrics`、
`UiFeedback.toast`、`NavTransitions.MiuixDefault` —— 与 R11 结论一致，属于页面级组合或 example
私有能力的等价移植，不重复实现官方底层能力。

---

## 3. 长按锚点浮层：R12 崩溃的结论修正

R12 记录"`GlassPopup` / `GlassTransformPopup` 在本机稳定崩在 RenderThread（RenderNode 递归 512 层）"。
本轮模拟器接入宿主 GPU（`dumpsys SurfaceFlinger`：`Android Emulator OpenGL ES Translator
(AMD Radeon RX 9070 XT)`，GLES 3.1，硬件 RenderEffect）后：

- `GlassTransformPopup` 在主页网格、应用页列表行都**正常工作**，无崩溃、无 ANR；
- 关闭时能正常播回弹（main 的 `preserve closing rebound on anchor button` 修复生效）；
- 展开期间锚点内容隐藏，松手后不再有按下层残留（应用页 `Card` 的按下层问题一并消失，
  因为浮层是就地绘制的，不另开窗口吃 UP）。

结论：R12 的崩溃是 **SwiftShader 软件渲染路径**的问题，不是用法问题。真机/硬件渲染路径可用。
（库的 `isRuntimeShaderSupported()` 只判 `SDK_INT >= 33`，无法在运行时探测 SwiftShader；本轮不做
自定义探测，使用场景已由 GPU 模拟器覆盖。）

### 3.1 踩到的两个上游行为（已绕过，未改库）

1. **固有尺寸只探测前 8 个子项**：`GlassPopupColumn.POPUP_PROBE_COUNT = 8`。菜单 = 标题 + 分割线
   + N 行，超过 6 行时面板高度按 6 行算，其余行在弹层内滚动。主页 10 行菜单实测可滚动到最后一行，
   文案完整；顺序按使用频率排（启动/冻结/定时/置顶/白名单/标签在前）。
2. **固有尺寸缓存按 content lambda 做 token**：如果浮层在"空菜单"状态下先组合过一次，
   面板高度会被缓存成只有标题行，之后补上菜单内容也不再重算（表现为只显示标题、行被裁掉）。
   绕过方式：`if (lastActionEntry != null)` 门控——第一次组合就带完整菜单；退场期间
   `lastActionEntry` 保留，退出动画不受影响。

---

## 4. 已知问题清单逐条处置

| # | 原问题 | 本轮结果 |
|---|---|---|
| 1 | 长按菜单是底部弹层，顶边贴图标 | **已修**：换成 `GlassTransformPopup` 锚点浮层，从图标原地展开 |
| 2 | 应用页长按后按下层可能残留 | **已修**：应用页也改锚点浮层（就地绘制，不另开窗口吃 UP），锚点内容展开期隐藏 |
| 3 | 底栏指示器切换页签后蓝色模糊残影 | **复核为非缺陷**：设置页底栏左侧的蓝色块是「主页标签字号」滑块蓝色轨道透过玻璃的正常采样，滚动离开即消失，切页签后不残留；1.5.0 报告的"残影"本轮未复现 |
| 4 | `default` 模式冻结只记录状态 | 设计如此（工作模式说明），文档保留说明，不改 |
| 5 | 锚点浮层真机可用性 | **已在 GPU 模拟器验证可用**（见 §3）；真机仍需一次补验光学表现 |
| 6 | 崩溃技术结论 | **已定性**：SwiftShader 软渲染路径；硬件 RenderEffect 正常（见 §3） |
| 7 | 真机玻璃光学（L3） | 未覆盖：模拟器已接宿主 GPU，仍不等同真机材质/描边/阴影 |
| 8 | 六条冻结后端只有源码证据 | 未覆盖：本轮只做 UI，未改冻结链路；模拟器只有 Root 链路验证过 |
| 9 | 覆盖升级只测最近一档 | 未覆盖：本轮没有发版，仍只验证过 1.6.0 → 1.6.1 |
| 文档 | §7 `UiFeedback.toast()` 重复两行 | **已清理** |

---

## 5. 改动面

| 文件 | 改动 |
|---|---|
| `MIUIX_BASELINE.md` | 基线 `542463a0`；§5 组件表补锚点浮层/TopLayer/新弹层语义；§6 补 `TopLayer.kt`；§7 去重 + 更新已知差异；§8 升级注意；§10 变更记录 |
| `README.md` | 基线数值、长按菜单说明、TopLayer/GlassCard 对齐说明、审计索引 |
| `app/.../ui/TopLayer.kt` | **新增**（官方 example 移植） |
| `app/.../ui/GlacierShell.kt` | 提供 `LocalTopLayer` + 根部 `TopLayerHost` |
| `app/.../ui/components/PageFrame.kt` | `overlay` 回调带页面 backdrop；`PageMetrics` 用官方 `HorizontalPadding`；删死常量；`GroupCard` 去掉重复 `insideMargin` |
| `app/.../feature/home/HomeScreen.kt` | 长按改锚点浮层（每格锚点 + `GlassPopupItem`）；删除零透明 indication 补丁；3 个弹层补标题栏内边距/去 `overScrollVertical`；网格 `overscrollEffect = null` |
| `app/.../feature/apps/AppsScreen.kt` | 长按改锚点浮层（行图标锚点）；2 个弹层同上；列表 `overscrollEffect = null` |
| `app/.../feature/common/AppComponents.kt` | `AppRow` 图标登记锚点；应用图标尺寸统一 `PageMetrics.ListAppIcon` |
| `app/.../feature/common/AppSearchResults.kt` | 搜索结果行长按把锚点交给页面；不传 `onLongPick` 时不登记锚点 |
| `app/.../feature/common/AppActions.kt` | `AppActionSheet`（仅 API Activity 用）内容让出标题栏 |
| `app/.../feature/settings/SettingsScreen.kt` · `feature/about/AboutScreen.kt` · `feature/about/LicensesScreen.kt` | 列表 `overscrollEffect = null`；AboutCard 换 `GlassCard`；删除关于页卡片混色 token |
| `app/.../ui/effect/AboutBlendTokens.kt` | 只保留品牌名 textureBlur token |
| `docs/audit/miuix-os4-r13-audit.md` · `docs/verification/miuix-os4-r13/` | 本文件 + 11 张验证截图 |
| `docs/screenshots/home-menu.png` · `apps-menu.png` | 换成锚点浮层实拍 |

---

## 6. 验证

设备：`emulator-5554`，1080×2400 @420dpi，Android 16 / API 36，**宿主 GPU（AMD Radeon RX 9070 XT）**。
构建：`:app:assembleDebug`、`:app:testDebugUnitTest` 均通过。

截图见 `docs/verification/miuix-os4-r13/`：

| 文件 | 场景 |
|---|---|
| `01-home-menu-anchor.png` | 主页长按 → 从格子原地展开的锚点菜单（标题 + 6 行，另 3 行滚动可见） |
| `02-home-menu-scrolled.png` | 弹层内滚动到底：添加主屏幕 / 导出剪切板 / 从主页移除，文案完整 |
| `03-apps-menu-anchor.png` | 应用页长按 → 从行图标展开的管理菜单（3 行） |
| `04-menu-closed-no-press-residue.png` | 关闭后主页干净：图标回归、无按下层残留 |
| `05-sort-sheet-header.png` · `06-filter-sheet-header.png` | 新版 `GlassBottomSheet` 标题栏 + 内容让位 |
| `07-about-glasscard.png` · `08-about-glasscard-dark.png` | 关于页信息卡换成官方 `GlassCard`（浅/深色） |
| `09-settings.png` · `10-home-dark.png` | 设置页 / 深色主页 |
| `11-landscape-rail.png` | 横屏宽屏：玻璃侧栏折叠胶囊（官方宽屏行为） |

---

## 7. 未覆盖 / 后续

- 真机（非模拟器）玻璃光学 L3：材质、描边、阴影的观感仍需一台真机确认。
- 冻结后端：Shizuku / Dhizuku / DeviceOwner / Island / 特权 未改动，仍只有源码级证据。
- 覆盖升级：本轮未发版；下次发版仍需跑 1.6.1 → 新版覆盖安装。
- 上游可选优化：若官方把 `POPUP_PROBE_COUNT` 改成按内容全量探测，10 行主页菜单可以一屏展示，
  届时删除"6 行 + 滚动"的说明即可。
