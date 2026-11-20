package com.ayang.glacier.feature.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayang.glacier.BuildConfig
import com.ayang.glacier.GlacierApplication
import com.ayang.glacier.R
import com.ayang.glacier.ui.components.BackNavigationAction
import com.ayang.glacier.ui.components.PageFrame
import com.ayang.glacier.ui.components.PageMetrics
import com.ayang.glacier.ui.components.pageContentPadding
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 关于页。
 *
 * 结构照 MIUIX 官方 `example/AboutPage.kt`（Apache-2.0）：
 * 品牌区（88dp 白底圆角容器 + 74dp 图标 + 35sp Bold 应用名 + 14sp 版本）→
 * `SmallTitle` 分组 → 官方 `Card` 信息卡（只读 [BasicComponent] / 可跳转 [ArrowPreference]）。
 *
 * 品牌区是**固定在列表下方的头图**（官方 `AboutPage.kt:233-305`），LazyColumn 的第一个
 * item 是与它等高的透明占位（官方 `:318-339`）。列表滚动时真实内容盖过头图，
 * 头图的三层元素（图标 / 名称 / 版本）由同一个 [scrollProgress] 错峰淡出并轻微缩放。
 *
 * 该进度只读 [androidx.compose.foundation.lazy.LazyListState.layoutInfo] 与
 * `firstVisibleItemScrollOffset`，是滚动位置的纯函数：没有 Animatable / 补间，
 * 快滚、慢滚、中途停住、回滚到顶部都直接跟随手指，不存在两套动画互相追赶。
 *
 * 与官方示例的裁剪（理由见 `docs/audit/r4/part-about.md`）：
 * - 官方 `BgEffectBackground` 动态背景（`:225-232`）与品牌图 `textureBlur`
 *   （`:271-286`）不移植：Glacier 的页面正文本身就是玻璃录制层
 *   （`ui/components/PageFrame.kt:184-198`），从这里再采 `LocalPageBackdrop`
 *   会变成自采样；官方 blur 采的是它自己的 BgEffect 层，Glacier 没有该层。
 * - 顶栏标题随滚动淡入（官方 `:127`）需要 `PageFrame` 暴露 `titleAlpha`；
 *   当前 `PageFrame` 只把 `LocalTopBarTitleAlpha`（侧栏折叠）传给玻璃档顶栏，
 *   非玻璃档 `TopAppBar` 没有 `titleAlpha` 形参，两档无法同时对齐，故本轮不做。
 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as GlacierApplication
    val repository = app.repository
    val scrollBehavior = MiuixScrollBehavior()
    val lazyListState = rememberLazyListState()

    // 官方 AboutPage.kt:94-109 原样：首项未滚出时按 logoSpacer 的滚动偏移占比计算，
    // 滚出后恒为 1f。保持 State 形式，读点只落在 graphicsLayer（绘制阶段）与占位高度换算，
    // 避免滚动时整页逐帧重组。
    val scrollProgress = remember(lazyListState) {
        derivedStateOf {
            when {
                lazyListState.firstVisibleItemIndex > 0 -> 1f

                else -> {
                    val spacer = lazyListState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.key == LogoSpacerKey }
                    if (spacer != null && spacer.size > 0) {
                        (lazyListState.firstVisibleItemScrollOffset.toFloat() / spacer.size)
                            .coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                }
            }
        }
    }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    // 官方 AboutPage.kt:223 的默认值，首帧后由品牌区实测高度替换；转屏/改字体后自动重测。
    var logoHeight by remember { mutableStateOf(DefaultLogoHeight) }

    PageFrame(
        title = stringResource(R.string.title_about),
        scrollBehavior = scrollBehavior,
        // 二级页不折叠大标题：官方 AboutPage.kt:139 的 `expandable = false`。
        expandable = false,
        // 官方 AboutPage.kt:127：标题随同一个滚动进度淡入（0.35→1.0 区间）。
        topBarTitleAlpha = { ((scrollProgress.value - 0.35f) / 0.65f).coerceIn(0f, 1f) },
        // 官方示例每页显式加滚动条（`example/MainPage.kt:341-345`），这里交给 PageFrame 按 Scaffold 实测值排版。
        scrollBarAdapter = rememberScrollBarAdapter(lazyListState),
        navigationIcon = {
            BackNavigationAction(
                onClick = onBack,
                contentDescription = stringResource(R.string.close),
            )
        },
    ) { padding ->
        // 官方 AboutPage.kt:156-157 的 scrollPadding / logoPadding：
        // 列表的顶栏留白与品牌区多出的 40dp 上边距分开算，后面的占位公式要用两者之差。
        val scrollPadding = pageContentPadding(padding)
        val logoPadding = pageContentPadding(padding, extraTop = BrandTopGap)
        val brandTopPadding = logoPadding.calculateTopPadding() + BrandLogoTopGap
        val brandStartPadding = logoPadding.calculateStartPadding(layoutDirection)
        val brandEndPadding = logoPadding.calculateEndPadding(layoutDirection)

        Box(modifier = Modifier.fillMaxSize()) {
            // 先画头图、再画列表：列表盖在头图上（官方 AboutPage.kt:233-339 的层序），
            // 透明占位让出头图区域，真实内容滚上来时从头图上方压过去。
            AboutBrandHeader(
                scrollProgress = { scrollProgress.value },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = brandTopPadding,
                        start = brandStartPadding,
                        end = brandEndPadding,
                    )
                    .onSizeChanged { size ->
                        with(density) { logoHeight = size.height.toDp() }
                    },
            )

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = scrollPadding,
                verticalArrangement = Arrangement.spacedBy(PageMetrics.SectionGap),
            ) {
                // 官方 AboutPage.kt:318-339 的透明占位。
                // 高度公式逐项保留官方：logoHeight + 52dp + logoPadding.top - scrollPadding.top + 126dp。
                // 唯一调整是减去本页 LazyColumn 的 12dp SectionGap（官方没有 spacedBy）：
                // arrangement 会在占位 item 后额外加 12dp，不减的话头图与正文的视觉间距会比官方大 12dp。
                item(key = LogoSpacerKey) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(
                                logoHeight +
                                    BrandLogoTopGap +
                                    (logoPadding.calculateTopPadding() - scrollPadding.calculateTopPadding()) +
                                    BrandContentGap -
                                    PageMetrics.SectionGap,
                            ),
                    )
                }

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
}

