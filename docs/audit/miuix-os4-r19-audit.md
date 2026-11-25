# R19 · 冻结状态跟首帧一起到位，不再「应用先出来、冻结后到」

## 现象

R18 之后，冷启动进主页应用已经能第一时间显示，但**冻结态**还要「稍等一会儿」才出现：
冻结应用先画成未冻结的样子（原色图标 + 正常色名称），过一阵才转成冻结样式
（灰度图标 + 次要色名称）。刚冻结/解冻一个主页应用后也有同样的延迟。

## 原因

两处叠加：

1. **首帧快照里冻结态是写死的 `false`**。R18 的 `primeSnapshot()` 为了不阻塞主线程刻意留空：

   ```kotlin
   // 冻结态要问后端（可能是 root / Shizuku），首帧不做；refresh 会修正。
   frozen = false,
   ```

   真实的冻结态只由 `refresh()` → `refreshSnapshot()` 补上。

2. **补冻结态的那次刷新很贵**，而且只产出「最终快照」一次。`refreshSnapshot()` 先
   `installedApplications()` 枚举全部包，再对每个包按包名重查
   `applicationInfoOrNull` / `loadLabel` / `packageInfoOrNull` / `isInstalled` / `isFrozen`。
   模拟器 252 个包实测 **593ms**；真机包更多、`PackageManager` 更慢，这个窗口是秒级。
   窗口内渲染的每一帧都还是「未冻结」的旧快照 —— 也就是用户看到的「冻结状态迟到」。

## 修法

四条，全部在数据层（`data/` / `platform/` / `system/`），不动 UI：

1. **`FreezeController.isFrozen(info: ApplicationInfo)`**（新增重载）。冻结态本来就落在
   `ApplicationInfo` 的 flags / privateFlags 上：所有后端（Shizuku / Root / Dhizuku /
   DeviceOwner / Island / Privileged）的 `isFrozen(packageName, action)` 都只是按当前动作读
   同一份对象的位。所以已经拿到 info 时可以直接算，不必再查一次 `PackageManager`。
   `PackageTools` 相应补了 `isDisabled/isHidden/isStopped/isSuspended` 的 info 重载，
   原包名版委托给 info 版，语义不变。
2. **`primeSnapshot()` 用 info 直接算冻结态**：首帧快照就带正确的冻结状态。
   而且**总查询次数反而变少**——原实现为了 `isInstalled` 又查了一次 `applicationInfoOrNull`，
   现在直接用这份 info 的 `FLAG_INSTALLED`。
3. **`refresh()` 拆两段**：先 `publishHome()`（只读主页那十几个包，实测 **1~2ms**）刷新主页
   的冻结态与标签，再 `refreshSnapshot()` 全量枚举补「全部应用」。于是任何一次刷新
   （冷启动、`ON_RESUME`、冻结/解冻之后）主页都是毫秒级先到位。
4. **全量刷新复用枚举到的 `ApplicationInfo`**（`entryFor(info)`）：`installedApplications()`
   已经返回 info，不再按包名重查。252 个包的全量刷新从 **593ms → 480ms（−19%）**，
   顺带让「全部应用」列表也来得更快。

`primeSnapshot()` / `publishHome()` 仍在主线程/IO 线程各做各的：主线程只承担主页那十几个包的
一次文件读 + 一条 `getApplicationInfo`（比原来还少一次），`Displayed` 与改动前同一量级
（模拟器 2.4~2.9s，逐次波动），没有观察到的退化。

## 验证

- `:app:compileDebugKotlin`、`:app:testDebugUnitTest`、`:app:assembleDebug` 通过。
- 临时插桩日志（已从提交代码中移除，仅用于测量）：
  - 旧：`primeSnapshot home=2 frozen=0` → `refresh done in 593ms`（frozen=1 才到位）
  - 新：`primeSnapshot home=2 frozen=1` / `publishHome … in 1~2ms` / `refresh done in 480ms`
- 视觉 A/B（把竞态放大后才看得出来，见下）：同一时机截图，
  - 旧码：应用已显示但仍是**未冻结**（彩色图标 + 深色名称），刷新完成后才转灰；
  - 新码：首帧即**已冻结**（灰度图标 + 次要色名称）。
  截图见 `docs/verification/miuix-os4-r19/`。
- 应用页回归：`installedApplications()` 的 info 直传路径正常，冻结行仍显示「已冻结」。

## 关于「为什么要放大竞态才算看得见」

模拟器上 splash ≈1.3s、全量刷新只要 480~593ms，刷新在首帧之前就结束了，两个版本视觉上看不出差别。
放大实验临时给 `refreshSnapshot()` 注入 2.5s sleep（**仅调试构建，未提交**），把「刷新窗口」
拉到大于 splash，就能复现用户描述的先后差；同条件下新码首帧即冻结。真机包更多、
`PackageManager` 更慢，窗口本来就会大于 splash，这正是用户看到的现象。

## 限制

- 冻结态语义没变：默认档仍取 disabled / hidden / suspended 的并集，其余档按 `workingMode.action`。
- 图标灰度位图本身是异步加载的（`rememberAppIcon`），冷启动第一帧可能还是占位方块；
  这是图标解码时序，与冻结数据无关。
