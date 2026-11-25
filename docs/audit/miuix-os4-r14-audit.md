# R14 · 锚点浮层收尾（宽度 / 首开动画 / 横屏侧栏让位）+ 设置文案与顺序

> 状态：**已实施并在 GPU 模拟器验证**
> 起因（用户 4 条）：① 工作模式「禁用」文案改「停用」，「主页标签字号」与「合成自适应图标」对调；
> ② 长按悬浮弹窗是否过宽、是否符合 MIUIX OS4 设计；③ 有时第一次长按没有弹出动画；
> ④ 横屏长按后弹窗遮挡侧栏，问官方有没有自动让位能力。

---

## 1. 结论

| # | 问题 | 结论 |
|---|---|---|
| ① | 文案 / 顺序 | 4 条工作模式文案的「禁用」改「停用」；设置页两个选项对调 |
| ② | 弹窗过宽 | **是过宽**。官方 `GlassPopupDefaults.MinWidth = 200.dp`、`MaxWidth = 288.dp`，**宽度由菜单内容固有尺寸驱动**；R13 为绕开高度探测把标题行写成 `width(320.dp)`（应用页 280dp），实际把面板强行顶到 MaxWidth。R14 删除写死宽度后实测面板收回到 200dp（MinWidth），文案完整、无省略 |
| ③ | 首次长按无动画 | **R13 的绕过写法造成的**。官方文档要求浮层常驻组合、用 `show` 控制开合；R13 用 `if (lastActionEntry != null)` 包裹调用，第一次长按时才第一次组合，`updateTransition(show)` 的初始值直接是 `true`，展开动画不会播。R14 改为常驻组合，并用列表首项预热菜单内容（避开上游固有尺寸缓存对"空菜单首帧"的记忆），冷启动后第一次长按即有完整动画 |
| ④ | 横屏遮挡侧栏 | 官方**没有"识别侧栏"的专用 API**，自动让位来自 `GlassPopupSurface` 的放置规则：面板被夹在「弹窗宿主边界 + `safeMargin`」内，空间不足时上下翻转（`placeGlassPopup`）。官方文档的原话是"放在带 inset 内边距或侧栏偏移的面板中也能对齐"。所以把根部 `TopLayerHost` 按侧栏让位宽度（`overlayStart`）内缩，浮层就自动停在侧栏右侧，且仍在侧栏之后绘制（z 序不变） |

## 2. 官方依据

| 依据 | 内容 |
|---|---|
| `miuix/docs/zh_CN/components/glass/glasstransformpopup.md:30` | 浮层放在整页 Box 最后、裁剪容器之外；"弹窗会减去自身宿主的窗口偏移，因此放在带 inset 内边距或侧栏偏移的面板中也能对齐" |
| 同上 `:34` | "关闭时仍须保留组件调用，直到离场动画完成；不要使用 `if (show)` 包裹调用" |
| `miuix/docs/zh_CN/components/glass/glasspopup.md:108-115` | `GlassPopupSizing`：`minWidth` 默认 200dp、`maxWidth` 默认 288dp、`safeMargin` 默认 12dp |
| `miuix-glass/.../GlassPopup.kt:61-73` | `GlassPopupDefaults`：`MinWidth 200.dp` / `MaxWidth 288.dp` / `MaxHeight 416.dp` / `SafeMargin 12.dp` |
| `miuix-glass/.../GlassPopupSurface.kt:141-160` | `placeGlassPopup`：水平按 `horizontalAlignment` 对齐后夹进宿主窗口安全区；垂直空间不足时翻到锚点上方 |
| `miuix-glass/.../GlassPopupSurface.kt:329-346` | 面板宽度 = `measurables[1].maxIntrinsicWidth()` 夹在 `[minWidth, maxWidth]`；高度 = `minIntrinsicHeight` 夹到 `maxHeight` 后滚动 |
| `miuix-glass/.../GlassPopupColumn.kt:31` | `POPUP_PROBE_COUNT = 8`：固有尺寸只采样前 8 个子项（标题 + 分割线 + 6 行） |

## 3. 改动

