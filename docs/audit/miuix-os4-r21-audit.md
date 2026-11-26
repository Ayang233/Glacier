# R21 · 升级 MIUIX 最新基线 + 全项目自造轮子复查 + 四个用户问题

本轮四类工作：

1. MIUIX 基线从 `542463a0`（`v0.9.5-rc01`）升到官方 main 最新 `e7c108ee`；
2. 按用户要求再次全项目扫描「自造 UI / 动画 / 尺寸 / 间距 / 边距」，把官方已有能力替换回去；
3. 修复「冻结后点应用启动延迟」「应用页重复显示不在主页」「预测性返回」「关于页杂乱」；
4. 编译、装模拟器，交付验收清单。

审计明细见 `.workbuddy/audit-findings.md`（只读审计，含逐条官方源码行号）。

## 一、基线升级：`542463a0` → `e7c108ee`

官方 `v0.9.5-rc01` 的 tag 仍指向上一轮基线 `542463a0`；main 上新增 10 个提交，直接相关的是：

| commit | 内容 | Glacier 处理 |
|---|---|---|
| `6aa83241` | `GlassBottomSheet.progressiveBlur` 默认 `true → false` | 6 个底部弹层显式传 `progressiveBlur = true`（官方 demo 同样的写法），保留 OS4 Glass 覆盖式模糊标题栏 |
| `04e9b818` | 新增 `GlassScrollableTabRow` | 主页标签 >4 个的玻璃档从普通 `TabRow` 换成它 |
| `8d3d0bb4` / `f633ad2c` / `e5338080` | `GlassTopAppBar` 渐进模糊带重写、`BandOverhang 32→10dp`、`ProgressiveBlurRadius 80→32dp`、去接缝 | Glacier 不传这些默认值，自动跟随；`bottomContent` 已按官方结构使用 |
| `588d029f` | `ScrollBar` 新增 `interactive`（弹层内非交互指示器） | 页面滚动条需要交互，不用 |
| `6d784f19` | AGP 9.5.3 | Glacier `libs.versions.toml` 同步 9.5.3（composite build 两端必须一致） |

工具链：Gradle 9.9.1 / Kotlin 2.4.21 / Compose BOM 2026.11.01（androidx.compose 1.13.0）/ AGP 9.5.3 / compileSdk 37.2。

## 二、自造轮子复查结论

再次全量 grep（`Canvas/drawBehind/Brush/shadow/graphicsLayer/tween/spring/animate*AsState/AnimatedContent/Toast/fontSize/sp/dp/clickable/indication`）并逐文件对照官方库与 example：

**没有发现**自建玻璃 / 模糊 / 阴影管线、自建按压层、自建选中态、自建弹层 / 导航 / 滚动条 / FAB / 偏好行。R4–R20 的收敛结论成立。

本轮真正替换的：

1. **P0：6 个 `GlassBottomSheet` 补 `progressiveBlur = true`**
   基线升级把默认值改成 `false`（标题栏在内容上方、`content` 拿到 0.dp）。Glacier 的 6 个弹层（应用页排序/筛选、主页标签分组/标签管理/延时冻结、API 操作单）都按「覆盖式模糊标题栏」写内容让位，若不显式开启，OS4 Glass 观感会退化为实心标题栏。官方 demo 同样显式传 `true`（`GlassBottomSheetDemo.kt:78`）。
2. **主页标签 >4 个：`TabRow` → 官方 `GlassScrollableTabRow`**
   `GlassSegmentedTabRow` 要求 2–4 格、有界宽度；更多标签官方新增横向滚动版本（每格最小 76dp）。非玻璃档仍用 `TabRow`。
3. **非玻璃档顶栏动作图标 28dp → 官方 24dp**
   官方 `BackNavigationIcon.kt:37` / `MainPage.kt:299` 的玻璃/非玻璃两档分别是 28/24dp；Glacier 之前两档共用 28dp。
4. **弹层标题垂直内边距 `8.dp` → 官方 `GlassPopupDefaults.ContentPaddingVertical`**
5. **标签命名对话框输入框到按钮 `16.dp` → 官方 12dp**（`GlassPage.kt` 对话框写法）
6. **关于页标题淡入：连续线性 0.35 → 官方「进度 ≥0.45 离散阈值 + 200ms tween」**（`AboutPage.kt:134-139`）

保留但补说明（不是自造轮子）：

- 雪花图标：官方 icon 库确实没有 Snow/Freeze/Ice 相关图标（`FreezePanes` 是表格冻结窗格、`QuietMode` 是勿扰），品牌图标自绘有据；
- `Toast`：Worker / Receiver / 快捷方式无 Composition，且页面浮层会盖住 Snackbar，沿用系统反馈件；
- `ui/PageInsets.kt`、`ui/TopLayer.kt`、`ui/effect/`：官方 example 逐字节移植（Apache-2.0），库不导出；
- `rememberSheetBottomPadding`：官方把底部安全区交给调用方，这是调用方职责实现；
- 二级页 `NavTransitions.MiuixDefault`：官方 example 的 `InstantNavTransition` 是私有实现，库不导出。

