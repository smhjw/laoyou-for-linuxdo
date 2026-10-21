package org.linuxdo.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.linuxdo.android.ui.design.IosDivider
import org.linuxdo.android.ui.design.IosGroupCard
import org.linuxdo.android.ui.design.avatarShape
import org.linuxdo.android.ui.design.IosLargeTitleScaffold
import org.linuxdo.android.ui.design.IosRow
import org.linuxdo.android.ui.design.IosTheme
import org.linuxdo.android.ui.design.IosLoadingBox
import org.linuxdo.android.data.ProfileSection
import org.linuxdo.android.data.NotificationFilter
import coil.compose.AsyncImage
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import android.graphics.Canvas
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import org.linuxdo.android.ui.*
import org.linuxdo.android.ui.design.IosTextAction

/** 列表攒够这么多条才允许继续自动翻页;不足则改成手动按钮。 */
private const val AUTO_LOAD_THRESHOLD = 20

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
    val summaryState = states[ProfileSection.Summary] ?: ProfileUiState()
    val summaryFields = summaryState.content.fields

    val visibleEntries = state.content.entries
    val nearEnd by remember { derivedStateOf {
        (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= list.layoutInfo.totalItemsCount - 3
    } }
    LaunchedEffect(section, active) {
        if (active) {
            onLoad(section, section == ProfileSection.Notifications, false)
            if (section != ProfileSection.Summary && summaryFields.isEmpty()) {
                onLoad(ProfileSection.Summary, false, false)
            }
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

    val daysVisited = remember(summaryFields) { findMetric(summaryFields, "访问天数") }
    val timeRead = remember(summaryFields) { findMetric(summaryFields, "阅读时长") }
    val topicsEntered = remember(summaryFields) { findMetric(summaryFields, "浏览话题") }

    val postsRead = remember(summaryFields) { findMetric(summaryFields, "已读帖子") }
    val likesReceived = remember(summaryFields) { findMetric(summaryFields, "收到赞") }
    val likesGiven = remember(summaryFields) { findMetric(summaryFields, "送出赞") }
    val postCount = remember(summaryFields) { findMetric(summaryFields, "回复数量") }
    val topicCount = remember(summaryFields) { findMetric(summaryFields, "创建话题", findMetric(summaryFields, "发表话题")) }
    val bookmarkCount = remember(summaryFields) { findMetric(summaryFields, "我的收藏") }

    IosLargeTitleScaffold(
        title = if (notificationOnly) "通知" else "我的",
        listState = list,
        onBack = onBack,
        showLargeTitle = !notificationOnly,
        backLabel = if (notificationOnly) "" else "返回",
        scrollToTopRequest = scrollToTopRequest,
        trailing = {
            if (!notificationOnly) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    trailing()
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onSettings),
                        contentAlignment = Alignment.Center,
                    ) {
                        ProfileSettingsGearIcon(tint = IosTheme.colors.secondaryLabel)
                    }
                }
            }
        },
    ) {
        if (!notificationOnly) {
            // ── 顶部用户信息与核心三大足迹指标卡片 ──
            item("identity-card") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(IosTheme.colors.card)
                        .padding(horizontal = 20.dp, vertical = 22.dp),
                ) {
                    // 上半部分: 头像 + 用户名 + 你的社区足迹 + 右侧退出登录胶囊
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .clip(avatarShape(username, cornerRadius = 8.dp))
                                .background(Color(0xFFE1F0FF)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = (username?.firstOrNull()?.uppercase() ?: "U"),
                                style = androidx.compose.ui.text.TextStyle(
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = IosTheme.colors.accent,
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = username ?: "尚未确认登录",
                                style = androidx.compose.ui.text.TextStyle(
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = IosTheme.colors.label,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "你的社区足迹",
                                style = IosTheme.type.footnote,
                                color = IosTheme.colors.secondaryLabel,
                            )
                        }
                        // 退出登录胶囊按钮
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Color(0xFFFFF0F0))
                                .clickable { showLogoutDialog = true }
                                .padding(horizontal = 15.dp, vertical = 7.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "退出登录",
                                style = IosTheme.type.caption.copy(fontSize = 12.sp),
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFFF4D4F),
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    IosDivider()
                    Spacer(Modifier.height(18.dp))

                    // 下半部分: 3 列核心指标 (访问天数、阅读时长、浏览话题)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 访问天数
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = daysVisited,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = IosTheme.colors.label,
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                text = "访问天数",
                                style = IosTheme.type.caption.copy(fontSize = 12.sp),
                                color = IosTheme.colors.secondaryLabel,
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(0.5.dp)
                                .height(32.dp)
                                .background(IosTheme.colors.separator),
                        )

                        // 阅读时长
                        Column(
                            modifier = Modifier.weight(1.3f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = timeRead,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = IosTheme.colors.label,
                                maxLines = 1,
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                text = "阅读时长",
                                style = IosTheme.type.caption.copy(fontSize = 12.sp),
                                color = IosTheme.colors.secondaryLabel,
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(0.5.dp)
                                .height(32.dp)
                                .background(IosTheme.colors.separator),
                        )

                        // 浏览话题
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = topicsEntered,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                                color = IosTheme.colors.label,
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                text = "浏览话题",
                                style = IosTheme.type.caption.copy(fontSize = 12.sp),
                                color = IosTheme.colors.secondaryLabel,
                            )
                        }
                    }
                }
            }

            // ── 四大功能入口横向 4 宫格卡片 (我的总结、活动足迹、系统通知、消息中心) ──
            item("nav-quad-cards") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // 1. 我的总结
                    ProfileNavCard(
                        title = "我的总结",
                        icon = ProfileNavIconType.Summary,
                        selected = section == ProfileSection.Summary,
                        modifier = Modifier.weight(1f),
                        onClick = { section = ProfileSection.Summary },
                    )
                    // 2. 活动足迹
                    ProfileNavCard(
                        title = "活动足迹",
                        icon = ProfileNavIconType.Activity,
                        selected = section == ProfileSection.Activity,
                        modifier = Modifier.weight(1f),
                        onClick = { section = ProfileSection.Activity },
                    )
                    // 3. 系统通知
                    ProfileNavCard(
                        title = "系统通知",
                        icon = ProfileNavIconType.Notifications,
                        selected = section == ProfileSection.Notifications,
                        hasBadge = unreadFilters.isNotEmpty() || visibleEntries.any { it.unread },
                        modifier = Modifier.weight(1f),
                        onClick = {
                            if (section == ProfileSection.Notifications) {
                                onLoad(ProfileSection.Notifications, true, false)
                            } else {
                                section = ProfileSection.Notifications
                            }
                        },
                    )
                    // 4. 消息中心
                    ProfileNavCard(
                        title = "消息中心",
                        icon = ProfileNavIconType.Messages,
                        selected = section == ProfileSection.Messages,
                        modifier = Modifier.weight(1f),
                        onClick = { section = ProfileSection.Messages },
                    )
                }
            }

            // ── 当处于“我的总结”时：展示 6 个双列大统计卡片 (已读帖子、收到赞、送出赞、回复数量、创建话题、我的收藏) ──
            if (section == ProfileSection.Summary) {
                item("metric-cards-row1") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ProfileMetricCard(
                            label = "已读帖子",
                            value = postsRead,
                            iconType = MetricCardIconType.Book,
                            modifier = Modifier.weight(1f),
                        )
                        ProfileMetricCard(
                            label = "收到赞",
                            value = likesReceived,
                            iconType = MetricCardIconType.Heart,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                item("metric-cards-row2") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ProfileMetricCard(
                            label = "我的草稿",
                            value = draftCount,
                            iconType = MetricCardIconType.Draft,
                            modifier = Modifier.weight(1f),
                            onClick = onOpenDrafts,
                        )
                        ProfileMetricCard(
                            label = "回复数量",
                            value = postCount,
                            iconType = MetricCardIconType.Reply,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                item("metric-cards-row3") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 7.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ProfileMetricCard(
                            label = "创建话题",
                            value = topicCount,
                            iconType = MetricCardIconType.Rocket,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (!username.isNullOrBlank()) onOpenUserTopics?.invoke(username, "创建的话题", false)
                            },
                        )
                        ProfileMetricCard(
                            label = "我的收藏",
                            value = bookmarkCount,
                            iconType = MetricCardIconType.Bookmark,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (!username.isNullOrBlank()) onOpenUserTopics?.invoke(username, "我的收藏", true)
                            },
                        )
                    }
                }
            }
        }

        // 通知分类 Tab (全部、回复、点赞、私信)
        if (section == ProfileSection.Notifications) item("notification-filters") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NotificationFilter.entries.forEach { filter ->
                    val selected = notificationFilter == filter
                    val color = if (selected) IosTheme.colors.accent else IosTheme.colors.secondaryLabel
                    val hasUnread = filter != NotificationFilter.All && (
                        filter in unreadFilters || visibleEntries.any { it.unread && filter.includes(it.notificationType) }
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) IosTheme.colors.accent.copy(alpha = 0.12f) else IosTheme.colors.card)
                            .clickable { onNotificationFilter(filter) }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (hasUnread) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF3B30)),
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        NotificationTabIcon(filter, color)
                        Text(
                            text = filter.label,
                            style = IosTheme.type.subheadline,
                            color = color,
                            modifier = Modifier.padding(start = 5.dp),
                        )
                    }
                }
            }
        }

        if (state.loading && !state.loaded) item("loading") { IosLoadingBox() }
        state.error?.let { message -> item("error") { ErrorNotice(message) { onLoad(section, true, false) } } }

        if (section != ProfileSection.Summary) {
            items(visibleEntries, key = { it.id }) { entry ->
                val notification = section == ProfileSection.Notifications
                IosGroupCard(Modifier.padding(bottom = if (notification) 4.dp else 8.dp)) {
                    IosRow(onClick = {
                        if (section == ProfileSection.Notifications && entry.unread) entry.id.toLongOrNull()?.let(onReadNotification)
                        entry.topicId?.let { onOpenTopic(it, entry.title, entry.postNumber) }
                    }) {
                        if (entry.unread) Text("•", color = IosTheme.colors.accent, modifier = Modifier.padding(end = 6.dp))
                        if (notification) {
                            NotificationAvatarWithBadge(
                                avatarUrl = entry.avatarUrl,
                                actor = entry.actor,
                                notificationType = entry.notificationType,
                                unread = entry.unread,
                            )
                            Spacer(Modifier.width(10.dp))
                        } else {
                            entry.avatarUrl?.let { url ->
                                AsyncImage(url, entry.actor, modifier = Modifier.size(32.dp).clip(avatarShape(entry.actor, url, 4.dp)))
                                Spacer(Modifier.width(10.dp))
                            }
                        }
                        Column(Modifier.weight(1f).padding(vertical = if (notification) 0.dp else 4.dp)) {
                            if (notification) {
                                val typeTag = notificationTypeTag(entry.notificationType)
                                val dateStr = entry.subtitle.substringAfterLast(" · ").takeLast(5)
                                Row(
                                    Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (!entry.actor.isNullOrBlank()) {
                                            Text(
                                                text = entry.actor,
                                                style = IosTheme.type.footnote,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                            )
                                            Spacer(Modifier.width(6.dp))
                                        }
                                        Box(
                                            Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(notificationTypeTint(entry.notificationType).copy(alpha = 0.13f))
                                                .padding(horizontal = 5.dp, vertical = 1.dp),
                                        ) {
                                            Text(
                                                text = typeTag,
                                                style = IosTheme.type.caption.copy(fontSize = 11.sp),
                                                fontWeight = FontWeight.Medium,
                                                color = notificationTypeTint(entry.notificationType),
                                            )
                                        }
                                    }
                                    if (dateStr.isNotBlank()) {
                                        Text(
                                            text = dateStr,
                                            style = IosTheme.type.caption,
                                            color = IosTheme.colors.secondaryLabel,
                                            modifier = Modifier.padding(start = 6.dp),
                                        )
                                    }
                                }
                            } else {
                                entry.actor?.let { actor ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(actor, style = IosTheme.type.footnote, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                            Text(
                                text = entry.title,
                                style = if (notification) IosTheme.type.subheadline else IosTheme.type.body,
                                maxLines = if (notification) 2 else 3,
                                modifier = Modifier.padding(top = if (notification) 2.dp else 0.dp),
                            )
                            if (!notification && entry.subtitle.isNotBlank()) Text(entry.subtitle, style = IosTheme.type.footnote,
                                color = IosTheme.colors.secondaryLabel, maxLines = 3, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }

        // 还有更多但不足一屏: 给手动加载入口
        if (state.loaded && !state.loading && state.error == null && state.content.hasMore &&
            visibleEntries.size < AUTO_LOAD_THRESHOLD && state.content.fields.isEmpty()) {
            item("load-more") {
                Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                    IosTextAction(if (visibleEntries.isEmpty()) "这一页没有更多，继续往前找" else "加载更早的记录") {
                        onLoad(section, false, true)
                    }
                }
            }
        }
        if (state.loaded && visibleEntries.isEmpty() && state.content.fields.isEmpty() &&
            state.error == null && !state.content.hasMore) {
            item("empty") { EmptyNotice() }
        }
        if (state.loading && state.loaded) item("more") { IosLoadingBox() }
    }

    // 退出登录确认对话框（防止误触）
    if (showLogoutDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("退出登录") },
            text = { Text("确认要退出登录吗？退出后需要重新输入账号密码。") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showLogoutDialog = false; onLogout() },
                ) {
                    Text("退出", color = IosTheme.colors.destructive)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showLogoutDialog = false },
                ) {
                    Text("取消")
                }
            },
        )
    }
}

