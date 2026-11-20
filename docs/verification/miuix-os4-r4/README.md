# Glacier R4 验收实测记录（MIUIX OS4 · 模拟器）

> 验收人：verifier（只做验证与记录，未修改任何源码）
> 设备：Android 模拟器 `emulator-5554`，API 36，1080x2400 / 420dpi，MIUIX OS4 组件
> 被测包：`com.ayang.glacier`，versionName **1.1.0**（versionCode 2, minSdk 33, targetSdk 36），release 构建，**未重新构建/安装**
> 数据基线：Home 4 个应用（Glacier / InstallerX Revived / Miuix / Shizuku），2 个标签（默认 / q）
> 截图目录：`Glacier/docs/verification/miuix-os4-r4/`（`before/` 为改造前基线，未改动）
> 采集时间：2026-11-19 23:38 起（Asia/Shanghai）

## 环境与操作约定

- adb：`C:\Users\10487\AppData\Local\Android\Sdk\platform-tools\adb.exe -s emulator-5554`
- 截图：`adb -s emulator-5554 exec-out screencap -p > <文件>`（按字节重定向，未损坏 PNG）
- 坐标：**全部先用 `adb shell uiautomator dump` 取真实 bounds 再点击**，未靠截图猜坐标。注意一个坑：Home 进入选择态后标签行隐藏、网格整体上移，点击坐标必须重新 dump。
- 长按中间帧：`adb shell input motionevent DOWN x y` → sleep 300ms → screencap → sleep 500ms → screencap → `input motionevent UP x y`
- 深色/主题/字体缩放切换后均 `am force-stop com.ayang.glacier` 再 `am start -n com.ayang.glacier/com.ayang.glacier.ui.MainActivity`，确保生效
- 每阶段后收集 `adb logcat -d -b crash`，整轮 FATAL/ANR 汇总见文末第 12 节

## Glass 生效说明（重要）

- 当前主题=跟随系统、Glass=开。`isRuntimeShaderSupported()` 在 MIUIX 中就是 `SDK_INT >= 33`
  （`miuix/miuix-material/src/androidMain/kotlin/top/yukonga/miuix/kmp/material/RuntimeShader.android.kt:18`），
  模拟器 API 36 满足条件，因此 **R4 的 Glass 路径（RuntimeShader 实时模糊）在本次验收中确实被走到**（非降级路径）。
- 但这是模拟器的软件 GPU 渲染结果，**真实 GPU 上的光学表现（折射/色散/边缘高光，L3）不可验证**，见文末「无法验证」。

---

## 1. Home 长按 / 选择态

| 文件 | 场景 | 操作步骤 | 观察结论 |
|---|---|---|---|
| `01-longpress-hold-early.png` | 长按按住中间帧（未触发选择） | 首页对 Glacier 图标（cell bounds `[32,590][271,796]`，点 151,693）`input motionevent DOWN`，300ms 后截图 | **通过**。按压叠层是圆角矩形，严格限制在 Glacier 自己的格位内（x 约 32–271），没有越到相邻格，也没有方形切角/直角穿透 |
| `02-longpress-hold-selection-triggered.png` | 按住 800ms（长按已触发、手指未抬起） | 继续 sleep 500ms 后截图，随后 UP | **通过**。已进入选择态（顶部动作栏 冻结/解冻/标签/更多/关闭、标题「已选 1 个」），当前格出现蓝色圆角选中环；叠层仍是圆角、不越格。注：进入选择态后标签行（默认/q）隐藏，网格整体上移约 193px（属选择态布局差异，非叠层越界） |
| `03-selection-mode-after-release.png` | 松手后的选择态 | `input motionevent UP`，600ms 后截图 | **通过**。保持「已选 1 个」，选中环与按压高亮正常，无残留方形叠层、无闪烁条纹 |
| `04-selection-multi-2.png` | 多选 2 个 | 选择态下点 InstallerX Revived 格（cell `[292,397][530,603]`，点 411,500），dump 确认「已选 2 个」 | **通过**。两格均有蓝色圆角选中环，计数正确 |
| `05-selection-deselected-one.png` | 取消其中一个选择 | 再点 Glacier 格，dump 确认「已选 1 个」 | **通过**。取消选中后选中环消失、计数回到 1，无残影 |
| `06-selection-exited.png` | 退出选择模式 | 点顶部「关闭」（bounds `[953,159][1027,233]`，点 990,196） | **通过**。回到普通首页：标签行恢复、网格下移回位、动作栏消失，无残留遮罩 |
| `52-longpress-selection-transition.png` | 进入选择态的过渡中间帧 | 同一长按过程用 `screenrecord` 录屏后拆帧（30fps）取中间帧 | **通过**。网格上移是**动画**而非瞬时跳变：连续约 5 帧（≈0.17s）内图标名从普通态 y≈753 平滑移动到选择态 y≈560（中间帧处于两者之间），无闪白、无重影 |

补充：长按触发瞬间标签行（默认/q）隐藏、网格整体上移 193px（真实 bounds：普通态首格 `[32,590][271,796]` → 选择态 `[32,397][271,603]`），已确认是带过渡动画的布局重排（约 5 帧 / 0.17s），不是叠层越界。

未在该组发现新缺陷。crash buffer 全程 0 条（见第 12 节）。

## 2. 标签栏四态（浅/深 × Glass 开/关）

切换方式：设置 →「外观」→「玻璃效果」开关（开关 bounds `[877,1319][1006,1445]`，用 uiautomator 定位后点 941,1382），每次切换后 dump 核对 `checked` 值；深色用 `cmd uimode night yes` + `am force-stop` + 重启 App。截图均为 Home 标签行（默认选中 / q 未选中同屏可见）。

