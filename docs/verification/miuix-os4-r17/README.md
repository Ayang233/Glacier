# R17 验收：冻结按钮雪花 + 设置页一级/二级重排

模拟器 1080×2400、420dpi、Android 16（API 36）、宿主 GPU、玻璃效果开。

## 截图

| 文件 | 内容 |
|---|---|
| `01-settings-home.png` | 设置首页：无小标题；通用两项 + 主题设置/搜索/自动化/系统入口 + 关于 |
| `02-settings-theme-page.png` | 主题设置二级页：外观全部设置项 + 预测性返回动画 |
| `03-settings-search-page.png` | 搜索二级页 |
| `04-settings-automation-page.png` | 自动化二级页（自动冻结关闭时依赖项收起） |
| `05-settings-system-page.png` | 系统二级页：通知使用权 / 使用情况访问权 / 设备所有者命令 |
| `06-home-fab-snowflake.png` | 主页冻结 FAB 的雪花图标特写 |

## 说明

- 二级页没有底栏、左上角是官方返回按钮，标题居中，与 About / Licenses 一致。
- 在主题设置页切换「预测性返回动画」：Activity 会重建，但停留在当前二级页（导航栈被保存），
  重建后开关状态正确。
- 一级页入口行只负责跳转；所有设置项内容都在对应二级页里。
