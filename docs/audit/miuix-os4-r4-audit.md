# Glacier R4：MIUIX OS4 / OS4 Glass 深度体验审计与整改

- 日期：2026-11-19
- 对应任务：`Glacier R4：MIUIX OS4  OS4 Glass 深度体验审计与精细化整改.md`
- 审计对象：`C:\Users\10487\WorkBuddy\Hail\Glacier`（独立 git 仓库，`:app` 单模块）
- MIUIX 基线：本地源码 `C:\Users\10487\WorkBuddy\Hail\miuix`（composite build 引用，0.9.5 档位）
- 参考项目（均为本地克隆，非网页截图）：`upstream/Mishka`、`upstream/InstallerX`、`upstream/awesome-miuix`
- 验证设备：Android 16 模拟器（API 36，1080×2400，420dpi → 411dp 宽），`ro.hardware.egl = emulation`

> 本文分两部分：**第一部分（§1–§3）是基线与方法**；**第二部分（§4 起）是本轮问题清单、整改与结论**。
> 每条结论带证据等级（E1 源码 / E2 模拟器实测 / E3 真机 GPU）与源码位置。
> Glass 分级的完整逐项判定在 `docs/audit/r4-glass-audit.md`，本文只引用其结论并标 G-L1/G-L2/G-L3。

---

## 一、本轮定位

R3 解决的是**结构性问题**（骨架、尺寸、玻璃层级、搜索退出动画、组件替换）。R4 不再问"有没有用 MIUIX 组件"，而是问：

1. 用的是否正确（按压 / 长按 / 选中 / 禁用 / 滚动 / 转场五类微交互）。
2. 动画是否完整、是否由统一进度驱动。
3. 是否还有普通 Compose 组件在"模拟" MIUIX。
4. 文案是否像成熟产品而不是 AI 说明书。
5. GitHub 发布页是否去 AI 味。

判据是 MIUIX 官方源码与官方 example 的实际实现（file:line），以及 Mishka / InstallerX 的成熟实现；不凭观感改参数，也不为了"看起来更 OS4"而推翻官方层级。

---

## 二、代码基线核对

### 2.1 仓库状态（审计开始时）

| 项 | 值 |
|---|---|
| 工作目录 | `C:\Users\10487\WorkBuddy\Hail\Glacier` |
| 分支 | `main`（唯一本地分支；无 `origin/main` 远端跟踪引用） |
| 工作区状态 | 干净（`git status --short --branch` 仅输出 `## main`） |
| HEAD | `9457df123c3825447553d01bb1a1957b0033a598` — `docs: record REST release fallback for the bundled gh` |
| 最近提交 | `9457df1` docs → `3394e88 release: Glacier 1.1.0` → `b3309fe ui: align Glacier with MIUIX OS4 and OS4 Glass (round 2)` → `2f9a201 docs` → `976d825 build` → `0e7538d docs` → `967ba42 Glacier 1.0.0: 独立重建` |
| 远端 | `origin https://github.com/Ayang233/Glacier.git`（本机 `github.com` 直连 502，只有 `api.github.com` 可用） |

### 2.2 与任务书所述 R3 提交 `1caec00` 的关系（重要）

任务书要求"以实际仓库状态核对 R3 提交 `1caec00`"。核对结果：

- `1caec00` **存在**于本地对象库，但**不在当前 `main` 的历史中**；它只出现在 `git reflog`（`HEAD@{4}`）。
- reflog 序列：`1caec00`（commit: round 2）→ `2f9a201`（reset）→ `b3309fe`（commit: round 2，重新提交）。
- `git diff 1caec00 b3309fe` **为空**，两者 tree 内容完全一致。

结论：**R3 成果完整保留**，其内容以 `b3309fe` 的形式留在 `main` 历史里；`3394e88`（发布 1.1.0）与 `9457df1`（发布流程文档）都建立在其之上。本轮基线 = `9457df1`，R3 内容 = `b3309fe`，不存在 R3 被覆盖或丢失的情况。

### 2.3 构建与运行环境

| 项 | 值 |
|---|---|
| JDK | 21.0.12.1 LTS |
| Gradle | 9.9.1（wrapper） |
| Android SDK | `C:\Users\10487\AppData\Local\Android\Sdk`（platforms 含 36/37.x，build-tools 37.0.0） |
| 模拟器 | AVD `android16`，`emulator-5554`，API 36，1080×2400 / 420dpi → 411dp 宽 |
| 已装包 | `com.ayang.glacier`（release，versionCode 2 / 1.1.0，R4 构建覆盖安装）；`com.ayang.glacier.debug` |
| 字体缩放 | `font_scale = 1.0`（大字体验收时切换） |
| GPU | `ro.hardware.egl = emulation`（SwiftShader 路径） |

