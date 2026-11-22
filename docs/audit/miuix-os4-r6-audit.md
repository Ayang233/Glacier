# MIUIX OS4 / OS4 Glass 自造轮子清查与基线升级（R6）

- 审计对象：Glacier（`com.ayang.glacier`），Android 13+ 应用冻结工具
- 审计时间：2026-11-22
- 基线变更：MIUIX `9128921c` → `44f1d315`（`v0.9.5-beta03` 之后第 18 个 main 提交）
- 审计口径：**UI / 界面结构 / 动画 / 尺寸 / 间距 / 边距** —— 凡是 MIUIX 官方库或官方 example
  已经给出实现的地方，Glacier 不得再写一份；官方没有的能力（业务组合、页面骨架）保留并登记。
- 证据分级：**L1 源码接线**／**L2 运行时能力**（进程存活、组件真实挂载绘制）／**L3 像素光学**（真机 GPU）

> 本文件只回答三个问题：**扫到了什么、换成了什么、还剩什么**。
> 基线数值与升级流程见 [MIUIX_BASELINE.md](../../MIUIX_BASELINE.md)，
> 历史轮次结论见 `miuix-os4-audit.md`、`miuix-os4-glass-audit.md`、`miuix-os4-r4-audit.md`、`miuix-os4-r5-audit.md`。

---

## 1. 审计方法

1. 全量扫 `app/src/main/kotlin`（51 个文件 / 8.9k 行）中的 UI 面：
   `Modifier.layout` / `graphicsLayer` / `Canvas` / `drawBehind` / `anim*` / `tween` / `spring` /
   `RoundedCornerShape` / `WindowInsets` / 硬编码 `dp` / `sp`。
2. 对每个命中项问四个问题（沿用 R5 口径）：
   - 这是官方**库**组件吗？不是的话，官方**库**有没有等价能力？
   - 官方 example（`GlassPage` / `PageUtils` / `AppContent` / `IconPage` / `AboutPage`）是怎么写的？
   - 官方能力与当前实现是否重叠？重叠的是底层能力还是业务组合？
   - 参数是不是官方默认值？有没有把默认值抄一遍再"微调"？
3. 交叉对照三份官方材料：库源码（`miuix-ui` / `miuix-glass` / `miuix-blur`）、
   官方 example、官方迁移指南（`Mishka: docs/miuix-glass-migration.md`）与 `docs/guide/*.md`。
4. 改完后编译、跑单测，并在模拟器上按**窄屏 / 折叠侧栏 / 展开侧栏 / 二级页 / 搜索浮层**五种形态逐一看图。

---

## 2. 扫到的自造轮子（全部已替换）

