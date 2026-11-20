# R4 专项：Settings / Licenses / ApiActivity 微交互与官方一致性

- 日期：2026-11-19
- 负责 agent：settings-micro（task-5）
- 代码基线：R3 交付后的 Glacier 工作区（`Glacier/app/src/main/kotlin/...`）
- 官方基线：本地 MIUIX `miuix/`（源码 composite build）+ 官方 example `miuix/example/shared`；成熟参考 `upstream/InstallerX`
- 证据等级：L1 = 源码接线正确；L2 = 运行时挂载/交互正常；L3 = 真实 GPU 光学观感。**本轮无构建/无真机，只有 L1 静态证据**；下文每条都标出实际完成度。

## 0. 本轮实际改动（摘要）

| 文件 | 改动 |
|---|---|
| `feature/settings/SettingsScreen.kt` | 依赖"自动冻结"的 4 行改为 `AnimatedVisibility` 整组收起（官方 dependent-option 范式）；跳过前台/通知的开关按缺失权限**禁用 + summary 说明**；玻璃开关在设备不支持 RuntimeShader 时不再显示；无图标包时图标包行收起；`modeState` 初值不再在主线程跑 `su`；分节标题左边距 40dp→官方 28dp（横向 padding 下放到卡片）；复制成功 Toast 改成语义正确的 `settings_device_owner_copied` |
| `feature/about/LicensesScreen.kt` | 组件卡层次对齐官方行 token（`headline1`/`body2`/`footnote1`）；分节标题/卡片横向内边距与官方一致（标题 28dp、卡片 12dp）；许可证正文异步为空时不渲染空的 GPL 分节标题；明确保留"只读信息卡"语义（不伪造箭头/链接） |
| `feature/api/ApiActivity.kt` | 窗口改为不透明 surface，使**可见像素 = 录制层像素**（修复 R3 审计登记的"92% 透明窗口 vs 录制不透明 surface"采样语义错误）；新增解析期 `InfiniteProgressIndicator` 加载态；错误弹层 summary 不再塞 `stackTraceToString()`；操作单按真实主页归属传 `inHome`；按 apps-micro 的 `unsupportedActions` 约定隐藏 EditTags/DeferredFreeze 两个死行 |
| `res/values/strings_settings_r4.xml`、`values-zh-rCN/strings_settings_r4.xml` | 新增：`settings_device_owner_copied`、`settings_skip_foreground_needs_access`、`settings_skip_notifying_needs_access` |

未改动：`ui/`（shell-nav）、`feature/about/AboutScreen.kt`（about-anim）、`feature/common/AppComponents.kt`（home-p0）、`feature/common/AppActions.kt`（apps-micro）、`strings.xml` 与 `strings_*_r3.xml`（copy）。

---

## 1. Settings（`feature/settings/SettingsScreen.kt`）

### 1.1 逐项：禁用态语义（权限未授予：禁用并说明 vs 可点后提示）

**结论（选官方/成熟参考做法）：**

- **"配置选择"类**（工作模式下拉）：保持**可点**，状态只由 summary 如实说明。依据是成熟参考 InstallerX 的授权选择页：Root/Shizuku/Dhizuku 在未授权时仍可点选，summary 写 `not authorized`/`not available`（`upstream/InstallerX/.../miuix/settings/home/priv/MiuixPrivPage.kt:145-180`）；只有"当前平台根本不支持该会话安装"这一项才 `enabled=false` 且 summary 写 `unavailable`（同文件 `:136-153`）。
- **"依赖型开关"**（跳过前台/有通知应用）：权限缺失时该开关会**静默无效**——`Platform.lastForegroundPackage()` 无使用情况访问时直接返回 `null`（`glacier/platform/Platform.kt:44-53`），`AutoFreezeService.activePackages()` 无通知使用权时返回空集（`glacier/platform/auto/AutoFreezeService.kt:103-110`）。所以采用 InstallerX 的"**禁用 + summary 说明缺哪项权限**"，而不是静默置灰。
- **"依赖父开关的项"**：用官方 example 的 `AnimatedVisibility(visible = parent)`（`miuix/example/shared/src/commonMain/kotlin/SettingsPage.kt:172-207`、`:257-265`、`:275-283`、`:294-318`），不再出现"置灰且没有解释"的行。

