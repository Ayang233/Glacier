# R4 · About 页品牌区滚动联动动效（P0-3）

- 负责人：about-anim
- 范围：`Glacier/app/src/main/kotlin/com/ayang/glacier/feature/about/AboutScreen.kt`
- 官方基准：`miuix/example/shared/src/commonMain/kotlin/AboutPage.kt`（Apache-2.0）
- 参考实现（已逐段阅读）：`upstream/Mishka/app/src/main/kotlin/top/yukonga/mishka/ui/screen/settings/AboutScreen.kt`、
  `upstream/InstallerX/app/src/main/java/com/rosan/installer/ui/page/miuix/settings/preferred/about/MiuixAboutPage.kt`
- 约束遵守：未改 `PageFrame.kt` / 其他 agent 文件 / 现有 `strings*.xml`；未运行 gradle；未提交 git。

---

## 1. 结论

1. 官方 About 页的品牌区滚动联动已完整实现：**品牌图标、应用名、版本号三层由同一个 `scrollProgress` 驱动**，
   错峰淡出 + 最大 5% 缩放，没有第二个动画源。
2. 结构改成官方形态：**品牌区是固定在列表下方的头图**，`LazyColumn` 第一个 item 为透明占位
   `logoSpacer`；滚动时真实内容从头图上压过去，头图元素按进度淡出。
3. `scrollProgress` 是滚动位置的**纯函数**（无 `Animatable`/补间）：快滚、慢滚、中途停止、回滚到顶部
   都只是下一帧重新求值，不会出现“动画追不上手指”或两套动画互相拉扯。
4. 状态恢复：`rememberLazyListState()` 自带 `rememberSaveable`，Miuix `NavDisplay` 为每个导航 entry
   提供 `NavSaveableStateProvider`（`miuix-nav/.../core/NavDisplay.kt:342`、
   `miuix-nav/.../state/NavSaveableStateHolder.kt:84-85`），
   因此 About → Licenses → 返回、横竖屏切换都能恢复滚动位置；品牌区进度的分母（`logoSpacer` 高度、
   品牌区实测高度）在每次布局后重新测量。
5. 深浅色 / Glass 开 / Glass 关两档走**同一条代码路径**，没有按档位分叉的动画。

---

## 2. R3 现状（问题）

R3 的 About 品牌区是静态的，且品牌区本身是 `LazyColumn` 的第一个 item：

- `AboutScreen.kt`（R3）：`item { AboutBrandHeader() }` + `verticalArrangement = spacedBy(12.dp)`；
- 品牌区尺寸已对齐官方（88 / 24 / 74 / 35sp / 14sp / 40dp），但**没有滚动淡出/缩放**；
- R3 验收文档自认：“About 页滚动淡入/缩放动画 | 未实现”（`docs/verification/miuix-os4-r3/README.md:139`、
  `docs/audit/miuix-os4-audit.md:675`）。

本轮的根因不是“少写一个动画”，而是**结构不对**：官方要求头图是“固定层 + 透明占位”，
只有这样才能用 `firstVisibleItemScrollOffset / logoSpacer.size` 得到 0→1 的进度；
品牌区若作为普通 item，进度会变成“品牌区自身滚过的比例”，与官方语义不同。

---

## 3. 官方实现逐行对照

