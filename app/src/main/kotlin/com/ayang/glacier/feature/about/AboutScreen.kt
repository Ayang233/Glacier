package com.ayang.glacier.feature.about

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.computedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode as ComposeBlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayang.glacier.BuildConfig
import com.ayang.glacier.GlacierApplication
import com.ayang.glacier.R
import com.ayang.glacier.ui.LocalGlassActive
import com.ayang.glacier.ui.components.BackNavigationAction
import com.ayang.glacier.ui.components.PageFrame
import com.ayang.glacier.ui.components.PageMetrics
import com.ayang.glacier.ui.components.pageScrollModifiers
import com.ayang.glacier.ui.effect.BgEffectBackground
import com.ayang.glacier.ui.effect.aboutLogoBlend
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.glass.GlassCard
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 关于页。
 *
 * 结构照 MIUIX 官方 `example/AboutPage.kt`（Apache-2.0）：
 * 品牌区（88dp 白底圆角容器 + 74dp 图标 + 35sp Bold 应用名 + 14sp 版本）→
 * 官方 `Card` 信息卡（只读 [BasicComponent] / 可跳转 [ArrowPreference]）。
 *
 * 本页不再使用 `SmallTitle` 分组小标题：品牌区已经给出应用名与版本号，信息卡只保留
 * 不重复的信息（技术信息 / 系统状态 / 开源许可证入口），卡片之间统一靠
 * [PageMetrics.SectionGap] 分隔。
 *
 * 品牌区是**固定在列表下方的头图**（官方 `AboutPage.kt:233-305`），LazyColumn 的第一个
 * item 是与它等高的透明占位（官方 `:318-339`）。列表滚动时真实内容盖过头图，
 * 头图的三层元素（图标 / 名称 / 版本）由同一个 [scrollProgress] 错峰淡出并轻微缩放。
 *
 * 该进度只读 [androidx.compose.foundation.lazy.LazyListState.layoutInfo] 与
 * `firstVisibleItemScrollOffset`，是滚动位置的纯函数：没有 Animatable / 补间，
 * 快滚、慢滚、中途停住、回滚到顶部都直接跟随手指，不存在两套动画互相追赶。
 *
 * 动态背景与纹理模糊（对齐官方 `AboutPage.kt:114-121/229-236/271-290/351-375`）：
 * 按官方三层 backdrop 范式接入，避免自采样：
 * - 本页自建独立的 **aboutBackdrop**（`rememberLayerBackdrop`），只录制 BgEffect 动态背景；
 *   [BgEffectBackground] 的 `bgModifier = layerBackdrop(aboutBackdrop)` 把背景 Spacer 录进去，
 *   品牌名与信息卡作为它的**兄弟**采样，满足「消费者不是录制层后代」红线。
 * - 品牌名 `textureBlur(DstIn)` 与信息卡 `textureBlur` 均走官方混色 token
 *   （`ui/effect/AboutBlendTokens.kt`，逐字节取自官方 example）。
 * - 动态背景 `alpha = 1 - scrollProgress`，随滚动淡出（官方 `AboutPage.kt:235`）。
 * - 全链路用 [LocalGlassActive] 门控：用户关玻璃或设备不支持时，backdrop 为 null，
 *   动态背景与纹理模糊都不挂载，回退纯色底（官方 `rememberBlurBackdrop` 同款语义）。
 *
 * BgEffect 的绘制代码逐字节移植自 MIUIX 官方 example（Apache-2.0，见 `ui/effect/`），
 * 只把两个 example 内部依赖换成 Glacier 侧等价物：`shouldShowSplitPane` → [isWideLayout]、
 * `isInDarkTheme` → 主题 surface 亮度判断。
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
        computedStateOf {
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
    // 官方 AboutPage.kt:134-139：离散阈值 0.45 + 200ms tween。只在跨过阈值时切换目标值，
    // 滚动过程不逐帧改 alpha，顶栏不会跟着手指抖。
    val showTitle by remember(lazyListState) {
        computedStateOf { scrollProgress.value >= 0.45f }
    }
    val titleAlpha by animateFloatAsState(
        targetValue = if (showTitle) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "aboutTitleAlpha",
    )
    val density = LocalDensity.current
    // 官方 AboutPage.kt:223 的默认值，首帧后由品牌区实测高度替换；转屏/改字体后自动重测。
    var logoHeight by remember { mutableStateOf(DefaultLogoHeight) }

    // 内层的**背景录制层**：与 PageFrame 的页面录制层（供顶栏采样）是两个独立 layer。
    // 它只录 BgEffect 的背景 Spacer，品牌名/信息卡作为 Spacer 的兄弟采样它 → 不自采样。
    // 这正是官方 AboutPage 的 backdrop（外层，顶栏用）+ AboutContent 的 backdrop（内层，
    // 品牌/卡片用）双 backdrop 范式在 Glacier PageFrame 架构下的对应落点。
    val glassActive = LocalGlassActive.current
    val surface = MiuixTheme.colorScheme.surface
    val isDark = surface.luminance() < 0.5f
    val aboutBackdrop = rememberLayerBackdrop {
        drawRect(surface)
        drawContent()
    }.takeIf { glassActive }
    val logoBlend = remember(isDark) { aboutLogoBlend(isDark) }

    PageFrame(
        title = stringResource(R.string.title_about),
        scrollBehavior = scrollBehavior,
        // 二级页不折叠大标题：官方 AboutPage.kt:139 的 `expandable = false`。
        expandable = false,
        // 官方 AboutPage.kt:134-139：进度过 0.45 才显示标题，并用 200ms tween 淡入，
        // 不是随滚动连续变化的线性函数（R21 审计 P1-7）。
        topBarTitleAlpha = { titleAlpha },
        // 官方示例每页显式加滚动条（`example/MainPage.kt:341-345`），这里交给 PageFrame 按 Scaffold 实测值排版。
        scrollBarAdapter = rememberScrollBarAdapter(lazyListState),
        // 本页背景/品牌区是固定的，只有列表可以滚动：弹性 overscroll 必须挂在列表上，
        // 不能由 PageFrame 挂在整页 wrapper 上。否则上/下拉回弹会把 BgEffect 背景与品牌区
        // 一起拖走，顶栏下方露出宿主底色（官方 AboutPage 正是把 pageScrollModifiers 挂在
        // LazyColumn 上，AboutPage.kt:312-316）。
        pageScrollOnContent = true,
        navigationIcon = {
            BackNavigationAction(
                onClick = onBack,
                contentDescription = stringResource(R.string.close),
            )
        },
    ) { scrollPadding ->
        // 官方 AboutPage.kt:156-157 的 scrollPadding / logoPadding：
        // 列表的顶栏留白与品牌区多出的 40dp 上边距分开算，后面的占位公式要用两者之差。
        // 官方用 `PaddingValues` 委托派生第二份 padding，横向/安全区的读取仍然留在布局阶段
        // （`AboutContent.kt:180-192`），不在组合期解包。
        val logoPadding = remember(scrollPadding) {
            object : PaddingValues by scrollPadding {
                override fun calculateTopPadding() = scrollPadding.calculateTopPadding() + BrandTopGap
            }
        }

        // 官方 `logoContentPadding`：品牌区在 logoPadding 之上再加 52dp，底部不占位。
        val logoContentPadding = remember(logoPadding) {
            object : PaddingValues by logoPadding {
                override fun calculateTopPadding() = logoPadding.calculateTopPadding() + BrandLogoTopGap
                override fun calculateBottomPadding() = 0.dp
            }
        }

        // 官方 AboutPage.kt:193-197 的 listContentPadding：底部留白从 LazyColumn 的
        // contentPadding 挪进正文 Column。这样最后一段仍属于可滚动内容，而不是固定的列表内边距。
        val listContentPadding = remember(scrollPadding) {
            object : PaddingValues by scrollPadding {
                override fun calculateBottomPadding() = 0.dp
            }
        }

        // 官方 AboutPage.kt:229-236 / AboutContent.kt:311-339 的动态背景层：
        // bgEffect 的背景 Spacer 录进 aboutBackdrop，下面品牌区/卡片作为它的兄弟采样，
        // alpha 随滚动进度淡出（1-p）。玻璃关闭 / shader 不支持时 aboutBackdrop 为 null，
        // BgEffectBackground 自动退回普通 Box（纯色底），纹理模糊也不挂载。
        BgEffectBackground(
            dynamicBackground = glassActive,
            modifier = Modifier.fillMaxSize(),
            bgModifier = aboutBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier,
            isFullSize = true,
            // 玻璃关闭时 alpha 恒为 0：官方 BgEffectDraw 只按 alpha 画 brush（不看 playing），
            // 若仍传 1-p 会在纯色档把彩色渐变直接画出来。关档必须彻底退回纯色底。
            alpha = { if (glassActive) (1f - scrollProgress.value).coerceIn(0f, 1f) else 0f },
        ) {
            // 先画头图、再画列表：列表盖在头图上（官方 AboutPage.kt:233-339 的层序），
            // 透明占位让出头图区域，真实内容滚上来时从头图上方压过去。
            AboutBrandHeader(
                scrollProgress = { scrollProgress.value },
                backdrop = aboutBackdrop,
                logoBlend = logoBlend,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(logoContentPadding)
                    .onSizeChanged { size ->
                        with(density) { logoHeight = size.height.toDp() }
                    },
            )

            LazyColumn(
                state = lazyListState,
                // 官方 AboutPage.kt:312-316：滚动/回弹/顶栏折叠只作用于列表本身，
                // 固定在下方的 BgEffect 背景与品牌区不跟着位移。
                modifier = Modifier
                    .fillMaxSize()
                    .pageScrollModifiers(topAppBarScrollBehavior = scrollBehavior),
                contentPadding = listContentPadding,
                overscrollEffect = null,
            ) {
                // 官方 AboutPage.kt:318-339 的透明占位。
                // 高度公式逐项保留官方：logoHeight + 52dp + logoPadding.top - scrollPadding.top + 126dp。
                item(key = LogoSpacerKey) {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(
                                logoHeight +
                                    BrandLogoTopGap +
                                    (logoPadding.calculateTopPadding() - scrollPadding.calculateTopPadding()) +
                                    BrandContentGap,
                            ),
                    )
                }

                // 官方 AboutPage.kt:354-413：正文放在同一个 item 里，并用
                // `Spacer(Modifier.fillParentMaxHeight())` 把这一项撑到至少一屏。
                // 少了这个占位，正文不足一屏时 LazyColumn 根本不可滚动，下拉只会走弹性
                // overscroll：卡片会压到还没淡出的品牌区上，且 scrollProgress 永远为 0，
                // 顶栏标题、品牌淡出都不会触发 —— 这正是官方与 Glacier 上下滑观感不同的根因。
                item(key = AboutContentKey) {
                    Box {
                        Spacer(Modifier.fillParentMaxHeight())
                        Column(
                            modifier = Modifier.padding(bottom = scrollPadding.calculateBottomPadding()),
                            verticalArrangement = Arrangement.spacedBy(PageMetrics.SectionGap),
                        ) {
                            // 品牌区已展示应用名 + 版本号，这里不再重复版本行；
                            // 包名与构建类型合成为一行技术信息。
                            AboutCard {
                                BasicComponent(
                                    title = stringResource(R.string.about_package_label),
                                    summary = "${BuildConfig.APPLICATION_ID} · ${BuildConfig.BUILD_TYPE}",
                                )
                            }

                            AboutCard {
                                BasicComponent(
                                    title = stringResource(R.string.working_mode),
                                    // selectedMode() 最终读 preferences.workingMode（普通 SharedPreferences
                                    // 属性），订阅 revision 才能让设置页改完后摘要立即更新。
                                    summary = remember(app.preferences.revision.intValue) {
                                        repository.selectedMode().key
                                    },
                                )
                                BasicComponent(
                                    title = stringResource(R.string.about_device_label),
                                    summary = repository.deviceSummary(),
                                )
                            }

                            // 许可证只保留可点击入口：SPDX 标识作为行尾值，
                            // 不再额外放一条重复的 license label / notice。
                            AboutCard {
                                ArrowPreference(
                                    title = stringResource(R.string.about_licenses),
                                    endActions = {
                                        AboutTrailingValue(stringResource(R.string.about_license_spdx))
                                    },
                                    onClick = onOpenLicenses,
                                )
                            }

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
    backdrop: LayerBackdrop?,
    logoBlend: List<top.yukonga.miuix.kmp.blur.BlendColorEntry>,
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
                }
                // 官方 AboutPage.kt:274-290：品牌名前景模糊，采 aboutBackdrop（只录背景 Spacer），
                // DstIn 让文字 alpha 当遮罩，文字本身取模糊后的彩色纹理。无 backdrop 时跳过。
                .then(
                    if (backdrop != null) {
                        Modifier.textureBlur(
                            backdrop = backdrop,
                            shape = BrandLogoBlurShape,
                            blurRadius = BrandLogoBlurRadius,
                            noiseCoefficient = BlurDefaults.NoiseCoefficient,
                            colors = BlurDefaults.blurColors(blendColors = logoBlend),
                            contentBlendMode = ComposeBlendMode.DstIn,
                        )
                    } else {
                        Modifier
                    },
                ),
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
 * 官方信息卡（对齐最新官方 `AboutPage.kt:352-400`）。
 *
 * 官方已把关于页的信息卡从"Card + 自己 textureBlur"改成官方 [GlassCard]：
 * 半透明浅色填充让底下的玻璃材质透出来，本身不再采样/模糊 backdrop，
 * 因此不需要传 `aboutBackdrop`，也不会自采样。圆角/填充色全部走 `GlassCardDefaults`。
 *
 * 横向 12dp 留白由 [pageContentPadding] 的默认 `PageMetrics.Horizontal` 提供（页面统一），
 * 卡片本身不再叠加第二层 horizontal padding，否则会变成 24dp。
 */
@Composable
private fun AboutCard(
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
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

/** 官方 `item(key = "about")`：正文卡片 + `fillParentMaxHeight` 撑高项。 */
private const val AboutContentKey = "about"

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

// ---- 纹理模糊（官方 AboutPage.kt:276-286 品牌名）----
/** 官方品牌名的模糊裁切圆角 16dp。 */
private val BrandLogoBlurShape = RoundedCornerShape(16.dp)
/** 官方品牌名前景模糊半径 100f。 */
private const val BrandLogoBlurRadius = 100f