> 注意一个容易误判的事实：MIUIX 的 `isRuntimeShaderSupported()` 实现是 `Build.VERSION.SDK_INT >= TIRAMISU`
> （`miuix-material/src/androidMain/kotlin/top/yukonga/miuix/kmp/material/RuntimeShader.android.kt:18`），
> 所以 **API 36 模拟器上 Glass 是"开启且生效"的**（`GlacierShell.kt` 的 `glassActive = preferences.glassEnabled && isRuntimeShaderSupported()`）。
> R3 报告里"模拟器只能验证 L2"的说法正确，但不能据此认为模拟器上 Glass 处于关闭档；L3 的限制来自"没有真实 GPU 光学"，不是"组件没启用"。

### 2.4 证据等级约定

| 等级 | 含义 | 可复现方式 |
|---|---|---|
| **E1** | 源码证据：代码路径 + MIUIX 官方源码 file:line 对照 | 阅读文件即可复现 |
| **E2** | 模拟器运行证据：截图 / uiautomator dump / logcat | 安装本轮 APK 后按 `docs/verification/miuix-os4-r4/README.md` 步骤复现 |
| **E3** | 真实 GPU 光学证据（Glass L3） | **本轮不具备**（模拟器 SwiftShader），不得标为通过 |

Glass 另有 L1/L2/L3 分级，定义与逐项判定见 `docs/audit/r4-glass-audit.md`。

---

## 三、取证方式

1. **基线取证**：整改前用 R3 构建（release 1.1.0）在模拟器上实拍缺陷现场，作为"修改前"证据（`docs/verification/miuix-os4-r4/before/`）：
   - `01-longpress-square-overlay-fading.png`：Home 长按应用后，**整格方角灰块**压在图标 + 文字上（松手 spring 淡出的中间态）。
   - `02-selection-mode-r3.png`：选择态（squircle 蓝色描边）——证明描边本身是圆角，方角只来自按压/长按指示层。
   - `03-tab-selected-black-light.png`：浅色下标签行选中项是**近黑实心胶囊 + 白字**，未选中项是白色胶囊。
2. **源码取证**：每条问题追到 file:line，并与官方源码逐行对照（官方 example 是"应该是怎样"的唯一权威）。
3. **运行取证**：整改后重新构建、安装、逐场景截图（含 Glass 开/关、深/浅色、竖/横屏、大字体、动画中间帧）。
4. **不做的事**：不把 R2/R3 旧截图当作 R4 证据；不在没有真机 GPU 时声称 L3 通过；不把"组件已接线"等同于"效果正确"。

---

## 四、R3 完成度复核（结论）

完整逐项重判表见 `docs/audit/r4-glass-audit.md` §9（`miuix-os4-audit.md` §17 的 15 项逐条重判）。结论摘要：

| 分类 | 项 | 复核结论 |
|---|---|---|
| R3 确实完成且证据充分 | P0-1 底栏宽度/内边距、P1-5 搜索结果行、P1-7 Dialog 按钮、P1-8 标签删除入口、P1-12 About 品牌区尺寸、P2-11 版本去重 | 【有截图证据】或【已实测可复现】，本轮不重复整改 |
| R3 完成但证据不足 | P0-2 FAB 让位数值（只有算账文字）、P0-5 空态 padding（无空态截图）、P1-4 间距 token、P1-10 分页 spring、P1-1 弹层退场动画/ P0-4 搜索退场动画（只有源码 + 单帧） | **本轮补齐或明确标注待验证**（见 §8） |
| R3 完成但**当时未识别缺陷** | P0-3 宽屏：`railInset` 与页面/顶栏 start 安全区双算（R3 把"多让一段"当成正确让位） | **本轮修复**（§5 P1-1） |
| R3 明确未完成 | P1-9 页面滚动修饰、P1-11 二级页转场、P1-15 Apps 多选、About 滚动淡出/缩放、操作面板大字体 | **本轮：前 2 项实现；About 实现；Apps 多选给明确决策；面板大字体实现** |
| R3 结论仍然成立 | L3 未验证 | **本轮维持未验证**（G-L3） |
| R3 不能自动继承的结论 | "L2 通过" | **本轮重做**：Shell/PageFrame 绘制路径已变，必须在 R4 构建上重拍（见 §8） |

---

## 五、本轮问题清单与整改（P0 / P1 / P2）

> 状态说明：**已整改** = 代码已改；**已实测** = 有 E2 证据；**未验证** = 只有 E1。
> 详细证据链见 `docs/audit/r4/part-{home,about,apps,settings,shell}.md`。

### P0-1 主页长按应用出现正方形叠层（已整改 / 已实测 E2）

**现象**：Home 长按应用进入选择态时，出现方角灰块覆盖整个网格单元（含应用名），与 squircle 语言冲突。

**根因（逐层核验，单一来源）**：`HomeAppItem` 的整格 `Column` 直接 `combinedClickable`，
未做任何裁切；`combinedClickable` 默认 `indication = LocalIndication`，而 MIUIX 主题把它设成
`MiuixIndication`（`miuix-ui/.../theme/MiuixTheme.kt:33,37,63,67`），后者绘制就是
`drawRect(color, alpha, size)` —— 一整块**无圆角矩形**（`utils/MiuixIndication.kt:129-135`，
KDoc 第 36 行原文 "draws a rectangular overlay"），按压 0.10、按住 0.10 的 alpha 叠在整格上。
排除项：无自定义 Ripple、无选择态背景、无长按额外覆盖层；选择态的 squircle 描边本身是圆角（`before/02` 可见）。
`combinedClickable` 不产生 `HoldDownInteraction`，所以不存在"0.20 双层叠加"。