| 官方 `AboutPage.kt` | 官方机制 | Glacier 落点 | 状态 |
|---|---|---|---|
| `:94-109` | `scrollProgress = derivedStateOf { firstVisibleItemIndex>0 -> 1f; else firstVisibleItemScrollOffset / logoSpacer.size }` | `AboutScreen.kt:92-113` | **照搬**（只把 `"logoSpacer"` 提成常量 `LogoSpacerKey`） |
| `:117` | `collapsed = scrollProgress == 1f`（顶栏底色切换用） | 未使用 | 偏离，见 §4.4 |
| `:127` | `titleAlpha = ((p-0.35)/0.65).coerceIn(0,1)` | 未实现 | 偏离，见 §4.5，需 lead 决策 |
| `:155-162` | `AboutContent(scrollPadding, logoPadding, lazyListState, scrollProgressProvider, …)` | `AboutScreen.kt:119-246`（`PageFrame` 内容槽内完成同等接线） | 结构等价（Glacier 没有 backdrop/provider 参数） |
| `:180-190` | `logoContentPadding = logoPadding + 52dp(top)`；`listContentPadding` 保留 `scrollPadding` | `AboutScreen.kt:132-134` | **照搬**（直接按 top/start/end 施加，bottom=0 等价） |
| `:223` | `var logoHeightDp by remember { mutableStateOf(300.dp) }` | `AboutScreen.kt:116-117`、`:358` | **照搬**（默认 300dp，首帧后实测替换） |
| `:231` | `BgEffectBackground(alpha = { 1f - scrollProgress })` | 未使用 | 偏离，见 §4.3 |
| `:233-239` | 品牌 `Column(fillMaxWidth + logoContentPadding + onSizeChanged{ logoHeightDp })`，`CenterHorizontally` | `AboutScreen.kt:139-151`、`:262-265` | **照搬**（固定层，不在 LazyColumn 内） |
| `:242-254` | 图标 `Box(88dp).graphicsLayer { iconProgress=((p-0.35)/0.15); clip=true; shape=RoundedCornerShape(24dp); alpha=1-p; scale=1-p*0.05 }` | `AboutScreen.kt:266-280` | **照搬**（`BrandContainerShape` 只建一次） |
| `:255-260` | `.background(Color.White)` + `Image(74dp)` | `AboutScreen.kt:280-287` | **照搬** |
| `:262-291` | 应用名 35sp Bold，`projectNameProgress=((p-0.20)/0.15)`，alpha/scale 同上 | `AboutScreen.kt:288-301` | **照搬** |
| `:292-304` | 版本 14sp、`onSurfaceVariantSummary`、居中，`versionCodeProgress=((p-0.05)/0.15)`，alpha/scale 同上 | `AboutScreen.kt:302-319` | **照搬** |
| `:308-316` | `LazyColumn(state, contentPadding = listContentPadding)` | `AboutScreen.kt:153-158` | **照搬**（contentPadding = `pageContentPadding(padding)`） |
| `:318-339` | `item(key="logoSpacer")`，高度 `logoHeightDp + 52dp + logoPadding.top - scrollPadding.top + 126dp` | `AboutScreen.kt:163-175` | **照搬公式**，仅补偿 `-12dp`，见 §4.2 |
| `:341-445` | 信息卡（`Card` + `ArrowPreference`） | `AboutScreen.kt:177-242` | 保留 R3 的 `SmallTitle + Card` 结构（R3 已对齐，非本任务重点） |
| `:447-451` | `VerticalScrollBar` | 未使用 | 偏离，见 §4.6 |
| `:456-495` | 背景效果 BottomSheet（调 BgEffect 参数） | 未使用 | 偏离，见 §4.3 |

参考项目的数字正交验证（三份实现同源）：

- Mishka `AboutScreen.kt:92-112`（同一套 `scrollProgress` derivedStateOf）、`:187`（`logoHeightDp=300.dp`）、
  `:231-237`/`:262-267`/`:288-293`（同样的 alpha/scale）、`:315-325`（同样的 spacer 公式）；
- InstallerX `MiuixAboutPage.kt:438`（`logoHeightDp=300.dp`）、`:481-486`/`:516-521`/`:545-550`（同样的
  alpha/scale）、`:582-601`（同样的 spacer 公式，并在 spacer 上回调 `onLogoHeightChanged`）。

结论：官方数字就是 `35sp / 14sp / 0.05 / 0.15 / 0.20 / 0.35 / 88 / 24 / 74 / 52 / 40 / 126`，
本轮全部沿用，没有自创参数。

---

## 4. 采用 / 偏离的参数与理由

### 4.1 采用官方 92dp 品牌区上边距（R3 只有 40dp）

官方品牌区顶部 = `pageContentPadding(extraTop = 40.dp)`（`:157`）再 `+52.dp`（`:182`），
即相对顶栏 **92dp**。R3 只做了其中的 40dp。Mishka（`:199-203,218`）与 InstallerX（`:449-453,468`）
都是同样 `+40` 再 `+52`。本轮按官方完整采用 92dp，否则占位公式里的 `+52dp` 与
`logoPadding.top - scrollPadding.top` 会失去意义。视觉上品牌区整体下移 52dp，与官方一致。

### 4.2 `logoSpacer` 高度扣掉 12dp：`... + 126dp - PageMetrics.SectionGap`

