package com.ayang.glacier.feature.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayang.glacier.GlacierApplication
import com.ayang.glacier.R
import com.ayang.glacier.platform.rememberAppIcon
import com.ayang.glacier.ui.components.PageMetrics
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.glass.GlassPopupAnchor
import top.yukonga.miuix.kmp.glass.glassPopupAnchor
import top.yukonga.miuix.kmp.glass.glassPopupAnchorContent
import top.yukonga.miuix.kmp.squircle.squircleBackground
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
 * 结构是「固定尺寸 Box + 图标铺满」：图标永远等于 `size`，选中与否都不参与布局。
 *
 * **选中态不在这里画**：MIUIX 没有「图标描边」这种选中语言，自绘的 2dp 蓝色
 * squircle 描边是自造轮子（R9 已删）。多选指示统一交给官方 [Checkbox]
 * （`miuix-ui/.../basic/Checkbox.kt`）：列表行放 `endActions`（与官方作者自己的
 * Mishka `AppProxyScreen.kt:415-441` 完全同构），网格单元叠在图标右上角。
 */
@Composable
fun AppIconView(
    packageName: String,
    size: Dp,
    frozen: Boolean,
    modifier: Modifier = Modifier,
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
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val color = MiuixTheme.colorScheme.onSurface
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
 * 应用列表 / 搜索结果里的一行（图标 + 名称 + 副标题 + 冻结徽标 + 主页勾选），同样走官方 [BasicComponent]。
 *
 * 行高（最小 56dp）、内边距（16dp）、图文间距（8dp）全部用 [BasicComponent] 默认值（规范 2.2 / 第 3 节）；
 * 应用图标 40dp 是规范第 3 节登记的"应用图标"token；冻结徽标与勾选框按官方顺序放 `endActions`。
 *
 * 手势：行本身可选注册 [onClick]（搜索等"点一下就走"的入口用）；
 * 列表页需要同时支持点按与长按时，`onClick` 传 `null`，由外层官方 `Card(onClick, onLongPress)`
 * 统一承担（`AppListCardRow`），避免同一行出现两层点击/两层反馈。
 *
 * @param onHome 主页归属：`null` = 不显示勾选框（主页搜索这类入口），
 *   `true/false` = 渲染官方 [Checkbox] 表示"已在主页 / 不在主页"。应用页点一下就是把这一行
 *   移进/移出主页，没有"多选模式"，所以勾选框也**不是**多选计数，只是归属状态。
 *   勾选框本身 `onClick = null`：点它的位置等于点这一行，由外层 `Card` 统一处理。
 */
@Composable
fun AppRow(
    packageName: String,
    label: String,
    summary: String?,
    frozen: Boolean,
    onClick: (() -> Unit)? = null,
    onHome: Boolean? = null,
    anchor: GlassPopupAnchor? = null,
) {
    BasicComponent(
        title = label,
        summary = summary?.takeIf { it.isNotBlank() },
        startAction = {
            val icon = @Composable {
                AppIconView(
                    packageName = packageName,
                    size = PageMetrics.ListAppIcon,
                    frozen = frozen,
                )
            }
            if (anchor != null) {
                // 图标登记为锚点：长按菜单从图标原地展开，展开期间只隐藏图标（整行仍可见）。
                Box(
                    modifier = Modifier
                        .glassPopupAnchor(anchor, cornerRadius = PageMetrics.ListAppIcon * AppIconCornerRatio)
                        .glassPopupAnchorContent(anchor),
                ) {
                    icon()
                }
            } else {
                icon()
            }
        },
        // 冻结徽标 / 主页勾选放 endActions（空间不够时官方组件会自动让位）；都没内容时不渲染。
        endActions = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (frozen) {
                    Text(
                        text = stringResource(R.string.frozen),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.primary,
                    )
                }
                if (onHome != null) {
                    Checkbox(state = ToggleableState(onHome), onClick = null)
                }
            }
        },
        onClick = onClick,
    )
}
