# R4 专项四 · Apps 页 + 应用操作面板微交互（part-apps）

> 负责人：apps-micro（task-4）
> 写作范围：`feature/apps/AppsScreen.kt`、`feature/common/AppActions.kt`、`feature/common/AppSearchResults.kt`（实际改动）；`feature/common/AppComponents.kt` 属 home-p0，不改，只给交接项。
> 约束：未运行 gradle / 安装 / adb / git；所有结论来自本地 MIUIX 源码逐行对照与代码审查，运行时效果待 lead 统一构建与截图验证。

---

## 0. 结论摘要

| # | 项 | 结论 | 改动 |
|---|---|---|---|
| 1 | 按压状态 | Apps 行与搜索行改为**同一个**官方 `Card` + `BasicComponent` 行实现（`AppListCardRow`）；按压反馈只有 `BasicComponent` 一层 `MiuixIndication`，被 `Card` 的 `squircleSurface` 裁成圆角；`Card Sink` 不再与行内点击并存 | 改 `AppSearchResults.kt`（新增共用行）、`AppsScreen.kt`（列表行接入）；`AppActions.kt` / `AppsScreen.kt` 弹层行加 `Card` 分组 |
| 2 | 长按状态 | Apps 行**没有也不引入长按**：点按 = 打开操作单（该页的单一语义）。无 hold-down 高亮、无长按/点击竞争；不必要高亮问题不存在 | 无（结论 + 多选路线见 §2） |
| 3 | 选中状态 | **本轮不实现 Apps 多选**（理由与影响见 §3，非含糊）；排序单选项改为官方 `RadioButtonPreference`，选中态/语义/触觉按官方 | `AppsScreen.kt` SortSheet |
| 4 | 禁用状态 | 修掉两处「看起来可点但无效」：Apps 操作单里的 `编辑标签` / `定时冻结`（runner 默认空实现）不再渲染；`Reinstall/Uninstall` 继续按安装态二选一 | `AppActions.kt` 新增 `unsupportedActions`；`AppsScreen.kt` 传入集合 |
| 5 | 滚动交互 | 页面级 overscroll / 滚动结束触觉由 shell-nav 在 `PageFrame` 统一落地（已对齐，本页**不重复挂**）；Apps 列表已接入 `PageFrame.scrollBarAdapter`（官方每页显式滚动条）；弹层内用官方 `scrollEndHaptic + overScrollVertical` | `AppsScreen.kt`（列表 state + 滚动条接线；弹层内滚动修饰） |
| 6 | 大字体 / 弹层 | 操作单 10 行由 `Column` 改 `LazyColumn`（官方 BottomSheet 内容写法），2.0x 字体 / 横屏下可滚动不再被弹层 `heightIn(max)` 裁掉；排序、筛选弹层同样改 `LazyColumn` | `AppActions.kt`、`AppsScreen.kt` |
| 7 | 排序 / 筛选弹层 | 行结构统一为官方 preference（单选 `RadioButtonPreference` / 开关 `SwitchPreference`）+ `Card` 分组；选中态、触觉、`Role` 语义由官方组件给；`backdrop` 传参核对为正确（残余 L3 采样偏差属 Shell 根录制层，见 §7） | `AppsScreen.kt` |
| 8 | 搜索结果行 = Apps 列表行 | 收敛为**同一 composable `AppListCardRow` + 同一摘要 `appRowSummary`**，搜索/列表只剩外层间距不同；Home 搜索行仍是 home-p0 的私有实现（交接见 §8） | `AppSearchResults.kt`、`AppsScreen.kt` |

本轮**未新增任何字符串资源**（全部复用现有 `R.string.*`：`action_*`、`badge_*`、`sort_*`、`filter_*` 等），因此没有创建 `res/values/strings_apps_r4.xml` / `values-zh-rCN/strings_apps_r4.xml`。

---

## 1. 按压状态（是否有方形叠层 / 多层 indication / 异常缩放 / 与 Glass 冲突）

### 1.1 现状（改动前）

- `AppsScreen.kt:221-238`（改前）Apps 列表行：`Card(onClick, pressFeedbackType = Sink)` + `AppRow(onClick = null)`。
  - 只有一层反馈，但用的是 `Card` 的 Sink **缩放**；而搜索行（`AppSearchResults.kt:97-110` 改前）用的是 `Card`（无点击）+ `AppRow(onClick)` 的 **indication 覆盖层**。两者按压语言不同 → 违反第 8 项。
- `AppActions.kt:184-264`（改前）操作单：`Column` 直接装 10 个 `SheetActionRow`（`BasicComponent(onClick)`），**没有 Card 分组**。`BasicComponent` 的 `LocalIndication = MiuixIndication` 在 `ContentDrawScope` 里 `drawRect`（`miuix-ui/.../utils/MiuixIndication.kt:129-135`），在无裁切的 Column 里就是一块**直角矩形**叠层，且直接压在 Glass 弹层上。
- `AppsScreen.kt:260-287 / 309-328`（改前）排序/筛选弹层同样是 `Column` 直装 `BasicComponent` / `SwitchPreference`，没有任何圆角裁切。

### 1.2 官方依据

