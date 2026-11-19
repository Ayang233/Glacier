# Glacier R3 修改清单（每个页面改了什么 / 用什么官方 API）

- 日期：2026-11-19
- 对应任务：`Glacier 第二轮任务：全面审计并完成 MIUIX OS4 / OS4 Glass 对齐.md`
- 官方依据：本地 MIUIX `58b0a3d`（0.9.5）源码与 `example/shared`；参考项目只取结构/尺寸（GPL-3.0，未复制代码）
- 业务层（`data/`、`domain/`、`system/`、`app/`、`platform/` 除 `AppIcons.kt`）**未改动**

---

## 0. 全局骨架（本轮最关键的结构整改）

### `ui/GlacierShell.kt`（重写）

| 项 | 整改前 | 整改后 | 官方依据 |
|---|---|---|---|
| 底部导航宿主 | Shell 最外层 `Box` 里浮一个 `align(BottomCenter)` 的 `GlassNavigationBar` | 放进 Shell `Scaffold.bottomBar` 槽 | `example/AppContent.kt:644-683`、`example/GlassPage.kt:272-334` |
| 底栏横向宽度 | `fillMaxWidth()`（整屏 411dp） | `padding(horizontal = 24.dp)`（实测 363.4dp） | `example/AppContent.kt:772-774`、`GlassPage.kt:277` |
| 底栏 insets | 库默认（`defaultWindowInsetsPadding=true`）+ 页面再手算让位 | 官方做法：`defaultWindowInsetsPadding = false`，外层 layout 底部 `max(24.dp, safeBottom + 8.dp)`，顶部 20dp | `example/GlassPage.kt:274-285` |
| 页面让位 | `LocalNavReserve = Height + navigationBars + 32.dp`（魔法值，偏大 68~104dp） | 底栏 `onSizeChanged` 实测高度 → `LocalShellBottomBarHeight`，页面用透明 `bottomBar` 占位由 Scaffold 计算 | `ui/basic/Scaffold.kt:328-346`（topBar/bottomBar 存在时正文 padding 只取栏高） |
| 宽屏侧栏 | `showToggle=false` 常驻展开；8 40dp 就展开 | 官方自适应：`>=840dp` 用侧栏；`>=1200dp` 展开，840~1200dp 折叠成顶部胶囊（可点开） | `example/utils/AdaptiveUtils.kt:12-42`、`example/AppContent.kt:210-216` |
| 侧栏标题 | 无 | 折叠胶囊会盖住标题：顶栏标题透明度跟随 `railState.progress` | `example/utils/PageUtils.kt:67`、`example/AppContent.kt:217-223`、`GlassNavigationRail.kt:289-291` |
| 搜索浮层顶部让位 | 无 | 折叠胶囊高度 `(CollapsedTop + CollapsedHeight + 4dp) * (1 - progress)` 通过 `LocalSearchTopInset` 提供 | `example/utils/PageUtils.kt:239-245`（`searchTop`） |
| 分页动画 | `animateScrollToPage` | 官方 `PagerNavigationSpringSpec` + `springAnimateToPage`，`flingBehavior` 使用官方 spring | `example/AppContent.kt:982-1006` |
| 分页 tab 同步 | `LaunchedEffect(currentPage){tab=...}`（动画中来回写，取消 spring → 两页各露一半） | 只回写 `settledPage`（`snapshotFlow`），动画期间不回写 | 官方 `MainPagerState` 只在 `isNavigating=false` 时同步（`AppContent.kt:1055-1086`） |
| 侧栏选中项 | 二级页沿用一级 tab 高亮 | 二级页 `selectedIndex = -1`（官方"无选中"语义） | `example/AppContent.kt:530-537` |

### `ui/components/PageFrame.kt`（重写）

