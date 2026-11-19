package com.ayang.glacier.feature.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ayang.glacier.R
import com.ayang.glacier.ui.components.GroupCard
import com.ayang.glacier.ui.components.PageFrame
import com.ayang.glacier.ui.components.TopBarAction
import com.ayang.glacier.ui.components.pageContentPadding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.ChevronBackward
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class Component(val name: String, val license: String, val note: String)

private val components = listOf(
    Component("MIUIX", "Apache-2.0", "OS4 theme, components and official glass surfaces."),
    Component("AndroidX / Jetpack Compose", "Apache-2.0", "UI runtime and lifecycle."),
    Component("Shizuku", "Apache-2.0", "Privileged binder access without root."),
    Component("Dhizuku", "Apache-2.0", "Delegated device-owner access."),
    Component("AppIconLoader", "Apache-2.0", "Adaptive icon rendering."),
    Component("libxposed API", "Apache-2.0", "Optional module entry point."),
    Component(
        "Hail",
        "GPL-3.0-or-later",
        "Original project by aistra0528. Glacier's freeze backends and data format derive from it.",
    ),
)

/**
 * 开源许可证页。
 *
 * GPL-3.0 的分发义务要求随附完整许可证文本，所以这里直接渲染随包分发的 `LICENSE.txt`，
 * 而不是只给一个链接。段落按条目渲染，避免一次排版三万字。
 */
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()

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
        navigationIcon = {
            TopBarAction(MiuixIcons.Glass.ChevronBackward, stringResource(R.string.close), onBack)
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = pageContentPadding(padding, horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.licenses_body),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            item { SmallTitle("Components") }
            items(components) { component ->
                GroupCard(modifier = Modifier.padding(bottom = 4.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = component.name,
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = component.license,
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.primary,
                        )
                        Text(
                            text = component.note,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
            item { SmallTitle("GNU General Public License v3.0") }
            items(paragraphs) { paragraph ->
                Text(
                    text = paragraph,
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                )
            }
        }
    }
}