- 默认 indication 就是 MiuixIndication 矩形：`miuix-ui/theme/MiuixTheme.kt:33`、`:63`（`MiuixIndication(color = onBackground)`）+ `:37`、`:67`（`LocalIndication provides miuixIndication`）→ `MiuixIndication.kt:129-135` `drawRect`。
- 官方列表行结构（搜索结果）：`miuix/example/shared/src/commonMain/kotlin/IconPage.kt:221-244`：`Card`（**不传 onClick**）内放 `BasicComponent(onClick = ...)`。行内 `BasicComponent` 是唯一点击/反馈来源。
- `Card` 自身语义：`miuix-ui/basic/Card.kt:92-93`（`pressFeedbackType = None`、`showIndication = false` 默认）、`:110-116`（Sink/Tilt 是 Layout 缩放反馈，不是 ripple）、`:119`（`indicationToUse = if (showIndication) LocalIndication.current else null`）、`:121-136`（`combinedClickable(indication = indicationToUse)`）、`:139-143`（外层 `pressable(indication = pressFeedback)`）。
  → **`Card(onClick, Sink)` 是官方 `CardSection.kt:68-105` 演示过的"整卡可点"写法，但行点击的官方范式是行组件自身 `BasicComponent(onClick)`**；两者都合法，但同一列表里不能混用，否则首屏与搜索结果的按压语言不一致。
- 圆角裁切链：`Card` 内容盒用 `squircleSurface`（`Card.kt:174-183`）→ `miuix-squircle/.../SquircleBackground.kt:124-165`（`squircleSurface` = fill + `squircleShaderMask` 裁切子树，`:255-278` 用 `Offscreen` + `DstIn` 把四角挖掉）。因此 `Card` 内的 `BasicComponent` 矩形叠层会被裁成圆角 —— **这是"没有正方形按压层"的官方机制**。
- 官方网格点击控件也先裁切再 clickable：`IconPage.kt:403-411`（`.squircleClip(12.dp).clickable { ... }`）。
- 官方 BottomSheet 内容用 `LazyColumn` + `scrollEndHaptic()` + `overScrollVertical()`：`example/component/BottomSheetSection.kt:209-213`；行分组用 `Card`：同文件 `:219-237`。

### 1.3 改动

- `AppSearchResults.kt:51-68` 新增共用行 `AppListCardRow`：`Card`（不注册点击）+ `AppRow(onClick)`。**Apps 列表与搜索结果都只走这一个实现**。
- `AppsScreen.kt:224-235`（列表）改为调用 `AppListCardRow`；不再有 `Card(onClick, Sink)`，也不再存在"Card 与行两层点击/两层反馈"的组合风险。
- `AppActions.kt:297-326`（操作单）与 `AppsScreen.kt:271-286`（排序）、`:306-330`（筛选）把行包进 `Card(Modifier.fillMaxWidth())`：`BasicComponent` 的 MiuixIndication 矩形叠层被 Card 的 squircle 裁切，第一行/最后一行的高亮圆角与卡片轮廓一致。
- 层数核对：`AppListCardRow` 内 `Card` 无 `onClick`/`onLongPress` → 不创建 `combinedClickable`、无 `pressable`（`Card.kt:99-136`）；`AppRow` 的 `BasicComponent(onClick)` 只创建 1 个 `clickable` → **1 层 indication**。操作单每行 `SheetActionRow` 同为 1 层。
- 异常缩放：不再有 `Card Sink`（0.94 缩放在长列表里滚动/连点会显得整卡抖动），行按压只有 alpha 叠层，无缩放；`BasicComponent` 无缩放（`Component.kt:148-160` 只 `clickable`）。

### 1.4 未完成 / 风险

- `HomeAppItem` 的网格 `combinedClickable`（`HomeScreen.kt:509-521`）是 P0-1 的同类问题，属 home-p0 范围；其修复方式可参考 `IconPage.kt:403-411`（先 `squircleClip` 再 clickable）或改用 `Card` 的 pressable 封装。**本轮 Apps 不复制那套写法**。
- 运行期需确认：Card 圆角裁切依赖 `LocalSquircleEnabled` 与 runtime shader（`SquircleBackground.kt:362-386`）；API < 33 自动退回 `clip(RoundedCornerShape)`（`:120`、`:162`），仍有圆角，无方形叠层。
- `AppRow`（home-p0）当前 KDoc 仍写"外层 `Card(onClick, Sink)` 承担点击时传 `null`"（`AppComponents.kt:160`），与新统一写法相反；只是注释不一致，不影响编译，建议 home-p0 顺手改注释（见 §9 交接）。

---

## 2. 长按状态

### 2.1 现状

- Apps 列表行**没有长按**：改前 `Card(onClick)`（`Card.kt:95-96` 的 `onLongPress` 未传）→ 改后 `BasicComponent(onClick)` 只有单击。Apps 页点一行的语义 = 打开该应用的**操作单**（`AppsScreen.kt:222-226`）。
- 没有 hold-down 高亮：`holdDownState` 未传（`Card.kt:102-108`、`Component.kt:138-146` 的 HoldDownObserver 不会挂），长按不会出现额外的 0.10 alpha 叠层。
- 重复点击/长按冲突：`BasicComponent` 是纯 `clickable`（无 long-click），不存在"长按后松手又触发点击"的组合；快速连点只会打开一次操作单（`actionTarget` 幂等赋同一个包名）。

### 2.2 官方依据

