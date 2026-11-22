# R7 · 交互与转场对齐 OS4（底栏归属 / 行手势 / 顶栏动作动画）

> 目标：把三处「感觉不对」的交互改成 MIUIX OS4 的既有行为，一律用官方组件与官方动画曲线，不自造动效。
> 参考源：本地 MIUIX 源码 `../miuix`（基线 `44f1d315`）＋模拟器上的官方示例 APK
> （`top.yukonga.miuix.uitest.debug`，已 pull 成 `miuix-check/example.apk` 并反汇编核对结构）。
> 验证环境：`emulator-5554`（1080×2400 / 420dpi，Android 16），截图见 `docs/verification/miuix-os4-r7/`。

---

## 1. 二级页没有底栏 —— 底栏是一级目的地自己的 bottomBar

### 官方实现（APK 反汇编核对）
把示例 APK 的 dex 反汇编后可以直接读出调用链：

```
AppContentKt.Home$lambda$1        → AppContentKt.CompactScreenLayout(...)
AppContentKt.CompactScreenLayout$lambda$0 → AppContentKt.NavigationBar(...)
AppContentKt.NavigationBar$lambda$0       → GlassNavigationBarKt.GlassNavigationBar(...)
```

对应源码：`example/AppContent.kt` 的 `Home()`（`Route.Main` 的 entry）自己套了一层
`Scaffold { CompactScreenLayout(...) }`，`CompactScreenLayout` 的 `bottomBar` 才是 `NavigationBar`。
**底栏属于一级目的地**，进 About / Licenses 时整块一级页（含底栏）随 `NavDisplay` 转场一起走，
二级目的地里根本没有底栏 —— 所以也不存在任何缩放。
官方示例实拍：`23-miuix-about-mid.png`（转场中，底栏还在一级页那侧、正被新页盖住）、
`25-miuix-about-settled.png`（二级页里没有底栏）。

### 之前的错误
`GlacierShell` 按 `backStack.size == 1` 给 `GlassNavigationBar` 传 `visible = navVisible`。
`visible = false` 不是「隐藏」，库的实现是**缩小到 0.6 倍 + 淡出 + 加模糊**
（`GlassNavigationBar.kt:272-283`，`GlassMotion.NAV_HIDE_SCALE = 0.6f`、`NAV_HIDE_BLUR_DP = 18f`），
回场还要等 `NAV_SHOW_DELAY_MS = 100ms` —— 就是那下「缩一下再弹回来」。

### 改动：照搬官方结构
`ui/GlacierShell.kt` 把底栏从宿主 `Scaffold` 的 `bottomBar` 挪进 `entry<Route.Main>`：

- 一级目的地里是 `Box { 录制层(分页器) ; 底栏 align(BottomCenter) }`，
  于是底栏和页面同属一个目的地，转场时一起移动、一起被新页盖住（与官方一致）；
- 底栏采样**一级页内部**的 `rememberLayerBackdrop()`（`pageBackdrop`），而不是宿主壳的
  `rootBackdrop` —— 宿主壳那层录的是整个 `NavDisplay`，底栏进到 `NavDisplay` 里再采它就是
  记录层的后代（自采样红线，Glass 审计 R4-P2）。这是与官方唯一的实现差别，官方
  `CompactScreenLayout` 的 backdrop 本来就在同一层，不存在这个问题；
- `LocalShellBottomBarHeight` 改由一级页自己提供（`if (wide) 0.dp else barHeight`），
  于是二级页读到默认 `0.dp`，正文只让出系统手势区（`PageFrame` 退回 `safeBottom`）；
- `GlassNavigationBar` 的 `visible` 形参彻底不再使用；宿主 `Scaffold` 不再有 `bottomBar`。

### 验证
- `64-after-tap.png` / `63-cold-start.png`：一级页底栏正常（玻璃胶囊 + 选中胶囊）；
- `nav-2.png`：10× 动画时长下的转场中间帧 —— 底栏跟着设置页往左走、被关于页盖住，
  **没有缩放、没有淡出**，与官方 `23-miuix-about-mid.png` 同一形态；