| 行 | 现状（改前 file:line） | 官方依据 | 改动 | 未完成/风险 |
|---|---|---|---|---|
| 自动冻结依赖组（延迟 Slider、跳过充电/前台/通知 3 个 Switch） | `SettingsScreen.kt:269-309`（旧行号）：4 行 `enabled = autoFreezeEnabled`，置灰无任何说明 | `SettingsPage.kt:257-265/275-283/294-318`：依赖项整组 `AnimatedVisibility` | 改为 `AnimatedVisibility(visible = autoFreezeEnabled) { Column { ... } }`（新 `SettingsScreen.kt:296-354`） | 依赖项在父开关关闭时不可预先配置，这是官方 example 的取舍；如产品要求"先配置后开启"需改回禁用态并补 summary（需 copy 文案） |
| 跳过前台应用 Switch | `SettingsScreen.kt:292-300`：始终 `enabled=true`，无权限时点了也不生效 | `MiuixPrivPage.kt:136-180`（禁用 + summary 说明） | 加 `enabled = hasUsageAccess`；无权限时 summary = `settings_skip_foreground_needs_access`（新 `:326-337`） | 权限状态在 `ON_RESUME` 重读（`SettingsScreen.kt:111-117`）；`AppOpsManager` 读在组合期（主线程一次廉价 Binder 调用），量级可接受 |
| 跳过有通知应用 Switch | `SettingsScreen.kt:301-309`：同上 | 同上 | `enabled = hasNotificationAccess` + summary = `settings_skip_notifying_needs_access`（新 `:338-349`） | 用 `NotificationManagerCompat.getEnabledListenerPackages` 判定；若 OEM 有额外通知监听开关可能误判，需真机复验 |
| 工作模式 GlassDropdownPreference | `SettingsScreen.kt:115-136`：可点，summary 只在后端 `NeedsPermission/Unavailable` 时附状态 | `MiuixPrivPage.kt:145-180`（可点 + summary 如实说明） | 保持可点；summary 仍为 `模式 · 后端状态`，仅把初值改 null 避免主线程阻塞（见 1.3） | **权限授予入口不在本文件范围**：Shizuku/Dhizuku/DPM 的授权 API 在 platform 层，需另开 P2 任务；当前用户只能看到"needs permission" |
| 玻璃效果 Switch | `SettingsScreen.kt:163-171`：设备不支持 RuntimeShader 时也显示，`glassEnabled` 实际被 `GlassActive = glassEnabled && isRuntimeShaderSupported()` 吞掉 → 可点但无效 | `SettingsPage.kt:210-229`：官方只在 `isRuntimeShaderSupported()` 时展示玻璃/模糊开关 | `if (glassSupported) { SwitchPreference(...) }`（新 `:187-199`） | 无；这是"能力门控"，官方同款 |
| 图标包 GlassDropdownPreference | `SettingsScreen.kt:172-183`：没装图标包时候选只有 "None"，可点但选了无效果 | `SettingsPage.kt:172-207`（候选依赖其它状态时收起） | `AnimatedVisibility(visible = iconPacks.isNotEmpty())`（新 `:200-216`） | 图标包列表异步加载，首次组合会晚一帧出现（高度动画），可接受 |
| 系统权限入口 3 个 ArrowPreference | `SettingsScreen.kt:380-421`：可点跳系统设置/复制命令 | 官方无权限组件；`ArrowPreference` 即"可点行"语义（`contract §G.5`） | 保留；复制成功 Toast 从 `R.string.ok`（"确定"）改为 `settings_device_owner_copied`（新 `:419`，lead 追加项） | 设备管理员行只复制命令，用户仍需自行执行 adb；文案已由 copy 侧维护 |

### 1.2 按压反馈 / 开关动画 / Summary 一致性