- `Card(onLongPress)` 与 `holdDownState` 的官方用法：`CardSection.kt:110-168`（长按弹 Dialog + `holdDownState` 保持按下态，直到 `onDismissFinished` 复位）。
- 网格长按进入选择：官方 `IconPage.kt` 无多选；Glacier Home 的交互语言是 `HomeScreen.kt:474-483`（长按切换选中）+ 顶栏选择态动作。
- 任务书 §7.2 要求检查"长按触发准确性 / 进入选择模式过渡"——该检查项**对 Apps 不适用**：Apps 没有长按，也没有选择模式；不存在"长按误触""不必要高亮"。（多选取舍见 §3。）

### 2.3 改动

- 无代码改动（有意不引入长按）。若未来 Apps 接多选，长按进入选择是既定方向，见 §3 的接入方案。

### 2.4 未完成 / 风险

- 与 Home 的交互语言存在信息架构差异：Home 单击=启动、长按=多选；Apps 单击=操作单。这是 R3 的既定设计（Apps 是管理页），本轮不改；但需 lead 确认产品上是否接受（写进 R4 最终未完成清单的"设计取舍"）。

---

## 3. 选中状态（Apps 多选要不要做 —— 明确不做的理由与影响）

### 3.1 现状

- Apps 无多选（R3 P1-15 判为功能增量）。列表行没有选中态；搜索行 onPick 打开操作单；排序弹层的"选中"此前用尾部 `Ok` 图标（`AppsScreen.kt:272-280` 改前）表达，无 `selected` 语义。
- Home 已有完整多选：`HomeScreen.kt:159-161`（`selecting` / `selection`）、`:474-483`（长按切换）、`:211-241`（顶栏冻结/解冻/标签/更多/关闭）、`:185`（`inSelection` 标题变"已选 N 项"）。
- home-p0 本轮已给 `AppRow` 增加 `selected: Boolean = false`（`AppComponents.kt:166-197`，转发 `AppIconView(selected=...)` 的 squircle 描边），**Apps 接多选的视觉 API 已经就位**。

### 3.2 官方依据 / 参考

- 选中描边语言：`miuix-squircle` 的 `squircleBorder`（`AppComponents.kt:90-100`）；Home 网格同一套。
- 多选顶栏动作：`GlassPage`/`IconPage` 无"多选"官方组件，官方 example 与本项目 Home 都是业务自组（`HomeScreen.kt:211-241`）。
- 单选行语义：`RadioButtonPreference`（`miuix-preference/.../RadioButtonPreference.kt:50-143`，`role = Role.RadioButton`，`:127-138` 选中/取消触觉）。

### 3.3 决定：本轮**不实现** Apps 多选（理由 / 影响 / 后续方案）

**理由（不是"没时间"，是分层与一致性）：**

1. **不能"半套"复用 Home 的交互语言**：Apps 多选若只做"长按选中 + Card 变色"，选中态就不是 Home 的 squircle 描边（`AppIconView.selected`），会出现第二套选择语言 —— 违反 §9「Home 和 Apps 统一性」。现在 `AppRow.selected` 已由 home-p0 提供，视觉这一关可以过，但还缺下面 2、3。
2. **操作集合不一致**：Home 选择态的动作里，`编辑标签` 与 `定时冻结` 依赖 Home 私有的 `TagNameDialog` / `DeferredSheet`（`HomeScreen.kt:865-890` 的 `TagNameRequest`、`DeferredSheet`）。Apps 没有这两个弹层；本轮已经因为"没有实现"把这两行从操作单隐藏（§4）。如果多选只做冻结/解冻/关闭，同一"多选"在 Home 和 Apps 能干的事不一样，属于产品功能增量而非微交互整改。
3. **批量结果反馈要复制 Home 私有逻辑**：Home 的 `reportBatch`（`HomeScreen.kt:879-897`）是 private，依赖 `FreezeOutcome`。放进 AppsScreen 就是第二份复制，违反 §12「减少自造轮子」；正确做法是抽到 `feature/common`（`AppActions.kt`）供两页共用，但这会改动 Home 文件（home-p0 范围），必须由 lead 统一安排。
4. R3 已明确把 P1-15 判为"功能增量"；本轮任务是"微交互正确"，不是堆功能（任务书 §13 第三阶段 + 本轮约束）。

**影响（写清，不含糊）：**

- Apps 页无法批量冻结/解冻/移除主页；只能逐条点开操作单。Home 页仍可对"已在主页"的应用批量操作。
- 不存在"选中态错误/进入选择模式过渡缺失"的微交互缺陷 —— 因为功能不存在；验收时 Apps 的"选中"检查项应判 N/A，而不是 fail。
- 无障碍/IME 大字体下不受影响。

**后续落地方案（交给 lead 排期，预计 P2）：**

1. home-p0 或 lead 把 Home 的 `reportBatch` 抽成 `feature/common/AppActions.kt` 的 `reportFreezeBatch(context, outcome, frozen)`；
2. AppsScreen 增加 `selecting/selection` 状态，顶栏切换为 `QuietMode / UnlockOutline / Close`（文案复用现有 `selected_count`、`action_freeze`、`action_unfreeze`、`close`）；
3. 行容器 `AppListCardRow` 增加 `selected` / `onLongClick`：`Card(onClick, onLongPress, pressFeedbackType = Sink)` + `AppRow(onClick = null, selected = selected)`（Card 的官方长按/按下态 API：`Card.kt:87-154`、`CardSection.kt:110-143`）；
4. 与搜索结果行的"同一实现"需要重新对齐（搜索不需要多选，可在 `AppListCardRow` 上留可选参数）。

### 3.4 未完成 / 风险

