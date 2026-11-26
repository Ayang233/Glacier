# R25 · 关闭玻璃后上滑：列表内容从状态栏那一条里透出来

## 现象

关闭「玻璃效果」后，在关于页上滑（以及继续上拖到底部回弹）：

- 列表内容会出现在**状态栏那一条**里，压在时间/信号上面；
- 顶栏本体是实色 `surface`，但顶栏最上面（`appPageTopBarInsets()` 让出来的安全区）
  是一条透明缝，列表滚到下面就透出来；
- 玻璃开启时没有这个现象，因为 `GlassTopAppBar` 自己会画满整条顶栏。

真机截图见用户反馈；模拟器可 100% 复现（`docs/verification/miuix-os4-r25/01-glassoff-before.png`）。
这不是关于页独有的：所有非玻璃档页面在滚动时都有同一条透明缝。

## 对照物

MIUIX 官方 example 的非玻璃顶栏 `example/shared/src/commonMain/kotlin/utils/PageUtils.kt:297-314`
（`AdaptiveTopAppBar` 的 `else` 分支）：

```kotlin
TopAppBar(
    title = title,
    modifier = Modifier
        .background(color)              // 底色在最外层，铺满包括 inset 在内的整条顶栏
        .then(contentModifier)
        .then(contentInsetModifier),    // appPageTopBarInsets 在底色里面
    color = Color.Transparent,          // TopAppBar 自己不再画第二层
    ...
)
```

关键就两点：**底色挂在外层 modifier 上**、**`TopAppBar` 的 `color` 传透明**。

## 根因

Glacier 的 `PageTopBar` 非玻璃分支原来是这样：

```kotlin
TopAppBar(
    modifier = modifier.then(Modifier.appPageTopBarInsets()),
    // 没有外层 background，color 用 TopAppBar 默认的 surface
)
```

`appPageTopBarInsets()` 是**外层** modifier，而 `TopAppBar` 的底色画在它**里面**：
状态栏高度的安全区因此没有被任何一层覆盖，列表滚到下面就透出来。
玻璃档不受影响是因为 `GlassTopAppBar` 的材质画在 inset 之外。

## 修法

按官方 `AdaptiveTopAppBar` 非玻璃分支改 `PageFrame.kt` 的 `PageTopBar`：

```kotlin
val surface = MiuixTheme.colorScheme.surface
TopAppBar(
    color = Color.Transparent,
    modifier = modifier
        .background(surface)
        .then(Modifier.appPageTopBarInsets()),
    ...
)
```

- 外层 `background(surface)` 覆盖整条顶栏（含状态栏安全区）；
- `color = Color.Transparent` 避免 `TopAppBar` 再画一层，保持官方"只有一层底色"的语义；
- 这是**全局非玻璃顶栏**的修复，设置页/主页等所有非玻璃页面一并受益；
- 玻璃分支 `GlassTopAppBar` 不动。

## 验证

- `:app:compileDebugKotlin`、`:app:assembleDebug`、`:app:testDebugUnitTest`、`:app:assembleRelease` 通过。
- 模拟器（1080×2400，玻璃关）：
  - 关于页上滑到列表顶部进入状态栏区域时，状态栏那一条保持纯 `surface`，不再透出包名；
  - 上拖到底部回弹同样干净；
  - 设置页大标题、主页等内容页非玻璃档外观无变化；
  - 玻璃开启时关于页仍是动态背景 + 模糊顶栏，无额外实色条。

## 证据

- `docs/verification/miuix-os4-r25/01-glassoff-before.png`：修复前，状态栏透出列表内容
- `docs/verification/miuix-os4-r25/02-glassoff-after.png`：修复后，状态栏是干净的 surface
