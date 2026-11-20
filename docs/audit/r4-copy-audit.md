# R4 文案专项审计（应用内文案 + GitHub 发布页）

- 审计人：copy（R4 整改团队文案专项）
- 日期：2026-11-19
- 范围：`app/src/main/res/values*/strings*.xml`（英文 + 简体中文）、`README.md`、GitHub 仓库 `Ayang233/Glacier` 的 Description / README / Releases
- 结论摘要：
  - 应用内中英文文案已实改 **51 条**（英文 22 条、中文 29 条；含同一句的中英对应改动，License 组件说明占中英各 3/5 条），未改任何资源 key、未改任何格式参数（`%1$s` / `%1$d`）、未改许可证原文。
  - GitHub 发布页只做了**只读**检查；**线上 Description / README / Release 均未修改**。本文件只给可直接粘贴的文案。
  - 额外发现 3 处代码级文案问题（在 `.kt` / `AndroidManifest.xml`，不在本任务写作范围），只在第 1.5 节报告给 lead。

---

## 一、应用内文案

### 1.1 改动文件

| 文件 | 改动数 | 说明 |
| --- | --- | --- |
| `app/src/main/res/values/strings.xml` | 19 | 英文：空状态、Settings 说明、错误提示、About、Licenses、外部 API |
| `app/src/main/res/values-zh-rCN/strings.xml` | 24 | 简体中文对应项 + 中文特有的生硬表达 |
| `app/src/main/res/values/strings_pages_r3.xml` | 3 | Licenses 组件说明（英文） |
| `app/src/main/res/values-zh-rCN/strings_pages_r3.xml` | 5 | Licenses 组件说明（中文） |
| `app/src/main/res/values/strings_home_r3.xml`、`strings_apps_r3.xml` 及对应 `values-zh-rCN` | 0 | 已逐条核查，原文无需修改 |
| `README.md` | 结构调整 | 见第二节，本地已改，**远端未改** |

### 1.2 改前 → 改后对照表

#### Home / Apps 空状态

| key | 改前（EN） | 改后（EN） | 理由 |
| --- | --- | --- | --- |
| `empty_home` | No apps here yet.\nAdd apps from the Apps page. | No apps on Home yet.\nAdd them on the Apps page. | “here”指代不明；第二句补主语，避免机翻感 |
| `empty_apps` | No apps match the current filter. | No apps match this filter. | “current”是冗余限定 |

| key | 改前（ZH） | 改后（ZH） | 理由 |
| --- | --- | --- | --- |
| `empty_home` | 这里还没有应用。\n去「应用」页添加。 | 主页还没有应用。\n去「应用」页添加。 | “这里”在空状态下没有指向，明确成“主页” |
| `empty_apps` | 当前筛选条件下没有应用。 | 没有符合当前筛选的应用。 | 更符合中文语序，信息不变 |

#### Settings：每个设置项 title + summary

| key | 改前（EN） | 改后（EN） | 理由 |
| --- | --- | --- | --- |
| `biometric_login_summary` | Ask for biometrics or device credential on launch | Require biometrics or screen lock on launch | “device credential”是 Android 术语直译，改成用户看得懂的 screen lock |
| `glass_effect_summary` | …Turn off for a solid fallback. | …Turn off for solid backgrounds. | “solid fallback”是实现术语 |
| `icon_pack_summary` | Load icons from an installed icon pack | Use icons from an installed icon pack | Load 是内部动作，Use 才是用户视角 |
| `grayscale_icon_summary` | Desaturate the icon of a frozen app | Turn off to keep the original icon colors | 原句只是把标题换个说法重述；改为“关闭后会怎样”，补信息 |
| `synthesize_adaptive_icons_summary` | Shrink legacy icons into the adaptive mask | Fit legacy icons into the adaptive mask | Shrink 不准确（是套遮罩，不是单纯缩小） |
| `auto_freeze_summary` | Freeze the home list when the screen locks | Freeze Home apps when the screen locks | “home list”不是用户概念 |
| `settings_notification_access_summary` | Auto freeze needs it to skip apps that are notifying | Lets auto freeze skip apps with notifications | 一句写清“授权后得到什么”，去掉“needs it”解释腔 |
| `settings_usage_access_summary` | Used to skip the foreground app | Lets auto freeze skip the foreground app | 原句缺主语，不知道谁在用这个权限 |
| `settings_device_owner_summary` | Copy the dpm set-device-owner command | Copy the device owner setup command | `dpm` 是 adb 子命令，放进设置摘要太生硬（命令本身仍会复制） |
| `working_mode_hint` | Pick a backend that this device supports. Every mode needs its own permission. | Pick a mode your device supports. Each mode has its own permission. | “backend”是开发者词；两句合并语气更顺（该 key 目前未被引用，一并修正） |

