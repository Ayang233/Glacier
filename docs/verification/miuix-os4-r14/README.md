# R14 验收截图

环境：`emulator-5554`，1080×2400 @420dpi，Android 16（API 36），模拟器接入宿主 GPU
（AMD Radeon，GLES 3.1 硬件 RenderEffect），玻璃效果开启。对应审计：`docs/audit/miuix-os4-r14-audit.md`。

| 文件 | 内容 |
|---|---|
| `01-home-anchor-menu.png` | 主页长按锚点菜单：面板 200dp（官方 `MinWidth`），宽度由菜单内容驱动，不再被写死宽度顶到 288dp；六行文案完整 |
| `02-apps-anchor-menu.png` | 应用页长按锚点菜单：同样 200dp，三行文案完整 |
| `03-first-longpress-animation.png` | 冷启动后**第一次**长按的展开动画中间帧：锚点内容已隐藏、面板正在从小变大（与稳定帧对比可见半径/位置差异） |
| `04-settings-appearance-order.png` | 外观组顺序：主页标签字号（滑块）在前、合成自适应图标（开关）在后 |
| `05-work-mode-label.png` | 工作模式下拉文案：「Shizuku · 停用」「Root · 停用」 |
| `06-landscape-rail-avoidance.png` | 横屏 + 展开侧栏：浮层停在侧栏右侧，侧栏标签完整可见（`TopLayerHost` 按侧栏让位宽度内缩） |

补充说明：

- 动画截图由设备端连续 `screencap` 采样得到（宿主端并发截屏会丢输入事件），冷启动后第一次长按可稳定复现。
- 主页菜单超过 6 行仍受上游 `GlassPopupColumn.POPUP_PROBE_COUNT = 8` 限制，在弹层内滚动，属已知上游行为。
