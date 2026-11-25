# R12 验收：多选态长按菜单 + 按下态收尾

- 设备：`emulator-5554`（1080×2400 @420dpi，Android 16 / API 36，KernelSU root）
- 冻结模式：`su_disable`（`pm disable --user 0`）
- 构建：`./gradlew :app:assembleDebug` / `:app:testDebugUnitTest` 均通过

| 文件 | 场景 | 结论 |
|---|---|---|
| `01-multiselect-menu.png` | 多选态（选中 1 个）长按**已选中**项 | 标题「已选择：1」，6 行：冻结 / 解冻 / 标签分组 / 导出到剪切板 / 从主页移除 / 解冻并从主页移除 |
| `02-single-menu-in-multiselect.png` | 多选态（选中日历）长按**未选中**的「时钟」 | 回到单应用菜单：标题「时钟」+ 使用向 10 行（作用域只有时钟） |
| `03-copy-selected.png` | 选中 2 个 → 批量「导出到剪切板」 | 剪贴板得到两行包名，提示「已复制 2 个包名」，多选态保留 |

另有实测（未截图）：批量「冻结」作用于整批 —— `pm list packages -d` 同时出现
`com.google.android.calendar` 与 `com.google.android.deskclock`，两个图标同时转灰度。

## 未覆盖

- 锚点浮层（`GlassTransformPopup`）：本机（SwiftShader）稳定崩在 RenderThread（详见
  `docs/audit/miuix-os4-r12-audit.md` §3），已从交付中移除，待真机验证。
  **R13 已在 GPU 模拟器接回并通过，见 `docs/verification/miuix-os4-r13/`。**
- 应用页长按的按下态（`Card` 内部 indication 不可外部替换）。R13 换锚点浮层后已随锚点内容隐藏解决。