| key | 改前（ZH） | 改后（ZH） | 理由 |
| --- | --- | --- | --- |
| `biometric_login` | 启动校验 | 启动时验证 | “启动校验”名词化生硬 |
| `biometric_login_summary` | 打开应用时要求生物识别或设备凭据 | 打开应用时要求生物识别或锁屏密码 | “设备凭据”是 Android 术语直译，中文用户习惯“锁屏密码” |
| `glass_effect_summary` | …关闭后为实心回退 | …关闭后为纯色背景 | “实心回退”是内部术语 |
| `icon_pack_summary` | 从已安装的图标包读取图标 | 使用已安装图标包中的图标 | “读取”是代码视角 |
| `grayscale_icon_summary` | 已冻结应用的图标显示为黑白 | 关闭后保留图标原色 | 原句重述标题；改为补充关闭后的行为 |
| `synthesize_adaptive_icons_summary` | 把旧版图标缩入自适应遮罩 | 把旧图标套进自适应图标遮罩 | “缩入…遮罩”搭配不自然 |
| `auto_freeze_summary` | 锁屏后冻结主页列表 | 锁屏后冻结主页中的应用 | “主页列表”不是用户概念 |
| `tile_action` | 快捷设置磁贴动作 | 快捷设置磁贴操作 | “动作”是英文 action 直译，中文语境用“操作” |
| `dynamic_shortcut_action` | 桌面快捷方式动作 | 桌面快捷方式操作 | 同上 |
| `notification_auto_freeze_text` | 等待屏幕关闭 | 等待熄屏 | 同一屏通知文字，短句更自然 |
| `settings_notification_access_summary` | 自动冻结需要它来跳过正在通知的应用 | 让自动冻结跳过有通知的应用 | 去掉“需要它”解释腔，一句说清用途 |
| `settings_usage_access_summary` | 用于跳过前台应用 | 让自动冻结跳过前台应用 | 原句缺主语 |
| `settings_device_owner_summary` | 复制 dpm set-device-owner 命令 | 复制设备所有者设置命令 | 面向用户，不暴露 adb 子命令名 |
| `working_mode_hint` | 请选择当前设备支持的后端。每个模式都需要各自的权限。 | 请选择设备支持的模式，每种模式需要单独授权。 | “后端”是开发者词；去掉“当前”“各自”等冗余 |

#### 冻结成功 / 失败 / 错误提示、Dialog

| key | 改前（EN） | 改后（EN） | 理由 |
| --- | --- | --- | --- |
| `app_not_installed` | App is not installed | This app is not installed | 指向具体应用，toast 里更自然 |
| `activity_not_found` | No launchable activity | This app can\'t be launched | “launchable activity”是 Android 开发术语，用户看不懂 |
| `package_not_checked` | The app is not on the home list | This app is not on Home | 同上，统一 Home 概念 |
| `api_missing_package` | Package must not be null | Package name is required | “must not be null”是程序断言语气 |

| key | 改前（ZH） | 改后（ZH） | 理由 |
| --- | --- | --- | --- |
| `app_not_installed` | 应用未安装 | 该应用未安装 | 与其它提示保持同一主语 |
| `activity_not_found` | 没有可启动的 Activity | 该应用无法启动 | 中文界面不混排 “Activity”，且说明结果而非内部原因 |
| `package_not_checked` | 该应用不在主页列表中 | 该应用不在主页 | “主页列表”冗余 |
| `api_missing_package` | 包名不能为空 | 缺少包名 | 更短，语义一致 |

#### About / Licenses 入口与说明

