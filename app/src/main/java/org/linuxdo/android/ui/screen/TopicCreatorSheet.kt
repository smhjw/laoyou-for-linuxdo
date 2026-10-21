package org.linuxdo.android.ui.screen

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
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        // 顶部操作栏：关闭 ✕ + 标题 + 保存 + 发布
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable(enabled = !state.savingDraft && !state.submitting) { attemptClose() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "✕",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.width(10.dp))

            Text(
                text = if (state.isReplyDraft) "编辑回复草稿" else "创建新话题",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            // 保存草稿按钮
            val canSave = state.canSaveDraft
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (canSave) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    .clickable(enabled = canSave) {
                        editorView?.exportToMarkdown()?.let { onRawChange(it) }
                        onSaveDraft(false)
                    }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (state.savingDraft) "保存中…" else "保存",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = if (canSave) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
            }

            Spacer(Modifier.width(8.dp))

            // 发布按钮
            val canPublish = state.canSubmit
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (canPublish) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    )
                    .clickable(enabled = canPublish) {
                        editorView?.exportToMarkdown()?.let { onRawChange(it) }
                        onSubmit()
                    }
                    .padding(horizontal = 15.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (state.submitting) "发布中…" else if (state.isReplyDraft) "发送" else "发布",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                )
            }
        }

        ListDivider()

        // 错误或保存成功轻提示
        state.error?.let { err ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        state.notice?.let { msg ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF52C41A).copy(alpha = 0.12f))
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "✓ $msg",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = Color(0xFF389E0D),
                )
            }
        }

        if (!state.isReplyDraft) {
            // 分类与标签在同一行，高 38.dp，RoundedCornerShape(50) 胶囊形态，样式高度一致，无黄色底色
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                        .height(38.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(50))
                        .clickable { showCategoryDialog = true }
                        .padding(horizontal = 14.dp),
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
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    } else {
                        Text(
                            text = "选择分类…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "▾",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), RoundedCornerShape(50))
                            .clickable { showTagDropdown = true }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ComposerTagIcon(
                            tint = if (state.tags.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        if (state.tags.isEmpty()) {
                            Text(
                                text = "可选标签…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "+",
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp, fontWeight = FontWeight.Normal),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = state.tags.joinToString(", "),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "▾",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
                            .heightIn(max = 340.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerLow),
                    ) {
                        // 顶部必选标签组提示（如果有）
                        state.requiredTagGroupName?.let { groupName ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                            ) {
                                Text(
                                    text = "要求至少选择 ${state.requiredTagMinCount} 个「$groupName」标签",
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        // 顶部搜索过滤输入框（对齐参考图）
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        ) {
                            BasicTextField(
                                value = tagSearchQuery,
                                onValueChange = { query ->
                                    tagSearchQuery = query
                                    onSearchTags(query)
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { innerField ->
                                    Box {
                                        if (tagSearchQuery.isEmpty()) {
                                            Text(
                                                text = "搜索…",
                                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                                                color = MaterialTheme.colorScheme.outline,
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
                                                // 浅灰背景标签徽章块，对齐参考图
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(
                                                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                            else MaterialTheme.colorScheme.surfaceContainerHighest
                                                        )
                                                        .padding(horizontal = 7.dp, vertical = 3.dp),
                                                ) {
                                                    Text(
                                                        text = tagItem.text.ifBlank { tagItem.name },
                                                        style = MaterialTheme.typography.bodyMedium.copy(
                                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                                            fontSize = 13.5.sp,
                                                        ),
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                    )
                                                }
                                                if (tagItem.count > 0) {
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        text = "x${tagItem.count}",
                                                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                            if (isSelected) {
                                                Text(
                                                    text = "✓",
                                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = MaterialTheme.colorScheme.primary,
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
                                        style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.error),
                                    )
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
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 2,
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        Box(Modifier.fillMaxWidth()) {
                            if (state.title.isEmpty()) {
                                Text(
                                    text = "输入标题，或在此处粘贴链接",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Normal,
                                    ),
                                    color = MaterialTheme.colorScheme.outline,
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
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = if (titleLen < 6) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ListDivider()
        } else {
            // 回复草稿信息提示
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = state.title.ifBlank { "回复话题 #${state.replyTopicId ?: ""}" },
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LoadingIndicator(diameter = 16.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "正在上传图片并插入正文…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        ListDivider()

        // 底部工具栏：图片上传、文件上传、Emoji、快捷 Markdown、字数提示
        val canPickMedia = !state.uploading && !state.submitting && !state.savingDraft
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 上传图片
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = canPickMedia) {
                        imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                contentAlignment = Alignment.Center,
            ) {
                ComposerImageIcon(
                    tint = if (canPickMedia) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
                )
            }

            Spacer(Modifier.width(4.dp))

            // 上传附件/文件
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = canPickMedia) {
                        filePicker.launch(arrayOf("*/*"))
                    },
                contentAlignment = Alignment.Center,
            ) {
                ComposerAttachmentIcon(
                    tint = if (canPickMedia) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
                )
            }

            Spacer(Modifier.width(4.dp))

            // Emoji 面板开关
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (showEmojiPicker) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent,
                    )
                    .clickable(enabled = canPickMedia) {
                        if (!showEmojiPicker) keyboard?.hide()
                        showEmojiPicker = !showEmojiPicker
                    },
                contentAlignment = Alignment.Center,
            ) {
                ComposerEmojiIcon(
                    tint = if (showEmojiPicker) MaterialTheme.colorScheme.primary
                    else if (canPickMedia) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.outline,
                )
            }

            Spacer(Modifier.width(4.dp))

            // 粗体图标：与表情/图片/附件图标完全一致的 36.dp 规范与矢量绘制
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = canPickMedia) {
                        appendTextToken("**粗体文字**")
                    },
                contentAlignment = Alignment.Center,
            ) {
                ComposerBoldIcon(
                    tint = if (canPickMedia) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
                )
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
                    color = MaterialTheme.colorScheme.outline,
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

    // 关闭二次确认对话框
    // 关闭二次确认对话框（参考最新 UI：右上角关闭 X，居中标题与说明，垂直全宽胶囊按钮）
    if (showCloseConfirmDialog) {
        Dialog(onDismissRequest = { showCloseConfirmDialog = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // 右上角关闭 ✕（点击关闭弹框，继续编辑）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { showCloseConfirmDialog = false },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "✕",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Text(
                    text = "保存草稿？",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, fontSize = 22.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "当前有未保存的内容，关闭前是否\n将其保存为草稿？",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp, lineHeight = 22.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(24.dp))

                // 蓝色胶囊主按钮：保存草稿并退出
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
                            showCloseConfirmDialog = false
                            editorView?.exportToMarkdown()?.let { onRawChange(it) }
                            onSaveDraft(true)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "保存草稿并退出",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                        color = Color.White,
                    )
                }

                Spacer(Modifier.height(12.dp))

                // 浅红背景红字胶囊按钮：放弃更改
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f))
                        .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f), RoundedCornerShape(50))
                        .clickable {
                            showCloseConfirmDialog = false
                            onClose()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "放弃更改",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
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
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(vertical = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "选择话题分类",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TextAction("关闭", color = MaterialTheme.colorScheme.onSurfaceVariant, onClick = onDismiss)
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
                                if (isGroupSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                                else Color.Transparent,
                            )
                            .clickable { onSelect(parent.id.toInt()) }
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(11.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(dotColor),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = baseName,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
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
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isVariantSelected) MaterialTheme.colorScheme.primary else pillBg)
                                                .clickable { onSelect(variant.id.toInt()) }
                                                .padding(horizontal = 8.dp, vertical = 4.dp),
                                        ) {
                                            Text(
                                                text = "Lv.$lvl",
                                                style = MaterialTheme.typography.labelMedium.copy(
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                ),
                                                color = if (isVariantSelected) Color.White else pillFg,
                                            )
                                        }
                                    }
                                }
                            } else if (selectedCategoryId == parent.id.toInt()) {
                                Text(
                                    text = "✓",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        if (!parent.description.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = parent.description,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    ListDivider()
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
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "选择标签",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (categoryName != null) "分类「$categoryName」可选固定标签" else "可选固定标签列表",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextAction("关闭", color = MaterialTheme.colorScheme.onSurfaceVariant, onClick = onDismiss)
            }

            Spacer(Modifier.height(14.dp))
            ListDivider()
            Spacer(Modifier.height(14.dp))

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
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                                    )
                                    .clickable {
                                        workingTags = if (isSelected) {
                                            workingTags - tag
                                        } else {
                                            workingTags + tag
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = if (isSelected) "# $tag ✓" else "# $tag",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    ),
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            ListDivider()
            Spacer(Modifier.height(12.dp))

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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextAction("清空", color = MaterialTheme.colorScheme.onSurfaceVariant, onClick = { workingTags = emptySet() })
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable {
                                onConfirm(workingTags.toList())
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "确定",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}
