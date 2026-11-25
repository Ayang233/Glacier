# R11 · 基线对齐 `v0.9.5-beta04` + 自造轮子清查 + 冻结交互重构

> 起因：用户要求三件事——① 全项目扫描"自造轮子"（UI / 界面 / 动画 / 尺寸 / 间距 / 边距），
> 一律换成 MIUIX OS4 与 OS4 Glass 的官方内容；② 把基线升到当前最新的 `beta4`；
> ③ 排查软件自身的交互/联动是否自洽，并给出一个明确的例子：
> 「主页的冻结按钮就应该只是冻结，为什么主页的软件全冻上之后会出现解冻按钮；
> 应该只是冻结，点开软件自动解冻打开才是对的。而且冻结/解冻之后主页图标也不跟着变。」
>
> 验证：`emulator-5554`（1080×2400 @420dpi，KernelSU 可用 root 模式），截图见
> `docs/verification/miuix-os4-r11/`。

---

## 1. 基线：`44f1d315` → tag `v0.9.5-beta04`

| 项 | 旧 | 新 |
|---|---|---|
| 表达方式 | `v0.9.5-beta03` + 18 个 main 提交 | **直接对齐 tag** |
| commit | `44f1d315` | `3c560868` |
| 与 main 关系 | HEAD == `origin/main` | 基线 = tag，`origin/main` 再往前 2 个提交（未纳入） |

官方在 2026-11-24 重写了 main 历史（fetch 输出 `+ 44f1d315...03d15786 main -> origin/main (forced update)`），
旧基线已经不在 main 上，"tag + N 个提交"这种写法从此无法复算，因此基线改为**钉 tag**。

beta04 相对旧基线的官方变化（9 个提交）：

| 提交 | 内容 | 对 Glacier 的影响 |
|---|---|---|
| `93e5f80f` | 新增 `SheetMotion`，BottomSheet 动效统一 | 全部 `GlassBottomSheet` 手感自动跟随，无需改代码 |
| `3c560868` | 弹层退场尾部提前释放触摸拦截、动效精修 | 同上 |
| `07d48590` | 搜索清除按钮样式统一（新增 `SearchClearButton`） | `GlassSearchOverlay` 内部生效 |
| `2f7dbff9` | `Slider` 支持点按定位（tap-to-seek） | `SliderPreference` 生效 |
| `583e8723` | `GlassPopup` 支持水平对齐 | Glacier 未用锚点弹层 |
| 其余 | example / docs / CI 调整 | 无 |

结论：**编译零改动通过**（`./gradlew :app:assembleDebug` BUILD SUCCESSFUL），
收益是弹层与按压手感的官方迭代直接吃到，不需要在 Glacier 侧做适配。

## 2. 全项目自造轮子清查（结论 + 处置）

扫描口径：`Canvas` / `drawBehind` / `RoundedCornerShape` / `Modifier.clip` /
`animateXxxAsState` / `tween` / `spring` / 硬编码 `dp` `sp` / `FontWeight` 全量 grep，
再逐文件对照官方 example 与库源码。

### 2.1 找到并删除的自造件

| 位置 | 自造内容 | 处置 |
|---|---|---|
| `PageFrame.kt` + `HomeScreen.kt` | **「已选 N 个」的自造排版层**：把多选计数塞进大标题，为对齐中文字形加 0.83 字号补偿（`title1` 32sp → 26.5sp）、再叠一层整条顶栏的 `AnimatedContent` 交叉淡入淡出 | 删除 `titleFontScale` 形参、`SelectionTitleScale` 常量、`AnimatedContent` 容器与逐状态 `MiuixTheme(textStyles=…)` 覆盖。多选进度改写成官方副标题（`title` + `subtitle` 两行），顶栏结构与高度两态一致 |
| `HomeScreen.kt` | FAB 的"智能翻转"：`willFreeze = scopeEntries.any { !it.frozen }`，全冻时按钮变成解冻并批量解冻整页 | 改为**只冻结**（见 §3） |
| `GlacierRepository.kt` | `if (refreshing) return` —— 正在刷新就丢弃本次请求 | 改 `Mutex` 串行排队（见 §4） |
| `AppIcons.kt` | `produceState(initialValue = null)`：冻结切换时 key 变化 → 状态归零 → 图标闪成空占位；灰度位图每次重跑去色 | 灰度档并入 LRU + 新增同步 `peek()` 作为 `initialValue`；加载失败保留上一帧 |
| `HomeScreen.kt` | 第二份 `SearchResultVerticalPadding = 4.dp`（与 `AppSearchResults` 里那份重复） | 删掉私有副本，复用 `feature/common` 的那一份 |