| key | 改前（EN） | 改后（EN） | 理由 |
| --- | --- | --- | --- |
| `app_description`（系统「应用信息」显示） | Freeze and unfreeze apps with OS4 glass. | Freeze apps you rarely use. | 原句宣传界面技术、未说清用途；系统描述只需说明应用干什么 |
| `about_description` | Glacier freezes apps you rarely need, so the rest of the phone stays quiet. Built on MIUIX OS4 with official glass surfaces. | Freeze apps you rarely use so they stop running in the background. | 空洞宣传（“手机其余部分保持安静”“官方玻璃表面”）+ 功能说明不清；改为真实效果 |
| `licenses_body` | …Full license texts are reproduced from their distributions. | …License texts are reproduced from their distributions. | “Full”无实义 |
| `licenses_note_miuix` | OS4 theme, components and official glass surfaces. | OS4 theme, components and glass surfaces. | “official”是自我背书，无信息量 |
| `licenses_note_androidx` | UI runtime and lifecycle. | Compose UI runtime and lifecycle. | 补具体组件，避免低信息密度 |
| `licenses_note_appiconloader` | Adaptive icon rendering. | Adaptive icon loading and rendering. | 与实际能力一致 |

| key | 改前（ZH） | 改后（ZH） | 理由 |
| --- | --- | --- | --- |
| `app_description` | 用 OS4 玻璃界面冻结与解冻应用。 | 冻结不常用的应用。 | 同英文：系统描述应是用途，不是界面技术 |
| `about_description` | Glacier 冻结你很少使用的应用，让手机的其他部分保持安静。基于 MIUIX OS4 与官方玻璃表面构建。 | 冻结很少使用的应用，减少后台运行。 | 两句都是直译腔/空洞宣传；改成一句真实效果 |
| `licenses_summary` | GPL-3.0-or-later · 随包组件 | GPL-3.0-or-later · 内置组件 | “随包”是打包术语 |
| `licenses_body` | Glacier 打包或链接了以下开源项目，完整许可证文本按其发行版本收录。 | Glacier 使用了以下开源项目，许可证文本取自各项目的发布版本。 | “打包或链接”“按其发行版本收录”是工程直译 |
| `licenses_note_miuix` | OS4 主题、组件与官方玻璃表面。 | OS4 主题、组件与玻璃表面。 | 去掉无信息量的“官方” |
| `licenses_note_androidx` | UI 运行时与生命周期。 | Compose UI 运行时与生命周期。 | 补具体组件 |
| `licenses_note_shizuku` | 无需 root 的特权 binder 访问。 | 无需 root 的特权 Binder 访问。 | 专有名词大小写修正 |
| `licenses_note_dhizuku` | 设备所有者委托授权。 | 委托的设备所有者授权。 | 语序不自然 |
| `licenses_note_appiconloader` | 自适应图标渲染。 | 自适应图标的加载与渲染。 | 与英文一致，信息更准确 |
| `licenses_note_hail` | aistra0528 的原始项目，Glacier 的冻结后端与数据格式由其衍生。 | Glacier 的冻结后端与数据格式源自该项目。 | 前半句与组件名重复；“由其衍生”生硬 |

#### 外部 API 提示

| key | 改前 | 改后 | 理由 |
| --- | --- | --- | --- |
| `api_missing_package` | Package must not be null / 包名不能为空 | Package name is required / 缺少包名 | 断言式语气 → 用户可读的报错 |

### 1.3 核查后保留原文的项（说明理由）

- **许可证原文**：`assets/LICENSE.txt` 及 `licenses_gpl_heading` 等只渲染原文，未改一字。
- **法律声明**：`about_license_notice`、`NOTICE.md` 未改（GPL 归属与衍生说明属于法务文本，不做“润色”）。
- **专有名词/标识符**：`mode_*`（Shizuku / Root / Dhizuku / DeviceOwner / Island / Privileged）、`license_spdx`、组件名等，不改名、不翻译。
- **功能名**：产品名 `Glacier`、`工作模式`、`自动冻结` 等沿用现有命名，避免与代码、文档语义脱节。
- **短标签**：`frozen`/`unfrozen`、`pinned`/`whitelisted` 徽标、排序/筛选标签本身已是最短表达，保留。
- **Dialog / BottomSheet**：逐条看过，标题与按钮全部复用 `action_*` / `cancel` / `save` / `delete` / `ok` 等最短动作词，没有长说明句，未发现 AI 腔，保留。
- **冻结成功/失败提示**：`msg_freeze` / `msg_unfreeze` / `msg_freeze_many` / `msg_unfreeze_many` / `permission_denied` / `operation_failed` 已逐条核查；均为「动作 + 对象」的短句，语义准确，保留。
- **空状态**：`empty_search`（No results / 没有匹配结果）已足够简洁，保留。

