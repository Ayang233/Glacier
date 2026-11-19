# 验收记录

环境：`android16` AVD，Android 16 / API 36 / 1080×2400 / 420dpi / x86_64，软件渲染。
所有截图在 `docs/verification/stage1/`。截图均为该阶段真实构建 + 真实安装运行所得，未使用旧图。

> 证据分级遵循知识库约定：
> **L1 = 源码接线**（可读源码判定）／**L2 = 运行时能力**（进程活着、无异常、组件真的挂载）／
> **L3 = 像素观感**（只能真机硬件 GPU 判定）。本机是软件渲染，**L3 不下结论**。

## 1. 构建

| 项 | 结果 | 证据 |
|---|---|---|
| `:app:assembleDebug` | 通过 | `app/build/outputs/apk/debug/Glacier-debug.apk`（49.1 MB） |
| `:app:assembleRelease` | 通过（R8 + resource shrink） | `app/build/outputs/apk/release/Glacier-release.apk`（4.19 MB） |
| Release 安装启动 | 通过，导航/关于/许可证可进入 | `39-release-home.png`、`40-release-apps.png`、`41-release-about.png` |
| 崩溃/序列化异常 | `logcat` 无 `FATAL`、无 `SerializationException` | 见各阶段命令输出 |

## 2. 页面与交互

| 场景 | 结果 | 证据 |
|---|---|---|
| 首启空态 | 正常，大字标题 + 空态文案 + 悬浮导航胶囊 | `01-home-empty.png` |
| 应用列表 | 7 个应用，图标/名称/包名/冻结标记正确 | `03-apps-icons.png` |
| 设置页 | OS4 Preference 卡片分组，下拉/开关/滑块均渲染 | `04-settings.png` |
| 顶栏折叠 | 滚动后大标题收为居中标题 + 副标题，内容从下方穿过 | `05-settings-scrolled.png` |
| 应用操作单 | 玻璃底部面板，11 个动作，破坏性操作为红色 | `06-app-sheet.png` |
| 添加到主页 | 生效，主页计数 1 | `07-apps-after-add.png`、`08-home-with-app.png` |
| FAB | 位于导航胶囊之上，未被遮挡 | `09-home-fab.png` |
| 标签条 | 默认/工具/社交，切换后网格与副标题同步过滤 | `17-home-tabs-tabrow.png`、`18-tag-filter.png` |
| 标签编辑 | 面板列出标签 + 编辑按钮 + 新建 | `37-tag-editor.png` |
| 新建标签对话框 | 输入框 + 保存/取消，贴底对齐（OS4 手机尺寸规范） | `38-tag-dialog.png` |
| 多选模式 | 顶栏切换为批量动作，选中项描边 | `19-search.png`（实为多选态） |
| Glass 搜索浮层 | 玻璃搜索条 + 结果列表，排版不重叠 | `22-search-clean.png` |
| 深色模式 | 全局转深色，导航胶囊/卡片/文字对比正常 | `23-dark-home.png` |
| 横屏 | 折叠侧栏在顶部居中，内容不被挤压 | `24-landscape.png` |
| 侧栏展开 | 左侧面板 + 内容整体右移，让位正确 | `33-rail-inset4.png` |
| 关于页 | 品牌区、版本、系统信息、许可证入口 | `34-about-scrolled.png` |
| 许可证页 | 7 个组件 + 完整 GPL 文本 | `35-licenses.png` |
| 外部 API | `ACTION_FREEZE` 进入独立窗口并给出玻璃错误弹窗 | `36-api-dialog.png` |

## 3. 稳定性

| 项 | 结果 |
|---|---|
| 18 次一级页切换 | 进程存活，`logcat` 中 `FATAL` / `prepareTreeImpl` 命中数 **0** |
| 横竖屏切换 | 无崩溃；侧栏/底栏按断点切换 |
| 深色/浅色切换 | 无崩溃 |
| 进程内多次进入二级页 | 无崩溃（About → Licenses → 返回） |

## 4. Glass 证据分级

| 级别 | 状态 | 说明 |
|---|---|---|
| L1 源码接线 | 已确认 | 消费者/录制层/采样源关系见 `docs/architecture/glass-contract.md`；崩溃修复记录见同文件"两个已知陷阱" |
| L2 运行时能力 | 已确认 | 组件真实挂载并绘制：顶栏、导航胶囊、侧栏、FAB、底部面板、对话框、搜索浮层 |
| L3 像素观感 | **未验证** | 本机为 `swiftshader`/软件渲染，AGSL 与真实模糊不可信。按知识库要求，**不声称完成 L3** |

## 5. 未验证项（如实列出）

1. **真实冻结**：模拟器上没有任何可用的冻结后端。
   - 未安装 Shizuku / 无 root / 未设为 DeviceOwner / 未装 Island。
   - `Default` 模式下 `canFreeze() == false`，点击冻结会如实提示"权限不足"（`09-home-fab.png` 后的 toast）。
   - 因此 Shizuku / Root / Dhizuku / DeviceOwner / Island / 特权 六条后端的**写操作**均为
     L1（源码接线）级别，没有 L2 运行证据。这是本次最大的未验证面。
2. **自动冻结链路**：Worker 与前台服务只在"有可冻结应用且开启开关"时启动；
   模拟器上没有可用后端，`setFrozenBatch` 返回 `Denied` → Worker 走 `Result.retry()`。
   实际熄屏 → 延迟 → 冻结的端到端行为未验证。
3. **通知监听条件**：未授予通知使用权，"跳过有通知的应用"未实测。
4. **使用情况访问**：未授权，"跳过前台应用"未实测。
5. **快捷方式 / QS 磁贴**：代码与清单已就位，未在桌面上实际创建/点击验证。
6. **生物识别门禁**：模拟器无锁屏凭据，`canAuthenticate` 返回非 SUCCESS，
   代码按"无可用凭据则放行"处理，未实测成功路径。
7. **窄屏大字体（1.6x 以上）**：未验证。
8. **真机 Glass 光学观感（折射、模糊、边缘光）**：未验证，且不在本机能力范围内。

## 6. 与目标等级的差距

目标是 L6。按实际证据：

- 结构、主题、组件、导航、Glass 接线、业务模型：达到 L5 的"实现完整"。
- 冻结业务与系统能力的**运行**验证不足（缺后端），按知识库评级规则不能算 L6。
- 真机 L3 未验证。

结论：**实现层面完成，验收层面为 L4/L5，L6 需要一台有 Shizuku 或 root 的真机补验。**