**整改**：照官方图标网格的顺序 **先裁切再 clickable**——`fillMaxWidth → squircleClip(GridAppIcon × AppIconCornerRatio = 12.32dp) → combinedClickable`
（`HomeScreen.kt` 的 `HomeAppItem`）。没有把热区缩到图标上、没有 `indication = null`：
官方就是整格（含标签）可点 + 裁切，`squircleClip` 只裁绘制不裁命中测试，热区与之前完全一致。

**官方依据**：`example/IconPage.kt:403-411`（`squircleClip(12.dp)` → `clickable{}` → `padding(...)`）；
MIUIX 自己的 `basic/TabRow.kt:410-418` 同款顺序（`squircleClip(cornerRadius)` 包住带 indication 的 `selectable`）。

**实测**：`docs/verification/miuix-os4-r4/` 中长按按住帧、松手选择态帧（叠层为圆角、不越到相邻格、无缩放/位移/描边）。

### P0-2 主页标签选中项黑色底（已整改 / 已实测 E2）

**现象**：Home 顶部标签行，选中项是近黑实心胶囊 + 白字。

**排查结论（分别回答问题）**：
1. **黑色底色是 MIUIX 官方默认行为吗？** 颜色值本身是官方的，但**档位用错**。Glacier 用的是
   `GlassTabRow(colors = GlassTabRowDefaults.neutralColors())`，neutral 档的选中容器在浅色下是
   `Color(0xFF383838)`、深色下 `0xFFE6E6E6`（`miuix-glass/.../GlassTabRow.kt:218-262`）——
   这不是 Glacier 自己硬编码的颜色。
2. **是 Glacier 自己设的吗？** 是 Glacier 选择传了 `neutralColors()`（`HomeScreen.kt` 旧代码 + 注释），
   但传的是官方预设，不是自定义色值。
3. **是玻璃材质还是选中指示器？** 是**选中指示器容器色**；未选中项的白色胶囊是 official resting fill，
   与玻璃材质无关（`GlassTabRow.kt:236-253`）。
4. **官方推荐效果？** neutral 是"页面内容已有很强强调色时"的单色特例档，官方示例只用在组件演示页，
   且要求配 `NeutralHeight = 35dp`（`GlassTabRow.kt:119-120,210-217`；`docs/zh_CN/.../glasstabrow.md:44-58`）。
   官方**页面级**标签（"顶栏 bottomContent + 内容"的结构，Glacier Home 正是这种）用：
   玻璃档 `GlassSegmentedTabRow`（`example/IconPage.kt:274-285`，Mishka 生产应用 `ProviderScreen.kt:133-146` 同款），
   非玻璃档 `TabRow`（`IconPage.kt:308-315`）。
5. **深色模式适配？** neutral 深色是白胶囊，官方有适配；但"黑/白实心胶囊"本身不是页签该有的视觉重量。

**整改**：换成官方页面级标签范式——Glass 开 = `GlassSegmentedTabRow`（相连轨道 + 滑动指示器，
选中态是 6%/13.3% 半透明指示器，文字才是强调元素），`surfaceAlpha` 按"内容是否滚进顶栏"给
（`IconPage.kt:283` 同义）；Glass 关 = 官方 `TabRow`。**未新增任何硬编码颜色**，全部走组件默认 token。
四个状态（浅/深 × Glass 开/关）的取值与结论见 `docs/audit/r4/part-home.md`。

**实测**：浅色首次启动截图（选中项为半透明指示器、无实心黑块）；四状态截图见验收目录。

### P0-3 About 页品牌区滚动联动动效（已整改 / 已实测 E2）

**现状（整改前）**：品牌区是静态布局，只有顶栏随滚动折叠；图标/名称/版本不参与任何动画。R3 §17.5 已承认未实现。

**整改**：按官方 `AboutPage.kt` 重建为"头图 + 列表"结构——
品牌区固定在列表下方，`LazyColumn` 第一个 item 是 `item(key = "logoSpacer")` 透明占位（官方 `:318-339` 的高度公式，
唯一补偿是减掉本页 `spacedBy(12dp)` 的 12dp）；**唯一进度源**为 `scrollProgress = derivedStateOf { index > 0 ? 1f : firstVisibleItemScrollOffset / logoSpacer.size }`（官方 `:94-109`），
三层元素在 `graphicsLayer` 绘制期读同一进度：
- 版本：`t = ((p − 0.05) / 0.15)`，`alpha = 1 − t`，`scale = 1 − t×0.05`（官方 `:292-304`）
- 名称：`t = ((p − 0.20) / 0.15)`，同上（`:262-291`）
- 图标：`t = ((p − 0.35) / 0.15)`，同样缩放淡出，容器 88dp / `RoundedCornerShape(24.dp)` / 白底 / 图 74dp（`:242-261`）
- 顶栏标题：`alpha = ((p − 0.35) / 0.65)`（`:127`），经 PageFrame 新增的 `topBarTitleAlpha` 同时作用于玻璃/非玻璃两档
- 品牌区上边距改为官方 92dp（40 extraTop + 52 logoContentPadding，`:157`、`:180-185`）

