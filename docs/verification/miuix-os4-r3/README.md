# Glacier MIUIX OS4 第二轮 · 构建与运行验收记录（R3）

- 验收日期：2026-11-19
- 验收对象：`Hail/Glacier`（`com.ayang.glacier`），本轮全部代码改动见 `docs/plan/r3-change-list.md`
- 代码状态：`git` 提交见仓库历史（本轮以 `wip: 第二轮整改前检查点` 为基线，验收基于本轮重新构建的 Debug / Release APK）
- MIUIX 基线：本地 `../miuix` = `58b0a3d`，`LIBRARY_VERSION = 0.9.5`（composite build，源码依赖）
- 参考基线：MIUIX `example/shared`（Apache-2.0）、Mishka `main@e5a4a41`、InstallerX-Revived `main@caccb787`（仅参考结构与尺寸）

## 一、构建验收

| 项 | 命令 | 结果 | 证据 |
|---|---|---|---|
| Debug 构建 | `gradlew :app:assembleDebug` | **BUILD SUCCESSFUL** | `app/build/outputs/apk/debug/Glacier-debug.apk`（约 49.8 MB） |
| Release 构建 | `gradlew :app:assembleRelease` | **BUILD SUCCESSFUL**（含 R8、`lintVitalRelease`、Compose mapping） | `app/build/outputs/apk/release/Glacier-release.apk`（4,252,852 B） |
| R8 / 资源压缩 | `isMinifyEnabled=true`、`isShrinkResources=true` | 正常，无 `MissingClass`/`R8` 报错 | Gradle 日志 `BUILD SUCCESSFUL`，无 warning 升级为错误 |
| 许可证冲突 | — | 未新增任何依赖；`squircle` 复用同一 MIUIX 仓库与 Apache-2.0 | `app/build.gradle.kts` 依赖块未改动 |
| 运行时崩溃 | Debug / Release 全程 adb 操作与截图 | 未出现崩溃、ANR；`adb logcat` 无 `FATAL EXCEPTION` | 见下文各页面截图 |

构建命令输出摘要：

```
> Task :app:assembleDebug
BUILD SUCCESSFUL in 15s
> Task :app:assembleRelease
BUILD SUCCESSFUL in 1m 21s
> Task :app:lintVitalRelease
> Task :app:minifyReleaseWithR8
```

## 二、运行环境

| 项 | 值 |
|---|---|
| 设备 | Android 16 模拟器 `sdk_gphone64_x86_64`（emulator-5554） |
| SDK | API 36（Android 16） |
| 分辨率 / 密度 | 1080 × 2400，density 420（2.625x）→ 411dp × 914dp |
| 导航方式 | 手势导航（底部手势条） |
| GPU | `ro.hardware.egl = emulation`（软件/模拟渲染）→ **真实 GPU 光学观感未验证** |
| 字体 | `font_scale` 1.0（另测 1.3 / 2.0，见截图） |
| 旋转 | 竖屏 / 横屏各测一轮，最后恢复竖屏 |

## 三、截图清单（全部来自本轮重新构建并安装的 APK）

> Debug 包（`com.ayang.glacier.debug`）用于有数据的完整 UI 验收；Release 包（`com.ayang.glacier`）用于发布构建的冒烟验收。截图均直接 `adb shell screencap` 取得，未使用上一轮图片。

