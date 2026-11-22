# R7 · 交互与转场对齐 OS4（底栏常驻 / 行手势 / 顶栏动作动画）

> 目标：把三处「感觉不对」的交互改成 MIUIX OS4 的既有行为，一律用官方组件与官方动画曲线，不自造动效。
> 参考源：本地 MIUIX 源码 `../miuix`（基线 `44f1d315`）＋模拟器上的官方示例 APK `top.yukonga.miuix.uitest.debug`。
> 验证环境：`emulator-5554`（1080×2400 / 420dpi，Android 16），截图见 `docs/verification/miuix-os4-r7/`。

---

## 1. 二级页底栏不再缩放下沉

### 成因
`GlacierShell` 之前按 `backStack.size == 1` 给 `GlassNavigationBar` 传 `visible = navVisible`。
`visible = false` 不是「隐藏」，库的实现是**缩小到 0.6 倍 + 淡出 + 加模糊**再退场
（`miuix-glass/.../GlassNavigationBar.kt:272-283`，`GlassMotion.NAV_HIDE_SCALE = 0.6f`、
`NAV_HIDE_BLUR_DP = 18f`），并且回场还要再等 `NAV_SHOW_DELAY_MS = 100ms`。
所以每次进 About / Licenses，底栏都会「缩一下再弹回来」。

### 官方行为
官方 example 的底栏**从不传 `visible`**（`example/shared/.../AppContent.kt:791-799`），
即默认 `visible = true`：二级页里底栏原地不动。`visible` 是留给「整页切到无导航语义目的地」的开关，
不是二级页转场的一部分。

### 改动
- `ui/GlacierShell.kt`：去掉 `GlacierBottomNavigation` 的 `visible` 形参，`GlassNavigationBar` 恒为可见；
  二级页只剩侧栏 `selectedIndex = -1` 的表达（官方同款）。
- `LocalShellBottomBarHeight` 不再在二级页归零（`provides if (wide) 0.dp else bottomBarHeight`），
  于是 About / Licenses 的正文底部让位与一级页一致，最后一行不会被悬浮底栏压住。

### 验证
`27-glacier-about.png`：About 页底栏固定、满尺寸，动态渐变从玻璃里透出来；
`28-back-to-apps.png`：二级页点底栏可回落一级页并切页签。

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
app/src/main/kotlin/com/ayang/glacier/ui/GlacierShell.kt             底栏常驻、去掉 visible
app/src/main/kotlin/com/ayang/glacier/ui/GlassEnvironment.kt         底栏高度的二级页语义
app/src/main/kotlin/com/ayang/glacier/ui/components/PageFrame.kt     AnimatedTopBarActions
app/src/main/kotlin/com/ayang/glacier/feature/apps/AppsScreen.kt     点按选中 / 长按操作单 / 批量动作
app/src/main/kotlin/com/ayang/glacier/feature/home/HomeScreen.kt     顶栏动画、FAB、页签行、共享 reportFreezeBatch
app/src/main/kotlin/com/ayang/glacier/feature/common/AppSearchResults.kt  Card 交互版行、选中态
app/src/main/kotlin/com/ayang/glacier/feature/common/AppComponents.kt    行 KDoc 同步
app/src/main/kotlin/com/ayang/glacier/feature/common/AppActions.kt   reportFreezeBatch
```

## 7. 验证清单

- `assembleDebug` / `testDebugUnitTest` 通过（无新增警告，除 Gradle 自身的 KGP 属性提示）。
- 模拟器：窄屏一级页 / 二级页 / Apps 多选 / 长按操作单 / 搜索选中 / 批量冻结 / 返回键 / 页签行收放。
- 未覆盖：宽屏（侧栏形态，无底栏）、横屏、2.0× 字体、深色主题（本轮改动不涉及颜色与尺寸 token）。