| 检查项 | 现状 | 官方依据 | 结论 |
|---|---|---|---|
| 按压反馈形状 | 所有可点行都是官方 `SwitchPreference`/`SliderPreference`/`ArrowPreference`/`GlassDropdownPreference`，indication 来自 `LocalIndication = MiuixIndication`（`miuix-ui/.../theme/MiuixTheme.kt:34-44`），矩形由 `drawRect` 绘制（`utils/MiuixIndication.kt:129-135`） | 同上；行都包在 `GroupCard` → `Card` 的 `squircleSurface` 裁切内（`miuix-ui/.../basic/Card.kt:179`，见 `contract §G.2`） | **无正方形问题**；首/末行的矩形会被卡片圆角裁掉，中间行整行按压符合官方 |
| 多重 indication | 页面没有手写 `clickable/indication/ripple`（3 个文件 grep 为 0 命中） | 官方组件内部：行 `BasicComponent` 的 clickable + `Switch` 自身按压动画（`preference/SwitchPreference.kt:61-92`）与 `GlassDropdownPreference` 的 `holdDownState = mounted`（`glass/GlassDropdownPreference.kt:139`）都是官方行为 | **无页面级叠加**；弹层展开时整行保持按压态是官方"选中态"表达，保留 |
| 开关/滑块动画 | 官方 `Switch`/`Slider` 自带动画与触觉（`SliderPreference` 透传 `hapticEffect`） | `preference/SliderPreference.kt:108-171` | 不改 |
| Slider 边界 | `labelSize` 11..18 / steps=6（8 档），`delay` 0..30 / steps=29（31 档）；值文本 `%1$d sp`、`%1$d min` | 官方 `SliderPreference(valueRange/steps/valueText)` 同款（`SettingsPage.kt:548-565` 的 `showKeyPoints` 只用于 4 个关键点的密度滑杆，不适用 31 档） | 档位/边界正确；未加 keyPoints 是刻意的（31 个点会糊成一条线） |
| Summary 与行为一致 | `auto_freeze_*`、`skip_*`、`glass_effect_*` 的 summary 与 `AutoFreezeWorker`/Shell 的实际行为一致（`platform/auto/AutoFreezeWorker.kt:23-40`、`ui/GlacierShell.kt:98`） | — | 一致；唯 `working_mode_hint`（strings.xml:163）仍未使用，留给 copy 判断是否删除 |

### 1.3 本轮额外修复：`produceState` 初值阻塞主线程

- 现状：`SettingsScreen.kt:62-64`（旧行号）`produceState(initialValue = freeze.stateOf(), ...)` 在**组合期**求初值；`RootBackend.state()` 可能同步执行 `su whoami`（`glacier/system/RootBackend.kt:26-35`，30s 缓存），选中 Root 模式后进入设置页会卡主线程。
- 改动：初值改 `null`，IO 读完再补；summary 在未读到状态时只显示模式名（新 `:72-75`、`:147-158`）。依据：`produceState` 的 `initialValue` 在组合期求值（Compose runtime 语义），且官方 example 的能力状态都是可直接读的本地值，不存在同步 shell。

### 1.4 结构 / 分节间距（R3 遗留：分节标题 40dp vs 官方 28dp）

| 检查项 | 现状（改前） | 官方依据 | 改动 | 未完成/风险 |
|---|---|---|---|---|
| GroupCard / SmallTitle 结构 | `SmallTitle` + `GroupCard`（`Card(insideMargin = 0)` + 行自带 16dp），与官方同构 | `SettingsPage.kt:165-202` 的 `groupedCardItems`（`example/component/GroupedCardItems.kt:24-89`）：`SmallTitle` 独立 item、卡片各自 `outerHorizontalPadding = 12` | 结构保持 | 无 |
| 分节标题左边距 | 内容 `pageContentPadding` 横向 12 + `SmallTitle` 自带 28 = **40dp** | 官方：`SmallTitle` 28dp 直接对页面边距，12dp 只在卡片上（`GroupedCardItems.kt:24-58`、`SettingsPage.kt:415`） | `pageContentPadding(padding, horizontal = 0.dp)` + `GroupCard(modifier = Modifier.padding(horizontal = PageMetrics.Horizontal))`（新 `:130`、6 处 GroupCard） | 横屏刘海/侧栏安全区仍由 `pageContentPadding` 的 `max(0, safeInset)` 保证，不丢安全区 |
| 分节间距 | `verticalArrangement.spacedBy(SectionGap = 12.dp)` + `SmallTitle` 自带 8dp 上下 padding → 标题↔卡片、卡片↔标题视觉都是 20dp | 官方：`groupedCardItems(outerTopPadding = 12, outerBottomPadding = 12)` + `SmallTitle` 8dp（同上） | 保持 12dp，视觉间距与官方一致 | 无 |
| 弹层 backdrop 采样 | `GlassDropdownPreference(backdrop = LocalRootBackdrop.takeIf { glassActive })`（`:57-64`） | `GlassDropdownPreference.kt:41-44` 要求 backdrop 排除 popup 宿主；`LocalRootBackdrop` 是 Shell 根录制层 R，弹层由根 Scaffold popup 槽绘制（`contract §J.3`） | 保持（R3 已验证 L1） | R3 审计记录"R 不含底栏像素 → L3 偏差"，属 Shell 录制层范围（shell-nav），本轮不改 |


