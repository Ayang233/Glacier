# MIUIX 44f1d315 基线升级 + 自造轮子清查验收（R6）

- 时间：2026-11-22
- 基线：MIUIX `44f1d315`（`v0.9.5-beta03` 之后第 18 个 main 提交，`LIBRARY_VERSION = 0.9.5`）
- 上一基线：`9128921c`
- 应用：Glacier 1.3.0（versionCode 4）
- 环境：`android16` AVD / Android 16（API 36）/ 1080×2400 / 420dpi / x86_64，宿主 GPU 渲染（真实 AGSL 模糊路径）
- 宽屏形态通过 `wm size` / `wm density` 模拟：
  - 折叠侧栏：`1600×1000 @ 240dpi` → 1066dp 宽（`≥840dp` 且 `<1200dp`）
  - 展开侧栏：`2000×1200 @ 240dpi` → 1333dp 宽（`≥1200dp`）
  - 验收结束后已 `wm size reset` / `wm density reset`
- 证据分级：L1 源码接线 / L2 运行时能力 / L3 像素光学

## 1. 构建与测试

| 项 | 结果 |
|---|---|
| `:app:assembleDebug` | 通过 |
| `:app:testDebugUnitTest` | 通过（`TextIndexTest`） |
| `tools/miuix-baseline.sh verify` | 通过（HEAD == `44f1d315`，本地补丁已应用） |
| 覆盖安装（同签名 debug → debug） | 通过，数据保留 |

## 2. 结构变化（本轮改的就是这些）

| 变化 | 验收关注点 | 截图 |
|---|---|---|
| `NavDisplay` 不再被 `railStartInset` 右移；侧栏宽度改由 `contentInsets` 下发 | 展开侧栏时内容整体右移 262dp，侧栏与背景都不被裁断 | `r6-05` / `r6-07` |
| 顶栏 inset 改走 `appPageTopBarInsets()` | 标题、副标题、动作不被状态栏/胶囊压住 | `r6-01` / `r6-03` |
| 搜索浮层用 `safeInsets.top + searchTop` + `appPageHorizontalInsets()` | 折叠态搜索条位于胶囊下方；展开态右移与内容对齐 | `r6-06` / `r6-07` |
| `pageContentPadding` 改惰性 `PaddingValues` | 侧栏 morph 过程中不出现"内容先跳后归位" | `r6-05`（展开态稳定态） |
| 滚动条 `trackPadding = contentPadding` + 顶栏行为 | 轨道起止与内容区一致 | `r6-04` |
| Home 复用 `AppSearchResults` | 搜索结果行与 Apps 页一致 | `r6-06` |
| 关于页品牌区改用 `PaddingValues` 委托 | 头图横向居中、滚动后标题淡入且不与胶囊重叠 | `r6-02` / `r6-08` |

## 3. 逐张结论（L2）

| 截图 | 场景 | 结论 |
|---|---|---|
| `r6-01-narrow-home.png` | 窄屏首页 | 标题在状态栏下方、动作右对齐、底栏胶囊 24dp 边距与官方一致 |
| `r6-02-narrow-about.png` | 窄屏关于页（顶部） | 品牌区居中；标题按官方曲线 alpha=0（滚动前不可见，非回归） |
| `r6-03-wide-collapsed-settings.png` | 折叠侧栏 + 设置页 | 顶部胶囊居中、页面标题按官方语义淡出；卡片 12dp、`SmallTitle` 28dp |
| `r6-04-wide-collapsed-settings-scrolled.png` | 同上滚动态 | 顶栏渐进模糊正常，内容从头图下方穿过，未被横向裁断 |
| `r6-05-wide-expanded-rail-home.png` | 展开侧栏 + 首页 | 侧栏满高不被裁切；标题与空态在剩余区域内居中 |
| `r6-06-wide-collapsed-search.png` | 折叠态 + 搜索浮层 | 搜索条位于胶囊下方，不重叠；键盘上方无遮挡 |
| `r6-07-wide-expanded-rail-search.png` | 展开侧栏 + 搜索浮层 | 搜索条与键盘整体右移 262dp，侧栏完整可见 |
| `r6-08-wide-about-scrolled.png` | 展开/折叠过渡后关于页滚动 | 标题出现在返回箭头右侧（非居中标题，不参与避让），卡片与 `SmallTitle` 对齐 |

## 4. 未覆盖

| 项 | 原因 |
|---|---|
| 底部弹层（排序 / 筛选 / 操作单）逐像素 | 模拟器无第三方应用，操作单行数不足以撑满；弹层底部让位与改动前等价（`safeInsets.bottom` == 系统 `navigationBars`） |
| 真机玻璃光学（L3） | 需要真实设备 |
| 冻结业务链路 | 模拟器无可用后端 |
| 旋转 / 分屏切换过程中的 transient | 只在稳定态取图；morph 期间的中间帧未逐帧比对 |