### 2.2 核对后确认"不是轮子"的（保留，理由记录在案）

| 位置 | 看起来像自造 | 实际依据 |
|---|---|---|
| `PageFrame.pageContentPadding` / `pageScrollModifiers` | 自己实现的 PaddingValues / Modifier 链 | 官方 `example/utils/PageUtils.kt:191-245` 的逐条对齐（库不导出） |
| `ui/PageInsets.kt` 全套 | 自建 inset 契约 | 官方 `PageUtils.kt:62-152` 逐字节移植（Apache-2.0，带出处） |
| `GlacierBottomNavigation` 的 `Modifier.layout` | 手算底部留白 | 官方 `GlassPage.kt:292-320` 同款：`max(24dp, safeBottom + 8dp)` |
| 主页网格 `squircleClip` + `combinedClickable` | 自绘按下层 | 官方 `IconPage.kt:403-411`（先裁 squircle 再 clickable）；叠层由主题里的 `MiuixIndication` 画，没有自绘高亮 |
| `HomeGridMetrics`（56dp 图标 / 6dp / 8dp / 16dp / 80dp 单元） | 一堆硬编码尺寸 | 图标 56dp 是产品档；列数公式与官方 `((maxWidth - 24dp) / 80dp).coerceIn(3, 8)` 同值；间距在 `docs/plan/r3-implementation-spec.md` 登记 |
| `TagRow` 的 `padding(top=6dp, bottom=12dp)` | 魔法值 | 官方 `IconPage.kt:295` 的 `padding(horizontal = 12.dp).padding(top = 6.dp, bottom = 12.dp)` 原样 |
| `AboutScreen` 的 `RoundedCornerShape(24.dp / 16.dp)`、35sp Bold、0.35/0.20/0.05 错峰进度 | 自绘品牌区 | 官方 `AboutPage.kt:242-304` 的品牌区与 `textureBlur` 参数逐条对应 |
| `ui/effect/`（BgEffect / 混色 token） | 自写动态背景 | 官方 example 的 `BgEffect` 逐字节移植（Apache-2.0） |
| `HoldDownEffect.kt` | 自实现按下态保持 | 官方 `HoldDownObserver` 是 `internal`，库外不可调用；只发官方公开的 `HoldDownInteraction`，逻辑逐行对齐 |
| `SessionLock` 锁屏页 | 自建布局 | MIUIX 无"锁屏/门禁"组件；页面只用官方 `Icon` / `Text` / `Button` |
| `UiFeedback.toast` | Android `Toast` | 系统反馈件，不是自造视觉。**这一轮试过换成 `SnackbarHost`（官方 `Scaffold(snackbarHost=…)` 槽，Scaffold 会自动避开 FAB），实机验证后回退**：页面级浮层（`GlassSearchOverlay` / `GlassBottomSheet`）画在 Scaffold 之上，会在"搜索结果里点一行加入/移出主页"这类高频路径上把 Snackbar 整条盖住，反馈直接丢失；另外 Worker / Receiver / 快捷方式没有 Composition，本来就走不到 Snackbar。HyperOS 上系统 Toast 由系统按 OS 风格绘制，与 MIUIX 观感不冲突 |

### 2.3 仍未做的（明确记录，不是遗漏）

- 主窗口内反馈仍走系统 Toast（理由见 2.2；换成 Snackbar 会丢反馈）。
- 二级页切 Tab 转场仍用 `NavTransitions.MiuixDefault`（官方 example 用的是私有 `InstantNavTransition`）。

## 3. 冻结交互：主页只有一个"冻结"

### 3.1 原来的问题

```
FAB 图标 = if (范围内有未冻结) 冻结 else 解冻
点击     = if (有未冻结) 冻结未冻结的 else 解冻全部
```

也就是说：**同一颗按钮，会因为"这一页全冻上了"而变成它的反操作**。
用户的原话是"主页的冻结按钮，就应该仅仅只是冻结"——按钮的语义不该随数据状态翻转，
否则用户永远要先看一眼图标才知道点下去会发生什么。

