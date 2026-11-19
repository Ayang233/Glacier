# 参考项目研究：Mishka / InstallerX-Revived 与 MIUIX 官方 Example

> 第二轮 MIUIX OS4 / OS4 Glass 整改 · 参考实现研究（task-2）
> 本文只做「事实提取 + 可复用模式判定」，**不修改任何源码**。

## 0. 范围、版本与证据约定

### 0.1 本次实际核对的仓库状态

| 仓库 | 分支 | HEAD（已用 `git log -1` 核对） | 许可证 |
|---|---|---|---|
| MIUIX（本地源码，最高优先级） | — | `58b0a3dd06f8ee83c1099a7ebee1183adce3f83c`（0.9.5） | Apache-2.0（`miuix/LICENSE:1-3`） |
| Mishka | `main` | `e5a4a410834ce085786a2f0d270c1a80d00944bf`（2026-11-18） | GPL-3.0（`upstream/Mishka/LICENSE:1-2`，`upstream/Mishka/README.md:113`） |
| InstallerX-Revived | `main` | `caccb787c1a4189da3f917eb990fb949305e3899`（2026-11-12） | GPL-3.0-only（`upstream/InstallerX/LICENSE:1-2`，逐文件 SPDX 头，如 `upstream/InstallerX/app/src/main/java/com/rosan/installer/ui/page/miuix/settings/preferred/about/MiuixAboutPage.kt:1`） |
| awesome-miuix | — | 仅用于筛选，未作为代码参考 | 清单仓库 |

### 0.2 证据约定

- 所有 `路径:行号` 均**相对工作区根** `C:\Users\10487\WorkBuddy\Hail`，且全部写成完整路径（可直接 `Test-Path` 校验）。
- 少数表格里为节省宽度使用短文件名（如 `HomeScreen.kt:335-341`），其完整路径在同节正文第一次出现时已给出；Glacier 侧文件的根目录统一是 `Glacier/app/src/main/kotlin/com/ayang/glacier/`。
- 行号来自本次实际读取的文件内容，未读过的文件不写行号。
- 引用 MIUIX 官方契约（组件默认值、官方页面骨架）时只给**判定所需的最少行号**；完整的官方契约表由同轮另一任务输出到 `Glacier/docs/reference/miuix-os4-contract.md`，本文不重复抄写。
- 标记约定：
  - `【官方·可直接照抄】`：MIUIX（Apache-2.0）源码 / `miuix/example` / 官方文档里的用法与数字，可直接搬进 Glacier 代码（建议在注释或 NOTICE 里注明来源）。
  - `【GPL·仅思路】`：Mishka / InstallerX 的 **GPL** 代码与文档。本文只提取尺寸、结构、交互流程与 API 组合方式；**不提供可粘贴的代码块**。
  - `【平台事实】`：Android 平台行为（Adaptive Icon 108/72dp 画布等），不属于任何项目版权。
  - `【Glacier 现状】`：Glacier 当前实现位置，用于整改对照。

### 0.3 一个必须先说清楚的前提

**Mishka 与 InstallerX-Revived 都没有「桌面式应用网格」页面**：

- Mishka 是代理客户端，应用只以**列表**出现（分应用代理页），见 `upstream/Mishka/app/src/main/kotlin/top/yukonga/mishka/ui/screen/settings/AppProxyScreen.kt:404-435`。
- InstallerX-Revived 是安装器，应用图标只出现在安装确认页 / 信息插槽等**单个应用**场景，见 `upstream/InstallerX/app/src/main/java/com/rosan/installer/ui/page/miuix/installer/components/common.kt:106-177`、`upstream/InstallerX/app/src/main/java/com/rosan/installer/ui/page/miuix/installer/sheetcontent/InstallConfirmContent.kt:59-70`。

因此本轮任务书要求 A 项里的「网格列数/间距/标签字号与截断」，**不能声称照搬 Mishka / InstallerX**；唯一有依据的参考是 MIUIX 官方 example 的图标网格（`miuix/example/shared/src/commonMain/kotlin/IconPage.kt:347-435`，Apache-2.0）与本任务书给出的尺寸要求。本文会把「有参考」与「无参考、需自研」分开写清楚。

---

## 1. 结论速览（Top 12）

| # | 结论 | 主要证据 | 类别 |
|---|---|---|---|
| 1 | Glacier 是 **GPL-3.0-or-later**，两个参考项目是 GPL-3.0，因此「GPL 代码一律不可复制」这个前提在法律上不成立；真正的约束是**优先用 Apache-2.0 的 MIUIX 官方实现**，GPL 项目只取思路，避免把整改变成一场许可证混用。 | `Glacier/NOTICE.md:7-10`、`Glacier/README.md:72-74` | 许可证 |
| 2 | About 页应**直接照抄 MIUIX 官方 example 的 `AboutPage.kt`**，而不是 Mishka / InstallerX：Mishka 与 InstallerX 的 About 页都是从这份官方示例演化来的（同一套 +40dp/+52dp/+126dp/35sp/14sp 数字）。 | `miuix/example/shared/src/commonMain/kotlin/AboutPage.kt:242-305,317-339,347-441`；`upstream/Mishka/app/src/main/kotlin/top/yukonga/mishka/ui/screen/settings/AboutScreen.kt:194-325`；`upstream/InstallerX/app/src/main/java/com/rosan/installer/ui/page/miuix/settings/preferred/about/MiuixAboutPage.kt:438-602` | 官方·可直接照抄 |
| 3 | 品牌区规范：**88dp 容器**、`graphicsLayer { shape = RoundedCornerShape(24.dp); clip = true }`、**白色底**（不随主题反转）、内部图标 **74dp**；应用名 **35sp Bold**；版本 **14sp** `onSurfaceVariantSummary`。Glacier 当前是 80dp 白底 + 56dp 图 + `title1`/`footnote1`，比例偏小。 | `miuix/example/shared/src/commonMain/kotlin/AboutPage.kt:242-305` | 官方·可直接照抄 |
| 4 | **弹层/抽屉绝不能用 `if (show)` 包裹**，必须保持组合、只切 `show`/`expanded`，否则离场动画被砍成硬切。Glacier 的搜索、标签编辑、操作面板、TagNameDialog、DeferredSheet 全部是 `if` 包裹，属系统性偏差。 | `miuix/docs/zh_CN/components/glass/glassbottomsheet.md:74,167`、`glassdialog.md:81`；Glacier `HomeScreen.kt:215-231,254-263,265-291,321-332` | 官方·可直接照抄 |
| 5 | `GlassSearchOverlay` 的「清空输入」与「关闭搜索」是**两个不同按钮、两个不同回调**：清空按钮在 `:445-456`（`contentDescription = "Clear"`），关闭按钮在 `:490-500`（`onClick = onDismissRequest`）。本任务书里的搜索 × BUG 必须按此语义核对，不能共用回调。 | `miuix/miuix-glass/src/commonMain/kotlin/top/yukonga/miuix/kmp/glass/GlassSearchOverlay.kt:445-456,490-500` | 官方·可直接照抄 |
| 6 | 搜索浮层必须**始终组合**并传 `expanded`，且应把官方参数 `topInsets` / `searchTopPadding` / `horizontalInsetsModifier` / `scrollMaterialThreshold` / `scrollOffset` 用起来，而不是在结果列表里手工累加状态栏高度。 | `miuix/example/shared/src/commonMain/kotlin/IconPage.kt:499-544`；Glacier `feature/common/AppSearchResults.kt:50-72` 为手工累加 | 官方·可直接照抄 |
| 7 | 底栏应 `modifier = Modifier.padding(horizontal = 24.dp)`（官方文档示例显式这么写），且 `defaultWindowInsetsPadding` 内部已处理导航栏/标题栏底部 inset（≤16dp 时给 20dp）。Glacier 底栏无水平内边距，且手工 `navReserve = Height + navBars + 32.dp` 比官方多留一截。 | `miuix/docs/zh_CN/components/glass/glassnavigationbar.md:39`、`GlassNavigationBar.kt:305-324`、`GlassNavigationBar.kt:116-157`；Glacier `GlacierShell.kt:147-149,224-231` | 官方·可直接照抄 |
| 8 | 宽屏侧栏的官方推荐姿势：父容器统一处理安全区、`defaultWindowInsetsPadding = false`、页面用 layout modifier 让出 `(Margin + ExpandedWidth) * progress`、顶栏 `titleAlpha = { railState.progress }`。Mishka 与官方文档完全一致。 | `miuix/docs/zh_CN/components/glass/glassnavigationrail.md:24-62,74`；Mishka `AppNavigation.kt:296-306` | 官方·可直接照抄 |
| 9 | 列表行一律 `BasicComponent`：最小行高 **56dp**、默认内边距 **16dp**、图文间距由组件内部决定；应用图标 40dp 直接放 `startAction`。Mishka 的应用列表就是「每行一张 Card + BasicComponent」，InstallerX 的分组设置就是「SmallTitle + 每节一张 Card」。 | `miuix/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/Component.kt:171,287-292`；`upstream/Mishka/app/src/main/kotlin/top/yukonga/mishka/ui/screen/settings/AppProxyScreen.kt:411-434`；`upstream/InstallerX/app/src/main/java/com/rosan/installer/ui/page/miuix/settings/preferred/MiuixPreferredPage.kt:244-334` | 官方 + GPL·仅思路 |
| 10 | 卡片外边距统一 **水平 12dp**，节内/节间用 **bottom 12dp**（官方 example 的 `CardItem` 默认 `outerHorizontalPadding = 12.dp`、`outerBottomPadding = 6/12dp`），不要用 `Arrangement.spacedBy()` 拼 Card。长分组应拆成逐行 lazy item（`groupedCardItems` 模式），短分组可整卡。 | `miuix/example/shared/src/commonMain/kotlin/component/GroupedCardItems.kt:23-89`；`miuix/example/shared/src/commonMain/kotlin/SettingsPage.kt:149-153`；Glacier `PageFrame.kt:51-60`（SectionGap=6dp 与官方 12/6 不一致） | 官方·可直接照抄 |
| 11 | 面板/弹层里的动作行不要手搓：`BasicComponent`（Glacier 已做，`AppComponents.kt:93-119`）是正确的；**列表页/搜索结果里的行却手搓了 `Row + clickable`**（`AppSearchResults.kt:76-108`），应改成 `BasicComponent` 以获得 56dp 行高、涟漪、无障碍与按压反馈。 | `upstream/Mishka/app/src/main/kotlin/top/yukonga/mishka/ui/screen/settings/AppProxyScreen.kt:416-433`（搜索浮层内容同样用 `BasicComponent`） | 官方 + GPL·仅思路 |
| 12 | 大字体适配有现成解法：底栏标签在 `fontScale ≥ 1.6` 时用 `LargeLabelSize`(16dp)，并按可用宽度**量测 + 省略号裁剪**；`fontScale` 判断与 `textMeasurer.measure` 组合可防溢出。 | `upstream/InstallerX/app/src/main/java/com/rosan/installer/ui/page/miuix/widgets/InstallerMiuixGlassNavigationBar.kt:32-118`（GPL·仅思路）；常量本身是官方 `GlassNavigationBar.kt:130-131` | GPL·仅思路 + 官方常量 |

---

## 2. 许可证核查与「可复制边界」

### 2.1 三份许可证的事实

