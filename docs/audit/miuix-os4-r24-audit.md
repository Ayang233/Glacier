# R24 · 关于页上下滑：背景/品牌区不再跟着列表一起位移

## 现象

真机（Android 17 / MIUIX OS4）在关于页上/下拖拽：

- 下拉到顶部回弹时，整页（BgEffect 动态背景 + 品牌图标/名称/版本 + 卡片）一起向下位移，
  顶栏下方露出一条宿主底色（`#F7F7F9`）；
- 上拖时卡片会压到还没淡出的品牌区上，顶栏标题不出现，`scrollProgress` 始终为 0；
- 背景渐变看起来只在「正文内容」范围内，而不是整页固定。

官方 MIUIX example 的 `AboutPage` 没有这两个现象：无论怎么拖，背景始终铺满整页，
只有列表内容和它的 overscroll 在动，品牌区由滚动进度淡出、顶栏标题按 0.45 阈值淡入。

## 对照物

按用户要求编译了 MIUIX 最新基线 APK（`e7c108ee`）并安装到模拟器逐项对照：

```bash
cd miuix
./gradlew :example:android:assembleDebug
adb install -r example/android/build/outputs/apk/debug/Miuix-v1.1.0\(31\)-debug.apk
```

官方 `example/shared/src/commonMain/kotlin/AboutPage.kt` 的关键结构：

- `:312-316` —— `pageScrollModifiers(...)` 挂在 **LazyColumn** 上，不是整页 wrapper；
- `:354-413` —— 正文卡片放在同一个 `item` 里，并用
  `Spacer(Modifier.fillParentMaxHeight())` 把这一项撑到至少一屏；
- `:193-197` —— `listContentPadding` 把底部留白从 `contentPadding` 挪进正文 `Column`；
- `:226-233` —— `BgEffectBackground` 在列表之外，背景与品牌区固定。

## 根因

Glacier 的 `PageFrame` 默认把 `pageScrollModifiers` 挂在**包住整页内容的 wrapper** 上：

```kotlin
Box(Modifier.fillMaxSize().pageScrollModifiers(...)) {
    content(contentPadding)   // 关于页的 BgEffect + 品牌区 + LazyColumn 都在里面
}
```

关于页的背景、品牌区和列表都在这个 wrapper 内，于是：

1. 弹性 overscroll 的位移会带着 BgEffect 与品牌区一起走，顶部/底部露出 `Scaffold` 底色；
2. 关于页正文不足一屏（品牌占位 + 3 张卡 + 脚注 < 视口），列表本身不可滚动，
   上拖只会走 overscroll，`LazyListState.firstVisibleItemScrollOffset` 不变，
   `scrollProgress` 恒为 0 —— 品牌不淡出、标题不出现，卡片直接压到品牌上。

官方 About 页把 `pageScrollModifiers` 挂在列表上，而且用一个
`fillParentMaxHeight` 的占位项保证列表至少有一屏可滚，所以上述两个问题都不存在。

## 修法（只改 `AboutScreen.kt`，不新增页面骨架）

1. `PageFrame(..., pageScrollOnContent = true)`，让 PageFrame 不再给整页 wrapper
   挂滚动/回弹；把 `Modifier.pageScrollModifiers(topAppBarScrollBehavior = scrollBehavior)`
   挂到 About 自己的 `LazyColumn` 上（与官方 `AboutPage.kt:312-316` 同参）。
2. 按官方 `AboutPage.kt:354-413` 把正文卡片收进同一个 `item`：
   `Box { Spacer(fillParentMaxHeight()); Column { 卡片… } }`。
   这一项至少一屏高，列表因此可滚动，品牌淡出/标题淡入重新由真实滚动进度驱动。
3. 按官方 `AboutPage.kt:193-197` 增加 `listContentPadding`（底部为 0），
   把底部留白放到正文 `Column` 里。
4. 去掉 LazyColumn 的 `verticalArrangement = spacedBy(12.dp)`；占位高度不再减
   `SectionGap`（卡片列改用 12dp `spacedBy`），静止布局与修改前逐像素一致。

## 验证

- `:app:compileDebugKotlin`、`:app:assembleDebug`、`:app:testDebugUnitTest`、`:app:assembleRelease` 通过。
- 模拟器（1080×2400，API 36，玻璃开/关两种）：
  - 顶栏下方不再出现白带；下拉回弹时只有列表内容移动，背景与品牌区固定；
  - 上拖时列表真实滚动，品牌按 `scrollProgress` 淡出、顶栏「关于」按 0.45 阈值淡入，
    卡片不再压到未淡出的品牌上；
  - 滚动到底时背景按官方 `alpha = 1 - p` 淡为纯色，无硬边；
  - 把窗口缩到 1080×1600 强制内容超出视口，滚动/淡出/标题仍正常。
- 与同机运行的 MIUIX example `AboutPage` 逐帧对照，上下滑的固定层与移动层一致。

## 证据

- `docs/verification/miuix-os4-r24/00-before-after.png`：修复前/后同位置对照（白带消失）
- `docs/verification/miuix-os4-r24/01-before-overscroll.png`：修复前下拉，背景/品牌一起位移，顶栏下露白
- `docs/verification/miuix-os4-r24/02-after-overscroll-top.png`：修复后下拉，只有列表动，背景铺满
- `docs/verification/miuix-os4-r24/03-after-scroll.png`：修复后上拖，品牌淡出、标题出现
- `docs/verification/miuix-os4-r24/04-after-bottom.png`：修复后到底，背景按官方语义淡为纯色
- `docs/verification/miuix-os4-r24/05-miuix-reference.png`：同机 MIUIX `AboutPage` 下拉对照