- 本轮 Apps 无选中态：属功能未实现（不是微交互回退）。
- 排序弹层的"选中"已改为官方 `RadioButtonPreference`（`AppsScreen.kt:285-289`），本轮"选中态正确"的检查对象落在排序/筛选，而不是应用行。

---

## 4. 禁用状态（不可操作项是否明确 / 对比度 / 图标弱化 / 假可点控件）

### 4.1 现状（改动前）

- Apps 操作单固定渲染最多 10 行（12 种动作中 `Freeze/Unfreeze`、`Reinstall/Uninstall` 各按状态取其一；`AppActions.kt:185-252` 改前）。其中：
  - `编辑标签` → `AppActionRunner.run` 的 `EditTags -> onEditTags(pkg)`，默认 `onEditTags = {}`（`AppActions.kt:70`）→ **点了没反应**；
  - `定时冻结` → `DeferredFreeze -> Unit`（`AppActions.kt:115` 改前）→ **点了没反应**；
  - 二者在 Home 由调用方特殊处理（`HomeScreen.kt:345-347`），在 Apps / ApiActivity 没有入口 → 典型的"看起来可点但无效"。
- `Reinstall / Uninstall` 已按 `entry.isInstalled` 二选一（`AppActions.kt:241-252` 改前），无假可点。
- `SheetActionRow(enabled)` 参数此前**从未被使用**；home-p0 本轮补了图标禁用色（`AppComponents.kt:131-152`，`iconColor = if (enabled) color else disabledOnSecondaryVariant`）。

### 4.2 官方依据

- 禁用色：`BasicComponent` 的 `BasicComponentColors.color(enabled)` → `disabledOnSecondaryVariant`（`miuix-ui/basic/Component.kt:287-320`、`:323-330`）。
- 可点 vs 只读的官方区分方式：文档契约 §G.5（`docs/reference/miuix-os4-contract.md:902-918`）；不可操作行要么 `enabled=false`，要么不渲染。
- 行为准则：任务书 §7.4「不允许出现看起来可以点击但实际上无效的控件」。

### 4.3 改动

- `AppActions.kt:182-196` 新增 `unsupportedActions: Set<AppAction> = emptySet()`；`:234-242`、`:255-263` 两处按集合**不渲染**对应行（默认空集合 → Home / ApiActivity 行为不变）。
- `AppsScreen.kt:71-75` 定义 `AppsUnsupportedActions = setOf(EditTags, DeferredFreeze)` 并传给 `AppActionSheet`（`:192`）→ Apps 操作单不再出现两个死行。
- 对比度/图标弱化：被隐藏，不再有"灰字亮图标"或"亮字无响应"；`SheetActionRow(enabled=false)` 的图标弱化已由 home-p0 补齐，本轮**无需**使用（隐藏优于禁用：禁用仍占位、仍暗示"以后能用"）。

### 4.4 未完成 / 风险

- `ApiActivity.kt:78-101` 仍以默认 `unsupportedActions` 调用 → 独立窗口操作单里的 `编辑标签/定时冻结` 仍是死行。属 settings-micro 范围（task-5 第 3 项"操作面板复用是否正确"）；已写交接（§9）。
- 若后续给 Apps 补上标签编辑/定时冻结入口，只需从 `AppsUnsupportedActions` 移除对应项。
- 系统应用卸载失败等"可操作但可能失败"的项保留可点 + toast（`AppActionRunner`），符合官方做法（失败反馈），无需禁用。

---

## 5. 滚动交互（overscroll / 触觉 / 滚动条 / 顶栏折叠 / 大字体滚动区）

### 5.1 现状

- Apps 列表 `LazyColumn`（改前 `AppsScreen.kt:194-208`）此前没有任何滚动修饰：无 overscroll 弹性、无滚动结束触觉、无滚动条（`git grep overScrollVertical` 在 Glacier 只有 Home 的 `DeferredSheet` 等零散使用；Apps 无）。
- 页面滚动与顶栏折叠：`MiuixScrollBehavior` 已由 `PageFrame` 的 `nestedScroll` 接入（`PageFrame.kt:190-197`，改前即如此），无问题。
- 搜索浮层结果列表：官方 `IconPage.kt:534-543` 同样不挂 overScroll/haptic（浮层列表），本轮保持官方行为，`AppSearchResults` 不加修饰。
- PullToRefresh：Apps 页**没有 PullToRefresh**，也不应有（应用列表来自本地 `repository.snapshot`，不存在"下拉刷新"语义；`repository.refresh()` 在动作后自动调用）。任务书要求"对照官方 PullToRefresh 用法检查语义"——结论：未使用是正确语义，若为凑功能加下拉刷新反而会产生"看起来能刷新、实际无新数据"的假交互。

### 5.2 官方依据

- 页面级统一入口：官方 `example/utils/PageUtils.kt:168-183` 的 `Modifier.pageScrollModifiers` = `scrollEndHaptic()` + `overScrollVertical(onOffsetChanged = { topAppBarScrollBehavior.state.overscrollOffset = it })` + `nestedScroll(topAppBarScrollBehavior.nestedScrollConnection)` + `fillMaxHeight()`。
- 组件：`miuix-ui/utils/Overscroll.kt:48-55`（`overScrollVertical`）、`ScrollEndHaptic.kt:124-126`（`scrollEndHaptic`，默认 `HapticFeedbackType.TextHandleMove`）。
- 触觉默认开启的官方依据：`example/AppState.kt:35` `enableScrollEndHaptic = true`（Glacier 无此设置项，按官方默认）。
- 重复挂载警告：官方 `PullToRefreshPage.kt:156` —— "pageScrollModifiers owns the app bar connection; do not feed it twice"。
- 滚动条：官方每页显式加 `VerticalScrollBar`（`MainPage.kt:341-345`、`IconPage.kt:439-443`、`SettingsPage.kt:506-507`），组件 `miuix-ui/basic/ScrollBar.kt:136-159`。