| 项目 | 声明位置 | 结论 |
|---|---|---|
| MIUIX | `miuix/LICENSE:1-3`；每个 `.kt` 首行 `SPDX-License-Identifier: Apache-2.0`（如 `miuix/miuix-squircle/src/commonMain/kotlin/top/yukonga/miuix/kmp/squircle/SquircleBackground.kt:1-2`） | **Apache-2.0**。可自由复制、修改、商用，需保留版权/许可声明并标注修改。 |
| Mishka | `upstream/Mishka/LICENSE:1-2`（标准 GPLv3 全文）；`upstream/Mishka/README.md:111-113` 明确「以 GPL-3.0 许可证开源」 | **GPL-3.0**（README 与 LICENSE 都没有 "or later" 表述）。 |
| InstallerX-Revived | `upstream/InstallerX/LICENSE:1-2`；`upstream/InstallerX/docs/README.md:127-133`；598 个 `.kt` 带 `SPDX-License-Identifier: GPL-3.0-only` 头（如 `upstream/InstallerX/app/src/main/java/com/rosan/installer/ui/page/miuix/settings/preferred/about/MiuixAboutPage.kt:1`） | **GPL-3.0-only**（"only"，不可升版）。 |

### 2.2 Glacier 自身许可证（决定了「能不能复制」）

- `Glacier/NOTICE.md:7-10`：`either version 3 of the License, or (at your option) any later version` → **GPL-3.0-or-later**。
- `Glacier/README.md:72-74`：同样表述；`Glacier/LICENSE` 为 GPLv3 全文；`Glacier/app/src/main/assets/LICENSE.txt` 随包分发并在 Licenses 页渲染（`feature/about/LicensesScreen.kt:56-69`）。

由此得到**精确**的结论（不要照抄任务书里过简的说法）：

1. **法律层面**：GPL-3.0-or-later 的工程可以并入 GPL-3.0 / GPL-3.0-only 的代码。复制 InstallerX 或 Mishka 的代码**并不被 GPL 禁止**，前提是满足 GPL-3.0 义务：保留版权声明与许可文本、标明修改、提供对应源码、整体继续以 GPL 分发。
2. **但**：一旦并入 GPL-3.0-only 的代码，整个产物实际上**不能再声称 "or later"**（`GPL-3.0-only` 部分无法被升级），`NOTICE.md`/`README.md` 的 "or later" 表述需要修正为 GPL-3.0。这是一条**真实的合规风险**，不是理论问题。
3. **工程层面**：本轮整改的正确优先级是 MIUIX 官方（Apache-2.0）→ 官方 example → 参考项目思路。原因不是"GPL 禁止"，而是：官方实现有持续维护、API 与当前 HEAD 对齐、复制成本与版权归属成本都更低。

> **本项目采用的纪律（建议写进审计报告）**：
> - 可以直接搬的代码：只来自 **MIUIX 源码 / `miuix/example` / 官方文档**（Apache-2.0），并在 `NOTICE.md` 增加一行来源说明。
> - 参考项目（Mishka / InstallerX）：**只取尺寸、结构、交互顺序、API 组合方式**，代码自己按官方 API 写一遍。本文所有 GPL 证据都只描述"做了什么、多大、什么顺序"，**没有提供可粘贴实现**。
> - 若某天确实要复制 GPL 代码：必须同时 (a) `NOTICE.md` 增版权归属、(b) 该文件标注修改、(c) 复核整仓 "or later" 表述、(d) 验收记录写明来源。

### 2.3 哪些内容属于「通用尺寸/结构」，不受版权保护

以下内容在任何许可证下都只是**思想、事实、操作方法**，可以自由采用（本文大量引用这类数字）：

- 具体的 **dp / sp 数值**：12dp 卡片外边距、16dp 行内边距、56dp 行高、54dp 底栏高度、250dp 侧栏宽度、24dp 底栏水平留白、88dp 品牌区、24dp 品牌圆角、40dp 列表图标、35sp 应用名、14sp 版本号。
- **布局结构与信息架构**：About = 品牌区 + SmallTitle + 信息卡；设置 = SmallTitle + Card；搜索 = 覆盖式浮层 + 结果列表。
- **交互流程**：长按进入多选、点击行打开 BottomSheet、返回键先退多选/先回首页 Tab。
- **组件 API 组合方式**：`BasicComponent` + `Card` + `SmallTitle`、`GlassNavigationRail` + `state` + `progress`。
- **颜色语义 token 名**：`onSurfaceVariantSummary`、`surfaceContainer` 等（MIUIX 自身 Apache-2.0，更无争议）。

**受版权保护的**：Kotlin 代码的具体表达（函数实现、自定义 `Layout`、shader 源码、注释文本）、文档正文。因此 Clash 项目的 `docs/ui-guidelines.md` 与 `AGENTS.md` 的**文字不能整段抄进** Glacier 文档；本文只做转述与数字引用。

### 2.4 逐文件证据索引（许可证）

- `upstream/Mishka/LICENSE:1-2`、`upstream/Mishka/README.md:111-113`
- `upstream/Mishka/scripta/LICENSE:1-3`（Apache-2.0，为其子构建）、`upstream/Mishka/mihomo/LICENSE:1-3`（GPL-3.0，native 内核）
- `upstream/InstallerX/LICENSE:1-2`、`upstream/InstallerX/docs/README.md:127-133`、`upstream/InstallerX/app/src/main/java/com/rosan/installer/ui/page/miuix/settings/preferred/about/MiuixAboutPage.kt:1-2`
- `miuix/LICENSE:1-3`、`miuix/miuix-glass/src/commonMain/kotlin/top/yukonga/miuix/kmp/glass/GlassNavigationBar.kt:1-2`
- `Glacier/LICENSE:1-2`、`Glacier/NOTICE.md:7-10`、`Glacier/README.md:72-74`、`Glacier/app/src/main/assets/LICENSE.txt`

---

## 3. A. 主页 / 应用网格 / App 图标

### 3.1 结论先行

| 子项 | 有无成熟参考 | 判定 |
|---|---|---|
| 网格列数/间距/标签 | 无（两项目都没有网格）；只有 MIUIX 官方图标网格 | 参考官方 example 公式，**改造** Glacier 固定 4/5 列 |
| 图标 squircle 圆角矩形 | 两项目都**没有**用 squircle 裁应用图标 | **保留 Glacier 现有自研**（官方 squircle modifier 是唯一正确底座） |
| 自适应图标前后景/安全区 | 两项目都直接吃系统图标 | **保留 Glacier 现有自研**（这是两者都没有解决的问题） |
| 列表/搜索里的图标 40dp | Mishka | 照搬尺寸 |
| 冻结状态表现 | 两项目都没有 | 自研，按官方排版 token |
| 选中态 | 无（Mishka 用 Checkbox） | 保留 Glacier 的 squircleBorder 方案 |

### 3.2 Glacier 现状（对照基线）

- 图标轮廓：`Glacier/app/src/main/kotlin/com/ayang/glacier/feature/common/AppComponents.kt:24-25`（`AppIconCornerRatio = 0.22f`）、`:35-85`（`squircleClip(radius)` + 选中 `squircleBorder`）。
- 图标来源与自适应合成：`Glacier/app/src/main/kotlin/com/ayang/glacier/platform/AppIcons.kt:60-93`（自己把 `AdaptiveIconDrawable` 的 background/foreground 铺满画布 `renderUnmasked`）、`:157-178`（`produceState` + IO + LRU）。
- 网格：`Glacier/app/src/main/kotlin/com/ayang/glacier/feature/home/HomeScreen.kt:335-341`（`GridCells.Fixed(if (preferences.compactGrid) 5 else 4)`、`horizontalArrangement = spacedBy(6.dp)`、`verticalArrangement = spacedBy(18.dp)`、`contentPadding horizontal 16.dp`）、`:378-412`（`HomeAppItem`：56dp 图标、6dp 间隔、`footnote1` + 可变 fontSize、`maxLines = 1` + Ellipsis、冻结时 `onSurfaceVariantSummary`）。

### 3.3 参考项目做法

**（1）MIUIX 官方图标网格（唯一有效的网格参考）**【官方·可直接照抄】

- 列数按可用宽度计算，不写死：`miuix/example/shared/src/commonMain/kotlin/IconPage.kt:347-350`
  ```
  val columns = remember(maxWidth) { ((maxWidth - 24.dp) / 80.dp).toInt().coerceIn(3, 8) }
  ```
- 每个「网格行」用一个整体容器：外层 `padding(horizontal = 12.dp)` → `squircleClip(首行 16/16/0/0 或末行 0/0/16/16)` → `background(surfaceContainer)` → 内层 `padding(horizontal = 6.dp)`、`padding(top = if (isFirst) 8.dp else 2.dp, bottom = ...)`：`IconPage.kt:383-398`。
- 单元格：`weight(1f)` + `squircleClip(12.dp)` + `clickable` + `padding(horizontal = 4.dp, vertical = 10.dp)`，图标 **24/28dp**，标签 `footnote2`、居中、`maxLines = 1`、Ellipsis，图标与标签间距 **6dp**：`IconPage.kt:400-432`。
- 该页有 `VerticalScrollBar`（`IconPage.kt:439-443`），Glacier 未使用，可作为可选增强。

**（2）Mishka 的应用图标**【GPL·仅思路 + 平台事实】

- 组件：`upstream/Mishka/app/src/main/kotlin/top/yukonga/mishka/ui/platform/AppIcon.kt:25-62`。默认 `size = 40.dp`（`:29`），**只画 `Image`，不做任何裁切**（`:64-71`）；占位是 `squircleBackground(secondaryContainer, 8.dp)`（`:72-78`）。
- 加载：`upstream/Mishka/app/src/main/kotlin/top/yukonga/mishka/util/AppIconCache.kt:27-57`，`getApplicationIcon(packageName).toBitmap(sizePx, sizePx)`（`:40-41`），转 `Bitmap.Config.HARDWARE`（`:43-49`），LRU 容量 `maxMemory/8`（`:14`），并发信号量 4（`:15`）。
- **关键判断（不要照抄）**：`getApplicationIcon()` 返回的 `AdaptiveIconDrawable` 在 `toBitmap()` 时会走**系统遮罩**；AOSP 默认遮罩是圆形，HyperOS 是圆角矩形。Mishka 又在外面不做任何裁切，所以它的图标形状**完全由 ROM 决定**。这正是 Glacier 上一轮「图标是圆的」现象的成因，不能用 Mishka 论证图标方案。
- Mishka 的 `Crossfade 150ms` 图标淡入（`AppIcon.kt:55-60`）是可借鉴的小细节（缓存命中时不淡入，`:51-53`）。

**（3）InstallerX 的应用图标**【GPL·仅思路 + 平台事实】

- 加载：`upstream/InstallerX/app/src/main/java/com/rosan/installer/data/engine/repository/AppIconCache.kt:79-92`，`me.zhanghai.android.appiconloader.AppIconLoader(size, shrink = icon is AdaptiveIconDrawable, context)`（`:83-89`）——和 Glacier 用的是同一个库、同一种「是否自适应图标」判断，可互相印证选型正确。
- 展示尺寸与圆角（**这是两份项目里唯一可用的"图标圆角比例"证据**）：
  - 72dp 图标配 `RoundedCornerShape(16.dp)`：`upstream/InstallerX/app/src/main/java/com/rosan/installer/ui/page/miuix/installer/components/common.kt:122-128` → 比例 16/72 ≈ **0.222**；
  - 86dp 图标配 `RoundedCornerShape(20.dp)`：`upstream/InstallerX/app/src/main/java/com/rosan/installer/ui/page/miuix/installer/sheetcontent/InstallConfirmContent.kt:59-66` → 比例 20/86 ≈ **0.233**；
  - 两者都用 `RoundedCornerShape` 而非 squircle——说明 InstallerX 只做到「圆角矩形」，没做 MIUIX 连续曲率。