官方 LazyColumn 没有 `spacedBy`，Glacier 页面统一用 `verticalArrangement = spacedBy(12.dp)`
（`PageMetrics.SectionGap`，R3 既有排版，本轮不动）。`arrangement` 会在占位 item 与第一个
正文 item（`SmallTitle`）之间再加 12dp，导致头图底部到正文的实际留白变成 `126+12=138dp`。
因此占位高度扣掉一个 `SectionGap`，**净视觉留白仍是官方 126dp**。
副作用：`scrollProgress` 的分母比官方公式小 12dp（约占 3-5%，随品牌区高度浮动），只影响“滚多少距离到达 p=1”，
不影响淡出参数与视觉间距；`SectionGap` 若被 shell-nav 调整，公式自动跟随，不会失配。

### 4.3 不移植 `BgEffectBackground` / 品牌图 `textureBlur` / 背景效果 BottomSheet

- 官方 `BgEffectBackground`（`:225-232`）是**动态背景层**，`alpha = 1-p` 是“背景随滚动淡出”；
  Glacier 没有动态背景（品牌方向是白底黑标，`docs/reference/reference-projects.md:333` 已定），
  页面底色就是 `MiuixTheme.colorScheme.surface`（`PageFrame.kt:142,159`）。
- 官方品牌图/应用名的 `textureBlur`（`:271-286`）采样的是这个 BgEffect 层的 `layerBackdrop`。
  Glacier 的页面正文本身就是玻璃录制层（`PageFrame.kt:184-198` 的 `layerBackdrop(backdrop)`），
  在品牌区里再采 `LocalPageBackdrop` 会变成**自采样**（`docs/architecture/glass-contract.md` 的层级约束，
  官方 example 也专门把内容层和栏层拆成两个 backdrop 才敢采）。
- 因此 Glass 档下品牌区**没有** textureBlur 需求：它是“白底 + 黑色标记 + 主题文字”，
  在玻璃录制层里正常参与录制即可。两档视觉与代码路径一致。
- 背景效果 BottomSheet（`:456-495`）是官方示例用来现场调 BgEffect 参数的调试面板，无产品意义，不移植。

### 4.4 不做顶栏底色切换（官方 `:117-126` 的 `collapsed → surface`）

Glacier `PageFrame` 的非玻璃 `TopAppBar` 默认就是 `color = MiuixTheme.colorScheme.surface`
（不透明，`PageFrame.kt:241-248` 未传 `color`），玻璃档 `GlassTopAppBar` 自带 band 材质；
官方“透明 → surface”的过渡是它自己的 `BlurredBar`/`AdaptiveTopAppBar` 组合。
在不改 `PageFrame` 的前提下，Glacier 顶栏始终是同一材质，**不需要也不存在**这个切换；
若强行加，会与 PageFrame 的顶栏材质冲突。

### 4.5 不做顶栏标题随滚动淡入（官方 `:127`）——需 lead 决策，可能交给 shell-nav

官方 `titleAlpha = ((p-0.35)/0.65)`，即初始顶栏标题不可见、滚动后淡入。Glacier 无法在不改
`PageFrame` 的前提下两档同时做到：

- 玻璃档：`PageFrame` 把 `LocalTopBarTitleAlpha`（侧栏折叠进度）传给 `GlassTopAppBar`
  （`PageFrame.kt:165-173,219-238`），About 可以在外层 `CompositionLocalProvider` 覆盖它；
- 非玻璃档：`PageFrame.kt:239-249` 调用的 `TopAppBar` **没有 `titleAlpha` 形参**
  （`miuix-ui/.../basic/TopAppBar.kt:108-126`；带 `titleAlpha` 的是内部 `TopAppBarLayout`
  与 `GlassTopAppBar`），无法注入。

若只做玻璃档，会出现“Glass 开时标题初始隐藏、Glass 关时标题常显”的两档不一致，
违反任务要求 4。因此本轮**两档都保持标题常显**（与 R3 一致，不引入新回归）。
建议（供 lead 转给 shell-nav）：`PageFrame` 增加可选参数
`titleAlpha: (() -> Float)? = null`（玻璃档乘到 `LocalTopBarTitleAlpha` 上；非玻璃档要么给
`TopAppBar` 传 `titleColor.copy(alpha=…)` 的派生实现，要么等 MIUIX 暴露形参），About 再传
`{ ((p - 0.35f) / 0.65f).coerceIn(0f, 1f) }`。本轮不改 `PageFrame`。

### 4.6 不接 `VerticalScrollBar` / 页面滚动修饰（`pageScrollModifiers`）

