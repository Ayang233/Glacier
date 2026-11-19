# Glacier

独立的 Android 应用冻结工具。用 MIUIX OS4 与官方 Glass 表面构建界面，
把不常用的应用"冻"起来，让手机的其他部分保持安静。

- Application ID / Namespace / Kotlin 包名：`com.ayang.glacier`
- minSdk 33（`miuix-blur` 的 RuntimeShader 门槛）
- targetSdk 36
- UI：Jetpack Compose + MIUIX OS4（无 Material3）
- 设计体系：MIUIX OS4 官方主题 + 官方 Glass

## 构建

```bash
# 需要 JDK 21 与 Android SDK（compileSdk 37.2 / build-tools 37.0.0）
export JAVA_HOME=/path/to/jdk-21
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

产物：

```
app/build/outputs/apk/debug/Glacier-debug.apk
app/build/outputs/apk/release/Glacier-release.apk
```

MIUIX 以 composite build 引入（默认 `../miuix`，可用 `MIUIX_DIR` 覆盖）。
当前基线记录在 [docs/architecture/miuix-baseline.md](docs/architecture/miuix-baseline.md)。

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
Scaffold(Transparent, insets=0)                 ← 弹层宿主（最外层）
└ Box(fillMaxSize)
   ├ Box(fillMaxSize).layerBackdrop(root)       ★录制层 R（满屏，录在分页容器外）
   │  └ NavDisplay → 一级页 HorizontalPager / 二级页
   ├ GlassNavigationBar(backdrop = root)        ← R 的兄弟（窄屏）
   └ GlassNavigationRail(backdrop = root)       ← R 的兄弟（宽屏）
```

页面内部：

```
Scaffold(topBar = GlassTopAppBar(pageBackdrop))
└ content
   └ Box(fillMaxSize).layerBackdrop(pageBackdrop)   ★录制层 P（先 drawRect(surface)）
      └ 列表 / 网格（padding 走 contentPadding，内容才能滚到顶栏下面）
```

三条红线（见 `docs/architecture/glass-contract.md`）：消费者不是录制层的后代、
采样源在消费者整个绘制区域都有像素、尺寸坐标只在量测/布局/绘制期读取。

## 许可证

Glacier 以 **GPL-3.0-or-later** 发布，包含源自 [Hail](https://github.com/aistra0528/Hail)
（作者 aistra0528，同样 GPL-3.0-or-later）的衍生代码。完整许可证见 `LICENSE`，
第三方组件与来源说明见 `NOTICE.md`，应用内也有"开源许可证"页面。

Hail 的商标与品牌不用于 Glacier 的正常界面。

## 发布

- 仓库：https://github.com/Ayang233/Glacier
- 版本：https://github.com/Ayang233/Glacier/releases

Release APK 使用独立密钥签名（`keystore/` 与 `signing.properties` 不入库）。
在缺少这两个文件的机器上，`assembleRelease` 会退回 debug 签名，仅用于本机构建。

### 本机推送方式的说明

这台机器的网络只放行 `api.github.com`，`github.com` 被代理拦截（`CONNECT tunnel failed, response 502`），
所以 `git push` 不可用。仓库内容通过 Git Data API 推送：

```bash
python tools/publish.py . Ayang233/Glacier /path/to/gh
```

脚本是增量的：按 tree SHA 判断远端已有哪些提交，只推剩下的。需要已登录的 `gh`。
因此**远端提交的 SHA 与本地不同**（内容一致）。若以后网络恢复，首次同步请用
`git fetch origin && git reset --hard origin/main`，之后即可正常 `git push`。