- `InstallConfirmContent.kt:53-83` 的「图标 86dp → 16dp 间距 → 名称 title3 Bold 居中 → horizontal 24dp」是很好的**单应用大图标区**结构（可用于 Glacier 的操作面板/关于页某类区块）。

### 3.4 建议（照搬 / 改造 / 保留）

| Glacier 位置 | 参考 | 建议 | 理由 |
|---|---|---|---|
| `HomeScreen.kt:336` 固定 4/5 列 | `IconPage.kt:347-350` | **改造**：列数 = `((maxWidth - 24.dp) / 80.dp).toInt().coerceIn(3, 8)`（或按图标 56dp + 最小间距推导 72–80dp 的单元宽），宽屏不再硬撑 4 列 | 固定列数在平板/横屏下单元被拉宽、留白失控；官方 example 已给公式 |
| `HomeScreen.kt:338-340` 6dp/18dp 间距 | `IconPage.kt:394-398` | **改造**：水平与垂直间距由同一套 token 推导（如水平 6dp、垂直 18dp 可保留，但要在文档中写明来源），并保证「网格与 Apps 列表图标尺寸一致」 | 任务书 6.2 硬要求 |
| `AppIconView` 圆角比例 0.22 | `common.kt:122-128`、`InstallConfirmContent.kt:59-66` | **保留** 0.22f（与两者 0.222/0.233 吻合），但改为**由 `SquircleDefaults` 体系**驱动（`squircleClip`，`SquircleBackground.kt:96-99`），并在 `LocalSquircleEnabled=false` 时优雅退回 `RoundedCornerShape`（库已内建） | 官方 squircle 是唯一 MIUIX 正解 |
| `AppIcons.kt:68-93` 自适应图标铺满 + UI 层裁 | 无参考（两项目都未解决） | **保留自研**，并在文档里写明 108dp 画布 / 72dp 安全区依据（`AppIcons.kt:60-67` 注释已有），补一条：**图标包（icon pack）来源也要走同一轮廓**，否则图标包图标会变成方形/异形 | 任务书 6.2 明确要求「不许忽略前后景/安全区」 |
| `HomeAppItem` 56dp 图标 + 6dp + `footnote1` | 官方排版 token；Mishka 40dp（列表） | **保留 56dp**（网格）与 **40dp**（列表/搜索），两者不要混用；标签保持 `maxLines=1 + Ellipsis`，字号用 `MiuixTheme.textStyles` 派生而不是裸 `sp`（当前 `fontSize = labelSize.sp` 同时叠加 `footnote1` 的 lineHeight，见 `HomeScreen.kt:401-410`） | 一致性 + 大字体 |
| 选中态 `squircleBorder(2.dp, primary)` | `miuix/miuix-squircle/src/commonMain/kotlin/top/yukonga/miuix/kmp/squircle/SquircleBorder.kt:30-65` | **保留**（这是官方 API 的正确用法），但把 `radius + 2.dp` 与 `padding(2.dp)` 的魔数写进 `PageMetrics`/常量 | 减少硬编码 |
| 冻结状态 | 无参考 | **自研**：网格=去色（已有 `grayscaleFrozenIcons`，`AppIcons.kt:111-118`）+ 标签 `onSurfaceVariantSummary`（已有）；列表=尾部文字徽标（已有 `AppsScreen.kt:194-202`）。**注意**：两处表达必须同语义（去色是可选项，不能一处去色一处不去） | 两项目无此需求，只能自洽 |

### 3.5 许可证风险

- 网格公式、间距数字、圆角比例：**通用尺寸/结构**，无风险。
- `AppIcon.kt` / `AppIconCache.kt` / `InstallConfirmContent.kt` 的**具体实现代码**是 GPL；本文只给了尺寸与 API 选型，**不要粘贴**。
- 唯一建议直接照抄的是 `IconPage.kt` 的网格公式（Apache-2.0），需在 `NOTICE.md` 注明「网格列数公式参考 MIUIX example」。

---

## 4. B. 应用列表页（Apps）

### 4.1 Glacier 现状

- 列表：`Glacier/app/src/main/kotlin/com/ayang/glacier/feature/apps/AppsScreen.kt:149-167`（`LazyColumn` + `spacedBy(8.dp)` + `horizontal = 16.dp`）。
- 行：`:170-205` 每行一张 `Card`（`insideMargin = PaddingValues(0.dp)`、`pressFeedbackType = Sink`）+ `AppRow`（`AppComponents.kt:122-139`：`BasicComponent` + 40dp 图标 + summary 拼接包名/徽标 + 尾部「已冻结」`Text`）。
- 排序/筛选弹层：`:207-241`（`SheetActionRow` 用勾选图标）、`:244-273`（`SwitchPreference`）。
- 搜索：`:96-112` 打开 `GlassSearchOverlay`，结果见 `AppSearchResults.kt:76-108`（**手搓 `Row + clickable`**）。

### 4.2 参考项目做法

**（1）Mishka 的应用列表行**【GPL·仅思路】

- 结构：每行一张 Card，`padding(horizontal = 12.dp).padding(bottom = 12.dp)`；卡内一个 `BasicComponent`：`title = appName`、`summary = packageName`、`startAction` 里 40dp `AppIcon` + `padding(end = 6.dp)`、`endActions` 是 `Checkbox`、整行 `onClick` 也切换选中：`upstream/Mishka/app/src/main/kotlin/top/yukonga/mishka/ui/screen/settings/AppProxyScreen.kt:404-435`。
- 列表容器：`LazyColumn` 依次挂 `layerBackdrop` → `horizontalCutoutPadding()` → `scrollEndHaptic()` → `overScrollVertical` → `nestedScroll`，`contentPadding` **只给 top**：`AppProxyScreen.kt:154-165`。
- 计数标题：`SmallTitle(text = "应用列表（已选/总数）")`：`AppProxyScreen.kt:210-221`。
- 尾部徽标：`Badge` 用 `squircleBackground(..., 3.dp)` + `9.sp Bold Monospace`（项目自己的 UI 规范，`upstream/Mishka/docs/ui-guidelines.md:6`）；官方示例里的 Badge 是计数语义（`miuix/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/Badge.kt:180-199`：无内容 6dp、有内容 16dp、error 色），**不适合当状态文字徽标**。
- 多选：行内 Checkbox + 顶部 `More` 菜单里的「全选/取消全选/反选/显示系统应用/导入/导出」：`AppProxyScreen.kt:319-400`。

**（2）InstallerX 的列表行与分组**【GPL·仅思路】

- 通用行封装：`MiuixNavigationItemWidget` = `ArrowPreference(title, summary, insideMargin = BasicComponentDefaults.InsideMargin)`：`upstream/InstallerX/app/src/main/java/com/rosan/installer/ui/page/miuix/widgets/MiuixNavigationItemWidget.kt:20-35`。
- 带描述的多选行：`MiuixMultiApkCheckboxWidget` 给出 64dp 最小行高、16dp 四边内边距、文字与勾选框 16dp 间距、标题/副标题 2dp 间距、`Role.Checkbox`、禁用时 `alpha 0.4f`：`upstream/InstallerX/app/src/main/java/com/rosan/installer/ui/page/miuix/widgets/MiuixBaseWidget.kt:101-174`。
- 状态徽标：`MiuixBadge` = `Surface(CircleShape)` + `defaultMinSize(minHeight = 24.dp)` + `padding(horizontal = 8.dp)` + `12.sp SemiBold` + 关闭 font padding：`upstream/InstallerX/app/src/main/java/com/rosan/installer/ui/page/miuix/widgets/MiuixBadge.kt:44-75`。**这是两项目里最适合「已冻结 / 置顶 / 白名单」尾部徽标的形态**。
- 每行一张卡 + 40dp 图标也可印证「一卡一行」在正文列表里是 MIUIX 常见形态（`LibContainer` 的 LibraryCard：`upstream/InstallerX/app/src/main/java/com/rosan/installer/ui/page/miuix/settings/preferred/about/ossLicensePage/LibrariesContainer.kt:162-224`）。

**（3）每行一卡 vs 分组卡（判定）**

| 场景 | 推荐形态 | 证据 |
|---|---|---|
| 长列表（Apps、搜索结果、许可证库） | **每行一张 Card**（或列表行不带 Card、只带分隔） | Mishka `AppProxyScreen.kt:411-415`；InstallerX `LibrariesContainer.kt:163-169` |
| 短设置分组（3–8 行） | **SmallTitle + 一张 Card 内多行** | InstallerX `MiuixPreferredPage.kt:244-334`；Glacier 现状（`SettingsScreen.kt:117-149`）已符合 |
| 超长设置分组（>10 行） | 逐行 lazy item 拼接（`groupedCardItems`） | 官方 `miuix/example/shared/src/commonMain/kotlin/component/GroupedCardItems.kt:66-89`；Mishka `SettingsScreen.kt:128-238` |

### 4.3 建议

| Glacier 位置 | 参考 | 建议 |
|---|---|---|
| `AppsScreen.kt:153-157` `spacedBy(8.dp)` + `horizontal 16.dp` | `PageMetrics.Horizontal=12.dp`（`PageFrame.kt:51-60`）、Mishka 12/12 | **改造**：水平改 12dp，行间距改由每行 `padding(bottom = 12.dp)` 承担（与 About/Settings 统一），去掉 `spacedBy` |
| `AppsScreen.kt:184-203` 行内 summary 拼接 | Mishka `AppProxyScreen.kt:416-433` | **保留**（结构正确），但把「包名 · 徽标」的拼法统一到一处；「已冻结」改用 `MiuixBadge` 形态（自己按 `Surface` 写，勿复制 InstallerX 代码）或保持 `footnote2 primary` 但两处一致 |
| `AppSearchResults.kt:76-108` 手搓 Row | Mishka 搜索浮层同样用 `BasicComponent`（`AppProxyScreen.kt:296-308` 复用 `AppItem`） | **改造**：换成 `AppRow`/`BasicComponent`，自动获得 56dp 行高 + 涟漪 + 无障碍 |
| `AppsScreen.kt:207-241` 排序弹层「选中行带图标、未选中行不带图标」 | 官方 `BasicComponent` 行为 | **改造**：两种行视觉高度不一致（`SheetActionRow` 有 24dp 图标、`BasicComponent` 无），应统一为「都带图标（未选中用透明占位）」或「都不带图标 + 尾部 Checkmark」，避免行高跳动 |
| `AppsScreen.kt:244-273` 筛选用 `SwitchPreference` | 官方 `CheckboxPreference`/`RadioButtonPreference` | **可选**：多选语义用 Checkbox 更符合 MIUIX（Mishka 筛选用 `RadioButtonPreference`，`AppProxyScreen.kt:172-192`） |
| 多选模式 | Mishka 行内 Checkbox + 顶部菜单批量 | **改造**：Apps 页若要多选，行尾用 `Checkbox` + `BasicComponent` 组合；不要只靠长按隐式进入 |

### 4.4 许可证风险

- 行结构、40dp 图标、12dp 外边距、Badge 尺寸：**通用结构/尺寸**，无风险。
- `MiuixMultiApkCheckboxWidget`、`MiuixBadge` 的具体实现是 GPL，「24dp + 8dp + 12sp」这类数值可自由采用，**代码自行按 `Surface`/`BasicComponent` 写**。

---

## 5. C. 设置页

### 5.1 Glacier 现状

