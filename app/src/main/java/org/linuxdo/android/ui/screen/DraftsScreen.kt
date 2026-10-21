package org.linuxdo.android.ui.screen

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
import org.linuxdo.android.ui.design.IosActivityIndicator
import org.linuxdo.android.ui.design.IosTheme
import org.linuxdo.android.ui.design.IosLoadingBox
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
import org.linuxdo.android.ui.design.IosNavBar
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
            .background(IosTheme.colors.groupedBackground),
    ) {
        IosNavBar(
            title = "我的草稿",
            onBack = onBack,
            trailing = trailing,
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
            ) {
                if (state.loading && !state.loaded) {
                    item("loading") { IosLoadingBox() }
                }

                state.error?.let { message ->
                    item("error") {
                        ErrorNotice(message, onRefresh)
                    }
                }

                if (state.loaded && state.items.isEmpty() && state.error == null) {
                    item("empty") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 72.dp, bottom = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "暂无保存的草稿",
                                    style = IosTheme.type.headline,
                                    color = IosTheme.colors.secondaryLabel,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = "在发帖界面点击“保存”即可将草稿存放在这里",
                                    style = IosTheme.type.footnote,
                                    color = IosTheme.colors.tertiaryLabel,
                                )
                            }
                        }
                    }
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

                item("bottom-spacer") {
                    Spacer(Modifier.height(28.dp))
                }
            }
        }
    }

    draftPendingDelete?.let { target ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { draftPendingDelete = null },
            title = { Text("删除草稿") },
            text = {
                val displayTitle = target.title.ifBlank { "无标题草稿" }
                Text("确认要删除草稿「$displayTitle」吗？删除后无法恢复。")
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        val toDelete = target
                        draftPendingDelete = null
                        onDeleteDraft(toDelete)
                    },
                ) {
                    Text("删除", color = IosTheme.colors.destructive)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { draftPendingDelete = null },
                ) {
                    Text("取消")
                }
            },
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(IosTheme.colors.card)
            .border(0.5.dp, IosTheme.colors.separator.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .clickable(enabled = !isDeleting, onClick = onEdit)
            .padding(14.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                if (avatarUrl != null) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = draft.username,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(avatarShape(draft.username, avatarUrl, 10.dp)),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(IosTheme.colors.accent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = draft.username?.firstOrNull()?.uppercase() ?: "草",
                            style = IosTheme.type.headline,
                            color = IosTheme.colors.accent,
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = displayTitle,
                            style = IosTheme.type.headline.copy(fontSize = 16.sp),
                            color = IosTheme.colors.label,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (timeText.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = timeText,
                                style = IosTheme.type.caption,
                                color = IosTheme.colors.secondaryLabel,
                            )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (draft.action != "createTopic") {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(IosTheme.colors.accent.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = if (draft.replyToPostNumber != null) "回复 #${draft.replyToPostNumber}" else "回复草稿",
                                    style = IosTheme.type.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                                    color = IosTheme.colors.accent,
                                )
                            }
                        } else if (category != null) {
                            val baseName = categoryBaseNameOf(category.name)
                            val level = categoryLevelOf(category)
                            val dotColor = parseCategoryColor(category.color) ?: IosTheme.colors.accent
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(vertical = 1.dp),
                            ) {
                                Box(
                                    Modifier
                                        .size(9.dp)
                                        .clip(RoundedCornerShape(2.5.dp))
                                        .background(dotColor),
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = baseName,
                                    style = IosTheme.type.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                                    color = IosTheme.colors.secondaryLabel,
                                )
                                if (level > 0) {
                                    Spacer(Modifier.width(5.dp))
                                    val (pillBg, _, pillFg) = levelPillColors(level, IosTheme.colors.isDark)
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(pillBg)
                                            .padding(horizontal = 5.dp, vertical = 1.dp),
                                    ) {
                                        Text(
                                            text = "Lv.$level",
                                            style = IosTheme.type.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                                            color = pillFg,
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "未选择分类",
                                style = IosTheme.type.caption.copy(fontSize = 12.sp),
                                color = IosTheme.colors.tertiaryLabel,
                            )
                        }

                        draft.tags.forEach { tag ->
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(IosTheme.colors.fieldBackground)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = tag,
                                    style = IosTheme.type.caption.copy(fontSize = 11.sp),
                                    color = IosTheme.colors.secondaryLabel,
                                )
                            }
                        }
                    }
                }
            }

            if (summaryText.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = summaryText,
                    style = IosTheme.type.subheadline.copy(lineHeight = 20.sp),
                    color = IosTheme.colors.secondaryLabel,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(IosTheme.colors.fieldBackground)
                        .clickable(enabled = !isDeleting, onClick = onEdit),
                    contentAlignment = Alignment.Center,
                ) {
                    PostActionIcon(PostAction.Edit, selected = false, tint = IosTheme.colors.secondaryLabel)
                }

                Spacer(Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFF4D4F).copy(alpha = 0.14f))
                        .clickable(enabled = !isDeleting, onClick = onDelete),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isDeleting) {
                        IosActivityIndicator(diameter = 14.dp, color = Color(0xFFFF4D4F))
                    } else {
                        PostActionIcon(PostAction.Delete, selected = false, tint = Color(0xFFFF4D4F))
                    }
                }
            }
        }
    }
}