- `67-about-settled.png`：关于页里完全没有底栏，正文铺到屏幕底部；
- `68-back-with-bar.png`：返回一级页，底栏随页面一起滑回来；
- `66-tap-home.png` / `65-tap-settings.png`：底栏页签切换正常。
- （`27-glacier-about.png`、`51-about-no-bar.png` 是前两版走错方向时留下的对照图：
  第一版底栏浮在二级页上，第二版靠淡出收掉，都已废弃。）

---

## 2. Apps 页：点按 = 选中，长按 = 操作单

### 官方能力
`BasicComponent` 只有 `clickable`，没有长按（`miuix-ui/basic/Component.kt:148-160`）；
`Card` 的交互版才有官方长按组合：
`onClick` / `onLongPress` / `holdDownState` / `showIndication`
（`miuix-ui/basic/Card.kt:87-154`），官方示例的长按范式见
`example/component/CardSection.kt:110-143`（长按弹层 + `holdDownState` 保持按下态，弹层退场后复位）。


### 改动
- `feature/common/AppSearchResults.kt` → `AppListCardRow`
  - 手势交给 `Card(onClick, onLongPress, showIndication = true, pressFeedbackType = None, holdDownState)`；
  - 行内 `AppRow` 不再注册 `onClick`，全行只有一层按压反馈（被 `Card` 的 squircle 表面裁成圆角）；
  - 新增 `selected`（复用图标 squircle 选中环）/ `onLongClick` / `holdDown`。
- `feature/apps/AppsScreen.kt`
  - 新增 `selecting` / `selection` 多选态（与 Home 同一套语义）：点按切换成员关系，第一次点按即进入多选；
    长按打开该应用操作单，并在弹层显示期间保持该行按下态（`heldPackage`，`onDismissFinished` 复位）；
  - 顶栏普通档（搜索 / 排序 / 筛选）与多选档（冻结 / 解冻 / 更多 / 关闭）二选一；
    标题变「已选 N 项」，副标题让位；批量动作结束后自动退出多选；
  - 搜索浮层沿用同一行组件：点结果 = 选中并收起浮层（选中态要回到列表才看得到），长按 = 操作单。
- `feature/common/AppActions.kt`：新增 `reportFreezeBatch(context, outcome, frozen)`，
  Home 与 Apps 的批量冻结共用同一结果出口（原本是 `HomeScreen` 里的 `private fun reportBatch`）。
- 多选态返回键：Home / Apps 都用官方 `BackHandler`（activity-compose）先退出多选。
  点按即进多选之后这条退路必须有，否则误触一行再按返回会直接结束 Activity。

### 验证
- `10-select-one.png`：点按 → 图标蓝色选中环 + 标题「已选 1 个」+ 顶栏 4 个批量动作；
- `14-longpress-1s.png` / `39-more-from-selection.png`：长按（及多选档「更多」）→ 操作单 + 该行保持按下态；
- `41-search-select.png`：搜索结果点按 → 选中并收起浮层；
- `42-batch-freeze.png`：批量冻结 → 退出多选 + 统一 toast（本机无授权，提示「权限不足」，路径正确）；
- `38-back-exits-selection.png`：多选态返回 → 退出多选，不再退出应用。

---

## 3. 顶栏动作区两态切换动画

### 成因
Home 的顶栏动作是 `if (inSelection) { 5 个 } else { 3 个 }`，标题同时换成「已选 N 个」，
一帧之内全变，观感就是硬跳。

### 官方依据（不自造曲线）
- 控件显隐的官方写法：`fadeIn() + scaleIn()` / `fadeOut() + scaleOut()`
  （`miuix-ui/basic/SearchBar.kt:190-193` 的清除按钮、`example/component/SuperSearchBar.kt:196-201`）；
- 曲线取库里的 `GlassMotion`：`scaleIn()`（0.6/0.35）、`scaleOut()`（0.75/0.2）、
  `barExpand()`（1.0/0.3）、`barCollapse()`（1.0/0.15）——后两个正是顶栏小标题进出场用的弹簧
  （`miuix-ui/basic/TopAppBar.kt:757-766`）；
