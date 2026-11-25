package com.ayang.glacier.feature.about

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ayang.glacier.R
import com.ayang.glacier.ui.components.BackNavigationAction
import com.ayang.glacier.ui.components.PageFrame
import com.ayang.glacier.ui.components.PageMetrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberScrollBarAdapter
import top.yukonga.miuix.kmp.interfaces.ExperimentalScrollBarApi
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 一个随包组件：组件名、许可证、说明全部走资源。 */
private data class Component(
    @StringRes val name: Int,
    @StringRes val license: Int,
    @StringRes val note: Int,
)

private val components = listOf(
    Component(R.string.licenses_component_miuix, R.string.licenses_license_apache2, R.string.licenses_note_miuix),
    Component(R.string.licenses_component_androidx, R.string.licenses_license_apache2, R.string.licenses_note_androidx),
    Component(R.string.licenses_component_shizuku, R.string.licenses_license_apache2, R.string.licenses_note_shizuku),
    Component(R.string.licenses_component_dhizuku, R.string.licenses_license_apache2, R.string.licenses_note_dhizuku),
    Component(R.string.licenses_component_appiconloader, R.string.licenses_license_apache2, R.string.licenses_note_appiconloader),
    Component(R.string.licenses_component_libxposed, R.string.licenses_license_apache2, R.string.licenses_note_libxposed),
    Component(R.string.licenses_component_hail, R.string.licenses_license_gpl3, R.string.licenses_note_hail),
)

/**
 * 开源许可证页。
 *
 * GPL-3.0 的分发义务要求随附完整许可证文本，所以这里直接渲染随包分发的 `LICENSE.txt`，
 * 而不是只给一个链接。段落按条目渲染，避免一次排版三万字。
 *
 * 布局照官方 `example/LicensePage.kt`（Apache-2.0）：每张 `Card` 横向 12dp、
 * 正文用 `body2` / `footnote1`、分节标题用 `SmallTitle`、节间距 12dp。
 */
@OptIn(ExperimentalScrollBarApi::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()

    val paragraphs by produceState(initialValue = emptyList<String>()) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("LICENSE.txt").bufferedReader().use { it.readText() }
                    .split("\n\n")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            }.getOrDefault(emptyList())
        }
    }

    PageFrame(
        title = stringResource(R.string.title_licenses),
        scrollBehavior = scrollBehavior,
        // 二级页不折叠大标题（官方 AboutPage.kt:139 的同一做法）。
        expandable = false,
        // 官方示例每页显式加滚动条（`example/LicensePage.kt`）；轨道内边距由 PageFrame 计算。
        scrollBarAdapter = rememberScrollBarAdapter(listState),
        navigationIcon = {
            BackNavigationAction(
                onClick = onBack,
                contentDescription = stringResource(R.string.close),
            )
        },
        // 横向 12dp 只给卡片：SmallTitle 自带 28dp，再叠一层会变成 40dp。
        contentPaddingHorizontal = 0.dp,
    ) { contentPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // 官方 LicensePage 是"卡片各自 padding(horizontal = 12) + SmallTitle 28dp"两层
            // （LicensePage.kt:117-120、miuix-ui/.../basic/SmallTitle.kt:42），
            // 因此内容 padding 横向为 0，12dp 由卡片自己提供。
            contentPadding = contentPadding,
            // 弹性 overscroll 由 PageFrame 的官方 pageScrollModifiers 统一消费（官方 LicensePage 同参）。
            overscrollEffect = null,
            verticalArrangement = Arrangement.spacedBy(PageMetrics.SectionGap),
        ) {
            item {
                Text(
                    text = stringResource(R.string.licenses_body),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = PageMetrics.Horizontal),
                )
            }
            item { SmallTitle(stringResource(R.string.licenses_components)) }
            items(components) { component ->
                // 三段文字（组件名 / 许可证 / 说明）不是一行信息，用官方 Card 的内边距承载；
                // 内边距直接取官方行组件默认值（16dp），不再手写一个魔法数字。
                // 卡片自身横向 12dp（与官方 LicensePage.kt:117-120 一致），页面 contentPadding 横向为 0。
                //
                // 层次用官方行的 token：组件名 = 行标题 `headline1`（17sp Medium，Component.kt:92-95），
                // 许可证 = 行摘要 `body2`（14sp，Component.kt:97-100），说明 = `footnote1`。
                // 这三段都允许自动换行：不设置 maxLines/ellipsis，1.3x/2.0x 下靠官方行高自然增高。
                // 另外本页这些卡是"只读信息行"，官方 LicensePage 的卡片里放 ArrowPreference 是因为
                // 它能跳到库主页；Glacier 无外网也无已核实的项目 URL，所以不伪造箭头/点击语义。
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PageMetrics.Horizontal),
                    insideMargin = BasicComponentDefaults.InsideMargin,
                    colors = CardDefaults.defaultColors(),
                ) {
                    Text(
                        text = stringResource(component.name),
                        style = MiuixTheme.textStyles.headline1,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(component.license),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(component.note),
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            // 许可证正文异步读 assets；读失败时 paragraphs 为空，
            // 此时不展示一个空的 GPL 分节标题（否则用户会看到"有标题没正文"）。
            if (paragraphs.isNotEmpty()) {
                item { SmallTitle(stringResource(R.string.licenses_gpl_heading)) }
                items(paragraphs) { paragraph ->
                    Text(
                        text = paragraph,
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PageMetrics.Horizontal),
                    )
                }
            }
        }
    }
}
