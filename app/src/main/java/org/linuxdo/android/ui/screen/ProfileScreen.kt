package org.linuxdo.android.ui.screen

import androidx.compose.ui.unit.sp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.linuxdo.android.data.NotificationFilter
import org.linuxdo.android.data.ProfileEntry
import org.linuxdo.android.data.ProfileSection
import org.linuxdo.android.ui.ProfileUiState
import org.linuxdo.android.ui.design.AppScaffold
import org.linuxdo.android.ui.design.AppTheme
import org.linuxdo.android.ui.design.ListDivider
import org.linuxdo.android.ui.design.LoadingBox
import org.linuxdo.android.ui.design.TextAction
import org.linuxdo.android.ui.design.UserAvatar

/** 列表攒够这么多条才允许继续自动翻页;不足则改成手动按钮。 */
private const val AUTO_LOAD_THRESHOLD = 20

/** 「我的」页顶部的分区标签。 */
private val ProfileTabs = listOf(
    Triple(ProfileSection.Summary, "总结", Icons.Outlined.Summarize),
    Triple(ProfileSection.Activity, "活动", Icons.Outlined.History),
    Triple(ProfileSection.Notifications, "通知", Icons.Outlined.Notifications),
    Triple(ProfileSection.Messages, "消息", Icons.Outlined.MailOutline),
)