**未移植（明确理由）**：官方 `BgEffectBackground` 动态背景与品牌图 `textureBlur`（`:225-232`、`:271-286`）
——Glacier 正文本身是玻璃录制层，页面内再采 backdrop 会自采样崩溃；官方 blur 采的是它自己的 BgEffect 层，Glacier 没有该层。

**实测**：滚动六阶段截图（初始 / 1/4 / 1/2 / 将折叠 / 完全折叠 / 回滚）见验收目录。

### P1-1 宽屏 start 安全区双算（已整改 / 已实测 E2）

玻璃专项审计在复核宽屏时发现（`r4-glass-audit.md` §6.5 P1-1）：`railInset` 已把
「侧栏宽度 + start 侧系统 inset」扣掉（`GlacierShell.kt`），但页面内 `pageContentPadding` 又让一次
同一块 cutout，玻璃顶栏默认 inset padding 再让一次 → 刘海横屏设备上内容被多推一个 cutout，
展开态面板与内容之间多一段空白；R3 截图把它当成"正确让位"记录。

**整改**：引入与官方 `startAlreadyReserved` 同语义的 `LocalPageStartReserved`（Shell 宽屏时 provide true），
`pageContentPadding` 在 reserved 时不再叠加 start 侧 cutout（`PageUtils.kt:99-103`：
为 true 时 `safeInsets` 只保留 End + Vertical）。

**实测（E2）**：修复前横屏折叠态内容左缘 288px（Apps 256px），修复后 **160px = 128px cutout + 12dp(32px)**，
与期望值完全吻合；右缘 2368px 不变（未被多切）；展开态左缘 848px（=160+侧栏 688px），
展开/折叠位移两页一致 688px。证据 `53`–`57`。**顶栏与搜索浮层仍有同类 start cutout 叠加（纯缩进、不遮挡），
属已知未改偏差**，见 §9。

### P1-2 页面滚动修饰（已整改 / 已实测 E2）

R3 未完成项（§17.5 P1-9）。本轮按官方 `PageUtils.kt` 的 `pageScrollModifiers` 语义落地：
- **overscroll 弹性**：视觉弹性原本由库主题提供，缺的是 `overscrollOffset` 宿主接线（玻璃顶栏的弹性材质要读它）。
  已接 `scrollEndHaptic → overScrollVertical(onOffsetChanged → scrollBehavior.state.overscrollOffset) → nestedScroll`，
  并用 foundation 字节码核对 dispatch 顺序，确保祖先消费后容器自身不会二次拉伸（不双重回弹）。
- **滚动结束触觉**：官方设置项默认 `true`（`example/AppState.kt:35`），Glacier 无该设置项 → 按官方默认开启（`scrollEndHaptic` 参数可覆盖）。
- **滚动条**：官方**非默认**能力，是每页显式添加（`MainPage.kt:341-345`）。PageFrame 新增 `scrollBarAdapter` 入口并自动用 Scaffold padding 作轨道内边距；
  Home / Apps / Settings / About / Licenses 已接线。
- **入口**：既有 `Modifier.pageScrollModifiers(...)` 公开扩展，也有 PageFrame 统一接管（页面无需改动）；`pageScrollOnContent = true` 供"头图不跟随弹性"的页面自挂。

### P1-3 二级页转场特效（已整改 / 已实测 E2）

R3 未完成项（§17.5 P1-11）。按官方 `AppContent.kt:256-305` 接 `NavDisplayEffects`：
`enableCornerClip`、`cornerClipRadius = 窄屏 rememberNavSystemCornerRadius() / 宽屏 0dp`、`cornerClipMode = Leading`、
`dimAmount = 0.5f`、`backdropColor = surface`；`transition = NavTransitions.MiuixDefault`；
宽屏 pane 加 `clipToBounds()`（否则滑入页会画到玻璃侧栏下面）。
**滑动返回**：二级页（About/Licenses）启用并按 RTL 镜像物理方向；一级页显式 `None`（横向手势归 HorizontalPager，官方示例默认也是关闭）。

### P1-4 返回箭头与 RTL（已整改）

About/Licenses 原来用通用 `TopBarAction` 当返回按钮：非玻璃档图标/尺寸不符、RTL 不镜像。
新增 `BackNavigationAction`（玻璃档 28dp `ChevronBackward`、非玻璃档 24dp `MiuixIcons.Back`，RTL `scaleX=-1`），
对齐官方 `example/component/BackNavigationIcon.kt:29-43`。

### P1-5 Apps / 搜索行统一与按压反馈（已整改 / 已实测 E2）

