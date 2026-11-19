# 第二轮验收记录：MIUIX OS4 对齐 + Glass 复核

- 日期：2026-11-19
- 代码状态：`Hail/Glacier` 工作区（本轮改动全部在实际源码上，见 `docs/audit/miuix-os4-audit.md`）
- MIUIX 基线：`58b0a3d` / `0.9.5`（`../miuix`，composite build，本轮未升级）
- 环境：Android 16 模拟器（API 36，1080×2400，density 2.625），SwiftShader 渲染，系统语言 zh-CN

---

## 一、构建结果

| 任务 | 结果 | 耗时 | 产物 |
|---|---|---|---|
| `./gradlew :app:assembleDebug` | BUILD SUCCESSFUL | 37s（增量） | `app/build/outputs/apk/debug/Glacier-debug.apk`（48 MB） |
| `./gradlew :app:assembleRelease` | BUILD SUCCESSFUL（含 `lintVitalRelease`、R8、资源压缩） | 1m 55s | `app/build/outputs/apk/release/Glacier-release.apk`（4.1 MB） |

校验和：

```
b5214f096b7afe87064583278024125bd5a1c8981a03f758c82dd90dd4cbea8f *Glacier-release.apk
0695f0d4becdc000fd5c270d4ef2ebc80801c7b93c1eb37d4480f7f2fe31fbe2 *Glacier-debug.apk
```

结论：

- Debug / Release 均成功；R8 未报缺失类（`lintVitalRelease` 通过）。
- 没有新增依赖冲突：唯一新依赖 `miuix-squircle` 与已用的 MIUIX 模块同 group、同版本、同许可证。
- Release 包 4.1 MB，比上一轮的体积没有明显变化（squircle 模块只带一份 SDF 资源）。

## 二、运行验证

`adb install` 后冷启动、切页、开弹层、旋转，全程观察 `logcat`：**无 `FATAL EXCEPTION`、无 ANR、无 `prepareTreeImpl` 递归**。

### 2.1 截图索引

截图全部来自本轮重新构建并运行的版本：21、22 为 Release 包，其余为 Debug 包。数据为本地测试数据（4 个主页应用、2 个标签）。

| 文件 | 内容 | 验证点 |
|---|---|---|
| `01-home-light.png` | 主页 · 浅色 | 圆角矩形图标、玻璃标签页签、网格间距 |
| `02-home-dark.png` | 主页 · 深色 | 深色下标签胶囊、图标轮廓、FAB |
| `03-search-open.png` | 搜索展开 | 顶栏按官方转场淡出，搜索条与取消按钮 |
| `04-search-query.png` | 搜索输入 `mi` | 结果列表；条内清空 × 与条外取消 × 同时存在且职责不同 |
| `05-search-closed.png` | 点击取消后 | 回到正常主页：无弹层、无残留输入、**未弹出标签编辑** |
| `06-tag-editor.png` | 标签编辑弹层（新建标签后） | 官方行组件、16dp 行内边距；同一弹层里「新建标签」可继续操作 |
| `07-apps-dark.png` | 应用页 · 深色 | 卡片行高、图标 40dp、副标题层级 |
| `08-apps-light.png` | 应用页 · 浅色 | 同上 |
| `09-apps-search.png` | 应用页搜索 | 与主页同一套搜索浮层，无顶栏穿透 |
| `10-action-sheet.png` | 应用操作单 | 统一行高、24dp 图标、危险项红色 |
| `11-selection-mode.png` | 多选态 | 选中环为 squircle 描边、顶栏 5 个动作 |
| `12-settings-dark.png` | 设置 · 深色 | Preference 行、玻璃下拉 |
| `13-settings-light.png` | 设置 · 浅色 | 同上 |
| `14-settings-bottom.png` | 设置底部 | 系统权限三项中文化、关于入口 |
| `15-about-dark.png` | 关于页 | 白底黑主体品牌图标、`SmallTitle` + 分组卡 |
| `16-about-scrolled.png` | 关于页下半 | 只读行无箭头、可跳转行有箭头、长说明换行 |
| `17-licenses.png` | 许可证页 | 组件卡 16dp 内边距、GPL 正文 |
| `18-landscape-rail.png` | 横屏 | 左侧常驻展开的侧栏、内容让位（含刘海 inset）、卡片不被压住 |
| `19-rotate-back-portrait.png` | 旋转往返后回到竖屏 | 布局恢复正常，无残留让位 |
| `20-rotation-bug-before.png` | 旋转 BUG 现场（修复前） | 侧栏折叠到顶部、内容被裁切、两页各露一半 |
| `21-release-home.png` | Release 包主页 | R8 包冷启动正常，无白屏/崩溃 |
| `22-release-apps.png` | Release 包应用列表 | 图标、行高、卡片与 Debug 包一致；Glacier 自身的图标为白底黑雪花 |
| `23-api-activity.png` | 外部 API 窗口 | `SHOW_APP_INFO` 打开半透明独立窗口并弹出操作单 |

