package org.linuxdo.android.ui.screen.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.linuxdo.android.data.AutoRefreshInterval
import org.linuxdo.android.ui.screen.ConfirmDialog

@Composable
internal fun ReadingSettings(
    treeView: Boolean,
    onTreeView: (Boolean) -> Unit,
    syncReadProgress: Boolean,
    onSyncReadProgress: (Boolean) -> Unit,
    autoRefreshInterval: AutoRefreshInterval,
    onAutoRefreshInterval: (AutoRefreshInterval) -> Unit,
    readingStatus: String?,
    canRetryReading: Boolean,
    onRetryReading: () -> Unit,
) {
    var showSyncConfirm by remember { mutableStateOf(false) }
    var showIntervalDialog by remember { mutableStateOf(false) }

    SettingsGroup(title = "阅读偏好") {
        SettingsSwitchItem(
            title = "树形查看话题",
            icon = Icons.Outlined.AccountTree,
            summary = "按回复关系树状排列，关闭后恢复顺序楼层",
            checked = treeView,
            onCheckedChange = onTreeView,
        )
        // 开启会向服务端上报阅读进度,可能触发站点验证,所以要先确认。
        SettingsSwitchItem(
            title = "同步楼层与阅读时长",
            icon = Icons.Outlined.Schedule,
            summary = "向服务端上报阅读位置与时长，消除网页端未读标记",
            checked = syncReadProgress,
            onCheckedChange = { if (it) showSyncConfirm = true else onSyncReadProgress(false) },
        )
        SettingsItem(
            title = "自动感知新话题",
            icon = Icons.Outlined.NewReleases,
            summary = "停留在列表时静默探测并提示新话题",
            value = autoRefreshInterval.label,
            onClick = { showIntervalDialog = true },
        )
        if (syncReadProgress && readingStatus != null) {
            SettingsItem(
                title = readingStatus,
                trailing = if (canRetryReading) {
                    { TextButton(onClick = onRetryReading) { Text("继续") } }
                } else null,
            )
        }
    }

    if (showSyncConfirm) {
        ConfirmDialog(
            title = "开启阅读进度同步",
            text = "开启后将向服务端上报各楼层阅读进度与阅读时长（消除网页端未读小蓝点）。\n\n" +
                "需要站点安全验证时会显示验证页，确认上报成功后回到当前话题。取消验证会暂停同步，可在设置中继续。\n\n" +
                "话题访问记录不受此开关控制。",
            confirmText = "确认开启",
            onDismiss = { showSyncConfirm = false },
            onConfirm = {
                showSyncConfirm = false
                onSyncReadProgress(true)
            },
        )
    }

    if (showIntervalDialog) {
        SingleChoiceDialog(
            title = "新话题感知频率",
            description = "停留在话题列表时只读请求 /latest.json 探测新话题并在顶部提示。频率越高越及时。",
            options = AutoRefreshInterval.entries,
            selected = autoRefreshInterval,
            label = { it.label },
            onSelect = onAutoRefreshInterval,
            onDismiss = { showIntervalDialog = false },
        )
    }
}