---

## 2. Licenses（`feature/about/LicensesScreen.kt`）

### 2.1 逐项

| 检查项 | 现状（改前 file:line） | 官方依据 | 改动 | 未完成/风险 |
|---|---|---|---|---|
| 行点击语义 | `LicensesScreen.kt:102-123`（旧）：每组件一张 `Card` + 3 段只读 `Text`，**没有** `clickable`/箭头 | 官方 `LicensePage.kt:114-131`：每库一张卡 + `ArrowPreference`（点击打开库主页） | 保留只读卡（不伪造箭头）。Glacier 无外网、也没有已核实的各项目 URL，按"不虚构"原则不加跳转 | **未完成**：若后续要补链接，需要新增 7 条已核实的 URL（`translatable=false`）+ 改成 `ArrowPreference`，属 copy/产品决策 |
| 组件名/许可证/说明的排版层次 | `:107-120`（旧）：name=`body2`(14sp)、license=`footnote1`(13sp)、note=`footnote1` | 官方行 token：标题 `headline1`(17sp Medium) / 摘要 `body2`(14sp)（`miuix-ui/.../basic/Component.kt:92-100`，`contract §H.1`） | name → `headline1`，license → `body2`，note 保持 `footnote1`（新 `:108-129`） | 无；只改排版不改许可证原文 |
| 长文本 / 大字体换行 | 3 段 `Text` 都未设 `maxLines`/`ellipsis`，默认软换行；`Card` 无固定高度 | Compose `Text` 默认软换行；官方卡片行同样不截断 | 保留（新增注释说明"禁止给这几段加 maxLines"） | 组件名 "AndroidX / Jetpack Compose" 在 2.0x 会折行，行高自然增长（见 §5） |
| 分节标题 / 卡片横向内边距 | `:81-117`（旧）：内容 padding 横向 12 同时作用于 `SmallTitle` 与卡片 → 标题左边距 40dp | `LicensePage.kt:105-131`：卡片各自 `padding(horizontal = 12)`，`SmallTitle` 28dp 直接对页面（`SmallTitle.kt:42`） | `pageContentPadding(padding, horizontal = 0.dp)` + 卡片 `.padding(horizontal = PageMetrics.Horizontal)` + 引言/GPL 正文加 12dp 横向 padding（新 `:85-99`、`:111-115`、`:141-148`） | 无 |
| 许可证原文 | `:125-133`（旧）：按 `LICENSE.txt` 的 `\n\n` 切段渲染，`footnote1` | GPL 分发合规要求完整正文（R3 结论），官方 example 无此场景 | **未改任何正文/切分逻辑**；只加"正文为空时不渲染空 GPL 标题"（新 `:131-143`），避免 assets 读取失败时出现"有标题没正文" | 若 `LICENSE.txt` 缺失会静默只剩组件列表；assets 随包，实际风险低 |
| Glass 采样 | 本页只有普通 `Card`，没有 Glass 消费者 | 官方无 GlassCard（`contract §G.2/§14`）；`LicensePage.kt` 也不在卡片上用 glass | 不改 | 顶栏的 GlassIconButton 由 `PageFrame`/Shell 的 R 层承载（shell-nav 范围） |
| 滚动条 | 无 `VerticalScrollBar` | `LicensePage.kt:137-141` 有官方滚动条 | **未改**：`PageFrame`（shell-nav 范围）没有暴露 listState，页面内自己包 `Box + VerticalScrollBar` 会与 shell-nav 的 `pageScrollModifiers` 方案重叠；已写进 §7 交接 | 长 GPL 页只能靠手势滚动，属 P1 遗留 |

### 2.2 大字体换行结论