/**
 * 「我的」页。[notificationOnly] 为 true 时只显示通知列表(从首页铃铛进入的独立通知页)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    username: String?,
    states: Map<ProfileSection, ProfileUiState>,
    onLoad: (ProfileSection, Boolean, Boolean) -> Unit,
    onOpenTopic: (Long, String, Int?) -> Unit,
    onDiagnostics: () -> Unit,
    onSettings: () -> Unit,
    onLogout: () -> Unit = {},
    active: Boolean,
    notificationOnly: Boolean = false,
    onBack: (() -> Unit)? = null,
    onReadNotification: (Long) -> Unit = {},
    notificationFilter: NotificationFilter = NotificationFilter.All,
    onNotificationFilter: (NotificationFilter) -> Unit = {},
    unreadFilters: Set<NotificationFilter> = emptySet(),
    onOpenUserTopics: ((username: String, title: String, isBookmark: Boolean) -> Unit)? = null,
    draftCount: String = "0",
    onOpenDrafts: (() -> Unit)? = null,
    scrollToTopRequest: Int = 0,
    trailing: @Composable () -> Unit = {},
) {
    var section by rememberSaveable { mutableStateOf(if (notificationOnly) ProfileSection.Notifications else ProfileSection.Summary) }
    var previousSection by rememberSaveable { mutableStateOf(section) }
    val list = rememberLazyListState()
    val state = states[section] ?: ProfileUiState()
    val summaryFields = (states[ProfileSection.Summary] ?: ProfileUiState()).content.fields
    val visibleEntries = state.content.entries

    val nearEnd by remember { derivedStateOf {
        (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= list.layoutInfo.totalItemsCount - 3
    } }
    LaunchedEffect(section, active) {
        if (active) {
            onLoad(section, section == ProfileSection.Notifications, false)
            if (section != ProfileSection.Summary && summaryFields.isEmpty()) onLoad(ProfileSection.Summary, false, false)
        }
        if (previousSection != section) {
            previousSection = section
            list.scrollToItem(0)
        }
    }
    LaunchedEffect(active, nearEnd, state, notificationFilter) {
        if (active && nearEnd && state.loaded && !state.loading && state.error == null &&
            state.content.hasMore && visibleEntries.size >= AUTO_LOAD_THRESHOLD) {
            onLoad(section, false, true)
        }
    }
    var showLogoutDialog by remember { mutableStateOf(false) }
    fun metric(key: String, fallback: String = "0") =
        summaryFields.firstOrNull { it.first == key }?.second?.ifBlank { fallback } ?: fallback

    AppScaffold(
        title = if (notificationOnly) "通知" else "我的",
        listState = list,
        onBack = onBack,
        showLargeTitle = false,
        scrollToTopRequest = scrollToTopRequest,
        trailing = {
            if (!notificationOnly) {
                trailing()
                IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "设置") }
            }
        },
    ) {
        if (!notificationOnly) {
            item("identity-card") {
                IdentityCard(
                    username = username,
                    daysVisited = metric("访问天数"),
                    timeRead = metric("阅读时长"),
                    topicsEntered = metric("浏览话题"),
                    onLogout = { showLogoutDialog = true },
                )
            }
            item("section-tabs") {
                val notificationsUnread = unreadFilters.isNotEmpty() || visibleEntries.any { it.unread }
                PrimaryTabRow(
                    selectedTabIndex = ProfileTabs.indexOfFirst { it.first == section }.coerceAtLeast(0),
                    containerColor = MaterialTheme.colorScheme.surface,
                    divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) },
                ) {
                    ProfileTabs.forEach { (tabSection, label, icon) ->
                        Tab(
                            selected = section == tabSection,
                            onClick = {
                                if (section == tabSection && tabSection == ProfileSection.Notifications) {
                                    onLoad(ProfileSection.Notifications, true, false)
                                } else {
                                    section = tabSection
                                }
                            },
                            text = {
                                BadgedBox(badge = {
                                    if (tabSection == ProfileSection.Notifications && notificationsUnread) Badge()
                                }) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                                        fontWeight = if (section == tabSection) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                }
                            },
                            selectedContentColor = MaterialTheme.colorScheme.onSurface,
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (section == ProfileSection.Summary) {
                item("metrics") {
                    val metrics = listOf(
                        MetricSpec("已读帖子", metric("已读帖子"), Icons.AutoMirrored.Outlined.MenuBook),
                        MetricSpec("收到赞", metric("收到赞"), Icons.Outlined.FavoriteBorder),
                        MetricSpec("我的草稿", draftCount, Icons.Outlined.Drafts, onOpenDrafts),
                        MetricSpec("回复数量", metric("回复数量"), Icons.AutoMirrored.Outlined.Reply),
                        MetricSpec("创建话题", metric("创建话题", metric("发表话题")), Icons.Outlined.RocketLaunch) {
                            if (!username.isNullOrBlank()) onOpenUserTopics?.invoke(username, "创建的话题", false)
                        },
                        MetricSpec("我的收藏", metric("我的收藏"), Icons.Outlined.BookmarkBorder) {
                            if (!username.isNullOrBlank()) onOpenUserTopics?.invoke(username, "我的收藏", true)
                        },
                    )
                    Column(Modifier.fillMaxWidth()) {
                        metrics.forEach { MetricCard(it) }
                    }
                }
            }
        }

        if (section == ProfileSection.Notifications) item("notification-filters") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NotificationFilter.entries.forEach { filter ->
                    val hasUnread = filter != NotificationFilter.All && (
                        filter in unreadFilters || visibleEntries.any { it.unread && filter.includes(it.notificationType) }
                    )
                    FilterChip(
                        selected = notificationFilter == filter,
                        onClick = { onNotificationFilter(filter) },
                        label = { Text(filter.label) },
                        leadingIcon = {
                            BadgedBox(badge = { if (hasUnread) Badge() }) {
                                Icon(filterIcon(filter), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
                            }
                        },
                    )
                }
            }
        }

        if (state.loading && !state.loaded) item("loading") { LoadingBox() }
        state.error?.let { message -> item("error") { ErrorNotice(message) { onLoad(section, true, false) } } }

        if (section != ProfileSection.Summary) {
            items(visibleEntries, key = { it.id }) { entry ->
                val notification = section == ProfileSection.Notifications
                EntryRow(
                    entry = entry,
                    notification = notification,
                    onClick = {
                        if (notification && entry.unread) entry.id.toLongOrNull()?.let(onReadNotification)
                        entry.topicId?.let { onOpenTopic(it, entry.title, entry.postNumber) }
                    },
                )
                ListDivider(startIndent = 72.dp)
            }
        }

        // 还有更多但不足一屏:给手动加载入口
        if (state.loaded && !state.loading && state.error == null && state.content.hasMore &&
            visibleEntries.size < AUTO_LOAD_THRESHOLD && state.content.fields.isEmpty()) {
            item("load-more") {
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    TextAction(if (visibleEntries.isEmpty()) "这一页没有更多，继续往前找" else "加载更早的记录") {
                        onLoad(section, false, true)
                    }
                }
            }
        }
        if (state.loaded && visibleEntries.isEmpty() && state.content.fields.isEmpty() &&
            state.error == null && !state.content.hasMore) {
            item("empty") { EmptyNotice() }
        }
        if (state.loading && state.loaded) item("more") { LoadingBox() }
        item("bottom-space") { Spacer(Modifier.height(24.dp)) }
    }

    if (showLogoutDialog) {
        ConfirmDialog(
            title = "退出登录",
            text = "确认要退出登录吗？退出后需要重新登录。",
            icon = Icons.AutoMirrored.Outlined.Logout,
            confirmText = "退出",
            destructive = true,
            onDismiss = { showLogoutDialog = false },
            onConfirm = { showLogoutDialog = false; onLogout() },
        )
    }
}

@Composable
private fun IdentityCard(
    username: String?,
    daysVisited: String,
    timeRead: String,
    topicsEntered: String,
    onLogout: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(
                    avatarUrl = null,
                    username = username,
                    cornerRadius = 12.dp,
                    modifier = Modifier.size(56.dp),
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        username ?: "尚未确认登录",
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("你的社区足迹", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onLogout) { Text("退出登录") }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                Stat(daysVisited, "访问天数", Modifier.weight(1f))
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Stat(timeRead, "阅读时长", Modifier.weight(1.2f))
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Stat(topicsEntered, "浏览话题", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private class MetricSpec(
    val label: String,
    val value: String,
    val icon: ImageVector,
    val onClick: (() -> Unit)? = null,
)

/** 统计行:图标 + 名称 + 数值;可点的那几行(草稿、创建话题、收藏)带箭头并跳转到对应列表。 */
@Composable
private fun MetricCard(spec: MetricSpec, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (spec.onClick != null) Modifier.clickable(onClick = spec.onClick) else Modifier)
                .heightIn(min = 56.dp)
                .padding(start = 20.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(spec.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(16.dp))
            Text(spec.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                spec.value,
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            if (spec.onClick != null) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
            } else {
                Spacer(Modifier.width(24.dp))
            }
        }
        HorizontalDivider(Modifier.padding(start = 58.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** 通知 / 活动 / 消息列表的一行。未读通知加粗并带浅色底。 */
@Composable
private fun EntryRow(entry: ProfileEntry, notification: Boolean, onClick: () -> Unit) {
    val unread = notification && entry.unread
    ListItem(
        overlineContent = if (notification) {
            {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    entry.actor?.takeIf { it.isNotBlank() }?.let {
                        Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(notificationTypeTag(entry.notificationType), color = notificationTypeTint(entry.notificationType))
                }
            }
        } else entry.actor?.let { actor -> { Text(actor) } },
        headlineContent = {
            Text(
                entry.title,
                maxLines = if (notification) 2 else 3,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (unread) FontWeight.SemiBold else null,
            )
        },
        supportingContent = if (!notification && entry.subtitle.isNotBlank()) {
            { Text(entry.subtitle, maxLines = 3, overflow = TextOverflow.Ellipsis) }
        } else null,
        leadingContent = {
            if (notification) NotificationAvatar(entry)
            else UserAvatar(avatarUrl = entry.avatarUrl, username = entry.actor, modifier = Modifier.size(40.dp))
        },
        trailingContent = if (notification) {
            {
                Column(horizontalAlignment = Alignment.End) {
                    val date = entry.subtitle.substringAfterLast(" · ").takeLast(5)
                    if (date.isNotBlank()) Text(date, style = MaterialTheme.typography.labelSmall)
                    if (unread) {
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                    }
                }
            }
        } else null,
        colors = ListItemDefaults.colors(
            containerColor = if (unread) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f) else Color.Transparent,
        ),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** 通知头像:右下角叠一个表示通知类型的小图标。 */
@Composable
private fun NotificationAvatar(entry: ProfileEntry) {
    Box(Modifier.size(44.dp)) {
        UserAvatar(
            avatarUrl = entry.avatarUrl,
            username = entry.actor,
            modifier = Modifier.size(40.dp).align(Alignment.TopStart),
        )
        val tint = notificationTypeTint(entry.notificationType)
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .size(20.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .padding(2.dp)
                .background(tint.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(notificationTypeIcon(entry.notificationType), contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
        }
    }
}

private fun filterIcon(filter: NotificationFilter): ImageVector = when (filter) {
    NotificationFilter.All -> Icons.Outlined.Notifications
    NotificationFilter.Replies -> Icons.AutoMirrored.Outlined.Reply
    NotificationFilter.Likes -> Icons.Outlined.FavoriteBorder
    NotificationFilter.Messages -> Icons.Outlined.MailOutline
}

private fun notificationTypeIcon(type: Int): ImageVector = when (type) {
    1, 15, 29, 32 -> Icons.Outlined.AlternateEmail
    2 -> Icons.AutoMirrored.Outlined.Reply
    3, 33 -> Icons.Outlined.FormatQuote
    4 -> Icons.Outlined.Edit
    5, 19, 25 -> Icons.Outlined.FavoriteBorder
    6, 7, 16, 30, 31 -> Icons.Outlined.MailOutline
    11, 38 -> Icons.Outlined.Link
    12 -> Icons.Outlined.EmojiEvents
    14 -> Icons.Outlined.CheckCircle
    43 -> Icons.Outlined.RocketLaunch
    800, 801, 802 -> Icons.Outlined.PersonAdd
    else -> Icons.Outlined.ChatBubbleOutline
}

@Composable
private fun notificationTypeTint(type: Int): Color = when (type) {
    5, 19, 25 -> AppTheme.colors.like
    14 -> AppTheme.colors.success
    12 -> AppTheme.colors.gold
    43 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.primary
}

private fun notificationTypeTag(type: Int): String = when (type) {
    1, 15 -> "提及"
    2 -> "回复"
    3 -> "引用"
    4 -> "编辑"
    5, 19 -> "赞了你"
    6, 7, 16 -> "私信"
    8 -> "接受邀请"
    9, 17 -> "新帖"
    10 -> "移动"
    11, 38 -> "链接"
    12 -> "徽章"
    13 -> "话题邀请"
    14 -> "解决方案"
    25 -> "表情回应"
    27 -> "活动提醒"
    28 -> "活动邀请"
    29, 32 -> "聊天提及"
    30 -> "聊天消息"
    31 -> "聊天邀请"
    33 -> "聊天引用"
    34 -> "指派"
    43 -> "简评"
    800 -> "关注动态"
    801 -> "关注发帖"
    802 -> "关注回复"
    else -> "通知"
}