| 文件 | 场景 | 操作步骤 | 观察结论 |
|---|---|---|---|
| `07-tabbar-light-glass-on.png` | 浅色 × Glass 开 | 切回主页截屏 | **通过**。标签行是胶囊形 Glass 轨道，选中「默认」为半透明灰填充、未选中「q」为透明底；两态文字对比度均清晰，胶囊外轮廓/圆角完整 |
| `08-tabbar-light-glass-off.png` | 浅色 × Glass 关 | 设置关玻璃效果（dump 确认 checked=false）→ 主页截图 | **通过**。退化为纯色：选中「默认」= 白色填充胶囊 + 阴影，未选中「q」= 描边胶囊（浅灰 1px 边框）；两种状态区分明显，无文字与背景混色 |
| `09-tabbar-dark-glass-on.png` | 深色 × Glass 开 | `cmd uimode night yes` → 重启 App → 设置开玻璃效果（checked=true）→ 主页截图 | **通过**。深色 Glass 轨道（深灰半透明），选中为更亮的灰色填充胶囊，未选中 q 为更暗的透明区；文字为浅色，选中/未选中均可辨识 |
| `10-tabbar-dark-glass-off.png` | 深色 × Glass 关 | 设置关玻璃效果 → 主页截图 | **通过**。纯色深灰：选中 = 亮一档的实心胶囊，未选中 = 深色描边胶囊；边界清楚，无低对比度问题 |

四态均未发现文字截断、胶囊越界、圆角缺口或切换后残留。

## 3. About 滚动联动 6 阶段

进入路径：设置 → 滚到底 →「关于」行（`ArrowPreference`，uiautomator 行 bounds `[32,1898][1048,2089]`，点 540,1990）。
滚动用慢速 `input swipe 540 1700 540 1480 900`（每步约 206px，避免 fling 失控），每阶段同时截屏 + `uiautomator dump` 记录可见文本。
小标题（SmallTitle）：「应用」「系统」「开源信息」；顶栏标题「关于」由 `topBarTitleAlpha` 随 scrollProgress 淡入（AboutScreen 中 `(progress-0.35)/0.65`）。

| 文件 | 阶段 | 操作 | uiautomator 可见 SmallTitle / 顶栏文本 | 观察结论 |
|---|---|---|---|---|
| `11-about-initial.png` | 初始（progress=0） | 刚进入 About | SmallTitle：「应用」@y1305、「系统」@y1993；顶栏「关于」**不在 dump 中**（alpha=0 不可见） | **通过**。品牌区完整（图标+Glacier+v1.1.0(2)），滚动条不显示 |
| `12-about-scroll-quarter.png` | 约 1/4（列表滚动约 207px） | 慢速上滑 1 步 | SmallTitle：「应用」@y1098、「系统」@y1786、「开源信息」@y2326；顶栏「关于」仍未出现 | **通过**。品牌区整体上移，图标/名称/版本仍可见；顶栏标题尚未到淡入阈值（progress≈0.18<0.35），符合实现曲线 |
| `13-about-scroll-half.png` | 约 1/2（约 412px） | 再上滑 1 步 | SmallTitle：「应用」@y893、「系统」@y1581、「开源信息」@y2121；顶栏「关于」@[488,159] 已淡入 | **通过**。品牌「Glacier」文字已淡出、图标缩小并半透明；顶栏标题为半透明灰，联动跟随、无跳变 |
| `14-about-brand-collapsing.png` | 将折叠（约 618px） | 再上滑 2 步 | 「应用」@y683、「系统」@y1371、「开源信息」@y1911；顶栏「关于」@[488,159] | **通过**。品牌层完全淡出，顶栏标题接近全黑；内容连续滑过，无闪白/双影 |
| `15-about-brand-collapsed.png` | 完全折叠（滚到底，offset≈814px 夹紧） | 再上滑 1 步（到边界） | 「应用」@y491、「系统」@y1179、「开源信息」@y1719；顶栏「关于」全黑 @[488,159] | **通过**。标题不透明、内容到「冻结很少使用的应用，减少后台运行。」为止，未出现 overscroll 后残留错位。注：滚到底时品牌占位区仍留约 200px 空白（logo spacer 未滚出，属官方 AboutPage 同款结构），非布局错位 |
| `16-about-scroll-restored-top.png` | 回滚恢复（回顶部） | 慢速下滑 3 步回顶 | SmallTitle 位置与 `11` 完全一致（「应用」@y1305、「系统」@y1993）；顶栏「关于」重新消失 | **通过**。品牌区、标题 alpha 全部复原，与初始帧一致（像素尺寸 85782 vs 85866，仅状态栏时钟差异），无残留半透明/残影 |

阶段证据补充：顶栏标题在 progress≈0.35 附近才出现，回滚到顶后完全消失，说明 alpha 是滚动位置的纯函数、没有两套动画互相追赶。

## 4. 搜索（Home / Apps 各一组，回归「关闭后残留遮罩/键盘」）

| 文件 | 场景 | 操作步骤 | 观察结论 |
|---|---|---|---|
| `17-home-search-open.png` | Home 搜索打开 | 主页点搜索图标（uiautomator 定位 desc=搜索，`[679,159][753,233]`） | **通过**。出现搜索条（EditText + Clear + Cancel），键盘 `mInputShown=true`；页面按官方转场淡出，无遮挡错位 |
| `18-home-search-results.png` | Home 输入关键词出结果 | `adb shell input text "gla"` | **通过**。结果列表出现 Glacier 一行（显示包名 `com.ayang.glacier.debug`，见下方备注），Clear 清除按钮正常出现 |
| `19-home-search-closed.png` | Home 关闭搜索（回归） | 点 Cancel（desc=Cancel，`[953,160][1027,234]`） | **通过，无回归**。回到普通主页：无遮罩、无残留搜索条；`dumpsys input_method` 确认 `mInputShown=false`，焦点仍在 MainActivity。注：用系统返回键第一次只收起键盘、搜索层保留，再按一次/点 Cancel 才关闭——这是 Android 常规交互，不算缺陷 |
| `20-apps-search-open.png` | Apps 搜索打开 | 应用页点搜索图标（`[721,159][795,233]`） | **通过**。搜索条 + 键盘正常，原列表淡出 |
| `21-apps-search-results.png` | Apps 输入关键词出结果 | `input text "shi"` → 结果 Shizuku（moe.shizuku.privileged.api） | **通过**。关键词高亮/结果正确，无空白结果页 |
| `22-apps-search-closed.png` | Apps 关闭搜索（回归） | 点 Cancel | **通过，无回归**。列表恢复原 8 个应用，无遮罩、`mInputShown=false` |

