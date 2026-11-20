# Glacier R4 修改清单

- 日期：2026-11-19
- 基线：`9457df1`（R3 内容 = `b3309fe`）
- 官方依据：本地 MIUIX 源码 `C:\Users\10487\WorkBuddy\Hail\miuix`（0.9.5 档位）与 `example/shared`；成熟参考 `upstream/Mishka`、`upstream/InstallerX`（只读结构，GPL-3.0 未复制代码）
- 逐项审计与取舍理由见 `docs/audit/miuix-os4-r4-audit.md` 与 `docs/audit/r4/part-*.md`
- 本清单只记录**实际改动的代码/资源**；未改的一律不列

---

## 0. 全局骨架（`ui/components/PageFrame.kt`、`ui/GlacierShell.kt`）

| 项 | 修改前 | 修改后 | 官方依据 |
|---|---|---|---|
| 页面滚动修饰（P1-9） | `pageScrollModifiers` 未移植：overscroll 不回写 `overscrollOffset`（玻璃顶栏弹性材质失效）、无滚动结束触觉 | PageFrame 正文 wrapper 挂官方等价链 `scrollEndHaptic → overScrollVertical(onOffsetChanged → scrollBehavior.state.overscrollOffset) → nestedScroll`；并新增公开 `Modifier.pageScrollModifiers(scrollBehavior, showTopAppBar, enableScrollEndHaptic)` 供页面自挂（`pageScrollOnContent = true`） | `example/utils/PageUtils.kt` 的 `pageScrollModifiers`；`GlassTopAppBar.kt:388` 的 overscrollOffset 消费 |
| 滚动条 | 无 | PageFrame 新增 `scrollBarAdapter: ScrollBarAdapter?`，轨道 padding 用 Scaffold 实测值；Home / Apps / Settings / About / Licenses 已接线 | 官方示例每页显式挂（`example/MainPage.kt:341-345`、`IconPage.kt`） |
| 二级页转场（P1-11） | 只保证能导航，无系统圆角、无遮罩、无官方默认转场 | `NavDisplayEffects(enableCornerClip, cornerClipRadius = 窄屏 rememberNavSystemCornerRadius()/宽屏 0dp, cornerClipMode = Leading, dimAmount = 0.5f, backdropColor = surface)`；`transition = NavTransitions.MiuixDefault`；宽屏 pane `clipToBounds()` | `example/AppContent.kt:256-305`、`:342`；`miuix-nav.md:343-349` |
| 滑动返回 | 无 | 二级页（About/Licenses）启用并按 RTL 镜像物理方向；一级页显式 `None`（横向手势归 HorizontalPager） | `example/AppState.kt:40`（默认关闭，示例为设置项）；`AppContent.kt:287-292`（RTL 镜像） |
| 顶栏标题透明度 | 只有玻璃档接 `LocalTopBarTitleAlpha`，非玻璃档不支持 | PageFrame 新增 `topBarTitleAlpha: (() -> Float)?`，与 Shell 侧栏进度**相乘**；玻璃档 `GlassTopAppBar(titleAlpha=)`，非玻璃档用 `titleColor/largeTitleColor/subtitleColor` 的 alpha | `example/AboutPage.kt:127`；`example/utils/PageUtils.kt:264`（两曲线相乘） |
| 返回箭头 | 用通用 `TopBarAction`，RTL 不镜像、非玻璃档图标/尺寸不对 | 新增 `BackNavigationAction`：玻璃档 28dp `ChevronBackward`、非玻璃档 24dp `MiuixIcons.Back`，RTL `scaleX=-1`；About/Licenses 已改用 | `example/component/BackNavigationIcon.kt:29-43` |
| 侧栏让位 | `getLeft()` 取 inset | `asPaddingValues().calculateStartPadding(layoutDirection)`，RTL 语义正确 | Compose layout-direction 语义；官方 `PageUtils.kt` 同类写法 |
| **宽屏 start 安全区双算（Glass 审计 P1-1）** | `railInset` 已让出「侧栏 + start inset」，页面内 `pageContentPadding` 又让一次同一 cutout → 折叠态整体右移一个 cutout（实测折叠态左缘 288px / Apps 256px） | 新增 `LocalPageStartReserved`（Shell 宽屏 provide true），`pageContentPadding` reserved 时不再叠加 start 侧 cutout | 官方 `startAlreadyReserved` 语义（`example/utils/PageUtils.kt:99-103`：为 true 时 `safeInsets` 只保留 End + Vertical） |
| 死代码 / 自采样陷阱 | `LocalPageBackdrop`（provider 在录制层子树内，谁读谁自采样崩溃）、`LocalRailInset`、`LocalGlassRequested` 无消费者 | 三者全部删除；HomeScreen 未使用的 `LocalRootBackdrop` 引用删除 | Glass 审计 P2-1/P2-2；§12「减少不必要的自造轮子」 |
| 宽屏 pane 裁剪 | 无 | 宽屏 `NavDisplay` 外 `clipToBounds()`，滑入页不再画到玻璃侧栏下面 | `example/AppContent.kt:342`、`miuix-nav.md:345` |