- 列表行与搜索行合并为**同一实现** `AppListCardRow`（`Card` 不注册点击 + 行内官方 `BasicComponent(onClick)`），
  只有一层 indication，并被 `Card.squircleSurface` 裁成圆角（`SquircleBackground.kt:124-165`），无方形按压层、无双层点击、无 Sink 缩放抖动。
- 行摘要统一为 `appRowSummary`（包名 + 置顶/白名单/不在主页徽标），Home 搜索/Apps 列表/Apps 搜索三处一致。
- 官方依据：`example/IconPage.kt:221-244`。

### P1-6 应用操作面板大字体（已整改 / 已实测 E2）

R3 遗留（P1-6 部分整改、§17.5 未验证）：操作面板 10 行 `Column`，2.0x 字体或横屏会被 `heightIn(max)` 裁掉。
改为 `LazyColumn` + `Card` 分组 + 导航栏 `contentPadding`，可滚动到「卸载」。
排序/筛选弹层同步改为官方分组行 + `RadioButtonPreference` / `SwitchPreference`。
另：Apps 页两个"点了没反应"的行（标签、延时冻结）通过 `unsupportedActions` 不再渲染（§7.4 红线）。

### P1-7 Settings 依赖项与权限门控（已整改 / 部分 E2）

- 依赖"自动冻结"的 4 行由"置灰但无说明"改为官方 dependent-option 范式 `AnimatedVisibility(visible = autoFreezeEnabled)`
  （`example/SettingsPage.kt:257-265/275-283/294-318`）。
- 两个"看起来可点、实际静默失效"的开关（跳过前台应用 / 跳过有通知应用）改为**禁用 + summary 说明缺哪项权限**，
  权限状态在 ON_RESUME 重读（参考 InstallerX `MiuixPrivPage.kt:136-180`）。
- 玻璃效果开关做能力门控（不支持 RuntimeShader 时不显示，`SettingsPage.kt:210-229`）；无图标包时图标包行收起。
- 分节标题左边距 40dp → 官方 28dp（`GroupedCardItems.kt:24-58`）。
- 组合期不再在主线程跑 `RootBackend` 的 `su whoami`。

### P1-8 ApiActivity 采样语义与窗口不透明（已整改 / 已实测 E2）

- 窗口底色从 `surface.copy(alpha = 0.92f)` 改为**不透明** `surface`：录制层先 `drawRect(surface)` 再录内容，
  可见像素 = 录制像素（旧实现"录不透明、显示 8% 透明"是 L1 采样语义错位）。
- 主题从 `Theme.Glacier.Translucent`（`windowIsTranslucent=true` + 透明 `windowBackground`）改为 `Theme.Glacier.Api`（不透明、`windowBackground=@color/glacier_window_background`，含 night 变体），
  消除首帧/窗口动画透出宿主。
- 新增解析期 `InfiniteProgressIndicator` 加载态；错误弹层不再把 `stackTraceToString()` 塞给用户；
  操作面板 `inHome` 改按真实归属（修复深链应用显示 "Remove from Home"）；接入 `unsupportedActions`。

### P1-9 图标管线健壮性（已整改 / 证据 E1）

`AppIcons.kt` 第三方图标包路径：原来 `BitmapFactory.decodeResource` 按原尺寸解码（192–512px 图标包在主页一屏几十个图标时内存成倍增长，低端机 OOM 风险），
且 XML/Vector 图标包直接返回 null；`appfilter.xml` 解析依赖属性顺序。
现在：先读界算 `inSampleSize` 再解码并缩放到目标像素；非位图资源回退 `getDrawable` + 画到目标尺寸；
按属性名（`component` / `drawable`）只在 `item` 标签上匹配。

### P1-10 图标与文字一致性（已整改 / 已实测 E2）

- 三处共用同一 `AppIconView`（同一 squircle 比例 `0.22`、同一取图管线）。
- 选中环从"`squircleBorder + padding(2.dp)` 放在 size 内层"改为"固定尺寸 Box + 图标铺满 + `matchParentSize` 描边叠加"，
  描边宽 0→2dp 动画：消除选中瞬间图标 56→52dp、描边半径 12.32→14.32 的跳变与不平行。
- 新增 `AppLabelTextStyle`：网格标签与列表行同为 Medium 字重，只差字号（可被设置覆盖）。

### P2-1 文案去 AI 味（已整改）

应用内 51 条文案（中英各一套）逐条精简，key 与占位符零变化；另修 3 处代码级文案接线错误
（设备管理器 description 用 `action_lock`、复制命令 toast 用 `ok`、延时冻结 toast 用菜单标题）。
逐条前后对照见 `docs/audit/r4-copy-audit.md`。

### P2-2 死代码/自采样陷阱清理（已整改）

玻璃审计发现的 `LocalPageBackdrop`（provider 在录制层子树内，谁读谁自采样崩溃）已删除；
无消费者的 `LocalRailInset`、`LocalGlassRequested` 一并清理；HomeScreen 未使用的 `LocalRootBackdrop` 引用删除。

### P2-3 README / 发布页（README 已改；GitHub 线上未改，需授权）

