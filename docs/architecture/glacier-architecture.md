# Glacier 架构

## 1. 分层

```
app/src/main/kotlin/com/ayang/glacier/
├── GlacierApplication.kt     进程入口：装配数据层/系统层，恢复夜间模式与自动冻结服务
├── app/GlacierApi.kt         对外契约：Action、URI scheme、extra key
├── domain/                   纯模型，无 Android 依赖（除 ApplicationInfo 相关工具）
│   ├── Model.kt              ManagedApp / Tag / WorkingMode / Backend / FreezeAction / QuickAction
│   └── AppEntry.kt           渲染快照：AppEntry / SearchKeys / AppSnapshot
├── data/
│   ├── GlacierPreferences.kt 偏好（独立文件 glacier_preferences）+ 可观察的 glass/theme
│   ├── GlacierDataStore.kt   apps.json / tags.json 的读写（原子写、去重、标签回收）
│   ├── TextIndex.kt          ICU 转写 + 模糊匹配 + T9 的搜索键
│   └── GlacierRepository.kt  业务门面：把"用户意图"与"系统真实状态"拼成快照
├── platform/                 Android 能力封装，不含业务判断
│   ├── PackageTools.kt       包状态读取（含隐藏/挂起/禁用/幽灵包过滤）
│   ├── AppIcons.kt           图标加载（图标包 → AppIconLoader → 去色）
│   ├── Platform.kt           充电/熄屏/使用统计/模拟器探测
│   ├── Notifications.kt      通知渠道
│   ├── UiFeedback.kt         Toast / 剪贴板 / 系统 Intent
│   ├── AutoFreezeScheduler.kt WorkManager 调度（唯一任务名 glacier.*）
│   ├── auto/                 AutoFreezeService / Workers / UnsuspendReceiver
│   ├── tile/FreezeTileService.kt
│   ├── admin/GlacierDeviceAdminReceiver.kt
│   └── shortcut/ShortcutBridge.kt
├── system/                   冻结后端，唯一能碰特权的地方
│   ├── FreezeBackend.kt      接口：state / isFrozen / setFrozen / lockScreen / shell
│   ├── ShizukuBackend.kt     Binder 代理 + 隐藏方法反射
│   ├── RootBackend.kt        su 子进程
│   ├── DhizukuBackend.kt     DPM 代理（admin = null）
│   ├── DeviceOwnerBackend.kt DPM 正式接口
│   ├── IslandBackend.kt      有序广播 + 硬超时
│   ├── PrivilegedBackend.kt  特权应用 / 越狱环境
│   ├── NativeShell.kt        带超时的进程执行
│   └── FreezeController.kt   模式 → 后端 → 动作 的唯一分派点
├── feature/                  home / apps / settings / about / api
└── ui/                       主题、Glass 环境、Shell、通用组件、SessionLock
```

调用方向是单向的：

```
Composable → GlacierRepository → FreezeController → FreezeBackend → 系统
```

页面里没有任何一处直接调用 Shizuku / DPM / su，也没有一处写死颜色或尺寸常量。

## 2. 导航

- `miuix-nav` 的 `NavDisplay` 承载顶层路由（`Route.Main` / `About` / `Licenses`），
  自带预测性返回与转场；路由是 `@Serializable` 的 sealed interface，可跨进程重建。
- 一级页（主页/应用/设置）在 `Route.Main` 内用 `HorizontalPager` 承载，
  `beyondViewportPageCount = 0`：每页一个录制层，不预组合。
- 底栏/侧栏由 Shell 绘制，二级页压上来时通过 `visible` 收起，而不是卸载重挂。

## 3. 状态

| 状态 | 归属 | 可观察方式 |
|---|---|---|
| 应用快照（名称/图标/冻结态/标签） | `GlacierRepository.snapshot` | `mutableStateOf` |
| Glass 开关 | `GlacierPreferences.glassEnabled` | `mutableStateOf` + 落盘 |
| 主题模式 | `GlacierPreferences.themeMode` | `mutableStateOf` + `UiModeManager` |
| 其余设置 | `GlacierPreferences` 普通属性 | 写入后 `bumpRevision()`，页面读 `revision` |
| 当前标签、搜索词、多选 | 页面本地 | `rememberSaveable` / `remember` |

冻结状态永不落盘：它就是系统状态，每次刷新重新读。

## 4. 数据格式

`files/v1/apps.json`

```json
[{"package":"…","pinned":false,"whitelisted":false,"tags":[0,1]}]
```

`files/v1/tags.json`

```json
[{"tag":"Default","id":0},{"tag":"工具","id":1}]
```

键名与历史数据结构一致，便于将来做导入导出；目录和文件属于 Glacier 自己。
标签 0 恒为默认标签，删除标签时其下应用回落到 0，不产生孤儿归属。

## 5. 刻意不做的事

- 不做多模块拆分：当前业务规模下拆模块只会制造空壳。
- 不引入 `androidx.compose.material3`：Glacier 只有 MIUIX 一套设计体系。
- 不引入第三方拼音库：ICU `Han-Latin` 转写足够，且避免 GPL-2.0-only 依赖。
- 不自建 Glass 框架：全部使用官方 `miuix-glass` 组件与 `Modifier.glassPanel`。