## 1. Home（`feature/home/HomeScreen.kt`、`feature/common/AppComponents.kt`）

| 项 | 修改前 | 修改后 | 官方依据 |
|---|---|---|---|
| **P0-1 长按正方形叠层** | `HomeAppItem` 的整格 `Column` 直接 `combinedClickable`，默认 `indication = MiuixIndication` 用 `drawRect` 画**无圆角矩形**覆盖整格（含标签），实拍为方角灰块 | `fillMaxWidth → squircleClip(GridAppIcon × 0.22 = 12.32dp) → combinedClickable`：叠层被裁成与图标同一条 squircle；热区仍是整格（clip 不裁命中测试），无缩放/位移/描边 | `example/IconPage.kt:403-411`（先 `squircleClip(12.dp)` 再 `clickable`）；`MiuiX TabRow.kt:410-418` 同款顺序；`MiuixIndication.kt:129-135` |
| **P0-2 标签黑色选中底** | 玻璃档用 `GlassTabRow(colors = neutralColors())`：浅色选中是官方 neutral 的 `#383838` 实心胶囊，与页面中性色对比突兀；且 neutral 是"内容强调色过强"的特例档，官方示例仅用于组件演示 | 换成官方**页面级标签**范式：Glass 开 = `GlassSegmentedTabRow`（相连轨道 + 半透明指示器）；Glass 关 = `TabRow`。`surfaceAlpha` 跟随内容是否滚进顶栏 | `example/IconPage.kt:274-285`（玻璃档）、`:308-315`（非玻璃档）；`GlassTabRow.kt:210-262`（neutral 的语义与 `NeutralHeight`）；Mishka `ProviderScreen.kt:133-146` |
| P1 §9 图标-文字一致性 | 图标 56dp / 列表 40dp 两套裁切；网格标签 `footnote1` 默认字重，与列表行 Medium 分叉；搜索结果行是第三份手写实现 | 三处共用 `AppIconView`（同一 squircle 比例）+ `AppLabelTextStyle`（footnote1 + Medium，字号仍可被设置覆盖）+ Home 搜索改用共享 `AppRow` | `Component.kt:89-95`（headline1 + Medium）；`IconPage.kt:215-244` |
| 选中环跳变 | `squircleBorder + padding(2.dp)` 在 `size` 内层：选中时图标内容 56→52dp，描边半径 12.32→14.32，选中瞬间跳变且描边与轮廓不平行 | 固定尺寸 Box + 图标 `fillMaxSize` + `matchParentSize` 描边叠加；描边宽度 0→2dp `animateDpAsState`；半径与裁切半径一致 | `squircleBorder` 的 onDrawBehind 语义；`AppIconView` 新 KDoc |
| 延时冻结 toast | 用菜单项标题 `action_deferred_freeze`（「延时冻结」）当提示 | 新 key `home_deferred_freeze_scheduled`（「已安排 %1$d 分钟后冻结」） | 文案整改 §1.5 |