备注：Home 搜索结果里 Glacier 显示的是 `com.ayang.glacier.debug`，因为模拟器上同时装有 debug 包（应用页可见 `Glacier / com.ayang.glacier.debug` 与 `com.aistra.hail` 等），属环境数据，不是本轮 R4 代码问题。

## 5. 页面转场（设置 → 关于 push / 返回 pop）

中间帧获取方式（比连拍可靠）：`adb shell screenrecord --time-limit 4 --bit-rate 8M /sdcard/push.mp4` 在后台录制 → 700ms 后点「关于」→ 录完 pull 回来用 ffmpeg 按 30fps 拆帧 → 按键值逐帧比对找出动画区间。push 动画约 `f018→f034`（≈16 帧 / 0.53s），pop 约 `f022→f042`。

| 文件 | 场景 | 操作步骤 | 观察结论 |
|---|---|---|---|
| `23-push-transition-early.png` | push 中间帧（约 30%） | 录制中点击设置页「关于」行（540,1990） | **通过**。新页（About）从右侧滑入，旧页（设置）整体被灰色 scrim 压暗、轻微缩小让位；无白闪、无黑帧；旧页内容没有被裁切成锯齿或撕裂 |
| `24-push-transition-late.png` | push 中间帧（约 80%） | 同一次录制取后段帧 | **通过**。进入中的 About 页左侧上下角为**圆角**（约 16dp 观感），圆角外透出下方压暗的设置页；页内内容（品牌图标/卡片）完整、无逐层错位。系统级圆角裁剪的可见性说明见「无法验证」 |
| `25-pop-transition-early.png` | pop 中间帧（约 30%） | 录制中点系统返回（keyevent 4） | **通过**。About 页向右退出，设置页从左侧回位并同步解除 scrim；无残影层叠错乱 |
| `26-pop-transition-late.png` | pop 中间帧（约 80%） | 同一次录制后段帧 | **通过**。设置页基本回位，底部 Glass 导航栏已完全可见；导航栏玻璃把它后方的「关于」行模糊掉，属 Glass 正常采样行为（关闭动画后静态帧验证无异常）。pop 结束时页面滚动位置保持原状（仍停在设置页底部） |

补充：录屏最后一帧仍在动画尾段（scrub 未完全结束），另做静态复核确认动画结束后无残留遮罩：最终 `uiautomator dump` 与静态截图均为完整设置页，无重叠区域。

## 6. 二级页：About 完整页 / Licenses 页

| 文件 | 场景 | 操作步骤 | 观察结论 |
|---|---|---|---|
| `27-about-page-full.png` | About 完整页 | 设置底部点「关于」（行 bounds `[32,2114][1048,2305]`） | **通过**。完整结构可见：品牌区（88dp 白底圆角容器 + 74dp 图标 + 35sp 应用名 + 版本）、「应用」SmallTitle + 信息卡、「系统」分组；返回箭头在左上、无大标题折叠（`expandable=false`），与 R4 预期一致 |
| `28-licenses-page.png` | Licenses 页 | About 滚到「开源许可证」行（`[32,1824][1048,1972]`）点击进入 | **通过**。左上返回箭头（ChevronBackward）存在、可点；标题「开源许可证」；组件卡片列表（MIUIX / AndroidX / Shizuku / Dhizuku / AppIconLoader / libxposed API / Hail）排版正常，长文案自动换行不溢出卡片 |
| `29-licenses-scrolling-scrollbar.png` | Licenses 滚动条 | `input motionevent DOWN 540 1700` → MOVE 到 1000 保持按住 → 截图 → UP | **通过**。右缘出现官方 `VerticalScrollBar`（右缘 x≈1062–1065，y≈666 起的一小段灰色竖条），随滚动位置移动；顶部 Glass 顶栏正常模糊其下滚过的内容 |
| `30-licenses-scrolled.png` | Licenses 滚动到底 | 继续 MOVE → UP，滚到 GPL-3.0 正文段落 | **通过**。长许可证正文正常滚动、无截断/重叠；GPL 标题使用 SmallTitle 层级正确 |
| `31-about-scrollbar-drag.png` | About 滚动条 | About 页按住上拖（DOWN 1700 → MOVE 1200）中途截图 | **通过**。About 页同样出现右侧滚动条（x≈1063，y 从 556 起，约 700 个采样点），与 Licenses 行为一致；对照：静止/到顶时不显示，符合 MIUIX 滚动条显隐规则 |

未发现二级页返回箭头缺失、滚动条缺失或正文横向溢出。

## 7. 应用操作面板（Apps）

| 文件 | 场景 | 操作步骤 | 观察结论 |
|---|---|---|---|
| `32-apps-action-sheet.png` | Apps 点按打开操作单 | 应用页点 InstallerX Revived 行（y≈1230） | **通过**。底部操作单圆角（顶部大圆角）+ 拖拽把手 + 标题「InstallerX Revived」；行项 启动/冻结/置顶/加入白名单/从主页移除/应用信息/提取 APK/卸载 全部可见，卸载为红色危险色，底部显示包名。无遮挡、无行高塌陷 |
| `33-action-sheet-font20.png` | 2.0x 字体下的操作单 | `settings put system font_scale 2.0` + 重启 App → 应用页点行 | **通过**。标题与所有行在 2.0x 下仍完整显示，**「卸载」无需滚动即直接可见**（`uiautomator`：卸载 `[189,2053][341,2163]`），操作单没有超出屏幕、没有被底栏遮挡；行高随字体放大、无文字互压 |

字体已按约束恢复：`font_scale=1.0` + 重启 App（`settings get system font_scale` 复核 = 1.0）。

## 8. 大字体：1.3x / 2.0x 的 Home / Apps / Settings