| 文件 | 场景 | 说明 |
|---|---|---|
| `01-home-light.png` | Home 浅色 | 大标题 + 标签页签（neutral 玻璃页签）+ 4 列应用网格 + FAB + 24dp 边距底栏 |
| `02-home-dark.png` | Home 深色 | 深色玻璃底栏、深色页签轨道 |
| `03-apps-light.png` | Apps 浅色 | 卡片列表（12dp 边距 / 12dp 间距）+ BasicComponent 行 |
| `04-apps-dark.png` | Apps 深色 | 同上，深色 |
| `05-settings-light.png` | Settings 浅色 | SmallTitle + GroupCard + 官方 Preference |
| `06-settings-bottom.png` | Settings 底部 | 滚动后顶栏折叠为紧凑标题（glass band） |
| `07-search-open.png` | 搜索展开（浅色） | 官方 GlassSearchOverlay：搜索条 `Clear`（清空）与 `Cancel`（关闭）分离 |
| `08-search-results.png` | 搜索输入 + 结果 | 结果使用 Card + BasicComponent 行（40dp 图标） |
| `09-search-closed.png` | 点 × 关闭后 | 回到 Home，标签编辑**未**打开（BUG 回归验证） |
| `10-tag-editor.png` | 标签编辑 BottomSheet | 行 BasicComponent + 行尾编辑/删除图标 + 新建标签 |
| `11-tag-name-dialog.png` | 标签命名 GlassDialog | 空文本时保存按钮禁用；取消/保存并排 |
| `12-selection-mode.png` | 多选态 | 顶栏 5 个动作（28dp 图标）+ squircle 选中描边 |
| `13-action-sheet.png` | 应用操作 BottomSheet | 10 行动作 + 包名脚注 + destructive 红色卸载 |
| `14-sort-sheet.png` | 排序 BottomSheet | 统一 BasicComponent 行，选中项 endActions 打勾 |
| `15-filter-sheet.png` | 筛选 BottomSheet | SwitchPreference 默认行内边距 |
| `16-about-light.png` | About 浅色 | 88dp 白底品牌区 / 74dp 黑色标记 / 35sp Bold 名称 / 14sp 版本 / 官方卡片分组 |
| `17-about-scrolled.png` | About 滚动 | 滚动后顶栏紧凑标题 |
| `18-licenses.png` | Licenses | 官方卡片结构 + ArrowPreference 入口 |
| `19-settings-dark.png` | Settings 深色 | 深色卡片 / 开关 / 下拉 |
| `21-bigfont-1.3.png` | 大字体 1.3x | Home |
| `22-bigfont-2.0.png` | 大字体 2.0x | Home：标签省略、导航标签走官方 LargeLabelSize |
| `23-landscape-collapsed.png` | 横屏 · 侧栏折叠 | 官方折叠胶囊（顶部居中）+ 标题按 rail progress 淡出 |
| `24-landscape-rail-expanded.png` | 横屏 · 侧栏展开 | 250dp 玻璃面板 + 内容让位 + 标题显示 |
| `25-portrait-restored.png` | 转回竖屏 | 布局恢复、分页器吸附当前页、底栏回归 |
| `26-api-activity.png` | ApiActivity（SHOW_APP_INFO） | 独立窗口录制层 + 操作面板 |
| `27-action-sheet-dark.png` | 深色操作面板 | 面板背后内容被玻璃采样（L2 证据） |
| `30-release-home.png` | Release 包 Home | 发布构建冒烟 |
| `31-release-apps.png` | Release 包 Apps | 发布构建冒烟 |
| `32-release-search.png` | Release 包搜索 + 结果 | 搜索结果顶部让位状态栏 + 搜索条高度 |
| `33-release-search-closed.png` | Release 包关闭搜索 | 关闭后回到应用列表 |
| `34-release-action-sheet.png` | Release 包操作面板 | |
| `35-release-sort-sheet.png` | Release 包排序面板 | |
| `36-release-filter-sheet.png` | Release 包筛选面板 | |

## 四、底栏几何实测（本轮重点修复项）

本轮按 MIUIX 官方 example 的调用（`miuix/example/shared/src/commonMain/kotlin/AppContent.kt:772-774`、`GlassPage.kt:272-334`）把底栏改成：

- `GlassNavigationBar` 放进 Shell 的 `Scaffold.bottomBar`；
- 外层 `fillMaxWidth().padding(horizontal = 24.dp)`；
- `defaultWindowInsetsPadding = false`，底部安全区由 layout 处理：`max(24dp, safeBottom + 8dp)`。

`uiautomator dump` 实测（1080 × 2400，density 2.625，竖屏）：

| 量 | 本轮实测 | 上一轮（r2） | 官方 example 期望 |
|---|---|---|---|
| 底栏胶囊宽度 | **363.4dp**（954px） | 411dp（整屏） | 411 − 48 = 363dp |
| 目的地（主页）bounds | `[97,2190][375,2300]` | `[34,2179][354,2289]` | 24dp 边距后等分 |
| 目的地（应用）bounds | `[401,2190][679,2300]` | `[380,2179][700,2289]` | 等分 |
| 目的地（设置）bounds | `[705,2190][983,2300]` | `[726,2179][1046,2289]` | 等分 |
| 页面底部让位 | 由 Scaffold 计算：胶囊总高 106.3dp + 12dp 末尾留白 | 手算 `Height + navBars + 32dp`（偏大 68~104dp） | Scaffold bottomBar 语义 |

结论：底栏宽度与官方 example 完全一致（363dp），不再整屏拉满。

## 五、搜索 × BUG 回归步骤（本轮实测通过）

环境：Debug 包；`adb shell` 坐标基于 1080×2400。

1. 进入 Home（`01-home-light.png`）。
2. 点顶栏搜索（约 `716,196`）→ 搜索浮层展开（`07-search-open.png`）。
3. 输入 `glac` → 结果列表出现（`08-search-results.png`）。
4. 点搜索结果右侧的 `Cancel`（约 `985,170`）。
5. 观察：搜索浮层关闭并播放退出动画，回到 Home，**标签编辑没有打开**，焦点/键盘收起（`09-search-closed.png`）。
6. 再次展开搜索 → 输入 → 关闭，重复 3 次：行为一致。
7. 切到 Apps → 搜索 → 关闭 → 再切回 Home → 搜索：行为一致（页面间状态互不串扰）。

