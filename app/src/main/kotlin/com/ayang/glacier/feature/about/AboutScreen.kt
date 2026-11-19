package com.ayang.glacier.feature.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayang.glacier.BuildConfig
import com.ayang.glacier.GlacierApplication
import com.ayang.glacier.R
import com.ayang.glacier.ui.components.PageFrame
import com.ayang.glacier.ui.components.PageMetrics
import com.ayang.glacier.ui.components.TopBarAction
import com.ayang.glacier.ui.components.pageContentPadding
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.ChevronBackward
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 关于页。
 *
 * 结构照 MIUIX 官方 `example/AboutPage.kt`（Apache-2.0）：
 * 品牌区（88dp 白底圆角容器 + 74dp 图标 + 35sp Bold 应用名 + 14sp 版本）→
 * `SmallTitle` 分组 → 官方 `Card` 信息卡（只读 [BasicComponent] / 可跳转 [ArrowPreference]）。
 *
 * 与官方示例的差异只有一处：官方品牌区带 `BgEffectBackground` 与滚动淡出/缩放动画，
 * Glacier 没有动态背景层，因此只保留静态品牌区；动画需要 PageFrame 暴露滚动联动能力后再补。
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as GlacierApplication
    val repository = app.repository
    val scrollBehavior = MiuixScrollBehavior()

    PageFrame(
        title = stringResource(R.string.title_about),
        scrollBehavior = scrollBehavior,
        // 二级页不折叠大标题：官方 AboutPage.kt:139 的 `expandable = false`。
        expandable = false,
        navigationIcon = {
            TopBarAction(MiuixIcons.Glass.ChevronBackward, stringResource(R.string.close), onBack)
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = pageContentPadding(padding),
            verticalArrangement = Arrangement.spacedBy(PageMetrics.SectionGap),
        ) {
            item { AboutBrandHeader() }

            item { SmallTitle(stringResource(R.string.about_section_app)) }
            item {
                AboutCard {
                    BasicComponent(
                        title = stringResource(R.string.about_version_label),
                        endActions = {
                            AboutTrailingValue(
                                stringResource(
                                    R.string.version_value,
                                    BuildConfig.VERSION_NAME,
                                    BuildConfig.VERSION_CODE,
                                ),
                            )
                        },
                    )
                    BasicComponent(
                        title = stringResource(R.string.about_package_label),
                        summary = BuildConfig.APPLICATION_ID,
                    )
                    BasicComponent(
                        title = stringResource(R.string.about_build_label),
                        summary = BuildConfig.BUILD_TYPE,
                    )
                }
            }

            item { SmallTitle(stringResource(R.string.settings_system)) }
            item {
                AboutCard {
                    BasicComponent(
                        title = stringResource(R.string.working_mode),
                        summary = repository.selectedMode().key,
                    )
                    BasicComponent(
                        title = stringResource(R.string.about_device_label),
                        summary = repository.deviceSummary(),
                    )
                }
            }

            item { SmallTitle(stringResource(R.string.about_section_open_source)) }
            item {
                AboutCard {
                    ArrowPreference(
                        title = stringResource(R.string.about_licenses),
                        endActions = {
                            AboutTrailingValue(stringResource(R.string.about_license_spdx))
                        },
                        onClick = onOpenLicenses,
                    )
                    BasicComponent(
                        title = stringResource(R.string.about_license_label),
                        summary = stringResource(R.string.about_license_notice),
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.about_description),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * 品牌区。尺寸逐条对应官方 `example/AboutPage.kt:242-304`：
 * 容器 88dp / 圆角 24dp / 白底 / 图标 74dp / 应用名 35sp Bold / 版本 14sp 居中，
 * 名称上方 12dp、下方 5dp；品牌区上方留白 40dp（官方 `logoPadding` 的 `extraTop`）。
 */
@Composable
private fun AboutBrandHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = BrandTopPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(BrandContainerSize)
                // 官方写法（AboutPage.kt:242-254）：graphicsLayer 的 shape + clip 裁容器，
                // 白底固定不随主题反转，保证浅色/深色下品牌标记都有清晰轮廓。
                .graphicsLayer {
                    shape = RoundedCornerShape(BrandCornerRadius)
                    clip = true
                }
                .background(Color.White),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_brand_mark),
                contentDescription = null,
                modifier = Modifier.size(BrandIconSize),
            )
        }
        Text(
            text = stringResource(R.string.app_name),
            modifier = Modifier.padding(top = BrandNameTopPadding, bottom = BrandNameBottomPadding),
            color = MiuixTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            fontSize = BrandNameFontSize,
        )
        Text(
            text = stringResource(
                R.string.about_version_brand,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
            ),
            modifier = Modifier.fillMaxWidth(),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = BrandVersionFontSize,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * 官方信息卡：`CardDefaults.defaultColors()`。
 *
 * 横向 12dp 留白由 [pageContentPadding] 的默认 `PageMetrics.Horizontal` 提供（页面统一），
 * 卡片本身不再叠加第二层 horizontal padding，否则会变成 24dp；这与官方 AboutPage
 * `Card(padding(horizontal = 12.dp))` 的视觉等价（官方 pageContentPadding 横向只管安全区）。
 * `CardDefaults.InsideMargin` 在 0.9.5 是 0dp，行内留白由 `BasicComponent` 的 16dp 提供。
 */
@Composable
private fun AboutCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(),
        content = content,
    )
}

/** 行尾信息值：官方 AboutPage.kt:374-382 的 `body2` + `onSurfaceVariantActions`。 */
@Composable
private fun AboutTrailingValue(text: String) {
    Text(
        text = text,
        fontSize = MiuixTheme.textStyles.body2.fontSize,
        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
    )
}

private val BrandTopPadding = 40.dp
private val BrandContainerSize = 88.dp
private val BrandCornerRadius = 24.dp
private val BrandIconSize = 74.dp
private val BrandNameTopPadding = 12.dp
private val BrandNameBottomPadding = 5.dp
private val BrandNameFontSize = 35.sp
private val BrandVersionFontSize = 14.sp