| 文件 | 场景 | 操作步骤 | 观察结论 |
|---|---|---|---|
| `37-home-font1.3.png` | 1.3x Home | `font_scale=1.3` + 重启 App | **通过**。标题/副标题/标签/图标名等比放大，4 列网格不溢出 |
| `38-apps-font1.3.png` | 1.3x Apps | 同上 | **通过**。行高自适应，包名完整单行显示，无截断 |
| `39-settings-font1.3.png` | 1.3x Settings | 同上 | **通过**。分组卡片与行文案正常换行，开关位置不错位 |
| `34-home-font2.0.png` | 2.0x Home | `font_scale=2.0` + 重启 App | **通过（有截断属预期）**。仍保持 4 列；图标名放不下时以省略号截断（`Install…` / `Shizu…`），不溢出格位、不与相邻格重叠；标签栏「默认/q」与标题正常 |
| `35-apps-font2.0.png` | 2.0x Apps | 同上 | **通过**。应用行文字放大到 2 行（包名换行），行高随内容增高，图标与文字仍垂直居中，无重叠 |
| `36-settings-font2.0.png` | 2.0x Settings | 同上 | **通过（记录一个观感现象，不算缺陷）**。描述文字换行为两行、卡片高度自适应，没有裁切；但列表内容可以滚到 Glass 底部导航栏之下（见底部「关闭后保留图标原色」在导航胶囊下方透出）——这是悬浮玻璃导航栏 + 内容滚动的正常叠层，继续下滚可完整看到；暂未见底部 padding 不足导致最后一项无法滚出的证据 |

字体已按约束恢复：`font_scale=1.0`（见第 7 节末尾）。

## 9. 横屏（宽屏侧栏档）

操作：`settings put system accelerometer_rotation 0` + `settings put system user_rotation 1`（= Surface.ROTATION_90，宽屏 2400x1080），等待 2.5s 生效；侧栏展开/折叠用顶栏胶囊左侧按钮（desc=Expand/Collapse navigation rail）切换。完成后已恢复 `user_rotation=0`、`accelerometer_rotation=1`。

坐标与实测（clickable 行 bounds，命令可复现）：
- 折叠态：Apps 行 `x 256→2368`，Settings 行 `x 288→2368`
- 展开态：Apps 行 `x 944→2368`，Settings 行 `x 976→2368`

| 文件 | 场景 | 操作步骤 | 观察结论 |
|---|---|---|---|
| `40-home-landscape.png` | 横屏 Home（侧栏折叠） | 转屏后默认状态截图 | **通过（有 1 个记录点）**。顶栏改为居中的 Glass 导航胶囊（主页/应用/设置）+ 左侧展开按钮，底部导航消失；4 列网格、标签栏正常；页面**标题「Glacier / 默认 · 4 个应用」在折叠态不可见**（只有胶囊），展开态才出现——与 PageFrame 注释里「宽屏时主页面也会自动折叠成紧凑标题」的预期不完全一致，作为观察项记录，未判缺陷 |
| `41-home-landscape-rail-expanded.png` | 横屏 Home（侧栏展开） | 点展开按钮 | **通过**。左侧 Glass 侧栏（主页/应用/设置 + 折叠按钮）出现，标题/副标题出现在内容区上方；内容整体右移让位 |
| `42-apps-landscape-rail-expanded.png` | 横屏 Apps（侧栏展开） | 侧栏点「应用」 | **通过**。列表在宽屏 pane 内正常排布，右上动作图标（搜索/排序/筛选）位置正确 |
| `43-settings-landscape-rail-expanded.png` | 横屏 Settings（侧栏展开） | 侧栏点「设置」 | **通过**。分组卡片在宽 pane 内左右铺满，右侧开关对齐 row 右缘 |
| `44-settings-landscape-rail-collapsed.png` | 横屏 Settings（侧栏折叠） | 点折叠按钮 | **有问题（与 lead 已知修复项一致）**。内容卡片左缘 x≈288（Apps 为 256），右缘 2368（右边距≈32=12dp）；左右明显不对称，左缘比右侧多出约 256px，量级≈两次 128px 刘海 inset。即**宽屏 pane 的 start 侧发生了二次让位**（刘海 inset 被算两次），与 lead 说明的「最终 APK 已改 start 侧不再二次让 inset」对应。当前 APK 上该问题可复现，最终 APK 需按此口径复核（期望左缘回到 128+12dp≈160 量级） |
| `45-apps-landscape-rail-collapsed.png` | 横屏 Apps（侧栏折叠） | 同上 | 同上（左缘 x≈256 / 右缘 2368），与 Settings 一致地多让一次 inset |

一致性结论（供 delta 复核对照）：展开 vs 折叠时 Apps 行左缘位移 `944-256=688`，Settings 位移 `976-288=688`，两页完全一致；且展开/折叠的**右缘恒为 2368 不变**。即「侧栏展开/折叠两态内容左缘一致（相对量固定）」在折叠口径上成立，剩下的是上面这条全局 start 二次让位问题。

## 10. 深色模式（Home / Apps / Settings / About）

操作：`cmd uimode night yes` → `am force-stop` → 重启 App（确保主题生效）；关于页从设置底部进入。完成后 `cmd uimode night no` + 重启（已复核 Night mode: no）。

| 文件 | 场景 | 观察结论 |
|---|---|---|
| `46-home-dark.png` | 深色 Home | **通过**。背景纯黑、标题/副标题/图标名浅色可读；标签栏深灰轨道 + 选中胶囊清晰；底部 Glass 导航栏深色材质正常，无「黑底黑字」 |
| `47-apps-dark.png` | 深色 Apps | **通过**。应用行卡片深灰、标题浅色、包名 secondary 色对比度足够；右上动作图标可见 |
| `48-settings-dark.png` | 深色 Settings | **通过**。分组卡片、开关（开=蓝色、关=灰色）与文案对比度正常；「跟随系统」状态值可见 |
| `49-about-dark.png` | 深色 About | **通过**。品牌区、SmallTitle「应用/系统」、信息卡在深色下层级清楚，卡片与背景有足够分离度；无白底残留块 |

深色下未发现文字对比度不足、黑色图标不可见或卡片混色问题。


## 11. ApiActivity（对外 API / 深链窗口）