private fun findMetric(fields: List<Pair<String, String>>, key: String, fallback: String = "0"): String {
    return fields.firstOrNull { it.first == key }?.second?.ifBlank { fallback } ?: fallback
}

@Composable
private fun ProfileSettingsGearIcon(tint: Color) {
    Canvas(Modifier.size(22.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val cx = 12f
            val cy = 12f
            val r = 9.2f
            val cornerRatio = 0.22f
            val vertices = Array(6) { i ->
                val rad = (i * 60f) * (kotlin.math.PI / 180.0)
                Offset(
                    x = cx + (r * kotlin.math.cos(rad)).toFloat(),
                    y = cy + (r * kotlin.math.sin(rad)).toFloat(),
                )
            }
            val hexPath = Path().apply {
                for (i in 0 until 6) {
                    val prev = vertices[(i + 5) % 6]
                    val curr = vertices[i]
                    val next = vertices[(i + 1) % 6]
                    val pStart = Offset(
                        x = curr.x + (prev.x - curr.x) * cornerRatio,
                        y = curr.y + (prev.y - curr.y) * cornerRatio,
                    )
                    val pEnd = Offset(
                        x = curr.x + (next.x - curr.x) * cornerRatio,
                        y = curr.y + (next.y - curr.y) * cornerRatio,
                    )
                    if (i == 0) moveTo(pStart.x, pStart.y) else lineTo(pStart.x, pStart.y)
                    quadraticTo(curr.x, curr.y, pEnd.x, pEnd.y)
                }
                close()
            }
            val stroke = Stroke(
                width = 1.7f,
                cap = StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round,
            )
            drawPath(hexPath, color = tint, style = stroke)
            drawCircle(color = tint, radius = 3.0f, center = Offset(cx, cy), style = stroke)
        }
    }
}

