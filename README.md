# Glacier

独立的 Android 应用冻结工具：把不常用的应用冻起来，减少后台运行。界面基于 MIUIX OS4 与 Glass 构建。

- 包名：`com.ayang.glacier`
- 系统要求：Android 13（API 33）及以上
- UI：Jetpack Compose + MIUIX OS4（不使用 Material3）

## 下载与安装

- 从 [Releases](https://github.com/Ayang233/Glacier/releases) 下载 `Glacier-release.apk`
- `adb install Glacier-release.apk`，或直接在设备上打开 APK
- 与 1.0.0 使用同一签名密钥，可直接覆盖升级

## 使用前

Glacier 不申请系统级冻结权限，冻结依赖设备侧的工作模式。刚装好时是 `default` 模式，
此时不会真的冻结任何应用。请到「设置 → 通用 → 工作模式」选择本机支持的方式并完成授权：

- Shizuku（强停 / 禁用 / 隐藏 / 挂起）
- Root（强停 / 禁用 / 隐藏 / 挂起）
- Dhizuku 或设备所有者（DeviceOwner）
- Island（冻结 / 挂起）
- 特权应用

自动冻结还需要通知使用权与使用情况访问权，用来跳过正在通知或处于前台的应用。

## 构建

```bash
# 需要 JDK 21 与 Android SDK（compileSdk 37.2 / build-tools 37.0.0）
export JAVA_HOME=/path/to/jdk-21
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
./gradlew :app:testDebugUnitTest      # 纯 JVM 单元测试（搜索键/匹配）
```

minSdk 33（`miuix-blur` 的 RuntimeShader 门槛）、targetSdk 36。

产物：

```
app/build/outputs/apk/debug/Glacier-debug.apk
app/build/outputs/apk/release/Glacier-release.apk
```

MIUIX 以 composite build 引入（默认 `../miuix`，可用 `MIUIX_DIR` 覆盖）。
首次构建前请按 `settings.gradle.kts` 的提示准备 MIUIX 源码，并按
[MIUIX_BASELINE.md](MIUIX_BASELINE.md) 的基线切到对应 commit：

```bash
tools/miuix-baseline.sh pin 44f1d31580bd127360d79bdb0bb869bd6c5af025   # 切到基线 commit 并打本地补丁
tools/miuix-baseline.sh verify              # 校验 commit 与补丁
tools/miuix-baseline.sh status              # 看官方 main 领先了多少
```

## 架构

```
app/src/main/kotlin/com/ayang/glacier/
├── GlacierApplication.kt     进程入口：装配数据层/系统层，恢复主题与自动冻结
├── app/                      对外 API 契约（Action / Scheme）
├── domain/                   纯模型：ManagedApp、Tag、WorkingMode、AppEntry
├── data/                     偏好、JSON 存储、搜索索引、Repository
├── platform/                 Android 能力封装：包状态、图标、通知、调度、快捷方式
├── system/                   冻结后端：Shizuku / Root / Dhizuku / DeviceOwner / Island / 特权
├── feature/                  home / apps / settings / about / api
└── ui/                       主题、Glass 环境、Shell、通用组件
```

UI 不直接调用系统服务：页面 → `GlacierRepository` → `FreezeController` → 具体后端。

## Glass 结构

```
Scaffold(Transparent, insets=0)                    ← 弹层宿主（最外层）
├ bottomBar（窄屏）→ GlassNavigationBar(root)       ← R 的兄弟；24dp 横向内边距
└ Box(fillMaxSize)
   ├ Box(fillMaxSize).layerBackdrop(root)          ★录制层 R（满屏，录在分页容器外）
   │  └ NavDisplay → 一级页 HorizontalPager / 二级页
   └ GlassNavigationRail(backdrop = root)          ← R 的兄弟（宽屏）
```

页面内部（`PageFrame`，对照 MIUIX 官方 `GlassPage`）：

```
Box
├ Scaffold(containerColor = surface, insets = 0)
│  ├ topBar   = GlassTopAppBar(pageBackdrop)
│  ├ bottomBar= Spacer(LocalShellBottomBarHeight)   ← 透明占位：正文/FAB 按真实底栏高度让位
│  └ body     = Box(fillMaxSize).layerBackdrop(pageBackdrop)   ★录制层 P
│                └ 列表 / 网格（padding 走 contentPadding，内容才能滚到顶栏下面）
└ overlay()                                          ← 搜索/弹层，画在 Scaffold 之上
```

三条红线（见 `docs/architecture/glass-contract.md`）：消费者不是录制层的后代、
采样源在消费者整个绘制区域都有像素、尺寸坐标只在量测/布局/绘制期读取。

## 设计对齐

界面按 MIUIX OS4 官方源码与 `example/shared` 逐项对齐（底栏 24dp 横向内边距、
页面 12dp 留白、官方脚手架/滚动/弹层动画、About 页结构与尺寸、squircle 应用图标）。
页面让位走官方 `AppPageInsets` 契约（`ui/PageInsets.kt`，官方 `utils/PageUtils.kt` 的逐字节移植）：
`NavDisplay` 永远满宽，侧栏宽度与系统安全区只作用于内容，背景和顶栏模糊带不会被裁断。
审计与验收记录：

- **基线与升级流程**：[MIUIX_BASELINE.md](MIUIX_BASELINE.md)（当前 `44f1d315`，`v0.9.5-beta03` 之后 18 个 main 提交）
- [R6 自造轮子清查与基线升级](docs/audit/miuix-os4-r6-audit.md) · [R5 审计](docs/audit/miuix-os4-r5-audit.md) · [R5 验收](docs/verification/miuix-os4-r5/README.md)
- [历史审计](docs/audit/miuix-os4-audit.md) · [Glass 架构](docs/audit/miuix-os4-glass-audit.md)
- [修改清单](docs/plan/r3-change-list.md) · [构建与运行验收 R3](docs/verification/miuix-os4-r3/README.md)
- [MIUIX 官方契约摘录](docs/reference/miuix-os4-contract.md) · [参考项目与许可证](docs/reference/reference-projects.md)

## 许可证

Glacier 以 **GPL-3.0-or-later** 发布，包含源自 [Hail](https://github.com/aistra0528/Hail)
（作者 aistra0528，同样 GPL-3.0-or-later）的衍生代码。完整许可证见 `LICENSE`，
第三方组件与来源说明见 `NOTICE.md`，应用内也有"开源许可证"页面。

## 发布

- 仓库：https://github.com/Ayang233/Glacier
- Release：https://github.com/Ayang233/Glacier/releases

Release APK 使用独立密钥签名（`keystore/` 与 `signing.properties` 不入库）。
在缺少这两个文件的机器上，`assembleRelease` 会退回 debug 签名，仅用于本机构建。
