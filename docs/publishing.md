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

本机 `github.com` 被拦截，用 Git Data API：

```bash
python tools/publish.py . Ayang233/Glacier /path/to/gh
```

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

**本机实测限制（1.1.0 发布时遇到）**：`tools/gh/bin/gh.exe`（2.105.0）执行
`gh release create` 会在本地校验阶段报 ``no matches found for `v1.1.0` ``，与网络无关；
`gh api` / `gh release list` 正常。因此本机改用 REST API 直接发布（`api.github.com` 与
`uploads.github.com` 均放行）：

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