官方 About 用 `VerticalScrollBar`（`:447-451`）+ `pageScrollModifiers`（`:310-314`：
`scrollEndHaptic` / `overscroll` / `nestedScroll`）。这属于 R4 任务书 P1 “页面滚动修饰”，
由 shell-nav 在 `PageFrame` 统一收口；About 页在这里接会形成第二套滚动修饰入口。
本页保持 `PageFrame` 既有的 `nestedScroll(scrollBehavior)` 通道（`PageFrame.kt:190-194`），
等 shell-nav 的统一入口落地后，About 只需在调用处传入即可（无需改本文件的动效逻辑）。

### 4.7 品牌区是固定层而不是列表 item

官方 `:233-339` 的结构就是“固定头图 + 透明占位”。本轮照做，原因是：
1. 进度分母必须是 `logoSpacer`（官方语义），品牌区作为 item 时分母会变成品牌区自身高度；
2. 头图固定不滚，才能在内容压上来之前按进度淡出，避免“品牌区一边滚一边淡”的怪效果；
3. 品牌区在列表下方、列表在上方（后声明的绘制在上），透明占位让位，内容自然覆盖头图，
   不依赖任何 `alpha` 遮挡技巧。

### 4.8 首帧占位高度默认 300dp

官方 `:223` 就是 `mutableStateOf(300.dp)`，首帧后由 `onSizeChanged` 实测替换。Glacier 照搬。
若首帧可见，正文会在第二帧上移到正确位置（官方同样行为）。实测是否可见由 verifier 在截图/录屏中确认
（见 §9 风险）。

---

## 5. 动效正确性分析

### 5.1 统一进度、无第二动画源

`scrollProgress` 只有一个 `derivedStateOf`（`AboutScreen.kt:96-113`），三层元素只读它：

- 版本：`(p-0.05)/0.15`，`p=0.05` 开始、`p=0.20` 完全消失；
- 名称：`(p-0.20)/0.15`，`p=0.20` 开始、`p=0.35` 完全消失；
- 图标：`(p-0.35)/0.15`，`p=0.35` 开始、`p=0.50` 完全消失；
- 每层的 `alpha = 1-progress`、`scale = 1-progress*0.05`。

三段区间首尾相接（0.05→0.20→0.35→0.50），同一时刻最多两层在动，且都是单调变化；
不存在“某一层用 tween、另一层跟手指”导致的不协调跳动。

### 5.2 帧率级读取点

`scrollProgress()` 只在 `graphicsLayer` 的 lambda 里读（绘制阶段），滚动时**只重绘、不重组**；
唯一由它间接影响组合的是 `logoHeight`（品牌区实测高度）——它只在首次布局/转屏/字体变化时变。
`derivedStateOf` 本身也避免了下游对 `layoutInfo` 的重复读取。

### 5.3 阈值处连续

当 `logoSpacer` 完全滚出时，`firstVisibleItemIndex` 从 0 变 1，此时
`firstVisibleItemScrollOffset / spacer.size` 恰好接近 1，代码回退到常量 `1f`，
不会出现 1→0 的跳变。回滚时反向连续。

### 5.4 快速/缓慢/中途停止/回滚

进度是 `firstVisibleItemScrollOffset` 的即时函数，没有任何动画状态机：
快速 fling 时每一帧按当前位置重算；中途停住时进度停住；回滚时按反方向连续还原。
`coerceIn(0f, 1f)` 保证 overscroll 负偏移不会产生负 alpha / 缩放 >1。

### 5.5 与内容滚动的相对关系

头图固定在原位，透明占位随列表滚出；正文卡片从头图上方压过去。官方在 `p=0.5` 时三层全部
不可见，而正文通常要到 `p≈1` 才到顶，所以“内容压住品牌区”发生在品牌区已完全透明之后，
不会出现半透明品牌区与卡片叠画的脏像素。

---

## 6. 状态恢复

| 场景 | 机制 | 证据 |
|---|---|---|
| About → Licenses → 返回 About | `rememberLazyListState` 内部 `rememberSaveable`；Miuix `NavDisplay` 每个 entry 包 `NavSaveableStateProvider` | `miuix-nav/.../core/NavDisplay.kt:342`、`state/NavSaveableStateHolder.kt:84-85` |
| 横竖屏切换（Activity 重建） | 同 `rememberSaveable`；`logoHeight` 是新 `remember`，首帧默认 300dp 后由新布局实测替换；`logoSpacer` 高度按新 `scrollPadding` 重算 | `AboutScreen.kt:117,148-151,163-175` |
| 系统字体放大/缩小（Activity 重建） | 品牌区 `onSizeChanged` 重新测量，spacer 自动跟随文字行高 | 同上 |
| 返回顶部（手势/程序滚动回 0） | 进度直接由偏移求值，回到顶部即 `p=0`，三层 alpha/scale 精确还原 | `AboutScreen.kt:96-113` |
| 页面进入（初始） | `firstVisibleItemIndex=0`、offset=0 → `p=0` | 同上 |

