# Glass 契约：三条红线与 Glacier 的落点

本文件是 Glacier 的 Glass 结构约束。任何新增玻璃组件都必须先在这里找到它属于哪一类，
并在"落点"一栏写明它采样哪一层。

## 红线

1. **消费者不能是录制层的后代。**
   录制层在绘制时会把子树的绘制命令录进去；子节点再去采样这个 layer 就是递归，
   Android 侧表现是 `libhwui` 的 `prepareTreeImpl` 栈溢出、`Fatal signal 11`。

2. **采样源必须在消费者的整个绘制区域里都有像素。**
   页面录制层必须"先 `drawRect(页面真实底色)`，再 `drawContent()`"；
   否则空白区域是透明的，模糊透明等于透明，玻璃上会出现脏斑。

3. **尺寸与坐标只在量测/布局/绘制阶段读取。**
   逐帧动画值（如侧栏 morph 进度）不能在组合期解包成 `Dp` 再 `padding`，
   否则 300ms 展开过程中整页重组。

## Glacier 的树

```
Scaffold(containerColor = Transparent, contentWindowInsets = 0)     ← 弹层宿主
└ Box(fillMaxSize)
   ├ Box(fillMaxSize).layerBackdrop(rootBackdrop)                   ★R 满屏录制层
   │  │   (rootBackdrop 先 drawRect(surface))
   │  └ NavDisplay(padding(start = railInset))                      ← 让位只在内容层
   │     ├ Route.Main → HorizontalPager
   │     │   └ 页面 PageFrame
   │     │      Scaffold(topBar = GlassTopAppBar(pageBackdrop))     ← 顶栏与 P 同级
   │     │      └ content
   │     │         └ Box(fillMaxSize).layerBackdrop(pageBackdrop)   ★P 页面录制层
   │     │            └ 列表 / 网格
   │     └ Route.About / Route.Licenses（同上）
   ├ GlassNavigationBar(backdrop = rootBackdrop)                    ← R 的兄弟（窄屏）
   └ GlassNavigationRail(backdrop = rootBackdrop)                   ← R 的兄弟（宽屏）
```

弹层（`GlassDialog` / `GlassBottomSheet` / `GlassDropdownPreference`）通过
`renderInRootScaffold = true` 由最外层 Scaffold 的 `MiuixPopupHost` 绘制，
因此天然在 R 之外，采样 R 就一定是兄弟关系。

## 每个消费者的落点

| 消费者 | 位置 | 采样源 | 兄弟关系 |
|---|---|---|---|
| `GlassTopAppBar` | 页面 `Scaffold.topBar` | P | 与 P 同父（Scaffold 的两个子槽） |
| `GlassFloatingActionButton` | 页面 `Scaffold.floatingActionButton` | P | 与 P 同父 |
| `GlassNavigationBar` | Shell `Box` 的第二个子节点 | R | 与 R 同父 |
| `GlassNavigationRail` | Shell `Box` 的第二个子节点 | R | 与 R 同父 |
| `GlassDialog` / `GlassBottomSheet` | 页面 `overlay` 槽（实际渲染在根 Scaffold） | R | 在 R 之外 |
| `GlassSearchOverlay` | 页面 `overlay` 槽 | 自建内部录制层 | 组件自包含，不采外部层 |
| `GlassIconButton`（顶栏内） | 顶栏子树 | 继承顶栏 | 由库的 `LocalGlassTopAppBarContext` 处理 |

## 两个已知陷阱（本项目实际踩过）

### 1. 页签不能塞进正文列表

第一版把 `GlassTabRow` 放在 `LazyVerticalGrid` 的第一个 item 里，采样 `pageBackdrop`。
它自己就在 P 的子树里 → 首次渲染即 `prepareTreeImpl` 递归崩溃。

官方 example 对这一点的注释是决定性的：

```kotlin
// List tabs sample the wallpaper to avoid sampling their own recording layer.
val tabBackdrop = if (tabsInTopBar) backdrop else wallpaperBackdrop
```

Glacier 的取舍：**标签栏改用官方非玻璃 `TabRow`，放在正文列表顶部**。
理由：页签是导航控件，"浮在内容之上且需要看见背后内容"这个判据只满足一半；
而放进顶栏 `bottomContent` 虽然结构合法，但在本版 MIUIX 上实测该槽的高度测量不稳定
（`bottomContent` 折叠为 0）。改用普通 `TabRow` 既没有采样问题，也符合 OS4 设置页的既有范式。

### 2. 让位不能在组合期解包

第一版把 `railStartInset` 写成 `Modifier.layout` + `hasBoundedWidth` 判断，
在 `minWidth == maxWidth` 的固定约束下会抛出 `maxWidth must be >= than minWidth`。
最终把让位收到 Shell 层：`NavDisplay(modifier = fillMaxSize().padding(start = railInsetDp))`，
`railInsetDp` 来自"是否展开"这个布尔状态而不是逐帧 `progress`。
好处是录制层（外层 Box）保持满宽，符合"录制层不跟着内容让位收窄"的要求。

## 回退策略

- **唯一决策点**：`LocalGlassActive = 用户开关 && isRuntimeShaderSupported()`。
  页面里不再出现第二个 `isRuntimeShaderSupported()` 判断。
- **每个组件都能 `backdrop = null` 工作**：官方组件自身走不透明回退；
  `GlassTabRow` 这种"没 backdrop 就没材质"的情况，Glacier 干脆不用它做导航。
- **模拟器**：`Platform.isEmulator` 为真时顶栏关闭 `progressiveBlur`，
  走库自带的 band 渐变，不自绘替代视觉。
