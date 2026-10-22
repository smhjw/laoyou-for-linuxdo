package org.linuxdo.android.ui.screen

import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.SaveAs
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.vector.ImageVector
import org.linuxdo.android.ui.design.AppTheme
import org.linuxdo.android.ui.design.*
import androidx.compose.material3.MaterialTheme

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.linuxdo.android.data.DiscourseTagInfo
import org.linuxdo.android.ui.design.ListDivider
import android.net.Uri
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.linuxdo.android.ui.design.LoadingIndicator
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.SolidColor
import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.EditText
import android.text.style.ReplacementSpan
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import org.linuxdo.android.data.Category
import org.linuxdo.android.data.compactCategoryGroups
import org.linuxdo.android.data.fixedTagsForCategory
import org.linuxdo.android.data.flattenCategories
import org.linuxdo.android.ui.*
import org.linuxdo.android.ui.design.TextAction
import org.linuxdo.android.ui.editor.RichMarkdownEditorView

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopicCreatorSheet(
    state: TopicCreatorUiState,
    categories: List<Category>,
    onTitleChange: (String) -> Unit,
    onRawChange: (String) -> Unit,
    onCategoryChange: (Int?) -> Unit,
    onTagsChange: (List<String>) -> Unit,
    onSearchTags: (String) -> Unit = {},
    onAttach: (Uri) -> Unit,
    onRemoveAttachment: (String) -> Unit = {},
    onSaveDraft: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
    onClearNotice: () -> Unit,
    onOpenDrafts: (() -> Unit)? = null,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    var showCloseConfirmDialog by remember { mutableStateOf(false) }
    var previewingImage by remember { mutableStateOf<String?>(null) }

    var editorView by remember { mutableStateOf<RichMarkdownEditorView?>(null) }

    LaunchedEffect(state.attachments) {
        editorView?.syncAttachments(state.attachments)
    }

    val appendTextToken = { token: String ->
        editorView?.insertToken(token)
    }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null) onAttach(uri) },
    )
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri -> if (uri != null) onAttach(uri) },
    )

    val attemptClose = {
        if (state.savingDraft || state.submitting) {
            Unit
        } else if (state.hasUnsavedChanges) {
            showCloseConfirmDialog = true
        } else {
            onClose()
        }
    }

    BackHandler {
        when {
            showEmojiPicker -> showEmojiPicker = false
            else -> attemptClose()
        }
    }

    LaunchedEffect(state.notice) {
        if (state.notice != null) {
            kotlinx.coroutines.delay(2200)
            onClearNotice()
        }
    }

    val flattenedCategories = remember(categories) {
        flattenCategories(categories)
    }
    val categoryGroups = remember(flattenedCategories) {
        compactCategoryGroups(flattenedCategories)
    }
    val selectedCategory = remember(flattenedCategories, state.categoryId) {
        state.categoryId?.let { id -> flattenedCategories.firstOrNull { it.id.toInt() == id } }
    }
    val selectedGroup = remember(categoryGroups, state.categoryId) {
        val targetId = state.categoryId ?: return@remember null
        categoryGroups.firstOrNull { (parent, levels) ->
            parent.id == targetId || levels.any { it.id == targetId }
        }
    }
    val selectedGroupVariants = remember(selectedGroup) {
        if (selectedGroup == null) {
            emptyList()
        } else {
            (listOf(selectedGroup.first) + selectedGroup.second.sortedBy { categoryLevelOf(it) })
                .distinctBy { it.id }
                .filter { categoryLevelOf(it) in 0..3 }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        // 顶部操作栏:关闭 + 标题 + 保存 + 发布
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(start = 4.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { attemptClose() },
                enabled = !state.savingDraft && !state.submitting,
            ) {
                Icon(Icons.Filled.Close, contentDescription = "关闭")
            }

            Text(
                text = if (state.isReplyDraft) "编辑回复草稿" else "创建新话题",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )

            // 还没写内容时这里是「草稿」入口(接着写之前存下的);一旦开始写就换成「保存」。
            if (onOpenDrafts != null && !state.isReplyDraft && !state.hasUnsavedChanges && !state.savingDraft) {
                TextButton(onClick = onOpenDrafts) {
                    Icon(Icons.Outlined.Drafts, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("草稿")
                }
            } else {
                TextButton(
                    onClick = {
                        editorView?.exportToMarkdown()?.let { onRawChange(it) }
                        onSaveDraft(false)
                    },
                    enabled = state.canSaveDraft,
                ) {
                    Text(if (state.savingDraft) "保存中…" else "保存")
                }
            }

            Spacer(Modifier.width(4.dp))

            Button(
                onClick = {
                    editorView?.exportToMarkdown()?.let { onRawChange(it) }
                    onSubmit()
                },
                enabled = state.canSubmit,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                if (state.submitting) {
                    LoadingIndicator(diameter = 16.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(if (state.submitting) "发布中…" else if (state.isReplyDraft) "发送" else "发布")
            }
        }

        ListDivider()

        // 错误或保存成功轻提示
        state.error?.let { err ->
            ComposerBanner(
                text = err,
                icon = Icons.Outlined.ErrorOutline,
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
            )
        }

        state.notice?.let { msg ->
            ComposerBanner(
                text = msg,
                icon = Icons.Outlined.CheckCircle,
                container = AppTheme.colors.successContainer,
                content = AppTheme.colors.onSuccessContainer,
            )
        }

        if (!state.isReplyDraft) {
            // 分类与标签并排,两个等高的 outlined 选择框
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 左侧分类胶囊：宽度充裕，未选择时也具有良好开阔度
                val dotColor = selectedCategory?.let { parseCategoryColor(it.color) } ?: MaterialTheme.colorScheme.primary
                val baseName = selectedCategory?.let { categoryBaseNameOf(it.name) }
                val level = selectedCategory?.let { categoryLevelOf(it) } ?: 0
                val isRestricted = (level > 0) || (selectedCategory?.readRestricted == true)

                Row(
                    modifier = Modifier
                        .weight(1.15f)
                        .height(40.dp)
                        .clip(MaterialTheme.shapes.small)
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                        .clickable { showCategoryDialog = true }
                        .padding(start = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selectedCategory != null) {
                        Box(
                            Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(dotColor),
                        )
                        Spacer(Modifier.width(6.dp))
                        if (isRestricted) {
                            ComposerLockIcon(
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            text = if (level > 0) "$baseName Lv.$level" else baseName.orEmpty(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Text(
                            text = "选择分类",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 右侧标签胶囊：与分类胶囊大小样式保持完全一致，根据当前分类下拉选择
                Box(modifier = Modifier.weight(1f)) {
                    var showTagDropdown by remember { mutableStateOf(false) }
                    var tagSearchQuery by remember { mutableStateOf("") }
                    val candidateTags: List<DiscourseTagInfo> = remember(state.availableTags, selectedCategory) {
                        if (state.availableTags.isNotEmpty()) state.availableTags
                        else fixedTagsForCategory(selectedCategory).map { DiscourseTagInfo(name = it, text = it, count = 0) }
                    }
                    val filteredCandidateTags = remember(candidateTags, tagSearchQuery) {
                        if (tagSearchQuery.isBlank()) candidateTags
                        else candidateTags.filter {
                            it.name.contains(tagSearchQuery.trim(), ignoreCase = true) ||
                            it.text.contains(tagSearchQuery.trim(), ignoreCase = true)
                        }
                    }

                    val tagsSelected = state.tags.isNotEmpty()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(if (tagsSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            .then(
                                if (tagsSelected) Modifier
                                else Modifier.border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small),
                            )
                            .clickable { showTagDropdown = true }
                            .padding(start = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val tagContent = if (tagsSelected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant
                        ComposerTagIcon(tint = tagContent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (tagsSelected) state.tags.joinToString(", ") else "添加标签",
                            style = MaterialTheme.typography.labelLarge,
                            color = tagContent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            if (tagsSelected) Icons.Filled.ArrowDropDown else Icons.Filled.Add,
                            contentDescription = null,
                            tint = tagContent,
                            modifier = Modifier.padding(4.dp).size(18.dp),
                        )
                    }

                    // 下拉选择菜单（根据分类动态调取 /tags/filter/search 返回的候选标签）
                    DropdownMenu(
                        expanded = showTagDropdown,
                        onDismissRequest = {
                            showTagDropdown = false
                            tagSearchQuery = ""
                        },
                        modifier = Modifier
                            .widthIn(min = 250.dp, max = 320.dp)
                            .heightIn(max = 360.dp),
                    ) {
                        // 顶部必选标签组提示（如果有）
                        state.requiredTagGroupName?.let { groupName ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.secondaryContainer)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = "要求至少选择 ${state.requiredTagMinCount} 个「$groupName」标签",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                            }
                        }

                        // 顶部搜索过滤输入框
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .height(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            BasicTextField(
                                value = tagSearchQuery,
                                onValueChange = { query ->
                                    tagSearchQuery = query
                                    onSearchTags(query)
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.weight(1f),
                                decorationBox = { innerField ->
                                    Box {
                                        if (tagSearchQuery.isEmpty()) {
                                            Text(
                                                text = "搜索标签",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        innerField()
                                    }
                                },
                            )
                        }

                        if (state.loadingTags) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                LoadingIndicator(diameter = 14.dp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "加载标签中…",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (filteredCandidateTags.isEmpty() && !state.loadingTags) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (tagSearchQuery.isBlank()) "当前分类无可选标签" else "未找到匹配标签",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                onClick = {},
                                enabled = false,
                            )
                        } else {
                            for (tagItem in filteredCandidateTags) {
                                val isSelected = tagItem.name in state.tags
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f, fill = false),
                                            ) {
                                                Text(
                                                    text = tagItem.text.ifBlank { tagItem.name },
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f, fill = false),
                                                )
                                                if (tagItem.count > 0) {
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        text = "×${tagItem.count}",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    Icons.Filled.Check,
                                                    contentDescription = "已选",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        if (isSelected) onTagsChange(state.tags - tagItem.name)
                                        else onTagsChange(state.tags + tagItem.name)
                                    },
                                )
                            }
                        }

                        if (state.tags.isNotEmpty()) {
                            ListDivider()
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "清空已选标签 (${state.tags.size})",
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Outlined.ClearAll, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                },
                                onClick = { onTagsChange(emptyList()) },
                            )
                        }
                    }
                }
            }

            ListDivider()

            // 标题输入框 (带右侧 0/6 字符提示)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = state.title,
                    onValueChange = onTitleChange,
                    textStyle = MaterialTheme.typography.titleLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 2,
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        Box(Modifier.fillMaxWidth()) {
                            if (state.title.isEmpty()) {
                                Text(
                                    text = "输入标题，或在此处粘贴链接",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
                Spacer(Modifier.width(8.dp))
                val titleLen = state.title.length
                Text(
                    text = "$titleLen / 6",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (titleLen < 6) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ListDivider()
        } else {
            // 回复草稿信息提示
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Reply,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = state.title.ifBlank { "回复话题 #${state.replyTopicId ?: ""}" },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ListDivider()
        }

        // 正文编辑区（所见即所得图文流：基于原生 EditText + ReplacementSpan，全光标连续穿越）
        val textColorInt = MaterialTheme.colorScheme.onSurface.toArgb()
        val hintColorInt = MaterialTheme.colorScheme.outline.toArgb()

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    RichMarkdownEditorView(ctx).apply {
                        updateThemeColors(textColorInt, hintColorInt)
                        setInitialMarkdown(state.raw, state.attachments)
                        setOnMarkdownChangeListener { newRaw -> onRawChange(newRaw) }
                        setOnRemoveAttachmentListener { mediaId -> onRemoveAttachment(mediaId) }
                        setOnPreviewImageListener { previewUrl -> previewingImage = previewUrl }
                        editorView = this
                    }
                },
                update = { view ->
                    view.updateThemeColors(textColorInt, hintColorInt)
                },
            )

            // 图片上传中悬浮提示卡片
            if (state.uploading) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    shadowElevation = 3.dp,
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LoadingIndicator(diameter = 18.dp, color = MaterialTheme.colorScheme.inversePrimary)
                        Spacer(Modifier.width(12.dp))
                        Text(text = "正在上传图片并插入正文…", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        ListDivider()

        // 底部工具栏:图片上传、文件上传、Emoji、快捷 Markdown、字数提示
        val canPickMedia = !state.uploading && !state.submitting && !state.savingDraft
        val toolTint = MaterialTheme.colorScheme.onSurfaceVariant
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                enabled = canPickMedia,
            ) { ComposerImageIcon(tint = LocalContentColor.current) }

            IconButton(onClick = { filePicker.launch(arrayOf("*/*")) }, enabled = canPickMedia) {
                ComposerAttachmentIcon(tint = LocalContentColor.current)
            }

            IconToggleButton(
                checked = showEmojiPicker,
                onCheckedChange = {
                    if (it) keyboard?.hide()
                    showEmojiPicker = it
                },
                enabled = canPickMedia,
                colors = IconButtonDefaults.iconToggleButtonColors(
                    contentColor = toolTint,
                    checkedContentColor = MaterialTheme.colorScheme.primary,
                ),
            ) { ComposerEmojiIcon(tint = LocalContentColor.current) }

            IconButton(onClick = { appendTextToken("**粗体文字**") }, enabled = canPickMedia) {
                ComposerBoldIcon(tint = LocalContentColor.current)
            }

            Spacer(Modifier.weight(1f))

            if (state.uploading) {
                LoadingIndicator(diameter = 14.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "上传中…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "${state.raw.length} 字",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (showEmojiPicker) {
            ListDivider()
            QuickEmojiPicker(
                onSelect = { token -> appendTextToken(token) },
            )
        }
    }

    // 分类选择弹窗
    if (showCategoryDialog) {
        CategoryPickerDialog(
            groups = categoryGroups,
            selectedCategoryId = state.categoryId,
            onSelect = { id ->
                val newCat = categories.firstOrNull { it.id.toInt() == id }
                val newAllowed = fixedTagsForCategory(newCat)
                val validTags = state.tags.filter { it in newAllowed }
                if (validTags != state.tags) {
                    onTagsChange(validTags)
                }
                onCategoryChange(id)
                showCategoryDialog = false
            },
            onDismiss = { showCategoryDialog = false },
        )
    }

    // 标签选择弹窗（从固定列表中勾选，不同分类不同标签）
    if (showTagDialog) {
        TagPickerDialog(
            category = selectedCategory,
            currentTags = state.tags,
            onConfirm = { newTags ->
                onTagsChange(newTags)
                showTagDialog = false
            },
            onDismiss = { showTagDialog = false },
        )
    }

    // 关闭二次确认:点对话框外继续编辑
    if (showCloseConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showCloseConfirmDialog = false },
            icon = { Icon(Icons.Outlined.SaveAs, contentDescription = null) },
            title = { Text("保存草稿？") },
            text = { Text("当前有未保存的内容，关闭前是否将其保存为草稿？") },
            confirmButton = {
                TextButton(onClick = {
                    showCloseConfirmDialog = false
                    editorView?.exportToMarkdown()?.let { onRawChange(it) }
                    onSaveDraft(true)
                }) { Text("保存并退出") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCloseConfirmDialog = false
                        onClose()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("放弃更改") }
            },
        )
    }

    previewingImage?.let { previewUrl ->
        org.linuxdo.android.ui.screen.ImageViewer(url = previewUrl) { previewingImage = null }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPickerDialog(
    groups: List<Pair<Category, List<Category>>>,
    selectedCategoryId: Int?,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 560.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "选择话题分类",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "关闭") }
            }

            Spacer(Modifier.height(8.dp))
            ListDivider()

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
            ) {
                items(groups, key = { it.first.id }) { (parent, levels) ->
                    val baseName = categoryBaseNameOf(parent.name)
                    val dotColor = parseCategoryColor(parent.color) ?: MaterialTheme.colorScheme.primary
                    val variants = remember(parent, levels) {
                        (listOf(parent) + levels.sortedBy { categoryLevelOf(it) })
                            .distinctBy { it.id }
                            .filter { categoryLevelOf(it) in 0..3 }
                    }
                    val isGroupSelected = selectedCategoryId != null && variants.any { it.id.toInt() == selectedCategoryId }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isGroupSelected) MaterialTheme.colorScheme.secondaryContainer
                                else Color.Transparent,
                            )
                            .clickable { onSelect(parent.id.toInt()) }
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(dotColor),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = baseName,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )

                            if (variants.size > 1) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    for (variant in variants) {
                                        val lvl = categoryLevelOf(variant)
                                        val isVariantSelected = selectedCategoryId == variant.id.toInt()
                                        val (pillBg, _, pillFg) = levelPillColors(lvl, AppTheme.isDark)
                                        Box(
                                            modifier = Modifier
                                                .clip(MaterialTheme.shapes.small)
                                                .background(if (isVariantSelected) MaterialTheme.colorScheme.primary else pillBg)
                                                .clickable { onSelect(variant.id.toInt()) }
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                        ) {
                                            Text(
                                                text = "Lv.$lvl",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (isVariantSelected) MaterialTheme.colorScheme.onPrimary else pillFg,
                                            )
                                        }
                                    }
                                }
                            } else if (selectedCategoryId == parent.id.toInt()) {
                                Icon(Icons.Filled.Check, contentDescription = "已选", tint = MaterialTheme.colorScheme.primary)
                            }
                        }

                        if (!parent.description.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = parent.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagPickerDialog(
    category: Category?,
    currentTags: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var workingTags by remember(currentTags) { mutableStateOf(currentTags.toSet()) }
    val availableTags = remember(category) { fixedTagsForCategory(category) }
    val categoryName = category?.let { categoryBaseNameOf(it.name) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 520.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "选择标签",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (categoryName != null) "分类「$categoryName」可选固定标签" else "可选固定标签列表",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            if (availableTags.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "当前分类暂无可关联标签",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        for (tag in availableTags) {
                            val isSelected = tag in workingTags
                            FilterChip(
                                selected = isSelected,
                                onClick = { workingTags = if (isSelected) workingTags - tag else workingTags + tag },
                                label = { Text(tag) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                                } else null,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "已选 ${workingTags.size} 个标签",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { workingTags = emptySet() }) { Text("清空") }
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(onClick = { onConfirm(workingTags.toList()) }) { Text("确定") }
                }
            }
        }
    }
}

/** 编辑器顶部的错误 / 成功提示条。 */
@Composable
private fun ComposerBanner(text: String, icon: ImageVector, container: Color, content: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(container)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium, color = content, modifier = Modifier.weight(1f))
    }
}