先核实源码契约（`app/src/main/kotlin/com/ayang/glacier/app/GlacierApi.kt` + `AndroidManifest.xml`）：
- **没有** `com.ayang.glacier.action.API` 这个 action。真实 action 是 `${applicationId}.action.LAUNCH/FREEZE/UNFREEZE/FREEZE_TAG/.../LOCK_FREEZE`，包名 extra 键为 `"package"`。
- `android.intent.action.SHOW_APP_INFO` 分支读的是 **`Intent.EXTRA_PACKAGE_NAME`**（`android.intent.extra.PACKAGE_NAME`），不是 `"package"`（用错键会弹「缺少包名」）。
- 系统 action `SHOW_APP_INFO` 在模拟器上有多家声明（Glacier release/debug + 系统），不带 `-n` 会先弹「选择要使用的应用」，所以验收命令要显式指定组件。

| 文件 | 场景 | 操作步骤 | 观察结论 |
|---|---|---|---|
| `50-api-show-app-info.png` | ApiActivity：SHOW_APP_INFO 操作单 | `am start -n com.ayang.glacier/com.ayang.glacier.feature.api.ApiActivity -a android.intent.action.SHOW_APP_INFO --es android.intent.extra.PACKAGE_NAME com.rosan.installer.x.revived` | **通过（记录现状）**。独立窗口内弹出完整操作单（标题 InstallerX Revived + 8 项），窗口背景为半透明灰（当前 `Theme.Glacier.Translucent`），**背景是纯灰、没有透出桌面/主界面内容**；lead 说明最终 APK 会把该窗口改成不透明主题，届时需按「不透明页面 + Glass 弹层背景完整、不出现窗口透底」复核 |
| `51-api-error-dialog.png` | ApiActivity：错误 GlassDialog | `-a com.ayang.glacier.action.FREEZE --es package com.aistra.hail.debug`（该包不在主页/未托管） | **通过**。弹 GlassDialog：「Glacier / 该应用不在主页 / 确定」，文案正确、按钮全宽、无截断；点确定后窗口关闭 |

补充观察：窗口是用 `launchMode=singleInstance` 的独立 Activity，`dumpsys window` 确认 focus 落在 `com.ayang.glacier/.feature.api.ApiActivity`，返回后回到主界面/桌面，无残留窗口。

## 12. 崩溃 / ANR 检查

- 每个阶段后都执行 `adb logcat -d -b crash`：**整轮 crash buffer 为 0 行**（从 23:37 baseline 到 23:57 结束，本文档出现的每次 `CrashLog` 均为 0 行）。
- 整轮主 logcat（61301 行）过滤 `FATAL EXCEPTION|ANR in|am_anr|E AndroidRuntime`：**0 命中**。
- `dumpsys dropbox` 里最近的 `data_app_crash` 条目全部早于本轮（最新 19:34，本轮 23:37 开始），且无任何 `ayang` 相关条目。
- 结论：**本轮 12 组场景未发现 Glacier 的崩溃或 ANR**。

## 无法验证（受环境限制，非通过）

1. **L3 真实 GPU 光学表现（Glass 折射/色散/边缘高光）**：模拟器 API36 满足 `isRuntimeShaderSupported()`（`SDK_INT>=33`），Glass RuntimeShader 路径确实被执行，但渲染结果是模拟器软件 GPU 产出，像素级光学质量不能代表真机。需真机复验。
2. **系统级圆角/刘海裁剪的像素证据**：`dumpsys window displays` 报告屏幕四角 `RoundedCorners radius=28px`、`DisplayCutout insets=(0,128,0,0)`（横屏 ROTATION_90 时 `overrideConfigInsets=[128,63]`，即刘海在左），但 **`screencap` 截图不包含显示层的圆角/刘海遮罩**（实测截图四角像素是应用内容色，非黑/透明），因此「系统把页面裁成圆角/避开刘海」无法从截图直接验证；本轮只能验证应用自身页面容器的圆角（转场中间帧可见）与 inset 数值行为。
3. **真机高刷/长按触感与触控采样差异**：模拟器 60Hz、无真实触感，滚动末端触感（`scrollEndHaptic`）与长按触觉节奏无法判定。
4. **第三方输入法行为**：搜索场景用的模拟器 AOSP 输入法会弹「正在等待下载语言包…」提示，属键盘自身问题，与应用无关；输入法高度变化对布局的影响未做全键盘矩阵。
5. **正式发布签名/安装渠道相关行为**（如系统快捷方式、磁贴、通知使用权引导）未纳入本轮截图矩阵。

## 发现的问题 / 待复核清单（如实记录）

| # | 现象 | 证据文件 | 严重度/说明 |
|---|---|---|---|
| P1 | 横屏（宽屏侧栏档）折叠态内容左缘 ≈288px（Apps 256px）、右缘恒为 2368px（右边距仅 32px≈12dp），左右明显不对称，左缘量级≈刘海 128px inset 被应用了两次（宽屏 pane start 侧二次让位） | `44-settings-landscape-rail-collapsed.png`、`45-apps-landscape-rail-collapsed.png` | **已在最终 APK 修复**（详见文末 D1：折叠态左缘 288/256 → **160**，右缘 2368 不变，展开态 848、两态位移 688 一致）。此行为 23:35 构建上的复现记录 |
| P2 | 横屏折叠态下页面标题整体不可见（Home 的「Glacier / 默认·4 个应用」、Apps「应用 / 8 个应用」、Settings「设置 / Android 16」都只剩顶部导航胶囊），展开侧栏后才出现；与 `PageFrame` 注释「宽屏时主页面也会自动折叠成紧凑标题」表述不完全一致 | `40-home-landscape.png`、`44`、`45` | 观察项（可能是刻意隐藏以避免与胶囊冲突），不判缺陷，交由 lead 决策 |
| P3 | About 页滚到底（完全折叠）时，品牌 logo spacer 仍留约 200px 空白，内容最底部「冻结很少使用的应用，减少后台运行。」之后到屏幕底部还有留白 | `15-about-brand-collapsed.png` | 观察项。与官方 AboutPage 结构一致（头图占位不滚出），暂不判缺陷 |
| P4 | 2.0x 字体下 Settings 列表内容可滚动到悬浮 Glass 底部导航栏之下（导航胶囊区域半透明透出「关闭后保留图标原色」等文字） | `36-settings-font2.0.png` | 观察项。属悬浮玻璃导航 + 内容滚动的正常叠层；未发现最后一项无法滚出的证据，但建议最终 APK 顺带确认底部 padding |
| P5 | 搜索态按一次系统返回键只收起键盘、搜索层保留（需再按一次或点 Cancel 才关闭） | `18`/`19`、`21`/`22` | 符合 Android 常规交互，不判缺陷（此前的「关闭后残留遮罩/键盘」回归项已验证通过） |

