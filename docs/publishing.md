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

下面的 REST 方式保留作为备选（`api.github.com` 与 `uploads.github.com` 均放行）：

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