| 项 | 整改前 | 整改后 | 官方依据 |
|---|---|---|---|
| 页面录制层 | 正文内层 Box 挂 `layerBackdrop` | 保留（官方页面正文录制层同位置），顶栏/FAB 为兄弟 | `example/AppContent.kt:676`、`example/GlassPage.kt:447` |
| Scaffold insets | 默认（systemBars ∪ cutout） | `contentWindowInsets = WindowInsets(0)`：系统 inset 由顶栏与正文分别处理（避免与 Shell 重复） | `example/AppContent.kt:308-313`、`GlassPage.kt:698-706` |
| 底栏让位 | `padding(bottom = reserve - 20.dp)` 魔法值 | `bottomBar = Spacer(LocalShellBottomBarHeight)`；FAB 由 Scaffold 自动上浮 12dp | `ui/basic/Scaffold.kt:261-269`、`:465` |
| 页面留白 | 各页 16dp / 12dp 混用 | `PageMetrics.Horizontal = 12.dp`（官方卡片外边距） | `example/MainPage.kt:312`、`AboutPage.kt:348` |
| 分节间距 | `6.dp` / `8.dp` / `18.dp` 混用 | `PageMetrics.SectionGap = 12.dp`，列表末尾 `BottomGap = 12.dp` | `AboutPage.kt:399/442`、`MainPage.kt:339` |
| `pageContentPadding` | `remember(scaffoldPadding)` 缓存第一帧（顶栏底栏高度未量出时的错值） | 直接读 `calculate*Padding()`（订阅 Scaffold 内部状态），横向 `max(12dp, 刘海/横屏导航条)` | `example/utils/PageUtils.kt:186-222`（maxOf 语义） |
| 搜索态 | 只对顶栏 `graphicsLayer{alpha}` 淡出 | 整页 `Scaffold` 按 `rememberGlassSearchBarAlpha` 淡出 + `clearAndSetSemantics`；顶栏再用 `glassSearchBarTransition` 缩放/模糊 | `example/GlassPage.kt:264-271`、`:339-340` |
| 顶栏动作图标 | 24dp | 28dp（官方 example） | `example/GlassPage.kt:357/370/388` |
| 二级页大标题 | 仍可折叠 32sp | `expandable = false`；宽屏自动折叠成紧凑标题 | `example/AboutPage.kt:139`、`example/utils/PageUtils.kt:225-291` |

### `ui/GlassEnvironment.kt`

- 删除 `LocalNavReserve`（手算让位）。
- 新增 `LocalShellBottomBarHeight`（底栏实测高度）、`LocalTopBarTitleAlpha`（宽屏侧栏标题淡出）、`LocalSearchTopInset`（折叠胶囊的搜索顶部让位）。
- `isWideLayout()` 对齐官方：`width >= 840dp || (width >= 600dp && height/width < 1.2)`；新增 `isExtraWideLayout()`（>=1200dp 展开侧栏）。

---

## 1. Home（`feature/home/HomeScreen.kt`）

| 项 | 整改前 | 整改后 | 官方依据 |
|---|---|---|---|
| 标签页签 | 蓝色 primary 页签、留白 8dp | 官方 `GlassTabRow(colors = neutralColors())`（页面内容已有强调色时不抢色）+ 12dp 留白 | `GlassTabRow.kt:210-262`（neutralColors KDoc） |
| 应用网格列数 | 写死 4/5 列（横屏四列拉满、空洞巨大） | 按可用宽度自适应：`(可用宽度 / 80dp).coerceIn(3, 8)`，紧凑模式 68dp | `example/IconPage.kt:347-350`（官方图标网格公式） |
| 网格间距 | `spacedBy(6.dp, 18.dp)` + 自造 padding | 图标 56dp、图标-标签 6dp、水平 8dp、垂直 16dp；整格热区（含标签） | 规范 §2.1 登记值；`ScreenMetrics` 与 `PageMetrics` |
| 按压层 | `clip(RoundedCornerShape(18.dp))` 与 squircle 不同形状 | 删除自造按压层，`combinedClickable` + 官方 squircle 选中环 | `AppIconView`（squircle API） |
| 搜索浮层 | `if (searchOpen) { GlassSearchOverlay(expanded = true) }` | **常驻组合** + `expanded = searchOpen`；补 `horizontalInsetsModifier`、`searchTopPadding = LocalSearchTopInset`、`scrollMaterialThreshold`/`scrollOffset`、Glass 图标 | `GlassSearchOverlay.kt:196-245`、`example/IconPage.kt:499-544` |
| 搜索结果 | 手搓 `Row + clickable`、`take(50)` | `Card + BasicComponent`（40dp `AppIconView` + 标题 + 包名 + 冻结徽标），底部让位含 `LocalShellBottomBarHeight` | `example/IconPage.kt:215-244` |
| 标签编辑 BottomSheet | `if (tagEditorOpen)` 包裹、自造 Row | 常驻 + `show` + 保留最后一次内容；行 `BasicComponent`（行尾编辑/删除）+ `SheetActionRow` | `GlassBottomSheet.kt`、规范 0.1 红线 |
| 标签名 Dialog | `?.let { GlassDialog(show = true) }`、按钮上下贴死 | 常驻 + `show` + 保留最后请求；按钮 `Row(spacedBy(12.dp)) + weight(1f)`；空文本禁用保存 | `example/GlassPage.kt:422-436` |
| 删除标签确认 | 同上 + 通用文案 | 常驻 + `show` + 保留最后 tag；摘要带标签名（新增字符串） | `GlassDialog.kt` |
| 延时冻结 | 4 行同一图标、自造 row | 常驻 + `show`；4 行统一 `SheetActionRow`（Timer 图标） | `GlassBottomSheet.kt` |
| FAB | `padding(bottom = reserve - 20.dp)` | 无手动让位，`GlassFloatingActionButton(backdrop = pageBackdrop)` | `Scaffold.kt:261-269`、`GlassFloatingActionButton.kt` |
| 多选顶栏 | 动作无间距 | 5 个 `TopBarAction` + `Row(spacedBy(8.dp))`，图标 28dp | `example/GlassPage.kt:363` |