未发现：叠层越界/方形切角、标签栏文字不可读、About 淡入淡出跳变、转场白闪/黑屏、操作单行重叠、二级页返回箭头缺失、崩溃/ANR。

## 总体结论（当前 APK = lead 23:35 构建版本）

- 12 组场景全部执行完毕，产出 **52 张截图**（`01`–`52`）+ 本 README；`before/` 5 张基线未改动，临时 `tmp-*.png` 已清理。
- 主体改动（Home 长按叠层、标签栏官方页面级标签、About 滚动联动、转场、搜索、操作单、大字体、深色、ApiActivity）均按 R4 预期工作，未发现主干功能回归。
- 已知问题：P1（横屏 start 二次让位）**已在最终 APK 修复并复核通过**（文末 D1）；P2 为观察项；新增 P3 首帧标签行时序观察项（文末 D5）。
- 环境已恢复：`font_scale=1.0`、`user_rotation=0`、`accelerometer_rotation=1`、`cmd uimode night no`、Glass=开、主题=跟随系统；App 数据（Home 4 应用 + 默认/q 两标签）全程保留。
- 最终 APK（23:58 构建）的 delta 复核见文末「最终 APK delta 复核（23:59 之后）」：D1 P1 修复数值、D2 ApiActivity 不透明主题、D3 死代码删除、D4 三张回归、D5 观察项、D6 结论。

## 可重复命令清单

```powershell
$adb = "C:\Users\10487\AppData\Local\Android\Sdk\platform-tools\adb.exe"
$OUT = "C:\Users\10487\WorkBuddy\Hail\Glacier\docs\verification\miuix-os4-r4"
function Shot($n) { & $adb -s emulator-5554 exec-out screencap -p > (Join-Path $OUT $n) }
function Dump($n) { & $adb -s emulator-5554 shell uiautomator dump /sdcard/ui.xml | Out-Null; & $adb -s emulator-5554 exec-out cat /sdcard/ui.xml > (Join-Path $OUT $n) }

# 启动/清状态
& $adb -s emulator-5554 shell am force-stop com.ayang.glacier
& $adb -s emulator-5554 shell am start -n com.ayang.glacier/com.ayang.glacier.ui.MainActivity
Start-Sleep -Milliseconds 1800

# 1 Home 长按（先 dump 取格位 bounds；普通态首格 [32,590][271,796]）
& $adb -s emulator-5554 shell input motionevent DOWN 151 693
Start-Sleep -Milliseconds 300; Shot "01-longpress-hold-early.png"
Start-Sleep -Milliseconds 500; Shot "02-longpress-hold-selection-triggered.png"
& $adb -s emulator-5554 shell input motionevent UP 151 693
Start-Sleep -Milliseconds 600; Shot "03-selection-mode-after-release.png"
# 选择态下网格上移：第二格 [292,397][530,603]，点 411,500；关闭按钮点 990,196
& $adb -s emulator-5554 shell input tap 411 500
& $adb -s emulator-5554 shell input tap 151 500
& $adb -s emulator-5554 shell input tap 990 196

# 2 标签栏四态：设置页「玻璃效果」开关 bounds [877,1319][1006,1445] → 941,1382；深色需 cmd uimode night yes + force-stop/重启
& $adb -s emulator-5554 shell settings get system font_scale
& $adb -s emulator-5554 shell cmd uimode night yes   # / no

# 3 About 六阶段：设置滚到底点「关于」（行 [32,2114][1048,2305]→540,2160）
for ($i=0; $i -lt 3; $i++) { & $adb -s emulator-5554 shell input swipe 540 1700 540 1480 900; Start-Sleep -Milliseconds 700 }

# 5 转场逐帧：录屏拆帧（本机 ffmpeg 可用）
$p = Start-Process -FilePath $adb -ArgumentList '-s','emulator-5554','shell','screenrecord','--time-limit','4','--bit-rate','8000000','/sdcard/push.mp4' -NoNewWindow -PassThru
Start-Sleep -Milliseconds 700
& $adb -s emulator-5554 shell input tap 540 2160
Wait-Process -Id $p.Id
& $adb -s emulator-5554 pull /sdcard/push.mp4 "$env:TEMP\push.mp4"
ffmpeg -i "$env:TEMP\push.mp4" -vf fps=30 "$env:TEMP\frames\f%03d.png"

# 7 操作单 2.0x 字体
& $adb -s emulator-5554 shell settings put system font_scale 2.0
& $adb -s emulator-5554 shell am force-stop com.ayang.glacier
& $adb -s emulator-5554 shell am start -n com.ayang.glacier/com.ayang.glacier.ui.MainActivity

# 9 横屏（宽屏侧栏档），完成必须恢复
& $adb -s emulator-5554 shell settings put system accelerometer_rotation 0
& $adb -s emulator-5554 shell settings put system user_rotation 1
& $adb -s emulator-5554 shell settings put system user_rotation 0
& $adb -s emulator-5554 shell settings put system accelerometer_rotation 1

# 10 深色
& $adb -s emulator-5554 shell cmd uimode night yes
& $adb -s emulator-5554 shell am force-stop com.ayang.glacier
& $adb -s emulator-5554 shell am start -n com.ayang.glacier/com.ayang.glacier.ui.MainActivity
& $adb -s emulator-5554 shell cmd uimode night no

# 11 ApiActivity（不要用不存在 .action.API；SHOW_APP_INFO 的 extra 键是 android.intent.extra.PACKAGE_NAME）
& $adb -s emulator-5554 shell "am start -n com.ayang.glacier/com.ayang.glacier.feature.api.ApiActivity -a android.intent.action.SHOW_APP_INFO --es android.intent.extra.PACKAGE_NAME com.rosan.installer.x.revived"
& $adb -s emulator-5554 shell "am start -n com.ayang.glacier/com.ayang.glacier.feature.api.ApiActivity -a com.ayang.glacier.action.FREEZE --es package com.aistra.hail.debug"

# 12 崩溃/ANR
& $adb -s emulator-5554 logcat -d -b crash
& $adb -s emulator-5554 logcat -d -v brief | Select-String "FATAL EXCEPTION|ANR in|am_anr|E AndroidRuntime"
& $adb -s emulator-5554 shell dumpsys dropbox | Select-String "data_app_crash|data_app_anr"
```

