# R18 验收：冷启动首帧就有主页应用

模拟器 1080×2400、420dpi、Android 16（API 36）、宿主 GPU。

## 截图

| 文件 | 内容 |
|---|---|
| `01-cold-start-first-frame.png` | force-stop 后冷启动，第一帧 UI：主页直接显示「默认 · 2 个应用」和两枚图标，不再是空状态（处于淡入动画中） |
| `02-cold-start-stable.png` | 冷启动稳定后：图标、标签、FAB 全部就绪 |

## 复现步骤

```bash
adb shell am force-stop com.ayang.glacier
adb shell "am start -n com.ayang.glacier/.ui.MainActivity >/dev/null; sleep 2.2; screencap -p /sdcard/first.png"
```

对比修复前：同样的时机截到的是「主页还没有应用。去「应用」页添加。」的空状态。