- 结构：`Glacier/app/src/main/kotlin/com/ayang/glacier/feature/settings/SettingsScreen.kt:107-398`，5 个 `SmallTitle + GroupCard` 节（通用/外观/搜索/自动化/系统/关于），`verticalArrangement = spacedBy(PageMetrics.SectionGap = 6.dp)`（`PageFrame.kt:56`）。
- 控件：`GlassDropdownPreference`（`:120-137,154-163,173-184,311-330`）、`SwitchPreference`、`SliderPreference`、`ArrowPreference`。
- 弹层：筛选用 `GlassBottomSheet`（`AppsScreen.kt:248-273`）。

### 5.2 参考项目做法

**（1）Mishka 设置页** 【GPL·仅思路】

- 容器：`Scaffold(topBar = BlurredBar { AdaptiveTopAppBar(...) })`；`LazyColumn` 挂 `layerBackdrop → horizontalCutoutPadding → scrollEndHaptic → overScrollVertical → nestedScroll`，`contentPadding = PaddingValues(top = 顶栏, bottom = bottomPadding)`：`upstream/Mishka/app/src/main/kotlin/top/yukonga/mishka/ui/screen/settings/SettingsScreen.kt:101-124`。
- 分组：`item { SmallTitle(...) }` + `groupedCardItems(keyPrefix, items = buildList { add(CardItem("key") { ... }) })`：`SettingsScreen.kt:125-238`。
- 分段标题文案全部 `stringResource`，选项用 `GlassDropdownPreference(title, items, selectedIndex, onSelectedIndexChange, backdrop = LocalPageBackdrop.current, enabled)`：`SettingsScreen.kt:132-154`。
- 间隔：每个 `CardItem` 之间**不加 spacing**（拼接成一张视觉卡），分组之间由 `SmallTitle` 自带上下 8dp（官方 `SmallTitleDefaults.InsideMargin = PaddingValues(28.dp, 8.dp)`，`miuix/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/SmallTitle.kt:40-42`）。
- 弹层/下拉为离散选项时，MIUIX 推荐 `GlassDropdownPreference` + 根 Scaffold 弹层宿主；**不能表示**（带图标/带 summary/多级）时退回 `WindowSpinnerPreference`：InstallerX 的判定函数 `InstallerMiuixComponents.kt:252-300`（`plainOptions` 判断 + 两条分支）**这个判定逻辑非常值得照搬思路**。

**（2）InstallerX 设置页** 【GPL·仅思路】

- 页面容器：`LazyColumn` 挂 `scrollEndHaptic → overScrollVertical → nestedScroll`，`contentPadding` 用 `innerPadding` 的 start/top/end + `outerPadding` 的 bottom，`overscrollEffect = null`：`upstream/InstallerX/app/src/main/java/com/rosan/installer/ui/page/miuix/settings/preferred/MiuixPreferredPage.kt:230-243`。
- 首个 item 固定 `Spacer(12.dp)`：`MiuixPreferredPage.kt:244`。
- 每节：`SmallTitle(...)` + `Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp))` 内放多个行：`MiuixPreferredPage.kt:245-277,289-334`。
- 行：`BasicComponent(title, summary, onClick)` + `MiuixSwitchWidget` / `MiuixNavigationItemWidget`（`ArrowPreference`）。
- 设置项「当前值」展示：`BasicComponent` 的 `endActions` 放 `Text(fontSize = body2.fontSize, color = onSurfaceVariantActions)`（官方 AboutPage 同款，`miuix/example/shared/src/commonMain/kotlin/AboutPage.kt:374-382`）。
- 需要 glass 顶栏时用 `InstallerMiuixProgressiveAppBarSurface`：标题栏区域单独做「band 高度 = (statusBar + CollapsedHeight + 20dp) × 1.2」的渐进模糊，且**顶栏内容自身不因列表是否能滚动而消失**：`InstallerMiuixComponents.kt:168-209`。

### 5.3 建议

| Glacier 位置 | 参考 | 建议 |
|---|---|---|
| `PageFrame.kt:56` `SectionGap = 6.dp` 用于节间距 | 官方 `SmallTitle` 自带 8dp 上下 + Card bottom 12dp | **改造**：去掉节间 `spacedBy`，改为「SmallTitle 自带 8dp + 每个 GroupCard `padding(bottom = 12.dp)`」，与 About/Apps 统一 |
| `GroupCard` 内多行（`PageFrame.kt:288-300`） | InstallerX 每节一卡（短分组）+ 官方 `groupedCardItems`（长分组） | **保留**（设置页最长节 8 行，整卡可接受）；自动化那节若继续变长再拆 `groupedCardItems` |
| `SettingsScreen.kt:215-226` `SliderPreference` 的 `valueRange` / `steps` 与 `valueText` | 官方 `SliderPreference` 参数 | **保留**，但 `valueText` 应走 `stringResource`（当前 `"${labelSize.toInt()} sp"` 为硬编码拼接，`SettingsScreen.kt:223`） |
| 所有 `GlassDropdownPreference(backdrop = backdrop)` | InstallerX 判定函数 + 官方 `GlassDropdownPreference` | **保留**；补一条规则：选项带图标/多级时退回非玻璃 `SpinnerPreference`（与官方宿主能力对齐） |
| 设置行同控件跨页尺寸 | InstallerX/Mishka 单一 `BasicComponentDefaults.InsideMargin` | **改造**：Glacier 不应在设置行外层再加 `padding`；`BasicComponent` 自己 16dp（`Component.kt:287-292`） |
| 深/浅色 | 全部走 `MiuixTheme.colorScheme.*`（Mishka UI 规范 `docs/ui-guidelines.md:25` 明确禁止散落 `Color(0xFF...)`） | **保持** Glacier 现状（`GlacierTheme.kt:17-30` 单一主题入口），但与 `Color.White`（About 品牌区）这类**故意固定色**要在注释中标注原因 |

### 5.4 许可证风险

- 分组结构、12dp、SmallTitle 用法：通用结构。
- `InstallerMiuixSpinnerPreference` 的**判定函数**属可复用的「逻辑思路」，实现需自写。

---

## 6. D. About 页（可直接照抄的完整结构）

> **本节是全篇复用价值最高的部分。** 结论：**照抄 `miuix/example/shared/src/commonMain/kotlin/AboutPage.kt`（Apache-2.0）**；Mishka 与 InstallerX 的 About 页与它同源（数字几乎逐一对应），只是各自加了毛玻璃与主题适配。

### 6.1 三份实现的同源证据（数字对齐）

| 结构元素 | MIUIX 官方 `AboutPage.kt` | Mishka `AboutScreen.kt` | InstallerX `MiuixAboutPage.kt` |
|---|---|---|---|
| 品牌容器 | `size(88.dp)`，`graphicsLayer { clip = true; shape = RoundedCornerShape(24.dp) }`，`background(Color.White)` | `size(100.dp)` + `clipToBounds` | `size(88.dp)` + `RoundedCornerShape(16.dp)` 仅用于 textureBlur |
| 容器内图标 | `Image(Modifier.size(74.dp))` | `requiredSize(250.dp)`（美术字 logo 更大） | `requiredSize(160.dp)` / 无 backdrop 时 `size(80.dp)` |
| 品牌区顶部留白 | `logoPadding = pageContentPadding(extraTop = 40.dp)`，再 `+52.dp` → `:157,182` | `top = +40.dp`，再 `+52.dp` → `:199-203,218` | `top = +40.dp`，再 `+52.dp` → `:449-453,468` |
| 应用名 | `35.sp` / `FontWeight.Bold` / `padding(top = 12.dp, bottom = 5.dp)` | 同（`:280-284,261`） | 同（`:537-540,515`） |
| 版本行 | `14.sp` / `onSurfaceVariantSummary` / `TextAlign.Center` | 同（`:294-297`） | 同（`:553-557`） |
| 透明占位 | `item(key = "logoSpacer")`，`height = logoHeightDp + 52.dp + logoPadding.top - scrollPadding.top + 126.dp` | 同（`:315-325`） | 同（`:582-601`） |
| 内容卡 | `Card(Modifier.padding(horizontal = 12.dp))`，节间 `padding(top = 12.dp)`，末 `Spacer(12.dp)` | `Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp))` | `Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp))` |
| 行的组件 | `ArrowPreference(title, endActions = { Text(value, body2, onSurfaceVariantActions) }, onClick)` | `ArrowPreference(title, summary = url, onClick)` | `MiuixNavigationItemWidget` = `ArrowPreference(title, summary, onClick)` |

**结论**：官方 example 是「About 页结构」的唯一权威源，且是三份实现里唯一 Apache-2.0 的。Glacier 应照它重写，而不是照 Mishka 的毛玻璃版本（那版还叠加了 `textureBlur` 与 `BgEffect`，属于效果层，Glacier 若不做动态背景就不需要）。

### 6.2 官方 About 页的完整结构（可逐段落地）

证据：`miuix/example/shared/src/commonMain/kotlin/AboutPage.kt`

1. **滚动进度**（帧率级 State 留在 `State` 里，不 `by` 解包）：
   `:94-109` `scrollProgress = derivedStateOf { firstVisibleItemIndex > 0 -> 1f; else firstVisibleItemOffset / logoSpacer.height }`。
2. **翻页顶栏**：
   `:117-127` `collapsed = derivedStateOf { scrollProgress == 1f }`、`titleAlpha = ((scrollProgress - 0.35f) / 0.65f).coerceIn(0f,1f)`；
   `:135-149` `AdaptiveTopAppBar(expandable = false, titleColor, titleAlpha, navigationIcon = BackNavigationIcon)`。
3. **页面骨架**：`Scaffold { innerPadding -> Box(layerBackdrop) { AboutContent(...) } }`（`:120-164`）。
4. **品牌区**（`:233-305`）：
   - `Column(fillMaxWidth().padding(logoContentPadding = 顶栏 + 40dp + 52dp).onSizeChanged { logoHeightDp })`，`horizontalAlignment = CenterHorizontally`；
   - 根 `Box(size(88.dp).graphicsLayer { clip = true; shape = RoundedCornerShape(24.dp); alpha/scale 随 0.35/0.15 进度 })` + `.background(Color.White)`；
   - `Image(size(74.dp))`；
   - 应用名 `Text(35.sp, Bold, padding(top = 12.dp, bottom = 5.dp), 进度 0.20/0.15)`；
   - 版本 `Text(fillMaxWidth, 14.sp, Center, onSurfaceVariantSummary, 进度 0.05/0.15)`。
5. **内容列表**（`:308-446`）：
   - `item("logoSpacer")` 透明占位（高度公式见上）；
   - `item("about")` 内 `Spacer(fillParentMaxHeight())` + `Column(padding(bottom = scrollPadding.bottom))`；
   - Card 1：`ArrowPreference` ×2（「View Source」+ 尾部 `Text("GitHub")`；「Join Group」+ `Text("Telegram")`）；
   - Card 2（`padding(top = 12.dp)`）：「License」+ 尾部 `Text("Apache-2.0")`；「Third Party Licenses」→ `navigator.push(Route.License)`；
   - 末 `Spacer(12.dp)`。
6. **右侧滚动条**：`:447-451` `VerticalScrollBar(adapter = rememberScrollBarAdapter(lazyListState), Modifier.align(CenterEnd).fillMaxHeight(), trackPadding = scrollPadding)`。
7. **可选**：背景效果 BottomSheet（`:456-495`），用 `GlassBottomSheet(insideMargin = DpSize(0.dp, 0.dp))` + `GlassDropdownPreference` + `SwitchPreference`，末 `Spacer(navigationBars.asPaddingValues().bottom)`。

### 6.3 Glacier 现状差距（逐项）

`Glacier/app/src/main/kotlin/com/ayang/glacier/feature/about/AboutScreen.kt`