## 2. Apps / 应用操作面板（`feature/apps/AppsScreen.kt`、`feature/common/AppActions.kt`、`feature/common/AppSearchResults.kt`）

| 项 | 修改前 | 修改后 | 官方依据 |
|---|---|---|---|
| 列表行按压反馈 | `Card(onClick, pressFeedbackType = Sink)` + 行内 `BasicComponent` 点击双层 | `Card` 不注册点击、`BasicComponent(onClick)` 单层 indication，并被 `Card.squircleSurface` 裁成圆角；无 Sink 缩放抖动 | `example/IconPage.kt:221-244`；`Card.kt:50-67/174-183`；`SquircleBackground.kt:124-165` |
| 列表 / 搜索行统一 | 两份行实现（列表行、搜索行），摘要文案不一致 | 合并为 `AppListCardRow` + `appRowSummary`（包名 + 置顶/白名单/不在主页徽标） | 规范 §9；`IconPage.kt:221-244` |
| 操作面板大字体溢出 | 10 行 `Column`，2.0x 字体/横屏会被 `heightIn(max)` 裁掉 | `LazyColumn` + `Card` 分组 + 导航栏 `contentPadding`，可滚动到「卸载」 | `example/component/BottomSheetSection.kt:219-237` |
| 排序/筛选弹层 | 普通自造行 / `Column` | 官方 `Card` 分组 + `BasicComponent`，排序选中项用 `RadioButtonPreference`（选中语义/触觉/主色标题），筛选用官方 `SwitchPreference` | `BottomSheetSection.kt:209-267`、`SwitchPreference.kt:56` |
| 假可点行 | 操作面板中「标签」「稍后冻结」在 Apps 页点了无反应 | `AppActionSheet(unsupportedActions = …)`：Apps 传入 `{EditTags, DeferredFreeze}`，这两行不再渲染（Home / ApiActivity 调用点零改动） | §7.4「不允许看起来可以点但无效」 |
| 弹层内滚动 | 无触觉/弹性 | 弹层滚动挂官方 `scrollEndHaptic + overScrollVertical` | `PageUtils.kt` 同款 |

## 3. Settings（`feature/settings/SettingsScreen.kt`）

| 项 | 修改前 | 修改后 | 官方依据 |
|---|---|---|---|
| 依赖"自动冻结"的 4 行 | 置灰但无说明（延迟 Slider + 跳过充电/前台/通知） | 官方 dependent-option 范式 `AnimatedVisibility(visible = autoFreezeEnabled)` | `example/SettingsPage.kt:257-265/275-283/294-318` |
| 权限缺失的两个"静默失效"开关 | 可点但实际无效（`lastForegroundPackage` 无权限返回 null / `activePackages` 返回空集） | 禁用 + summary 说明缺哪项权限，ON_RESUME 重读权限状态 | InstallerX `MiuixPrivPage.kt:136-180`；§7.4 |
| 玻璃效果开关 | 无能力门控，在不支持 RuntimeShader 的设备上也会显示 | 能力门控：仅 `isRuntimeShaderSupported()` 时显示；无图标包时图标包行收起 | `example/SettingsPage.kt:210-229` |
| 分节标题左边距 | `SmallTitle` 左右各 40dp（12 内容 padding + 28 自带） | `pageContentPadding(horizontal = 0.dp)` + 卡片 12dp → `SmallTitle` 28dp | `example/component/GroupedCardItems.kt:24-58`、`SettingsPage.kt:415` |
| 组合期阻塞 | `modeState` 初值在主线程跑 `RootBackend` 的 `su whoami` | 改为不阻塞的初值，异步刷新 | Compose 主线程纪律 |
| 复制命令 toast | `R.string.ok`（「确定」） | 新 key `settings_device_owner_copied` | 文案整改 §1.5 |
| 滚动条 | 无 | `scrollBarAdapter = rememberScrollBarAdapter(listState)` | 官方示例每页显式挂 |

## 4. About（`feature/about/AboutScreen.kt`）

