# R10 · 主页管"用"、应用页管"挑"：两套操作单 + 应用页归属勾选

> 起因：用户指出主页和应用的职责要区分开——"主页冻结、应用页功能"，否则两页没有存在意义；
> 并给出两份菜单的确切内容（应用页 5 项、主页 10 项），同时要求应用页长按底部不再显示包名。
> 约束：UI 一律用 MIUIX OS4 / OS4 Glass 官方件，不自造。
> 验证：`emulator-5554`（1080×2400 @420dpi），截图见 `docs/verification/miuix-os4-r10/`。

---

## 1. 职责划分（这次改动的总纲）

| | 主页 | 应用页 |
|---|---|---|
| 定位 | 你挑出来的常用应用 | 全部应用的管理台 |
| 点 | 启动 | 移进 / 移出主页（行尾勾选框 = 是否在主页） |
| 长按 | 使用向菜单（启动/冻结/定时/置顶/白名单/标签/主屏幕/导包名/移出） | 管理向菜单（应用信息/导包名/提包） |
| 冻结 | **只在主页**：FAB 一键冻结、长按单应用、多选批量 | 不提供 |
| 装卸包 | 不提供 | 也**不提供**（用户定：这两个选项彻底去掉） |

两页唯一重叠的是「导出到剪切板」，因为两边都需要包名。

## 2. 应用页：从"多选列表"退回"挑应用"

- 点一行 → `AppActionRunner` 的 `AddToHome` / `RemoveFromHome`（同一个出口，提示语只有一份）；
  加入不提示（勾选出现即是反馈），移出给一句「已将 X 移出主页」防误点。
- 行尾用官方 `Checkbox`（`miuix-ui/.../basic/Checkbox.kt`，26dp）表达主页归属：
  选中 `On` / 未选 `Off`；`onClick = null`，点勾选框的位置等于点这一行（不套第二层热区）。
- **删除**：`selecting` / `selection` / `inSelection` / `BackHandler` / `applyBatch` /
  顶栏两态（`AnimatedTopBarActions`）/「已选 N 个」标题。顶栏恒为 搜索 / 排序 / 筛选。
- 搜索浮层里点一条也是"移进/移出主页"，且**不收起浮层**，可以连着挑几个；长按仍弹操作单
  （先收浮层，避免两层弹层叠着）。

净结果：应用页不再有冻结入口，也没有"点了只是选中、什么都不会发生"的中间态。

## 3. 两套菜单变成三份数据

`AppActionSheet` 原来靠 `inHome` + `unsupportedActions` 打补丁，现在改成
**调用方给清单**：`actions: List<AppAction>`，顺序即渲染顺序，渲染层只查表（图标/文案/语境）：

```kotlin
fun appsMenuActions(): List<AppAction>                    // 应用页：管理向 3 行
fun homeMenuActions(entry): List<AppAction>               // 主页：使用向 9/10 行
fun apiMenuActions(entry, inHome): List<AppAction>        // 深链入口：无页面级交互的那套
```

| 应用页（3） | 主页（未冻结 9 / 已冻结 10） |
|---|---|
| 应用信息 | 启动 |
| 导出到剪切板 | 冻结 ⇄ 解冻（语境行，位置固定） |
| 提取安装包 | 定时任务（打开 5/15/30/60 分钟选择） |
| | 置顶 ⇄ 取消置顶 |
| | 加入白名单 ⇄ 移出白名单 |
| | 标签分组 |
| | 添加到主屏幕（`ShortcutBridge.requestPin`，系统 Pin 请求） |
| | 导出到剪切板 |
| | 从主页移除 |
| | （仅已冻结）解冻并从主页移除 |

几个刻意的取舍：

- **包名脚注整段删除**（`item(key = "package-name")` + `rowInset`），包名改由「导出到剪切板」承担。
- **「解冻并从主页移除」只在已冻结时出现**：未冻结时它与「从主页移除」完全等价，两行并排只会让人犹豫。
- **卸载 / 重新安装彻底去掉**：`AppAction.Uninstall` / `Reinstall` 连同中英文案一并删除，
  `AppActionRunner` 的分支、菜单项、字符串都不再有它们（仓储与后端能力保留，UI 无入口）。
