package org.linuxdo.android.ui.screen

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.TabRowDefaults
import org.linuxdo.android.ui.design.*
import androidx.compose.material3.MaterialTheme

import org.linuxdo.android.data.TopicPage
import org.linuxdo.android.data.TopicNotificationLevel
import org.linuxdo.android.data.PostFlagReason
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt
import org.jsoup.Jsoup
import org.linuxdo.android.data.Category
import org.linuxdo.android.data.Topic
import org.linuxdo.android.data.TopicSortOrder
import org.linuxdo.android.data.HomeFeedType
import org.linuxdo.android.data.TopicListItem
import org.linuxdo.android.ui.design.LoadingIndicator
import org.linuxdo.android.ui.design.ListDivider
import org.linuxdo.android.ui.design.AppScaffold
import org.linuxdo.android.ui.design.LoadingBox
import org.linuxdo.android.ui.design.Dimens
import org.linuxdo.android.ui.design.UserAvatar
import org.linuxdo.android.ui.design.avatarShape
import org.linuxdo.android.ui.format.RelativeTime
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TextButton
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign

data class TopicListUiState(
    val items: List<TopicListItem> = emptyList(),
    val page: Int = 0,
    val hasMore: Boolean = true,
    val initialLoading: Boolean = true,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val pendingPage: TopicPage? = null,
    val incomingTopicIds: Set<Long> = emptySet(),
    val highlightedTopicIds: Set<Long> = emptySet(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicListScreen(
    title: String,
    state: TopicListUiState,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenTopic: (Topic) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    active: Boolean = true,
    scrollToTopRequest: Int = 0,
    trailing: @Composable () -> Unit = {},
    currentFeed: HomeFeedType? = null,
    onFeedSelect: ((HomeFeedType) -> Unit)? = null,
    hasUnread: Boolean = false,
    categoryVariants: List<Category> = emptyList(),
    currentCategoryId: Int? = null,
    onSelectCategoryVariant: ((Category) -> Unit)? = null,
    onApplyIncoming: (() -> Unit)? = null,
    onSetNotificationLevel: ((Topic, TopicNotificationLevel) -> Unit)? = null,
    onCreateTopic: (() -> Unit)? = null,
    onBookmarkTopic: ((Topic, (String) -> Unit) -> Unit)? = null,
    onFlagTopic: ((Topic, Int, String?, () -> Unit, (String) -> Unit) -> Unit)? = null,
    sortOrder: TopicSortOrder = TopicSortOrder.Default,
    sortAscending: Boolean = false,
    onSortChange: (TopicSortOrder) -> Unit = {},
) {
    val feedListStates = remember { mutableMapOf<HomeFeedType, LazyListState>() }
    val categoryListStates = remember { mutableMapOf<Int, LazyListState>() }
    val defaultListState = rememberLazyListState()
    val listState = when {
        currentFeed != null -> feedListStates.getOrPut(currentFeed) { LazyListState() }
        currentCategoryId != null -> categoryListStates.getOrPut(currentCategoryId) { LazyListState() }
        else -> defaultListState
    }
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showSortSheet by remember { mutableStateOf(false) }
    var flagTargetTopic by remember { mutableStateOf<Topic?>(null) }
    var flagSubmitting by remember { mutableStateOf(false) }
    var flagError by remember { mutableStateOf<String?>(null) }

    fun showNotice(message: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    val copyTopicLink: (Topic) -> Unit = { topic ->
        clipboardManager.setText(AnnotatedString("https://linux.do/t/topic/${topic.id}"))
        showNotice("已复制话题链接")
    }
    val bookmarkTopicAction: ((Topic) -> Unit)? = if (onBookmarkTopic != null) {
        { topic -> onBookmarkTopic(topic) { msg -> showNotice(msg) } }
    } else null
    val flagTopicAction: ((Topic) -> Unit)? = if (onFlagTopic != null) {
        { topic ->
            flagError = null
            flagSubmitting = false
            flagTargetTopic = topic
        }
    } else null

    var revealedTopicId by remember(listState) { mutableStateOf<Long?>(null) }
    LaunchedEffect(active, listState.isScrollInProgress, state.refreshing) {
        if (!active || listState.isScrollInProgress || state.refreshing) revealedTopicId = null
    }
    val hasCategoryDropdown = categoryVariants.size > 1 && onSelectCategoryVariant != null
    val isHome = currentFeed != null && onFeedSelect != null

    // 距底部还有 4 项时就开始取下一页,滚到底时数据通常已经就位。
    // 必须要求列表已有真实数据:空列表时占位项也会让 lastVisible 命中阈值,
    // 配合"失败不更新 page"就会变成每秒重打同一页的死循环。
    val nearEnd by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(active, nearEnd, state.hasMore, state.loadingMore, state.items.size, state.error) {
        val canLoadMore = active && state.hasMore && !state.refreshing && !state.initialLoading &&
            !state.loadingMore &&
            state.items.isNotEmpty() &&
            state.error == null
        if (nearEnd && canLoadMore) onLoadMore()
    }

    val (pinnedGroups, plainItems) = remember(state.items) { pinnedPrefixGroups(state.items) }

    AppScaffold(
        title = if (isHome) "LINUX DO" else title,
        modifier = modifier,
        listState = listState,
        onBack = onBack,
        showLargeTitle = onBack != null && !hasCategoryDropdown,
        scrollToTopRequest = scrollToTopRequest,
        trailing = {
            if (onCreateTopic != null) ComposeButton(onClick = onCreateTopic)
            if (!isHome) SortIconButton(sortOrder = sortOrder, onClick = { showSortSheet = true })
            trailing()
        },
        customTitle = if (hasCategoryDropdown) {
            {
                CategoryVariantSelector(
                    title = title,
                    variants = categoryVariants,
                    currentCategoryId = currentCategoryId,
                    onSelect = { onSelectCategoryVariant?.invoke(it) },
                )
            }
        } else null,
        topBarBottom = if (isHome) {
            {
                FeedTabs(
                    currentFeed = currentFeed!!,
                    onFeedSelect = onFeedSelect!!,
                    sortOrder = sortOrder,
                    onSortClick = { showSortSheet = true },
                )
            }
        } else {
            { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
        },
        contentPadding = PaddingValues(bottom = 24.dp),
        refreshing = state.refreshing,
        onRefresh = onRefresh,
        // 初始加载时列表中央已有加载指示,这里再叠一个下拉转圈就成了两个圈。
        showRefreshIndicator = !state.initialLoading,
        // 页面上不放常驻浮层(发帖在顶栏、排序在 Tab 行),只有 Snackbar 这种短暂提示叠在底部。
        floatingOverlay = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
            )
        },
    ) {
        if (state.incomingTopicIds.isNotEmpty()) {
            item(key = "incoming") {
                IncomingTopicsBanner(
                    count = state.incomingTopicIds.size,
                    onClick = {
                        onApplyIncoming?.invoke()
                        scope.launch { listState.animateScrollToItem(0) }
                    },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        state.error?.let { message ->
            item(key = "error") { ErrorNotice(message, onRefresh) }
        }
        if (state.initialLoading && state.items.isEmpty()) {
            item(key = "loading") { LoadingBox() }
        }
        if (!state.initialLoading && state.items.isEmpty() && state.error == null) {
            item(key = "empty") { EmptyNotice() }
        }

        val keyPrefix = currentFeed?.name ?: currentCategoryId ?: "topic"

        if (pinnedGroups.isNotEmpty()) {
            item(key = "${keyPrefix}_pinned") {
                val pinned = remember(pinnedGroups) { pinnedGroups.flatten() }
                PinnedGroupCard(
                    items = pinned,
                    isHighlighted = pinned.any { it.topic.id in state.highlightedTopicIds },
                    onOpenTopic = onOpenTopic,
                    onSetNotificationLevel = onSetNotificationLevel,
                    onCopyLink = copyTopicLink,
                    onBookmark = bookmarkTopicAction,
                    onFlag = flagTopicAction,
                )
            }
        }

        items(
            items = plainItems,
            key = { item -> "${keyPrefix}_${item.topic.id}" },
        ) { item ->
            TopicRow(
                item = item,
                isHighlighted = item.topic.id in state.highlightedTopicIds,
                revealed = revealedTopicId == item.topic.id,
                onReveal = { opened -> revealedTopicId = item.topic.id.takeIf { opened } },
                onSetNotificationLevel = if (onSetNotificationLevel != null) { level ->
                    onSetNotificationLevel(item.topic, level)
                } else null,
                onCopyLink = { copyTopicLink(item.topic) },
                onBookmark = bookmarkTopicAction?.let { fn -> { fn(item.topic) } },
                onFlag = flagTopicAction?.let { fn -> { fn(item.topic) } },
                onClick = { onOpenTopic(item.topic) },
            )
        }

        if (state.loadingMore) {
            item(key = "load-more") { LoadingBox() }
        }
    }

    if (showSortSheet) {
        TopicSortSheet(
            currentOrder = sortOrder,
            ascending = sortAscending,
            onDismiss = { showSortSheet = false },
            onSelectOrder = { order ->
                onSortChange(order)
                if (order == TopicSortOrder.Default || order != sortOrder) showSortSheet = false
            },
        )
    }

    flagTargetTopic?.let { topic ->
        TopicFlagDialog(
            topic = topic,
            submitting = flagSubmitting,
            error = flagError,
            onDismiss = {
                if (!flagSubmitting) {
                    flagTargetTopic = null
                    flagError = null
                }
            },
            onSubmit = { reasonId, message ->
                val handler = onFlagTopic ?: return@TopicFlagDialog
                flagSubmitting = true
                flagError = null
                handler(
                    topic,
                    reasonId,
                    message,
                    {
                        flagSubmitting = false
                        flagTargetTopic = null
                        showNotice("举报已提交，感谢您的反馈")
                    },
                    { err ->
                        flagSubmitting = false
                        flagError = err
                    },
                )
            },
        )
    }
}

/** 顶栏里的「发帖」:强调底的小胶囊,常驻但不遮挡任何内容。 */
@Composable
private fun ComposeButton(onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.padding(end = 4.dp).height(36.dp),
        contentPadding = PaddingValues(start = 12.dp, end = 14.dp),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("发帖", style = MaterialTheme.typography.labelLarge)
    }
}

/** 非首页(分类话题列表)没有 Tab 行,排序放进顶栏;非默认排序时图标换成强调色。 */
@Composable
private fun SortIconButton(sortOrder: TopicSortOrder, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            AppIcons.sort(sortOrder),
            contentDescription = "列表排序：${sortOrder.label}",
            tint = if (sortOrder == TopicSortOrder.Default) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
        )
    }
}

/** 排序选择底部面板。再次点选当前排序方式会切换升降序。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicSortSheet(
    currentOrder: TopicSortOrder,
    ascending: Boolean,
    onDismiss: () -> Unit,
    onSelectOrder: (TopicSortOrder) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "排序方式",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        TopicSortOrder.entries.forEach { order ->
            val selected = order == currentOrder
            ListItem(
                headlineContent = { Text(order.label) },
                leadingContent = { Icon(AppIcons.sort(order), contentDescription = null) },
                trailingContent = if (selected) {
                    {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (order != TopicSortOrder.Default) {
                                Text(
                                    if (ascending) "升序" else "降序",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Icon(Icons.Filled.Check, contentDescription = "已选", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else null,
                colors = ListItemDefaults.colors(
                    containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                ),
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .clip(MaterialTheme.shapes.large)
                    .clickable { onSelectOrder(order) },
            )
        }
        Text(
            "再次点击当前排序方式可切换升序 / 降序",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        )
    }
}

/**
 * 话题长按操作菜单(提醒四档 + 复制链接、添加书签、举报)。[TopicRow] 与 [PinnedGroupCard] 共用。
 */
@Composable
private fun NotificationLevelMenu(
    expanded: Boolean,
    currentLevel: TopicNotificationLevel?,
    updating: Boolean,
    onDismiss: () -> Unit,
    onPick: ((TopicNotificationLevel) -> Unit)? = null,
    onCopyLink: (() -> Unit)? = null,
    onBookmark: (() -> Unit)? = null,
    onFlag: (() -> Unit)? = null,
) {
    if (!expanded) return
    DropdownMenu(expanded = true, onDismissRequest = onDismiss) {
        if (onPick != null) {
            val current = currentLevel ?: TopicNotificationLevel.Regular
            TopicNotificationLevel.entries.forEach { level ->
                DropdownMenuItem(
                    enabled = !updating,
                    text = { Text(level.title) },
                    leadingIcon = { TopicNotificationIcon(level, modifier = Modifier.size(20.dp)) },
                    trailingIcon = if (level == current) {
                        { Icon(Icons.Filled.Check, contentDescription = "当前", tint = MaterialTheme.colorScheme.primary) }
                    } else null,
                    onClick = {
                        onDismiss()
                        onPick(level)
                    },
                )
            }
        }
        if (onPick != null && (onCopyLink != null || onBookmark != null || onFlag != null)) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        }
        onCopyLink?.let { action ->
            DropdownMenuItem(
                text = { Text("复制链接") },
                leadingIcon = { Icon(Icons.Outlined.Link, contentDescription = null) },
                onClick = { onDismiss(); action() },
            )
        }
        onBookmark?.let { action ->
            DropdownMenuItem(
                text = { Text("添加书签") },
                leadingIcon = { Icon(Icons.Outlined.BookmarkBorder, contentDescription = null) },
                onClick = { onDismiss(); action() },
            )
        }
        onFlag?.let { action ->
            DropdownMenuItem(
                text = { Text("举报") },
                leadingIcon = { Icon(Icons.Outlined.Flag, contentDescription = null) },
                onClick = { onDismiss(); action() },
            )
        }
    }
}

/**
 * 把列表**头部连续**的置顶项按作者切成若干组,返回 (置顶分组, 其余话题)。
 *
 * 只吃头部前缀而不是全列表 filter:站方的置顶本来就排在最前,取前缀足以解决"置顶吃光首屏";
 * 全列表 filter 会把翻页后零散冒出来的置顶帖也抽到顶上,列表会在加载更多时自己跳一下。
 */
private fun pinnedPrefixGroups(
    items: List<TopicListItem>,
): Pair<List<List<TopicListItem>>, List<TopicListItem>> {
    val prefix = items.takeWhile { it.topic.pinned }
    if (prefix.isEmpty()) return emptyList<List<TopicListItem>>() to items
    // LinkedHashMap 保持作者首次出现的顺序,不打乱站方的置顶排序。
    val grouped = LinkedHashMap<String, MutableList<TopicListItem>>()
    prefix.forEach { entry ->
        grouped.getOrPut(entry.authorUsername ?: "") { mutableListOf() }.add(entry)
    }
    return grouped.values.map { it.toList() } to items.drop(prefix.size)
}

@Composable
private fun topicRowBackground(isHighlighted: Boolean): Color {
    val background by animateColorAsState(
        targetValue = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        animationSpec = tween(if (isHighlighted) 100 else 450), label = "topic-highlight",
    )
    return background
}

/**
 * 置顶帖合成一行。
 *
 * 站方同时挂多条全局置顶时,每条各占一行会把首屏吃光 —— 下拉刷新后想看的新帖只剩一条。
 * 收起时只露第一条置顶的标题和总数;点开后逐条列出标题(长按仍有操作菜单),再点收起。
 * 标题走 [titleWithBadges],未读蓝点、"已关闭"标记不必重写一套。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PinnedGroupCard(
    items: List<TopicListItem>,
    onOpenTopic: (Topic) -> Unit,
    onSetNotificationLevel: ((Topic, TopicNotificationLevel) -> Unit)? = null,
    onCopyLink: ((Topic) -> Unit)? = null,
    onBookmark: ((Topic) -> Unit)? = null,
    onFlag: ((Topic) -> Unit)? = null,
    isHighlighted: Boolean = false,
) {
    val head = items.firstOrNull() ?: return
    var expanded by rememberSaveable { mutableStateOf(false) }
    var menuTopicId by remember { mutableStateOf<Long?>(null) }
    val hasAnyLongPress = onSetNotificationLevel != null || onCopyLink != null || onBookmark != null || onFlag != null
    Column(Modifier.fillMaxWidth().background(topicRowBackground(isHighlighted))) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable { if (items.size == 1) onOpenTopic(head.topic) else expanded = !expanded }
                .padding(start = 20.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.PushPin, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("置顶", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(10.dp))
            Text(
                text = if (expanded) "共 ${items.size} 条" else head.topic.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (expanded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (items.size > 1) {
                if (!expanded) {
                    Text("${items.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
                    Spacer(Modifier.width(2.dp))
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "收起置顶" else "展开置顶",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (expanded) {
            items.forEach { entry ->
                Box {
                    Text(
                        text = titleWithBadges(entry.topic.copy(pinned = false), MaterialTheme.colorScheme.primary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        inlineContent = unseenDotContent(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onOpenTopic(entry.topic) },
                                onLongClick = if (hasAnyLongPress && !entry.notificationLevelUpdating) {
                                    { menuTopicId = entry.topic.id }
                                } else null,
                            )
                            .padding(start = 44.dp, end = 20.dp, top = 10.dp, bottom = 10.dp),
                    )
                    NotificationLevelMenu(
                        expanded = menuTopicId == entry.topic.id && hasAnyLongPress,
                        currentLevel = entry.notificationLevel,
                        updating = entry.notificationLevelUpdating,
                        onDismiss = { menuTopicId = null },
                        onPick = onSetNotificationLevel?.let { fn -> { level -> fn(entry.topic, level) } },
                        onCopyLink = onCopyLink?.let { fn -> { fn(entry.topic) } },
                        onBookmark = onBookmark?.let { fn -> { fn(entry.topic) } },
                        onFlag = onFlag?.let { fn -> { fn(entry.topic) } },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * 话题行(三段式,平铺无卡片):作者 + Lv + 分类、标题 + 按需摘要 + 按需首图、
 * 回复 / 浏览 / 活动时间 + 标签。行与行之间只有一条发丝线,不叠背景和阴影,滚动时绘制层最少。
 *
 * 之所以不给摘要和多图预留固定区域:linux.do 的 /latest.json 对普通帖**不下发 excerpt**
 * (只有置顶帖和搜索结果有),图也只给单张 image_url。硬留版位要么大面积留白,
 * 要么得逐帖拉详情去凑内容 —— 那是在拿跳盾风险换排版。所以这里"有就展开、没有就收起"。
 *
 * 左滑露出四档提醒级别;长按弹出完整操作菜单。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopicRow(
    item: TopicListItem,
    query: String? = null,
    isHighlighted: Boolean = false,
    revealed: Boolean = false,
    onReveal: (Boolean) -> Unit = {},
    onSetNotificationLevel: ((TopicNotificationLevel) -> Unit)? = null,
    onCopyLink: (() -> Unit)? = null,
    onBookmark: (() -> Unit)? = null,
    onFlag: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    var showLevelMenu by remember { mutableStateOf(false) }
    val hasAnyLongPress = onSetNotificationLevel != null || onCopyLink != null || onBookmark != null || onFlag != null
    val topic = item.topic
    val drawerWidth = with(LocalDensity.current) { (DrawerSlotWidth * TopicNotificationLevel.entries.size).toPx() }
    var dragging by remember(topic.id) { mutableStateOf(false) }
    var dragOffset by remember(topic.id) { mutableFloatStateOf(0f) }
    val currentRevealed by rememberUpdatedState(revealed)
    val offset by animateFloatAsState(
        targetValue = if (dragging) dragOffset else if (revealed) -drawerWidth else 0f,
        animationSpec = if (dragging) snap() else tween(180), label = "topic-swipe",
    )
    val rowBackground = topicRowBackground(isHighlighted)
    val needle = query?.trim().orEmpty()
    val accent = MaterialTheme.colorScheme.primary
    val excerpt = remember(topic.id, topic.excerpt, needle, accent) {
        val raw = topic.excerpt?.let { Jsoup.parseBodyFragment(it).text().trim() }?.takeIf { it.isNotEmpty() }
        if (raw.isNullOrBlank()) null
        else if (needle.isEmpty()) buildAnnotatedString { append(raw) }
        else buildAnnotatedString {
            var start = raw.indexOf(needle, ignoreCase = true)
            var cursor = 0
            while (start >= 0) {
                append(raw.substring(cursor, start))
                withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) {
                    append(raw.substring(start, start + needle.length))
                }
                cursor = start + needle.length
                start = raw.indexOf(needle, cursor, ignoreCase = true)
            }
            append(raw.substring(cursor))
        }
    }
    Box(Modifier.fillMaxWidth()) {
        if (onSetNotificationLevel != null && (revealed || dragging || offset < 0f)) {
            Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.End) {
                TopicNotificationLevel.entries.forEach { level ->
                    val (container, content) = notificationLevelContainer(level)
                    Column(
                        Modifier
                            .width(DrawerSlotWidth)
                            .fillMaxHeight()
                            .background(container)
                            .clickable(enabled = !item.notificationLevelUpdating) {
                                onReveal(false)
                                onSetNotificationLevel(level)
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(AppIcons.notificationLevel(level), contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(level.badgeLabel, style = MaterialTheme.typography.labelSmall, color = content)
                    }
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = offset }
                .background(rowBackground)
                .then(if (onSetNotificationLevel == null) Modifier else Modifier.pointerInput(topic.id, drawerWidth, item.notificationLevelUpdating) {
                    if (!item.notificationLevelUpdating) detectHorizontalDragGestures(
                        onDragStart = {
                            dragOffset = if (currentRevealed) -drawerWidth else 0f
                            dragging = true
                            onReveal(true)
                        },
                        onHorizontalDrag = { change, distance ->
                            change.consume()
                            dragOffset = (dragOffset + distance).coerceIn(-drawerWidth, 0f)
                        },
                        onDragEnd = { dragging = false; onReveal(dragOffset < -drawerWidth / 3f) },
                        onDragCancel = { dragging = false; onReveal(false) },
                    )
                })
                .combinedClickable(
                    onClick = { if (revealed) onReveal(false) else onClick() },
                    onLongClick = if (hasAnyLongPress && !item.notificationLevelUpdating) {
                        { onReveal(false); showLevelMenu = true }
                    } else null,
                )
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 作者区吃掉除分类外的全部宽度,分类才能稳定贴住右边界,上下卡片的分类对成一条竖线。
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    UserAvatar(
                        avatarUrl = item.avatarUrl,
                        fallbackUrl = item.staticAvatarUrl,
                        username = item.authorUsername,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = item.displayName.ifBlank { "匿名" },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    item.trustLevel?.takeIf { it > 0 }?.let { level ->
                        Spacer(Modifier.width(6.dp))
                        LevelBadge(level)
                    }
                }
                item.notificationLevel?.takeIf { it != TopicNotificationLevel.Regular }?.let { level ->
                    TopicNotificationLevelTag(level)
                    Spacer(Modifier.width(8.dp))
                }
                item.category?.let { CategoryTag(it) }
            }
            NotificationLevelMenu(
                expanded = showLevelMenu && hasAnyLongPress,
                currentLevel = item.notificationLevel,
                updating = item.notificationLevelUpdating,
                onDismiss = { showLevelMenu = false },
                onPick = onSetNotificationLevel,
                onCopyLink = onCopyLink,
                onBookmark = onBookmark,
                onFlag = onFlag,
            )
            Spacer(Modifier.height(6.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = titleWithBadges(topic, MaterialTheme.colorScheme.primary, query),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        inlineContent = unseenDotContent(MaterialTheme.colorScheme.primary),
                    )
                    excerpt?.let { text ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (query.isNullOrBlank()) 2 else 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                topic.imageUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    Spacer(Modifier.width(12.dp))
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 76.dp, height = 56.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MetaStat(Icons.Outlined.ChatBubbleOutline, formatCount(topic.totalReplies), textWidth = 30.dp)
                if (topic.views > 0) {
                    Spacer(Modifier.width(12.dp))
                    MetaStat(Icons.Outlined.Visibility, formatCount(topic.views), textWidth = 34.dp)
                }
                Spacer(Modifier.width(12.dp))
                MetaStat(Icons.Outlined.Schedule, RelativeTime.format(topic.bumpedAt ?: topic.lastPostedAt ?: topic.createdAt))
                Spacer(Modifier.weight(1f))
                topic.tags.take(2).forEach { tag ->
                    Spacer(Modifier.width(8.dp))
                    TagChip(tag)
                }
            }
        }
        HorizontalDivider(Modifier.align(Alignment.BottomStart), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** 左滑抽屉里每一档提醒级别占的宽度。 */
private val DrawerSlotWidth = 60.dp

/** 左滑抽屉里各档提醒级别的底色与前景色,用色板容器色保证浅色深色都清楚。 */
@Composable
private fun notificationLevelContainer(level: TopicNotificationLevel): Pair<Color, Color> = when (level) {
    TopicNotificationLevel.Watching -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    TopicNotificationLevel.Tracking -> AppTheme.colors.successContainer to AppTheme.colors.onSuccessContainer
    TopicNotificationLevel.Regular -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
    TopicNotificationLevel.Muted -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
}

/**
 * 作者信任等级徽章:扁平小色块,颜色本身就是身份(Lv1 蓝、Lv2 琥珀、Lv3 橙、Lv4 紫加金冠)。
 * 色值与「分类」页的 Lv 药丸同源([levelPillColors]);不用渐变和描边,列表每行都要画,越简单越省。
 */
@Composable
private fun LevelBadge(level: Int) {
    val (bg, _, fg) = levelPillColors(level.coerceAtMost(4), AppTheme.isDark)
    Row(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 5.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (level >= 4) {
            CrownGlyph(AppTheme.colors.gold)
            Spacer(Modifier.width(2.dp))
        }
        Text(text = "Lv$level", style = TextStyle(color = fg, fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold))
    }
}

/** Lv4 专属小金冠。只有最高等级才画,所以列表里的数量天然很少。 */
@Composable
private fun CrownGlyph(color: Color) {
    Canvas(Modifier.size(8.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val crown = Path().apply {
                moveTo(3f, 19f)
                lineTo(1.5f, 7f)
                lineTo(7.5f, 12f)
                lineTo(12f, 4f)
                lineTo(16.5f, 12f)
                lineTo(22.5f, 7f)
                lineTo(21f, 19f)
                close()
            }
            drawPath(crown, color)
        }
    }
}

@Composable
private fun TagChip(tag: String) {
    Text(
        text = "#$tag",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.outline,
        maxLines = 1,
    )
}

/**
 * 底栏的一项统计。传了 [textWidth] 时数字区**定宽**并开 `tnum` 等宽数字:
 * 回复数从 1 位变 3 位时,后面的浏览量和时间就不会跟着左右平移,上下卡片对得齐。
 */
@Composable
private fun MetaStat(icon: ImageVector, value: String, textWidth: Dp? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1,
            modifier = if (textWidth != null) Modifier.width(textWidth) else Modifier,
        )
    }
}