| 项目 | 现状 | 官方结构 | 判定 |
|---|---|---|---|
| 品牌区 | `:150-182` `Column(top = 16.dp, bottom = 8.dp)`、`Box(size(80.dp).squircleBackground(Color.White, 18.dp))` + `Image(56.dp)`、名称 `title1`、副标题 `footnote1` | 顶部 `40+52`、`88dp` 容器 + `24dp` 圆角、`74dp` 图、`35sp Bold`、版本 `14sp` | **改造**（尺寸与层级全部对齐官方） |
| 折叠动画 | 无；顶栏固定 | `scrollProgress` + 品牌区三段淡出 + 顶栏 `titleAlpha` | **改造**（这是 About 页最明显的 OS4 特征） |
| 信息分组 | `:76-125` `SmallTitle + GroupCard{ BasicComponent×3 }` 三节 | `Card(padding(horizontal = 12))` + `ArrowPreference`（带尾部值） | **保留结构 + 改造间距**（12dp 外边距、`padding(top=12)`、末 `Spacer(12)`） |
| 只读信息 | `BasicComponent(title, summary)` | 官方 About 页没有只读行；InstallerX Home 用 `BasicComponent(title, summary)` 表达只读信息（`MiuixHomePage.kt:163-216`） | **保留** `BasicComponent`（正确），但版本号等应放到**品牌区版本行**而不是信息卡里重复 |
| 许可证入口 | `:114-119` `ArrowPreference(title, summary)` | `ArrowPreference(title, endActions = { Text("Apache-2.0") })` | **改造**：尾部用 `endActions` 显示许可证标识（`body2` + `onSurfaceVariantActions`），summary 留给说明 |
| 说明文字 | `:127-137` 居中 `footnote1` + `padding(top=12, horizontal 12)` | 官方无此块；InstallerX 无 | **保留**（合理），但左右 12dp 与卡片对齐已被 `pageContentPadding` 覆盖，不要叠两层 |
| 页面底部 | `:139` `Spacer(24.dp)` | 官方 `Spacer(12.dp)` / Mishka `windowInsetsBottomHeight(systemBars)` | **改造**：末留白用 `windowInsetsBottomHeight(WindowInsets.navigationBars)` 或统一 24dp 并写进 `PageMetrics` |

### 6.4 建议

**建议直接照搬官方 `AboutPage.kt` 的结构与全部数字**（Apache-2.0），但做三点裁剪（Glacier 没有动态背景）：

1. 去掉 `BgEffectBackground` / `textureBlur` / 背景效果 BottomSheet（Glacier 的品牌区就是「白底黑标」，不需要动态背景，任务书 6.1）。
2. 品牌图标用 `Image(painterResource(R.drawable.ic_brand_mark))` 保持现有资源，容器改为 `Box(88.dp) + graphicsLayer{shape=RoundedCornerShape(24.dp);clip=true} + background(Color.White)`，图标 `74.dp`。
3. 信息卡保留 Glacier 的 `SmallTitle + GroupCard` 语义，但：
   - Card `padding(horizontal = 12.dp)`，节间 `padding(top = 12.dp)`；
   - 许可证入口用 `ArrowPreference(title, endActions = { Text("GPL-3.0", body2, onSurfaceVariantActions) })`；
   - 版本/包名/构建类型（`AboutScreen.kt:79-95`）可保留，但版本号在品牌区已出现，信息卡里改为「Build type / Package / Working mode / Device」不重复版本。

### 6.5 许可证风险

- 官方 `AboutPage.kt` 是 Apache-2.0：**可直接照抄结构**，建议在 `NOTICE.md` 增加「About 页布局参考 MIUIX 官方 example（Apache-2.0）」。
- Mishka / InstallerX 的 About 页代码是 GPL：**不复制它们的毛玻璃/动态背景实现**（Glacier 也不需要）。

---

## 7. E. 导航（NavigationBar / NavigationRail / 宽屏 / insets / 大字体）

### 7.1 Glacier 现状

- Shell：`Glacier/app/src/main/kotlin/com/ayang/glacier/ui/GlacierShell.kt`
  - `:82-83` `glassActive`、`wide = isWideLayout()`；
  - `:88-99` pager + 宽度变化吸附；
  - `:103-126` `railState`、`LaunchedEffect(wide) { railState.expanded = wide }`、`railInset` 手工把 `systemStartInset + (Margin + ExpandedWidth) * progress`；
  - `:129-132` `rootBackdrop`（录制层，满屏）；
  - `:147-149` `navReserve = GlassNavigationBarDefaults.Height + navBars.bottom + 32.dp`；
  - `:150-156` `CompositionLocalProvider(LocalRailInset / LocalNavReserve ...)`；
  - `:157-166` 外层 `Scaffold(containerColor = Transparent, contentWindowInsets = 0)`；
  - `:209-232` 宽屏 `GlassNavigationRail(showToggle = false, modifier = align(CenterStart))`；窄屏 `GlassNavigationBar(modifier = align(BottomCenter), visible = navVisible)`——**没有 `padding(horizontal = 24.dp)`**。

### 7.2 参考做法（官方为准，Mishka 为印证）

**（1）底栏（GlassNavigationBar）**【官方·可直接照抄】

- 官方文档示例显式给水平内边距：`miuix/docs/zh_CN/components/glass/glassnavigationbar.md:34-40`
  ```kotlin
  GlassNavigationBar(..., modifier = Modifier.padding(horizontal = 24.dp))
  ```
- 官方组件内部已处理底部安全区（`defaultWindowInsetsPadding = true` 时）：`GlassNavigationBar.kt:305-324`，逻辑是 `maxOf(navigationBars.bottom, captionBar.bottom)`，`<=16dp → 20dp`、`>=28dp → 原值`、否则 `12dp + systemBottom`。
- 默认尺寸：`GlassNavigationBar.kt:116-149` —— `Height = 54.dp`（`:119`）、`IconSize = 25.dp`（`:125`）、`LabelSize = 11.dp`（`:128`）、`LargeLabelSize = 16.dp`（fontScale ≥ 1.6，`:130-131`）、`ContentPaddingHorizontal = 8.dp`（`:143`）、`IndicatorOverhang = 5.dp`（`:146`）。
- 正确的宿主位置：放在页面 `Scaffold` 的 `bottomBar` 槽，让正文 `contentPadding` 自动让位。Mishka 就是这样：`AppNavigation.kt:762-802`（`bottomBar = { GlassNavigationBar(..., modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) }`，正文用 `padding.calculateBottomPadding()`）。
- 隐藏：用 `visible` 参数（`glassnavigationbar.md:49-59`），而不是从组合里删掉——删掉会跳过隐藏动画（`GlassNavigationBar.kt:329-338` 有 scale/alpha/blur 的显隐动画）。

**（2）侧栏（GlassNavigationRail）**【官方·可直接照抄】

- 官方文档给的完整姿势：`miuix/docs/zh_CN/components/glass/glassnavigationrail.md:24-62`
  - 父容器统一 `windowInsetsPadding(systemBars ∪ captionBar ∪ displayCutout)`；
  - 页面用 `Modifier.layout { inset = ((Margin + ExpandedWidth) * progress).roundToInt(); measure(constraints.offset(-inset)); layout(maxWidth, h) { place(inset) } }` 让位；
  - `GlassNavigationRail(..., state = railState, defaultWindowInsetsPadding = false)`；
  - **背景仍铺满**（不让位），否则玻璃无内容可采。
- `:62` 明确「`railState.progress` 在布局/绘制阶段读取，可避免动画逐帧重组」。
- `:74` 折叠态「页面恢复完整宽度，胶囊居于顶栏中央；为 `GlassTopAppBar` 设置 `titleAlpha = { railState.progress }` 可隐藏被覆盖的标题」。
- `:76-89` 不要内置开关时 `showToggle = false`，由调用方控制 `railState.expanded`。
- 默认值：`GlassNavigationRail.kt:204-230` —— `ExpandedWidth = 250.dp`、`Margin = 12.dp`、`CollapsedHeight = GlassTopAppBarDefaults.ButtonSize (44dp)`、`CollapsedTop = (CollapsedHeight - CollapsedHeight)/2`（4dp 级）。
- Mishka 的落地与官方完全一致：`AppNavigation.kt:296-306`（`LocalTopBarStartInset = { (Margin + ExpandedWidth) * progress }`、`LocalTopAppBarTitleAlpha = { progress }`）、`:575-610`（Rail 与 `NavDisplay` 同级、非 Main 页 `selectedIndex = -1`、点击 `popUntil { it is Route.Main }` + `animateToPage`）。
- 宽屏时背景处理：Mishka 给根 Box 加 `background(surface)` + `clipToBounds()`，并给内容单独一层 `layerBackdrop`：`AppNavigation.kt:284-294`。

**（3）大字体底栏**【GPL·仅思路 + 官方常量】

- InstallerX 的包装：`upstream/InstallerX/app/src/main/java/com/rosan/installer/ui/page/miuix/widgets/InstallerMiuixGlassNavigationBar.kt:32-118`
  - `fontScale >= 1.6f` 时 `labelSize = GlassNavigationBarDefaults.LargeLabelSize`，否则 `LabelSize`（`:41-47`）；
  - 用 `rememberTextMeasurer` + `Constraints(maxWidth = labelWidth)` 预量标签，`isLineEllipsized` 后逐步裁字符并追加 `…`，保证**实际传给库的字符串**能放下（`:66-104`）；
  - `GlassNavigationBar(defaultWindowInsetsPadding = false, style = GlassDefaults.style(isDark), trailingAction = ...)`（`:104-118`）。
- 这套「量测 + 裁剪」是唯一能治「大字体下底栏标签溢出/换行错位」的现成方案，**逻辑可照搬，代码自己写**。

### 7.3 建议

| Glacier 位置 | 参考 | 建议 |
|---|---|---|
| `GlacierShell.kt:224-231` 底栏无水平内边距 | `glassnavigationbar.md:39` | **改造**：`modifier = Modifier.align(BottomCenter).padding(horizontal = 24.dp)` |
| `GlacierShell.kt:147-149` `navReserve` 多算 32dp | `GlassNavigationBar.kt:305-324` 的官方公式 | **改造**：`navReserve = Height + max(navBars.bottom, captionBar.bottom)`（与库内部一致），不要额外 `+32.dp`；若保留安全余量，必须写进 `PageMetrics` 并注释原因 |
| `GlacierShell.kt:147,209-231` 底栏用 `visible` 隐藏 | 官方 `visible` 参数 | **保留**（正确），但确认二级页（About/Licenses）时 `navReserve` 同时归零，否则二级页底部多留一截 |
| `GlacierShell.kt:103-126` 侧栏让位手工加 `systemStartInset` | `glassnavigationrail.md:24-62` | **改造**：官方推荐父容器先 `windowInsetsPadding(...)`、再对内容做 layout 让位；Glacier 现在是 `defaultWindowInsetsPadding = true` + 手工补 inset，两套机制叠加容易在刘海/横屏出偏移。建议对齐官方：Shell 根部统一 inset，Rail `defaultWindowInsetsPadding = false`，内容 layout 让位 |
| `GlacierShell.kt:220` `showToggle = false` | `glassnavigationrail.md:74-89` | **保留**（这是对「折叠胶囊与页面玻璃顶栏打架」的合理取舍），但**必须在审计报告里写明理由**（任务书要求「保留必须说明原因」） |
| 宽屏顶栏标题 | `glassnavigationrail.md:74`、Mishka `AppNavigation.kt:296-300` | **改造**：`PageTopBar` 在宽屏应 `expandable = false` 且 `titleAlpha = { railState.progress }`（Glacier 目前两处都没做） |
| 大字体 | `InstallerMiuixGlassNavigationBar.kt:41-104`（思路）+ 官方 `LargeLabelSize` | **改造**：给导航项标签加量测裁剪，避免 `fontScale 2.0` 时标签被截成两行或溢出胶囊 |
| 返回键语义 | InstallerX `MainScreenBackHandler.kt:18-33`（非首页 Tab 时返回键回第 0 页）；Mishka `AppNavigation.kt:868-887` | **改造**：Glacier 的 pager 在 Apps/Settings 上按返回键应回 Home，而不是直接退出应用（当前无此处理） |