| # | 原实现 | 性质 | MIUIX 官方对应 | 替换结果 |
|---|---|---|---|---|
| 1 | `Modifier.railStartInset`（`Modifier.layout` 把整个 `NavDisplay` 右移并缩窄） | 重新实现了宿主让位 | `AppPageInsets` + `Modifier.appPageHorizontalInsets()`（`example/utils/PageUtils.kt:62-152`）；迁移指南 §4.2 明确禁止 `.layout` 右移容器 | 删除。`NavDisplay` 恒 `fillMaxSize()`，侧栏宽度经 `contentInsets.start` 只作用于内容 |
| 2 | `LocalPageStartReserved` | 自造合同 | 官方 `startAlreadyReserved`（同一 `rememberAppPageInsets` 入参） | 删除。Glacier 只有悬浮玻璃侧栏，恒为 `false` |
| 3 | `LocalSearchTopInset`（自造 lambda 合同） | 自造合同 | `AppPageInsets.searchTop` | 删除。页面读 `LocalAppPageInsets.current.searchTop` |
| 4 | `pageSafeBottom()` 手拼 `navigationBars ∪ displayCutout` | 重新实现了安全区 | `rememberAppSafePadding()` = `safeInsets.asPaddingValues()` | 删除 |
| 5 | 4 处 `windowInsetsPadding(WindowInsets.displayCutout.only(Horizontal))`（顶栏 / 搜索浮层 / 搜索结果 ×2） | 重新实现了横向让位 | `Modifier.appPageHorizontalInsets()`（含刘海、横屏导航条、侧栏让位） | 全部替换 |
| 6 | 顶栏在"宽屏已让位"时用 End-only inset 补丁 + `defaultWindowInsetsPadding` 两档分支 | 绕开官方参数 | `defaultWindowInsetsPadding = false` + `Modifier.appPageTopBarInsets()`（官方 `AdaptiveTopAppBar`，`PageUtils.kt:265/282/299-304`） | 玻璃档挂 `contentModifier`，非玻璃档挂整条顶栏 |
| 7 | `pageContentPadding` 在组合期 `calculate*Padding()` | 组合期读动态值 | 官方同名函数返回**惰性** `PaddingValues`（布局期读，`PageUtils.kt:209-245`） | 换成官方形态；侧栏 morph 不再重组整页 |
| 8 | `Modifier.pageScrollModifiers(scrollBehavior = ...)`（自定参数名/顺序） | 同义重写 | 官方签名 `(enableScrollEndHaptic, showTopAppBar, topAppBarScrollBehavior)`（`PageUtils.kt:191-206`） | 逐条对齐，调用点同步 |
| 9 | 滚动条 `trackPadding` 用 Scaffold padding、未挂顶栏行为 | 参数不全 | 官方 `VerticalScrollBar(trackPadding = contentPadding, topAppBarScrollBehavior = ...)`（`MainPage.kt:359-364`） | 改为页面内容内边距 + 顶栏滚动行为 |
| 10 | 底栏自拼 `displayCutout ∪ navigationBars` 横向 inset | 重新实现了横向让位 | 官方 `bottomBar` 用 `appPageHorizontalInsets()`（`GlassPage.kt:294-300`） | 替换；24dp / 20dp / `max(24dp, safe+8dp)` 数值不变 |
| 11 | `HomeScreen.kt` 里第二份手写搜索结果列表 `HomeSearchResults`（≈65 行） | UI 重复实现 | 同一份 `AppSearchResults`（官方 `Card` + `BasicComponent` 行） | 删除，Home 直接复用 |
| 12 | 搜索结果顶部让位（Home / Apps 各一份）手拼 `statusBars + searchTop + BarHeight` | 重复公式 | 官方 `safeInsets.top + searchTop + BarHeight + VerticalPadding*2`（`IconPage.kt:537-540`） | 统一为 `safeInsets` + `searchTop` |
| 13 | 弹层底部让位 3 处手写 `WindowInsets.navigationBars.asPaddingValues()` | 重新实现了安全区 | `rememberAppSafePadding()` | 替换 |
| 14 | `AboutScreen` 品牌区在组合期读 start/end padding | 组合期读布局值 | 官方 `PaddingValues` 委托（`AboutContent.kt:180-192` 的 `logoContentPadding`） | 改为 `.padding(logoContentPadding)`，布局期读 |
| 15 | 每个页面各自 `pageContentPadding(padding)`（含参数不一致风险） | 重复计算 | PageFrame 统一算好并把**最终内容内边距**交给页面；滚动条与内容同源 | 5 个页面改为直接使用传入值；设置/许可证页用 `contentPaddingHorizontal = 0.dp` |

### 2.1 未发现问题的类别（逐类核对结论）

| 类别 | 结论 |
|---|---|
| 动画 | 全部走官方原语：`PagerNavigationSpringSpec` / `springAnimateToPage` / `GlassMotion.default()`（选中环）/ `CapturedAnimatedVisibility`（依赖项）/ 官方弹层 motion（本轮基线新增 `DialogMotion`，`GlassDialog` 默认即 Glass 预设）。无自写 spring/tween 曲线 |
| 尺寸 / 间距 / 边距 | `PageMetrics` 每条都对应官方 example 数值（12dp 卡片外边距、12dp 分节、28dp 顶栏图标、40/56dp 图标、`CardDefaults.CornerRadius`）；设置页 16dp 与 28dp 分别来自 `BasicComponentDefaults.InsideMargin` 与 `SmallTitle` 自带值 |
| 形状 | 应用图标与图标网格单元走 `squircleClip` / `squircleBorder`；玻璃表面圆角由 `GlassShape` 组件自带，无自绘 `RoundedCornerShape` 替代品（关于页的 16dp 是官方 `textureBlur` 裁切形状，数值与官方一致） |
| 绘制 | 除 `ui/effect/`（官方 `BgEffect` 逐字节移植，Apache-2.0）外无 `Canvas` / `drawBehind` 自绘 UI |
| 主题 | `MiuixTheme` + `ThemeController`，无自建色板 |