### 3.2 现在的规则

| 状态 | FAB | 多选入口 |
|---|---|---|
| 有未冻结应用 | 显示「冻结」，点击冻结范围内未冻结的 | 显示 |
| 全部已冻结 | **仍然显示在原位**（位置与语义都不随数据变化），点击提示「已全部冻结」 | **仍然显示** |
| 多选态 | 只冻结选中的那些（已冻结的自动跳过）；选中的都已冻结时提示「所选应用已全部冻结」 | 显示（同时是退出口） |
| 页面没有任何应用 | 不挂载（空态没有可冻结对象） | 显示（顶栏布局不跳动） |

两条设计取舍：

- **不做"消失"**：冻结入口是这一页的主操作，位置必须稳定。上一版在"全冻"时把 FAB 与多选入口
  一起隐去，用户会说"冻结按钮怎么没了"——布局随数据跳动比"点了没反应"更让人困惑。
- **不做自造禁用态**：`GlassFloatingActionButton` 没有 `enabled` 形参，灰化 / 降透明度都属于
  自造视觉。既然不能禁用，就让它在原位、用一句话如实说明"已经全冻上了"。

### 3.3 解冻只有两条路

1. **点开应用**：`AppActionRunner.launch` 先解冻再启动（Island 模式额外 `awaitLaunchIntent`）。
   这是"图标即入口"的闭环——用户不该为了开一个应用先去菜单里解冻。
   解冻失败现在会**停下并提示"权限不足"**，不再硬启动然后报 `ActivityNotFound`。
2. **长按应用 → 菜单「解冻」**（语境行，位置固定）。批量解冻从主页彻底移除。

`ApiActivity` 的 `ACTION_LAUNCH` 是同一语义的第二处实现，也补上了"解冻失败即抛错"。

## 4. 冻结后图标/文案不刷新

两个原因，都已修：

1. **`refresh()` 会丢弃请求**：`if (refreshing) return@withContext`。冻结完成后的那次刷新
   只要撞上 `ON_RESUME` 触发的那次就会被丢掉，界面停在冻结前的快照上。
   → 改成 `Mutex` 排队：后来的刷新等前一次结束再跑，不会被丢。
2. **图标状态会闪、且灰度不缓存**：`produceState` 的 `initialValue` 是 `null`，
   `grayscale` 一变整个状态重建 → 先闪一帧空占位；灰度位图每次重新去色。
   → 灰度档进 LRU（键 `pkg@size#gray`），并新增同步 `peek()` 作为 `initialValue`。

实测（`emulator-5554`，root 模式）：

| 操作 | 结果 |
|---|---|
| 点 FAB 冻结整页 | 6 个图标全部转灰度、标签同时转 `onSurfaceVariantSummary`；FAB 与多选入口留在原位，副标题追加「已全部冻结」 |
| 全冻状态下点 FAB | 提示「已全部冻结」（不静默、也不自造灰化禁用态） |
| 点已冻结的「时钟」 | 系统层 `enabled=1`（已解冻）+ Clock 被拉起 |
| 返回 Glacier | 时钟图标恢复彩色，其余保持灰度，副标题回到「默认 · 6 个应用」 |

## 5. 顺带统一的交互细节

- 多选进度从大标题移到副标题（`已选 2 个 · 默认`），进/出多选不再让整页上下跳。
- 冻结入口常驻：FAB 与多选入口都不再随冻结状态显隐，顶栏与右下角的布局在冻结前后完全一致。
- `AppActionRunner.launch` 与 `ApiActivity.ACTION_LAUNCH` 对"解冻失败"的处理统一为
  "停下 + 报权限不足"。

## 6. 回归清单（本轮实机走查）

- [x] 主页 / 应用页 / 设置 / 关于 四页正常渲染，玻璃档与纯色档都在
- [x] FAB 冻结整页 → 图标灰度 + 副标题「已全部冻结」，FAB / 多选入口仍在原位
- [x] 全冻时点 FAB → 提示「已全部冻结」
- [x] 点开已冻结应用 → 自动解冻并启动；返回后图标恢复彩色
- [x] 多选：标题恒为 Glacier，副标题显示「已选 N 个 · 标签」
- [x] 全冻状态下没有多选入口（也无法进入死局）
- [x] `./gradlew :app:assembleDebug` 通过；`tools/miuix-baseline.sh verify` 通过