### 5.3 改动（与 shell-nav 的接口对齐）

- 已与 shell-nav（task-3）确认最终方案：**P1-9 的主体由 `PageFrame` 统一落地**，`PageFrame` 内部等价挂 `PageUtils.kt:168-183` 的链（haptic → overscroll → nestedScroll → fillMaxHeight）；页面**不要**自己再挂，否则双份。
- 本页因此不再添加 `overScrollVertical/scrollEndHaptic` 到主列表；`AppsScreen.kt:113-115` 增加主列表 `LazyListState`。
- 滚动条：shell-nav 已在 `PageFrame.kt` 落地官方每页滚动条参数 `scrollBarAdapter: ScrollBarAdapter? = null`
  （`PageFrame.kt:173-181`、`:253-266`，`trackPadding` 按 Scaffold padding 自动算，层序与官方 `MainPage.kt:341-345` 一致）。
  Apps 页按官方接线：`AppsScreen.kt:88-89` 加 `@OptIn(ExperimentalScrollBarApi::class)`，
  `:128-131` 传 `scrollBarAdapter = rememberScrollBarAdapter(listState).takeIf { entries.isNotEmpty() }`
  （空列表时正文是 `EmptyState`、没有滚动容器，不挂滚动条），`:217-233` 的 `LazyColumn` 使用同一个 `listState`。
  - 注意：`rememberScrollBarAdapter` 属 `ExperimentalScrollBarApi`（`miuix-ui/interfaces/ExperimentalScrollBarApi.kt:23`），页面必须 `@OptIn`。
- 弹层内滚动（不是页面滚动）：操作单 / 排序 / 筛选的 `LazyColumn` 按官方 BottomSheet 写法挂 `.scrollEndHaptic().overScrollVertical()`（`AppActions.kt:297-301`、`AppsScreen.kt:268-272`、`:318-322`；官方 `BottomSheetSection.kt:209-213`）。弹层内的 overscroll 会通过 `LocalBottomSheetNestedScrollConnection` 让位给弹层拖拽（`Overscroll.kt:357-367`、`BottomSheetContentLayout.kt:602-605`），不会和页面滚动打架。

### 5.4 未完成 / 风险

- 页面滚动修饰的**单一来源是 PageFrame**：Apps 页没有自己挂 `pageScrollModifiers`，交接给 shell 的滚动实现（若 shell 侧回退该实现，Apps 会同时失去 overscroll/haptic，需统一回退而不是在 Apps 单独补）。
- 「滚动结束触觉默认开启」是官方 `example/AppState.kt:35` 的默认值，Glacier 没有对应设置项，需在 lead 汇总时写进 R4 变更。
- 滚动条轨道内边距由 PageFrame 用 Scaffold padding 计算（`PageFrame.kt:256-266`）；搜索浮层展开时页面被 `clearAndSetSemantics` + alpha 淡出，滚动条随页面一起淡出（与官方页面一致），无需额外处理。
- 大字体下滚动区域：本页列表是 `LazyColumn` + `pageContentPadding`（Scaffold padding 随顶栏/底栏变化自动更新），无固定高度假设；弹层侧见 §6。

---

## 6. 大字体 / 弹层（应用操作单 10 行、2.0x、横屏）

### 6.1 现状（改动前）

- `AppActions.kt:184-264` 用 `Column(Modifier.fillMaxWidth())` 装 10 行 + 脚注。弹层面板被 `BottomSheetContentLayout.kt:653-700` 限制为 `heightIn(max = windowHeight - topInset)`，`Column` **不滚动**：超出部分直接被面板裁掉，且 `onGloballyPositioned` 记录的 sheet 高度按自然高度（可能超过窗口）计算，拖拽/退场也会异常。
- 估算（默认字体）：10 行 × 56dp min + 脚注 24dp + 标题/拖拽手柄 ≈ 620dp，多数手机可用高度 ~700dp，**默认字体已接近上限**；2.0x 字体时行高增长（`BasicComponent` 标题 headline1 17sp→34sp + 上下 16dp 内边距），10 行 ≈ 800dp 以上 → 底部若干行不可达。横屏（可用高度 ~300-400dp）默认字体就溢出。
- 排序（3 行）/ 筛选（4 行）在竖屏默认字体不溢出，但 2.0x + 横屏同样可能溢出（4×~80dp + 标题 ≈ 380dp）。

### 6.2 官方依据

- 弹层高度约束：`miuix-ui/layout/BottomSheetContentLayout.kt:653-700`（`wrapContentHeight().heightIn(max = windowHeight - topInset)`）。
- 官方 BottomSheet 内容必须是可滚动容器：`example/component/BottomSheetSection.kt:209-213`（`LazyColumn` + `scrollEndHaptic` + `overScrollVertical`），并在内容里加导航栏底部留白（`:260-265`）。
- GlassBottomSheet KDoc 明确"Add scrolling, navigation-bar padding and extra spacing as needed"（`miuix-glass/.../GlassBottomSheet.kt:47`）；"Apply navigation-bar padding inside scrollable content"（`:22-23`）。
- 行组件本身：`BasicComponent` `heightIn(min = 56.dp)` + 16dp insideMargin（`Component.kt:169-175`、`:292`），会自动随字体长高，不需要也不应该写死行高。