说明：Miuix `NavDisplay` 在详情页压栈时保留 About entry 的 saveable state，因此返回后滚动位置
不丢；若当前 NavDisplay 版本在转场期间销毁了 entry 组合，saveable 仍会恢复（同一机制）。

---

## 7. 深浅色 / Glass 两档

- **颜色全部走主题**：应用名 `onBackground`、版本 `onSurfaceVariantSummary`、卡片 `CardDefaults.defaultColors()`；
  唯一固定色是品牌容器的 `Color.White`（官方 `:254` 同样写死，保证浅色/深色下品牌标记轮廓一致，
  R3 起就是这样并已注明）。
- **Glass 开**：`PageFrame` 正文包在 `layerBackdrop` 录制层里（`PageFrame.kt:184-198`），
  品牌区是录制层内的普通内容，正常参与录制；不额外采样 backdrop（避免自采样）。
- **Glass 关**：同一段组合代码，只是 `PageFrame` 不录制、顶栏换普通 `TopAppBar`。
- 品牌区的 `graphicsLayer` 只改 alpha/scale/shape/clip，不依赖玻璃能力，两档行为完全一致。
- 深浅色对动效无影响（动效只改透明度/缩放；白底品牌块在深色下同样按官方保持白色）。

---

## 8. 未实现项 / 需要 lead 决策或转交

| 项 | 状态 | 原因 / 下一步 |
|---|---|---|
| 顶栏标题随滚动淡入（官方 `:127`） | 未实现 | 需 `PageFrame` 暴露标题透明度参数；非玻璃档 `TopAppBar` 无 `titleAlpha` 形参。两档同时做才不会出现档位不一致（§4.5）。建议转给 shell-nav |
| 顶栏底色切换（官方 `:117-126`） | 不适用 | Glacier 顶栏两档都是固定材质（§4.4） |
| 品牌图 `textureBlur` / `BgEffectBackground` | 不实现 | Glacier 无动态背景；正文是录制层，再采样会自采样（§4.3） |
| `VerticalScrollBar` / `pageScrollModifiers` / 触觉 / overscroll | 未接 | 属 shell-nav 的 P1 页面滚动修饰统一入口；About 等它落地后按入口参数接入（§4.6） |
| 首帧 300dp→实测高度的一帧修正 | 官方行为 | 与官方一致；若验证发现可见跳动，可再评估用 `TextMeasurer` 预算高度 |
| 首次进入时品牌区 92dp 下移（相对 R3 的 40dp） | 已采用官方 | 旧 R3 截图 `16-about-light.png` 品牌区位置会变，验收以新截图为准 |

---

## 9. 验收检查点（lead/verifier 统一截图）

### 9.1 需要产出的滚动阶段截图（浅色 + 深色；Glass 开/关各一套）

| # | 阶段 | 预期 |
|---|---|---|
| 1 | 初始（p=0） | 品牌区完整：88dp 白底圆角图标 + 74dp 标记 + 35sp Bold 名称 + 14sp 版本；三层 alpha=1、scale=1；品牌区顶部距顶栏 92dp；第一条 `SmallTitle` 在品牌区下方约 126dp |
| 2 | 滚动约 1/4（p≈0.25） | 版本已完全消失；名称 alpha≈0.67、scale≈0.983；图标仍完整（p<0.35）；顶栏标题常显（Glacier 现状） |
| 3 | 滚动约 1/2（p≈0.5） | 三层全部不可见；正文已滚动到品牌区位置并覆盖；顶栏无异常 |
| 4 | 将折叠（p≈0.85–0.95） | 品牌区完全透明且不参与视觉；正文接近顶部；无残影、无半透明叠画 |
| 5 | 完全折叠（p=1） | 第一个 `SmallTitle` 紧贴顶栏下方；品牌区不可见；继续上滑不应再有品牌区像素 |
| 6 | 回滚（同一会话从 p=1 拖回顶部） | 图标先出现、名称、版本按相反顺序还原；到顶部时与阶段 1 完全一致；中途停住时三层停在各自对应 alpha/scale，不抖动 |

