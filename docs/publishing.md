# 发布流程

## 1. 版本号

`app/build.gradle.kts` 里的 `versionCode` / `versionName` 与 `app/src/main/.../BuildConfig` 保持递增。

## 2. 构建

```bash
export JAVA_HOME=/path/to/jdk-21
./gradlew :app:assembleDebug :app:assembleRelease
```

产物：

```
app/build/outputs/apk/debug/Glacier-debug.apk
app/build/outputs/apk/release/Glacier-release.apk
```

Release 签名来自仓库根目录的 `signing.properties`（含 storeFile / storePassword / keyAlias / keyPassword），
该文件与 `keystore/` 已被 `.gitignore` 排除，**不要提交**。丢失密钥将无法覆盖升级已发布的版本。

## 3. 校验

```bash
apksigner verify --print-certs app/build/outputs/apk/release/Glacier-release.apk
sha256sum app/build/outputs/apk/release/Glacier-release.apk
```

## 4. 推送源码

优先直接 `git push`。若本机到 `github.com` 的连接被拦截（`Recv failure: Connection was reset`、
`Failed to connect to github.com:443`，此时 `api.github.com` 往往仍可用），改用 Git Data API：

```bash
python tools/push.py . Ayang233/Glacier /path/to/gh      # 需要 gh
python tools/push_api.py . Ayang233/Glacier main         # 无 gh：直接 urllib + token
```

`tools/push_api.py` 是 1.4.0 起补的免 gh 版本：token 取 `GITHUB_TOKEN` 或 git credential
manager 里记住的 github.com 凭据，把本地提交的 author/committer（含 ISO 时间）原样带上，
**远端生成的提交 SHA 与本地一致**，推完不需要再 `git fetch` 对齐引用。

两个脚本都用 Git Data API 的 `base_tree`，**只上传相对远端最新树发生变化的文件**，
一次 4 文件的文档提交通常 3~5 秒完成。`tools/publish.py` 是早期的全量版（每提交重传整棵树），
只在需要从空仓库初始化或修复异常状态时使用。

脚本按 tree sha 判断哪些提交已存在于远端，因此本地与远端 SHA 不同也能增量推送；
首次经 API 推送会重建提交 SHA（内容一致），之后 `git fetch` 再决定是否对齐本地引用。

## 5. 发 Release

标准流程（GitHub CLI）：

```bash
gh release create v1.1.0 \
  app/build/outputs/apk/release/Glacier-release.apk \
  SHA256SUMS.txt \
  --title "Glacier 1.1.0" \
  --notes-file docs/release-notes/1.1.0.md \
  --target main
```

**本机记录（1.1.0 发布时）**：`tools/gh/bin/gh.exe`（2.105.0）执行 `gh release create` 曾在本地校验阶段
报 ``no matches found for `v1.1.0` ``，因此当时改用 REST API 直接发布。**1.2.0 发布时 `gh release create`
已可直接使用**，指定 `--target <远端提交 SHA>` 即可（tag 由 gh 创建），无需再手写 REST：

```bash
gh release create v1.2.0 \
  app/build/outputs/apk/release/Glacier-release.apk \
  SHA256SUMS.txt \
  --title "Glacier 1.2.0" \
  --notes-file docs/release-notes/1.2.0.md \
  --target <远端 main 的提交 SHA>
```

本机没有 gh 时（1.4.0 / 1.5.0 发布都是这种状态），用 `tools/release_api.py` 走同一套 REST：

```bash
python tools/release_api.py v1.5.0 <远端 main 的提交 SHA> \
  docs/release-notes/1.5.0.md \
  app/build/outputs/apk/release/Glacier-release.apk SHA256SUMS.txt
```

脚本只碰 `api.github.com` 与 `uploads.github.com`，token 取 `GITHUB_TOKEN` 或 git credential manager
里存好的 github.com 凭据。它打印的 asset `digest` 应与本地 `sha256sum` 一致。

下面的手工脚本版本保留作为备选（`api.github.com` 与 `uploads.github.com` 均放行）：