### 7.4 许可证风险

- 所有导航尺寸、`padding(horizontal=24.dp)`、`defaultWindowInsetsPadding=false`、`progress` 让位公式：官方文档/源码，可自由使用。
- InstallerX 的标签量测函数是 GPL：只借「先量再裁」的思路。

---

## 8. F. 动画与交互

### 8.1 页面切换动画

**参考**【官方·可直接照抄】

- MIUIX 默认转场就是 OS4 观感，不需要自研：`miuix/miuix-nav/src/commonMain/kotlin/top/yukonga/miuix/kmp/nav/transition/NavTransitions.kt:28-57`
  - `MiuixDefault`：进入页从 trailing 边整宽滑入（`translationX` 像素取整防抖），被覆盖页 quarter-width 视差 + `alpha 1 - 0.1*progress`；
  - 圆角裁剪与暗色遮罩**不在 transition 里**，而由 `NavDisplayEffects(enableCornerClip, cornerClipRadius, dimAmount)` 承担（`:36-42`）。
- 用法：`NavDisplay(transition = NavTransitions.MiuixDefault, effects = NavDisplayEffects(cornerClipRadius = rememberNavSystemCornerRadius()))`，Mishka 就是这么用：`AppNavigation.kt:274,307-314`（并区分宽屏 `cornerClipRadius = 0.dp`）。
- InstallerX 把选择权做成设置项，默认值仍是 MIUIX：`upstream/InstallerX/app/src/main/java/com/rosan/installer/ui/animation/predictiveback/InstallerNavTransition.kt:9-18`（`PredictiveBackAnimation.MIUIX -> NavTransitions.MiuixDefault`），并有 AOSP / Scale / Classic 备选。

**Glacier 现状**：`GlacierShell.kt:168-179` 使用 `NavDisplay`，但未设置 `transition` / `effects`（用默认值，行为上等同 `MiuixDefault`，但**没有系统圆角裁剪**）。

**建议**：显式声明 `transition = NavTransitions.MiuixDefault` + `effects = NavDisplayEffects(cornerClipRadius = if (wide) 0.dp else rememberNavSystemCornerRadius())`。理由：默认值虽然一样，但「显式」才能被审计，且圆角裁剪必须显式开启。**不要**写自定义 transition（两份参考项目都没写，InstallerX 的三个备选也只是 `navGraphicsTransition` 包装）。

### 8.2 搜索展开 / 关闭

**Glacier 现状（问题点）**

- `HomeScreen.kt:215-231`：
  ```kotlin
  if (searchOpen) { GlassSearchOverlay(expanded = true, onDismissRequest = { searchOpen = false }, ...) }
  ```
  两层问题：
  1. `if (searchOpen)` 包裹 → 关闭时组合被移除，**官方 400ms 退场动画不会播**（`GlassSearchOverlay.kt:215-219` 的 `updateTransition` 需要组件还在组合中）；
  2. `expanded = true` 写死，组件只能感知「出现/消失」而不是「展开/收起」。
- `AppsScreen.kt:97-112` 同样写法。
- `AppSearchResults.kt:50-72` 在内容里手工 `topInset = statusBars + GlassSearchOverlayDefaults.BarHeight + VerticalPadding*2` 给自己加 padding。

**官方做法**【官方·可直接照抄】

- 组件始终组合，只切 `expanded`，其余全部交给 `onDismissRequest`：
  `miuix/example/shared/src/commonMain/kotlin/IconPage.kt:499-544`
  ```kotlin
  val searchBarSpace = GlassSearchOverlayDefaults.BarHeight + GlassSearchOverlayDefaults.VerticalPadding * 2
  GlassSearchOverlay(
      onDismissRequest = { searchExpanded = false },
      expanded = searchExpanded,
      scrollMaterialThreshold = GlassSearchOverlayDefaults.VerticalPadding + SearchResultVerticalPadding,
      label = "Search icons",
      scrollOffset = { ...结果列表滚动偏移或 Float.MAX_VALUE... },
      topInsets = pageInsets.safeInsets.only(WindowInsetsSides.Top),
      searchTopPadding = pageInsets.searchTop,
      blurEnabled = appState.enableBlur,
      horizontalInsetsModifier = Modifier.appPageHorizontalInsets(),
      searchIcon = MiuixIcons.Glass.Search,
      closeIcon = MiuixIcons.Glass.Close,
  ) { query -> LazyColumn(contentPadding = PaddingValues(top = topInsets + searchTop + searchBarSpace)) { ... } }
  ```
- 顶栏淡出用官方动画：`glassSearchBarTransition(expanded, blurEnabled) { barAlpha }`，Mishka 的同类实现是 `contentModifier = Modifier.glassSearchBarTransition(searchExpanded, blurEnabled) { 1f }`（`upstream/Mishka/app/src/main/kotlin/top/yukonga/mishka/ui/component/SearchBar.kt` 概念同源；`miuix` 中该 API 的用法见 `IconPage.kt:260`）。
- 两个按钮语义必须分开：清空输入（`contentDescription = "Clear"`，`GlassSearchOverlay.kt:445-456`）与关闭搜索（`contentDescription = "Cancel"`，`onClick = onDismissRequest`，`:490-500`）。**内部实现不用改，只要不共用回调、不把 × 的 onClick 接到别的动作上。**
- 返回键：组件内部已挂 `NavigationBackHandler(isBackEnabled = expanded, onBackCompleted = onDismissRequest)`（`GlassSearchOverlay.kt:241-245`），**不要**在外面再叠一个会抢事件的 BackHandler。
- 搜索框展开时把顶栏语义清空（官方 `clearAndSetSemantics {}` 思路），Glacier 已在 `PageFrame.kt:118-122` 做了 alpha + 清语义，属正确方向。

**建议**：把 `PageFrame` 的 `searchExpanded` 从「布尔」升级为「始终组合的 search 槽」，即：

- `PageFrame` 接收 `searchExpanded: Boolean` + `searchContent: @Composable ColumnScope.(String) -> Unit`；
- `overlay` 槽里**无条件**渲染 `GlassSearchOverlay(expanded = searchExpanded, onDismissRequest = onCloseSearch, ...)`；
- 页面只维护一个 `searchOpen` 状态，关闭路径唯一（`onCloseSearch`）。

### 8.3 弹层（BottomSheet / Dialog / Dropdown）

**关键规则**【官方·可直接照抄】

- `miuix/docs/zh_CN/components/glass/glassbottomsheet.md:74`：「关闭时仍须保留组件调用，直到离场动画完成。不要使用 `if (show)` 包裹调用。」
- 同文件 `:167`：「需要等待离场动画完成的操作可放入 `onDismissFinished`……不要使用 `if (showSheet)` 包裹抽屉。」
- `glassdialog.md:81`、`glasstransformpopup.md:34` 同规则。
- 宿主：`GlassBottomSheet` / `GlassDialog` 依赖 `Scaffold` 提供的 `MiuixPopupHost`，**必须在 Scaffold 内**（`glassbottomsheet.md:1-10` 的 front-matter 与 `:20-22` 的 danger 块）。Glacier 的 `PageFrame` 内部有 Scaffold、Shell 外层也有 Scaffold，满足条件；但弹层写在 `PageFrame` 的 `overlay` 槽（Scaffold 之外、外层 Scaffold 之内）**仍是合法的**，这与官方「弹层由外层宿主承载」一致。

**Glacier 现状（违反规则的位置）**

| 位置 | 现状 | 影响 |
|---|---|---|
| `HomeScreen.kt:233-252` `AppActionSheet` | 传 `show = actionTarget != null`，但 `AppActionSheet` 内部 `if (entry == null) return`（`feature/common/AppActions.kt:152`） | 关闭瞬间 entry 变 null → 组合被移除 → 无退场动画 |
| `HomeScreen.kt:254-263` `TagEditorSheet` | `if (tagEditorOpen) { TagEditorSheet(...) }`，内部 `GlassBottomSheet(show = true)`（`:463-467`） | 同上 |
| `HomeScreen.kt:265-291` `TagNameDialog` | `tagNameDialog?.let { ... GlassDialog(show = true) }` | 同上 |
| `HomeScreen.kt:293-319` 删除标签 `GlassDialog` | `deleteTagTarget?.let { ... show = true }` | 同上 |
| `HomeScreen.kt:321-332` `DeferredSheet` | `deferredTarget?.let { ... show = true }` | 同上 |
| `AppsScreen.kt:114-130` `SortSheet` / `FilterSheet` | `if (sortOpen) { SortSheet(...) }`，内部 `show = true` | 同上 |

**建议**：全部改成「状态保留 + 组件常驻」：

- BottomSheet/Dialog 组件提到 `PageFrame.overlay` 固定位置，`show = state != null`，内容用 `state ?: empty` 兜底（或者用一个 `remember { MutableState<T?> }` 并在 `onDismissFinished` 里清空）；
- 这样 400ms 级退场（`GlassBottomSheet` 内部动画）才会播放；同时能避免「关闭瞬间状态为 null 导致内容重建/闪一下」。

### 8.4 按压反馈

**参考**

- 官方 `PressFeedbackType { None, Sink, Tilt }`：`miuix/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/utils/PressFeedback.kt:199-211`。
- `Card` 默认 `pressFeedbackType = None`：`miuix/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/Card.kt:92`。
- InstallerX 在可点卡片上显式给 `PressFeedbackType.Tilt`（首页状态卡 `MiuixHomePage.kt:283-288,403-409`）/ `Sink`（许可证卡 `LibrariesContainer.kt:162-168`）。
- Glacier 的 Apps 行已经用 `Sink`（`AppsScreen.kt:177-182`），正确。

**建议**：

- 网格单元（`HomeScreen.kt:387-392`）当前只有 `clip + combinedClickable`，没有 MIUIX 按压反馈；官方网格示例用 `squircleClip + clickable`（`IconPage.kt:403-410`），也没有额外反馈——**两者一致，可保留**。若想要 OS4 手感，用 `Card`/`Surface` 的 `pressFeedbackType = Sink` 或官方 `interactionSource` 方案，不要自定义 `animateFloat` 缩放。
- 列表行统一走 `BasicComponent`（自带涟漪/按下态），**不要**像 `AppSearchResults.kt:76-80` 那样裸 `clickable`。

### 8.5 滚动折叠

**参考**

- 顶栏折叠：官方 `TopAppBar(scrollBehavior)` / `GlassTopAppBar(scrollBehavior)`，内容列表 `nestedScroll(scrollBehavior.nestedScrollConnection)`；Mishka 的 `AdaptiveTopAppBar` 在手机 `expandable = !wide`，宽屏固定不折叠：`upstream/Mishka/app/src/main/kotlin/top/yukonga/mishka/ui/component/AdaptiveTopAppBar.kt:58-99`（`expandable = !rememberIsWideScreen()`）；官方 example 同：`miuix/example/shared/src/commonMain/kotlin/utils/PageUtils.kt:224-268`（`expandable = !isWideScreen`）。
- 全屏页（About）不用 `scrollBehavior` 折叠，而是「透明 spacer + `scrollProgress` 驱动品牌区淡出」（§6.2）。
- 帧率级 State 必须延迟读：Mishka 用 `Modifier.layout{}`（`SearchBar.kt:81-88` 的 `topInset`）、`graphicsLayer{}`；官方 About 用 `derivedStateOf` + `graphicsLayer`。**禁止在组合期 `by` 解包进度值**（Mishka `docs/ui-guidelines.md:29` 记录过该坑）。