---

## 3. 基线变更（`9128921c` → `44f1d315`）

| commit | 内容 | 对 Glacier 的影响 |
|---|---|---|
| `bf61ef44` | `library: Align dialog enter and exit motion with HyperOS (#494)`：新增 `DialogMotion`（`Default` = 系统 AlertDialog，`Glass` = 桌面弹窗），`GlassDialog` 改用 `Glass`；弹层在退场 2/3 处释放触摸拦截，spring `visibilityThreshold` 1e-4 → 1e-3 | 无需改调用点：`GlassDialog` / `GlassBottomSheet` / `GlassPopUp` 的进出动画由库接管，Glacier 没有覆写 motion；弹层触摸拦截行为由库修正 |
| `44f1d315` | `build: Add googleapis maven-central for ci`：`settings.gradle.kts` / `build-logic/settings.gradle.kts` 调整仓库声明（`CI=true` 时加 Google 的 maven-central 镜像），`gradle.properties` 开 `parallel` | 与本机构建无关（`CI` 未设置）；composite build 仍用 Glacier 的仓库声明 |

无公开签名破坏、无新增 `@Deprecated`、无 Compose/Kotlin 版本变化。

---

## 4. 验证

| 项 | 结果 |
|---|---|
| `./gradlew :app:assembleDebug` | 通过（debug APK 产出） |
| `./gradlew :app:testDebugUnitTest` | 通过 |
| `tools/miuix-baseline.sh verify` | 通过（HEAD == 基线，补丁已应用） |
| 窄屏 411dp（1080×2400 @420dpi） | 顶栏标题/副标题/动作对齐；底栏胶囊 24dp 边距；空态居中 |
| 1066dp 折叠侧栏（1600×1000 @240dpi） | 顶部胶囊居中、标题按官方语义淡出；设置页卡片 12dp、`SmallTitle` 28dp；搜索条位于胶囊下方不重叠 |
| 1333dp 展开侧栏（2000×1200 @240dpi） | 侧栏满高不被裁切，内容整体右移 262dp（12dp margin + 250dp 面板）；搜索浮层同步右移；背景铺到侧栏下方 |
| 二级页（About，宽屏折叠态） | 滚动后标题出现在返回箭头右侧，不与胶囊重叠；动态背景 + 纹理模糊正常 |
| 弹层（排序/操作单） | L1 通过（`safeInsets` 与系统 `navigationBars` 在本项目等价）；**未在真机逐像素复验** |

---

## 5. 仍然保留的差异（与 R5 结论一致，无新增）

| 项 | 现状 | 原因 |
|---|---|---|
| `UiFeedback.toast()` | 仍用 Android `Toast` | 调用点含 Worker / Receiver / 快捷方式等非 UI 上下文，MIUIX `Snackbar` 需要宿主 Scaffold |
| 关于页背景调试面板 | 未移植 | 官方 example 的现场调试 UI，无产品意义 |
| Monet 动态取色 | 未接入 | 品牌一致性，属产品决策 |
| 真机玻璃光学（L3） | 未验证 | 模拟器是宿主 GPU，只能证明"有模糊像素"，不等于真机光学 |
| 冻结业务链路 | 仅源码级证据 | 模拟器无可用后端 |

> 附：本次升级发现本机 git 直连 GitHub 会被代理拒绝（`CONNECT ... 502`），
> `tools/miuix-baseline.sh` 的 `fetch` / `pin` 已加镜像回退（`MIUIX_GIT_MIRROR`，默认 ghfast.top），
> 不影响基线一致性校验（`status` / `verify` 仍只认 commit sha）。