| 项 | 修改前 | 修改后 | 官方依据 |
|---|---|---|---|
| **P0-3 品牌区滚动联动** | 静态品牌区，只有顶栏滚动折叠；图标/名称/版本不参与动画 | 品牌区改为固定在列表下方的头图，`LazyColumn` 首项为 `item(key = "logoSpacer")` 透明占位；单一 `scrollProgress = derivedStateOf { firstVisibleItemIndex > 0 ? 1f : firstVisibleItemScrollOffset / spacer.size }` 驱动三层：版本 `(p-0.05)/0.15`、名称 `(p-0.20)/0.15`、图标 `(p-0.35)/0.15`，`alpha = 1 - t`、`scale = 1 - t*0.05`，全部在 `graphicsLayer` 绘制期求值 | `example/AboutPage.kt:94-109`（进度）、`:242-304`（三层）、`:318-339`（占位高度）、`:223`（首帧 300dp） |
| 品牌区上边距 | 40dp | 官方完整 92dp（40dp extraTop + 52dp logoContentPadding） | `AboutPage.kt:157`、`:180-185` |
| 顶栏标题随滚动淡入 | 无（PageFrame 不支持非玻璃档） | 传 `topBarTitleAlpha = { ((p-0.35)/0.65).coerceIn(0,1) }`，两档都生效 | `AboutPage.kt:127` |
| 返回箭头 / 滚动条 | 通用动作按钮 / 无 | `BackNavigationAction` + `scrollBarAdapter` | `BackNavigationIcon.kt`、`MainPage.kt:341-345` |
| 未移植（有理由） | 官方 `BgEffectBackground` 动态背景与品牌图 `textureBlur` | 不移植：Glacier 正文本身是玻璃录制层，再采 `LocalPageBackdrop` 会自采样；官方 blur 采的是它自己的 BgEffect 层 | `AboutPage.kt:225-232`、`:271-286`；`GlassEnvironment.kt:29-35` |

## 5. Licenses（`feature/about/LicensesScreen.kt`）

| 项 | 修改前 | 修改后 | 官方依据 |
|---|---|---|---|
| 行层次与留白 | 名称/许可证/说明三层字号混用；标题左右 40dp | 名称 `headline1`、许可证 `body2`、说明 `footnote1`；标题 28dp + 卡片 12dp；长文本不设 `maxLines`（大字体只增高不截断） | `example/LicensePage.kt:117-120`、`SmallTitle.kt:42` |
| 空分节 | assets 读不到时仍渲染空的 GPL 分节标题 | 读不到时不渲染 | — |
| 返回箭头 / 滚动条 | 通用动作按钮 / 无 | `BackNavigationAction` + `scrollBarAdapter` | `BackNavigationIcon.kt`、官方示例 |

## 6. ApiActivity（`feature/api/ApiActivity.kt`）

| 项 | 修改前 | 修改后 | 官方依据 |
|---|---|---|---|
| 采样语义 | 窗口底色 `surface.copy(alpha = 0.92f)`，屏幕透 8% 系统背景，但录制层录的是不透明 surface → "可见像素 ≠ 录制像素" | 窗口改为**不透明** `surface`：可见像素与录制层像素一致，L1 采样源自洽 | `GlassEnvironment.kt:82-92` 的录制层约定 |
| 窗口主题 | `Theme.Glacier.Translucent`（`windowIsTranslucent=true` + 透明 `windowBackground`）→ 首帧/窗口动画透出宿主 | 新增 `Theme.Glacier.Api`（`windowIsTranslucent=false`、`windowBackground=@color/glacier_window_background`，含 `values-night` 变体），manifest 已改用；浅/深两态实测为不透明、无白闪 | Glass 审计 P1-2；Android 窗口语义 |
| 加载态 | 无，解析期空白 | `InfiniteProgressIndicator` 加载态 | `ProgressIndicator.kt:303`；InstallerX `LoadingContent.kt:20-35` |
| 错误弹层 | summary 塞 `stackTraceToString()`（用户看不懂、且可能过长） | 只给人能读的错误文案；`GlassDialog` 常驻 + `onDismissFinished` 退出动画保持 | `DialogContentLayout.kt:90-91` |
| 操作面板归属 | `inHome` 写死，深链应用会显示 "Remove from Home" | 按 `snapshot.home` 真实归属；并接入 `unsupportedActions = {EditTags, DeferredFreeze}` | `AppActions.kt` 语义 |

