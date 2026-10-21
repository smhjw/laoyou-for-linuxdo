package org.linuxdo.android.ui.screen.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import org.linuxdo.android.BuildConfig
import org.linuxdo.android.data.AutoRefreshInterval
import org.linuxdo.android.data.FloatingAction
import org.linuxdo.android.data.NotificationPollInterval
import org.linuxdo.android.data.NotificationScope
import org.linuxdo.android.net.DohPresets
import org.linuxdo.android.net.DohServer
import org.linuxdo.android.ui.ThemeMode
import org.linuxdo.android.ui.UpdateUiState
import org.linuxdo.android.ui.design.AppScaffold
import org.linuxdo.android.ui.design.LoadingIndicator
import org.linuxdo.android.ui.nav.SettingsPage
import org.linuxdo.android.ui.screen.ConfirmDialog

/**
 * 设置。首页只列品目入口,具体设置项点进子页再展开;版本信息与退出账号在首页末尾。
 *
 * 各子页的内容在同目录的 *Settings.kt 里,这里负责页面骨架、分发和跨子页的对话框。
 */
@Composable
fun SettingsScreen(
    treeView: Boolean,
    onTreeView: (Boolean) -> Unit,
    onBack: () -> Unit,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    page: SettingsPage? = null,
    onOpenPage: (SettingsPage) -> Unit = {},
    dynamicColor: Boolean = true,
    onDynamicColor: (Boolean) -> Unit = {},
    floatingBallEnabled: Boolean = true,
    onFloatingBallEnabled: (Boolean) -> Unit = {},
    floatingBallActions: List<FloatingAction> = FloatingAction.DEFAULT_ACTIONS,
    onFloatingBallActions: (List<FloatingAction>) -> Unit = {},
    updateState: UpdateUiState = UpdateUiState(),
    onCheckUpdate: () -> Unit = {},
    syncReadProgress: Boolean = true,
    onSyncReadProgress: (Boolean) -> Unit = {},
    onDiagnostics: () -> Unit = {},
    readingStatus: String? = null,
    canRetryReading: Boolean = false,
    onRetryReading: () -> Unit = {},
    onLogout: () -> Unit = {},
    autoRefreshIncoming: Boolean = true,
    onAutoRefreshIncoming: (Boolean) -> Unit = {},
    autoRefreshInterval: AutoRefreshInterval = AutoRefreshInterval.REALTIME,
    onAutoRefreshInterval: (AutoRefreshInterval) -> Unit = {},
    backgroundNotification: Boolean = false,
    onBackgroundNotification: (Boolean) -> Unit = {},
    notificationPollInterval: NotificationPollInterval = NotificationPollInterval.OFF,
    onNotificationPollInterval: (NotificationPollInterval) -> Unit = {},
    customRealtimeSeconds: Int = 15,
    onCustomRealtimeSeconds: (Int) -> Unit = {},
    notificationScope: NotificationScope = NotificationScope.INTERACTION_ONLY,
    onNotificationScope: (NotificationScope) -> Unit = {},
    keepAliveService: Boolean = false,
    onKeepAliveService: (Boolean) -> Unit = {},
    onTestNotification: () -> Unit = {},
    // ── DoH 安全直连 ────────────────────────────────────────────────────────────
    dohEnabled: Boolean = false,
    onDohEnabled: (Boolean) -> Unit = {},
    dohServers: List<DohServer> = DohPresets.ALL,
    selectedDohServerUrl: String = DohPresets.DEFAULT.url,
    onDohServerSelected: (DohServer) -> Unit = {},
    dohAutoFailover: Boolean = true,
    onDohAutoFailover: (Boolean) -> Unit = {},
    dohAutoOptimize: Boolean = true,
    onDohAutoOptimize: (Boolean) -> Unit = {},
    dohLatencies: Map<String, Long?> = emptyMap(),
    dohSummaries: Map<String, String> = emptyMap(),
    isTestingAllDoh: Boolean = false,
    onTestDohServer: (DohServer) -> Unit = {},
    onTestAllDohServers: () -> Unit = {},
    onAddCustomDohServer: (url: String, name: String) -> Unit = { _, _ -> },
    onUpdateDohServer: (oldServer: DohServer, url: String, name: String) -> Unit = { _, _, _ -> },
    onDeleteDohServer: (DohServer) -> Unit = {},
    onResetDohServers: () -> Unit = {},
) {
    AppScaffold(
        title = page?.title ?: "设置",
        onBack = onBack,
        showLargeTitle = true,
    ) {
        item(key = "settings-${page?.name ?: "home"}") {
            when (page) {
                null -> SettingsHome(
                    themeMode = themeMode,
                    floatingBallEnabled = floatingBallEnabled,
                    backgroundNotification = backgroundNotification,
                    dohEnabled = dohEnabled,
                    updateState = updateState,
                    onOpenPage = onOpenPage,
                    onCheckUpdate = onCheckUpdate,
                    onDiagnostics = onDiagnostics,
                    onLogout = onLogout,
                )
                SettingsPage.Appearance -> AppearanceSettings(
                    themeMode = themeMode,
                    onThemeMode = onThemeMode,
                    dynamicColor = dynamicColor,
                    onDynamicColor = onDynamicColor,
                )
                SettingsPage.Reading -> ReadingSettings(
                    treeView = treeView,
                    onTreeView = onTreeView,
                    syncReadProgress = syncReadProgress,
                    onSyncReadProgress = onSyncReadProgress,
                    autoRefreshInterval = autoRefreshInterval,
                    onAutoRefreshInterval = onAutoRefreshInterval,
                    readingStatus = readingStatus,
                    canRetryReading = canRetryReading,
                    onRetryReading = onRetryReading,
                )
                SettingsPage.FloatingBall -> FloatingBallSettings(
                    enabled = floatingBallEnabled,
                    onEnabled = onFloatingBallEnabled,
                    actions = floatingBallActions,
                    onActions = onFloatingBallActions,
                )
                SettingsPage.Notifications -> NotificationSettings(
                    backgroundNotification = backgroundNotification,
                    onBackgroundNotification = onBackgroundNotification,
                    notificationScope = notificationScope,
                    onNotificationScope = onNotificationScope,
                )
                SettingsPage.Network -> NetworkSettings(
                    dohEnabled = dohEnabled,
                    onDohEnabled = onDohEnabled,
                    servers = dohServers,
                    selectedServerUrl = selectedDohServerUrl,
                    onServerSelected = onDohServerSelected,
                    autoFailover = dohAutoFailover,
                    onAutoFailover = onDohAutoFailover,
                    autoOptimize = dohAutoOptimize,
                    onAutoOptimize = onDohAutoOptimize,
                    latencies = dohLatencies,
                    testingAll = isTestingAllDoh,
                    onTestAll = onTestAllDohServers,
                    onAddServer = onAddCustomDohServer,
                    onUpdateServer = onUpdateDohServer,
                    onDeleteServer = onDeleteDohServer,
                    onResetServers = onResetDohServers,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SettingsHome(
    themeMode: ThemeMode,
    floatingBallEnabled: Boolean,
    backgroundNotification: Boolean,
    dohEnabled: Boolean,
    updateState: UpdateUiState,
    onOpenPage: (SettingsPage) -> Unit,
    onCheckUpdate: () -> Unit,
    onDiagnostics: () -> Unit,
    onLogout: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("display_preferences", Context.MODE_PRIVATE) }
    var devOptionsUnlocked by remember {
        mutableStateOf(BuildConfig.DEBUG || prefs.getBoolean("dev_options_enabled", false))
    }
    var devClickCount by remember { mutableIntStateOf(0) }
    var lastDevClickTime by remember { mutableLongStateOf(0L) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    // 正式版连续点 7 次版本号切换开发者选项,和系统「关于手机」的手势一致。
    fun onVersionClick() {
        onCheckUpdate()
        if (BuildConfig.DEBUG) return
        val now = System.currentTimeMillis()
        devClickCount = if (now - lastDevClickTime > 2000L) 1 else devClickCount + 1
        lastDevClickTime = now
        if (devClickCount >= 7) {
            devOptionsUnlocked = !devOptionsUnlocked
            prefs.edit { putBoolean("dev_options_enabled", devOptionsUnlocked) }
            Toast.makeText(context, if (devOptionsUnlocked) "开发者选项已开启" else "开发者选项已隐藏", Toast.LENGTH_SHORT).show()
            devClickCount = 0
        } else if (devClickCount >= 4) {
            Toast.makeText(context, "再点击 ${7 - devClickCount} 次开启开发者选项", Toast.LENGTH_SHORT).show()
        }
    }

    SettingsGroup {
        SettingsItem(
            title = SettingsPage.Appearance.title,
            icon = Icons.Outlined.Palette,
            summary = themeMode.label,
            onClick = { onOpenPage(SettingsPage.Appearance) },
        )
        SettingsItem(
            title = SettingsPage.Reading.title,
            icon = Icons.AutoMirrored.Outlined.MenuBook,
            summary = "树形楼层、阅读进度、新话题感知",
            onClick = { onOpenPage(SettingsPage.Reading) },
        )
        SettingsItem(
            title = SettingsPage.FloatingBall.title,
            icon = Icons.Outlined.TouchApp,
            summary = if (floatingBallEnabled) "已开启" else "已关闭",
            onClick = { onOpenPage(SettingsPage.FloatingBall) },
        )
    }
    SettingsGroup {
        SettingsItem(
            title = SettingsPage.Notifications.title,
            icon = Icons.Outlined.Notifications,
            summary = if (backgroundNotification) "后台通知已开启" else "后台通知已关闭",
            onClick = { onOpenPage(SettingsPage.Notifications) },
        )
        SettingsItem(
            title = SettingsPage.Network.title,
            icon = Icons.Outlined.Security,
            summary = if (dohEnabled) "DoH 安全直连已开启" else "DoH 安全直连已关闭",
            onClick = { onOpenPage(SettingsPage.Network) },
        )
    }
    SettingsGroup {
        SettingsItem(
            title = "版本信息",
            icon = Icons.Outlined.Info,
            summary = when {
                updateState.manualChecking -> "正在检查更新…"
                updateState.updateInfo != null -> "发现新版本 v${updateState.updateInfo.latestVersion}"
                else -> "已是最新版本"
            },
            onClick = ::onVersionClick,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (updateState.manualChecking) {
                        LoadingIndicator(diameter = 16.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Box {
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(
                                "v${updateState.currentVersion}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                        if (updateState.updateInfo != null) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 3.dp, y = (-3).dp)
                                    .size(8.dp)
                                    .background(MaterialTheme.colorScheme.error, CircleShape),
                            )
                        }
                    }
                }
            },
        )
        if (devOptionsUnlocked) {
            SettingsItem(
                title = "开发者选项与诊断",
                icon = Icons.Outlined.Terminal,
                onClick = onDiagnostics,
            )
        }
    }
    SettingsGroup {
        SettingsItem(
            title = "退出当前账号",
            icon = Icons.AutoMirrored.Outlined.Logout,
            titleColor = MaterialTheme.colorScheme.error,
            iconTint = MaterialTheme.colorScheme.error,
            onClick = { showLogoutConfirm = true },
        )
    }

    if (showLogoutConfirm) {
        ConfirmDialog(
            title = "退出当前账号",
            text = "确认要退出当前账号吗？退出后需要重新登录。",
            icon = Icons.AutoMirrored.Outlined.Logout,
            confirmText = "退出",
            destructive = true,
            onDismiss = { showLogoutConfirm = false },
            onConfirm = {
                showLogoutConfirm = false
                onLogout()
            },
        )
    }
}
