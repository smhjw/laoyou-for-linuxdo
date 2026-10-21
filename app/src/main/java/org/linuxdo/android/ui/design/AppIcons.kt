package org.linuxdo.android.ui.design

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.ui.graphics.vector.ImageVector
import org.linuxdo.android.data.FloatingAction
import org.linuxdo.android.data.TopicNotificationLevel
import org.linuxdo.android.data.TopicSortOrder

/** 业务概念到 Material 图标的统一映射,各页面同一概念用同一个图标。 */
object AppIcons {
    fun sort(order: TopicSortOrder): ImageVector = when (order) {
        TopicSortOrder.Default -> Icons.AutoMirrored.Outlined.Sort
        TopicSortOrder.Activity -> Icons.Outlined.Update
        TopicSortOrder.Created -> Icons.Outlined.CalendarToday
        TopicSortOrder.Likes -> Icons.Outlined.FavoriteBorder
        TopicSortOrder.Views -> Icons.Outlined.Visibility
        TopicSortOrder.Posts -> Icons.Outlined.ChatBubbleOutline
        TopicSortOrder.Posters -> Icons.Outlined.Group
    }

    fun notificationLevel(level: TopicNotificationLevel): ImageVector = when (level) {
        TopicNotificationLevel.Watching -> Icons.Outlined.NotificationsActive
        TopicNotificationLevel.Tracking -> Icons.Outlined.Notifications
        TopicNotificationLevel.Regular -> Icons.Outlined.NotificationsNone
        TopicNotificationLevel.Muted -> Icons.Outlined.NotificationsOff
    }

    fun floatingAction(action: FloatingAction): ImageVector = when (action) {
        FloatingAction.CreateTopic -> Icons.Outlined.Edit
        FloatingAction.Sort -> Icons.AutoMirrored.Outlined.Sort
        FloatingAction.Drafts -> Icons.Outlined.Drafts
        FloatingAction.Search -> Icons.Outlined.Search
        FloatingAction.Categories -> Icons.Outlined.GridView
    }
}