/**
 * 品牌区。尺寸逐条对应官方 `example/AboutPage.kt:242-304`：
 * 容器 88dp / 圆角 24dp / 白底 / 图标 74dp / 应用名 35sp Bold / 版本 14sp 居中，
 * 名称上方 12dp、下方 5dp。
 *
 * 三层元素的透明度与缩放全部由同一个 [scrollProgress] 按官方错峰区间推导
 * （版本 0.05 / 名称 0.20 / 图标 0.35，区间宽 0.15，缩放最多 5%），
 * 在 `graphicsLayer` 里读进度 → 只重绘不重组，也不会出现某一层先动另一层后动的割裂。
 */
@Composable
private fun AboutBrandHeader(
    scrollProgress: () -> Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(BrandContainerSize)
                // 官方写法（AboutPage.kt:242-254）：graphicsLayer 的 shape + clip 裁容器，
                // 白底固定不随主题反转，保证浅色/深色下品牌标记都有清晰轮廓。
                .graphicsLayer {
                    val iconProgress = brandProgress(scrollProgress(), BrandFadeStartIcon)
                    shape = BrandContainerShape
                    clip = true
                    alpha = 1f - iconProgress
                    scaleX = 1f - iconProgress * BrandScaleDelta
                    scaleY = 1f - iconProgress * BrandScaleDelta
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
            modifier = Modifier
                .padding(top = BrandNameTopPadding, bottom = BrandNameBottomPadding)
                .graphicsLayer {
                    val nameProgress = brandProgress(scrollProgress(), BrandFadeStartName)
                    alpha = 1f - nameProgress
                    scaleX = 1f - nameProgress * BrandScaleDelta
                    scaleY = 1f - nameProgress * BrandScaleDelta
                },
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
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val versionProgress = brandProgress(scrollProgress(), BrandFadeStartVersion)
                    alpha = 1f - versionProgress
                    scaleX = 1f - versionProgress * BrandScaleDelta
                    scaleY = 1f - versionProgress * BrandScaleDelta
                },
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = BrandVersionFontSize,
            textAlign = TextAlign.Center,
        )
    }
}

/** 官方三段错峰进度：`((p - start) / 0.15).coerceIn(0f, 1f)`（`AboutPage.kt:247/265/295`）。 */
private fun brandProgress(progress: Float, start: Float): Float =
    ((progress - start) / BrandFadeSpan).coerceIn(0f, 1f)

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

/** LazyColumn 里透明占位 item 的 key，同时用于 `scrollProgress` 反查它的实测高度。 */
private const val LogoSpacerKey = "logoSpacer"

/** 官方 `mutableStateOf(300.dp)` 的首帧默认高度（`AboutPage.kt:223`）。 */
private val DefaultLogoHeight = 300.dp

/** 品牌区相对顶栏的额外上边距：官方 `pageContentPadding(extraTop = 40.dp)`（`:157`）。 */
private val BrandTopGap = 40.dp

/** 官方 `logoContentPadding` 在 `logoPadding` 之上再加的 52dp（`:182`）。 */
private val BrandLogoTopGap = 52.dp

/** 官方占位公式尾部的 126dp，即品牌区底部到第一条正文之间的留白（`:323`）。 */
private val BrandContentGap = 126.dp

/** 三段错峰淡出的起点（官方 `:247` / `:265` / `:295`）。 */
private const val BrandFadeStartIcon = 0.35f
private const val BrandFadeStartName = 0.20f
private const val BrandFadeStartVersion = 0.05f

/** 错峰淡出区间宽度与最大缩放幅度（官方 `:247-252`）。 */
private const val BrandFadeSpan = 0.15f
private const val BrandScaleDelta = 0.05f

private val BrandContainerSize = 88.dp
private val BrandCornerRadius = 24.dp

/** 只创建一次，避免每次绘制都在 graphicsLayer 里新建 Shape。 */
private val BrandContainerShape = RoundedCornerShape(BrandCornerRadius)
private val BrandIconSize = 74.dp
private val BrandNameTopPadding = 12.dp
private val BrandNameBottomPadding = 5.dp
private val BrandNameFontSize = 35.sp
private val BrandVersionFontSize = 14.sp