本页没有固定高度/`maxLines`/单行 Row，1.3x/2.0x 下只会"行变高"而不会截断或溢出（详见 §5）。

---

## 3. ApiActivity（`feature/api/ApiActivity.kt`）

### 3.1 Glass 采样语义（本轮核心修复）

- **现状（改前 `:296-325`）**：独立窗口 `Theme.Glacier.Translucent`；`ApiRoot` 自建录制层 `rememberLayerBackdrop { drawRect(surface); drawContent() }`（不透明），但窗口可见底色是 `surface.copy(alpha = 0.92f)` → 屏幕上透出的 8% 系统背景不在录制层里。R3 审计已登记为"L1 采样源错误"（`docs/audit/miuix-os4-glass-audit.md §二`、`docs/audit/miuix-os4-audit.md:241`）。
- **官方依据**：`miuix-glass/.../Glass.kt:49-50`（消费者不能采自己子树、backdrop 只录本窗口内容）；官方无"半透明独立窗口"的窗口级采样范式（`contract §14` 无 GlassScaffold/窗口采样能力）。
- **改动（新 `:338-380`）**：`Scaffold(containerColor = surface)` + 正文 `Box` 只挂 `layerBackdrop`，去掉外层 `background(alpha=0.92f)`。这样"屏幕上看到的像素"与"录制层录到的像素"严格相等（都是 `surface` + 内容），玻璃采样自洽。
- **代价/风险**：独立窗口失去 8% 的窗口后景透视。这是唯一能在一个文件内做到"采样源 = 可见像素"的方案；若要保留半透明窗口，需要窗口级真实背投（官方无此能力），只能记为 L3 不可达。

### 3.2 逐项

| 检查项 | 现状（改前 file:line） | 官方依据 | 改动 | 未完成/风险 |
|---|---|---|---|---|
| 独立窗口录制层 | `:301-325`：自建 R'，`Scaffold(Transparent, insets=0)` + Box(0.92 alpha).layerBackdrop | `Glass.kt:49-50`；主窗口 R 层结构 `ui/GlacierShell.kt:162-167` | 见 3.1；弹层仍由根 Scaffold 的 popup 宿主绘制（`OverlayDialog` → `MiuixPopupUtils`，`contract §F.1`），天然在 R' 之外，**不自采样** | L3 光学观感未验证（无真机 GPU） |
| 弹层 backdrop 完整性 | `GlassDialog(backdrop = LocalRootBackdrop.current)`（`:106`）；`AppActionSheet` 内部取 `LocalRootBackdrop.takeIf { LocalGlassActive }` | `GlassDialog.kt:20-21`：backdrop 必须含页面与栏、不含 dialog 宿主和 scrim；`GlassDropdownPreference.kt:41-44` 同理 | 保持；本窗口 R' 录的是"整窗 surface + 内容"，弹层宿主在 R' 之外 | 若未来在本窗口加顶栏/底栏，需要把它们也纳入 R'；当前窗口没有栏，完整 |
| Glass 开关两档 | `glassActive = preferences.glassEnabled && isRuntimeShaderSupported()`（`:308`） | `GlacierShell.kt:98` 同一决策点；`GlassDialog`/`GlassBottomSheet` 在 `backdrop=null` 时走不透明回退（`GlassDialog.kt:67`、`GlassBottomSheet.kt:70`） | 保留；读的是 Compose 状态，开关变化会重组；关档时 `LocalRootBackdrop=null`，弹层/面板全部回退不透明 | 无 |
| 加载态 | 无 | 官方 `InfiniteProgressIndicator`（`miuix-ui/.../basic/ProgressIndicator.kt:293-306`）；成熟用法 `upstream/InstallerX/.../sheetcontent/LoadingContent.kt:20-35` | 新增 `loading` 状态；解析期在窗口中央显示 `InfiniteProgressIndicator`，弹层/错误出现后自动让位（新 `:65-73`、`:139-147`） | 加载指示器会被弹层盖住（正常）；`loading` 在 `execute()` 返回后统一置 false |
| 错误弹层退出动画 | `GlassDialog(show = errorMessage != null, onDismissFinished = { finish() })`（`:103-119`） | `OverlayDialog`/`DialogContentLayout` KDoc：`onDismissFinished` = 隐藏动画完成后回调，动画被取消则不回调（`miuix-ui/.../layout/DialogContentLayout.kt:90-91`） | 保留（R3 已修）；summary 从"message 或整段堆栈"改为"message 或异常类名"（新 `:330-338`） | 错误文案若业务侧 message 很长（如 `api_unknown_scheme` 带换行参数）在 2.0x 下仍可能让弹层很高（见 §5） |
| 操作面板复用 | `AppActionSheet(entry = lastEntry, show = entry != null, inHome = true, ...)`（`:78-101`） | 共用 `feature/common/AppActions.kt`（apps-micro 同一实现）；官方 `GlassBottomSheet` 常驻 + `show`（`contract §F.3`） | ① `inHome` 改为按 `repository.snapshot.home` 的真实归属（新 `:169`、`:95`），修复"深链应用不在主页也显示 Remove from Home"；② 按 apps-micro 新增的 `unsupportedActions` 隐藏 EditTags/DeferredFreeze 两个空实现死行（新 `:96-99`） | `TogglePin/ToggleWhitelist` 对未管理应用仍可点（写入 store 后生效，行为正确）；Home/Apps 侧由 apps-micro 统一 |
| 外层遮罩 / 返回处理 | `GlassDialog` 默认 `enableWindowDim=true`（scrim 在根宿主内、R' 外）；`GlassBottomSheet` 自己处理 back/outside | `contract §F.2/F.3` | 保留 | 无 |
| 独立窗口 onNewIntent | 未覆写；Manifest `launchMode="singleInstance"`（`AndroidManifest.xml:60-65`）→ 窗口存活期间第二个深链会被**丢弃** | Android singleInstance 语义 | **未改**：修复需要把 `actionChosen/lastEntry` 等 Compose 局部状态提升为 Activity 状态并引入并发 execute 状态机，改动面超出"微交互收口"；见 §7 | 建议 P2 处理；当前窗口通常 1 秒内关闭，实际触发概率低 |
| 禁用态 | 操作行没有 loading/disabled 表达；动作执行期间行可重复点 | 官方无"操作中"组件 | 未加（动作执行后窗口很快 finish，重复点会被 `actionChosen` + finish 收敛） | 低风险；如要严格可加 `enabled` 行（需 AppActions 参数，属 apps-micro） |

