# R13 验收：main 最新基线 + 长按锚点浮层 + 自造轮子清查

- 设备：`emulator-5554`（1080×2400 @420dpi，Android 16 / API 36）
- 渲染：**宿主 GPU**（`dumpsys SurfaceFlinger`：Android Emulator OpenGL ES Translator
  (AMD Radeon RX 9070 XT)，GLES 3.1，硬件 `RenderEffect`）——R12 的 SwiftShader 崩溃不再出现
- 基线：MIUIX main `542463a0`（比 tag `v0.9.5-beta04` 多 12 个提交）
- 构建：`./gradlew :app:assembleDebug`、`:app:testDebugUnitTest` 均通过

| 文件 | 场景 | 结论 |
|---|---|---|
| `01-home-menu-anchor.png` | 主页长按「GIF 助手」 | 官方 `GlassTransformPopup` 从被按的格子原地展开；标题行 + 6 行（启动/冻结/定时任务/置顶/加入白名单/标签分组），面板不再贴顶 |
| `02-home-menu-scrolled.png` | 弹层内向上滑 | 剩余 3 行可见：添加到主屏幕 / 导出到剪切板 / 从主页移除，中文文案完整不省略 |
| `03-apps-menu-anchor.png` | 应用页长按第一行 | 从该行图标原地展开管理菜单（应用信息 / 导出到剪切板 / 提取安装包）；行按下层不残留 |
| `04-menu-closed-no-press-residue.png` | 点外部关闭菜单 | 图标回弹复位、页面干净，无按下层/遮罩残留 |
| `05-sort-sheet-header.png` | 应用页 → 排序 | 新版 `GlassBottomSheet` 的渐进模糊标题栏；内容从标题栏下方开始（`PaddingValues` 已接入） |
| `06-filter-sheet-header.png` | 应用页 → 筛选 | 同上；4 个 `SwitchPreference` 完整可见 |
| `07-about-glasscard.png` | 关于页（浅色） | 信息卡换成官方 `GlassCard`：半透明、20dp 圆角，动态背景从卡下透出；品牌名 textureBlur 保留 |
| `08-about-glasscard-dark.png` | 关于页（深色） | 深色档同样由 `GlassCardDefaults` 控制，通透对比正常 |
| `09-settings.png` | 设置页 | Preference 行 / 分组卡 / 玻璃下拉均为官方组件；底栏玻璃正常采样 |
| `10-home-dark.png` | 深色主页 | 主题、图标、FAB、玻璃底栏跟随官方深色板 |
| `11-landscape-rail.png` | 横屏（宽屏） | 玻璃侧栏折叠成顶部胶囊（官方 `GlassNavigationRail` 行为），内容按 `AppPageInsets` 让位 |

## 已验证的交互

- 锚点菜单：展开 / 关闭 / 关闭回弹 / 点外部关闭 / 返回键关闭 / 弹层内滚动，均正常。
- 应用页长按：菜单从行图标展开；关闭后该行无按下层。
- 弹层：排序 / 筛选 / 标签编辑 / 标签选择 / 延时冻结 / API Activity 的操作单都跟随新版标题栏语义。
- 主页多选、批量动作、搜索浮层、设置项、关于页滚动、宽屏侧栏：回归通过（细节同 R10–R12，不重复截图）。

## 未覆盖

- 真机玻璃光学（材质 / 描边 / 阴影观感）：模拟器已用宿主 GPU，但仍不等同真机。
- 冻结后端（Shizuku / Dhizuku / DeviceOwner / Island / 特权）：本轮未改冻结链路。
- 覆盖升级：本轮未发版。