- `README.md`：新增「下载与安装」「使用前」（默认 default 模式不冻结、需选后端并授权），删除内部推送工作流说明，精简空洞宣传。
- **GitHub 线上未做任何修改**（Description / README / Release 标题与正文 / topics 一个字符未动）。
  只做了只读核查：账号 `Ayang233` 对仓库有 `{admin:true, push:true}` 权限；1.1.0 Release 正文与 `docs/release-notes/1.1.0.md` 逐字一致；
  线上 APK digest = 本地 `SHA256SUMS.txt` = `0dd912ad…c1c3`。
  可直接粘贴的新文案（Description / Release 正文 / README 段落）已写入 `docs/audit/r4-copy-audit.md`，
  **未经用户明确授权不创建 Release、不 push、不改线上页面**。

### 本轮未发现 / 已复核无需改

- 宽屏断点公式：`isWideLayout()` 与官方 `shouldShowSplitPane()` 逐字相同（`>=840 || (>=600 && h/w<1.2)`），R3 已对齐。
- 主题：无自建色板，`MiuixTheme(ThemeController)` 唯一入口。
- Glass 消费点自采样：G-L1 复核通过（详见 `r4-glass-audit.md` §5.3）。

---

## 六、构建与运行回归

### 6.1 构建

| 命令 | 结果 | 产物 |
|---|---|---|
| `./gradlew :app:assembleDebug` | **BUILD SUCCESSFUL** | `app/build/outputs/apk/debug/Glacier-debug.apk`（约 49.9 MB） |
| `./gradlew :app:assembleRelease` | **BUILD SUCCESSFUL**（R8） | `app/build/outputs/apk/release/Glacier-release.apk`（4,287,928 B，SHA-256 `AB4B9234A34C608A960CA5ACC1C9A612A953E7817B80313193EB235597AC0F22`） |

- 最终 APK 与"通过 delta 复核的 APK"的差异只有两处**行为等价**改动：`ApiActivity` 的 `GlassDialog(backdrop = …takeIf { LocalGlassActive.current })`（provider 在非玻璃档本来就提供 null，行为不变）与若干 KDoc/注释；
  最终 APK 已重新安装并冒烟通过（`66-final-build-smoke.png`，数据保留）。
- 构建过程中没有出现 K2/Compose 编译器错误；未引入任何新依赖（文案/代码/资源改动均在既有依赖内完成）。

### 6.2 运行回归（最终 APK + delta 复核）

| 检查项 | 结果 | 证据 |
|---|---|---|
| 启动 / 崩溃 | **无崩溃**：整轮 `logcat -b crash` 0 行，主 logcat `FATAL EXCEPTION`/`ANR` 0 命中，dropbox 无本轮记录 | `docs/verification/miuix-os4-r4/README.md` §12 / D 节 |
| 搜索关闭 BUG 回归 | **通过**：Home/Apps 关闭搜索后无残留遮罩、`mInputShown=false` | `19`、`22` |
| 图标显示 | **通过**：自适应图标占比、第三方图标原色、选中环平滑 | `01`–`06`、`62` |
| 页面与弹层 | **通过**：About/Licenses/搜索/操作面板/排序/筛选/Dialog/BottomSheet 全部可用；2.0x 下操作单「卸载」直接可见 | `27`–`36`、`58`–`61` |
| 深浅色 / 竖横屏 / 大字体 | **通过**：浅/深 × Glass 开/关标签四态、深色四页、1.3x/2.0x 三页、横屏折叠/展开两态 | `07`–`10`、`37`–`45`、`53`–`57` |
| 转场 | **通过**：push/pop 真实中间帧（拆帧）无白闪/黑帧/残影；系统圆角与 scrim 正常 | `23`–`26` |
| 业务功能回归 | **未回归**：本次改动全部在 UI 层（`feature/`、`ui/`、`res/`、`platform/AppIcons.kt` 解码路径）；`data/`、`domain/`、`system/` 六条冻结后端零改动 | `git diff` 范围 |
| 冻结写操作 | **仅 L1** | 模拟器无 Root/Shizuku/设备所有者/Island 等后端，无法验证真实冻结（R3 已记录，本轮不变） |

---

## 七、逐项专项结论（对应任务书第四～十二节）

