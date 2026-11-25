# R11 验收：基线 `v0.9.5-beta04` + 自造轮子清查 + 冻结交互

- 设备：`emulator-5554`（1080×2400 @420dpi，Android 16 / API 36，KernelSU 提供 root）
- 冻结模式：设置里选「Root · 禁用」（`su_disable`），命令落 `pm disable --user 0 <pkg>`
- 构建：`./gradlew :app:assembleDebug` → `Glacier-debug.apk`
- 基线：`tools/miuix-baseline.sh verify` → `3c560868758f80cd97bbdab2f8e53612ad1c56be`

## 截图与结论

| 文件 | 场景 | 观察点 |
|---|---|---|
| `01-home-unfrozen.png` | 6 个应用全部未冻结 | 标题 `Glacier` + 副标题「默认 · 6 个应用」；右下角**冻结** FAB；顶栏 搜索 / 多选 / 标签编辑 三个动作；图标全彩 |
| `02-home-selection.png` | 进多选，选中「日历」「YouTube」 | 大标题**仍然是 Glacier**（不再换成「已选 2 个」、也没有字号突变）；副标题变「已选 2 个 · 默认」；两枚官方 `Checkbox` 角标；FAB 仍在（= 冻结选中的那些） |
| `03-home-selection-frozen.png` | 点 FAB 冻结所选两个 | 只有「日历」「YouTube」转灰度、标签转次要色；多选态自动退出；其余 4 个仍彩色，FAB 仍在（还有可冻结目标） |
| `04-home-all-frozen.png` | 再点 FAB 冻结整页 | 6 个图标全灰度；副标题「默认 · 6 个应用 · **已全部冻结**」；**FAB 与多选入口都还在原位**（不出现"解冻"按钮，也不消失） |
| `05-home-after-launch.png` | 点开已冻结的「时钟」→ 返回 | 时钟被自动解冻并成功启动（`enabled=1`）；返回后时钟图标恢复彩色、其余保持灰度；副标题回到「默认 · 6 个应用」 |
| `06-home-app-menu.png` | 长按「日历」 | 官方 `GlassBottomSheet`（beta04 的 `SheetMotion`），10 行使用向菜单；第 2 行是语境行「解冻」 |
| `07-search-empty.png` | 打开搜索、未输入 | 正文为空：不再把搜索条 placeholder（"搜索应用"）在正文复读一遍 |
| `08-home-all-frozen-tap.png` | 全冻状态下点 FAB | 按钮留在原位，点击给出「已全部冻结」的明确反馈——不做静默无反应，也不自造灰化禁用态 |
| `09-home-selection-all-frozen.png` | 全冻状态下进多选、选 2 个、点 FAB | 提示改用「所选应用已全部冻结」（范围不同不能共用一句"全部"）；勾选与 FAB 都保留 |
| `10-home-all-frozen-multiselect.png` | 全冻状态下进入多选 | 多选入口常驻，顶栏三颗按钮位置不变；副标题切到「已选 N 个 · 默认」 |

## 机器可读校验

```
$ tools/miuix-baseline.sh verify
基线校验通过：3c560868758f80cd97bbdab2f8e53612ad1c56be

$ ./gradlew :app:testDebugUnitTest
BUILD SUCCESSFUL

$ ./gradlew :app:assembleDebug
BUILD SUCCESSFUL
```

## 未覆盖

- 玻璃档视觉：本模拟器无 AGSL/RuntimeShader 支持，`LocalGlassActive = false`，
  截图全部是纯色档。玻璃档的弹层动效由 beta04 的 `SheetMotion` / `DialogMotion` 驱动，
  属库内行为，需真机补验。
- Shizuku / Dhizuku / DeviceOwner / Island 后端仍未在真机验证（模拟器只有 root 链路可用）。