### 1.4 未删除的无引用 string key（仅记录，未动）

grep 全仓库 `app/src`（含 Manifest）后确认以下 key 当前无引用。按“优先只改 value、不删 key”的约束**没有删除**，删除需 lead 决定（删错会导致 `R.string` 编译失败或其他人正在使用）：

`about_source`、`action_add_pin_shortcut`、`api_freeze`、`api_launch`、`api_unfreeze`、`app_subtitle`、`cannot_freeze_self`、`deselect_all`、`licenses_summary`、`select_all`、`switch_off`、`switch_on`、`tile_unfrozen`、`uninstall_message`、`working_mode_hint`

> 其中 `working_mode_hint` 若一直无引用，建议后续由 Settings 页面决定是否接入或删除；本轮只改文案。

### 1.5 代码级文案问题（不在本任务写作范围，报告给 lead）

1. **`app/src/main/AndroidManifest.xml`**：`GlacierDeviceAdminReceiver` 的 `android:description="@string/action_lock"`，设备管理员授权页会显示「锁屏 / Lock screen」作为这个 receiver 的描述。语义不对，应该是专门的字符串（如“用于锁屏并冻结应用”）。建议由 manifest/权限负责人新增字符串后替换。
2. **`feature/settings/SettingsScreen.kt:371`**：复制设备所有者命令后 `UiFeedback.toast(context, R.string.ok)`，会弹出「确定 / OK」toast，没有信息量。建议新增“命令已复制 / Command copied”。
3. **`feature/home/HomeScreen.kt:412`**：延时冻结设置成功后 toast 用的是 `R.string.action_deferred_freeze`（「稍后冻结… / Freeze after…」），这是菜单项文案而不是确认文案。建议改成“将在 %1$d 分钟后冻结”或类似确认文案。

### 1.6 校验方式与结果（未运行 gradle，符合约束）

- 用 `git diff` 确认只改了 4 个 strings 文件与 README；`git status` 未触碰 `.kt`、未触碰 `docs/audit/miuix-os4-r4-audit.md`、`docs/plan/r4-change-list.md`。
- 用 PowerShell `[xml]` 解析改前（`git show HEAD:<path>`）与改后文件：**key 新增 0、删除 0**；每个被改 key 的 `%n$x` 占位符数量与顺序完全一致（`placeholderMismatch` 为空）。
- 中英文 key 对齐：`values/strings.xml` 与 `values-zh-rCN/strings.xml` 均为 **183** 个 key，无单边 key。
- 未运行 gradle/构建/安装/adb（按任务约束由 lead 统一构建验证）。

---

## 二、GitHub 发布页审计（只读）

### 2.1 读取方式与事实

- 本机 `github.com` 网页 502，仅 `api.github.com` 可用；全部通过本地 `tools/gh/bin/gh.exe`（2.105.0）的 `gh api` 只读命令读取。
- 认证账号 `Ayang233`，仓库权限返回 `{"admin":true,"maintain":true,"push":true,"pull":true}`——**有写权限，但本轮未使用**。
- Repository Description 当前值：
  `Android app freezer built on MIUIX OS4 with official glass surfaces. GPL-3.0-or-later.`
- Releases：`v1.1.0`（latest，id `451502814`）、`v1.0.0`（id `451291857`）；两者都有 APK + `SHA256SUMS.txt` 附件。
- 校验：远端 `v1.1.0` APK 的 `digest = sha256:0dd912ad…c1c3`，与本地 `app/build/outputs/apk/release/Glacier-release.apk` 的 SHA-256、仓库 `SHA256SUMS.txt` 三者一致；附件大小 4,252,852 字节与本地一致。
- 远端 README 与本机 `README.md`（改动前）**完全一致**（git blob SHA 均为 `dd8249c0ca64aef7832ecc9058090183f71420e6`），说明当前 GitHub 上是本仓库快照，文案问题两边同源。
- 远端 Release 正文与本地 `docs/release-notes/1.1.0.md`、`1.0.0.md`（规范化换行后）**逐字一致**——改发布页正文应同时改这两个本地文件，避免下次发布再次带回旧文。