---

## 4. 三页"正方形 / 多重 indication / 异常缩放"专项核查（任务第 4 项）

核查方法：3 个文件内 grep `clickable|combinedClickable|indication|ripple|rememberRipple` → **0 命中**（`feature/` 下命中的是 HomeScreen/AppActions/AppSearchResults，均非本轮文件）。所有点击/按压都来自官方组件，逐控件落点：

| 页面 | 可点击控件 | indication 来源 | 是否在裁切内 | 结论 |
|---|---|---|---|---|
| Settings | `SwitchPreference`(6)、`SliderPreference`(2，滑杆本身可拖)、`ArrowPreference`(4)、`GlassDropdownPreference`(5) | `LocalIndication = MiuixIndication`（`MiuixTheme.kt:34-44`），矩形 `drawRect`（`MiuixIndication.kt:129-135`） | 全部在 `GroupCard` → `Card` 的 `squircleSurface` 裁切内（`Card.kt:179`；`PageFrame.kt:357-368` 的 GroupCard 就是官方 Card） | 无正方形；首/末行圆角被卡片裁切，中间整行按压是官方形态 |
| Settings | 下拉弹层里的 `GlassPopupItem` | 官方 Glass 表面内部 | 弹层面板自身圆角裁切（`GlassDropdownPopup.kt:31-38`，`contract §F.4`） | 官方内部，无需页面处理 |
| Licenses | 无（卡片是只读信息） | — | — | 无可按压层，符合"信息卡"语义；不做假箭头 |
| ApiActivity | `AppActionSheet` 的行（`BasicComponent`/`SheetActionRow`） | 同上 MiuixIndication | `GlassBottomSheet` 表面裁切（apps-micro 已按官方 Card 分组，`AppActions.kt:176-179`） | 无正方形 |
| ApiActivity | `GlassDialogButton`（错误弹层主按钮） | 官方 Button indication | `GlassDialog` 表面裁切（`GlassDialogSurface`） | 无正方形 |