### 6.3 改动

- `AppActions.kt:297-326`：内容改 `LazyColumn(Modifier.fillMaxWidth().scrollEndHaptic().overScrollVertical())`，`contentPadding = PaddingValues(bottom = navigationBars.bottom + PageMetrics.SectionGap)`（`:208`、`:302`）。
  - 10 行放进 `item(key = "actions")` 的 `Card` 内；脚注单独 `item(key = "package-name")`（保留退场期间 `lastEntry` 内容，不闪空）。
  - 底部导航栏留白由 `contentPadding` 提供（滚动时随内容走，弹层 glass 仍延伸到屏幕底边），与官方 `BottomSheetSection.kt:260-265` 等价。
- `AppsScreen.kt:268-293`（排序）、`:318-345`（筛选）：同样 `LazyColumn` + 官方滚动修饰 + `contentPadding` 导航栏留白。
- 顶栏/拖拽手势不受影响：`GlassBottomSheet` 自带 `drag handle`、返回键与 nested scroll（`BottomSheetContentLayout.kt:244-260`、`:486-574`）。

### 6.4 未完成 / 风险

- 运行时需实测：1.0x / 1.3x / 2.0x × 竖屏/横屏，操作单能否滚到最后一行的 `卸载/重新安装`，脚注不被导航栏遮挡；sheet 高度变化时拖拽退场是否正常。
- 弹层标题（`GlassBottomSheet(title=...)` 的 `TitleAndActionsRow`）不滚动，2.0x 下标题换行会挤占内容高度——由 `LazyColumn` 吸收；若标题本身超长（应用名）官方只居中单行，未做 ellipsis，这是官方组件行为，不在本页处理。
- 操作单 `Card` 内 10 行作为一个 Lazy item：滚动可用，但快速滚动时没有"逐行"的 recycle；行数固定（≤10）无性能问题。

---

## 7. 排序 / 筛选弹层（行结构一致性、选中态、点击反馈、Glass 采样）

### 7.1 现状（改动前）

- 排序：所有行 `BasicComponent`，选中态用尾部 `Ok` 图标（`AppsScreen.kt:269-283` 改前）。问题：
  - 没有选中语义（无 `Role.RadioButton`、无 selected 状态），无障碍只会读出"按钮"；
  - 结构其实是"有 endActions 的行"与"无 endActions 的行"混排（R3 注释说要避免，但 `Ok` 图标只在选中行出现，视觉基线仍会跳动）；
  - 无触觉反馈。
- 筛选：`SwitchPreference` 直接放进 `Column`（`AppsScreen.kt:309-327` 改前），无 Card 分组，按压 indication 是未裁切矩形。
- 点击反馈：见 §1（未裁切 MiuixIndication）。
- Glass 采样：`backdrop = LocalRootBackdrop.current.takeIf { LocalGlassActive.current }`（`AppsScreen.kt:257`、`:306` 改前）——传参正确：弹层由根 Scaffold popup 绘制，在根录制层 R 之外；玻璃关闭/不支持时 `null` 走 `containerColor` 不透明回退（`GlassBottomSheetSurface.kt:71-88`）。

### 7.2 官方依据

- 单选行：`miuix-preference/.../RadioButtonPreference.kt:50-143`（`selected`、`Role.RadioButton`、选中色 `RadioButtonPreferenceDefaults.radioButtonPreferenceColors` `:145-163`、点击触觉 `:127-138`）。
- 开关行：`SwitchPreference.kt:43-93`。
- 分组 Card + 行：`example/component/BottomSheetSection.kt:214-238`；`RadioButtonSection.kt:23-36`（`Card` + `RadioButtonPreference`）。
- Glass 采样契约：`GlassBottomSheet.kt:20-22`（backdrop 须含页面与栏、不含 sheet host 与 scrim）；`GlassBottomSheetSurface.kt:52-88`（`null`/无 shader 时回退不透明色）。
- R 层不含 Shell 底栏的残余偏差：`docs/reference/miuix-os4-contract.md` §J / 审计 §"采样内容不完整"；属 shell/glass 层面，非本弹层传参错误。

### 7.3 改动

- 排序：`AppsScreen.kt:268-293` 改为 `Card` + 官方 `RadioButtonPreference`（选中态 = 官方 RadioButton + 主色标题 + 触觉），行全部同构。
- 筛选：`AppsScreen.kt:318-345` 改为 `Card` + `SwitchPreference`（不覆盖任何 `insideMargin`，保持官方 16dp），行分组与排序一致。
- 两个弹层都改 `LazyColumn`（§6）+ 官方滚动修饰 + 导航栏 `contentPadding`。
- Glass 采样：保持不变（核对为正确传参），不改 `backdrop` 语义；`GlassActive=false`/无 shader 的不透明回退由官方组件保证。

### 7.4 未完成 / 风险

- 排序改成 RadioButton 是视觉变化（从"尾部勾"变"起始单选圆点"），需 lead 截图确认与 OS4 视觉预期一致；官方依据是 `RadioButtonSection.kt` / `RadioButtonPreference.kt`，不是自创。
- 卡片分组后弹层内行高亮圆角与卡片轮廓一致；若设计上希望"无卡片、直接铺在玻璃上"，则官方 BottomSheetSection 也不是这种写法，不建议回退。
- 筛选开关点击是"整行可点 + 开关自身可点"（官方 `SwitchPreference` 设计），不存在双触发（子节点消费点击）。

