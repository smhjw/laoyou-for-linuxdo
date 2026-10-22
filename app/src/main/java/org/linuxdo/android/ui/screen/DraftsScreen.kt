package org.linuxdo.android.ui.screen

import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.SolidColor
import org.linuxdo.android.ui.design.*
import androidx.compose.material3.MaterialTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.linuxdo.android.ui.design.avatarShape
import org.linuxdo.android.ui.design.LoadingIndicator
import org.linuxdo.android.ui.design.LoadingBox
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import org.linuxdo.android.data.Category
import org.linuxdo.android.data.DraftItem
import org.linuxdo.android.data.avatarUrlOf
import org.linuxdo.android.ui.design.AppTopBar
import org.linuxdo.android.ui.format.RelativeTime
import org.linuxdo.android.ui.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DraftsScreen(
    state: DraftsUiState,
    categories: List<Category>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onEditDraft: (DraftItem) -> Unit,
    onDeleteDraft: (DraftItem) -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    val categoryById = remember(categories) { categories.associateBy { it.id.toInt() } }
    var draftPendingDelete by remember { mutableStateOf<DraftItem?>(null) }
    val pullState = rememberPullToRefreshState()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        AppTopBar(
            title = "我的草稿",
            onBack = onBack,
            trailing = { trailing() },
        )

        PullToRefreshBox(
            isRefreshing = state.loading && state.loaded,
            onRefresh = onRefresh,
            state = pullState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (state.loading && !state.loaded) {
                    item("loading") { LoadingBox() }
                }

                state.error?.let { message ->
                    item("error") {
                        ErrorNotice(message, onRefresh)
                    }
                }

                if (state.loaded && state.items.isEmpty() && state.error == null) {
                    item("empty") { DraftsEmpty() }
                }

                items(state.items, key = { it.draftKey }) { draft ->
                    val category = draft.categoryId?.let { categoryById[it] }
                    val isDeleting = draft.draftKey in state.deletingKeys
                    DraftCard(
                        draft = draft,
                        category = category,
                        isDeleting = isDeleting,
                        onEdit = { onEditDraft(draft) },
                        onDelete = { draftPendingDelete = draft },
                    )
                }
            }
        }
    }

    draftPendingDelete?.let { target ->
        val displayTitle = target.title.ifBlank { "无标题草稿" }
        ConfirmDialog(
            title = "删除草稿",
            text = "确认要删除草稿「$displayTitle」吗？删除后无法恢复。",
            icon = Icons.Outlined.Delete,
            confirmText = "删除",
            destructive = true,
            onDismiss = { draftPendingDelete = null },
            onConfirm = {
                draftPendingDelete = null
                onDeleteDraft(target)
            },
        )
    }
}

@Composable
private fun DraftsEmpty() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Outlined.EditNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(56.dp),
        )
        Text(
            text = "暂无保存的草稿",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "在发帖界面点击“保存”即可将草稿存放在这里",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DraftCard(
    draft: DraftItem,
    category: Category?,
    isDeleting: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    // DraftItem.avatarUrl 已由 TopicRepository 用 avatarUrlOf(avatar_template) 转成绝对地址
    val avatarUrl = draft.avatarUrl?.takeIf { it.isNotBlank() }
    val displayTitle = remember(draft.title, draft.action, draft.topicId) {
        when {
            draft.title.isNotBlank() -> draft.title
            draft.action != "createTopic" && draft.topicId != null -> "回复话题 #${draft.topicId}"
            else -> "（无标题草稿）"
        }
    }
    val summaryText = remember(draft.excerpt, draft.raw) {
        draft.excerpt
            .ifBlank { draft.raw }
            .replace(Regex("\\s+"), " ")
            .trim()
    }
    val timeText = remember(draft.createdAt) {
        RelativeTime.format(draft.createdAt)
    }

    Column(Modifier.fillMaxWidth().clickable(enabled = !isDeleting, onClick = onEdit)) {
        Column(Modifier.padding(start = 20.dp, top = 16.dp, end = 12.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                if (avatarUrl != null) {
                    UserAvatar(
                        avatarUrl = avatarUrl,
                        username = draft.username,
                        cornerRadius = 10.dp,
                        modifier = Modifier.size(40.dp),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = draft.username?.firstOrNull()?.uppercase() ?: "草",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (timeText.isNotBlank()) {
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (draft.action != "createTopic") {
                    DraftLabel(
                        text = if (draft.replyToPostNumber != null) "回复 #${draft.replyToPostNumber}" else "回复草稿",
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer,
                        leading = { Icon(Icons.AutoMirrored.Outlined.Reply, null, Modifier.size(14.dp)) },
                    )
                } else if (category != null) {
                    val dotColor = parseCategoryColor(category.color) ?: MaterialTheme.colorScheme.primary
                    DraftLabel(
                        text = categoryBaseNameOf(category.name),
                        container = MaterialTheme.colorScheme.surfaceContainerHighest,
                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                        leading = { Box(Modifier.size(8.dp).clip(CircleShape).background(dotColor)) },
                    )
                    val level = categoryLevelOf(category)
                    if (level > 0) LevelPill(level)
                } else {
                    DraftLabel(
                        text = "未选择分类",
                        container = MaterialTheme.colorScheme.surfaceContainerHighest,
                        content = MaterialTheme.colorScheme.outline,
                    )
                }

                draft.tags.forEach { tag ->
                    DraftLabel(
                        text = "# $tag",
                        container = MaterialTheme.colorScheme.surfaceContainerHighest,
                        content = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (summaryText.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onDelete,
                    enabled = !isDeleting,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    if (isDeleting) {
                        LoadingIndicator(diameter = 16.dp, color = MaterialTheme.colorScheme.error)
                    } else {
                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("删除")
                }
                TextButton(onClick = onEdit, enabled = !isDeleting) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("继续编辑")
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** 草稿卡里的小标签:分类、回复目标与话题标签共用一种外观。 */
@Composable
private fun DraftLabel(
    text: String,
    container: Color,
    content: Color,
    leading: (@Composable () -> Unit)? = null,
) {
    Surface(shape = MaterialTheme.shapes.small, color = container, contentColor = content) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            leading?.invoke()
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}
