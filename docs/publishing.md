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

```bash
gh release create v1.0.0 \
  app/build/outputs/apk/release/Glacier-release.apk \
  SHA256SUMS.txt \
  --title "Glacier 1.0.0" \
  --notes-file docs/release-notes/1.0.0.md \
  --target main
```

## 6. 发布后自查

- Release 页面附件大小与本地一致；
- `SHA256SUMS.txt` 与 APK 匹配；
- 在干净设备上 `adb install` 一次（覆盖安装验证签名链路）。