| 专项 | 结论 |
|---|---|
| 一、主页长按正方形叠层 | **已定位并修复**（MiuixIndication 无圆角 `drawRect` + 整格无裁切；改为官方 `squircleClip → clickable` 顺序）。见 P0-1 |
| 二、标签选中黑色底 | **已定位并修复**（官方 neutral 档用错位置；换官方页面级标签 `GlassSegmentedTabRow` / `TabRow`），四状态均有源码取值依据。见 P0-2 |
| 三、About 动态效果 | **已完整实现**统一滚动进度驱动的三层错峰淡出+缩放+顶栏标题淡入；官方 `BgEffectBackground`/`textureBlur` 有明确不移植理由。见 P0-3 |
| 四、页面微交互 | 按压/长按/选中/禁用/滚动/转场六类逐项处理：Home 叠层、Apps 双层点击与假可点行、Settings 依赖项与权限门控、弹层大字体可滚、滚动修饰、转场特效、返回箭头 RTL。见 P1-1~P1-7、P1-10 |
| 五、Glass 是否真正完整 | L1 **通过**；L2 **本轮在新构建上重做并通过**；L3 **未验证**（无真实 GPU，不得标通过）。详见 `r4-glass-audit.md` |
| 六、图标与主页排版 | 三处统一 `AppIconView`/`AppLabelTextStyle`；选中环跳变修复；图标包解码与 appfilter 解析健壮性修复。见 P1-9、P1-10 |
| 七、文案精简 | 51 条中英文案重写 + 3 处代码级接线修复；许可证原文未改；未删用户必需信息。见 `r4-copy-audit.md` |
| 八、GitHub 发布页去 AI 味 | 只读核查完成，问题清单 + 可直接粘贴文案已给出；**线上未改**（需授权）。见 P2-3 |

---

## 八、保留的自定义实现（连同理由）

| 实现 | 位置 | 保留理由 |
|---|---|---|
| 空状态 `EmptyState` | `ui/components/PageFrame.kt` | MIUIX 无空状态组件；只用官方 `Text` 排版 |
| 应用网格 `LazyVerticalGrid` + `HomeAppItem` | `feature/home/HomeScreen.kt` | MIUIX 是组件库，不含启动器网格；网格尺寸/列数照官方图标网格公式，按压与选中走官方 squircle |
| `PageMetrics` | `ui/components/PageFrame.kt` | 官方无页面级 spacing token；仅若干常量，圆角取官方 `CardDefaults.CornerRadius` |
| 系统 Toast（`UiFeedback`） | `platform/UiFeedback.kt` | 大量调用点发生在无 Compose 界面的上下文（磁贴、快捷方式、权限回调、服务），系统 Toast 是唯一一致出口 |
| 第三方图标取图管线 | `platform/AppIcons.kt` | 业务能力（去色/图标包/自适应合成），只改"如何得到位图"；本轮修健壮性，不改 API |
| `isWideLayout` / `isExtraWideLayout` | `ui/GlassEnvironment.kt` | 复刻官方断点语义；**已知偏差**：取屏幕尺寸而非 `LocalWindowInfo.containerSize`，分屏/自由窗口下会误判（P2，未改，见 §9） |
| `AppListCardRow` / `appRowSummary` | `feature/common/AppSearchResults.kt` | 官方没有"应用行"组件；本身是 `Card + BasicComponent` 组合，不是新设计系统 |
| `BackNavigationAction` | `ui/components/PageFrame.kt` | 官方 `BackNavigationIcon` 是 example 组件（不在库里），按官方实现复刻（28/24dp + RTL 镜像） |
| `Modifier.railStartInset` | `ui/components/PageFrame.kt` | 官方 pane 布局由 example 宿主实现，库不提供；语义与官方 pane 一致 |

---

## 九、仍未完成 / 未验证（禁止用"基本完成"表述）

