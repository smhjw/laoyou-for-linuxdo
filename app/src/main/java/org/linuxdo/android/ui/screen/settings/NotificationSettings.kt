package org.linuxdo.android.ui.screen.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.linuxdo.android.data.NotificationScope
import org.linuxdo.android.data.SessionStore
import org.linuxdo.android.service.NotificationHelper
import org.linuxdo.android.service.NotificationKeepAliveService
import org.linuxdo.android.ui.design.AppTheme

/**
 * 角标实测下发后,桌面保持人造数字的时长。
 *
 * 未读每 10 秒轮询一次,不冻结的话实测数字按不到 Home 键就被真实未读覆盖了。
 */
private const val BADGE_TEST_HOLD_MILLIS = 3 * 60 * 1000L

@Composable
internal fun NotificationSettings(
    backgroundNotification: Boolean,
    onBackgroundNotification: (Boolean) -> Unit,
    notificationScope: NotificationScope,
    onNotificationScope: (NotificationScope) -> Unit,
) {
    val context = LocalContext.current
    var showScopeDialog by remember { mutableStateOf(false) }
    var showTestLab by remember { mutableStateOf(false) }

    // 从系统设置页返回时刷新电池优化与通知权限状态。
    var resumeTick by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) resumeTick++ }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val batteryOptimizationIgnored = remember(resumeTick) { NotificationHelper.isIgnoringBatteryOptimizations(context) }
    val systemNotificationsEnabled = remember(resumeTick) { NotificationManagerCompat.from(context).areNotificationsEnabled() }

    // Android 13+ 的 POST_NOTIFICATIONS 是运行时权限,默认拒绝。
    // 没授权时 notify() 会被系统静默丢弃 —— 用户只会觉得"推送不工作",
    // 所以必须在用户开启通知相关开关的那一刻顺手要权限。
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        resumeTick++ // 拒绝也不阻断设置本身,用户仍可在系统设置里补开
    }
    fun ensureNotificationPermission() {
        val needsRuntime = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        when {
            needsRuntime -> permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            !NotificationManagerCompat.from(context).areNotificationsEnabled() -> NotificationHelper.openNotificationSettings(context)
        }
    }

    SettingsGroup(title = "通知") {
        SettingsSwitchItem(
            title = "允许后台通知",
            icon = Icons.Outlined.NotificationsActive,
            summary = "亮屏实时推送，息屏智能切换系统低功耗唤醒",
            checked = backgroundNotification,
            onCheckedChange = { next ->
                if (next) {
                    ensureNotificationPermission()
                    NotificationHelper.promptIgnoreBatteryOptimizationsIfNeeded(context)
                }
                onBackgroundNotification(next)
            },
        )
        if (backgroundNotification) {
            if (!systemNotificationsEnabled) {
                SettingsItem(
                    title = "系统通知权限未开启",
                    icon = Icons.Outlined.NotificationsOff,
                    summary = "系统屏蔽了此应用的通知，点击前往开启通知与横幅提醒",
                    titleColor = MaterialTheme.colorScheme.error,
                    iconTint = MaterialTheme.colorScheme.error,
                    onClick = { NotificationHelper.openNotificationSettings(context) },
                )
            } else {
                SettingsItem(
                    title = "系统通知与横幅设置",
                    icon = Icons.Outlined.Tune,
                    summary = "管理横幅浮窗、锁屏显示与铃声震动",
                    onClick = { NotificationHelper.openNotificationSettings(context) },
                )
            }
            SettingsItem(
                title = "通知推送范围",
                icon = Icons.Outlined.NotificationsActive,
                summary = notificationScope.description,
                value = notificationScope.label,
                onClick = { showScopeDialog = true },
            )
            SettingsItem(
                title = "测试通知推送",
                icon = Icons.Outlined.Science,
                summary = "验证息屏浅睡、Doze 唤醒、亮屏抢跑、单帖正文拉取与桌面角标",
                onClick = {
                    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                        Toast.makeText(context, "系统通知权限尚未开启，请在设置中允许通知", Toast.LENGTH_LONG).show()
                        NotificationHelper.openNotificationSettings(context)
                    } else {
                        showTestLab = true
                    }
                },
            )
        }
    }

    if (backgroundNotification) {
        SettingsGroup(title = "休眠推送保活（息屏接收建议开启）") {
            SettingsItem(
                title = "电池优化设为不允许",
                icon = Icons.Outlined.BatteryChargingFull,
                summary = if (batteryOptimizationIgnored) "已加入白名单，息屏不会被系统冻结" else "设为“不允许 / 无限制”，防止息屏被系统挂起",
                value = if (batteryOptimizationIgnored) null else "去设置",
                onClick = { NotificationHelper.requestIgnoreBatteryOptimizations(context) },
                trailing = if (batteryOptimizationIgnored) {
                    { Text("已允许", style = MaterialTheme.typography.labelLarge, color = AppTheme.colors.success) }
                } else null,
            )
            SettingsItem(
                title = "应用启动管理",
                icon = Icons.Outlined.RocketLaunch,
                summary = "设为手动管理：允许自启动与后台活动",
                onClick = {
                    Toast.makeText(context, "请在系统中将本应用设为“手动管理”，允许自启动与后台活动", Toast.LENGTH_LONG).show()
                    NotificationHelper.openAutoStartSettings(context)
                },
            )
            SettingsItem(
                title = "休眠时保持网络连接",
                icon = Icons.Outlined.Wifi,
                summary = "在系统电池高级设置中开启，避免息屏断网",
                onClick = {
                    Toast.makeText(context, "请检查是否开启“休眠时始终保持网络连接”，避免息屏断网", Toast.LENGTH_LONG).show()
                    NotificationHelper.openPowerSettings(context)
                },
            )
        }
        SettingsNote("若只需亮屏使用时接收通知，以上三项可保持系统默认以省电；若需息屏深睡下稳定接收，建议全部配置并在多任务后台锁定本应用。")
    }

    if (showScopeDialog) {
        SingleChoiceDialog(
            title = "通知推送范围",
            description = "选择推送到系统状态栏的消息类型",
            options = NotificationScope.entries,
            selected = notificationScope,
            label = { it.label },
            supporting = { it.description },
            onSelect = onNotificationScope,
            onDismiss = { showScopeDialog = false },
        )
    }
    if (showTestLab) PushTestLabDialog(context) { showTestLab = false }
}