## 文件清单

- 本轮新增截图 **65 张**：`01`–`65`（`01`–`52` = 23:35 构建主矩阵；`53`–`65` = 最终 APK delta 复核，见文末「最终 APK delta 复核」）。
- `before/`（5 张改造前基线）未改动：`01-longpress-square-overlay-fading.png`、`02-selection-mode-r3.png`、`03-tab-selected-black-light.png`、`04-apps-list-r3.png`、`05-action-sheet-r3.png`。
- 临时文件（`tmp-*.png`）已按任务要求清理。
- 本 README 为增量记录：每组场景完成后即写入（1→2→3→4→5→6→7→8→9→10→11→12）。

---

# 最终 APK delta 复核（23:59 之后）

复核对象：lead 重新构建并安装的最终 release APK —— `com.ayang.glacier`，versionName **1.1.0**，versionCode **2**，`lastUpdateTime=2026-11-19 23:58:32`（相对上一轮 23:35 构建只改了 4 处：宽屏 start 让位、ApiActivity 窗口主题、删死代码、删 HomeScreen 未用变量）。
复核时间：2026-11-19 23:59 – 2026-11-20 00:09（模拟器 emulator-5554，API36，1080x2400/420dpi，浅色玻璃档为基线，font_scale=1.0、user_rotation=0、accel=1、night=no 已复原）。
本轮新增截图 **13 张**：`53`–`65`（累计 65 张，`before/` 5 张未动，`tmp-*.png` 已清理）。

## D1. P1 修复复核：横屏宽屏 start 让位双算（重点）

测量方法（双证据）：
1. `uiautomator dump` 取行/卡片可点击节点的真实 bounds（clickable node）；
2. 截图像素扫描：在卡片行内取一条水平线，从左/从右找第一个 RGB≥252 的白色像素作为卡片边缘（分辨率 2400x1080）。

| 项 | 修复前（23:35 构建） | 修复后（最终 APK） | 期望 | 结论 |
|---|---|---|---|---|
| 折叠态 Settings 卡片左缘 | 288px（clickable `[288,...]`） | **160px**（clickable `[160,347][2368,547]`；像素扫描 y=400 也得 160） | 128 刘海 + 12dp(32) = **160** | **通过，完全吻合** |
| 折叠态 Apps 卡片左缘 | 256px | **160px**（clickable `[160,221][2368,421]`；像素扫描 y=340 也得 160） | 160 | **通过** |
| 折叠态右缘 | 2368px（右边距≈32） | **2367–2368px 不变**（像素扫描 y=400/340 得 2367） | 不变 | **通过，未被多切** |
| 展开态 Settings 卡片左缘 | 976px | **848px**（clickable `[848,347][2368,547]`） | 折叠 160 + 侧栏 688 = 848 | **通过** |
| 展开态 Apps 卡片左缘 | 944px | **848px**（clickable `[848,221][2368,421]`） | 848 | **通过** |
| 展开/折叠左缘位移 | 688（Settings）/ 688（Apps） | **688 / 688，完全一致** | 一致 | **通过** |
| 展开态右缘 | 2368 | **2368 不变** | 不变 | **通过** |

结论：**P1 已修复**。折叠态左右仍不对称（160 vs 32）但这是“刘海 128 + 12dp 内容边距”的正确结果（横屏 ROTATION_90 的 cutout 在左，`dumpsys window displays` 的 `overrideConfigInsets=[128,63]`），不再有第二次叠加；展开/折叠两态位移一致、右缘恒定。

| 文件 | 场景 | 结论 |
|---|---|---|
| `53-home-landscape-final.png` | 横屏 Home（侧栏折叠，最终 APK） | **通过**。标签行「默认/q」、4 列网格、右上动作正常；标题在折叠态仍不可见（见 D5 的 P2 观察项，与本轮 4 处改动无关） |
| `54-settings-landscape-final-collapsed.png` | 横屏 Settings（折叠） | **通过**。卡片左缘 160 / 右缘 2368，与上表一致 |
| `55-apps-landscape-final-collapsed.png` | 横屏 Apps（折叠） | **通过**。卡片左缘 160 / 右缘 2368 |
| `56-settings-landscape-final-expanded.png` | 横屏 Settings（侧栏展开） | **通过**。卡片左缘 848 / 右缘 2368 |
| `57-apps-landscape-final-expanded.png` | 横屏 Apps（侧栏展开） | **通过**。卡片左缘 848 / 右缘 2368 |

## D2. ApiActivity 不透明窗口主题复核

源码依据（最终 APK 内主题，`app/src/main/res/values/themes.xml` 与 `values-night/themes.xml`）：
```xml
<style name="Theme.Glacier.Api" parent="Theme.Glacier">
    <item name="android:windowIsTranslucent">false</item>
    <item name="android:windowBackground">@color/glacier_window_background</item> <!-- #FFF7F7F9 -->
    <item name="android:windowNoTitle">true</item>
    <item name="android:backgroundDimEnabled">false</item>
</style>
```
`dumpsys window windows` 中 ApiActivity 窗口 `mAttrs` 为常规 `ty=BASE_APPLICATION`，无半透明标记；`dumpsys window` 确认焦点为 `com.ayang.glacier/.feature.api.ApiActivity`。

