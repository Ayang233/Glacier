# R12 · 长按菜单：多选态清单 + 按下态收尾（锚点浮层受阻记录）

> 状态：**已实施**（方案 A 的锚点浮层因环境崩溃暂不可用，见 §3）
> 起因（用户）：① 长按弹出后背景图标不该留下按下浮层；② 多选之后长按，标题应改为
> 「已选择：N」，功能改成 冻结 / 解冻 / 标签分组 / 导出到剪切板 / 从主页移除 / 解冻并从主页移除；
> ③ 长按浮层"很丑、顶部贴图标特别近"，官方那种"按钮按下/变大"的反馈更好。
> 决策（用户）：选方案 A；长按**未选中**项 → 单应用菜单；只有多选且选中之后 → 「已选择：N」。

---

## 1. 最终生效的规则

| 场景 | 菜单 | 标题 | 行 |
|---|---|---|---|
| 非多选态长按 | 单应用 | 应用名 | 使用向 10 行（启动/冻结解冻/定时/置顶/白名单/标签/添加到主屏幕/导出/从主页移除/解冻并移除） |
| 多选态 + 长按**已选中**项 | 批量 | `已选择：N` | 冻结 / 解冻 / 标签分组 / 导出到剪切板 / 从主页移除 / 解冻并从主页移除 |
| 多选态 + 长按**未选中**项 | 单应用 | 应用名 | 同上 10 行 |

作用域 = 标题所说的那个集合；"长按的对象永远落在作用域内"。

批量动作语义（`SelectionActionRunner`）：

| 动作 | 行为 |
|---|---|
| 冻结 / 解冻 | `setFrozenBatch`，已处于目标状态的自动跳过 |
| 标签分组 | 打开标签多选弹层（官方 `Checkbox` 三态），勾选即对整批生效 |
| 导出到剪切板 | 一次复制 N 个包名，每行一个，提示「已复制 N 个包名」 |
| 从主页移除 | 批量移除，随后清空选区并退出多选 |
| 解冻并从主页移除 | 先解冻（失败即中止并提示权限不足），再批量移除 |

## 2. 按下态：删除人工保持

- 删掉 `heldPackage` + `HoldDownEffect`（`ui/components/HoldDownEffect.kt` 整个文件删除）——
  之前是**主动**往 `interactionSource` 里发 `HoldDown`，让按下层一直留着直到弹层退场；
  官方 `MiuixIndication` 本身只按 `PressInteraction` 画、`Release` 后 200ms 淡出
  （beta04 的 `PRESS_RELEASE_HOLD_MS`），并不需要外力维持。
- 长按后手指抬起的那一下会被弹层窗口吃掉（弹层在 DOWN 之后才出现），pressed 可能无法自然释放。
  因此菜单打开的那一格改用**零透明度的官方 `MiuixIndication`**（`pressAlpha/hoverAlpha/focusAlpha = 0`）：
  换实例让 `Modifier.indication` 重建节点，叠层立即退场。不是自绘、也没改主题常量。
- 应用页的行（`Card(showIndication = true)`）去掉了 `holdDownState` 传参，按压层同样交回组件自己。

> 更正一处此前的误判：对比截图时"菜单打开后图标变暗约 45"是弹层的 **dim 遮罩**（顶栏、其它图标同步变暗），
> 不是按下层；用分区亮度对比（calendar -46.6 / topbar -42.0 / chrome -44.7）确认了这一点。

## 3. 锚点浮层（方案 A）：实现完成，但当前环境不可用

按用户选择先实现了官方 `GlassTransformPopup`（锚点 = 被长按的网格单元，`gap = 8dp`，
内容用 `GlassPopupItem`，标题单独一行 + 官方 `HorizontalDivider`），**在本机稳定崩溃**：

```
Fatal signal 11 (SIGSEGV), code 2 (SEGV_ACCERR), fault addr ...
Cause: stack pointer is not in a rw map; likely due to stack overflow.
"RenderThread"  #00 libhwui.so RenderNode::prepareTreeImpl ... （512 帧递归）
```

排查过程（每一步都单独编译、装机复现）：

| 变量 | 结果 |
|---|---|
| `GlassTransformPopup` + 真实 anchor + `AppIconView` 作为 anchorContent | 崩 |
| `GlassTransformPopup` + `anchorContent = {}` | 崩 |
| `GlassTransformPopup` + `visuals(shadow = null)` | 崩 |
| `if (!show) return`（跳过 morph 的进出动画帧） | 崩 |
| 换 `GlassPopup`（只吃 `anchorBounds: Rect`，不涉及 anchor 对象） | 崩 |
| 同一时刻的 `GlassBottomSheet` / `GlassSearchOverlay` | 正常 |