enum class ProfileNavIconType {
    Summary, Activity, Notifications, Messages
}

@Composable
private fun ProfileNavCard(
    title: String,
    icon: ProfileNavIconType,
    selected: Boolean,
    modifier: Modifier = Modifier,
    hasBadge: Boolean = false,
    onClick: () -> Unit,
) {
    val cardBg = if (selected) IosTheme.colors.accent.copy(alpha = 0.08f) else IosTheme.colors.card
    val borderColor = if (selected) IosTheme.colors.accent else IosTheme.colors.separator.copy(alpha = 0.35f)
    val contentColor = if (selected) IosTheme.colors.accent else IosTheme.colors.label

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .border(if (selected) 1.5.dp else 0.5.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(24.dp)) {
                    scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
                        when (icon) {
                            ProfileNavIconType.Summary -> {
                                val doc = Path().apply {
                                    moveTo(5f, 3f)
                                    lineTo(15f, 3f)
                                    lineTo(19f, 7f)
                                    lineTo(19f, 21f)
                                    lineTo(5f, 21f)
                                    close()
                                }
                                drawPath(doc, contentColor, style = Stroke(1.8f))
                                drawLine(contentColor, Offset(8f, 10f), Offset(16f, 10f), strokeWidth = 1.8f, cap = StrokeCap.Round)
                                drawLine(contentColor, Offset(8f, 14f), Offset(16f, 14f), strokeWidth = 1.8f, cap = StrokeCap.Round)
                                drawLine(contentColor, Offset(8f, 18f), Offset(13f, 18f), strokeWidth = 1.8f, cap = StrokeCap.Round)
                            }
                            ProfileNavIconType.Activity -> {
                                val p1 = Path().apply {
                                    moveTo(7f, 17f)
                                    cubicTo(5f, 14f, 7f, 10f, 9.5f, 11f)
                                    cubicTo(12f, 12f, 10f, 18f, 7f, 17f)
                                    close()
                                }
                                val p2 = Path().apply {
                                    moveTo(14f, 11f)
                                    cubicTo(12f, 8f, 14f, 4f, 16.5f, 5f)
                                    cubicTo(19f, 6f, 17f, 12f, 14f, 11f)
                                    close()
                                }
                                drawPath(p1, contentColor)
                                drawPath(p2, contentColor)
                            }
                            ProfileNavIconType.Notifications -> {
                                val bell = Path().apply {
                                    moveTo(5f, 17f); lineTo(7f, 14f); lineTo(7f, 9f)
                                    cubicTo(7f, 2.5f, 17f, 2.5f, 17f, 9f)
                                    lineTo(17f, 14f); lineTo(19f, 17f); close()
                                }
                                drawPath(bell, contentColor, style = Stroke(1.8f))
                                drawCircle(contentColor, 1.6f, Offset(12f, 20f))
                            }
                            ProfileNavIconType.Messages -> {
                                val chat = Path().apply {
                                    moveTo(4f, 5f)
                                    lineTo(20f, 5f)
                                    cubicTo(21f, 5f, 21.5f, 5.5f, 21.5f, 6.5f)
                                    lineTo(21.5f, 15f)
                                    cubicTo(21.5f, 16f, 21f, 16.5f, 20f, 16.5f)
                                    lineTo(8f, 16.5f)
                                    lineTo(4f, 20f)
                                    lineTo(4f, 6.5f)
                                    cubicTo(4f, 5.5f, 4.5f, 5f, 4f, 5f)
                                    close()
                                }
                                drawPath(chat, contentColor, style = Stroke(1.8f))
                                drawCircle(contentColor, 1.2f, Offset(8.5f, 11f))
                                drawCircle(contentColor, 1.2f, Offset(12f, 11f))
                                drawCircle(contentColor, 1.2f, Offset(15.5f, 11f))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                style = IosTheme.type.caption.copy(fontSize = 13.sp),
                color = contentColor,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
            )
        }
        if (hasBadge) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF3B30))
                    .align(Alignment.TopEnd)
                    .offset(x = (-5).dp, y = (5).dp)
            )
        }
    }
}