## 三、冻结后点应用不再延迟启动

根因：`AppActionRunner.launch()` 对已冻结应用先 `repository.setFrozen(pkg,false)`，而 `GlacierRepository.setFrozen` 成功后同步 `refresh()`；`refresh()` 会枚举全部已安装应用（约 500ms），`startActivity` 排在其后，于是点击到启动被全量刷新串行阻塞。

修法：

- `setFrozen(..., refresh: Boolean = true)`：默认行为不变；启动路径传 `refresh = false`；
- 先 `startActivity`，之后 `refreshInBackground()`（独立 `SupervisorJob + Dispatchers.IO`，仍走 `refreshLock` 串行）；
- Island 的 `awaitLaunchIntent` 仍在启动前保留；解冻失败提示不变。

验证：`adb logcat -s GlacierLaunch GlacierRepository:*` 可见 `startActivity requested=true` 早于 `background refresh finished`，两者不再相加。

## 四、应用页去掉「·不在主页」

行尾官方 `Checkbox` 已经用选中/未选中表达主页归属。`appRowSummary` 删除 `notOnHome` 片段，`AppListCardRow` 去掉 `inHome` 参数；包名与置顶/白名单徽标保留。`badge_not_on_home` 字符串中英一并删除。

## 五、关于页去重、去小标题

- 删除三个 `SmallTitle`（应用 / 系统 / 开源信息）；
- 品牌区已显示应用名 + 版本号 → 信息卡不再重复版本行；
- 包名 + 构建类型合成一行技术信息；
- 开源许可证卡只保留可点击的 `ArrowPreference`（SPDX 行尾），删除重复的 license label / notice 两行；
- 卡片间隔仍用 `PageMetrics.SectionGap`，动态背景 / 品牌区淡出 / GlassCard 不变。

## 六、预测性返回

现象定位：系统预测性返回本身正常。旧实例持久化里 `predictive_back_animation=false` 时，运行时会把进程内 `ApplicationInfo.enableOnBackInvokedCallback` 设为 false（logcat：`mIsAnimationCallback=false`），表现就是「页面不动、只有系统返回箭头」；把它打开后慢滑返回立即恢复跟手（页面右移、下层压暗、松手提交），与 MIUIX 官方 example 同几何。

但旧方案有两个结构性风险，本轮照 InstallerX-Revived 重做：

1. **不再运行时改 manifest 级标志**：`MainActivity` 删掉 `HiddenApiBypass` + 反射 `setEnableOnBackInvokedCallback`。manifest 常开 `enableOnBackInvokedCallback="true"`，系统始终派发返回进度。
2. **开关只切转场**：新增 `ui/NoPredictiveBackTransition.kt`（逐字节移植自 InstallerX-Revived，GPL-3.0）：`push/pop` 用 `NavTransitions.MiuixDefault`，`predictivePop` 用忽略手指进度的版本。关闭时页面不跟手位移，松手仍播 pop；开启时用官方 `MiuixDefault` 1:1 跟手。
3. **不再重建 Activity**：设置页只写偏好 + `bumpRevision()`，`GlacierShell` 订阅后立即换转场。R20 那套 `RecreatedInsetsGate` 因此一并删除（没有 recreate，也就没有「重建首帧」问题）。

这样即使 OEM ROM 屏蔽了隐藏 API，预测性返回也不再受开关实现影响；开关切换也不会闪帧。

## 七、验证

- `:app:compileDebugKotlin`、`:app:assembleDebug`、`:app:assembleRelease` 通过；`:app:testDebugUnitTest` 通过；
- 模拟器 Android 16（API 36，1080×2400）覆盖安装 `Glacier-release.apk`（v1.7.0 / 12）：
  主页、应用页、关于页、预测性返回、冻结弹层均正常；
- 关于页：无小标题、无重复版本/许可证行（`docs/verification/miuix-os4-r21/03-about-clean.png`）；
- 应用页：副标题不再出现「· 不在主页」（`02-apps-no-badge.png`）；
- 预测性返回：开 About 页跟手右移、下层压暗；关页面不动、松手仍播 pop；切换不需要重建 Activity
  （`04-predictive-back-on.png`、`05-predictive-back-off.png`）；
- 启动延迟日志：
  `startActivity ... in 324ms` 早于 `background refresh finished in 349ms`，两者不再相加。

完整验收步骤与截图见 `docs/verification/miuix-os4-r21/README.md`。

真机仍需确认：玻璃光学 / 模糊观感、OEM 手势导航下的预测性返回观感、各冻结后端。
