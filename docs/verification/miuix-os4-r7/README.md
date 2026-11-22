# R7 验证截图（emulator-5554 · 1080×2400 · Android 16）

对应整改说明：[docs/audit/miuix-os4-r7-audit.md](../../audit/miuix-os4-r7-audit.md)

| 文件 | 内容 |
|---|---|
| `01-home.png` | 改造前主页基线（主页为空） |
| `02/06/08-apps*.png` | 底栏页签切换（含早期坐标偏移的失败尝试，保留以示排查过程） |
| `04-search.png` | 主页搜索浮层 |
| `09-debug-apps.png` | 新构建的 Apps 页（4 个应用，普通档顶栏） |
| `10-select-one.png` | **点按一行 → 选中**，标题「已选 1 个」，顶栏切批量动作 |
| `11-longpress-sheet.png` | 长按进入操作单过程中的过渡帧 |
| `14-longpress-1s.png` | **长按 → 操作单**，该行保持官方按下态 |
| `15-after-back.png` | 关闭操作单后仍在 Apps 页 |
| `anim-1..3.png` | Apps 顶栏普通档 → 多选档的逐帧（10× 动画时长） |
| `16/17/18-settings*.png` | 设置页；`18` 与 `17` 逐字节相同，用于证明蓝色残影是静态的上游问题 |
| `19-release-settings.png` / `crop-bar*.png` | 同上，用 1.4.0 release 版对照 |
| `43-blue-check.png` / `44-miuix-blue-check.png` | 蓝色残影复现（Glacier）与官方示例底栏对照 |
| `20..25-miuix-*.png` | **MIUIX 官方示例 APK**：一级页 / 设置页 / 进二级页过程 / 二级页 —— 底栏属于一级页，二级页里没有底栏 |
| `26-glacier-settings-bottom.png` | Glacier 设置页底部「关于」入口 |
| `27-glacier-about.png` | 第一版（错误）做法：底栏浮在二级页上，仅作对照 |
| `28-back-to-apps.png` | 二级页点底栏回落一级页（当时底栏还浮在二级页上） |
| `30/31-home*.png` | 主页加入一个应用后的基线（含 FAB） |
| `hsel-1..3.png` | 主页点「多选」：顶栏交叉淡入淡出 + FAB 缩小淡出（10× 时长） |
| `32..35-tag*.png` | 新建第二个标签，出现页签行 |
| `taganim-1..4.png` | 进多选时页签行收起、网格随高度平滑上移 |
| `37/38-select-then-back.png` | **多选态返回 → 退出多选**，不再退出应用 |
| `39-more-from-selection.png` | 多选档「更多」→ 操作单 |
| `40/41-apps-search*.png` | 搜索结果点按 → 选中并收起浮层 |
| `42-batch-freeze.png` | 批量冻结 → 退出多选 + 统一 toast |
| `45/46-home*.png` | 退出多选后页签行与 FAB 复原 |
| `50/51/52-*.png` | 第二版（仍不对）做法：靠 `fadeOut` 收掉底栏，仅作对照 |
| `60..66-*.png` | 结构照搬官方后：一级页底栏正常、底栏页签切换准确 |
| `cold-1.png` / `63-cold-start.png` | 冷启动（无交互）底栏正常渲染，连续三次复测一致 |
| `nav-1..4.png` | **进二级页的 10× 逐帧：底栏随一级页一起左移、被新页盖住，无缩放无淡出** |
| `67-about-settled.png` | **二级页稳定态：完全没有底栏**，正文铺到屏幕底部 |
| `68-back-with-bar.png` | 返回一级页，底栏随页面一起滑回来 |
| `69-apps-select.png` | 多选态回归（顶栏批量动作 + 底栏并存） |