**Glacier 现状**：`PageFrame.kt:157` 已接 `nestedScroll`；`GlassTopAppBar` 未显式传 `expandable`，宽屏行为未区分。

**建议**：`PageTopBar` 增加 `expandable = !isWideLayout()`；About 页按 §6.4 增加品牌区折叠。

### 8.6 动画时长 / 曲线（官方 token，可直接引用）

| 动画 | 时长/曲线 | 证据 |
|---|---|---|
| 搜索浮层几何 | `tween(400, SearchModeEasing)` | `miuix/miuix-glass/src/commonMain/kotlin/top/yukonga/miuix/kmp/glass/GlassSearchOverlay.kt:113-126,215-219` |
| 搜索取消按钮材质 | `tween(150, delay 100ms)` | 同上 `:220-223` |
| 顶栏搜索淡出 | `tween(150, CubicBezier(0,0,0.67,1))` / 反向 `(0.26,0,0.6,0.2)` | Mishka `GlassSearchOverlay.kt:268-277`（与 miuix 内部同参） |
| 底栏显隐 | `GlassMotion.NAV_HIDE_SCALE` + `NAV_HIDE_BLUR_DP` | `GlassNavigationBar.kt:329-338` |
| 页面转场 | `NavTransitions.MiuixDefault` | `NavTransitions.kt:28-57` |
| 滚动末端触感 | `scrollEndHaptic()` | 官方/两项目均用（Mishka `AppProxyScreen.kt:159`，InstallerX `MiuixPreferredPage.kt:232`） |

**建议**：Glacier 不要再自己写 `animateFloatAsState(tween(300))` 之类；能用官方参数就用，确实没有的（如品牌区淡出）按 §6.2 的官方示例阈值（0.05/0.15、0.20/0.15、0.35/0.15）。

### 8.7 许可证风险

- 动画时长、曲线、`derivedStateOf` + `graphicsLayer` 的写法：官方，可自由使用。
- Mishka 的自定义 `SearchModeEasing`（数学曲线）与 `textureBlur` 实现是 GPL：Glacier 不需要（官方组件已内置），不要复制。

---

## 9. G. 许可证页（Licenses）

### 9.1 Glacier 现状

`Glacier/app/src/main/kotlin/com/ayang/glacier/feature/about/LicensesScreen.kt`
- `:60-69` 从 `assets/LICENSE.txt` 读全文，按 `\n\n` 分段；
- `:92-117` 组件清单：每个组件一张 `Card(insideMargin = 16.dp)` + 三段 `Text`（名称 `body1` / 许可证 `footnote2 primary` / 说明 `footnote1 onSurfaceVariantSummary`）；
- `:118-129` `SmallTitle("GNU GPL-3.0")` + 全文逐段 `Text(footnote2)`。

### 9.2 参考做法

**（1）官方 LicensePage**（推荐基准）【官方·可直接照抄】

- `miuix/example/shared/src/commonMain/kotlin/LicensePage.kt:105-136`：
  - `LazyColumn` + 每条库 `Card(Modifier.padding(horizontal = 12.dp).padding(top = 12.dp))` 内一个 `ArrowPreference(title = library.name, summary = "${artifactVersion}, ${license}", onClick = open website)`；
  - 末 `item { Spacer(12.dp) }`；
  - 右侧 `VerticalScrollBar`。
- 这给出「库列表」的最简形态：**一行一库 + 尾部/副标题显示版本与许可证**。

**（2）InstallerX 的两级结构**【GPL·仅思路】

- 列表页：`MiuixOpenSourceLicensePage.kt:32-74`（Scaffold + TopBar + BackdropSurface + `LibrariesContainer`）。
- 卡片：`LibrariesContainer.kt:162-224`
  - `Card(padding(horizontal = 12.dp).padding(bottom = 12.dp), pressFeedbackType = Sink)`；
  - 卡内 `Column(padding(16.dp))`：名称 `title2` + 版本 `headline1 secondary`；作者 `body1`；许可证徽标用嵌套 `Card(color = primaryContainer.copy(alpha = 0.2f))` + `Text(primary, 12.sp, SemiBold)`（`:206-222`）。
- 详情：`:75-159`
  - 用 `WindowDialog(title = library.name, summary = "License: ...")`；
  - 内容 `Column(heightIn(max = 420.dp))` + 内层 `LazyColumn(weight(1f, fill = false), spacedBy(12.dp))`，每条 license 一张 `Card(surfaceContainerHighest)` + `Column(padding(16.dp))`（license 名 `title4 primary` 可点、全文 `body2 onSurfaceContainer`）；
  - 关键细节：**全文渲染时 `CompositionLocalProvider(LocalSquircleEnabled provides false)`**（`:105`），避免长文本被 squircle 裁到；底部两个 `TextButton`（访问主页 / 关闭）。
- 许可证徽标与「长文本弹窗」的这两条（关 squircle、`heightIn(max)` + 内层滚动）是**修 UI 细节时最容易漏的**。

### 9.3 建议

| Glacier 位置 | 参考 | 建议 |
|---|---|---|
| `LicensesScreen.kt:92-117` 组件卡 | 官方 `LicensePage.kt:114-130` + InstallerX `LibraryCard` | **改造**：改为 `Card(padding(horizontal = 12.dp).padding(bottom = 12.dp))` + `Column(padding(16.dp))`（或整卡 `BasicComponent`）；保留三段文字，但许可证用 `footnote2 primary`、说明用 `footnote1 onSurfaceVariantSummary`（与现有语义一致） |
| `LicensesScreen.kt:118-129` GPL 全文 | InstallerX 用「点击弹窗 + 内层滚动 + `LocalSquircleEnabled=false`」 | **改造（关键）**：全文不要直接铺在页面里（当前是逐段 `Text` 堆在 LazyColumn 里，长文本卡片圆角会裁字，且滚动体验差）。改为：列表只放许可证摘要卡，点击打开 `GlassDialog`（或独立全屏页）；全文容器上 **`LocalSquircleEnabled provides false`**，内部 `LazyColumn` 滚动。GPL-3.0 分发义务（随附全文）仍然满足——全文仍在 `assets/LICENSE.txt` 且应用内可读 |
| 页面底部 | 官方 `Spacer(12.dp)` / `navigationBarsPadding` | **改造**：`pageContentPadding` 已加 `+24.dp`，不要再叠 `Spacer(24)` |
| 右侧滚动条 | 官方 `VerticalScrollBar` | **可选**：长许可证页加上更接近 OS4 |

### 9.4 许可证风险

- 官方 LicensePage（Apache-2.0）结构可直接用。
- InstallerX 的 `LibraryCard` / `LibrariesContainer` 是 GPL：只取「两级结构 + 关 squircle + `heightIn` 内滚」三个思路，代码自写。

---

## 10. 按 Glacier 组件汇总的落地表

> 「建议」列：**照搬**=直接用官方/参考实现的参数与结构；**改造**=结构保留、数值/API 替换；**保留**=现状已符合；**自研**=无参考或参考项目未解决。

> 「位置」列的 Glacier 短文件名统一位于 `Glacier/app/src/main/kotlin/com/ayang/glacier/` 下（如 `AppComponents.kt` = `feature/common/AppComponents.kt`）。

| Glacier 页面/组件 | 位置 | 参考做法（file:line） | 建议 | 许可证 |
|---|---|---|---|---|
| 应用图标轮廓 | `AppComponents.kt:24-85` | `miuix/miuix-squircle/src/commonMain/kotlin/top/yukonga/miuix/kmp/squircle/SquircleBackground.kt:96-99`；`upstream/InstallerX/app/src/main/java/com/rosan/installer/ui/page/miuix/installer/components/common.kt:122-128`（16/72） | 保留（改由 `SquircleDefaults` 驱动） | 官方 + 通用尺寸 |
| 图标来源/自适应 | `platform/AppIcons.kt:60-93` | 无（Mishka/InstallerX 均未解决） | 自研保留 | — |
| 主页网格 | `HomeScreen.kt:335-341` | `miuix/example/shared/src/commonMain/kotlin/IconPage.kt:347-350,383-432` | 改造列数公式 | 官方 Apache |
| 主页格子标签 | `HomeScreen.kt:401-410` | 官方 `footnote2` + `maxLines=1` + Ellipsis（`IconPage.kt:421-428`） | 改造（别用裸 sp 叠 style） | 官方 |
| 主页多选 | `HomeScreen.kt:127-176,352-370` | Mishka 行内 Checkbox + 顶部批量（`AppProxyScreen.kt:411-434,319-400`） | 改造（补返回键退多选） | GPL·思路 |
| 主页 FAB | `HomeScreen.kt:192-213` | 官方 `GlassFloatingActionButton`；间距参考官方 `FloatingActionButtonDefaults` | 保留 | 官方 |
| 主页标签页签 | `HomeScreen.kt:181-191,421-451` | 官方 `GlassTabRow` / `TabRow`；`topBarBottom` 槽位与录制层是兄弟（Glacier 注释已写明） | 保留 | 官方 |
| Apps 列表容器 | `AppsScreen.kt:153-157` | Mishka `AppProxyScreen.kt:154-165`；`PageMetrics` 12dp | 改造（12dp、去 spacedBy） | GPL·思路 |
| Apps 行 | `AppsScreen.kt:170-205` + `AppComponents.kt:122-139` | Mishka `AppProxyScreen.kt:411-434` | 保留结构，统一徽标 | GPL·思路 |
| 搜索浮层 | `HomeScreen.kt:215-231`、`AppsScreen.kt:97-112` | `miuix/example/shared/src/commonMain/kotlin/IconPage.kt:499-544` | 改造（常驻 + expanded） | 官方 |
| 搜索结果行 | `AppSearchResults.kt:76-108` | Mishka 复用 `BasicComponent`（`AppProxyScreen.kt:296-308`） | 改造（用 `BasicComponent`） | 官方 + GPL·思路 |
| 操作面板行 | `AppComponents.kt:93-119` | `miuix/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/Component.kt:171,287-292` | 保留（正确） | 官方 |
| 操作面板宿主 | `HomeScreen.kt:233-252` | `glassbottomsheet.md:74,167` | 改造（去掉 `if` 包裹） | 官方 |
| 标签编辑/命名 | `HomeScreen.kt:254-332` | 同上 | 改造（常驻 + `onDismissFinished` 清理） | 官方 |
| 设置分组 | `SettingsScreen.kt:117-398` | InstallerX `MiuixPreferredPage.kt:244-334`；官方 `GroupedCardItems.kt:66-89` | 保留结构 + 统一 12dp/12dp | 官方 + GPL·思路 |
| 设置下拉 | `SettingsScreen.kt:120-137` | `InstallerMiuixSpinnerPreference` 判定（`InstallerMiuixComponents.kt:252-300`） | 保留 + 补回退规则 | GPL·思路 |
| About 品牌区 | `AboutScreen.kt:150-182` | `miuix/example/shared/src/commonMain/kotlin/AboutPage.kt:233-305` | 照搬尺寸 | 官方 Apache |
| About 折叠 | 无 | `AboutPage.kt:94-149,317-339` | 照搬 | 官方 Apache |
| About 信息卡 | `AboutScreen.kt:76-125` | `AboutPage.kt:341-446` | 改造（12dp 外边距 + `endActions` 值） | 官方 Apache |
| Licenses 列表 | `LicensesScreen.kt:92-117` | `miuix/example/shared/src/commonMain/kotlin/LicensePage.kt:114-135` | 改造 | 官方 Apache |
| Licenses 全文 | `LicensesScreen.kt:118-129` | InstallerX `LibrariesContainer.kt:75-159`（关 squircle + 内滚） | 改造 | GPL·思路 |
| 底栏 | `GlacierShell.kt:224-231` | `glassnavigationbar.md:39`、`GlassNavigationBar.kt:305-324` | 改造（+24dp 水平、navReserve 公式） | 官方 |
| 侧栏 | `GlacierShell.kt:103-126,209-222` | `glassnavigationrail.md:24-89`、Mishka `AppNavigation.kt:296-306,575-610` | 改造（inset 职责归一） | 官方 + GPL·思路 |
| 宽屏顶栏 | `PageFrame.kt:169-209` | `PageUtils.kt:224-268`、`AdaptiveTopAppBar.kt:58-99` | 改造（`expandable=!wide`、`titleAlpha`） | 官方 + GPL·思路 |
| 页面转场 | `GlacierShell.kt:168-179` | `NavTransitions.kt:28-57`、Mishka `AppNavigation.kt:274,307-314` | 改造（显式 + 系统圆角） | 官方 |
| 返回键 | 无 | `MainScreenBackHandler.kt:18-33` | 改造（Tab 回首页、多选退出） | GPL·思路 |
| 大字体 | 底栏/标签 | `InstallerMiuixGlassNavigationBar.kt:41-104` | 改造 | GPL·思路 |