### 2.2 功能验收

| 项 | 步骤 | 结果 |
|---|---|---|
| 搜索按钮 | 主页顶栏 → 搜索 | 展开，键盘弹出，顶栏淡出 |
| **搜索关闭按钮（本轮修复项）** | 搜索态点条外 × | 关闭搜索、退出搜索态、回到主页；**没有打开标签编辑** |
| 搜索清空 | 输入 `mi` 后点条内 × | 只清空输入，不关闭浮层，焦点留在输入框 |
| 搜索结果 | 输入 `mi` / `sh` / `ka` | 命中 Mishka、Shizuku 等，点击可启动 |
| 反复开关 | 连续 3 次开→关 | 每次都能正常关闭，未出现弹层残留 |
| 标签切换 | 主页点「工具」「社交」 | 页签切换、网格按标签过滤 |
| 标签编辑 | 顶栏第 3 个动作 | 弹层正常打开；与搜索 × 无耦合 |
| 应用图标 | 主页 / 应用页 / 搜索结果 | 全部为圆角矩形（官方 squircle），保留原始配色 |
| 多选模式 | 长按图标 | 进入多选、标题变「已选 N 个」、选中环可见 |
| 应用操作单 | 多选态 → ⋮ | 10 项操作、行高一致、危险项红色 |
| FAB | 主页右下 | 显示/隐藏逻辑随选择态与空列表变化 |
| 页面导航 | 三页切换 / 进入关于 / 返回 | `NavDisplay` + pager 正常，返回栈正确 |
| 弹层开关 | 排序、筛选、标签、延时冻结 | 都能打开并关闭；`backdrop` 传递正常 |
| About 跳转 | 设置 → 关于 → 许可证 → 返回 | 正常 |
| 外部 API | `adb shell am start -a android.intent.action.SHOW_APP_INFO -e android.intent.extra.PACKAGE_NAME top.yukonga.mishka` | 打开独立窗口并弹出操作单（沿用上一轮实现，本轮仅行样式更新） |
| 横竖屏 | 竖→横→竖 | 旋转往返后布局正确（见 18 / 19 / 20） |
| 深浅色 | 系统深色 / 浅色切换 | 两套主题都正常，品牌图标保持白底黑主体 |

### 2.3 本轮修掉的两个运行期缺陷

1. **搜索 × 打开标签编辑**：根因是浮层在 `Scaffold` body 槽内、被淡出的顶栏抢走点击。已修复并逐像素验证关闭后的界面与进入前一致。
2. **旋转后布局错乱**（本轮实测发现）：侧栏状态被 `rememberSaveable` 带到新方向、窄屏仍按展开状态让位、分页器保留旧页宽偏移。修复后竖↔横往返正常。
3. **横屏内容被侧栏压住一列字**（本轮实测发现）：`GlassNavigationRail` 自带 `defaultWindowInsetsPadding`，横屏时刘海把面板整体右移，而让位量没算这段 inset。修复后让位量 = 系统起始 inset + `Margin + ExpandedWidth × progress`。

## 三、未验证项（如实记录）

| 项 | 原因 |
|---|---|
| Glass 的 L3 光学表现 | 模拟器 SwiftShader，折射/模糊/边缘光不可信 |
| 顶栏渐进模糊的真实观感 | 模拟器上 `progressiveBlur = false`（库的 band 渐变回退），只有真机能验 |
| 大字体（1.3x / 2.0x） | 未逐页截图；行高由官方 `heightIn(min = 56.dp)` 保证，但没有视觉证据 |
| 第三方图标包 | 未安装图标包实测；代码路径保留 |
| 窄屏（< 360dp 宽） | 未构造该配置 |
| 加载状态 | 仓库同步读取，当前没有加载窗口，未强行加假状态 |
| 真机上的自动冻结 / 六条后端 | 需要 Shizuku/Root 等环境，不在本轮 UI 验收范围 |

## 四、回归方式（可重复）

```bash
# 1. 构建
./gradlew :app:assembleDebug :app:assembleRelease

# 2. 安装（Debug 与 Release 的包名不同，可共存）
adb install -r app/build/outputs/apk/debug/Glacier-debug.apk
adb install -r app/build/outputs/apk/release/Glacier-release.apk

# 3. 搜索关闭回归（坐标按 1080x2400 竖屏）
adb shell am start -n com.ayang.glacier.debug/com.ayang.glacier.ui.MainActivity
adb shell input tap 753 196     # 搜索
adb shell input text "mi"
adb shell input tap 975 196     # 取消
adb exec-out screencap -p > after.png   # 应与进入搜索前一致，且没有标签编辑弹层

# 4. 旋转回归
adb shell settings put system user_rotation 1   # → 横屏
adb shell settings put system user_rotation 0   # → 竖屏
adb exec-out screencap -p > rotate.png
```
