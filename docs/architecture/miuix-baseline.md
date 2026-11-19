# MIUIX 版本基线（Glacier）

本文件记录 Glacier 构建时**实际使用**的 MIUIX 源码状态。每次重建或升级依赖都必须重新核对并更新本文件。

## 取用方式

composite build（源码引入），路径由 `settings.gradle.kts` 决定：

- 默认：`../miuix`（相对 Glacier 工程根目录）
- 可用环境变量 `MIUIX_DIR` 覆盖

不写死版本号，也不引用 Maven 坐标：MIUIX 自述为 experimental，主干默认值仍在快速变化，
源码引入能保证"读到的默认值就是运行时用的默认值"。

## 本次基线

| 项 | 值 |
|---|---|
| MIUIX commit | `58b0a3dd06f8ee83c1099a7ebee1183adce3f83c` |
| commit 时间 | 2026-11-19 17:16:26 +0800 |
| commit 标题 | `library, example: animate glass tab row material transitions` |
| LIBRARY_VERSION | `0.9.5`（`build-logic/src/main/kotlin/BuildConfig.kt`） |
| group | `top.yukonga.miuix.kmp` |
| Kotlin | `2.4.21` |
| AGP | `9.5.1` |
| Compose Multiplatform | `1.12.2` |
| compileSdk 要求 | `37` + `minorApiLevel = 2` |
| buildTools | `37.0.0` |
| JDK | `21` |
| 本地补丁 | 无（HEAD 的 `BuildConfig.kt` 已带 `workingDir = rootDir`，composite build 不再 `exit 128`） |
| 核对日期 | 2026-11-19 |

## 引用的模块

```
miuix-ui           基础组件 / MiuixTheme / Scaffold / TopAppBar / TabRow / Card
miuix-preference   设置行：SwitchPreference / ArrowPreference / SliderPreference
miuix-icons        扩展图标族
miuix-material     GlassDefaults / GlassStyle / isRuntimeShaderSupported
miuix-blur         Backdrop / rememberLayerBackdrop / layerBackdrop
miuix-glass        全部官方 Glass 组件
miuix-glass-icons  MiuixIcons.Glass.*（512 个，Regular 为默认字重）
miuix-nav          NavDisplay / NavBackStack / 预测性返回
```

`miuix-glass` 已 `api` 传递 `blur` / `material` / `ui`；显式声明只是为了可读性与版本可追溯。

## 与知识库基线的差异

知识库（`gif-toolkit/docs/miuix-os4-migration-kit`）的证据基线是
`3177a684` / `0.9.5`。本次使用 `58b0a3d`，已逐项核对以下会"悄悄改变观感"的点：

| 检查项 | 结果 |
|---|---|
| `GlassTopAppBar` 签名 | 未变（`progressiveBlur` / `bandBrush` / `contentModifier` 仍在） |
| `GlassNavigationBarDefaults.Height` | `54.dp` |
| `GlassNavigationRailDefaults.ExpandedWidth` / `Margin` | `250.dp` / `12.dp` |
| `GlassFloatingActionButtonDefaults.Size` | `56.dp` |
| `GlassTabRowDefaults.Height` / `TabGap` | `40.dp` / `12.dp` |
| `renderInRootScaffold` 默认值 | `GlassDialog` / `GlassBottomSheet` / `GlassDropdownPreference` 仍为 `true` |
| `isRuntimeShaderSupported()` | 仍为 `SDK_INT >= 33` |
| 可用的 Glass 组件 | 无删除；新增 `GlassFloatingToolbar`、`GlassSecondaryPopup`、`GlassTabRow` 材质过渡 |

## 升级流程

1. `git -C ../miuix pull`，记录新的 `HEAD` 与 `BuildConfig.LIBRARY_VERSION`。
2. 逐项过上面这张表；任何默认值变化都要在本文档追加一行"本次核对"。
3. `./gradlew :app:assembleDebug`，装到模拟器走一遍 `docs/verification/acceptance.md` 的回归清单。
4. 只有通过后才更新本文件的"本次基线"表。