---

## 11. 明确「不要照搬」清单（避免把参考项目的问题搬进 Glacier）

1. **Mishka 的 AppIcon（`ui/platform/AppIcon.kt`）**：不裁切、依赖 ROM 遮罩，直接导致「圆形图标」。不要复制其加载/展示方式；只用它的 40dp 尺寸与 Crossfade 细节。
2. **Mishka / InstallerX 的 About 页动态背景链**（`BgEffectBackground` + `textureBlur` + 品牌 logo 三色 blend）：Glacier 的品牌方向是「白底黑标、简洁」，不需要也不应该引入；且这部分是三份实现里最重、最难维护、最 GPL 的部分。
3. **InstallerX 的 `WindowSpinnerPreference` 依赖**：它存在是因为要为「OS3 / 非 Glass」兜底；Glacier 只做 OS4，优先 `GlassDropdownPreference`，只在能力不足时退回 `SpinnerPreference`。
4. **Mishka 的 `SearchBar` / `GlassSearchOverlay` 自维护副本**：Mishka 在 `ui/component/GlassSearchOverlay.kt` 里复制了一份官方组件（为了自定义 `SearchModeEasing`、侧栏 inset）。Glacier **不要复制**，直接用官方 `GlassSearchOverlay` + 官方参数。
5. **`if (show)` 包裹弹层的写法**：两份 GPL 项目里都能找到类似写法，但官方文档明确禁止（`glassbottomsheet.md:74,167`）。以官方为准。
6. **`Arrangement.spacedBy()` 给 Card 之间做间距**：Mishka 明确改用「每项 `padding(horizontal=12).padding(bottom=12)`」（`docs/ui-guidelines.md:15`），官方 example 同。Glacier 的 `verticalArrangement = spacedBy(...)` 用法要清理。
7. **自研页面转场 / 自研按压动画**：两项目都没有自研转场（InstallerX 的备选也只是官方 `navGraphicsTransition` 包装）；Glacier 也不需要。

---

## 12. 未覆盖 / 未能验证的事项

1. **应用网格的「标准答案」不存在**：Mishka / InstallerX 无网格页面，本文给的列数公式来自 MIUIX 官方**图标目录页**（`IconPage.kt`），不是应用桌面；Glacier 落地时应以「宽屏不破相 + 图标 56dp 不被拉伸」为验收标准，而不是「和某项目一模一样」。
2. **自适应图标在 HyperOS 上的实际遮罩**：属 ROM 行为，本地无法验证；Glacier 的 `renderUnmasked` + squircle 是绕开遮罩的路线，正确性依赖「背景层/前景层铺满画布」这一平台事实（`AppIcons.kt:60-67`），本轮未在真机 HyperOS 上核对。
3. **Glass 的 L3 观感**：参考项目的毛玻璃参数（blurRadius、blendColors）在模拟器/SwiftShader 上不可信，本文未给出任何「应该长什么样」的结论，只给了「不引入」的建议。
4. **Mishka / InstallerX 的 `GlassTopAppBar` 渐进模糊 band 参数**：`InstallerMiuixComponents.kt:168-209` 的 `(topInset + CollapsedHeight + 20dp) * 1.2`、`curve = 0.5f`、`blurRadius = GlassTopAppBarDefaults.ProgressiveBlurRadius` 是可用参考，但属于 GPL 实现细节，Glacier 应优先用官方 `GlassTopAppBar(progressiveBlur = true)` 自带能力（Glacier 已用，`PageFrame.kt:182-198`）。
5. **本文未阅读的文件**：两项目的 `AdaptiveTopAppBar` 之外的自定义动画（`DampedDragAnimation`、`InteractiveHighlight`）、`FloatingBottomBar`、`MiuixDialog` 等。判定：Glacier 不需要自定义拖拽动画（官方 `GlassNavigationRail` 已内置形变），未读不影响结论。

---

## 13. 证据文件索引（便于复核）

**MIUIX（Apache-2.0，最高优先级）**

- `miuix/example/shared/src/commonMain/kotlin/AboutPage.kt`（About 页完整结构）
- `miuix/example/shared/src/commonMain/kotlin/LicensePage.kt`（许可证列表）
- `miuix/example/shared/src/commonMain/kotlin/IconPage.kt`（图标网格、搜索浮层官方参数）
- `miuix/example/shared/src/commonMain/kotlin/SettingsPage.kt`（设置页分组）
- `miuix/example/shared/src/commonMain/kotlin/component/GroupedCardItems.kt`（逐行 lazy 分组卡）
- `miuix/example/shared/src/commonMain/kotlin/utils/PageUtils.kt`（页面 padding / scroll modifier / AdaptiveTopAppBar）
- `miuix/docs/zh_CN/components/glass/glassnavigationbar.md`、`glassnavigationrail.md`、`glassbottomsheet.md`、`glassdialog.md`
- `miuix/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/Component.kt`、`Card.kt`、`Badge.kt`、`SmallTitle.kt`
- `miuix/miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/utils/PressFeedback.kt`
- `miuix/miuix-squircle/src/commonMain/kotlin/top/yukonga/miuix/kmp/squircle/SquircleBackground.kt`、`SquircleBorder.kt`、`SquirclePath.kt`
- `miuix/miuix-glass/src/commonMain/kotlin/top/yukonga/miuix/kmp/glass/GlassNavigationBar.kt`、`GlassNavigationRail.kt`、`GlassTopAppBar.kt`、`GlassSearchOverlay.kt`
- `miuix/miuix-nav/src/commonMain/kotlin/top/yukonga/miuix/kmp/nav/transition/NavTransitions.kt`

**Mishka（GPL-3.0，仅思路）**

- `upstream/Mishka/docs/ui-guidelines.md`（页面骨架/毛玻璃/卡片间距/帧率级 State 的踩坑记录）
- `upstream/Mishka/app/src/main/kotlin/top/yukonga/mishka/ui/platform/AppIcon.kt`
- `upstream/Mishka/app/src/main/kotlin/top/yukonga/mishka/util/AppIconCache.kt`
- `upstream/Mishka/app/src/main/kotlin/top/yukonga/mishka/ui/screen/settings/AppProxyScreen.kt`
- `upstream/Mishka/app/src/main/kotlin/top/yukonga/mishka/ui/screen/settings/SettingsScreen.kt`
- `upstream/Mishka/app/src/main/kotlin/top/yukonga/mishka/ui/screen/settings/AboutScreen.kt`
- `upstream/Mishka/app/src/main/kotlin/top/yukonga/mishka/ui/component/AdaptiveTopAppBar.kt`
- `upstream/Mishka/app/src/main/kotlin/top/yukonga/mishka/ui/component/GroupedCardItems.kt`
- `upstream/Mishka/app/src/main/kotlin/top/yukonga/mishka/ui/navigation/AppNavigation.kt`
- `upstream/Mishka/app/src/main/kotlin/top/yukonga/mishka/ui/component/GlassSearchOverlay.kt`

**InstallerX-Revived（GPL-3.0-only，仅思路）**

- `upstream/InstallerX/app/src/main/java/com/rosan/installer/ui/page/miuix/settings/preferred/about/MiuixAboutPage.kt`
- `upstream/InstallerX/app/src/main/java/com/rosan/installer/ui/page/miuix/settings/preferred/about/ossLicensePage/LibrariesContainer.kt`
- `upstream/InstallerX/app/src/main/java/com/rosan/installer/ui/page/miuix/settings/preferred/about/ossLicensePage/MiuixOpenSourceLicensePage.kt`
- `upstream/InstallerX/app/src/main/java/com/rosan/installer/ui/page/miuix/settings/preferred/MiuixPreferredPage.kt`
- `upstream/InstallerX/app/src/main/java/com/rosan/installer/ui/page/miuix/settings/home/MiuixHomePage.kt`
- `upstream/InstallerX/app/src/main/java/com/rosan/installer/ui/page/miuix/widgets/MiuixBaseWidget.kt`、`MiuixBadge.kt`、`MiuixNavigationItemWidget.kt`、`InstallerMiuixComponents.kt`、`InstallerMiuixGlassNavigationBar.kt`
- `upstream/InstallerX/app/src/main/java/com/rosan/installer/ui/page/miuix/installer/components/common.kt`
- `upstream/InstallerX/app/src/main/java/com/rosan/installer/ui/page/miuix/installer/sheetcontent/InstallConfirmContent.kt`
- `upstream/InstallerX/app/src/main/java/com/rosan/installer/data/engine/repository/AppIconCache.kt`
- `upstream/InstallerX/app/src/main/java/com/rosan/installer/ui/navigation/MainScreenBackHandler.kt`
- `upstream/InstallerX/app/src/main/java/com/rosan/installer/ui/animation/predictiveback/InstallerNavTransition.kt`

**Glacier（对照基线）**

- `Glacier/app/src/main/kotlin/com/ayang/glacier/ui/GlacierShell.kt`
- `Glacier/app/src/main/kotlin/com/ayang/glacier/ui/components/PageFrame.kt`
- `Glacier/app/src/main/kotlin/com/ayang/glacier/feature/common/AppComponents.kt`、`AppActions.kt`、`AppSearchResults.kt`
- `Glacier/app/src/main/kotlin/com/ayang/glacier/feature/home/HomeScreen.kt`
- `Glacier/app/src/main/kotlin/com/ayang/glacier/feature/apps/AppsScreen.kt`
- `Glacier/app/src/main/kotlin/com/ayang/glacier/feature/settings/SettingsScreen.kt`
- `Glacier/app/src/main/kotlin/com/ayang/glacier/feature/about/AboutScreen.kt`、`LicensesScreen.kt`
- `Glacier/app/src/main/kotlin/com/ayang/glacier/platform/AppIcons.kt`
- `Glacier/app/src/main/kotlin/com/ayang/glacier/ui/theme/GlacierTheme.kt`
- `Glacier/NOTICE.md`、`Glacier/README.md`、`Glacier/LICENSE`
