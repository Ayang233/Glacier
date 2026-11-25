# R16 验收：设置顺序 / 预测性返回 / 图标 / 隐藏名称

模拟器 1080×2400、420dpi、Android 16（API 36）、宿主 GPU、玻璃效果开。

## 截图

| 文件 | 内容 |
|---|---|
| `01-settings-navigation-and-appearance.png` | 通用组收尾「预测性返回动画」开关；外观组顺序「合成自适应图标 → 主页标签字号 → 隐藏应用名称」 |
| `02-home-labels-hidden.png` | 隐藏应用名称后的主页（图标位置与显示时一致） |
| `03-home-labels-shown.png` | 显示名称（对照，确认关闭后图标没有位移） |
| `04-predictive-back-on-preview.png` | 预测性返回**开**：返回手势进行中，About 页跟着手指右移 |
| `05-predictive-back-off-no-preview.png` | 预测性返回**关**：同一手势位置，About 页原地不动，松手才播 pop 转场 |
| `06-app-icon-snowflake.png` | 新品牌图标（About 页 74dp 展示）：六向对称雪花、黑主体白底、主体略缩小 |

## 说明

- 预测性返回开关的差异用"手势中间帧"证明：两张截图在同一个滑动位置拍摄，
  开档页面已经跟手位移，关档页面未动；两者都会在松手后播放返回转场。
- 隐藏名称的截图与显示名称的截图对齐后，图标中心坐标一致（`PageMetrics.GridAppIcon` 56dp 未变），
  说明行距/列宽仍走原来的 MIUIX token。