/**
 * 未读蓝点与置顶 / 关闭标记都内联进标题文本流,和标题共占 2 行配额,
 * 不在标题上方额外占一行。
 */
private fun titleWithBadges(topic: Topic, accent: Color, query: String? = null) = buildAnnotatedString {
    if (topic.unseen) {
        appendInlineContent(UNSEEN_DOT, "•")
        append(" ")
    }
    if (topic.pinned) {
        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append("置顶") }
        append(" ")
    }
    if (topic.closed) {
        withStyle(SpanStyle(color = accent.copy(alpha = 0.7f))) { append("已关闭") }
        append(" ")
    }
    val title = topic.title
    val needle = query?.trim().orEmpty()
    if (needle.isNotEmpty()) {
        var start = title.indexOf(needle, ignoreCase = true)
        var cursor = 0
        while (start >= 0) {
            append(title.substring(cursor, start))
            withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) {
                append(title.substring(start, start + needle.length))
            }
            cursor = start + needle.length
            start = title.indexOf(needle, cursor, ignoreCase = true)
        }
        append(title.substring(cursor))
    } else {
        append(title)
    }
}

private const val UNSEEN_DOT = "unseen-dot"

@Composable
private fun unseenDotContent(accent: Color): Map<String, InlineTextContent> = remember(accent) {
    mapOf(
        UNSEEN_DOT to InlineTextContent(
            Placeholder(9.sp, 9.sp, PlaceholderVerticalAlign.TextCenter),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(accent))
            }
        },
    )
}