### 2.2 现状问题清单

#### A. Repository Description

| 问题 | 说明 |
| --- | --- |
| 技术栈先行、用途模糊 | “built on MIUIX OS4 with official glass surfaces”是界面实现，看描述的人想知道“这是什么、靠什么冻结”。 |
| “official glass surfaces”是空洞自我背书 | 没有信息量，且容易被理解为营销。 |
| 缺少关键能力词 | 没提 Shizuku / Root / 设备所有者 / Island，失去可搜索性；也没提中文，和全中文 README、中文 Release 不一致。 |
| 缺 GPL 之外的下载/使用信息 | Description 只需一句，但应包含“Android 应用冻结工具”这个核心定位。 |

#### B. README

| 位置 | 问题 |
| --- | --- |
| 开头 | “让手机的其他部分保持安静”是空洞宣传；没有告诉用户**冻结需要 Shizuku/Root 等设备侧权限**，也没给下载/安装入口，新用户从 Releases 下载后大概率直接“点了没反应”。 |
| 结构 | 只有构建/架构/Glass，没有“下载与安装”“使用前准备”这两个面向使用者的段落。 |
| 「发布」小节 | 写入了本机网络限制、`tools/publish.py`、远端/本地 SHA 不同需 `git reset --hard origin/main`——这是**本机构建环境的内部说明**，不应出现在公开 README（`docs/publishing.md` 已有记录），会误导 clone 仓库的人，也相当于公开内部工作流。 |
| 「设计对齐」 | 与开头、Glass 结构、Release 反复出现“官方 Glass/逐项对齐”，信息重复。 |
| 「Glass 结构」 | ASCII 图与实现细节对贡献者有用，保留；但开头重复“官方”可收敛。 |
| 许可证末尾 | “Hail 的商标与品牌不用于 Glacier 的正常界面。”语义突兀，NOTICE 已覆盖来源与商标归属，README 可删。 |
| 构建 | 已提示 MIUIX composite build，但没说明首次构建前要 clone MIUIX 源码（`settings.gradle.kts` 报错里才有），可补一句指引。 |

#### C. 当前 Release `v1.1.0`（id 451502814）

| 问题 | 说明 |
| --- | --- |
| 标题重复 | 正文第一行 `# Glacier 1.1.0` 与 Release 标题重复（GitHub 已显示标题）。 |
| 内部迭代叙事 | “第二轮界面整改”“1.0.0 最明显的问题”是内部项目口径，公开发布页读起来像工程周报。 |
| 实现细节过载 | 24dp/363dp/68~104dp、`pageContentPadding`、`PagerNavigationSpringSpec`、88dp/74dp/35sp、72dp 安全区 1.5× 等，用户不需要；AI/工程报告味最重的地方。 |
| 机械化小标题 | “这次修正了什么”“验收”是模板结构；`✅` emoji 与“37 张截图”“审计报告第 17.5 节”属于内部验收记录。 |
| 术语未落地 | L2/L3、`ro.hardware.egl = emulation`、`Scaffold.bottomBar` 对普通用户不可读；诚实说明限制是对的，但要翻译成用户能懂的话。 |
| 口径主观 | 「1.0.0 最明显的问题」这类自评式判断没有客观依据；「冻结后端、数据格式、外部 API 与自动化逻辑与 1.0.0 一致」是准确信息，应保留。 |
| 缺 SHA-256 | 1.0.0 正文内嵌了 APK SHA-256，1.1.0 正文没有（只有附件 `SHA256SUMS.txt`），不一致，用户校验不方便。 |

#### D. 历史 Release `v1.0.0`

- 同样存在“界面完全建立在 MIUIX OS4 与官方 Glass 之上”的空洞宣传和“这次包含什么”模板标题；但作为历史版本页，建议**不改**（改历史发布页收益低、且会改动已发布记录）。保持现状即可。

#### E. 仓库其它

- Topics 为空；Homepage 未设置。非文案硬伤，但补 topics 能提升可发现性（见 2.3.5）。

### 2.3 可直接粘贴版文案