enum class MetricCardIconType {
    Book, Heart, Thumb, Rocket, Reply, Bookmark, Draft
}

@Composable
private fun ProfileMetricCard(
    label: String,
    value: String,
    iconType: MetricCardIconType,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(IosTheme.colors.card)
            .border(0.5.dp, IosTheme.colors.separator.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(32.dp)) {
            scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
                when (iconType) {
                    MetricCardIconType.Book -> {
                        val leftPage = Path().apply {
                            moveTo(12f, 6f)
                            cubicTo(9f, 4.5f, 4f, 4.5f, 3f, 5f)
                            lineTo(3f, 18f)
                            cubicTo(4f, 17.5f, 9f, 17.5f, 12f, 19f)
                            close()
                        }
                        val rightPage = Path().apply {
                            moveTo(12f, 6f)
                            cubicTo(15f, 4.5f, 20f, 4.5f, 21f, 5f)
                            lineTo(21f, 18f)
                            cubicTo(20f, 17.5f, 15f, 17.5f, 12f, 19f)
                            close()
                        }
                        drawPath(leftPage, Color(0xFF67C23A))
                        drawPath(rightPage, Color(0xFF409EFF))
                        drawLine(Color.White, Offset(12f, 5.5f), Offset(12f, 19f), strokeWidth = 1.2f)
                    }
                    MetricCardIconType.Heart -> {
                        val heart = Path().apply {
                            moveTo(12f, 21f); cubicTo(10f, 19f, 2.5f, 13f, 2.5f, 8f)
                            cubicTo(2.5f, 2.5f, 9.5f, 1.5f, 12f, 6.2f)
                            cubicTo(14.5f, 1.5f, 21.5f, 2.5f, 21.5f, 8f)
                            cubicTo(21.5f, 13f, 14f, 19f, 12f, 21f); close()
                        }
                        drawPath(heart, Color(0xFFFF4D6A))
                    }
                    MetricCardIconType.Thumb -> {
                        val thumb = Path().apply {
                            moveTo(8f, 19f); lineTo(4f, 19f); lineTo(4f, 9f); lineTo(8f, 9f); close()
                            moveTo(9f, 9f); lineTo(13f, 2f); cubicTo(14.5f, 2f, 15.5f, 3.5f, 15f, 5f)
                            lineTo(14f, 9f); lineTo(20f, 9f); cubicTo(21.5f, 9f, 22f, 10.5f, 21.5f, 12f)
                            lineTo(19.5f, 17.5f); cubicTo(19f, 18.5f, 18f, 19f, 17f, 19f); lineTo(9f, 19f); close()
                        }
                        drawPath(thumb, Color(0xFFFA8C16))
                    }
                    MetricCardIconType.Rocket -> {
                        val rocket = Path().apply {
                            moveTo(20f, 4f)
                            cubicTo(15f, 4f, 10.5f, 7.5f, 8.5f, 12f)
                            lineTo(5f, 13f); lineTo(7.5f, 15.5f)
                            lineTo(8.5f, 15.5f); lineTo(11f, 19f)
                            lineTo(12f, 15.5f)
                            cubicTo(16.5f, 13.5f, 20f, 9f, 20f, 4f)
                            close()
                        }
                        drawPath(rocket, Color(0xFF8560F4))
                        drawCircle(Color.White, radius = 2f, center = Offset(14.5f, 9.5f))
                    }
                    MetricCardIconType.Reply -> {
                        val bubble = Path().apply {
                            moveTo(4f, 12f)
                            cubicTo(4f, 6.5f, 7.5f, 3f, 12f, 3f)
                            cubicTo(16.5f, 3f, 20f, 6.5f, 20f, 12f)
                            cubicTo(20f, 17.5f, 16.5f, 21f, 12f, 21f)
                            cubicTo(10f, 21f, 8.5f, 20.5f, 7f, 19.5f)
                            lineTo(4f, 20.5f)
                            lineTo(4.8f, 17.5f)
                            cubicTo(4.3f, 16f, 4f, 14f, 4f, 12f)
                            close()
                        }
                        drawPath(bubble, Color(0xFF1890FF))
                        drawCircle(Color.White, 1.2f, Offset(9f, 12f))
                        drawCircle(Color.White, 1.2f, Offset(12f, 12f))
                        drawCircle(Color.White, 1.2f, Offset(15f, 12f))
                    }
                    MetricCardIconType.Bookmark -> {
                        val bookmark = Path().apply {
                            moveTo(6f, 3.5f)
                            lineTo(18f, 3.5f)
                            cubicTo(18.5f, 3.5f, 19f, 4f, 19f, 4.5f)
                            lineTo(19f, 20.5f)
                            lineTo(12f, 16f)
                            lineTo(5f, 20.5f)
                            lineTo(5f, 4.5f)
                            cubicTo(5f, 4f, 5.5f, 3.5f, 6f, 3.5f)
                            close()
                        }
                        drawPath(bookmark, Color(0xFFFFB800))
                    }
                    MetricCardIconType.Draft -> {
                        val doc = Path().apply {
                            moveTo(5f, 3f)
                            lineTo(14.5f, 3f)
                            lineTo(19f, 7.5f)
                            lineTo(19f, 20.5f)
                            lineTo(5f, 20.5f)
                            close()
                        }
                        drawPath(doc, Color(0xFFFA8C16))
                        drawLine(Color.White, Offset(8f, 10f), Offset(16f, 10f), strokeWidth = 1.6f, cap = StrokeCap.Round)
                        drawLine(Color.White, Offset(8f, 14f), Offset(15f, 14f), strokeWidth = 1.6f, cap = StrokeCap.Round)
                        drawLine(Color.White, Offset(8f, 17.5f), Offset(12.5f, 17.5f), strokeWidth = 1.6f, cap = StrokeCap.Round)
                    }
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = label,
                style = IosTheme.type.caption.copy(fontSize = 12.sp),
                color = IosTheme.colors.secondaryLabel,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = IosTheme.colors.label,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun notificationTypeTint(type: Int): Color = when (type) {
    5, 19 -> Color(0xFFFF3B30)
    25 -> Color(0xFFFF9500)
    14 -> Color(0xFF30B85B)
    12 -> Color(0xFFAF52DE)
    43 -> Color(0xFF5856D6)
    800, 801, 802 -> Color(0xFF0084FF)
    else -> IosTheme.colors.accent
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

/**
 * 参照 Discourse Web 通知列表样式：在圆形头像右上角叠加带白/底色描边的类型角标图标，
 * 一眼区分回复(弯箭头)、点赞(爱心)、表情回应(笑脸点赞)、采纳解答(勾选框)、徽章(星章)、简评(小火箭)、私信(信封)、关注动态(蓝底小人加号/铃铛)。
 */
@Composable
private fun NotificationAvatarWithBadge(
    avatarUrl: String?,
    actor: String?,
    notificationType: Int,
    unread: Boolean,
) {
    val cardColor = IosTheme.colors.card
    val isFollowType = notificationType in setOf(800, 801, 802)
    val badgeBgColor = if (isFollowType) Color(0xFF0084FF) else cardColor
    val badgeIconColor = when {
        isFollowType -> Color.White
        unread -> IosTheme.colors.accent
        IosTheme.colors.isDark -> Color(0xFFD1D1D6)
        else -> Color(0xFF3A3A3C)
    }
    Box(Modifier.size(38.dp)) {
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = actor,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .size(34.dp)
                    .clip(avatarShape(actor, avatarUrl, 4.dp)),
            )
        } else {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .size(34.dp)
                    .clip(avatarShape(actor, null, 4.dp))
                    .background(IosTheme.colors.fieldBackground),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = actor?.firstOrNull()?.uppercase() ?: "★",
                    style = IosTheme.type.footnote,
                    fontWeight = FontWeight.Bold,
                    color = IosTheme.colors.secondaryLabel,
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(18.dp)
                .clip(CircleShape)
                .background(badgeBgColor)
                .then(
                    if (isFollowType) {
                        Modifier.border(1.5.dp, cardColor, CircleShape)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(13.dp)) {
                scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
                    val shape = Path()
                    when (notificationType) {
                        // 关注动态：800 关注人/成为粉丝（蓝底白图 👤+ 小人加号）
                        800 -> {
                            drawCircle(Color.White, radius = 3.6f, center = Offset(8f, 7.5f))
                            val body = Path().apply {
                                moveTo(3f, 18f)
                                cubicTo(3f, 13.5f, 5.5f, 13f, 8f, 13f)
                                cubicTo(10.5f, 13f, 13f, 13.5f, 13f, 18f)
                                close()
                            }
                            drawPath(body, Color.White)
                            drawLine(Color.White, start = Offset(15f, 10f), end = Offset(21f, 10f), strokeWidth = 2.4f, cap = StrokeCap.Round)
                            drawLine(Color.White, start = Offset(18f, 7f), end = Offset(18f, 13f), strokeWidth = 2.4f, cap = StrokeCap.Round)
                        }
                        // 关注动态：801 关注人发帖、802 关注人回复（蓝底白图 👤🔔 小人铃铛）
                        801, 802 -> {
                            drawCircle(Color.White, radius = 3.4f, center = Offset(7.5f, 7.5f))
                            val body = Path().apply {
                                moveTo(2.5f, 18f)
                                cubicTo(2.5f, 13.5f, 5f, 13f, 7.5f, 13f)
                                cubicTo(10f, 13f, 12.5f, 13.5f, 12.5f, 18f)
                                close()
                            }
                            drawPath(body, Color.White)
                            val bell = Path().apply {
                                moveTo(15f, 14f)
                                lineTo(16f, 11f)
                                cubicTo(16f, 8.5f, 20f, 8.5f, 20f, 11f)
                                lineTo(21f, 14f)
                                close()
                            }
                            drawPath(bell, Color.White)
                            drawCircle(Color.White, radius = 1.3f, center = Offset(18f, 15.5f))
                        }
                        // 回复 / 提及 / 引用 / 聊天引用：左弯箭头 (↩)
                        1, 2, 3, 9, 15, 33 -> {
                            shape.moveTo(10f, 5f); shape.lineTo(3f, 11f); shape.lineTo(10f, 17f)
                            shape.lineTo(10f, 13f); shape.cubicTo(17f, 12f, 19f, 15f, 21f, 20f)
                            shape.cubicTo(21f, 10f, 17f, 8f, 10f, 9f); shape.close()
                            drawPath(shape, badgeIconColor)
                        }
                        // 点赞 / 合并点赞：实心爱心 (❤)
                        5, 19 -> {
                            shape.moveTo(12f, 21f); shape.cubicTo(10f, 19f, 2.5f, 13f, 2.5f, 8f)
                            shape.cubicTo(2.5f, 2.5f, 9.5f, 1.5f, 12f, 6.2f)
                            shape.cubicTo(14.5f, 1.5f, 21.5f, 2.5f, 21.5f, 8f)
                            shape.cubicTo(21.5f, 13f, 14f, 19f, 12f, 21f); shape.close()
                            drawPath(shape, badgeIconColor)
                        }
                        // 表情回应：圆脸 + 微笑弧线 + 右侧小拇指
                        25 -> {
                            drawCircle(badgeIconColor, radius = 7.5f, center = Offset(9.5f, 12f), style = Stroke(2.2f))
                            drawCircle(badgeIconColor, radius = 1.2f, center = Offset(7f, 10.2f))
                            drawCircle(badgeIconColor, radius = 1.2f, center = Offset(12f, 10.2f))
                            drawArc(
                                color = badgeIconColor,
                                startAngle = 25f,
                                sweepAngle = 130f,
                                useCenter = false,
                                topLeft = Offset(6.5f, 11f),
                                size = androidx.compose.ui.geometry.Size(6f, 4.5f),
                                style = Stroke(2f),
                            )
                            shape.moveTo(18f, 11f); shape.lineTo(19.5f, 7f); shape.lineTo(21.5f, 7.8f)
                            shape.lineTo(20.5f, 11f); shape.lineTo(23f, 11f); shape.lineTo(22.2f, 16.5f)
                            shape.lineTo(18f, 16.5f); shape.close()
                            drawPath(shape, badgeIconColor)
                        }
                        // 采纳解决方案：圆角方框 + 对勾 (☑)
                        14 -> {
                            if (unread) {
                                drawRoundRect(
                                    color = badgeIconColor,
                                    topLeft = Offset(3f, 3f),
                                    size = androidx.compose.ui.geometry.Size(18f, 18f),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
                                    style = Stroke(2.4f),
                                )
                                shape.moveTo(7.5f, 12.2f); shape.lineTo(10.6f, 15.3f); shape.lineTo(16.8f, 9f)
                                drawPath(shape, badgeIconColor, style = Stroke(2.4f))
                            } else {
                                drawRoundRect(
                                    color = badgeIconColor,
                                    topLeft = Offset(3f, 3f),
                                    size = androidx.compose.ui.geometry.Size(18f, 18f),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f),
                                )
                                shape.moveTo(7.5f, 12.2f); shape.lineTo(10.6f, 15.3f); shape.lineTo(16.8f, 9f)
                                drawPath(shape, cardColor, style = Stroke(2.4f))
                            }
                        }
                        // 获赠徽章：多角星章 (☀)
                        12 -> {
                            val points = 12
                            val outerR = 9.5f
                            val innerR = 7.2f
                            for (i in 0 until points * 2) {
                                val r = if (i % 2 == 0) outerR else innerR
                                val angle = i * Math.PI / points
                                val x = 12f + (r * kotlin.math.cos(angle)).toFloat()
                                val y = 12f + (r * kotlin.math.sin(angle)).toFloat()
                                if (i == 0) shape.moveTo(x, y) else shape.lineTo(x, y)
                            }
                            shape.close()
                            drawPath(shape, badgeIconColor)
                        }
                        // 简评 (Boost)：小火箭 (🚀)
                        43 -> {
                            shape.moveTo(20f, 4f)
                            shape.cubicTo(15f, 4f, 10.5f, 7.5f, 8.5f, 12f)
                            shape.lineTo(5f, 13f); shape.lineTo(7.5f, 15.5f)
                            shape.lineTo(8.5f, 15.5f); shape.lineTo(11f, 19f)
                            shape.lineTo(12f, 15.5f)
                            shape.cubicTo(16.5f, 13.5f, 20f, 9f, 20f, 4f)
                            shape.close()
                            drawPath(shape, badgeIconColor)
                            drawCircle(cardColor, radius = 1.6f, center = Offset(14.5f, 9.5f))
                        }
                        // 私信：信封 (✉)
                        6, 7, 16 -> {
                            shape.moveTo(3f, 6f); shape.lineTo(21f, 6f); shape.lineTo(21f, 18f)
                            shape.lineTo(3f, 18f); shape.close()
                            shape.moveTo(3f, 7f); shape.lineTo(12f, 13.5f); shape.lineTo(21f, 7f)
                            drawPath(shape, badgeIconColor, style = Stroke(2.2f))
                        }
                        // 其他通知：小铃铛
                        else -> {
                            shape.moveTo(5f, 17f); shape.lineTo(7f, 14f); shape.lineTo(7f, 9f)
                            shape.cubicTo(7f, 2.5f, 17f, 2.5f, 17f, 9f)
                            shape.lineTo(17f, 14f); shape.lineTo(19f, 17f); shape.close()
                            drawPath(shape, badgeIconColor)
                            drawCircle(badgeIconColor, 1.8f, Offset(12f, 20f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationTabIcon(filter: NotificationFilter, color: Color) {
    Canvas(Modifier.size(17.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val shape = Path()
            when (filter) {
                NotificationFilter.All -> {
                    shape.moveTo(5f, 17f); shape.lineTo(7f, 14f); shape.lineTo(7f, 9f)
                    shape.cubicTo(7f, 2f, 17f, 2f, 17f, 9f)
                    shape.lineTo(17f, 14f); shape.lineTo(19f, 17f); shape.close()
                    drawPath(shape, color, style = Stroke(1.7f))
                    drawCircle(color, 1.5f, Offset(12f, 20f))
                }
                NotificationFilter.Replies -> {
                    shape.moveTo(10f, 5f); shape.lineTo(3f, 11f); shape.lineTo(10f, 17f)
                    shape.lineTo(10f, 13f); shape.cubicTo(17f, 12f, 19f, 15f, 21f, 20f)
                    shape.cubicTo(21f, 10f, 17f, 8f, 10f, 9f); shape.close()
                    drawPath(shape, color)
                }
                NotificationFilter.Messages -> {
                    shape.moveTo(3f, 5f); shape.lineTo(21f, 5f); shape.lineTo(21f, 19f)
                    shape.lineTo(3f, 19f); shape.close()
                    shape.moveTo(3f, 6f); shape.lineTo(12f, 13f); shape.lineTo(21f, 6f)
                    drawPath(shape, color, style = Stroke(1.7f))
                }
                NotificationFilter.Likes -> {
                    shape.moveTo(12f, 21f); shape.cubicTo(10f, 19f, 3f, 13f, 3f, 8f)
                    shape.cubicTo(3f, 2f, 10f, 1f, 12f, 6f); shape.cubicTo(14f, 1f, 21f, 2f, 21f, 8f)
                    shape.cubicTo(21f, 13f, 14f, 19f, 12f, 21f); shape.close(); drawPath(shape, color)
                }
            }
        }
    }
}