---

## 8. 搜索结果行 = Apps 列表行（同一实现 / 同一视觉）

### 8.1 现状（改动前）

- Apps 列表：`Card(onClick, Sink)` + `AppRow(onClick=null)`，摘要带置顶/白名单/不在主页徽标（`AppsScreen.kt:213-239` 改前）。
- 搜索结果：`Card`（无点击）+ `AppRow(onClick)`，摘要只有包名（`AppSearchResults.kt:97-110` 改前）。
- 差异：按压语言不同（缩放 vs indication）、摘要信息不同（徽标 vs 无）、两处各自拼装行结构。
- 另外 Home 自己的搜索行是第三份：`HomeScreen.kt:586-604`（`Card` + `AppRow(summary = 包名)`，home-p0 私有）。

### 8.2 官方依据

- 官方的"同一行"实现只有一个：`IconPage.kt:221-244`（`Card` + `BasicComponent`）。同一应用在列表/搜索中的图标、标题、摘要、冻结徽标应由同一行组件产出（任务书 §9「Home 和 Apps 统一性」、§7 第 8 项）。
- 摘要徽标文案 token：`R.string.badge_pinned / badge_whitelisted / badge_not_on_home`（R3 已在 Apps 列表使用）。

### 8.3 改动

- `AppSearchResults.kt:51-84`：新增 `AppListCardRow` + `appRowSummary`（两者均为 public，供 AppsScreen 复用）。
- `AppSearchResults.kt:143-151`：搜索行调用 `AppListCardRow`，`inHome` 由 `homePackages` 集合给出（`:102-106`）。
- `AppsScreen.kt:224-235`：列表行调用同一个 `AppListCardRow`，同一份 `appRowSummary`（含徽标）。
- 结果：Apps 列表行与 Apps 搜索行 = 同一 composable、同一图标（40dp `AppIconView`，由 `AppRow` 提供）、同一文字层级（`BasicComponent` headline1/body2）、同一按压反馈（BasicComponent indication + Card 圆角裁切）；唯一差异是外层间距（列表 `SectionGap` 12dp / 搜索 4dp，官方 `IconPage.kt:221` 的搜索行间距）。

### 8.4 未完成 / 风险

- 搜索结果行点击 → 关闭搜索并打开操作单，两段动画并行（操作单由根 Scaffold popup 绘制、自带 scrim，盖在退场中的搜索浮层之上）。官方搜索行点击后会 `delay(400ms)` 再滚动定位（`IconPage.kt:232-239`）——那是为了等浮层收起后滚动到网格位置；Apps 这里是打开上层弹层，没有"先收起再定位"的需求，故未加延迟。若实机观感上是"弹层与搜索退场互相抢戏"，再按官方 400ms 序列化处理（`GlassSearchOverlay.kt:215-240` 的 400ms 转场）。
- **Home 搜索行仍是第三份实现**（`HomeScreen.kt:586-604`，属 home-p0）：建议 Home 也改为 `AppListCardRow`（或至少 `appRowSummary`）。注意 Home 当前摘要只有包名，切到共用行会多出徽标文案——需 home-p0/lead 确认接受（信息更完整、也与 Apps 列表一致）。已写交接（§9）。
- `AppListCardRow` 在 `feature.common` 被 `feature.apps` 引用，方向正确（apps → common）；未反向依赖。
- 搜索结果空查询时仍是 `EmptyState`（`AppSearchResults.kt:118-131`），与官方"空列表不显示占位"不同，但这是产品文案选择（R3 已有），本轮未动。

---

## 9. 交接项（不在本 agent 写作范围，需 lead 转达）

| 对象 | 文件:行 | 内容 | 为什么必须别人改 |
|---|---|---|---|
| home-p0 | `AppComponents.kt:160` | `AppRow` KDoc 仍写"外层 Card(onClick, Sink) 承担点击时传 null"，与 R4 统一写法相反，建议改成"外层 Card 只做分组，点击交给 BasicComponent"。另：`selected` 已加，谢谢。 | `AppComponents.kt` 是 home-p0 写作范围 |
| home-p0 / lead | `HomeScreen.kt:586-604` | Home 搜索行是第三份行实现；建议改用 `com.ayang.glacier.feature.common.AppListCardRow`，实现 §7.8/§9 的"搜索行与列表行同一实现"。切换后 Home 搜索行会显示包名+徽标（与 Apps 一致）。 | HomeScreen 属 home-p0 |
| settings-micro | `ApiActivity.kt:78-101` | `AppActionSheet` 现在支持 `unsupportedActions`；ApiActivity 没有 EditTags / DeferredFreeze 的 processor，应传 `unsupportedActions = setOf(AppAction.EditTags, AppAction.DeferredFreeze)`，否则独立窗口仍有死行（§7.4）。默认值保持空集合，不改也不崩。 | ApiActivity 属 settings-micro |
| shell-nav | `PageFrame.kt:164-181,236-266` | **已落地，无待办**：`scrollBarAdapter` 可选参数 + 正文 wrapper 上的 `pageScrollModifiers`（haptic + overscroll + nestedScroll）已在工作区；Apps 页已按约定只传 `scrollBarAdapter`、不重复挂滚动链。仅需 shell 侧在最终汇报里确认该实现覆盖所有页面。 | PageFrame 属 shell-nav（已对齐） |
| lead | `HomeScreen.kt:879-897` | Home 的 `reportBatch` 建议抽到 `feature/common/AppActions.kt`，为 Apps 多选（§3 方案）铺路，同时去掉 Home/Apps 两处复制的可能。 | Home 属 home-p0；common 共享改动需 lead 协调 |
| lead | 产品决策 | Apps 单击=操作单、Home 单击=启动；Apps 无多选（§2/§3）。请确认这是 R4 接受的设计取舍，并写进最终未完成清单。 | 产品决策 |