> 以下内容均为**建议稿**。线上页面当前一个字符都没有修改；复制粘贴/推送需要用户明确授权。

#### 2.3.1 Repository Description（推荐中文，一行）

```text
Android 应用冻结工具：用 Shizuku、Root、设备所有者或 Island 冻结不常用的应用，界面基于 MIUIX OS4。GPL-3.0-or-later。
```

备选（英文）：

```text
Android app freezer. Freeze rarely used apps via Shizuku, root, device owner or Island. UI built with MIUIX OS4. GPL-3.0-or-later.
```

#### 2.3.2 Release `v1.1.0` 标题与正文

标题保持：`Glacier 1.1.0`（无需 emoji、无需“第二轮”）。

正文（可直接粘贴，已按当前 v1.1.0 附件校验 SHA-256）：

```markdown
1.1.0 只改界面与交互。冻结后端、数据格式、外部 API 与自动化逻辑与 1.0.0 一致，可直接覆盖升级。

## 主要变化

- 底部导航：玻璃胶囊改为 MIUIX 官方的 24dp 横向内边距；页面内容与 FAB 按实际底栏高度让位，修复底部多留白。
- 间距：页面留白、卡片间距、列表末端统一为 12dp。
- 宽屏：≥1200dp 常驻展开玻璃侧栏，840–1200dp 折叠为顶部胶囊；二级页不再继承一级页的侧栏高亮。
- 搜索：搜索浮层与关闭逻辑重做，输入框内的 × 只清空内容，右侧 × 只关闭搜索；搜索结果行改用官方卡片与标准行。
- 弹层：Dialog、BottomSheet、下拉菜单改为常驻组合，退出动画不再被截断。
- About 页：按 MIUIX 官方 About 示例重写版式。
- 图标：自适应图标按 72dp 安全区渲染后套 MIUIX squircle；Home 与 Apps 共用同一图标组件。

## 安装

- 要求 Android 13（API 33）及以上
- `adb install Glacier-release.apk`，或直接在设备上打开 APK
- 包名 `com.ayang.glacier`；与 1.0.0 使用同一签名，可直接覆盖升级
- SHA-256：`0dd912adaf2407eba2daa1220ee678b2c12c2dffbfb226b510e7113c4cdbc1c3`

## 使用前

默认 `default` 模式不会真的冻结任何应用。请先在「设置 → 通用 → 工作模式」选择设备支持的方式
（Shizuku / Root / Dhizuku / DeviceOwner / Island / 特权应用）并完成授权。
自动冻结还需要通知使用权与使用情况访问权，用来跳过正在通知或处于前台的应用。

## 已知限制

- 六种冻结后端的写操作目前只有源码级实现，尚未在真机验证；界面与导航在 Android 16 模拟器上验证。
- 玻璃的折射、模糊等光学效果未在真机确认（模拟器为软件渲染）。
- 通知使用权、使用情况访问、生物识别需要先在系统设置中手动授权才会生效。
- 尚未完成：About 品牌区滚动动画、页面 overscroll/滚动条、二级页转场、Apps 多选；RTL、三键导航、弹层大字体未逐项验证。

## 许可证

Glacier 以 GPL-3.0-or-later 发布，包含源自 Hail（作者 aistra0528，同为 GPL-3.0-or-later）的衍生代码。
完整许可证见仓库 `LICENSE`，第三方组件与来源见 `NOTICE.md`；应用内也有「开源许可证」页。
```

> 注：`SHA-256` 对应**当前** `v1.1.0` 附件（线上 digest、本地 APK、`SHA256SUMS.txt` 三者一致）。若本轮 R4 另发新版本，需替换为新 APK 的 SHA-256，不要沿用此值。

#### 2.3.3 Release `v1.1.0` 需要删除的旧内容

整段替换即可，重点删除：`# Glacier 1.1.0` 重复标题、“第二轮界面整改”“1.0.0 最明显的问题”“这次修正了什么”“验收”小节与 `✅`、`docs/verification/…` 截图数量、“审计报告第 17.5 节”、L2/L3 与 `ro.hardware.egl` 等内部术语。

#### 2.3.4 README 需要替换的段落

本地 `README.md` 已按下列内容改好（**远端 GitHub README 未改**）。直接粘贴替换对应小节即可。

**（1）开头（替换现有标题下三段）**

