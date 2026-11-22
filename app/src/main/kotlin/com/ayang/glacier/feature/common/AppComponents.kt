package com.ayang.glacier.feature.common

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayang.glacier.GlacierApplication
import com.ayang.glacier.R
import com.ayang.glacier.platform.rememberAppIcon
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassMotion
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleClip
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** MIUIX 图标的圆角比例：Mishka / InstallerX 都用 16/72 ≈ 0.22 的圆角矩形。 */
const val AppIconCornerRatio = 0.22f

/**
 * 应用图标。
 *
 * 图标来源、去色、自适应合成由设置决定；这里只负责轮廓：
 * 圆角矩形（官方 squircle，shader 不可用时自动退回 RoundedCornerShape），
 * 不再使用圆形裁切，也不改第三方图标本身的颜色。
 *
 * 结构是「固定尺寸 Box + 图标铺满 + 叠加描边」：
 * - 图标永远等于 `size`，选中与否都不参与布局，按住/选中不会让图标缩小或网格跳动；
 * - 选中环用官方的延迟读取版 [squircleBorder]，半径与图标裁切半径相同（描边外缘与图标轮廓重合），
 *   宽度 0→2dp 用 [animateDpAsState] 过渡，选中态不会突然出现一圈硬边。
 *
 * 旧实现把 `squircleBorder(...) + padding(2.dp)` 放在 `size` 内层，选中时图标内容被挤到 52dp，
 * 而描边半径同时从 12.32 变 14.32 → 选中/取消选中产生 4dp 跳变且描边与轮廓不平行。
 */
@Composable
fun AppIconView(
    packageName: String,
    size: Dp,
    frozen: Boolean,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val context = LocalContext.current
    val preferences = (context.applicationContext as GlacierApplication).preferences
    // 这三项设置都是直接读 SharedPreferences 的普通属性，不是快照状态；
    // 订阅 revision 并作为图标重载 key，设置页改完返回后图标才会立即重绘。
    val iconRevision = preferences.revision.intValue
    val bitmap = rememberAppIcon(
        packageName = packageName,
        size = size,
        grayscale = frozen && preferences.grayscaleFrozenIcons,
        iconPack = preferences.iconPack,
        synthesizeAdaptive = preferences.synthesizeAdaptiveIcons,
        revision = iconRevision,
    )
    val radius = size * AppIconCornerRatio
    // 用官方 GlassMotion 的物理曲线，而不是 Compose 默认 spring：选中环与 MIUIX
    // 分段轨道指示器共用 0.95/0.35 的 Folme 弹簧（GlassMotion.default）。
    val ringWidth by animateDpAsState(
        targetValue = if (selected) 2.dp else 0.dp,
        animationSpec = GlassMotion.default(),
        label = "appIconSelectionRing",
    )
    val ringColor = MiuixTheme.colorScheme.primary
    Box(modifier = modifier.size(size)) {
        if (bitmap != null) {
            Icon(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .squircleClip(radius),
                // 应用图标必须保持原色，不能被 LocalContentColor 染色。
                tint = Color.Unspecified,
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .squircleBackground(MiuixTheme.colorScheme.surfaceContainerHigh, radius),
            )
        }
        // 描边叠在图标之上、不占布局：`squircleBorder` 把 2dp 描边内缩半个线宽绘制，
        // 所以外缘正好落在与 `squircleClip(radius)` 相同的轮廓上。
        Box(
            Modifier
                .matchParentSize()
                .squircleBorder(
                    width = { ringWidth },
                    color = { ringColor },
                    cornerRadius = radius,
                ),
        )
    }
}

/**
 * 应用名称的统一文字层级（§9 一致性：Home 网格 / Apps 列表 / 搜索结果）。
 *
 * 列表行由官方 [BasicComponent] 排版（`headline1`=17sp + Medium，见 `Component.kt:89-95`）；
 * 网格标签沿用同一字重，只把字号降到 `footnote1`（13sp，可被「主页图标字号」设置覆盖），
 * 于是三处只在尺寸上有层级差异，字重 / 颜色角色 / 单行省略保持一致。
 */
@Composable
fun AppLabelTextStyle(fontSize: Float): TextStyle =
    MiuixTheme.textStyles.footnote1.copy(
        fontSize = fontSize.sp,
        fontWeight = FontWeight.Medium,
    )

/**
 * 底部操作单里的一行。
 *
 * 直接是官方 [BasicComponent]：56dp 最小行高、16dp 行内边距、8dp 图文间距都由组件自己决定，
 * 与设置页的 Preference 行完全同构，不再手写 Row + clickable + padding。
 */
@Composable
fun SheetActionRow(
    icon: ImageVector,
    label: String,
    destructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val color = if (destructive) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurface
    // 禁用态必须同时弱化图标：`BasicComponent` 只把 title 换成 disabledColor，
    // startAction 是自己传进去的子树，不跟着变就会留下一个「亮图标 + 灰文字」的行（§7.4）。
    val iconColor = if (enabled) color else MiuixTheme.colorScheme.disabledOnSecondaryVariant
    BasicComponent(
        title = label,
        titleColor = BasicComponentDefaults.titleColor(
            color = color,
            disabledColor = MiuixTheme.colorScheme.disabledOnSecondaryVariant,
        ),
        startAction = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = iconColor,
            )
        },
        enabled = enabled,
        onClick = onClick,
    )
}

/**
 * 应用列表 / 搜索结果里的一行（图标 + 名称 + 副标题 + 冻结徽标），同样走官方 [BasicComponent]。
 *
 * 行高（最小 56dp）、内边距（16dp）、图文间距（8dp）全部用 [BasicComponent] 默认值（规范 2.2 / 第 3 节）；
 * 应用图标 40dp 是规范第 3 节登记的"应用图标"token；冻结徽标按规范放 `endActions`。
 * 点击语义：列表行把 `onClick` 交给行内的 `BasicComponent`，外层 `Card` 不注册点击
 * （官方 `IconPage.kt:221-244`）；这样只有一层 indication，且会被 `Card` 的 squircle 表面裁成圆角。
 *
 * [selected] 与 Home 网格同一个选中语言（[AppIconView] 的 squircle 描边），
 * 供列表页将来接多选时直接复用；默认 false，对现有调用点零影响。
 */
@Composable
fun AppRow(
    packageName: String,
    label: String,
    summary: String?,
    frozen: Boolean,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
) {
    BasicComponent(
        title = label,
        summary = summary?.takeIf { it.isNotBlank() },
        startAction = {
            AppIconView(
                packageName = packageName,
                size = 40.dp,
                frozen = frozen,
                selected = selected,
            )
        },
        // 冻结徽标放 endActions（空间不够时官方组件会自动让位）；未冻结时不渲染任何尾部内容。
        endActions = {
            if (frozen) {
                Text(
                    text = stringResource(R.string.frozen),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
        },
        onClick = onClick,
    )
}