| 文件 | 场景 | 结论 |
|---|---|---|
| `58-api-final-light-sheet.png` | ApiActivity 浅色 + Glass 操作单 | **通过**。整窗为不透明页面（背景为 surface `#F7F7F9` 与弹层 scrim 合成的均匀灰，实测像素 `R198 G198 B198`，与“247×0.8≈198”吻合）；没有透出桌面/Home，操作单 8 项与包名完整。与旧版截图逐像素同值（说明旧版视觉上也无透底），但新版不再依赖下层窗口，语义正确 |
| `59-api-final-light-error.png` | ApiActivity 浅色 + 错误 GlassDialog | **通过**。「Glacier / 该应用不在主页 / 确定」，文案与按钮完整，关闭后窗口结束 |
| `60-api-final-dark-sheet.png` | ApiActivity 深色 + Glass 操作单 | **通过**。窗口背景为纯黑（不透明 night 变体），Glass 操作单深灰、内容完整清晰，无桌面/Home 透出，无白闪 |
| `61-api-final-dark-error.png` | ApiActivity 深色 + 错误弹层 | **通过**。深色 GlassDialog 对比度正常，文案完整 |
| `65-api-final-launch-frame.png` | ApiActivity 启动中间帧（录屏拆帧） | **通过**。新窗口首帧背景为自身 windowBackground 浅色（实测 246/246/246 与 244/246/246），未出现纯白闪帧，也未出现桌面透出；随后弹层动画进入 |

## D3. 删死代码 / HomeScreen 未用变量（无行为影响项）

- 删除 `LocalPageBackdrop` / `LocalRailInset` / `LocalGlassRequested`、HomeScreen 未用变量后，本轮全部场景（长按、选择、标签、About、Licenses、搜索、转场、操作单、大字体、横屏、深色、ApiActivity）均正常，未见崩溃或异常重绘；整轮 `logcat -b crash` 仍为 **0 行**，主 logcat `FATAL EXCEPTION|ANR in|am_anr` 0 命中。

## D4. 3 张回归（最终 APK）

| 文件 | 场景 | 结论 |
|---|---|---|
| `62-home-light-final.png` | Home 浅色（含标签行） | **通过**。标题「Glacier / 默认 · 4 个应用」、标签行「默认 / q」（uiautomator 两文本都在）、4 图标、底部导航与 FAB 均正常，与改造后基线一致 |
| `63-about-initial-final.png` | About 初始 | **通过**。品牌区完整（图标 + Glacier + v1.1.0(2)）、SmallTitle「应用」@y1305、「系统」@y1993、顶栏「关于」alpha=0 不显示，与上一轮 `11-about-initial.png` 一致 |
| `64-settings-top-final.png` | Settings 顶部（含玻璃效果开关状态） | **通过**。通用/外观分组与开关布局一致；「玻璃效果」开关 `checked=true`（bounds `[877,1319][1006,1445]`），主题=跟随系统；无回退 |

## D5. 遗留观察项（非本轮 4 处改动引入，如实记录）

- **P2（沿用）宽屏折叠态页面标题不可见**：`53-home-landscape-final.png` 与上一轮 `40` 表现一致（折叠态只有导航胶囊、无页面标题；展开态标题出现）。仍未判缺陷，交 lead 决策。
- **P3（新增观感项）冷启动首帧标签行可能未渲染**：最终 APK 上观察到 2 次 Home 标签行缺失（23:59 升级安装后首次启动；00:05 一次冷启动），但随后 **4+3 次冷启动在 1.2s 内均正常显示「默认/q」两个标签**，`q` 数据始终存在（横屏/竖屏多次 dump 可见），无法稳定复现。判定为首帧 snapshot/topBarBottom 内容就绪时序（页面无加载骨架，标签行直接不占位），不归因 R4 的 4 处改动，记录供后续观察。
- 数据说明：测试期间曾一度观察到第二个标签未显示，后确认**数据未丢失**（`q` 标签在横屏/重启后均正常），R4 diff 中 `data/`、`domain/`、`system/` 零改动，按 lead 要求不改动任何用户数据（未新建/删除标签）。

## D6. 最终 APK 复核结论

- **P1（横屏 start 二次让位）：已修复**，实测折叠态左缘 288→160 / 256→160，右缘 2368 不变，展开态 848，两态位移 688 一致。
- **ApiActivity 不透明主题：达标**（浅/深各验证操作单 + 错误弹层，无透底、无首帧白闪，Glass 弹层背景完整）。
- **删死代码/未用变量：无回归**（全场景正常，crash buffer 0）。
- **3 张回归：全部通过**。
- 遗留：P2（观察项）、P3（新增观感项，首帧标签行时序），均未阻塞验收。

环境已复原：`font_scale=1.0`、`user_rotation=0`、`accelerometer_rotation=1`、`cmd uimode night no`、Glass=开、主题=跟随系统；App 数据（Home 4 应用 + 默认/q 两标签）保持。

---

## D7. 最终交付 APK 冒烟（lead 执行，00:10 构建）

- 最终 Release 由 gradlew :app:assembleRelease 重新构建：Glacier-release.apk 4,287,928 B，
  SHA-256 `AB4B9234A34C608A960CA5ACC1C9A612A953E7817B80313193EB235597AC0F22`。
- 与上面 D1–D6 所测 APK 的差异只有两处**行为等价**改动：ApiActivity 的
  `GlassDialog(backdrop = LocalRootBackdrop.current.takeIf { LocalGlassActive.current })`
  （非玻璃档 provider 本就提供 null）与若干 KDoc/注释。
- 已用 adb 覆盖安装（install -r，数据保留）→ 冷启动 → 截图 `66-final-build-smoke.png`：
  Home 标题/标签行「默认 / q」/4 图标/FAB/底栏正常，`logcat -b crash` 空，无 ANR。
- 该张不在上面的场景表内，仅作为"最终构建可用"的冒烟证据。