```markdown
独立的 Android 应用冻结工具：把不常用的应用冻起来，减少后台运行。界面基于 MIUIX OS4 与 Glass 构建。

- 包名：`com.ayang.glacier`
- 系统要求：Android 13（API 33）及以上
- UI：Jetpack Compose + MIUIX OS4（不使用 Material3）
```

**（2）在「构建」之前新增两节**

```markdown
## 下载与安装

- 从 [Releases](https://github.com/Ayang233/Glacier/releases) 下载 `Glacier-release.apk`
- `adb install Glacier-release.apk`，或直接在设备上打开 APK
- 与 1.0.0 使用同一签名密钥，可直接覆盖升级

## 使用前

Glacier 不申请系统级冻结权限，冻结依赖设备侧的工作模式。刚装好时是 `default` 模式，
此时不会真的冻结任何应用。请到「设置 → 通用 → 工作模式」选择本机支持的方式并完成授权：

- Shizuku（强停 / 禁用 / 隐藏 / 挂起）
- Root（强停 / 禁用 / 隐藏 / 挂起）
- Dhizuku 或设备所有者（DeviceOwner）
- Island（冻结 / 挂起）
- 特权应用

自动冻结还需要通知使用权与使用情况访问权，用来跳过正在通知或处于前台的应用。
```

**（3）「构建」小节末尾补一句**

```markdown
MIUIX 以 composite build 引入（默认 `../miuix`，可用 `MIUIX_DIR` 覆盖）。
首次构建前请按 `settings.gradle.kts` 的提示准备 MIUIX 源码，基线记录在
[docs/architecture/miuix-baseline.md](docs/architecture/miuix-baseline.md)。
```

**（4）「发布」小节整体替换（删除本机网络限制与 `publish.py` 内部说明）**

```markdown
## 发布

- 仓库：https://github.com/Ayang233/Glacier
- Release：https://github.com/Ayang233/Glacier/releases

Release APK 使用独立密钥签名（`keystore/` 与 `signing.properties` 不入库）。
在缺少这两个文件的机器上，`assembleRelease` 会退回 debug 签名，仅用于本机构建。
```

**（5）删除**：「许可证」末尾的 `Hail 的商标与品牌不用于 Glacier 的正常界面。`（NOTICE.md 已覆盖）。

#### 2.3.5 可选：仓库 topics（提升可发现性）

`android`、`shizuku`、`root`、`app-freezer`、`miuix`、`jetpack-compose`、`gpl-3.0`

---

## 三、未做事项（需用户/lead 明确授权）

| 事项 | 状态 | 需要的操作（未执行） |
| --- | --- | --- |
| 修改 GitHub Repository Description | **未做** | `gh api -X PATCH repos/Ayang233/Glacier -f description="…"`（用 2.3.1 文案） |
| 修改 Release `v1.1.0` 正文 | **未做** | 用 2.3.2 内容写文件后 `gh api -X PATCH repos/Ayang233/Glacier/releases/451502814 -F body=@body.md` |
| 更新远端 README | **未做**（本地已改） | 需授权后走仓库现有 `tools/publish.py` 或 GitHub Contents API；本机 `git push` 不通 |
| 同步本地发布说明 | **未做** | 若确认改线上正文，应同步改 `docs/release-notes/1.1.0.md`，避免下次发布又把旧文推上去 |
| 设置 topics / homepage | **未做** | `gh api -X PUT repos/Ayang233/Glacier/topics -f names[]=…` 等 |
| 删除无引用 string key | **未做** | 需 lead 确认（见 1.4） |
| 代码级文案（Manifest receiver 描述、复制命令 toast、延时冻结 toast） | **未做** | 见 1.5，需对应 agent/lead 处理 |
| 构建与截图验证 | **未做** | 按约束本轮不运行 gradle；应用内文案需 lead 统一构建后目检 |

---

## 四、线上状态声明

- **GitHub 线上未做任何修改**：Repository Description、README、Releases（含 `v1.1.0` / `v1.0.0` 标题与正文）、附件、topics 全部保持审计时状态。
- 本轮对所有 GitHub 内容只执行了 `gh api` 的 GET 读取（repo、releases、release assets、readme、tags、commits）。
- 应用内改动只落在本地资源文件；未运行构建，未安装，未截图。
