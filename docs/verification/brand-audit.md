# 品牌与工程身份审计

## 1. 结论

- 正常界面中 Hail 品牌：**0 处**。
- 工程身份（包名/命名空间/组件/权限/URI/任务名/渠道/快捷方式）全部为 Glacier 自有：
  **0 处** `com.aistra`。
- 仅剩的 Hail 字样全部属于许可证义务或代码注释，见第 3 节逐条说明。

## 2. 工程身份清单

| 项 | 值 |
|---|---|
| applicationId / namespace / Kotlin 根包 | `com.ayang.glacier` |
| 桌面名称 / Launcher label | `@string/app_name` = Glacier |
| Adaptive Icon | `mipmap-anydpi-v26/ic_launcher.xml`（自绘雪花前景 + 冰蓝渐变底） |
| 启动页 / 窗口主题 | `Theme.Glacier` / `Theme.Glacier.Translucent`（无 Hail 主题残留） |
| Application | `com.ayang.glacier.GlacierApplication` |
| MainActivity | `.ui.MainActivity`，`singleTask` |
| ApiActivity | `.feature.api.ApiActivity`，`singleInstance`，`excludeFromRecents` |
| DeviceAdminReceiver | `.platform.admin.GlacierDeviceAdminReceiver` |
| NotificationListener / 前台服务 | `.platform.auto.AutoFreezeService` |
| QS Tile | `.platform.tile.FreezeTileService`，label = Glacier |
| Shizuku Provider Authority | `${applicationId}.shizuku` |
| 自定义权限 | 无自建权限；仅声明 Island 的两条外部权限 |
| 通知渠道 ID | `glacier.auto_freeze` |
| WorkManager 唯一任务名 | `glacier.auto_freeze` / `glacier.deferred.<pkg>` |
| 快捷方式 ID | `glacier.launch.<pkg>` / `glacier.pin.<pkg>` / `glacier.action` |
| SharedPreferences 文件 | `glacier_preferences` |
| 数据目录 | `files/v1/apps.json`、`files/v1/tags.json`（Glacier 私有目录） |
| 外部 API Action | `${applicationId}.action.LAUNCH` 等 11 个 |
| Deep link scheme | `glacier://`（host 与动作一一对应） |
| 构建产物 | `Glacier-debug.apk` / `Glacier-release.apk` |
| Gradle 工程名 | `Glacier` |

## 3. 剩余的 Hail / aistra 字样

| 位置 | 类型 | 是否可删除 |
|---|---|---|
| `data/GlacierDataStore.kt` 注释 | 说明数据格式来源 | 可留（不面向用户） |
| `data/GlacierPreferences.kt` 注释 | 说明偏好文件独立 | 可留 |
| `system/RootBackend.kt` 注释 | 说明 shell 语义来源 | 可留 |
| `system/FreezeController.kt` 注释 | 说明默认模式语义来源 | 可留 |
| `feature/about/AboutScreen.kt` 注释 | 说明品牌区不复用 Hail 素材 | 可留 |
| `feature/about/LicensesScreen.kt` 组件条目 | GPL 归属声明 | **不可删**（GPL-3.0 §5） |
| `res/values*/strings.xml` → `about_license_notice` | About 页"源码参考"分区的许可证说明 | **不可删**（GPL-3.0 §5） |

`about_license_notice` 位于 About 页的「源码参考」分区，不在品牌展示区；
品牌区只有 Glacier 图标、名称、副标题。这是许可证义务与"品牌统一"之间唯一合理的落点。

## 4. 扫描命令

```bash
# 代码与资源中的品牌残留
grep -rniE "hail|aistra" app/src --include=*.kt --include=*.xml

# 旧包名残留
grep -rn "com.aistra" app/src

# 运行时身份
adb shell pm list packages | grep glacier   # com.ayang.glacier / com.ayang.glacier.debug
```

## 5. 许可证

- `LICENSE`：GPL-3.0 全文（35 823 字节），随源码分发。
- `assets/LICENSE.txt`：同一份全文，装进 APK，由「开源许可证」页渲染。
- `NOTICE.md`：衍生关系、第三方组件、刻意排除项（material3 / pinyin4j）与理由。
- Hail 的版权声明、许可证、作者信息未被删除或改写。
