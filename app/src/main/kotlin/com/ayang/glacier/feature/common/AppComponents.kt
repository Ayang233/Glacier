package com.ayang.glacier.feature.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ayang.glacier.GlacierApplication
import com.ayang.glacier.R
import com.ayang.glacier.platform.rememberAppIcon
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
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
    val bitmap = rememberAppIcon(
        packageName = packageName,
        size = size,
        grayscale = frozen && preferences.grayscaleFrozenIcons,
        iconPack = preferences.iconPack,
        synthesizeAdaptive = preferences.synthesizeAdaptiveIcons,
    )
    val radius = size * AppIconCornerRatio
    // 选中环画在 2dp 的内缩框里：squircleBorder 是 onDrawBehind，
    // 若不留出内缩，图标会盖住描边。
    val frame = if (selected) {
        Modifier
            .squircleBorder(
                width = 2.dp,
                color = MiuixTheme.colorScheme.primary,
                cornerRadius = radius + 2.dp,
            )
            .padding(2.dp)
    } else {
        Modifier
    }
    if (bitmap != null) {
        Icon(
            bitmap = bitmap,
            contentDescription = null,
            // size 在最外层：选中环的内缩不能把图标撑大（否则选中时网格会跳动）。
            modifier = modifier
                .size(size)
                .then(frame)
                .squircleClip(radius),
            // 应用图标必须保持原色，不能被 LocalContentColor 染色。
            tint = Color.Unspecified,
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .then(frame)
                .squircleBackground(MiuixTheme.colorScheme.surfaceContainerHigh, radius),
        )
    }
}

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
                tint = color,
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
 * 点击语义由调用方决定：外层 `Card(onClick, pressFeedbackType = Sink)` 承担点击时传 `null`。
 */
@Composable
fun AppRow(
    packageName: String,
    label: String,
    summary: String?,
    frozen: Boolean,
    onClick: (() -> Unit)? = null,
) {
    BasicComponent(
        title = label,
        summary = summary?.takeIf { it.isNotBlank() },
        startAction = { AppIconView(packageName = packageName, size = 40.dp, frozen = frozen) },
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
