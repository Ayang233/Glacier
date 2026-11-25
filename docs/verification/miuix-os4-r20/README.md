# R20 验收：预测性返回开关不闪 + dependent-option 官方动画

模拟器 1080×2400、420dpi、Android 16（API 36）、宿主 GPU；「闪一下」的原始证据来自用户真机录屏
（1200×2608）。

## 截图

| 文件 | 内容 |
|---|---|
| `frames/01-before-normal.png` | 用户录屏里的正常帧（真机 1200×2608，缩放 60%） |
| `frames/02-before-flash.png` | 用户录屏里的**闪帧**：整页上移一个状态栏高度（144px） |
| `frames/03-before-flash-diff.png` | 正常帧与闪帧的差值图：同一页所有行出现两份、垂直错开 144px |
| `frames/04-label-row-collapse.png` | 「隐藏应用名称」打开时，「主页标签字号」行按官方 `fadeOut() + shrinkVertically()` 收起（中间帧，滑块被裁切） |
| `frames/05-label-row-expand.png` | 关闭后按官方 `fadeIn() + expandVertically()` 展开（中间帧） |
| `frames/06-after-predictive-toggle.png` | 预测性返回开关切换并重建后：页面停在被切换的那一页（主题设置），没有掉回首页 |
| `frames/07-preview-on.png` | 开关打开：慢滑返回，页面跟手右移、后页压暗（预测预览生效） |
| `frames/08-after-predictive-off.png` | 开关关闭并重建后：页面仍停在主题设置 |
| `frames/09-preview-off.png` | 开关关闭：慢滑时页面不动，只有系统返回指示箭头；松手后仍播 MIUIX pop 转场 |
| `frames/10-upgrade-1.6.4-home.png` | 1.6.3 → 1.6.4 覆盖升级后冷启动：主页应用与图标正常 |

## 复现步骤

```bash
# 1) 预测性返回开关（真机上观察闪帧；模拟器复现不出，原因见审计）
#    设置 → 主题设置 → 预测性返回动画，来回切换

# 2) dependent-option 动画：逐帧截屏
adb shell 'rm -f /sdcard/exp_*.png; (sleep 0.2; input tap <隐藏应用名称 开关坐标>) & \
  for i in 1 2 3 4 5 6 7 8 9 10; do screencap -p /sdcard/exp_$i.png; done; wait'

# 3) 跟手预览：滑动过程中截一帧
adb shell '(input swipe 20 1200 700 1200 1600) & sleep 0.9; screencap -p /sdcard/prev.png; wait'
```

## 说明

- 闪帧分析：5 次开关、每次恰好 1 帧异常；位移恒为 144px（该机状态栏高度），因此判定为
  「重建后第一帧 WindowInsets 为 0」。
- 模拟器实测重建后第一次 draw 的 `systemBars.top` 已是 128px（临时探针，已移除），
  所以这一帧在模拟器上看不到差异；修复以真机录屏为证据，仍需真机确认。