## 2. Apps（`feature/apps/AppsScreen.kt`）+ 操作面板（`feature/common/AppActions.kt`）

| 项 | 整改前 | 整改后 | 官方依据 |
|---|---|---|---|
| 列表卡片 | `Card{ AppRow(clickable) }` 双层点击 | `Card(onClick, pressFeedbackType = Sink)` + `BasicComponent` 行（40dp 图标、包名 summary、冻结徽标进 endActions） | `Card.kt:92-136`、`Component.kt:171-292` |
| 卡片间距 / 页面留白 | `spacedBy(8.dp)` / 16dp | `SectionGap = 12.dp` / `pageContentPadding` 默认 12dp | `AboutPage.kt:399`、`MainPage.kt:312` |
| 空状态 | 不吃 padding（从 y=0 起排、被顶栏盖住） | `Box(padding(pageContentPadding(padding)))` | 规范 §1.3 |
| 搜索 | `if (searchOpen)` + 手搓结果行 | 常驻组合 + 官方参数 + `Card + AppRow` 结果行 + `LocalShellBottomBarHeight` 底部让位 | 同 Home |
| 排序弹层 | 选中项 `SheetActionRow`、未选中 `BasicComponent` 两种行 | 全部 `BasicComponent`，选中项 `endActions` 打勾；常驻 + show | `GlassBottomSheet.kt`、`example/component/BottomSheetSection.kt:209-267` |
| 筛选弹层 | 覆盖 `insideMargin` | 全部官方默认；常驻 + show | `SwitchPreference.kt:56` |
| 操作面板 | `if (entry == null) return`；自造行/间距 | 常驻 + `show` + 保留最后 entry（新增可选 `onDismissFinished`）；`SheetActionRow` 24dp 图标；包名脚注 16dp；12dp 间距 | `GlassBottomSheet.kt:49-98` |
| 搜索结果列表（`AppSearchResults.kt`） | 手搓 Row + clickable | `Card + AppRow`；顶部让位 `statusBars + searchTop + BarHeight + VerticalPadding*2`；底部含底栏让位 | `example/IconPage.kt:537-540` |

## 3. Settings（`feature/settings/SettingsScreen.kt`）

- 结构保持官方 `SmallTitle` + `GroupCard`（`Card(insideMargin = 0)`），行全部官方 Preference，不覆盖 `insideMargin`。
- 分节间距统一 `PageMetrics.SectionGap`；删除重复"自动冻结"入口与底部魔法 Spacer。
- `GlassDropdownPreference` 统一传 `LocalRootBackdrop`；弹层由根 Scaffold 绘制（采样 R，安全）。
- 硬编码英文/拼接字符串资源化：`settings_notification_access*`、`settings_usage_access*`、`settings_device_owner*`、`working_mode_summary`、`home_font_size_value`、`auto_freeze_delay_value` 等；`onClick` 内的 `stringResource` 提前到组合期。
- `fontScale` 边界：关闭态开关、Slider 值文本由官方组件排版。

## 4. About（`feature/about/AboutScreen.kt`）

按官方 `example/AboutPage.kt` 结构重写（Apache-2.0，可照抄结构尺寸）：

| 元素 | 整改前 | 整改后 | 官方依据 |
|---|---|---|---|
| 品牌容器 | 自绘 `Box(80.dp) + squircleBackground(白, 18dp)` + 56dp 图 | `88.dp` + `graphicsLayer{shape=RoundedCornerShape(24.dp);clip=true}` + 白底 + 74dp 图 | `AboutPage.kt:242-261` |
| 应用名 | `title1`(32sp) | 35sp Bold，`onBackground` | `AboutPage.kt:262-291` |
| 版本 | `footnote1` 与标题挤在一行 | 14sp `onSurfaceVariantSummary`，版本行在卡片里用 `endActions` 值 | `AboutPage.kt:292-304`、`:373-382` |
| 上边距 | 16dp | 40dp（品牌区），名称上 12dp / 下 5dp | `AboutPage.kt:157`、`:263` |
| 信息分组 | 手搓 Row + padding | `SmallTitle` + `Card` 分组；只读 `BasicComponent`，可跳转 `ArrowPreference` | `AboutPage.kt:347-441` |
| 卡片间距 | `spacedBy(6.dp)` | 12dp | `AboutPage.kt:399/442` |
| 顶栏 | 可折叠大标题 | `expandable = false`（二级页），返回按钮 28dp 图标 | `AboutPage.kt:139` |