/**
 * 推送与角标全场景实测。直接重放账号最近一条真实通知,走完整后台拉取 + 单帖正文补全 + 角标联动。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PushTestLabDialog(context: Context, onDismiss: () -> Unit) {
    fun scenario(start: (Context) -> Unit, hint: String) {
        onDismiss()
        start(context)
        Toast.makeText(context, hint, Toast.LENGTH_LONG).show()
    }

    // 角标操作只驱动角标真相源 lastBadgeCount 与厂商桌面接口,**不写模拟未读数** ——
    // 否则人造数字会被 refreshUnread 当成真实未读累加进铃铛,且只能一条条点掉。
    fun badge(count: (SessionStore) -> Int, source: String, notify: String?, hold: Boolean = true) {
        val store = SessionStore(context)
        store.setTestSimulatedUnreadCount(0)
        val next = count(store)
        store.setLastBadgeCount(next, force = true)
        store.setBadgeTestHoldUntil(if (hold) System.currentTimeMillis() + BADGE_TEST_HOLD_MILLIS else 0L)
        notify?.let {
            NotificationHelper.showNotification(
                context = context,
                notificationId = System.currentTimeMillis() % 1_000_000L,
                title = "[角标实测] $it",
                content = "已请求将角标更新为 $next，请返回桌面确认；诊断详情见开发者选项日志",
                badgeCount = next,
            )
        }
        NotificationHelper.updateBadgeCount(context, next, source = source, force = true)
        NotificationHelper.recordDiagnosticSnapshot(context)
        Toast.makeText(context, if (hold) "已请求角标 $next，3 分钟内桌面保持该数字" else "已清除角标与实测锁定", Toast.LENGTH_SHORT).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Science, contentDescription = null) },
        title = { Text("推送与角标实测") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                listOf(
                    Triple("息屏浅睡（20 秒后触发）", "点击后立即锁屏，验证浅睡期后台拉取与锁屏弹窗") {
                        scenario({ NotificationKeepAliveService.startShallowSleepTest(it) }, "已启动！请立即锁屏，20 秒后将拉取真实通知并弹窗")
                    },
                    Triple("Doze 闹钟唤醒（60 秒后）", "断开长连接模拟深睡，由 AlarmManager 硬件唤醒拉取") {
                        scenario({ NotificationKeepAliveService.startDozeAlarmTest(it) }, "已模拟深睡！请立即锁屏等待 60 秒")
                    },
                    Triple("亮屏抢跑", "锁屏 5 秒以上后点亮屏幕（无需解锁），验证亮屏秒弹") {
                        scenario({ NotificationKeepAliveService.startScreenOnRushTest(it) }, "已就绪！锁屏 5 秒以上再按电源键点亮屏幕")
                    },
                    Triple("退回桌面（15 秒后）", "按 Home 键退回桌面并保持亮屏，验证后台轮询与弹窗") {
                        scenario({ NotificationKeepAliveService.startBackgroundMinimizeTest(it) }, "已启动！请立即按 Home 键，15 秒后弹窗")
                    },
                ).forEach { (title, summary, action) ->
                    ListItem(
                        headlineContent = { Text(title) },
                        supportingContent = { Text(summary) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable(onClick = action),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text("桌面角标", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        badge({ it.incrementLastBadgeCount().coerceAtLeast(1) }, "实测+1", "桌面未读角标 +1")
                    }) { Text("角标 +1") }
                    OutlinedButton(onClick = {
                        badge({ (it.getLastBadgeCount() - 1).coerceAtLeast(0) }, "实测已读-1", null)
                    }) { Text("已读 -1") }
                    OutlinedButton(onClick = { badge({ 99 }, "实测强设99", "桌面角标强制设为 99") }) { Text("设为 99") }
                    OutlinedButton(onClick = { badge({ 0 }, "实测归零", null, hold = false) }) { Text("角标归零") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