根因与官方语义（源码证据）：

- 官方 `GlassSearchOverlay` 的两个按钮语义不同：输入框内 `contentDescription = "Clear"` 只清空 `query` 并重新聚焦（`miuix/miuix-glass/.../GlassSearchOverlay.kt:444-459`），右侧 44dp 的 `Cancel` 才调用 `onDismissRequest`（`:490-503`），返回键/Esc 也走 `onDismissRequest`（`:241-258`）。
- 上一轮的解释是"浮层在 Scaffold body 槽里、被顶栏吃掉点击"；本轮把浮层放回 Scaffold 之后（兄弟节点，画在顶栏之上），见 `ui/components/PageFrame.kt` 的 `overlay()` 位置，与官方 example 一致（`example/GlassPage.kt:684-694`）。
- 本轮同时把 `if (searchOpen) { GlassSearchOverlay(expanded = true) }` 改为**常驻组合 + `expanded = searchOpen`**，保证退出动画、query 清空、键盘收起、返回键都由官方组件处理。修复前 `if` 包裹会让组件在关闭瞬间直接卸载，400ms 几何动画与 150ms 取消按钮动画永远不播放。

## 六、Glass 证据等级（L1 / L2 / L3）

| 等级 | 定义 | 本轮结论 | 证据 |
|---|---|---|---|
| L1 | 源码接线正确：组件在组合树里、采样源是兄弟节点、无自采样 | **通过** | 结构见 `docs/audit/miuix-os4-glass-audit.md` 第二、三节；录制层树：Shell `Box.layerBackdrop(R)` + 页面 `Scaffold` 的 `body Box.layerBackdrop(P)`，玻璃组件均为录制层兄弟 |
| L2 | 运行时挂载、绘制、交互正常 | **通过（模拟器）** | 底栏/顶栏/页签/搜索/FAB/操作面板/Dialog 均在截图中正常绘制与交互；`27-action-sheet-dark.png` 可见面板背后内容被玻璃采样 |
| L3 | 真实 GPU 下的折射、模糊、边缘光光学观感 | **未验证** | `ro.hardware.egl = emulation`（软件/模拟渲染）。截图中的模糊只能证明 L2 渲染路径，不能证明真实折射与边缘光。顶栏 `progressiveBlur` 在模拟器上主动降级为 band 渐变（`Platform.isEmulator`） |

## 七、未验证 / 未完成项（如实记录）

| 项 | 状态 | 说明 |
|---|---|---|
| L3 光学验收 | 未验证 | 无真实硬件 GPU 环境；`emulation` EGL 下不得宣称真实折射/边缘光 |
| 大字体 2.0 完整页面 | 部分验证 | Home 网格标签会省略（正常），但设置页/弹层在 2.0x 下的行高与换行未逐页截图 |
| RTL 镜像 | 未验证 | 布局使用 `placeRelative`/`start-end` 语义，但未切换 RTL 语言实测 |
| 真机 MIUIX OS4 遮罩 | 未验证 | 第三方自适应图标按 72dp 安全区 1.5× 渲染 + squircle 裁切；未在 HyperOS 真机桌面实测 |
| 页面切换动画观感 | 未逐帧验证 | 分页器使用官方 `PagerNavigationSpringSpec` 与 `springAnimateToPage`；截图无法呈现动画曲线 |
| 搜索浮层与底栏的关系 | 已知行为 | 搜索浮层位于页面层，Shell 的悬浮底栏绘制在其上（截图 `07`）；结果列表底部已加 `LocalShellBottomBarHeight` 让位 |
| 大字体 + 横屏组合 | 未验证 | — |
| About 页滚动淡入/缩放动画 | 未实现 | 官方 AboutPage 的 logo 滚动淡出属于增强项，当前只保留顶栏滚动折叠 |
| 第三方图标包位图 | 未验证 | 无第三方图标包安装环境 |
| 三键导航模式 | 未验证 | 模拟器为手势导航；底部安全区公式来自官方 example（`max(24dp, safeBottom+8dp)`） |

## 八、复现命令

```powershell
# 构建
./gradlew :app:assembleDebug :app:assembleRelease

# 安装 + 启动（Debug）
adb install -r -t app/build/outputs/apk/debug/Glacier-debug.apk
adb shell am start -n com.ayang.glacier.debug/com.ayang.glacier.ui.MainActivity

# 底栏几何
adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml

# 横竖屏
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1   # 横屏
adb shell settings put system user_rotation 0   # 竖屏

# 大字体
adb shell settings put system font_scale 1.3   # 或 2.0，测完恢复 1.0

# API 窗口
adb shell am start -a android.intent.action.SHOW_APP_INFO \
  --es android.intent.extra.PACKAGE_NAME com.ayang.glacier.debug \
  -n com.ayang.glacier.debug/com.ayang.glacier.feature.api.ApiActivity
```