每个阶段同时检查：无跳动/闪烁、无某一层单独“抢先”出现或消失、图标/名称/版本相对比例不变
（三者只按各自进度缩放，锚点都在品牌区中轴）。

### 9.2 交互与状态

1. 快速 fling 到顶/到底，再快速反向：进度连续，无跳变。
2. 缓慢拖动，在 p≈0.3 / 0.6 处停住 2s：三层静止在对应状态，不继续“补动画”。
3. About → Licenses → 返回：滚动位置恢复（若进入前已滚动）。
4. 竖屏滚动到 p≈0.5 → 旋转横屏 → 再转回竖屏：不崩溃，品牌区按新布局重测，进度不出现 >1 或负值。
5. 系统字体 1.3x / 2.0x：品牌名/版本不溢出、不重叠；占位高度跟随文字高度，正文不与品牌区重叠。
6. Glass 开/关各走一遍阶段 1–6：两档品牌区外观、淡出节奏一致；Glass 开时无自采样崩溃、无脏斑。
7. 深色模式阶段 1：白底品牌块保持白色，应用名/版本颜色来自主题（浅色/深色都可读）。

### 9.3 已知风险（验证时重点看）

- **首帧修正**：进入 About 页时若能看到正文在第二帧上移，属官方 300dp 默认值行为；
  若明显可感知，需要 lead 决定是否偏离官方改用预算高度。
- **横屏高度**：横屏时品牌区+占位总高度可能超过视口，列表仍可滚动、p 能到 1；
  需确认 `SmallTitle` 能滚到顶栏下方，不被品牌区残留遮挡。
- **2.0x 字体 + 横屏**：品牌名可能换行导致 `logoHeight` 变化，占位公式自动跟随，需确认无重叠。

---

## 10. 编译/API 核对（未运行 gradle，按任务约束）

只使用本地 MIUIX 源码真实存在的 API：

- `graphicsLayer { shape / clip / alpha / scaleX / scaleY }`：Compose 标准 API，同官方 `AboutPage.kt:246-253`、
  `:264-269`、`:294-299`；
- `LazyListState.layoutInfo.visibleItemsInfo`（`key`、`size`）与 `firstVisibleItemScrollOffset`：
  同官方 `AboutPage.kt:100-102`；
- `lazyListState` 创建用 `androidx.compose.foundation.lazy.rememberLazyListState()`；
- `item(key = "logoSpacer")`：`LazyListScope.item` 标准 API；
- `pageContentPadding(padding, extraTop = …)`：Glacier `PageFrame.kt:327-348` 既有函数；
- 其余组件（`PageFrame` / `SmallTitle` / `Card` / `BasicComponent` / `ArrowPreference` / `TopBarAction`）
  均为 R3 已在用的既有 API。

未新增字符串资源：品牌名 / 版本文案复用 `R.string.app_name`、`R.string.about_version_brand`
（`values/strings_pages_r3.xml:14`、`values-zh-rCN/strings_pages_r3.xml:12`），无需新建
`strings_about_r4.xml`。

---

## 11. 附：Mishka / InstallerX 阅读结论

| 项目 | 做法 | 对 Glacier 的取舍 |
|---|---|---|
| 官方 `AboutPage.kt` | 固定头图 + `logoSpacer`；`scrollProgress`；三段错峰；`BgEffect` + `textureBlur` | **数值与结构全部采用**；效果层不采用 |
| Mishka `AboutScreen.kt:92-112` | 同款 `scrollProgress`；`:189-192` 三段进度 lambda；`:213-325` 头图/占位 | 结构印证官方；它的 `titleAlpha`（`:126`）依赖自己持有的 `Scaffold`，Glacier 的 Scaffold 在 `PageFrame`，不可照搬；它的 `textureBlur`（`:243-252`）依赖 Mishka 自己的 BgEffect，不采用 |
| InstallerX `MiuixAboutPage.kt:438-602` | 同款 `logoHeightDp=300/52/40/126` 公式；`:477-486` 图标 alpha/scale；`:513-540` 名称；`:542-568` 版本；`:582-601` 占位并回调高度 | 印证官方数字；它的容器无 `clip+shape`（`:479-486` 仅 alpha/scale），官方更完整，采用官方写法 |
| 共同结论 | 三份实现的数字完全同源；差异只在“谁持有 Scaffold / 是否加 BgEffect+blur” | Glacier 保留 `PageFrame` 骨架、不做动态背景，故只取官方结构与参数 |