---

## 10. 验证建议（lead 统一构建 / 截图，本 agent 不跑 gradle）

**编译风险点（必须构建确认）**

1. `AppActions.kt` 的可选行列表用 `listOfNotNull(...)`（label 在组合期用 `stringResource` 先算好，元素本身是纯数据 `SheetActionSpec`），不依赖 `buildList` 的 inline-lambda 组合约定，降低编译风险；若仍报 Composable 上下文错误，把两个 `if` 拆成显式 `val ... = if (...) ... else null` 即可（等价）。
2. `AppsScreen.kt` 使用 `RadioButtonPreference`（miuix-preference）与 `rememberLazyListState`：依赖版本已包含（`SettingsScreen` 已用 preference 组件）。
3. `PageFrame.scrollBarAdapter` 已落地并接线（`AppsScreen.kt` 的 `@OptIn(ExperimentalScrollBarApi::class)` + `rememberScrollBarAdapter(listState)`）；`PageFrame` 内部的统一滚动链（`pageScrollModifiers`）也已落地，本页未重复挂。
4. `AppActionSheet` 新参数有默认值，Home/ApiActivity 现有命名调用不受影响（已检查 3 处调用点）。

**交互检查点**

1. Apps 列表：单击任意行 → 操作单；按压看高亮是否**圆角**跟随卡片、无方形、无整卡缩放抖动；快速连点不重复弹层。
2. Apps 搜索结果：同一应用在列表与搜索中的图标/文字/徽标/按压反馈一致；点击打开操作单，关闭搜索后浮层动画完整。
3. 操作单：默认字体下行全可见；字体 2.0x + 横屏：可滚到最后一行（卸载/重新安装），脚注不被导航栏遮挡；上下滚动到边界有触觉；向上滑可带动弹层下拽关闭；无卡片外的直角高亮。
4. 排序：三行单选圆点 + 主色标题；点击有触觉；选中后弹层关闭，再开显示新选中；2.0x 下可滚。
5. 筛选：四个开关行在 Card 内；整行点击与开关点击都只切换一次；2.0x 下可滚。
6. Glass 开：弹层 backdrop 采样正常（R 层不含底栏的 L3 偏差是既有问题，不属本页回归）；Glass 关：弹层为不透明回退色，功能完整。
7. 深色/浅色下 MiuixIndication 叠层颜色、RadioButton 选中色、disabled 图标色正常。

---

## 11. 参考源码索引（本地）

| 主题 | 路径:行 |
|---|---|
| MiuixIndication 矩形叠层 | `miuix/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/utils/MiuixIndication.kt:129-135` |
| 主题注入 LocalIndication | `miuix/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/theme/MiuixTheme.kt:33,37,63,67` |
| Card 无交互版 / 交互版 / 默认值 | `miuix/miuix-ui/.../basic/Card.kt:50-67,87-154,187-212` |
| BasicComponent（行底座/点击/禁用色） | `miuix/miuix-ui/.../basic/Component.kt:124-285,287-330` |
| squircleSurface 裁切机制 | `miuix/miuix-squircle/.../squircle/SquircleBackground.kt:124-165,255-278` |
| 官方搜索行（Card + BasicComponent） | `miuix/example/shared/src/commonMain/kotlin/IconPage.kt:215-244,534-543` |
| 官方网格"先裁切再 clickable" | `miuix/example/shared/src/commonMain/kotlin/IconPage.kt:403-411` |
| Card Sink/Tilt 长按 holdDown 演示 | `miuix/example/shared/src/commonMain/kotlin/component/CardSection.kt:68-168` |
| BottomSheet 内容 = LazyColumn + haptic + overscroll | `miuix/example/shared/src/commonMain/kotlin/component/BottomSheetSection.kt:209-267` |
| 弹层高度约束 / nested scroll | `miuix/miuix-ui/.../layout/BottomSheetContentLayout.kt:486-574,653-700` |
| GlassBottomSheet 契约 | `miuix/miuix-glass/.../glass/GlassBottomSheet.kt:20-22,47` |
| RadioButtonPreference | `miuix/miuix-preference/.../preference/RadioButtonPreference.kt:50-143` |
| SwitchPreference | `miuix/miuix-preference/.../preference/SwitchPreference.kt:43-93` |
| 官方 pageScrollModifiers | `miuix/example/shared/src/commonMain/kotlin/utils/PageUtils.kt:168-183` |
| overscroll / scrollEndHaptic 组件 | `miuix/miuix-ui/.../utils/Overscroll.kt:48-55`、`utils/ScrollEndHaptic.kt:124-126` |
| 滚动条组件 | `miuix/miuix-ui/.../basic/ScrollBar.kt:136-159` |
| Glacier 官方契约摘要 | `Glacier/docs/reference/miuix-os4-contract.md` §G.1/G.2/G.5、§F.3、§J |
