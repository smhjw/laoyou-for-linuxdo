package org.linuxdo.android.ui.screen.settings

import org.linuxdo.android.ui.design.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.linuxdo.android.BuildConfig
import org.linuxdo.android.service.NotificationHelper
import org.linuxdo.android.data.SessionStore
import org.linuxdo.android.data.AutoRefreshInterval
import org.linuxdo.android.data.NotificationPollInterval
import org.linuxdo.android.data.NotificationScope
import org.linuxdo.android.net.DohServer
import org.linuxdo.android.net.DohPresets
import org.linuxdo.android.ui.design.ListDivider
import org.linuxdo.android.ui.design.SectionCard
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import org.linuxdo.android.ui.design.LoadingIndicator
import org.linuxdo.android.ui.design.AppScaffold
import org.linuxdo.android.ui.design.Dimens
import org.linuxdo.android.ui.design.ListRow
import org.linuxdo.android.ui.nav.SettingsPage
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import android.widget.Toast
import java.util.Locale
import androidx.compose.ui.graphics.SolidColor
import android.graphics.Canvas
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import org.linuxdo.android.data.FloatingAction
import org.linuxdo.android.ui.*
import org.linuxdo.android.ui.screen.SectionTitle

/**
 * 角标实测下发后,桌面保持人造数字的时长。
 *
 * 未读每 10 秒轮询一次,不冻结的话实测数字按不到 Home 键就被真实未读覆盖了。
 */
private const val BADGE_TEST_HOLD_MILLIS = 3 * 60 * 1000L

