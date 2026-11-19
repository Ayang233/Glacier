package com.ayang.glacier.feature.home

import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayang.glacier.GlacierApplication
import com.ayang.glacier.R
import com.ayang.glacier.domain.AppEntry
import com.ayang.glacier.domain.AppSnapshot
import com.ayang.glacier.domain.DEFAULT_TAG_ID
import com.ayang.glacier.domain.Tag
import com.ayang.glacier.feature.common.AppAction
import com.ayang.glacier.feature.common.AppActionRunner
import com.ayang.glacier.feature.common.AppActionSheet
import com.ayang.glacier.feature.common.AppIconView
import com.ayang.glacier.feature.common.AppSearchResults
import com.ayang.glacier.feature.common.SheetActionRow
import com.ayang.glacier.platform.UiFeedback
import com.ayang.glacier.ui.LocalGlassActive
import com.ayang.glacier.ui.LocalRootBackdrop
import com.ayang.glacier.ui.components.EmptyState
import com.ayang.glacier.ui.components.PageFrame
import com.ayang.glacier.ui.components.TopBarAction
import com.ayang.glacier.ui.components.pageContentPadding
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.glass.GlassBottomSheet
import top.yukonga.miuix.kmp.glass.GlassDialog
import top.yukonga.miuix.kmp.glass.GlassDialogButton
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.glass.GlassFloatingActionButton
import top.yukonga.miuix.kmp.glass.GlassSearchOverlay
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.glass.Add
import top.yukonga.miuix.kmp.icon.glass.Close
import top.yukonga.miuix.kmp.icon.glass.Edit
import top.yukonga.miuix.kmp.icon.glass.More
import top.yukonga.miuix.kmp.icon.glass.MultiSelect
import top.yukonga.miuix.kmp.icon.glass.QuietMode
import top.yukonga.miuix.kmp.icon.glass.Search
import top.yukonga.miuix.kmp.icon.glass.Tags
import top.yukonga.miuix.kmp.icon.glass.UnlockOutline
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 主页：标签 + 应用网格 + 多选批量操作。
 *
 * 页面结构由 [PageFrame] 统一：录制层在正文，顶栏在 topBar 槽，弹层在 overlay 槽。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as GlacierApplication
    val repository = app.repository
    val preferences = app.preferences
    val snapshot: AppSnapshot = repository.snapshot

    var selectedTag by rememberSaveable { mutableIntStateOf(DEFAULT_TAG_ID) }
    var selecting by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(emptySet<String>()) }
    var searchOpen by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<String?>(null) }
    var tagEditorOpen by remember { mutableStateOf(false) }
    var tagNameDialog by remember { mutableStateOf<TagNameRequest?>(null) }
    var deleteTagTarget by remember { mutableStateOf<Tag?>(null) }
    var deferredTarget by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val runner = remember(repository) { AppActionRunner(context, repository) }
    val glassActive = LocalGlassActive.current
    val backdrop = LocalRootBackdrop.current

    // 标签被删除后，选中项要回到默认标签，否则页面会空白。
    if (snapshot.tags.none { it.id == selectedTag }) {
        selectedTag = DEFAULT_TAG_ID
    }

    val entries = remember(snapshot, selectedTag) { repository.homeApps(selectedTag) }
    val tagLabel = snapshot.tags.firstOrNull { it.id == selectedTag }?.let { repository.tagLabel(it) }
        ?: stringResource(R.string.label_default)

    val inSelection = selecting || selection.isNotEmpty()

    PageFrame(
        title = if (inSelection) stringResource(R.string.selected_count, selection.size)
        else stringResource(R.string.app_name),
        subtitle = if (inSelection) "" else "$tagLabel · ${stringResource(R.string.app_count, entries.size)}",
        scrollBehavior = scrollBehavior,
        topBarAlpha = if (searchOpen) 0f else 1f,
        topBarActions = {
            if (inSelection) {
                TopBarAction(MiuixIcons.Glass.QuietMode, stringResource(R.string.action_freeze)) {
                    val targets = selection
                    scope.launch {
                        val outcome = repository.setFrozenBatch(targets, true)
                        reportBatch(outcome, true, context, targets.size)
                        selecting = false
                        selection = emptySet()
                    }
                }
                TopBarAction(MiuixIcons.Glass.UnlockOutline, stringResource(R.string.action_unfreeze)) {
                    val targets = selection
                    scope.launch {
                        val outcome = repository.setFrozenBatch(targets, false)
                        reportBatch(outcome, false, context, targets.size)
                        selecting = false
                        selection = emptySet()
                    }
                }
                TopBarAction(MiuixIcons.Glass.Tags, stringResource(R.string.action_edit_tags)) {
                    if (selection.size == 1) {
                        tagNameDialog = TagNameRequest.Assign(selection.first())
                    }
                }
                TopBarAction(MiuixIcons.Glass.More, stringResource(R.string.more)) {
                    if (selection.size == 1) actionTarget = selection.first()
                }
                TopBarAction(MiuixIcons.Glass.Close, stringResource(R.string.close)) {
                    selecting = false
                    selection = emptySet()
                }
            } else {
                TopBarAction(MiuixIcons.Glass.Search, stringResource(R.string.search)) {
                    searchOpen = true
                }
                TopBarAction(MiuixIcons.Glass.MultiSelect, stringResource(R.string.action_select)) {
                    selecting = true
                }
                TopBarAction(MiuixIcons.Glass.Edit, stringResource(R.string.title_tag_editor)) {
                    tagEditorOpen = true
                }
            }
        },
        floatingActionButton = { fabBackdrop ->
            if (!inSelection && entries.isNotEmpty()) {
                GlassFloatingActionButton(
                    onClick = {
                        val targets = entries.filter { !it.frozen }.map { it.packageName }
                        val unfreeze = targets.isEmpty()
                        val set = if (unfreeze) entries.map { it.packageName } else targets
                        scope.launch {
                            val outcome = repository.setFrozenBatch(set, !unfreeze)
                            reportBatch(outcome, !unfreeze, context, set.size)
                        }
                    },
                    backdrop = fabBackdrop,
                ) {
                    top.yukonga.miuix.kmp.basic.Icon(
                        imageVector = if (entries.any { !it.frozen }) MiuixIcons.Glass.QuietMode
                        else MiuixIcons.Glass.UnlockOutline,
                        contentDescription = stringResource(R.string.action_freeze),
                        modifier = Modifier.size(24.dp),
                        tint = MiuixTheme.colorScheme.onPrimary,
                    )
                }
            }
        },
        overlay = {
            if (searchOpen) {
                GlassSearchOverlay(
                    expanded = true,
                    onDismissRequest = { searchOpen = false },
                    label = stringResource(R.string.search_hint),
                    blurEnabled = glassActive,
                ) { query ->
                    AppSearchResults(
                        query = query,
                        onPick = { entry ->
                            searchOpen = false
                            scope.launch { runner.run(entry, AppAction.Launch) }
                        },
                    )
                }
            }

            AppActionSheet(
                entry = actionTarget?.let { pkg -> snapshot.all.firstOrNull { it.packageName == pkg } },
                show = actionTarget != null,
                inHome = true,
                onDismiss = { actionTarget = null },
                onAction = { action ->
                    val entry = actionTarget?.let { pkg -> snapshot.all.firstOrNull { it.packageName == pkg } }
                    actionTarget = null
                    if (entry != null) {
                        when (action) {
                            AppAction.EditTags -> tagNameDialog = TagNameRequest.Assign(entry.packageName)
                            AppAction.DeferredFreeze -> deferredTarget = entry.packageName
                            else -> scope.launch {
                                runner.run(entry, action)
                                repository.refresh()
                            }
                        }
                    }
                },
            )

            if (tagEditorOpen) {
                TagEditorSheet(
                    tags = snapshot.tags,
                    onDismiss = { tagEditorOpen = false },
                    onNew = { tagNameDialog = TagNameRequest.Create },
                    onRename = { tag -> tagNameDialog = TagNameRequest.Rename(tag) },
                    onDelete = { tag -> deleteTagTarget = tag },
                    labelOf = { repository.tagLabel(it) },
                )
            }

            tagNameDialog?.let { request ->
                TagNameDialog(
                    request = request,
                    onDismiss = { tagNameDialog = null },
                    onConfirm = { name ->
                        scope.launch {
                            when (request) {
                                is TagNameRequest.Create -> repository.addTag(name)
                                is TagNameRequest.Rename -> repository.renameTag(request.tag.id, name)
                                is TagNameRequest.Assign -> {
                                    val current = repository.entryOf(request.packageName)?.tagIds ?: emptyList()
                                    val tag = snapshot.tags.firstOrNull { it.name == name }
                                    if (tag != null) {
                                        val next = if (tag.id in current) {
                                            current - tag.id
                                        } else {
                                            current + tag.id
                                        }
                                        repository.setTags(request.packageName, next)
                                    }
                                }
                            }
                            tagNameDialog = null
                        }
                    },
                )
            }

            deleteTagTarget?.let { tag ->
                GlassDialog(
                    onDismissRequest = { deleteTagTarget = null },
                    show = true,
                    backdrop = backdrop.takeIf { glassActive },
                    title = stringResource(R.string.action_delete_tag),
                    summary = stringResource(R.string.delete_tag_message),
                ) {
                    GlassDialogButton(
                        onClick = {
                            scope.launch { repository.deleteTag(tag.id) }
                            deleteTagTarget = null
                            if (selectedTag == tag.id) selectedTag = DEFAULT_TAG_ID
                        },
                        role = GlassDialogButtonRole.Destructive,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.delete), style = MiuixTheme.textStyles.button)
                    }
                    GlassDialogButton(
                        onClick = { deleteTagTarget = null },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.cancel), style = MiuixTheme.textStyles.button)
                    }
                }
            }

            deferredTarget?.let { packageName ->
                DeferredSheet(
                    onDismiss = { deferredTarget = null },
                    onPick = { minutes ->
                        com.ayang.glacier.platform.auto.AutoFreezeScheduler.scheduleDeferredFreeze(
                            context, packageName, true, minutes,
                        )
                        UiFeedback.toast(context, R.string.action_deferred_freeze)
                        deferredTarget = null
                    },
                )
            }
        },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (preferences.compactGrid) 5 else 4),
            modifier = Modifier.fillMaxSize(),
            contentPadding = pageContentPadding(padding, horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (snapshot.tags.size > 1) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    TagRow(
                        tags = snapshot.tags,
                        selectedTag = selectedTag,
                        labelOf = { repository.tagLabel(it) },
                        onSelect = { selectedTag = it },
                    )
                }
            }

            if (entries.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(text = stringResource(R.string.empty_home))
                }
            } else {
                items(entries, key = { it.packageName }) { entry ->
                    HomeAppItem(
                        entry = entry,
                        labelSize = preferences.homeLabelSize,
                        selected = entry.packageName in selection,
                        onClick = {
                            if (inSelection) {
                                selection = if (entry.packageName in selection) {
                                    selection - entry.packageName
                                } else {
                                    selection + entry.packageName
                                }
                            } else {
                                scope.launch { runner.run(entry, AppAction.Launch) }
                            }
                        },
                        onLongClick = {
                            selecting = true
                            selection = if (entry.packageName in selection) {
                                selection - entry.packageName
                            } else {
                                selection + entry.packageName
                            }
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeAppItem(
    entry: AppEntry,
    labelSize: Float,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            AppIconView(
                packageName = entry.packageName,
                size = 56.dp,
                frozen = entry.frozen,
                modifier = Modifier
                    .then(
                        if (selected) {
                            Modifier.border(
                                width = 2.dp,
                                color = MiuixTheme.colorScheme.primary,
                                shape = RoundedCornerShape(18.dp),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .padding(2.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = entry.label,
            style = MiuixTheme.textStyles.footnote1,
            color = if (entry.frozen) MiuixTheme.colorScheme.onSurfaceVariantSummary
            else MiuixTheme.colorScheme.onSurface,
            fontSize = labelSize.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TagRow(
    tags: List<Tag>,
    selectedTag: Int,
    labelOf: (Tag) -> String,
    onSelect: (Int) -> Unit,
) {
    val names = remember(tags) { tags.map(labelOf) }
    val index = tags.indexOfFirst { it.id == selectedTag }.coerceAtLeast(0)
    TabRow(
        tabs = names,
        selectedTabIndex = index,
        onTabSelected = { onSelect(tags[it].id) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TagEditorSheet(
    tags: List<Tag>,
    onDismiss: () -> Unit,
    onNew: () -> Unit,
    onRename: (Tag) -> Unit,
    onDelete: (Tag) -> Unit,
    labelOf: (Tag) -> String,
) {
    val backdrop = LocalRootBackdrop.current.takeIf { LocalGlassActive.current }
    GlassBottomSheet(
        onDismissRequest = onDismiss,
        show = true,
        backdrop = backdrop,
        title = stringResource(R.string.title_tag_editor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            tags.forEach { tag ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = labelOf(tag),
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    TopBarAction(MiuixIcons.Glass.Edit, stringResource(R.string.rename)) { onRename(tag) }
                }
            }
            Spacer(Modifier.height(8.dp))
            SheetActionRow(
                icon = MiuixIcons.Glass.Add,
                label = stringResource(R.string.action_new_tag),
                onClick = onNew,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun TagNameDialog(
    request: TagNameRequest,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val initial = when (request) {
        is TagNameRequest.Rename -> request.tag.name
        else -> ""
    }
    var text by remember { mutableStateOf(initial) }
    val titleRes = when (request) {
        is TagNameRequest.Create -> R.string.action_new_tag
        is TagNameRequest.Rename -> R.string.action_rename_tag
        is TagNameRequest.Assign -> R.string.action_edit_tags
    }

    GlassDialog(
        onDismissRequest = onDismiss,
        show = true,
        backdrop = LocalRootBackdrop.current.takeIf { LocalGlassActive.current },
        title = stringResource(titleRes),
    ) {
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(R.string.tag_name_hint),
        )
        Spacer(Modifier.height(12.dp))
        GlassDialogButton(
            onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
            role = GlassDialogButtonRole.Primary,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.save), style = MiuixTheme.textStyles.button)
        }
        GlassDialogButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.cancel), style = MiuixTheme.textStyles.button)
        }
    }
}

@Composable
private fun DeferredSheet(
    onDismiss: () -> Unit,
    onPick: (Long) -> Unit,
) {
    val options = listOf(5L, 15L, 30L, 60L)
    GlassBottomSheet(
        onDismissRequest = onDismiss,
        show = true,
        backdrop = LocalRootBackdrop.current.takeIf { LocalGlassActive.current },
        title = stringResource(R.string.action_deferred_freeze),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { minutes ->
                SheetActionRow(
                    icon = MiuixIcons.Glass.QuietMode,
                    label = "$minutes min",
                    onClick = { onPick(minutes) },
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private sealed interface TagNameRequest {
    data object Create : TagNameRequest
    data class Rename(val tag: Tag) : TagNameRequest
    data class Assign(val packageName: String) : TagNameRequest
}

private fun reportBatch(
    outcome: com.ayang.glacier.system.FreezeOutcome,
    frozen: Boolean,
    context: android.content.Context,
    requested: Int,
) {
    when (outcome) {
        is com.ayang.glacier.system.FreezeOutcome.Applied -> {
            if (outcome.count == 0) {
                UiFeedback.toast(context, R.string.permission_denied)
            } else {
                val res = if (frozen) R.string.msg_freeze_many else R.string.msg_unfreeze_many
                UiFeedback.toast(context, context.getString(res, outcome.count.toString()))
            }
        }

        com.ayang.glacier.system.FreezeOutcome.Denied ->
            UiFeedback.toast(context, R.string.permission_denied)
    }
}