- 容器用 `AnimatedContent`（MIUIX 自己在 `miuix-glass/.../GlassNavigationRail.kt:1062` 用它切内容），
  宽度变化交给 `SizeTransform`（同一个 `barExpand` 弹簧），按钮组因此是「推挤着长出来」而不是先跳宽。

### 改动
`ui/components/PageFrame.kt` 新增 `AnimatedTopBarActions(selectionMode, normal, selection)`：
内部是 `AnimatedContent` + `Row(spacedBy(8dp))`（官方动作间距 `example/GlassPage.kt:363`）。
Home 与 Apps 的顶栏都改用这个组件。

### 验证
10× `animator_duration_scale` 下逐帧抓图：
`anim-1..3.png`（Apps 进多选）、`hsel-1..3.png`（Home 进多选）——两组图标在同一位置交叉淡入淡出，
宽度平滑推挤，无溢出/裁切/标题抖动。

---

## 4. 其他缺失动画

| 位置 | 之前 | 现在 |
|---|---|---|
| Home FAB | `if (!inSelection) { FAB }` 直接消失 | `CapturedAnimatedVisibility` + `fadeIn/scaleIn`、`fadeOut/scaleOut`（`GlassMotion` 曲线），缩着淡出/淡入 |
| Home 页签行（`topBarBottom`） | `if (tags.size > 1 && !inSelection)` 直接消失，顶栏高度一帧变矮、正文跳一行 | `CapturedAnimatedVisibility(expandVertically + fadeIn)` / `shrinkVertically + fadeOut`，高度平滑收放（官方顶栏子内容显隐范式，`example/AppContent.kt:720-728` 导航条同款） |

验证：`taganim-1..4.png`（页签行收起，网格随高度平滑上移）、`46-home-restored.png`（退出多选后页签行与 FAB 复原）。

---

## 5. 已知问题（本轮未修，非本轮引入）

**底栏玻璃指示器偶发蓝色残影**：切换页签后，旧位置留下一条蓝色模糊色斑
（`17/18-settings-settled.png` 与裁剪图 `crop-bar.png`）。
用 **已发布的 1.4.0 release APK** 复现，裁剪图与 debug 版逐字节相同（`crop-bar-release.png`），
因此是本轮之前就存在的上游玻璃渲染问题，与本次改动无关；需要在 MIUIX 侧跟进
（`GlassNavigationBar` 的指示器玻璃层在位移后未清理旧位置的模糊快照）。

---

## 6. 涉及文件

```
app/src/main/kotlin/com/ayang/glacier/ui/GlacierShell.kt             底栏挪进 entry<Route.Main>（含页内 backdrop）
app/src/main/kotlin/com/ayang/glacier/ui/GlassEnvironment.kt         底栏高度只在有底栏的目的地上有值
app/src/main/kotlin/com/ayang/glacier/ui/components/PageFrame.kt     AnimatedTopBarActions
app/src/main/kotlin/com/ayang/glacier/feature/apps/AppsScreen.kt     点按选中 / 长按操作单 / 批量动作
app/src/main/kotlin/com/ayang/glacier/feature/home/HomeScreen.kt     顶栏动画、FAB、页签行、共享 reportFreezeBatch
app/src/main/kotlin/com/ayang/glacier/feature/common/AppSearchResults.kt  Card 交互版行、选中态
app/src/main/kotlin/com/ayang/glacier/feature/common/AppComponents.kt    行 KDoc 同步
app/src/main/kotlin/com/ayang/glacier/feature/common/AppActions.kt   reportFreezeBatch
```

## 7. 验证清单

- `assembleDebug` / `testDebugUnitTest` 通过（无新增警告，除 Gradle 自身的 KGP 属性提示）。
- 模拟器：窄屏一级页底栏 / 进二级页底栏随页离场（10× 慢放逐帧）/ 二级页无底栏 / 返回复原 /
  Apps 多选 / 长按操作单 / 搜索选中 / 批量冻结 / 返回键 / 页签行收放。
- 未覆盖：宽屏（侧栏形态，无底栏）、横屏、2.0× 字体、深色主题（本轮改动不涉及颜色与尺寸 token）。