结论：问题在 popup 面板本身的渲染路径（`GlassPopupSurface` 的
`graphicsLayer { outsets = ... }` + morph 的 `BlurEffect`），不是我的用法；
`GlassPopupDefaults` 里没有关闭它的入口，`isRuntimeShaderSupported()` 又只判
`SDK_INT >= 33`（本机 API 36 → 判为支持，但 SwiftShader 的 RenderEffect 有缺陷），
所以库里没有可靠的运行时探测可用。

**处置**：移除 `AppActionPopup`，菜单继续用官方 `GlassBottomSheet`
（用户可见的功能全部到位）。方案 A 的代码要点记在本文件 §5，真机（硬件 RenderEffect）验证通过后
再按 §5 接回来，届时把 `gap`、`GlassPopupItem`、标题行直接复用即可。

> **R13 更新（2026-11-25）**：模拟器接入宿主 GPU（硬件 `RenderEffect`）后，`GlassTransformPopup`
> 在相同环境稳定可用；R13 已按 §5 把主页网格与应用页行的长按菜单都接回锚点浮层，并补上
> 官方 `TopLayer` 宿主。SwiftShader 下的崩溃结论与绕行记录保留在本节，供历史对照；
> 两个上游尺寸探测/缓存行为见 `docs/audit/miuix-os4-r13-audit.md` §3.1。

## 4. 仍未解决

| 项 | 说明 |
|---|---|
| 弹层顶边贴着网格 | 底部弹层的顶边由内容高度决定，这是它的几何；唯一解法是锚点浮层（见 §3）。多选菜单只有 6 行，比单应用 10 行明显更矮，观感已好一档 |
| 应用页长按的按下态 | `Card` 的叠层由组件内部管理，无法像网格那样换 indication 实例；`holdDownState` 已去掉，但"UP 被弹层吃掉"时仍可能保持 |

## 5. 方案 A 接回时需要的东西（备查）

```kotlin
// 网格单元：整格登记锚点，图标标出过渡要复制的内容
Modifier.glassPopupAnchor(anchor, cornerRadius = HomeGridMetrics.CellCorner)   // 最外层 Column
Modifier.glassPopupAnchorContent(anchor)                                        // 图标节点
val anchor = rememberGlassPopupAnchor()                                         // 每格一个，map 按包名登记

// 菜单：必须包一层 Box(fillMaxSize) 才能调用（BoxScope 扩展）
GlassTransformPopup(
    show = ..., onDismissRequest = ..., anchor = anchor, backdrop = ...,
    anchorContent = { AppIconView(...) }, simplified = true, gap = 8.dp,
) {
    Text(title, style = footnote1, color = onSurfaceContainerVariant,
         modifier = Modifier.padding(horizontal = ItemPaddingHorizontal, vertical = 8.dp))
    HorizontalDivider(Modifier.padding(horizontal = DividerPaddingHorizontal),
                      thickness = DividerThickness, color = outline.copy(alpha = 0.15f))
    actions.forEach { GlassPopupItem(text = it.label, icon = it.icon, enabled = it.enabled, onClick = it.onClick) }
}
```

## 6. 改动面

| 文件 | 改动 |
|---|---|
| `feature/common/AppActions.kt` | 新增 `homeSelectionMenuActions()`、`ActionRowSpec`、`actionSpecOf()`（sheet / 锚点菜单共用文案表）、`SelectionActionRunner`；`AppActionSheet` 增加 `title` 覆盖 |
| `data/GlacierRepository.kt` | 新增 `setTagForApps(packageNames, tagId, add)`（批量打标签只刷新一次） |
| `feature/home/HomeScreen.kt` | 长按作用域分流（已选中 → 批量，否则单应用）、批量执行、`TagPickerSheet`、按下态清除、菜单标题 |
| `feature/apps/AppsScreen.kt` · `feature/common/AppSearchResults.kt` | 删除 `holdDown` / `heldPackage` 与 `holdDownState` |
| `ui/components/HoldDownEffect.kt` | 删除 |
| `res/values*/strings_home_r4.xml` | `home_selected_title`、`home_copied_packages`、`home_removed_many` |

## 7. 验证

见 `docs/verification/miuix-os4-r12/`：

- `01-multiselect-menu.png`：多选态长按已选中项 → 标题「已选择：1」+ 6 行
- `02-single-menu-in-multiselect.png`：多选态长按未选中项 → 标题「时钟」+ 10 行
- `03-copy-selected.png`：选中 2 个 → 导出到剪切板 → 剪贴板两行包名 + 提示「已复制 2 个包名」
- 另实测：批量冻结作用于整批（`pm list packages -d` 同时出现 calendar + deskclock）
