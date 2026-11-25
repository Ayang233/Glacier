# R18 · 冷启动首帧直接显示已加入主页的应用

## 现象

冷启动后主页会先渲染一帧空状态（副标题「0 个应用」、正文「主页还没有应用。去「应用」页添加。」），
大约半秒后才闪回真实列表。用户看到的就是「已经加到主页的应用没有第一时间显示出来」。

## 原因

`GlacierRepository.snapshot` 从 `AppSnapshot()`（空）开始，唯一的填充路径是 `refresh()`，
而 `refresh()` 由 `MainActivity` 的 `LaunchedEffect(Unit)` 在**首帧组合之后**才发起：

1. 首帧组合：`snapshot.home` 还是空 → 主页渲染空状态；
2. 效果启动 → `refresh()` 进 IO 线程读 `apps.json`、枚举全部已安装应用、查冻结状态；
3. 刷新完成 → 快照更新 → 主页重新组合，列表才出现。

## 修法

两条一起做（`GlacierRepository.primeSnapshot()` + `GlacierApplication.onCreate`）：

1. **同步预热**：`GlacierApplication` 装配好 repository 后立即 `primeSnapshot()`——
   只读 `apps.json` / `tags.json`、按包名查几项 `PackageManager` 基本信息、组出**主页**条目，
   并写进 `snapshot`。首帧组合时主页已经有内容，不会再闪空状态。
   - 不查冻结状态（可能要 root / Shizuku 后端）、不枚举全部应用（173 个包会很慢）：
     这两件事留给随后的 `refresh()`；代价只是冻结图标可能晚一帧转灰。
   - 全部应用列表保持空，不拿半份数据冒充"全部应用"（应用页仍然等 `refresh()` 填）。
2. **启动即刷新**：`Application` 用 `CoroutineScope(SupervisorJob() + Dispatchers.IO)` 立即发起
   `repository.refresh()`，与 Activity 里的那次 `refresh()` 由 `refreshLock`（Mutex）串行化。
   这样"完整快照（全部应用 + 冻结态）"通常也在首帧后的很短时间内就绪。

`refreshSnapshot()` 的写入仍在 IO 线程上做，主线程只承担预热的那一次文件读 + 两条包查询，
`ActivityTaskManager` 记录的 `Displayed` 时间没有可见变化（模拟器实测 2.55s，与改动前同一量级）。

## 验证

- `:app:compileDebugKotlin`、`:app:testDebugUnitTest`、`:app:assembleRelease` 通过；
- 模拟器冷启动（force-stop 后重启）连续截帧：
  - 第一帧 UI（`01-cold-start-first-frame.png`）：主页直接有 GIF 助手 / MiuiX 两项，
    副标题「默认 · 2 个应用」，不再是空状态；图标还在按 Enter 动画淡入；
  - 稳定帧（`02-cold-start-stable.png`）：冻结态与图标都补齐。
- 仍待真机确认：冷启动耗时与图标加载速度（模拟器是宿主 GPU + JIT 冷启动）。