## 7. 图标管线（`platform/AppIcons.kt`）

| 项 | 修改前 | 修改后 | 依据 |
|---|---|---|---|
| 图标包解码 | `BitmapFactory.decodeResource` 按原尺寸解码（192–512px 图标包 → 一屏几十个图标内存翻倍，低端机 OOM 风险）；XML/Vector 图标包返回 null | 先读界算 `inSampleSize` 再解码并缩放到目标像素；非位图资源回退 `getDrawable` + 画到目标尺寸 | Android 位图内存规范；§9「第三方图标包兼容性 / 低分辨率与高分辨率」 |
| appfilter 解析 | 依赖属性顺序（`getAttributeValue(0/1)`），任意标签都可能误匹配 | 按属性名 `component` / `drawable` 取值，只匹配 `item` 标签 | ADW/Nova appfilter 规范 |

## 8. 文案（`res/values*/strings*.xml`、`README.md`）

| 项 | 修改前 | 修改后 |
|---|---|---|
| 应用内 51 条文案 | 中英各一套共 51 条有 AI 味/重复/生硬表达（如 `about_description` 的 "让手机的其他部分保持安静…"） | 逐条精简为可读的产品文案，key 与占位符零变化（详见 `docs/audit/r4-copy-audit.md`） |
| 设备管理器描述 | `android:description="@string/action_lock"`（授权页显示「锁屏 / Lock screen」） | 新 key `device_admin_description`（说明只用于「锁屏并冻结」的 force-lock） |
| README | 开头空洞宣传 + 内部推送工作流说明 | 新增「下载与安装」「使用前」（默认 default 模式不冻结、需选择后端并授权），删除内部工作流 |

## 9. 未改动但已复核（结论见审计报告）

- `ui/Theme`：无自建色板，`MiuixTheme(ThemeController)` 唯一入口（无需改）。
- 宽屏断点：`isWideLayout()` 与官方 `shouldShowSplitPane()` 公式逐字一致（R3 已对齐，无需改）。
- `GlassEnvironment.kt` / `MainActivity.kt` / `Routes.kt`：复核后无需改动。

---

## 验证索引

- 构建与运行验收（65 张截图 + 12 组场景 + 转场拆帧 + logcat）：`docs/verification/miuix-os4-r4/README.md`
- 审计结论与未完成清单：`docs/audit/miuix-os4-r4-audit.md` §5–§9
- Glass 分级与逐消费点判定：`docs/audit/r4-glass-audit.md`
- 文案前后对照与 GitHub 粘贴版：`docs/audit/r4-copy-audit.md`

### 构建产物

| 产物 | 路径 | 大小 | SHA-256 |
|---|---|---|---|
| Debug | `app/build/outputs/apk/debug/Glacier-debug.apk` | 49,261,891 B | `5a41a4d95da50ea400022e7d032edc0228e86091204d769042adbec2297cb72d` |
| Release（1.2.0，已发布） | `app/build/outputs/apk/release/Glacier-release.apk` | 4,287,936 B | `66fea1c43d30fa0232b099cf6ce5151049fe4348c279ca75d2c28e5fa7b307b1` |

> 最终 Release APK 与"通过 delta 复核的 APK"仅差两处行为等价改动（`ApiActivity` 弹层 backdrop 的 `takeIf` 与注释），
> 已重新安装并冒烟通过（`66-final-build-smoke.png`）。
>
> **2026-11-20 已发布 1.2.0**：源码经 Git Data API 推送（远端 main `c8d21be`，tree 与本地一致），
> Release v1.2.0 附件 `Glacier-release.apk`（4,287,936 B，`sha256:66fea1c4…07b1`）与 `SHA256SUMS.txt`；
> 发布前完成 1.1.0 → 1.2.0 覆盖升级测试（数据保留）；仓库 Description / topics 同步更新。