- **「定时任务」= 原来的「稍后冻结…」**：只改文案（含它打开的弹层标题），交互不变。
- **「标签」→「标签分组」**：`action_edit_tags` 中英同步改，避免和"标签编辑弹层"混淆。

## 4. 主页长按：从"进多选"改成"弹菜单"，多选按钮保留

- 长按 = 该应用的使用向菜单；菜单打开期间该图标保持按下态。
  按下态用的是官方 `HoldDownInteraction`（公开类型）：库里的 `HoldDownObserver` 是 `internal`，
  库外调不到，所以按官方实现逐行照抄成 `ui/components/HoldDownEffect.kt`（保留 Apache-2.0 头），
  叠层仍由主题里的 `MiuixIndication` 画（0.10），没有自绘高亮。
- 顶栏「多选」按钮保留，用途是"快速挑几个冻结的批量解冻"：进多选后可批量冻结/解冻/标签/更多/关闭。
- 多选态里长按退化成"切换勾选"（否则选中几个后长按突然弹菜单，手势自相矛盾）。
- 多选态的「更多」（恰好选 1 个）仍然弹同一个使用向菜单。

## 5. 代码改动清单

| 文件 | 改动 |
|---|---|
| `feature/common/AppActions.kt` | 重写：`AppAction` 枚举（去 `ToggleHome`/`Uninstall`/`Reinstall`，加 `AddToHome`/`RemoveFromHome`/`UnfreezeAndRemoveFromHome`/`CopyPackageName`/`PinShortcut`）；`AppActionRunner` 新增 4 个分支；三份菜单函数；`AppActionSheet(entry, show, actions, …)` 只渲染；删包名脚注 |
| `ui/components/HoldDownEffect.kt` | 新增：官方 `HoldDownInteraction` 的公开类型实现（`HoldDownObserver` 是 internal） |
| `feature/apps/AppsScreen.kt` | 删多选/计数/BackHandler/两态顶栏；点 = 移进移出主页；长按 = 管理向菜单 |
| `feature/home/HomeScreen.kt` | 长按 = 使用向菜单 + 按下态；多选保留（顶栏按钮进）；移出主页时清理多选里的成员 |
| `feature/common/AppComponents.kt` | `AppRow` 的 `selectionState` → `onHome: Boolean?`（勾选框 = 主页归属） |
| `feature/common/AppSearchResults.kt` | `selectionMode` → `homePicker`；`AppListCardRow` 同步 |
| `feature/api/ApiActivity.kt` | 改用 `apiMenuActions(entry, inHome)`，去掉 `inHome` / `unsupportedActions` 形参 |
| `res/values*/strings.xml` | 增「导出到剪切板」「解冻并从主页移除」「已复制包名」「已将 %s 移出主页」；改「添加桌面快捷方式」→「添加到主屏幕」、「稍后冻结…」→「定时任务」、「标签」→「标签分组」；删卸载/重新安装 |
| `app/src/test/.../AppMenusTest.kt` | 新增 4 个单测：两页菜单不串味、冻结/解冻语境行、解冻并移除只在冻结时出现、API 菜单按归属切换 |

## 6. 验证

- `assembleDebug` + `testDebugUnitTest` 通过（`AppMenusTest` 4 个用例）。
- 应用页：勾选/取消勾选即时生效，副标题「· 不在主页」随之增减，移出有 toast；
  长按弹 3 行菜单、底部没有包名小字（`02-apps-menu-manage.png`）。
- 主页：长按弹 9 行（未冻结，`03-home-menu-unfrozen.png`）；把官方示例包临时
  `pm disable-user` 模拟冻结后弹 10 行、第 2 行变「解冻」、末行是「解冻并从主页移除」
  （`04-home-menu-frozen.png`，验证后已 `pm enable` 恢复）。
- 主页多选：顶栏「多选」进入、勾选角标出现、「更多」弹同一份菜单（`05-home-multiselect.png`）。
- 玻璃关闭档：菜单为不透明回退，内容完整（`06-home-menu-noglass.png`）。
- 菜单链：主页 → 长按 → 「定时任务」能接着弹出 5/15/30/60 分钟的选择弹层（两张弹层先后出现，
  没有互相顶掉）；「标签分组」能弹标签名 Dialog；「添加到主屏幕」会拉起系统的
  "轻触并按住此微件即可将其拖拽到主屏幕上任意位置"确认框（`ShortcutBridge.requestPin`）。