| 文件 | 改动 | 原因 |
|---|---|---|
| `res/values-zh-rCN/strings.xml` | `mode_shizuku_disable` / `mode_su_disable` / `mode_jailbreak_disable` / `mode_privapp_disable` 的「禁用」→「停用」 | 用户 ① |
| `feature/settings/SettingsScreen.kt` | 「主页标签字号」滑块移到「合成自适应图标」开关之前 | 用户 ① |
| `feature/home/HomeScreen.kt` | 删除标题行 `width(320.dp)`；`TopLayer { GlassTransformPopup(...) }` 不再用 `if (menuTarget != null)` 包裹；标题/行内容用 `menuTarget?.let` 保护；`lastActionEntry` 用 `entries.firstOrNull()` 预热 | 用户 ②③ |
| `feature/apps/AppsScreen.kt` | 同上（删除 `width(280.dp)`、常驻组合、`entries.firstOrNull()` 预热） | 用户 ②③ |
| `ui/GlacierShell.kt` | `TopLayerHost` 增加 `Modifier.layout`：按 `overlayStart()`（侧栏让位宽度）内缩宿主，布局期读取，侧栏 morph 不触发整壳重组 | 用户 ④ |

`ui/TopLayer.kt` 仍是官方 `example/utils/TopLayer.kt` 的逐字节移植，没有为了侧栏让位改它的结构——只是在调用点给宿主加内缩，符合官方"宿主边界即摆放边界"的契约。

## 4. 验证（模拟器 `emulator-5554`，1080×2400 @420dpi，Android 16 / API 36，宿主 GPU）

| 验证项 | 结果 | 证据 |
|---|---|---|
| 设置文案 | 工作模式值显示「Root · 停用」，下拉里「Shizuku · 停用」「Root · 停用」 | `docs/verification/miuix-os4-r14/05-work-mode-label.png` |
| 设置顺序 | 外观组：… → 主页标签字号（滑块）→ 合成自适应图标（开关） | `04-settings-appearance-order.png` |
| 主页锚点菜单宽度 | 面板 200dp（官方 MinWidth），六行文案完整、无省略；不再顶到 288dp | `01-home-anchor-menu.png` |
| 应用页锚点菜单宽度 | 面板 200dp，三行文案完整 | `02-apps-anchor-menu.png` |
| 首次长按动画 | 冷启动后第一次长按，连续截图能看到锚点隐藏 → 面板由小变大/模糊收敛 → 稳定 | `03-first-longpress-animation.png`（中间帧） |
| 横屏展开侧栏 | 浮层整体停在侧栏右侧（12dp 安全边距），侧栏标签「主页/应用/设置」完整可见 | `06-landscape-rail-avoidance.png` |
| 构建 | `:app:assembleDebug` 通过 | — |
| 单测 | `:app:testDebugUnitTest` BUILD SUCCESSFUL | — |

动画验证方法：`adb shell` 里后台起 `input swipe`（同一坐标长按 900ms），同时设备端连续 `screencap`，取中间帧与稳定帧对比。冷启动后第一次长按可稳定复现"锚点消失 → 面板展开"的中间态。

## 5. 上游限制 / 未做

- `POPUP_PROBE_COUNT = 8` 未变：标题 + 分割线 + 6 行以内一屏显示，更多行在弹层内滚动。主页 9 行菜单首屏 6 行、可滚到最后一行；这是上游探测上限，不通过改库绕过。
- 预热的是"列表首项"的完整菜单：浮层在不可见时已在组合树里，但 `GlassTransformPopup` 在 `active == false` 时提前返回、不组合 `GlassPopupSurface`，因此没有额外测量/采样成本。
- 横屏**折叠胶囊**态没有额外让位：此时侧栏不占横向空间，长按锚点一般也在胶囊下方；如果以后出现遮挡，用同一套"宿主内缩"再加 top inset 即可。
- 真机玻璃光学（材质/描边/阴影观感）仍未验证；模拟器已接宿主 GPU，但不等于真机。
- 未改 MIUIX 库源码（composite build 里的本地补丁仅为 `BuildConfig.kt` 的 git 执行目录修复）。

## 6. 变更面

```
app/src/main/kotlin/com/ayang/glacier/feature/home/HomeScreen.kt
app/src/main/kotlin/com/ayang/glacier/feature/apps/AppsScreen.kt
app/src/main/kotlin/com/ayang/glacier/feature/settings/SettingsScreen.kt
app/src/main/kotlin/com/ayang/glacier/ui/GlacierShell.kt
app/src/main/res/values-zh-rCN/strings.xml
docs/audit/miuix-os4-r14-audit.md
docs/verification/miuix-os4-r14/**
docs/screenshots/home-menu.png（更新）
docs/screenshots/apps-menu.png（更新）
docs/audit/miuix-os4-r13-audit.md（补 R14 说明）
MIUIX_BASELINE.md（§5 / §7 / §10）
README.md（审计索引）
```