## 5. Licenses（`feature/about/LicensesScreen.kt`）

- 官方卡片结构：`LazyColumn(pageContentPadding)` + 每张 `Card` + 组件行 `ArrowPreference`/`BasicComponent`；组件卡 `insideMargin` 用官方默认 16dp。
- 正文 `body2` / `footnote1`；分节 `SmallTitle`；节间距 12dp；全部文案资源化。

## 6. ApiActivity（`feature/api/ApiActivity.kt`）

- 独立窗口录制层 + `LocalRootBackdrop` 结构保留（与主窗口隔离）。
- `GlassDialog` 改常驻 + `show` + 保留最后错误信息（退出动画不被 `if` 砍掉）；`onDismissFinished` 后再 `finish()`。
- 操作面板复用 `AppActionSheet`（常驻版本）。
- 修复"动作失败时先 `showError` 又立刻 `finish()` 导致错误弹层永远看不到"的问题：失败保留窗口，成功才关闭。

## 7. 图标系统（`platform/AppIcons.kt`、`feature/common/AppComponents.kt`、`res/drawable*`）

| 项 | 整改前 | 整改后 | 依据 |
|---|---|---|---|
| 自适应图标渲染 | `background/foreground` 铺满 `sizePx`（把 108dp 画布当遮罩，图标内容只有约 2/3） | 按启动器遮罩语义 1.5×：bounds = `(-size/4, -size/4, size*5/4, size*5/4)`，让 108dp 画布里的 72dp 安全区铺满输出位图 | Android Adaptive Icon 规范（108dp 画布 / 72dp 可见区）；`docs/reference/reference-projects.md` |
| 轮廓 | 圆形 | 官方 `squircleClip(size * 0.22f)`（shader 不可用自动回退 `RoundedCornerShape`），选中环 `squircleBorder` + 2dp 内缩 | `miuix-squircle` API |
| 第三方颜色 | 部分被染色 | `tint = Color.Unspecified`，保留原色；去色只由设置项在 bitmap 层处理 | 任务书 §6.2 |
| 品牌图标 | 蓝色渐变底 + 白主体 | 白底 + 黑色主体（`ic_launcher_background/foreground/monochrome`、`ic_brand_mark`），无渐变 | 任务书 §6.1 |
| About 品牌图 | 108dp 画布直接缩放（主体偏小） | `ic_brand_mark` 裁到 72dp 安全区构图，74dp 渲染时主体约 60dp | 官方 AboutPage 74dp 图标 |
| Home / Apps 一致性 | 两处各自裁切 | 共用 `AppIconView`（同一圆角比例、同一取图管线、同一占位符） | 任务书 §6.2 |

## 8. 资源

- 新增字符串资源文件（避免多人并发改同一文件）：
  - `res/values/strings_home_r3.xml` / `values-zh-rCN/` — 删除标签确认摘要等
  - `res/values/strings_pages_r3.xml` / `values-zh-rCN/` — About/Licenses/Settings 新文案
  - `res/values/strings_apps_r3.xml` / `values-zh-rCN/` — 占位文件（本轮 Apps 无新增文案，复用既有键）
- `ic_brand_mark.xml`：108dp → 72dp 安全区构图（黑主体，无渐变）。
- 未新增任何第三方依赖；`miuix-squircle` 是上一轮引入的同仓库 Apache-2.0 模块。

## 9. 业务层与文档

- `data/`、`domain/`、`system/`、`app/`、`platform/`（除 `AppIcons.kt`）无改动；六条冻结后端、Repository、FreezeController、外部 API、数据持久化全部保持。
- 文档：`docs/reference/miuix-os4-contract.md`（官方契约）、`docs/reference/reference-projects.md`（参考项目与许可证）、`docs/audit/miuix-os4-audit.md`（审计 + 本轮结果）、`docs/audit/miuix-os4-glass-audit.md`（Glass 架构）、`docs/verification/miuix-os4-r3/`（验收记录与截图）、本清单。
