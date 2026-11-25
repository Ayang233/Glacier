# R19 验收：冻结状态跟首帧一起到位

模拟器 1080×2400、420dpi、Android 16（API 36）、宿主 GPU。测试用真机路径：
把 `top.yukonga.miuix.uitest.debug`（主页第二个应用）用 `pm disable-user` 置为冻结态，
默认工作模式（`FreezeAction.None`）读的就是 disabled / hidden / suspended 并集。

## 截图

| 文件 | 内容 |
|---|---|
| `01-cold-start-frozen-first-frame.png` | **最终构建**冷启动首帧：主页直接显示两项应用，第二项 MiuiX 已经是冻结样式（灰度图标 + 次要色名称） |
| `02-apps-page-frozen-row.png` | 应用页回归：冻结行仍显示「已冻结」，全量刷新复用 `ApplicationInfo` 的路径正常 |
| `03-before-slow-refresh-unfrozen.png` | **旧代码 + 放大竞态**：首帧后 ~0.9s，应用已显示但 MiuiX 仍是原色图标 + 正常色名称（用户报的现象） |
| `04-before-slow-refresh-after.png` | 旧代码同一次启动、全量刷新完成后：才转成冻结样式 |
| `05-after-slow-refresh-frozen.png` | **新代码 + 同样放大竞态**、同一时机：首帧起就是冻结样式 |
| `06-after-slow-refresh-after.png` | 新代码稳定后（一致，没有二次变化） |
| `07-reference-frozen.png` | 冻结样式基准（灰度图标 + 灰名称） |
| `08-reference-unfrozen.png` | 未冻结样式基准（原色图标 + 深色名称） |

`03` ~ `06` 的「放大竞态」= 临时给 `refreshSnapshot()` 加 2.5s sleep，仅调试构建用、**未提交**。
模拟器上 splash ≈1.3s、全量刷新只要 ~0.5s，刷新早于首帧结束，不放大两个版本看不出差别。

## 复现步骤（最终构建）

```bash
adb shell pm disable-user --user 0 top.yukonga.miuix.uitest.debug   # 造一个冻结应用
adb shell am force-stop com.ayang.glacier
adb shell "am start -n com.ayang.glacier/.ui.MainActivity >/dev/null; sleep 2.6; screencap -p /sdcard/frozen.png"
```

旧代码在这个时机截到的可能还是未冻结样式，取决于刷新窗口是否大于 splash。
把 `refreshSnapshot()` 换成耗时更长的真机环境时，先后差会明显到一眼可见。

## 测量（日志，临时插桩已移除）

| 版本 | 首帧快照 | 主页刷新 | 全量刷新（252 个包） |
|---|---|---|---|
| 旧（1.6.3） | `primeSnapshot home=2 frozen=0` | 无（随全量一起） | 593ms |
| 新（R19） | `primeSnapshot home=2 frozen=1` | `publishHome … 1~2ms` | 480ms（−19%） |