```powershell
$tok = (gh auth token)
$hdr = @{Authorization="token $tok"; 'User-Agent'='glacier-release'; 'Accept'='application/vnd.github+json'}
$notes = Get-Content docs/release-notes/1.1.0.md -Raw
$body = @{ tag_name='v1.1.0'; target_commitish='main'; name='Glacier 1.1.0'; body=$notes; draft=$false; prerelease=$false } | ConvertTo-Json
$rel = Invoke-RestMethod -Uri 'https://api.github.com/repos/Ayang233/Glacier/releases' -Method Post -Headers $hdr `
  -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))

$base = "https://uploads.github.com/repos/Ayang233/Glacier/releases/$($rel.id)/assets"
Invoke-RestMethod -Uri "$base?name=Glacier-release.apk" -Method Post -Headers $hdr `
  -ContentType 'application/vnd.android.package-archive' -InFile app/build/outputs/apk/release/Glacier-release.apk
Invoke-RestMethod -Uri "$base?name=SHA256SUMS.txt" -Method Post -Headers $hdr `
  -ContentType 'text/plain' -InFile SHA256SUMS.txt
```

tag 由 `target_commitish = main` 自动创建，指向远端 main 的最新提交；
发布后必须核对 tag/主分支 SHA、附件大小与下载文件的 SHA-256（见第 6 节）。

## 6. 发布后自查

- Release 页面附件大小与本地一致；
- `SHA256SUMS.txt` 与 APK 匹配；
- 在干净设备上 `adb install` 一次（覆盖安装验证签名链路）。

## 7. 发布记录

| 版本 | 日期 | 远端提交 | Release | 说明 |
|---|---|---|---|---|
| 1.0.0 | 2026-11-19 | 8ef81e9 | v1.0.0 | 首个公开版本 |
| 1.1.0 | 2026-11-19 | 6efc12 | v1.1.0 | MIUIX OS4 结构整改 |
| 1.2.0 | 2026-11-20 | c8d21be | v1.2.0 | R4 体验整改：长按叠层/标签栏/About 动效/滚动与转场/文案 |
| 1.3.0 | 2026-11-20 | 763a269 | v1.3.0 | MIUIX 基线升级 v0.9.5-beta02（渐进模糊/GlassTextField/标题避让） |
| 1.4.0 | 2026-11-22 | 9b8caa9 | v1.4.0 | R6 界面底子重做（官方 inset 契约）+ MIUIX 基线 `44f1d315` |
| 1.5.0 | 2026-11-22 | d45e5a3 | v1.5.0 | R7 交互整改（底栏归属 / 应用页点选 / 动作动画）+ R8 宽屏间距与自定值清查 |
| 1.6.0 | 2026-11-23 | 37d8c9a | v1.6.0 | R9 按压反馈与选中态 + R10 主页/应用页菜单分工 |
| 1.6.1 | 2026-11-25 | c631ef4 | v1.6.1 | R11 基线对齐 `v0.9.5-beta04` + 自造轮子清查 + 冻结交互；R12 长按菜单按选中状态分流 |
| 1.6.2 | 2026-11-25 | 325b523 | v1.6.2 | R13 基线升到 MIUIX main `542463a0` + 长按菜单换官方锚点浮层；R14 浮层宽度/首次动画/横屏侧栏让位收尾 |
| 1.6.3 | 2026-11-25 | a33ea0f | v1.6.3 | R15 底部弹层避开屏幕大 R 角与隐藏手势提示线；R16 设置顺序/预测性返回开关/对称雪花图标/隐藏应用名称；R17 冻结按钮雪花 + 设置页分级；R18 冷启动首帧显示主页应用 |

1.2.0 发布细节：
- 源码经 Git Data API 推送（github.com 直连被拦），远端 main 树与本地 HEAD 树一致（1620cc3c…）；
- Release 由 gh release create --target c8d21be… 创建，附件 Glacier-release.apk
  （4,287,936 B，sha256:66fea1c4…07b1）与 SHA256SUMS.txt；
- 仓库 Description 与 topics 同步更新；
- 发布前做过覆盖升级测试：线上 1.1.0 → 1.2.0，首页数据保留（见
  docs/verification/miuix-os4-r4/README.md D8）；
- 历史 Release（1.0.0 / 1.1.0）正文未回溯改写。

1.3.0 发布细节：
- 源码经 Git Data API 推送（github.com 直连不可用），远端 main = 763a269（API 重写了提交 SHA），
  远端树与本地 HEAD 树一致（bd92ca71…）；
- Release 由 `gh release create --target 763a269…` 创建，附件 Glacier-release.apk
  （4,369,372 B，sha256:bee840fd…75eb）与 SHA256SUMS.txt；
- 基线见仓库根目录 `MIUIX_BASELINE.md`（MIUIX `66248bac` / `v0.9.5-beta02`）；
- 发布前做过覆盖升级测试：线上 1.2.0 → 1.3.0，同签名覆盖安装成功、版本号 1.3.0、数据保留；
- 本轮新增 `.github` 之外的 CI 方案记录在 `MIUIX_BASELINE.md` §9.1（token 缺 `workflow` scope，
  含 workflow 的树会被 Git Data API 拒绝为 404）。

1.4.0 发布细节：
- 源码经 Git Data API 推送（`git push` 仍报 `RPC failed; curl 55 Send failure: Connection was reset`）。
  本轮新增免 gh 的 `tools/push_api.py`，两个提交的 **SHA 与本地完全一致**
  （`c89212a` / `9b8caa9`），远端 main = `9b8caa9`，无需事后对齐引用；
- 本机没有 `gh`，Release 由 REST 创建：`POST /releases`（`tag_name=v1.4.0`、
  `target_commitish=9b8caa9`）+ `uploads.github.com` 上传附件；
  tag `v1.4.0` → `9b8caa9`；
- 附件：`Glacier-release.apk` 4,418,528 B（sha256 `38a613da…e53d`）、`SHA256SUMS.txt`；
  API 返回的 asset `digest` 与本地 `sha256sum` 一致；
- 覆盖升级实测：从线上 v1.3.0 的 release APK（`bee840fd…75eb`）`adb install -r` 到 1.4.0，
  同签名覆盖成功，`versionCode 4 → 5`、`versionName 1.3.0 → 1.4.0`，启动无 crash；
- 基线见 `MIUIX_BASELINE.md`（MIUIX `44f1d315` / `v0.9.5-beta03` + 18）。

1.5.0 发布细节：
- 源码用 `tools/push_api.py` 推送，6 个提交的 SHA 与本地完全一致，远端 main = `d45e5a3`；
- Release 由 `tools/release_api.py` 创建（本机仍无 gh）：`POST /releases`（`tag_name=v1.5.0`、
  `target_commitish=d45e5a3`）+ `uploads.github.com` 上传附件，tag `v1.5.0` → `d45e5a3`；
- 附件：`Glacier-release.apk` 4,434,912 B（sha256 `6b8b90c7…e13c`）、`SHA256SUMS.txt`（172 B）；
  API 返回的 asset `digest` 与本地一致；
- 签名证书未变（`CN=Glacier, OU=Release, O=Ayang, C=CN`，证书 sha256 `46b3f848…82cf4`）；
- 覆盖升级实测：从已装的 1.4.0 release 包 `adb install -r` 到 1.5.0，
  同签名覆盖成功，`versionCode 5 → 6`、`versionName 1.4.0 → 1.5.0`，启动无 crash；
- README 同步重写了交互说明与 Glass 结构（底栏现在装在一级页里）。

1.6.0 发布细节：
- 源码用 `tools/push_api.py` 推送，6 个提交的 SHA 与本地完全一致（R9 / R10 三轮交互改动），
  远端 main = `37d8c9a`；
- Release 由 `tools/release_api.py` 创建：tag `v1.6.0` → `37d8c9a`；
- 附件：`Glacier-release.apk` 4,452,792 B（sha256 `f7560506…51ce`）、`SHA256SUMS.txt`（86 B）；
  API 返回的 asset `digest` 与本地 `sha256sum` 一致；
- 签名证书未变（`CN=Glacier, OU=Release, O=Ayang, C=CN`，证书 sha256 `46b3f848…82cf4`）；
- 覆盖升级实测：模拟器上从已装的 1.5.0 release 包 `adb install -r` 到 1.6.0，
  同签名覆盖成功，`versionCode 6 → 7`、`versionName 1.5.0 → 1.6.0`，启动无 crash；
- README 重写，并补了 `docs/screenshots/` 下 6 张 1.6.0 实拍（主页 / 应用页 / 多选 / 两个菜单 / 关于）。

1.6.1 发布细节：
- 源码用 `tools/push_api.py` 推送（`git ls-remote` 仍报 `schannel: server closed abruptly`），
  3 个提交的 SHA 与本地完全一致，远端 main = `c631ef4`；
- Release 由 `tools/release_api.py` 创建：tag `v1.6.1` → `c631ef4`；
- 附件：`Glacier-release.apk` 4,520,712 B（sha256 `b377aecc…6b9b`）、`SHA256SUMS.txt`（86 B）；
  API 返回的 asset `digest` 与本地 `sha256sum` 一致；
- 签名证书未变（`CN=Glacier, OU=Release, O=Ayang, C=CN`，证书 sha256 `46b3f848…82cf4`）；
- 覆盖升级实测：模拟器上从已装的 1.6.0 release 包 `adb install -r` 到 1.6.1，
  同签名覆盖成功，`versionCode 7 → 8`、`versionName 1.6.0 → 1.6.1`，启动无 crash；
- 已知未交付项：锚点浮层（`GlassTransformPopup`）在无硬件 `RenderEffect` 支持的环境会崩在
  RenderThread，本版未启用，长按菜单仍是底部弹层（见 `docs/audit/miuix-os4-r12-audit.md` §3）。

1.6.2 发布细节：
- 源码 `git push` 直连成功（github.com:443 连接不稳，`ls-remote` 重试到第 5 次才通；push 首次即成功），
  3 个提交（`ecd2bfe` R13 / `ef48b42` R14 / `325b523` 发版）推上去，远端 main = `325b523`；
- Release 由 `gh release create v1.6.2 --target 325b5239…` 创建：tag `v1.6.2` → `325b523`，
  与远端 main 一致；注意 `--target` 传短 SHA 会被 API 拒为 `target_commitish is invalid`，必须给完整 SHA；
- 附件：`Glacier-release.apk` 4,569,869 B（sha256 `238d310d…ddbc`）、`SHA256SUMS.txt`（86 B）；
  重新下载线上附件核对 sha256 一致，API 返回的 asset `digest` 也一致；
- 签名证书未变（`CN=Glacier, OU=Release, O=Ayang, C=CN`，证书 sha256 `46b3f848…82cf4`）；
- 覆盖升级实测：模拟器上从已装的 1.6.1 release 包 `adb install -r` 到 1.6.2，
  同签名覆盖成功，`versionCode 8 → 9`、`versionName 1.6.1 → 1.6.2`，启动无 crash；
- 本轮首次启用 `GlassTransformPopup`：需要硬件 `RenderEffect`，验证在接宿主 GPU 的模拟器上完成，
  软渲染环境仍会崩（原因见 `docs/audit/miuix-os4-r13-audit.md`、`docs/audit/miuix-os4-r14-audit.md`）。

1.6.3 发布细节：
- 源码 `git push` 直连成功（github.com:443 前 4 次连接被重置，第 6 次推上），
  远端 main = `a33ea0f`（包含 R15–R18 与发版提交）；
- Release 由 `gh release create v1.6.3 <apk> SHA256SUMS.txt --target a33ea0fb25ff…` 创建：
  tag `v1.6.3` → `a33ea0f`，与远端 main 一致（`--target` 仍必须给完整 SHA）；
- 附件：`Glacier-release.apk` 4,575,177 B（sha256 `9f9bdff5…e51e`）、`SHA256SUMS.txt`（86 B）；
  重新下载线上附件核对 sha256 一致，API 返回的 asset `digest` 也一致；
- 签名证书未变（`CN=Glacier, OU=Release, O=Ayang, C=CN`，证书 sha256 `46b3f848…82cf4`）；
- 覆盖升级实测：模拟器上从已装的 1.6.2（versionCode 9）`adb install -r` 到 1.6.3（versionCode 10），
  同签名覆盖成功、启动无 crash；冷启动首帧直接显示主页应用（R18），冻结 FAB 为雪花（R17）；
- 本轮重点：隐藏手势提示线的设备底部弹层不再被屏幕圆角裁掉（R15）、设置页改为一级入口 + 二级页（R17）、
  预测性返回开关与隐藏应用名称（R16）。

1.7.0 发布细节：
- 本地提交 `6dc75cc`（30 files，含 R21 审计、验收截图、1.7.0 release notes）；
  直连 `git push` 仍被拦（github.com:443 连不上），改用 `tools/push_api.py` 增量推送，
  远端 main = `6dc75cc`，与本地 HEAD 一致（脚本回显 "sha 与本地一致"）；
- Release 由 `tools/release_api.py v1.7.0 6dc75cc… docs/release-notes/1.7.0.md <apk> SHA256SUMS.txt` 创建：
  tag `v1.7.0` → `6dc75cc`，draft=false、prerelease=false；
- 附件：`Glacier-release.apk` 4,604,233 B（sha256 `61831671…d6d4`）、`SHA256SUMS.txt`（87 B）；
- 覆盖升级实测：模拟器上从 1.6.4（versionCode 11）`adb install -r` 到 1.7.0（versionCode 12），
  同签名覆盖成功、启动无 crash；主页/应用页/关于页/预测性返回/冻结启动均通过；
- 本轮重点：MIUIX 基线升到 `e7c108ee`（AGP 9.5.3）、底部弹层显式开启 `progressiveBlur`、
  标签 >4 用 `GlassScrollableTabRow`；冻结应用点按立即启动；应用页删除「· 不在主页」；
  预测性返回照 InstallerX 重做（manifest 常开 + 只切 predictivePop，删除 HiddenApiBypass/recreate）；
  关于页去小标题与重复行。
- 发布脚本修复：`release_api.py` 在 Windows 下把路径反斜杠当成文件名，附件被传成
  `app.build.outputs.apk.release.Glacier-release.apk`；已删除该附件、按正确文件名重传，
  并在脚本里统一 `path.replace("\\", "/")` 取 basename。

1.6.4 发布细节：
- 源码 `git push` 直连再次被拦（github.com:443 连续 8 次 timeout / reset），改用 `tools/push_api.py`
  增量推送：R19/R20 修复提交与发版提交共 3 个，远端 main = `4aec46b`，与本地 HEAD 一致；
- Release 由 `gh release create v1.6.4 <apk> SHA256SUMS.txt --target 4aec46bef561…` 创建：
  tag `v1.6.4` → `4aec46b`（`--target` 仍必须给完整 SHA）；
- 附件：`Glacier-release.apk` 4,575,173 B（sha256 `70ac5fe9…f6e7`）、`SHA256SUMS.txt`（85 B）；
  重新下载线上附件核对 sha256 一致，API 返回的 asset `digest` 也一致；
- 签名证书未变（`CN=Glacier, OU=Release, O=Ayang, C=CN`，证书 sha256 `46b3f848…82cf4`）；
- 覆盖升级实测：模拟器上从已装的 1.6.3（versionCode 10）`adb install -r` 到 1.6.4（versionCode 11），
  同签名覆盖成功、启动无 crash、主页应用与图标正常；
- 本轮重点：冻结状态随首帧一起到位（R19）、预测性返回开关不再闪（R20，真机录屏逐帧定位到
  「重建后首帧 WindowInsets 未下发」）、设置 dependent-option 行改用官方 enter/exit（R20）。
- 待真机确认：R20 的首帧闪烁修复在模拟器上复现不出（模拟器首帧 inset 已就绪）。
