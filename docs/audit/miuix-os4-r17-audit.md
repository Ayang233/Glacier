# R17 · 冻结按钮换雪花图标、设置页按「一级入口 + 二级归类」重排

## 1. 冻结 FAB 改用雪花图标

主页右下角的冻结按钮原来是 `MiuixIcons.Glass.QuietMode`（三道水波纹），语义上跟"冻结"
无关。MIUIX 图标库里没有雪花（`miuix-icons` / `miuix-glass-icons` 全量搜 `Snow` 无结果），
所以新增了一个 24dp 画布的 `ImageVector`：`ui/icons/SnowflakeIcon.kt`。

几何与品牌图标同一套规则：3 条主轴（竖直 + 两条 30° 对角线）+ 6 条臂各一对 60° 分叉，
全部按同一半径/角度生成；用 1.8dp / 1.5dp 圆头描边（官方 Glass 图标的观感），
放进 `GlassFloatingActionButton` 后由组件按内容色着色。

## 2. 设置页重排

### 一级页（去掉所有小标题）

首页不再有 `SmallTitle("通用")`、`SmallTitle("外观")` 这类分节标题，只剩三张卡：

| 卡 | 内容 |
|---|---|
| 通用（留在首页） | 工作模式、启动时验证 |
| 入口 | 主题设置 → / 搜索 → / 自动化 → / 系统 → |
| 关于（用户要求留一级） | 关于 → |

### 二级页（各自成页）

外观、搜索、自动化、系统四组各自一个二级页，骨架统一为
`SettingsSubPage()`：官方二级页的 `PageFrame(expandable = false)` + `BackNavigationAction`
返回按钮 + 滚动条，与 About / Licenses 同构。四个页面分别是：

- **主题设置**（原「外观」）：主题、玻璃效果、图标包、冻结图标去色、紧凑主页网格、
  合成自适应图标、主页标签字号、隐藏应用名称，以及**移到这里的「预测性返回动画」**；
- **搜索**：模糊搜索、T9；
- **自动化**：自动冻结 + 延迟/跳过规则、磁贴动作、快捷方式动作；
- **系统**：通知使用权、使用情况访问权、设备所有者命令。

一级页入口行只做导航（`ArrowPreference`），摘要说明这一类里有什么，不再把设置项平铺出来。
依赖关系仍然用官方 dependent-option 范式（`CapturedAnimatedVisibility`）：
自动冻结关着时延迟/跳过规则收起；隐藏应用名称开着时字号滑块收起。

### 导航

`Routes.kt` 新增 `ThemeSettings` / `SearchSettings` / `AutomationSettings` / `SystemSettings`
四个 `NavKey`；`GlacierShell` 为每个路由加 `entry`（`swipeDismiss = backSwipe`，
与 About/Licenses 一样支持官方交互式滑动返回），二级页标题恒为 1 并沿用
`LocalTopBarObstructionStart` 的宽屏避让。

## 验证

- `:app:compileDebugKotlin`、`:app:testDebugUnitTest`、`:app:assembleRelease` 通过；
- 模拟器（1080×2400 / Android 16）实测：设置首页三卡结构、四个二级页、返回按钮与边缘滑动
  返回都正常；在主题设置页里切换「预测性返回动画」仍是"重建 Activity 但停在当前页"；
- 截图见 `docs/verification/miuix-os4-r17/`。