**风险（维持条件）**：这个结论依赖"可点行始终包在官方 `Card`/`Glass*Surface` 裁切里"。如果后续把 `GroupCard` 换成裸 `Column`、或在卡片外新增裸 `clickable` 行，就会重现 Home 那类正方形叠层；新增行必须继续走官方组件。

---

## 5. 大字体 1.3x / 2.0x 风险行清单（任务第 5 项）

判定依据：官方 `BasicComponent` 用自定义 `Layout` + intrinsic 测量，行高只有 `heightIn(min = 56dp)` 没有上限（`miuix-ui/.../basic/Component.kt:169-177`、`:210-277`），因此官方 Preference 行在大字体下是"变高"而不是"溢出/截断"；`Text` 默认软换行。逐行清单：

| 页面 | 风险行（当前 file:line） | 1.3x | 2.0x | 处理 |
|---|---|---|---|---|
| Settings | 工作模式下拉 `:136-160`：`title` 与右侧值（如 `Shizuku · suspend`）在 `BasicComponent` 的 60% end 上限内争宽（`Component.kt:228-232`） | 值折 2 行，行变高 | 值可能折 3 行、标题被压到 40% 宽（逐字换行风险） | 不改：官方组件自身行为；如需更好可缩短值文案（copy 侧） |
| Settings | 图标包下拉 `:204-215`：图标包 label 可能很长 | 折行，行变高 | 同上；弹层项由官方 `GlassPopupItem` 单行处理 | 不改（无溢出）；极端长 label 由官方弹层省略 |
| Settings | 系统权限 3 个 `ArrowPreference` `:380-421`：summary 为整句（如 "Lets auto freeze skip apps with notifications"） | 2 行 | 3 行，卡片变高 | 不改：`BasicComponent` 允许增长，`LazyColumn` 可滚 |
| Settings | 延迟 Slider `:301-313`：`valueText = "30 min"` 在 end 区 | 无 | 无 | 无 |
| Settings | 顶栏 subtitle `repository.deviceSummary()`（`PageFrame.kt` 传参，shell-nav 范围） | 窄屏标题区被压 | 可能挤压标题/折行 | **跨范围**：`PageFrame`/顶部标题需 shell-nav 复核；本轮不改 |
| Licenses | 组件卡名称/许可证/说明 `:108-129`（"AndroidX / Jetpack Compose" 等） | 名称折 2 行 | 名称折 2-3 行、整卡变高 | 已确认无 maxLines；不截断 |
| Licenses | GPL 正文段落 `:133-143` | 段落更长 | 段落极长（但 `LazyColumn` 可滚） | 保留（许可证原文不得删改） |
| Licenses | GPL 分节标题 `SmallTitle`（`licenses_gpl_heading`） | 单行 | 14sp→28sp，可能折 2 行 | `SmallTitle` 无 maxLines，安全 |
| ApiActivity | 错误 `GlassDialog` 的 `summary`（`:126`） | 长错误 2-3 行 | 长错误可能超出对话框可视高度 | 已把 `stackTraceToString()` 改为单行类名（`:330-338`）；业务 message 仍可能长，官方 dialog 无滚动 slot → **残留风险** |
| ApiActivity | 操作单行 `AppActionSheet`（`AppActions.kt`，apps-micro） | 行变高 | 10 行 × 2.0x 超屏 | apps-micro 本轮已改为 `LazyColumn` + 底部 insets（`AppActions.kt:171-179`、`:205-207`），风险已消除；本报告只登记 |
| ApiActivity | 加载态 `InfiniteProgressIndicator`（默认 20dp） | 无 | 无 | 无 |

**本轮直接修掉的溢出风险**：无硬溢出（3 页都没有固定高度容器）；实际修掉的是"2.0x 下死行/空行"的信息风险（依赖项收起、空 GPL 标题、错误堆栈）。

---

## 6. 新增字符串（只在本 agent 的新文件里）

`res/values/strings_settings_r4.xml` / `res/values-zh-rCN/strings_settings_r4.xml`：

| key | EN | ZH | 用途 |
|---|---|---|---|
| `settings_device_owner_copied` | Setup command copied | 设置命令已复制 | 复制设备所有者命令后的 Toast（原误用 `R.string.ok`＝"确定"） |
| `settings_skip_foreground_needs_access` | Needs usage access | 需要「使用情况访问」权限 | 跳过前台应用开关禁用时的 summary |
| `settings_skip_notifying_needs_access` | Needs notification access | 需要「通知使用权」权限 | 跳过有通知应用开关禁用时的 summary |

