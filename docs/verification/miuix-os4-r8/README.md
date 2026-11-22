# R8 验证截图（emulator-5554 · 宽屏间距自查）

对应说明：[docs/audit/miuix-os4-r8-audit.md](../../audit/miuix-os4-r8-audit.md)

宽屏形态用 `wm size` / `wm density` 模拟（验收后已 reset）：

- 914dp 横屏：`wm size 2400x1080` + `wm density 420`（侧栏为折叠胶囊，点开后成面板）
- 1333dp 横屏：`wm size 2000x1200` + `wm density 240`（侧栏自动展开）

| 文件 | 内容 |
|---|---|
| `user-crop-zoom.png` | 用户反馈的截图放大（侧栏与卡片贴在一起） |
| `g-914-expanded.png` | **修复前**：914dp 手动展开侧栏，面板右缘 686 / 卡片左缘 688 |
| `g-914-expanded-fixed.png` | 修复 `pageContentPadding` 后的主页（页签行已有 12dp 间距） |
| `g-914-apps-fixed.png` | 修复后应用页：卡片左缘 721px |
| `ex-914-collapsed.png` / `ex-914-expanded.png` | 官方示例 APK 同一窗口：面板右缘 686 / 内容左缘 720 |
| `v2-914-apps-expanded.png` | 最终版（含断点与 inset 对齐）应用页，720-721px 与官方一致 |
| `v2-914-collapsed.png` / `v2-914-current.png` | 同轮宽屏状态记录 |
| `g-wide-home.png` / `ex-wide-home.png` | 1333dp 下双方内容起点均为 411px |
| `n-home.png` / `n-apps.png` / `final-narrow-home.png` | 窄屏回归：12dp 外边距、底栏均正常 |
| `fix2-narrow-apps.png` | **修复"左有右无"后**：竖屏卡片 32px…1048px（左右各 12dp） |
| `fix2-914-apps.png` | 914dp 横屏：卡片 721px…2367px，左距面板 12.6dp、右距屏幕 12.6dp |
| `fix2-narrow-settings.png` / `fix2-narrow-home.png` | 设置页（卡片自带 12dp）与主页窄屏回归 |