/** 浏览量这类大数按千位缩写,免得把这一行挤到换行。 */
internal fun formatCount(value: Int): String {
    if (value < 1_000) return value.coerceAtLeast(0).toString()
    val thousands = value / 1_000.0
    val text = if (thousands >= 100) {
        thousands.roundToInt().toString()
    } else {
        String.format(Locale.US, "%.1f", thousands).removeSuffix(".0")
    }
    return "${text}k"
}

@Composable
internal fun CategoryTag(category: Category, prominent: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(parseCategoryColor(category.color) ?: MaterialTheme.colorScheme.outline),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = category.name,
            style = if (prominent) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodySmall,
            color = if (prominent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
internal fun ErrorNotice(message: String, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(40.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        FilledTonalButton(onClick = onRetry) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("重试")
        }
    }
}

@Composable
internal fun EmptyNotice() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Outlined.Inbox,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(48.dp),
        )
        Text(
            text = "这里还没有内容",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Discourse 下发的分类色是不带 # 的六位十六进制。 */
fun parseCategoryColor(hex: String?): Color? {
    val cleaned = hex?.removePrefix("#")?.trim() ?: return null
    if (cleaned.length != 6) return null
    return runCatching { Color(cleaned.toLong(16) or 0xFF000000L) }.getOrNull()
}

/** 首页 feed 标签(最新 / 未读 / 热门 / 我的帖子),左对齐;右侧是排序入口。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedTabs(
    currentFeed: HomeFeedType,
    onFeedSelect: (HomeFeedType) -> Unit,
    sortOrder: TopicSortOrder,
    onSortClick: () -> Unit,
) {
    val feeds = HomeFeedType.entries
    val selectedIndex = feeds.indexOf(currentFeed).coerceAtLeast(0)
    Column {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            // 不用 ScrollableTabRow:它给每个 Tab 强制 90dp 最小宽度,四个标签加排序按钮在窄屏上放不下。
            Row(Modifier.weight(1f).fillMaxHeight().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                feeds.forEachIndexed { index, feed ->
                    val selected = index == selectedIndex
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .selectable(selected = selected, role = Role.Tab, onClick = { onFeedSelect(feed) })
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            feed.label,
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        if (selected) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .width(20.dp)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                }
            }
            val isDefault = sortOrder == TopicSortOrder.Default
            TextButton(
                onClick = onSortClick,
                modifier = Modifier.padding(end = 8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (isDefault) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                ),
            ) {
                Icon(AppIcons.sort(sortOrder), contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(sortOrder.label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Normal))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

private val CATEGORY_LEVEL_REGEX = Regex("[,，\\s]*Lv\\s*(\\d+)\\s*$", RegexOption.IGNORE_CASE)

internal fun categoryLevelOf(category: Category): Int =
    CATEGORY_LEVEL_REGEX.find(category.name)?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 3) ?: 0

internal fun categoryBaseNameOf(name: String): String =
    name.replace(CATEGORY_LEVEL_REGEX, "").trim().ifBlank { name }

/** 分类页标题:分类名 + 当前等级,点开在同一分类的各等级(Lv0~Lv3)之间切换。 */
@Composable
private fun CategoryVariantSelector(
    title: String,
    variants: List<Category>,
    currentCategoryId: Int?,
    onSelect: (Category) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val currentCategory = variants.firstOrNull { it.id == currentCategoryId }
    val currentLevel = currentCategory?.let(::categoryLevelOf)
        ?: CATEGORY_LEVEL_REGEX.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 3)
        ?: 0
    val baseTitle = categoryBaseNameOf(currentCategory?.name ?: title)

    Box {
        Row(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable { expanded = true }
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(baseTitle, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            LevelPill(currentLevel)
            Icon(
                if (expanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                contentDescription = "切换等级",
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            variants.forEach { variant ->
                val selected = variant.id == currentCategoryId
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(categoryBaseNameOf(variant.name))
                            Spacer(Modifier.width(8.dp))
                            LevelPill(categoryLevelOf(variant))
                        }
                    },
                    leadingIcon = {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(parseCategoryColor(variant.color) ?: MaterialTheme.colorScheme.outline),
                        )
                    },
                    trailingIcon = when {
                        selected -> {
                            { Icon(Icons.Filled.Check, contentDescription = "当前", tint = MaterialTheme.colorScheme.primary) }
                        }
                        variant.topicCount > 0 -> {
                            { Text("${variant.topicCount}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline) }
                        }
                        else -> null
                    },
                    onClick = {
                        expanded = false
                        onSelect(variant)
                    },
                )
            }
        }
    }
}

/** 分类等级药丸。色值与话题行上的 Lv 徽章同源,同一个 Lv 在各页面是同一种颜色。 */
@Composable
internal fun LevelPill(level: Int) {
    val (bg, _, text) = levelPillColors(level, AppTheme.isDark)
    Box(
        Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text("Lv.$level", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = text)
    }
}

/** (底色, 描边, 文字)。扁平设计下描边与底色相同,保留三元组是为了调用方不用改。 */
internal fun levelPillColors(level: Int, isDark: Boolean): Triple<Color, Color, Color> {
    val (bg, fg) = when (level) {
        0 -> if (isDark) Color(0xFF23262C) to Color(0xFFC9CDD5) else Color(0xFFF1F3F6) to Color(0xFF4A5160)
        1 -> if (isDark) Color(0xFF1C2A45) to Color(0xFF9DB8FF) else Color(0xFFE8F0FE) to Color(0xFF1A56DB)
        2 -> if (isDark) Color(0xFF3A2E12) to Color(0xFFF2C76B) else Color(0xFFFCF1D2) to Color(0xFF7A5000)
        3 -> if (isDark) Color(0xFF3D2414) to Color(0xFFF3A774) else Color(0xFFFDE6D4) to Color(0xFF943A00)
        else -> if (isDark) Color(0xFF33203D) to Color(0xFFD9A6F0) else Color(0xFFF3E3FA) to Color(0xFF7A2A9A)
    }
    return Triple(bg, bg, fg)
}

/** 列表第一行的"有新动态"提示:占自己的一行,不盖内容;点击并入新话题并回到顶部。 */
@Composable
fun IncomingTopicsBanner(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable(onClick = onClick),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.ArrowUpward, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("$count 条新动态，点击加载", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun TopicNotificationLevelTag(
    level: TopicNotificationLevel,
    modifier: Modifier = Modifier,
) {
    if (level == TopicNotificationLevel.Regular) return
    val color = notificationLevelColor(level)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Spacer(Modifier.width(5.dp))
        Text(
            text = level.badgeLabel,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun notificationLevelColor(level: TopicNotificationLevel): Color = when (level) {
    TopicNotificationLevel.Watching -> MaterialTheme.colorScheme.primary
    TopicNotificationLevel.Tracking -> AppTheme.colors.success
    TopicNotificationLevel.Regular -> MaterialTheme.colorScheme.outline
    TopicNotificationLevel.Muted -> MaterialTheme.colorScheme.error
}

@Composable
internal fun TopicNotificationIcon(level: TopicNotificationLevel, modifier: Modifier = Modifier) {
    Icon(
        AppIcons.notificationLevel(level),
        contentDescription = level.title,
        tint = notificationLevelColor(level),
        modifier = modifier.size(18.dp),
    )
}

@Composable
private fun TopicFlagDialog(
    topic: Topic,
    submitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSubmit: (reasonId: Int, message: String?) -> Unit,
) {
    FlagReasonDialog(
        title = "举报话题",
        subtitle = topic.title,
        stateKey = topic.id,
        submitting = submitting,
        error = error,
        onDismiss = onDismiss,
        onSubmit = onSubmit,
    )
}
