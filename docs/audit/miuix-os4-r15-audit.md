# R15 · 底部弹层避开屏幕大 R 角与隐藏手势提示线

真实机型反馈：底部弹层（排序 / 筛选等）的最后一行会被屏幕的大 R 角裁掉。用户设备使用的是
手势导航并隐藏了手势提示线。本文记录 MIUIX 侧有没有现成方案、结论、改动与验证。

## 1. 结论

**MIUIX 没有「自动避开屏幕圆角」的机制**，只提供了一个读取圆角的原语 `getRoundedCorner()`；
`GlassBottomSheet` 的底部安全区按官方契约本来就由调用方提供。所以这不是某个参数没开，
而是要在 Glacier 侧自己算。

## 2. 官方依据

| 事实 | 位置 |
|---|---|
| 提供读圆角的公开 API：Android 上 API 31+ 读 `RoundedCorner.POSITION_BOTTOM_LEFT`，读不到退回 `android:rounded_corner_radius_bottom`，其它平台 `0.dp` | `miuix-core/.../utils/Utils.kt:27-31`、`Utils.android.kt:25-53` |
| 官方文档把它定位为「设备圆角」适配工具（示例是自己拿它设组件形状） | `docs/zh_CN/guide/multiplatform.md`「设备圆角」 |
| 库内唯一使用者是对话框：圆角按「屏幕圆角 − 外边距」推成同心圆，**没有拿它做避让** | `miuix-ui/.../layout/DialogContentLayout.kt:390-399` |
| `GlassBottomSheet` 底角**保持直角**，面板贴屏幕底 | `miuix-glass/.../GlassBottomSheet.kt:83` |
| 底部导航栏内边距**由调用方提供**；`insideMargin` 默认 `DpSize(12.dp, 0.dp)`（底 0） | `docs/zh_CN/components/glass/glassbottomsheet.md` 的属性表 |
| 官方示例只写 `.navigationBarsPadding().padding(bottom = 16.dp)` | `example/.../GlassBottomSheetDemo.kt:138-142` |
| `ProvideDisplayCutoutFix` 只合并 display cutout，不处理圆角 | `miuix-ui/.../theme/ProvideDisplayCutoutFix.android.kt` |
| 上游 main 比我们基线只多一个 docs 提交（`7167594a`），没有相关新 API | `git ls-remote` / GitHub API 核对 |

## 3. 为什么只有真机才明显

WindowInsets 里没有「圆角」这种 inset，圆角是显示器的物理裁切，窗口内容画进去就被裁掉。
另外，**隐藏手势提示线后 `navigationBars` 底部 inset 会变成 0**，但屏幕底边那条手势区仍会抢触摸
（系统用 `mandatorySystemGestures` 报它）。两件事叠加，底层留白只剩我们自己的 12dp 分节间距。

从用户截图量到的证据：卡片底边到屏幕底只有 ~37px ≈ 12dp，正好等于 `PageMetrics.SectionGap`，
说明该机 `navigationBars` 底部就是 0。对照模拟器：圆角 28px（10.7dp）、导航栏 24dp、
手势区 32dp，底部留白 36dp，所以模拟器一直复现不出来。

## 4. 改动（方案 A）

新增 `rememberSheetBottomPadding(extraBottom)`（`app/src/main/kotlin/com/ayang/glacier/ui/PageInsets.kt`），
底部留白 = `max(safeInsets 底部, mandatorySystemGestures 底部, getRoundedCorner()) + extraBottom`：

- 三个系统值取**大**而不是相加，避免把内容顶高一整条导航栏；
- 圆角值来自官方 `getRoundedCorner()`，不自己读 `WindowInsets`；
- 纯函数部分 `sheetBottomPadding(...)` 单独抽出，便于单测；
- 圆角为 0、导航栏有值的设备（含模拟器）结果与只留导航栏一致或略大 8dp（手势区）。

替换的 6 个 `GlassBottomSheet` 调用点：

| 位置 | 弹层 | 额外留白 |
|---|---|---|
| `HomeScreen.kt` `TagsPickerSheet` | 标签分组（勾选） | `SectionGap` |
| `HomeScreen.kt` `TagEditorSheet` | 标签管理 | `BottomGap` |
| `HomeScreen.kt` `DeferredSheet` | 延时冻结 | `BottomGap` |
| `AppsScreen.kt` `SortSheet` | 排序 | `SectionGap` |
| `AppsScreen.kt` `FilterSheet` | 筛选 | `SectionGap` |
| `AppActions.kt` `AppActionSheet` | 应用操作单（API 窗口） | `SectionGap` |

其中 `TagEditorSheet`（原末尾固定 `BottomGap`）与 `DeferredSheet`（原 `Spacer(BottomGap)`）
此前**完全没有**导航栏留白，这次一并有值。

## 5. 验证

- `:app:compileDebugKotlin`、`:app:testDebugUnitTest` 通过（新增 `PageInsetsTest` 4 条取值规则）。
- 模拟器（1080×2400，宿主 GPU，Android 16）实测排序 / 筛选弹层：卡片底边到屏幕底
  从 **96px / 36dp → 117px / 44.5dp**，与公式 `max(24, 32, 10.7) + 12 = 44dp` 一致；
  截图见 `docs/verification/miuix-os4-r15/`。
- **大 R 角 + 隐藏手势提示线的真机效果需要用户侧验收**：模拟器圆角只有 28px、导航栏 inset 非 0，
  复现不出用户的现象。

## 6. 仍未做

- 玻璃面板本身仍然贴屏幕底、底角为直角（官方设计），物理圆角会裁掉面板底部两个角的一点点；
  面板级避让需要 `outsideMargin` 高度生效或新 API，MIUIX 目前没有，改库不在本轮范围。
- 建议给上游提 feature request：把屏幕圆角纳入 `GlassBottomSheet` 的默认底部安全区，
  或提供 `LocalRoundedCorner` 一类的组合局部值，避免每个应用各算一遍。