- 应用页搜索浮层里点一条也是移进/移出主页，且**不收起浮层**，可以连着挑几个。
- 导出到剪切板 / 从主页移除 的 toast 见 `07-copy-toast.png`、`08-removed-from-home-toast.png`。

已知限制：冻结/解冻的真实行为依赖冻结后端，模拟器上没有可用后端，所以「解冻并从主页移除」
只验证了"菜单出现"与"解冻失败则不移除"的代码路径（`repository.setFrozen` 返回 false 时中止），
没有跑通真机成功路径。

---

## 7. 追加：多选不再换顶栏，FAB 成为唯一出口

用户反馈：点「多选」后顶栏不该变成一排批量按钮，那排按钮的代码整段去掉；进多选给一句
「点按多选」的提示；并追问"标签收起后，『已选 N 个』与下面图标的间距是否符合 OS4"。

### 改动

- **删除 `AnimatedTopBarActions` 整个组件**（`PageFrame.kt` 里连同 KDoc 一起删，约 2.0k 字符），
  以及它带来的 `AnimatedContent` / `SizeTransform` / `togetherWith` / `IntSize` / `GlassMotion` 导入。
  主页顶栏改成恒单态：搜索 / 多选 / 编辑标签，三个 `TopBarAction` 放在
  `Row(spacedBy(8.dp))` 里（8dp 是官方示例 `example/GlassPage.kt:363` 的值）。
- **多选按钮变成 toggle**：顶栏不再提供"关闭"，所以同一个按钮负责进出；进入时
  `UiFeedback.toast("点按多选")`（新字符串 `hint_tap_to_select`）。
- **FAB 成为多选唯一的执行出口**：顶栏那排批量按钮消失后，多选必须有个"执行"的地方。
  FAB 在多选态保持可见，作用范围 = 已选集合（空选则整页），语义沿用原来的
  "把范围内还没冻的冻上；范围全冻着就反过来解冻"，图标随 `willFreeze` 在 `QuietMode` / `UnlockOutline` 之间切。
  只有真正落地（`FreezeOutcome.Applied` 且 `count > 0`）才退出多选，权限失败保留选区。
- 多选态里长按仍等于切换勾选（否则选中几个后长按会突然弹菜单）。

### 间距实测（1080×2400 @420dpi，1dp = 2.625px）

| 状态 | 参照点 | 数值 |
|---|---|---|
| 多选态 | 「已选 N 个」文本盒底部 386 → 首行图标顶 397 | **11px ≈ 4.2dp** |
| 正常态 | 标签轨道底 569 → 首行图标顶 590（标签行自带 12dp 底边距） | 21px ≈ 8dp |
| 官方 example 大标题页 | 标题文本盒底部 364 → 内容起点 375（= 栏底） | **11px ≈ 4.2dp** |

结论：**几何上符合官方**——内容从顶栏底部开始（`contentPadding.top = innerPadding.top`，
`pageContentPadding` 不额外加任何值），标题文本盒到栏底那 11px 是顶栏自己的内边距，
和官方示例逐像素一致，没有自造数值。

观感上比正常态紧，原因是两段留白消失了：副标题（52px）+ 标签行（含 12dp 底边距）。
需要提醒的是，官方**网格页**（`IconPage`）在首行之前总有一个小节标题
（`SmallTitle`，`SmallTitleDefaults.InsideMargin` 纵向 8dp），而 Glacier 的主页网格没有小节语义，
所以首行离标题只剩那 11px。若要让"看起来"也贴近官方网格页，最小改动是给网格的
`contentPadding` 顶部补 8dp（= 官方 SmallTitle 的纵向内边距），只在标签行隐藏时或两态都加，
这一步属于视觉取舍，未擅自改动。

### 验证

`assembleDebug` + `testDebugUnitTest` 通过；实机确认：点「多选」→ 顶栏三个图标不变、标签行收起、
标题「已选 0 个」、toast「点按多选」、FAB 仍在；选中一项后 FAB 作用于所选（模拟器无冻结后端，
点了给「权限不足」且**保留**选区）；再点「多选」退出、标签行回来。
