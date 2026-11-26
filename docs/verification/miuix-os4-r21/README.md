# R21 验收：最新基线 + 自造轮子复查 + 四个问题

模拟器：Android 16（API 36），1080×2400，手势导航，宿主 GPU。安装包：`Glacier-release.apk`（v1.7.0 / versionCode 12）。

## 截图

| 文件 | 内容 |
|---|---|
| `01-home.png` | 主页：底栏位置按官方 `9c256923` 收紧（不再多 20dp），正文/FAB 留白同步 |
| `02-apps-no-badge.png` | 应用页：行尾勾选框表达主页归属，副标题不再出现「· 不在主页」 |
| `03-about-clean.png` | 关于页：无小标题；版本只在品牌区出现一次；包名+构建类型一行；许可证只保留可点击入口 |
| `04-predictive-back-on.png` | 预测性返回开：慢滑返回中间帧，About 页跟手右移、下层 Settings 压暗 |
| `05-predictive-back-off.png` | 预测性返回关（debug 包）：同一手势位置页面不动，只有系统返回箭头；松手仍播 pop 转场 |
| `06-freeze-toast.png` | 主页 FAB 冻结后：Toast「已冻结 1 个应用」，图标转灰 |

## 关键日志（启动延迟修复）

```bash
adb logcat -c
adb shell input tap <已冻结应用图标>
adb logcat -d -s GlacierLaunch GlacierRepository:*
```

实测：

```
18:45:04.322 I GlacierLaunch: unfrozen com.ayang.giftoolkit in 308ms (snapshot refresh deferred)
18:45:04.337 I ActivityTaskManager: START ... com.ayang.giftoolkit/.MainActivity
18:45:04.338 I GlacierLaunch: startActivity com.ayang.giftoolkit requested=true in 324ms
18:45:04.688 I GlacierRepository: background refresh finished in 349ms
```

`startActivity` 发生在全量刷新结束之前 350ms，启动不再被刷新串行阻塞。

## 建议人工验收

1. **主页**：底栏离内容更近（官方 R21 值）；FAB 仍常驻右下。
2. **应用页**：点一行切主页归属，行尾勾选框变化；副标题只有包名（+置顶/白名单徽标），没有「不在主页」。
3. **关于页**：从上到下 = 品牌区 / 包名+构建 / 工作模式+设备 / 开源许可证 / 一句话说明；没有任何分组小标题，版本号只出现一次。
4. **预测性返回**：设置 → 主题设置 → 预测性返回动画。开：慢滑返回时页面跟手右移；关：页面不动、松手才播 pop。切换开关不再重建 Activity、不再闪一帧。
5. **冻结启动**：冻结一个应用后点图标，应立即拉起；列表变灰稍后由后台刷新补上。
6. **弹层**：应用页排序/筛选、主页标签分组/标签管理/延时冻结，标题栏应为覆盖式渐进模糊（R21 基线默认改为 false，Glacier 显式开启）。
7. **标签页**：主页标签 ≤4 个为分段轨道，>4 个为官方横向滚动 `GlassScrollableTabRow`。

真机仍需确认：玻璃光学观感、OEM 手势导航下的预测性返回、各冻结后端。