| 项 | 状态 | 原因 / 后续 |
|---|---|---|
| **Glass L3 光学** | **未验证** | 模拟器 `ro.hardware.egl = emulation`，SwiftShader 无真实折射/边缘光；且 `Platform.isEmulator == false` 才会开顶栏 `progressiveBlur`。需 HyperOS/OS4 真机 + 录屏 |
| **Glass L2（R4 新构建）** | **已实测（模拟器，E2）** | 65 张截图覆盖 12 组场景 + 转场 `screenrecord` 30fps 拆帧；整轮 `logcat -b crash` 0 行、`FATAL/ANR` 0 命中。逐项见 `docs/verification/miuix-os4-r4/README.md`；结论见 `docs/audit/r4-glass-audit.md` §7.2 |
| 刘海/挖孔真机上的宽屏 start 让位 | **模拟器上已实测数值；刘海真机未验证** | 无刘海模拟器上，P1-1 修复效果仍可用"人为 cutout"口径量到：折叠态内容左缘 288px/256px → **160px**（= 128 cutout + 12dp），右缘 2368px 不变；展开/折叠位移两页一致 688px（证据 `53`–`57`）。真实刘海设备仍需复核 |
| 宽屏顶栏与搜索浮层的 start cutout | **已知偏差（未改）** | Glass 审计 P1-4：`pageContentPadding` 已修，但 `GlassTopAppBar`/`TopAppBar` 的 `defaultWindowInsetsPadding` 与搜索/结果列表的 `displayCutout` 横向 inset 仍各自再加一次 → 宽屏+刘海设备上顶栏/搜索会比正文多右移一个 cutout（约 128px，纯缩进，不遮挡）。组件内部 inset 无法按边关闭，硬改需关闭整组 inset 并自绘背景，风险大于收益；修复方向见 `r4-glass-audit.md` §6.5 |
| 横屏折叠态页面标题不可见 | **官方语义，不改** | `titleAlpha` 跟随侧栏 morph（官方 `AppContent.kt:217-223`：`if (useGlassSidebar) sidebarState.progress else 1f`；`PageUtils.kt:254-266` 相乘）。折叠胶囊占据顶栏标题位，官方 840–1200dp 即此形态；动作按钮不受影响。已把 `PageFrame` 里"宽屏自动折叠成紧凑标题"的旧注释改正 |
| 分屏 / 自由窗口 / 折叠屏断点 | 未验证 | `isWideLayout` 用屏幕尺寸而非 `LocalWindowInfo.containerSize`（P2 已知偏差）；需真机多窗口场景 |
| 三键导航底部安全区 | 未验证 | 模拟器为手势导航 |
| 第三方图标包实测 | 未验证 | 只做了代码健壮性修复（E1），未安装真实图标包实测 |
| RTL 全量实拍 | 未验证 | 代码层已做（返回箭头镜像、滑动返回方向、`calculateStartPadding`）；未切 RTL 实测截图 |
| 真机 HyperOS 自适应图标遮罩 | 未验证 | 模拟器桌面遮罩与 HyperOS 不同 |
| Apps 多选 | **本轮不做**（明确决策） | 不能只做半套 Home 选择语言：`EditTags`/`DeferredFreeze` 依赖 Home 私有弹层，批量反馈 `reportBatch` 是 Home private；R3 已判为功能增量。落地方案（含 `AppRow.selected` 已就绪）写在 `docs/audit/r4/part-apps.md` §3 |
| 弹层 backdrop 不含 Shell 底栏/侧栏像素 | 记录性偏差（P2） | 与官方 `IconPage.kt:448-450` 实践一致，仅 L3 可见；修复需按官方两层合成 backdrop（`GlassPage.kt:152-168`），本轮不改结构 |
| 标签数 > 4 时的标签行观感 | 未验证 | 官方 segmented 轨道不适合过多标签，代码里回退 `TabRow`；未实拍 |
| 同名标签 | 不处理 | `GlassSegmentedTabRow`/`GlassTabRow` 内部 `key(label)` 的重复 key 是组件既有约束 |
| 15 个无引用 string key | 保留（未删） | 需先确认没有并行改动引用；删除是纯清理，不影响功能（清单见 `r4-copy-audit.md` §1.4） |
| ApiActivity 第二个深链（`onNewIntent`） | 未改（P2） | `singleInstance` 窗口存活期第二个深链会丢；修复需提升状态机，建议真机验证后再做 |
| GitHub 线上发布页文案 | **未改**（需授权） | 只读核查完成，粘贴版在 `r4-copy-audit.md` |
| **冷启动首帧 Home 标签行可能未渲染** | 观察项（P3，未修） | 最终 APK 上观察到 2 次（升级后首启、00:05 冷启动），随后 7 次冷启动均 <1.2s 正常显示两个标签；数据未丢失。判定为"首帧 snapshot 就绪时序 + 无加载骨架"，与 R4 四处改动无关；后续可加骨架屏或把标签行改为不吃 snapshot 的空列表 |
| **About 滚到底品牌占位仍留 ≈200px 空白** | 记录（非缺陷） | 官方 `AboutPage` 的 `logoSpacer` 同款结构（占位高度大于内容高度），滚动到边界时品牌区下方留白属官方行为 |
| **2.0x 字体时 Settings 内容可滚到 Glass 底栏之下** | 记录（正常叠层） | 悬浮玻璃导航栏 + 内容滚动的正常层级；未见最后一项滚不出的证据 |
| 弹层退场动画 / 分页 spring / 搜索退场动画 | 过程证据部分补足 | 转场 push/pop 已用 `screenrecord` + ffmpeg 拆帧验证（`23`–`26`、`52`）；弹层退场与分页 spring 仍只有源码 + 静态帧，未逐帧录屏 |

---

## 十、一句话结论

R4 把 R3 留下的"结构正确但不成熟"补成了**可指认的成熟度**：三个用户直接指出的 P0（长按方角叠层、标签黑色选中底、About 静态品牌区）
都已定位到具体源码行并修复，且第一个和第二个有整改前后的实拍对比；R3 明确未完成的两项 P1（页面滚动修饰、二级页转场）已实现；
文案与发布页按"产品文案"而非"AI 说明书"重写。**但 Glass 的 L3 光学仍未验证、R3 的 L2 不能继承、刘海/分屏/RTL 需真机复核**——
这些都在 §8 如实列出，不用"整体符合"掩盖。

---

## 附：交付物索引

| 交付物 | 路径 |
|---|---|
| R4 全量审计报告 | `docs/audit/miuix-os4-r4-audit.md`（本文） |
| 修改清单（逐项：修改前/后 + 官方依据） | `docs/plan/r4-change-list.md` |
| Glass 审计（L1/L2/L3 + R3 完成度复核） | `docs/audit/r4-glass-audit.md` |
| 文案整改报告（应用内 + GitHub） | `docs/audit/r4-copy-audit.md` |
| 分专项审计明细 | `docs/audit/r4/part-{home,about,apps,settings,shell}.md` |
| 实际运行截图与实测记录 | `docs/verification/miuix-os4-r4/`（`before/` 为整改前证据） |
