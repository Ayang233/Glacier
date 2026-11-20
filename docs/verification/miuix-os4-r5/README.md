# MIUIX 0.9.5-beta02 基线升级验收（R5）

- 时间：2026-11-20
- 基线：MIUIX `66248bac`（`v0.9.5-beta02`，`LIBRARY_VERSION = 0.9.5`）
- 上一基线：`58b0a3dd`
- 应用：Glacier 1.3.0（versionCode 4）
- 环境：`android16` AVD / Android 16（API 36）/ 1080×2400 / 420dpi / x86_64，**宿主 GPU 渲染**（`GLES: Android Emulator OpenGL ES Translator (AMD Radeon RX 9070 XT), GLES 3.1`）
- 证据分级：L1 源码接线 / L2 运行时能力 / L3 像素光学

> 与历史验收的差别：本次模拟器不再关闭渐进模糊。上一轮 `Platform.isEmulator` 分支已删除，
> 因此本轮截图里的顶栏模糊带、底栏/侧栏采样都是**真实 AGSL/模糊路径**产出的像素。

## 1. 构建与测试

| 项 | 结果 |
|---|---|
| `:app:assembleDebug` | 通过（50.65 MB） |
| `:app:assembleRelease`（R8 + resource shrink + release 签名） | 通过（4,369,372 B / 4.17 MB，SHA-256 `bee840fd…75eb`） |
| 覆盖升级（1.2.0 release → 1.3.0 release，同签名） | 通过，`versionCode=4 / versionName=1.3.0`，数据保留 |
| `:app:testDebugUnitTest` | 通过，5 个用例（`TextIndexTest`） |
| 编译告警（Glacier 源码） | 1 条：`AppOpsManager.unsafeCheckOpNoThrow` 被 Android 36 标注废弃（平台 API，非 MIUIX；无等价"不抛异常"替代，保留） |
| MIUIX 废弃 API 告警 | 0（`TextField` 迁移后） |

## 2. 升级前后对照（同设备、同分辨率、同操作路径）

| 页面 | 升级前 | 升级后 | 判定 |
|---|---|---|---|
| 首页空态 | `before-01-home.png` | `after-01-home.png` | 一致：大标题、副标题、空态文案、底部胶囊位置像素级相同 |
| 应用列表 | `before-02-apps.png` | `after-02-apps.png` | 一致：行高、图标 squircle、右对齐摘要、滚动条不可见（内容不满一屏） |
| 设置页 | `before-03-settings.png` | `after-03-settings.png` | 一致：分组卡片、开关、下拉、底栏玻璃采样（蓝色开关的洇色一致） |
| 设置页滚动态 | `before-04-settings-scrolled.png` | `after-04-settings-scrolled.png` | **有意变化**：升级前顶栏无模糊带（内容原样透出文字残影），升级后出现官方渐进模糊带（蓝色开关被模糊洇开）。这是恢复官方 `progressiveBlur` 的结果，不是回归 |
| 关于页 | `before-05-about.png` | `after-05-about.png` | 一致：品牌区 3 层结构、版本号、卡片排版 |
| 搜索浮层 | `before-06-search.png` | `after-06-search.png` | 一致：搜索条、取消按钮、结果行、键盘让位 |

> 逐像素差异的判定口径：A 官方新版带来的合理变化 / B 默认参数变化 / C 迁移回归 / D 未清理的历史实现 / E 与官方默认冲突。
> 本轮只出现 **A（设置页滚动态渐进展宽）**，无 B/C/D/E。

## 3. 新增/变更能力的实拍

| 场景 | 截图 | 结论（L2） |
|---|---|---|
| `GlassTextField`（玻璃对话框内） | `new-01-tag-dialog.png` | 官方控件挂载；未聚焦时底色=surface 上的 5.9% 覆盖，占位文字可读 |
| `GlassTextField` 聚焦 | `new-02-tag-dialog-typed.png` | 聚焦描边 `primary`、浮动标签上移、`TextFieldState` 双向绑定生效（保存按钮由 disabled→enabled） |
| `TextFieldState` 写回 | `new-03-tag-created.png` | 新建标签落库，标签条即时出现新页签 |
| 宽屏（1066dp，侧栏折叠为顶部胶囊） | `wide-01-home.png` | 胶囊居中、一级页标题按官方语义淡出、内容居中 |
| 宽屏设置 | `wide-02-settings.png` | 侧栏不退化为底栏；正文让位正确 |
| 宽屏二级页 + 标题避让 | `wide-03-about-scrolled.png` | 二级页标题**保持可见**（不再整条淡出），位置在返回键之后、未被胶囊遮挡；`centerObstructionStart` 已接线 |
| 深色模式 | `dark-01-settings.png` | 卡片/开关/胶囊对比正常，无亮块或透明穿透 |

## 4. 交互回归（手工走查）

| 场景 | 结果 |
|---|---|
| 一级页 3 页切换（HorizontalPager） | 正常，无半页残留 |
| 进入/退出二级页（About → Licenses） | 正常，转场圆角裁剪正常 |
| 搜索开/关 | 正常，关闭后无残留遮罩与焦点 |
| 标签编辑面板 / 新建标签 / 删除标签 | 正常 |
| 玻璃开关关闭 | 正常：底栏、顶栏、弹层回退为不透明，标签对话框走普通 `TextField` |
| 主题切换（浅色/深色） | 正常 |
| 横竖屏切换（1080×2400 ↔ 1600×1000@240dpi） | 正常，无崩溃 |

## 5. 性能与日志

设置页连续滚动 8 次（`dumpsys gfxinfo` 重置后统计）：

```
Total frames rendered: 190
Janky frames: 9 (4.74%)
50th percentile: 17ms
90th percentile: 27ms
95th percentile: 34ms
Number Missed Vsync: 0
```

| 项 | 结果 |
|---|---|
| 崩溃 | `logcat -b crash` 空；全量日志无 `FATAL` |
| 自采样递归（`prepareTreeImpl`） | 0 命中 |
| 序列化异常 | 无 |
| 首启 `FileNotFoundException` | 预期内（`apps.json`/`tags.json` 尚未创建，记 W） |
| GPU 层 | 无自建 pass；顶栏 idle 时库会跳过按钮 backdrop 录制（0.9.5 优化） |

## 6. 未验证（不得声称通过）

1. **真机光学（L3）**：模拟器是宿主 GPU，能产出真实模糊像素，但折射、边缘光、功耗必须真机判定。
2. **宽屏 + 刘海 cutout**：本轮把顶栏改为 End-only inset（源码级修正），模拟器无刘海，未能复现双算场景。
3. **冻结六条后端**：模拟器无 Shizuku / Root / DeviceOwner / Island，写操作仍只有 L1。
4. **自动冻结端到端**：同上。
5. **通知使用权 / 使用情况访问**：未授权，跳过逻辑未实测。
6. **大字体 2.0x / 横屏操作面板**：R4 遗留项，本轮未触碰相关代码，未复测。
7. **官方 main 的 3 个后续提交**：未纳入基线，未验证其修复效果。

## 7. 复现步骤

```bash
# 基线源码
tools/miuix-baseline.sh pin v0.9.5-beta02
tools/miuix-baseline.sh verify

# 构建与安装
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/Glacier-debug.apk

# 截图（示例）
adb exec-out screencap -p > home.png

# 宽屏
adb shell wm size 1600x1000 && adb shell wm density 240
adb shell wm size reset && adb shell wm density reset

# 帧统计
adb shell dumpsys gfxinfo com.ayang.glacier.debug reset
# …滚动…
adb shell dumpsys gfxinfo com.ayang.glacier.debug
```