未改 `strings.xml` / `strings_*_r3.xml`（copy agent 所有）。提示 copy：`working_mode_hint`（`strings.xml:163`）当前无引用，可在收口时决定删除或接进工作模式说明。

---

## 7. 未完成 / 风险 / 需要其它 agent 接手

1. **（shell-nav）页面滚动修饰与滚动条**：官方 `SettingsPage.kt:139-511` 与 `LicensePage.kt:137-141` 都是 `pageScrollModifiers(...)` + `VerticalScrollBar`；Glacier 目前 `PageFrame` 只接了 `nestedScroll(scrollBehavior)`，没有 overscroll 定制/滚动结束触觉/滚动条。因为 `PageFrame` 在 shell-nav 范围且页面内自包滚动条会与其方案重叠，本轮**不改**，建议由 shell-nav 统一补（官方 API：`miuix-ui/.../basic/ScrollBar.kt` 的 `VerticalScrollBar`/`rememberScrollBarAdapter`，需 `@OptIn(ExperimentalScrollBarApi::class)`）。
2. **（about-anim）About 页同一处分节标题左边距问题**：`AboutScreen.kt`（about-anim 范围）也用 `PageFrame + SmallTitle + Card`，同样会得到 40dp 标题左边距；本轮只修了 Settings/Licenses。若需三页统一，请 about-anim 采用同一写法（`pageContentPadding(padding, horizontal = 0.dp)` + 卡片 `padding(horizontal = PageMetrics.Horizontal)`）。
2. **（platform/P2）权限授予入口**：工作模式 summary 能显示 `needs permission`，但 Shizuku/Dhizuku/DPM 的授权请求 API 在 platform 层，本轮无法在 Settings 内闭环；Shizuku 未运行时用户只能去 Shizuku 应用授权。建议 P2 单开。
3. **（copy）** `working_mode_hint` 未使用；`settings_device_owner_summary` 仍写"Copy the device owner setup command"，与行为一致。
4. **ApiActivity `onNewIntent`**：`singleInstance` 窗口存活期间的第二个深链会被丢弃；修复需要把 Compose 局部状态（`lastEntry`/`actionChosen`）提升为 Activity 状态，属行为改动，建议 P2 并配真机验证。
5. **ApiActivity 错误 dialog 长文本**：官方 `GlassDialog` 的 summary 不是滚动区；业务 message 很长时 2.0x 仍可能超高。当前已排除堆栈截图式长文本；如需彻底解决，应改成 `content` 放一个 `LazyColumn`/`verticalScroll` 的正文（需要复制按钮/关闭按钮改版）。
6. **L3 未验证**：ApiActivity 的窗口不透明化后，玻璃采样自洽只到 L1；真实 GPU 下的折射/边缘光学仍需真机复验（与 R3 结论一致）。
7. **跨 agent 交接**：`AppActionSheet` 的 `unsupportedActions` 已按 apps-micro 的约定接入（`ApiActivity.kt:96-99`），apps-micro 可在 part-apps.md 登记"API 入口已消费"。

---

## 8. 验证状态（诚实记录）

- 本轮**未运行** gradle / 构建 / 安装 / adb（按任务硬性约束）。
- 代码正确性靠：① 所有新增 API 均来自本地 MIUIX 源码且被其它页面/官方 example 使用过（`AnimatedVisibility`=Compose 标准；`isRuntimeShaderSupported`=GlacierShell 同款；`InfiniteProgressIndicator`=`miuix-ui/.../basic/ProgressIndicator.kt:303`；`LifecycleEventEffect`=MainActivity/SessionLock 同款；`NotificationManagerCompat`=androidx.core 已有依赖）；② 仅使用官方组件现有参数（`SwitchPreference(enabled/summary)`、`AppActionSheet(unsupportedActions/inHome)`）。
- 建议 lead 构建后重点复核：Settings 关闭自动冻结时依赖组是否动画收起；2.0x 下工作模式/图标包下拉行是否过高；ApiActivity 玻璃开/关两档背景；ApiActivity 操作单不再出现 EditTags/DeferredFreeze 两行，且非主页应用显示 "Add to Home"。