@Composable
fun SettingsScreen(
    treeView: Boolean,
    onTreeView: (Boolean) -> Unit,
    onBack: () -> Unit,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    page: SettingsPage? = null,
    onOpenPage: (SettingsPage) -> Unit = {},
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
    // ── DoH 安全直连参数 ────────────────────────────────────────────────────────
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
    val context = LocalContext.current
    var showSyncConfirmDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }
    var showAutoRefreshIntervalDialog by remember { mutableStateOf(false) }
    var showNotificationScopeDialog by remember { mutableStateOf(false) }
    var showTestPushLabDialog by remember { mutableStateOf(false) }

    val prefs = remember { context.getSharedPreferences("display_preferences", Context.MODE_PRIVATE) }
    var devOptionsUnlocked by remember {
        mutableStateOf(BuildConfig.DEBUG || prefs.getBoolean("dev_options_enabled", false))
    }
    var devClickCount by remember { mutableIntStateOf(0) }
    var lastDevClickTime by remember { mutableStateOf(0L) }

    var isBatteryOptimizedIgnored by remember {
        mutableStateOf(NotificationHelper.isIgnoringBatteryOptimizations(context))
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isBatteryOptimizedIgnored = NotificationHelper.isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Android 13+ 的 POST_NOTIFICATIONS 是运行时权限，默认拒绝。
    // 没授权时 NotificationManager.notify() 会被系统静默丢弃 —— 用户只会觉得"推送不工作"，
    // 不会收到任何提示，所以必须在用户开启通知相关开关的那一刻顺手要权限。
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* 拒绝也不阻断设置本身，用户仍可在系统设置里补开 */ }

    fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (!androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationHelper.openNotificationSettings(context)
        }
    }

    // 动态计算本地缓存大小
    fun calcCacheSize(): String {
        return runCatching {
            var totalBytes = 0L
            listOfNotNull(context.cacheDir, context.codeCacheDir, context.externalCacheDir).forEach { dir ->
                if (dir.exists()) {
                    dir.walkTopDown().filter { it.isFile }.forEach { file ->
                        totalBytes += file.length()
                    }
                }
            }
            val mb = totalBytes / (1024.0 * 1024.0)
            if (mb < 0.1) "0.0 MB" else String.format(Locale.US, "%.1f MB", mb)
        }.getOrDefault("0.0 MB")
    }

    val handleSyncToggle: (Boolean) -> Unit = { targetState ->
        if (targetState) {
            showSyncConfirmDialog = true
        } else {
            onSyncReadProgress(false)
        }
    }

    AppScaffold(
        title = page?.title ?: "设置",
        onBack = onBack,
        showLargeTitle = page != null,
    ) {
        // ── 首页只列品目入口，具体设置项点进子页再展开；版本信息与退出账号在列表末尾 ──
        if (page == null) item("entries-general") {
            Spacer(Modifier.height(20.dp))
            SectionCard {
                SettingsEntryRow(
                    title = SettingsPage.Appearance.title,
                    badgeColor = Color(0xFFEEEDFC),
                    value = themeMode.label,
                    onClick = { onOpenPage(SettingsPage.Appearance) },
                ) { SettingAppearanceIcon(tint = Color(0xFF5856D6)) }
                ListDivider()
                SettingsEntryRow(
                    title = SettingsPage.Reading.title,
                    badgeColor = Color(0xFFFFF4E5),
                    onClick = { onOpenPage(SettingsPage.Reading) },
                ) { SettingBookIcon(tint = AppTheme.colors.warning) }
                ListDivider()
                SettingsEntryRow(
                    title = SettingsPage.FloatingBall.title,
                    badgeColor = Color(0xFFF3E5F5),
                    value = if (floatingBallEnabled) "已开启" else "已关闭",
                    onClick = { onOpenPage(SettingsPage.FloatingBall) },
                ) { SettingFloatingBallIcon(tint = Color(0xFFAB47BC)) }
            }
        }

        if (page == null) item("entries-system") {
            Spacer(Modifier.height(20.dp))
            SectionCard {
                SettingsEntryRow(
                    title = SettingsPage.Notifications.title,
                    badgeColor = Color(0xFFFFF0F5),
                    value = if (backgroundNotification) "已开启" else "已关闭",
                    onClick = { onOpenPage(SettingsPage.Notifications) },
                ) { SettingBellIcon(tint = Color(0xFFFF2D55)) }
                ListDivider()
                SettingsEntryRow(
                    title = SettingsPage.Network.title,
                    badgeColor = Color(0xFFE3F5E8),
                    value = if (dohEnabled) "DoH 已开启" else "DoH 已关闭",
                    onClick = { onOpenPage(SettingsPage.Network) },
                ) { SettingDohIcon(tint = AppTheme.colors.success) }
            }
        }

        // ── 1. 外观 ──
        if (page == SettingsPage.Appearance) item("group-appearance") {
            SectionTitle("主题")
            SectionCard {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    if (index > 0) ListDivider()
                    ListRow(onClick = { onThemeMode(mode) }) {
                        Text(mode.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (themeMode == mode) {
                            Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // ── 2. 阅读与浏览 ──
        if (page == SettingsPage.Reading) item("group-reading") {
            SectionTitle("阅读偏好")
            SectionCard {
                // 2.1 树形查看话题
                ListRow(onClick = { onTreeView(!treeView) }) {
                    SettingIconBadge(bgColor = Color(0xFFE8F3FF)) {
                        SettingTreeIcon(tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("树形查看话题", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "按回复树状排列，关闭后恢复顺序楼层",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    Switch(checked = treeView, onCheckedChange = onTreeView)
                }

                ListDivider()

                // 2.2 同步楼层与阅读时长
                ListRow(onClick = { handleSyncToggle(!syncReadProgress) }) {
                    SettingIconBadge(bgColor = Color(0xFFF0EDFF)) {
                        SettingClockIcon(tint = Color(0xFF6C5CE7))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("同步楼层与阅读时长", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "在后台实时记录阅读位置与社区足迹",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    Switch(checked = syncReadProgress, onCheckedChange = handleSyncToggle)
                }

                ListDivider()

                // 2.3 自动感知新话题频率
                ListRow(onClick = { showAutoRefreshIntervalDialog = true }) {
                    SettingIconBadge(bgColor = Color(0xFFE5F9E7)) {
                        SettingBroomIcon(tint = AppTheme.colors.success)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("自动感知新话题", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "停留在列表时静默探测并提示新话题",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    Text(
                        "${autoRefreshInterval.label} ›",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (autoRefreshInterval != AutoRefreshInterval.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (syncReadProgress && readingStatus != null) {
                    ListDivider()
                    ListRow(onClick = { if (canRetryReading) onRetryReading() }) {
                        Text(readingStatus, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        if (canRetryReading) Text("继续", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // ── 2.5 辅助悬浮按钮设置 ──
        if (page == SettingsPage.FloatingBall) item("group-floating-ball") {
            SectionTitle("辅助功能")
            SectionCard {
                ListRow(onClick = { onFloatingBallEnabled(!floatingBallEnabled) }) {
                    SettingIconBadge(bgColor = Color(0xFFF3E5F5)) {
                        SettingFloatingBallIcon(tint = Color(0xFFAB47BC))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("开启辅助悬浮球", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "单功能直接点击直达，多功能伞型展开；可拖动吸附贴边并记录坐标",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    Switch(
                        checked = floatingBallEnabled,
                        onCheckedChange = onFloatingBallEnabled,
                    )
                }

                if (floatingBallEnabled) {
                    ListDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "功能配置与排序 (${floatingBallActions.size}/5)",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        if (floatingBallActions != FloatingAction.DEFAULT_ACTIONS) {
                            Text(
                                "恢复默认",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { onFloatingBallActions(FloatingAction.DEFAULT_ACTIONS) },
                            )
                        }
                    }

                    val allOptions = remember(floatingBallActions) {
                        val enabled = floatingBallActions
                        val disabled = FloatingAction.entries.filter { it !in enabled }
                        enabled + disabled
                    }

                    allOptions.forEachIndexed { index, action ->
                        val isActionEnabled = action in floatingBallActions
                        val isFirstInEnabled = isActionEnabled && index == 0
                        val isLastInEnabled = isActionEnabled && index == floatingBallActions.lastIndex

                        ListDivider(startIndent = 16.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (action) {
                                            FloatingAction.CreateTopic -> Color(0xFFFF5722).copy(alpha = 0.15f)
                                            FloatingAction.Sort -> Color(0xFFFFB300).copy(alpha = 0.15f)
                                            FloatingAction.Drafts -> Color(0xFFAB47BC).copy(alpha = 0.15f)
                                            FloatingAction.Search -> Color(0xFF26A69A).copy(alpha = 0.15f)
                                            FloatingAction.Categories -> Color(0xFF29B6F6).copy(alpha = 0.15f)
                                        }
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                val actionTint = when (action) {
                                    FloatingAction.CreateTopic -> Color(0xFFFF5722)
                                    FloatingAction.Sort -> Color(0xFFFF9800)
                                    FloatingAction.Drafts -> Color(0xFF8E24AA)
                                    FloatingAction.Search -> Color(0xFF00897B)
                                    FloatingAction.Categories -> Color(0xFF0288D1)
                                }
                                Canvas(Modifier.size(16.dp)) {
                                    when (action) {
                                        FloatingAction.CreateTopic -> {
                                            val p = Path().apply { moveTo(3f, 13f); lineTo(6f, 12f); lineTo(13f, 5f); lineTo(11f, 3f); lineTo(4f, 10f); close() }
                                            drawPath(p, actionTint, style = Stroke(1.8f))
                                        }
                                        FloatingAction.Sort -> {
                                            drawLine(actionTint, Offset(5f, 12f), Offset(5f, 4f), 1.8f)
                                            val a1 = Path().apply { moveTo(3f, 6.5f); lineTo(5f, 4f); lineTo(7f, 6.5f) }
                                            drawPath(a1, actionTint, style = Stroke(1.8f))
                                            drawLine(actionTint, Offset(11f, 4f), Offset(11f, 12f), 1.8f)
                                            val a2 = Path().apply { moveTo(9f, 9.5f); lineTo(11f, 12f); lineTo(13f, 9.5f) }
                                            drawPath(a2, actionTint, style = Stroke(1.8f))
                                        }
                                        FloatingAction.Drafts -> {
                                            val f = Path().apply { moveTo(2f, 4f); lineTo(6f, 4f); lineTo(8f, 6f); lineTo(14f, 6f); lineTo(14f, 13f); lineTo(2f, 13f); close() }
                                            drawPath(f, actionTint, style = Stroke(1.6f))
                                        }
                                        FloatingAction.Search -> {
                                            drawCircle(actionTint, 4f, Offset(7f, 7f), style = Stroke(1.8f))
                                            drawLine(actionTint, Offset(10f, 10f), Offset(13.5f, 13.5f), 2f)
                                        }
                                        FloatingAction.Categories -> {
                                            drawRoundRect(actionTint, Offset(3f, 3f), Size(4.2f, 4.2f), CornerRadius(1f, 1f), style = Stroke(1.6f))
                                            drawRoundRect(actionTint, Offset(9f, 3f), Size(4.2f, 4.2f), CornerRadius(1f, 1f), style = Stroke(1.6f))
                                            drawRoundRect(actionTint, Offset(3f, 9f), Size(4.2f, 4.2f), CornerRadius(1f, 1f), style = Stroke(1.6f))
                                            drawRoundRect(actionTint, Offset(9f, 9f), Size(4.2f, 4.2f), CornerRadius(1f, 1f), style = Stroke(1.6f))
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(action.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                                Text(action.description, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            if (isActionEnabled) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "↑",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (!isFirstInEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable(enabled = !isFirstInEnabled) {
                                                val list = floatingBallActions.toMutableList()
                                                val idx = list.indexOf(action)
                                                if (idx > 0) {
                                                    val temp = list[idx]
                                                    list[idx] = list[idx - 1]
                                                    list[idx - 1] = temp
                                                    onFloatingBallActions(list)
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                    )
                                    Text(
                                        "↓",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (!isLastInEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable(enabled = !isLastInEnabled) {
                                                val list = floatingBallActions.toMutableList()
                                                val idx = list.indexOf(action)
                                                if (idx in 0 until list.lastIndex) {
                                                    val temp = list[idx]
                                                    list[idx] = list[idx + 1]
                                                    list[idx + 1] = temp
                                                    onFloatingBallActions(list)
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                            }

                            Switch(
                                checked = isActionEnabled,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        if (action !in floatingBallActions) {
                                            onFloatingBallActions((floatingBallActions + action).take(5))
                                        }
                                    } else {
                                        if (floatingBallActions.size > 1) {
                                            onFloatingBallActions(floatingBallActions - action)
                                        } else {
                                            Toast.makeText(context, "悬浮球至少需要保留 1 项功能", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        // ── 3. 通知与后台保活 ──
        if (page == SettingsPage.Notifications) item("group-notifications") {
            SectionTitle("通知")
            SectionCard {
                // 3.0 后台通知总开关
                ListRow(onClick = {
                    val next = !backgroundNotification
                    if (next) {
                        ensureNotificationPermission()
                        NotificationHelper.promptIgnoreBatteryOptimizationsIfNeeded(context)
                    }
                    onBackgroundNotification(next)
                }) {
                    SettingIconBadge(bgColor = Color(0xFFFFF0F5)) {
                        SettingInfoIcon(tint = Color(0xFFFF2D55))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("允许后台通知", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "亮屏实时推送，息屏智能切换系统低功耗唤醒",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    Switch(
                        checked = backgroundNotification,
                        onCheckedChange = { next ->
                            if (next) {
                                ensureNotificationPermission()
                                NotificationHelper.promptIgnoreBatteryOptimizationsIfNeeded(context)
                            }
                            onBackgroundNotification(next)
                        },
                    )
                }

                if (backgroundNotification) {
                    ListDivider()

                    // 系统通知权限与横幅直达
                    val isSystemNotificationEnabled = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
                    if (!isSystemNotificationEnabled) {
                        ListRow(onClick = { NotificationHelper.openNotificationSettings(context) }) {
                            SettingIconBadge(bgColor = Color(0xFFFFEBEE)) {
                                SettingInfoIcon(tint = Color(0xFFD32F2F))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("系统通知权限未开启", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = Color(0xFFD32F2F))
                                Text(
                                    "手机系统屏蔽了此应用通知，点击前往开启通知总开关与横幅提醒",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                            Text("去开启 ›", style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD32F2F))
                        }
                    } else {
                        ListRow(onClick = { NotificationHelper.openNotificationSettings(context) }) {
                            SettingIconBadge(bgColor = Color(0xFFE8F5E9)) {
                                SettingTreeIcon(tint = Color(0xFF4CAF50))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("系统通知与横幅设置", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "管理顶部横幅浮窗、锁屏显示与铃声震动，也可关闭常驻静音图标",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 3.dp),
                                )
                            }
                            Text("已开启 ›", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }



                    ListDivider()

                    // 通知推送范围
                    ListRow(onClick = { showNotificationScopeDialog = true }) {
                        SettingIconBadge(bgColor = Color(0xFFE8EAF6)) {
                            SettingTreeIcon(tint = Color(0xFF3F51B5))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("通知推送范围", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                notificationScope.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                        Text(
                            "${notificationScope.label} ›",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    ListDivider()

                    // 测试通知推送（全场景实测实验室）
                    ListRow(onClick = {
                        if (!androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                            Toast.makeText(context, "系统通知权限尚未开启，请在设置中允许通知", Toast.LENGTH_LONG).show()
                            NotificationHelper.openNotificationSettings(context)
                        } else {
                            showTestPushLabDialog = true
                        }
                    }) {
                        SettingIconBadge(bgColor = Color(0xFFE1F5FE)) {
                            SettingInfoIcon(tint = Color(0xFF03A9F4))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("测试通知推送 (全场景实测)", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                "验证息屏浅睡、Doze 闹钟硬件唤醒、亮屏抢跑、单帖正文拉取与桌面角标增减",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                        Text("实测实验室 ›", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            if (backgroundNotification) {
                // ── 3.1 专门的休眠状态推送保活整体区块（带左上角小标识） ──
                Row(
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "休眠推送保活",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(AppTheme.colors.warning.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "休眠状态推送建议开启",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                            color = AppTheme.colors.warning,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                SectionCard {
                    // 1. 电池优化直达配置
                    ListRow(onClick = {
                        NotificationHelper.requestIgnoreBatteryOptimizations(context)
                    }) {
                        SettingIconBadge(bgColor = Color(0xFFFFF8E1)) {
                            SettingBatteryIcon(tint = Color(0xFFFFB300))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("电池优化 (设为不允许)", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (isBatteryOptimizedIgnored) "已加入电池优化白名单，息屏不被系统睡眠冻结" else "设为“不允许 / 无限制”，防止息屏被系统休眠挂起",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                        if (isBatteryOptimizedIgnored) {
                            Text("已允许 ✓", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.success, fontWeight = FontWeight.Medium)
                        } else {
                            Text("去设置 ›", style = MaterialTheme.typography.bodyMedium, color = AppTheme.colors.warning, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    ListDivider()

                    // 2. 应用启动管理直达配置
                    ListRow(onClick = {
                        Toast.makeText(context, "请在系统中将本应用设为“手动管理”，允许自启动与后台活动", Toast.LENGTH_LONG).show()
                        NotificationHelper.openAutoStartSettings(context)
                    }) {
                        SettingIconBadge(bgColor = Color(0xFFF3E5F5)) {
                            SettingRocketIcon(tint = Color(0xFF9C27B0))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("应用启动管理 (后台自启)", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                "设为手动管理：允许自启动与后台活动，防止灭屏被杀",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                        Text("去配置 ›", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }

                    ListDivider()

                    // 3. 休眠网络与电池设置
                    ListRow(onClick = {
                        Toast.makeText(context, "请检查是否开启“休眠时始终保持网络连接”，避免息屏断网", Toast.LENGTH_LONG).show()
                        NotificationHelper.openPowerSettings(context)
                    }) {
                        SettingIconBadge(bgColor = Color(0xFFE0F7FA)) {
                            SettingClockIcon(tint = Color(0xFF00ACC1))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("休眠保持网络连接", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                "在系统电池高级设置中开启“休眠时始终保持网络连接”，避免断网",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 3.dp),
                            )
                        }
                        Text("去检查 ›", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Text(
                    text = "💡 提示：若仅需亮屏使用时接收通知，上述三项可保持系统默认以最大化省电；若需手机息屏深睡下稳定接收，建议全部配置并在多任务后台锁定本应用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
        }

        // ── 4.5 DoH 安全直连 ──
        if (page == SettingsPage.Network) item("group-doh") {
            var showAddServerDialog by remember { mutableStateOf(false) }
            var editingServer by remember { mutableStateOf<DohServer?>(null) }
            var showResetConfirmDialog by remember { mutableStateOf(false) }

            val isVpnActive = remember(context) {
                runCatching {
                    val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
                    cm?.allNetworks?.any { network ->
                        cm.getNetworkCapabilities(network)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) == true
                    } ?: false
                }.getOrDefault(false)
            }

            SectionTitle("DoH 安全直连")
            SectionCard {
                // VPN 开启提示
                if (isVpnActive) {
                    ListRow(onClick = {}) {
                        SettingIconBadge(bgColor = Color(0xFFFFF3E0)) {
                            SettingInfoIcon(tint = AppTheme.colors.warning)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                "已检测到 VPN / 系统代理",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = AppTheme.colors.warning,
                            )
                            Text(
                                "系统代理已接管全局网络，建议保持系统代理分流；断开代理后可继续使用 DoH 直连",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                    ListDivider()
                }

                // 开关行
                ListRow(onClick = {}) {
                    SettingIconBadge(bgColor = Color(0xFFE3F5E8)) {
                        SettingDohIcon(tint = AppTheme.colors.success)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "DNS over HTTPS",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (dohEnabled) "已开启 · 仅解析 linux.do，其余直透系统 DNS" else "关闭 · 使用系统默认 DNS",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    Switch(checked = dohEnabled, onCheckedChange = onDohEnabled)
                }

                if (dohEnabled) {
                    ListDivider()

                    // 自动故障切换
                    ListRow(onClick = {}) {
                        Text(
                            "自动故障切换",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = dohAutoFailover, onCheckedChange = onDohAutoFailover)
                    }

                    ListDivider()

                    // 智能定时测速切优
                    ListRow(onClick = {}) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "智能定时测速切优",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                "每 10 分钟自动监测各节点延迟，智能切换至最快健康节点",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        Switch(checked = dohAutoOptimize, onCheckedChange = onDohAutoOptimize)
                    }

                    ListDivider()

                    // 节点列表标题与一键测速栏
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "节点列表",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Row(
                            modifier = Modifier
                                .clickable(onClick = onTestAllDohServers)
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isTestingAllDoh) {
                                LoadingIndicator(diameter = 14.dp)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                "⚡ 一键测速所有节点",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    ListDivider()

                    // 节点列表
                    dohServers.forEachIndexed { idx, server ->
                        if (idx > 0) ListDivider(startIndent = 42.dp)
                        val latencyMs = dohLatencies[server.id]
                        val isSelected = server.url == selectedDohServerUrl

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = { onDohServerSelected(server) })
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 选中对勾或空白占位
                            Box(
                                modifier = Modifier.width(26.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (isSelected) {
                                    Text(
                                        "✓",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = AppTheme.colors.success,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                    )
                                }
                            }

                            // 节点名称与 URL
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                            ) {
                                Text(
                                    server.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    server.url,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }

                            // 右侧：延迟胶囊 + 编辑按钮 + 删除按钮
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                // 延迟胶囊
                                if (latencyMs != null) {
                                    val (bgColor, textColor, text) = when {
                                        latencyMs < 0L -> Triple(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant, "超时")
                                        latencyMs < 100L -> Triple(Color(0x3330D158), Color(0xFF30D158), "${latencyMs}ms")
                                        else -> Triple(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurface, "${latencyMs}ms")
                                    }
                                    Box(
                                        modifier = Modifier
                                            .background(bgColor, RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = textColor,
                                        )
                                    }
                                } else if (isTestingAllDoh || dohLatencies.containsKey(server.id)) {
                                    LoadingIndicator(diameter = 14.dp)
                                }

                                // 编辑按钮（小齿轮）
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
                                        .clickable { editingServer = server },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    DohGearIcon(tint = MaterialTheme.colorScheme.primary)
                                }

                                // 删除按钮（小垃圾桶）
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
                                        .clickable { onDeleteDohServer(server) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    DohTrashIcon(tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }

                    ListDivider()

                    // 添加自定义节点与恢复默认
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "+ 添加自定义节点",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable { showAddServerDialog = true },
                        )
                        Text(
                            "恢复默认预设",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { showResetConfirmDialog = true },
                        )
                    }
                }
            }

            // 添加自定义节点对话框
            if (showAddServerDialog) {
                var newServerUrl by remember { mutableStateOf("") }
                var newServerName by remember { mutableStateOf("") }
                Dialog(onDismissRequest = { showAddServerDialog = false }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("添加自定义 DoH 节点", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("服务器 URL（必须以 https:// 开头）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        BasicTextField(
                            value = newServerUrl,
                            onValueChange = { newServerUrl = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                        )
                        Text("节点名称（可选）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        BasicTextField(
                            value = newServerName,
                            onValueChange = { newServerName = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            androidx.compose.material3.TextButton(
                                onClick = { showAddServerDialog = false },
                                modifier = Modifier.weight(1f),
                            ) { Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            androidx.compose.material3.Button(
                                onClick = {
                                    val url = newServerUrl.trim()
                                    if (url.startsWith("https://")) {
                                        onAddCustomDohServer(url, newServerName.trim().ifBlank { url })
                                        showAddServerDialog = false
                                    } else {
                                        Toast.makeText(context, "URL 必须以 https:// 开头", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("添加") }
                        }
                    }
                }
            }

            // 编辑节点对话框
            editingServer?.let { server ->
                var editUrl by remember(server.id) { mutableStateOf(server.url) }
                var editName by remember(server.id) { mutableStateOf(server.name) }
                Dialog(onDismissRequest = { editingServer = null }) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("编辑 DoH 节点", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("服务器 URL（必须以 https:// 开头）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        BasicTextField(
                            value = editUrl,
                            onValueChange = { editUrl = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                        )
                        Text("节点名称", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        BasicTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            androidx.compose.material3.TextButton(
                                onClick = { editingServer = null },
                                modifier = Modifier.weight(1f),
                            ) { Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            androidx.compose.material3.Button(
                                onClick = {
                                    val url = editUrl.trim()
                                    if (url.startsWith("https://")) {
                                        onUpdateDohServer(server, url, editName.trim().ifBlank { url })
                                        editingServer = null
                                    } else {
                                        Toast.makeText(context, "URL 必须以 https:// 开头", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("保存") }
                        }
                    }
                }
            }

            // 恢复默认预设确认对话框
            if (showResetConfirmDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showResetConfirmDialog = false },
                    title = { Text("恢复默认预设") },
                    text = { Text("确定要将 DoH 节点列表恢复为官方默认预设（始皇DoH、社区优选DoH）吗？此操作将清除自定义节点与修改。") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                onResetDohServers()
                                showResetConfirmDialog = false
                            }
                        ) {
                            Text("恢复默认", color = MaterialTheme.colorScheme.primary)
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(
                            onClick = { showResetConfirmDialog = false }
                        ) {
                            Text("取消")
                        }
                    },
                )
            }
        }

        // ── 4. 网络与缓存 ──
        if (page == SettingsPage.Network) item("group-network") {
            // 遍历缓存目录有 I/O 开销，放在卡片内计算，避免每个设置子页都扫一遍。
            var cacheSizeText by remember { mutableStateOf(calcCacheSize()) }
            SectionTitle("缓存")
            SectionCard {
                // 3.1 清理本地缓存
                ListRow(onClick = {
                    runCatching {
                        listOfNotNull(context.cacheDir, context.codeCacheDir, context.externalCacheDir).forEach { dir ->
                            dir.deleteRecursively()
                        }
                    }
                    cacheSizeText = calcCacheSize()
                    Toast.makeText(context, "已清理图片与网络临时缓存，账号与已读状态不受影响", Toast.LENGTH_SHORT).show()
                }) {
                    SettingIconBadge(bgColor = Color(0xFFFFF4E5)) {
                        SettingBroomIcon(tint = AppTheme.colors.warning)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "清理本地缓存",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "仅清理图片与网络临时资源，不影响账号和已读状态",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    Text(
                        "$cacheSizeText ›",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // ── 3. 关于与维护 ──
        if (page == null) item("group-about") {
            Spacer(Modifier.height(20.dp))
            SectionCard {
                // 3.1 版本信息与检查更新 (合并)
                ListRow(onClick = {
                    onCheckUpdate()
                    if (!BuildConfig.DEBUG) {
                        val now = System.currentTimeMillis()
                        if (now - lastDevClickTime > 2000L) {
                            devClickCount = 1
                        } else {
                            devClickCount++
                        }
                        lastDevClickTime = now
                        if (devClickCount >= 7) {
                            devOptionsUnlocked = !devOptionsUnlocked
                            prefs.edit().putBoolean("dev_options_enabled", devOptionsUnlocked).apply()
                            val msg = if (devOptionsUnlocked) "开发者选项已开启" else "开发者选项已隐藏"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            devClickCount = 0
                        } else if (devClickCount >= 4) {
                            val remaining = 7 - devClickCount
                            Toast.makeText(context, "再点击 $remaining 次开启开发者选项", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) {
                    SettingIconBadge(bgColor = Color(0xFFE8F3FF)) {
                        SettingInfoIcon(tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "版本信息",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    "v${updateState.currentVersion}",
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            if (updateState.updateInfo != null) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .align(Alignment.TopEnd)
                                        .offset(x = 3.dp, y = (-3).dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.error),
                                )
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        if (updateState.manualChecking) {
                            LoadingIndicator(diameter = 14.dp)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "检查中...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                if (updateState.updateInfo != null) "可更新" else "已是最新",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (updateState.updateInfo != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("›", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.titleLarge)
                    }
                }

                if (devOptionsUnlocked) {
                    ListDivider()

                    // 3.3 开发者选项与诊断
                    SettingsEntryRow(
                        title = "开发者选项与诊断",
                        badgeColor = Color(0xFFF0F0F2),
                        onClick = onDiagnostics,
                    ) { SettingTerminalIcon(tint = Color(0xFF3A3A3C)) }
                }
            }
        }

        // ── 4. 底部独立退出账号卡片 ──
        if (page == null) item("logout-card") {
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.screenPadding)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .clickable { showLogoutConfirmDialog = true }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    SettingLogoutIcon(tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "退出当前账号",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.height(36.dp))
        }
    }

    if (showSyncConfirmDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSyncConfirmDialog = false },
            title = { Text("开启阅读进度同步") },
            text = {
                Text(
                    "开启后将向服务端上报各楼层阅读进度与阅读时长（消除网页端未读小蓝点）。\n\n" +
                        "需要站点安全验证时会显示验证页，确认上报成功后回到当前话题。取消验证会暂停同步，可在设置中继续。\n\n" +
                        "话题访问记录不受此开关控制。"
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showSyncConfirmDialog = false
                        onSyncReadProgress(true)
                    }
                ) {
                    Text("确认开启", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showSyncConfirmDialog = false }
                ) {
                    Text("取消")
                }
            },
        )
    }

    if (showLogoutConfirmDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            title = { Text("退出当前账号") },
            text = { Text("确认要退出当前账号吗？退出后需要重新输入账号密码登录。") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showLogoutConfirmDialog = false
                        onLogout()
                    }
                ) {
                    Text("退出", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showLogoutConfirmDialog = false }
                ) {
                    Text("取消")
                }
            },
        )
    }

    if (showAutoRefreshIntervalDialog) {
        Dialog(onDismissRequest = { showAutoRefreshIntervalDialog = false }) {
            Column(
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 340.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("新话题感知频率", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "停留在话题列表时只读 GET /latest.json 探测是否有新话题并在顶部提示。频率越高越及时，可按需测试 Cloudflare 影响。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AutoRefreshInterval.entries.forEach { option ->
                        val isSelected = option == autoRefreshInterval
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable {
                                    onAutoRefreshInterval(option)
                                    showAutoRefreshIntervalDialog = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                option.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            if (isSelected) {
                                Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNotificationScopeDialog) {
        Dialog(onDismissRequest = { showNotificationScopeDialog = false }) {
            Column(
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 340.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("通知推送范围", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "选择推送到系统状态栏的消息类型：",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    NotificationScope.entries.forEach { option ->
                        val isSelected = option == notificationScope
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                                .clickable {
                                    onNotificationScope(option)
                                    showNotificationScopeDialog = false
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    option.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    option.description,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (isSelected) {
                                Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTestPushLabDialog) {
        Dialog(onDismissRequest = { showTestPushLabDialog = false }) {
            Column(
                modifier = Modifier
                    .widthIn(min = 290.dp, max = 350.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("🧪 推送与角标全场景实测", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "无需等待他人回复，直接重放账号最近一条真实通知，走完整后台网络拉取 + 单帖正文补全 + 桌面角标联动：",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // 场景 1：息屏浅睡实测 (20秒)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                        .clickable {
                            showTestPushLabDialog = false
                            org.linuxdo.android.service.NotificationKeepAliveService.startShallowSleepTest(context)
                            Toast.makeText(
                                context,
                                "⚡ 已启动！请立即按电源键锁屏，20秒后将拉取真实通知并弹窗 (角标+1)",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        "1. 息屏浅睡实测 (20秒后触发)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "点此按钮后请立即锁屏黑屏，验证浅睡期后台网络拉取、单帖正文展示与锁屏弹窗（角标 +1）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 场景 2：Doze 闹钟穿透唤醒实测 (60秒)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppTheme.colors.warning.copy(alpha = 0.12f))
                        .clickable {
                            showTestPushLabDialog = false
                            org.linuxdo.android.service.NotificationKeepAliveService.startDozeAlarmTest(context)
                            Toast.makeText(
                                context,
                                "⏰ 已切断长连接模拟深睡！请立即锁屏等待60秒，验证 Doze 闹钟穿透唤醒 (角标+1)",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        "2. Doze 闹钟穿透唤醒 (60秒后唤醒)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFE67E22),
                    )
                    Text(
                        "立即断开长连接模拟深睡，60秒后由 AlarmManager + BroadcastReceiver 硬件唤醒拉取（角标 +1）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 场景 3：亮屏抢跑实测
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppTheme.colors.success.copy(alpha = 0.12f))
                        .clickable {
                            showTestPushLabDialog = false
                            org.linuxdo.android.service.NotificationKeepAliveService.startScreenOnRushTest(context)
                            Toast.makeText(
                                context,
                                "📱 已就绪！请锁屏等待5秒以上，再按电源键点亮锁屏界面（无需进App）观察秒弹",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        "3. 亮屏抢跑实测 (锁屏后点亮即弹)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF28A745),
                    )
                    Text(
                        "锁屏黑屏 5 秒以上后按电源键点亮屏幕（无需解锁打开 App），验证亮屏广播秒级抢跑弹窗（角标 +1）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 场景 4：退回桌面后台实测 (15秒)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .clickable {
                            showTestPushLabDialog = false
                            org.linuxdo.android.service.NotificationKeepAliveService.startBackgroundMinimizeTest(context)
                            Toast.makeText(
                                context,
                                "📱 已启动！请立即按 Home 键退回桌面，15秒后将自动弹窗 (角标+1)",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        "4. 退回桌面后台实测 (15秒后弹窗)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "点此按钮后按 Home 键将 App 最小化退回桌面（屏幕保持亮着），验证最小化后台轮询与弹窗（角标 +1）",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 场景 4：桌面角标实测操作行。
                // 只驱动角标真相源 lastBadgeCount 与厂商桌面接口，**不写模拟未读数**——
                // 否则人造数字会被 refreshUnread 当成真实未读累加进铃铛，且只能一条条点掉。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .clickable {
                                val store = org.linuxdo.android.data.SessionStore(context)
                                store.setTestSimulatedUnreadCount(0)
                                val nextBadge = store.incrementLastBadgeCount().coerceAtLeast(1)
                                store.setBadgeTestHoldUntil(System.currentTimeMillis() + BADGE_TEST_HOLD_MILLIS)
                                NotificationHelper.showNotification(
                                    context = context,
                                    notificationId = System.currentTimeMillis() % 1_000_000L,
                                    title = "[角标实测] 桌面未读角标 +1",
                                    content = "已请求将角标更新为 $nextBadge，请返回桌面确认显示结果；诊断详情见开发者选项日志",
                                    badgeCount = nextBadge,
                                )
                                NotificationHelper.updateBadgeCount(context, nextBadge, source = "实测+1", force = true)
                                NotificationHelper.recordDiagnosticSnapshot(context)
                                Toast.makeText(context, "已请求角标 $nextBadge，3 分钟内桌面保持该数字；日志已记录", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("角标 +1", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable {
                                val store = org.linuxdo.android.data.SessionStore(context)
                                store.setTestSimulatedUnreadCount(0)
                                val nextBadge = (store.getLastBadgeCount() - 1).coerceAtLeast(0)
                                store.setLastBadgeCount(nextBadge, force = true)
                                store.setBadgeTestHoldUntil(System.currentTimeMillis() + BADGE_TEST_HOLD_MILLIS)
                                NotificationHelper.updateBadgeCount(context, nextBadge, source = "实测已读-1", force = true)
                                NotificationHelper.recordDiagnosticSnapshot(context)
                                Toast.makeText(context, "已请求更新角标为 $nextBadge，日志已记录", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("已读 -1", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFF2D55).copy(alpha = 0.12f))
                            .clickable {
                                val store = org.linuxdo.android.data.SessionStore(context)
                                store.setTestSimulatedUnreadCount(0)
                                store.setLastBadgeCount(99, force = true)
                                store.setBadgeTestHoldUntil(System.currentTimeMillis() + BADGE_TEST_HOLD_MILLIS)
                                NotificationHelper.showNotification(
                                    context = context,
                                    notificationId = System.currentTimeMillis() % 1_000_000L,
                                    title = "[角标实测] 桌面角标强制设为 99",
                                    content = "已向荣耀/华为桌面提交角标 99，请按 Home 键返回桌面确认；如未显示请检查荣耀系统【数字角标】设置",
                                    badgeCount = 99,
                                )
                                NotificationHelper.updateBadgeCount(context, 99, source = "实测强设99", force = true)
                                NotificationHelper.recordDiagnosticSnapshot(context)
                                Toast.makeText(context, "已向桌面提交角标 99，请按 Home 键查看；3 分钟后自动恢复真实未读", Toast.LENGTH_LONG).show()
                            }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("设为 99", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFFFF2D55))
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .clickable {
                                val store = org.linuxdo.android.data.SessionStore(context)
                                // 解除锁定并清掉模拟未读，让下一轮 refreshUnread 立刻校准回真实未读。
                                store.setBadgeTestHoldUntil(0L)
                                store.setTestSimulatedUnreadCount(0)
                                store.setLastBadgeCount(0, force = true)
                                NotificationHelper.updateBadgeCount(context, 0, source = "实测归零", force = true)
                                NotificationHelper.recordDiagnosticSnapshot(context)
                                Toast.makeText(context, "已清除角标与实测锁定，未读数将恢复为真实值", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("角标归零", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
