package com.ayang.glacier.feature.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ayang.glacier.GlacierApplication
import com.ayang.glacier.platform.rememberAppIcon
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 应用图标。
 *
 * 图标来源、是否去色、是否合成自适应遮罩都由设置决定；
 * 这里只负责按 OS4 的圆角比例摆放。
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
    val bitmap = rememberAppIcon(
        packageName = packageName,
        size = size,
        grayscale = frozen && preferences.grayscaleFrozenIcons,
        iconPack = preferences.iconPack,
        synthesizeAdaptive = preferences.synthesizeAdaptiveIcons,
    )
    val shape = RoundedCornerShape(size * 0.23f)
    if (bitmap != null) {
        Icon(
            bitmap = bitmap,
            contentDescription = null,
            modifier = modifier
                .size(size)
                .clip(shape),
            // 应用图标必须保持原色，不能被 LocalContentColor 染色。
            tint = androidx.compose.ui.graphics.Color.Unspecified,
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(MiuixTheme.colorScheme.surfaceContainerHigh),
        )
    }
}

/** 底部操作单里的一行。颜色与字号全部来自官方主题。 */
@Composable
fun SheetActionRow(
    icon: ImageVector,
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (destructive) MiuixTheme.colorScheme.error else MiuixTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = color,
        )
        Text(
            text = label,
            style = MiuixTheme.textStyles.body1,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(0.dp))
    }
}

/** 应用列表里的一行（图标 + 名称 + 副标题 + 尾部内容）。 */
@Composable
fun AppRow(
    packageName: String,
    label: String,
    summary: String?,
    frozen: Boolean,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppIconView(packageName = packageName, size = 42.dp, frozen = frozen)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MiuixTheme.textStyles.body1,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!summary.isNullOrBlank()) {
                Text(
                    text = summary,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}
