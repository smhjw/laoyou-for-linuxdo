package org.linuxdo.android.ui.screen

import org.linuxdo.android.ui.design.*
import androidx.compose.material3.MaterialTheme

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.DateFormat
import java.util.Date
import org.linuxdo.android.Config
import org.linuxdo.android.service.NotificationHelper
import org.linuxdo.android.net.NetworkPath
import org.linuxdo.android.ui.design.ListDivider
import org.linuxdo.android.ui.design.SectionCard
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import org.linuxdo.android.ui.design.AppScaffold
import org.linuxdo.android.ui.design.Dimens
import org.linuxdo.android.ui.design.ListRow
import androidx.compose.ui.text.font.FontWeight
import android.widget.Toast
import org.linuxdo.android.ui.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Troubleshoot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import org.linuxdo.android.ui.screen.settings.SettingsGroup
import org.linuxdo.android.ui.screen.settings.SettingsItem
import org.linuxdo.android.ui.screen.settings.SettingsRadioItem
import org.linuxdo.android.ui.screen.settings.SettingsSwitchItem

/**
 * 开发者选项:网络路径切换、会话诊断与运行日志。
 *
 * 这些是排查 Cloudflare 与会话保持问题的主要手段,所以界面改版时只换位置、不削功能。
 */
@Composable
fun DiagnosticsScreen(state: UiState, viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val stats by viewModel.diagnostics.stats.collectAsStateWithLifecycle()
    val lines by viewModel.diagnostics.lines.collectAsStateWithLifecycle()
    val savedAt = if (state.cookieSavedAt == 0L) "-" else DateFormat.getDateTimeInstance().format(Date(state.cookieSavedAt))
    var zeroDohPocReport by remember { mutableStateOf<String?>(null) }

    AppScaffold(title = "开发者选项", onBack = onBack) {
        item(key = "paths") {
            SettingsGroup(title = "网络路径") {
                NetworkPath.entries.forEach { path ->
                    SettingsRadioItem(title = path.label, selected = state.path == path, onClick = { viewModel.setPath(path) })
                }
            }
        }

        item(key = "zero-doh-lab") {
            SettingsGroup(title = "网络直连实验") {
                SettingsItem(
                    title = "零 DoH 直连可行性验证",
                    icon = Icons.Outlined.Science,
                    summary = "验证 Anycast 零 DNS 直连、ECH 存活性、WebView 代理导流与 ClientHello 分片",
                    onClick = { zeroDohPocReport = "未集成实验模块" },
                )
            }
        }

        item(key = "actions") {
            SettingsGroup(title = "操作") {
                SettingsSwitchItem(
                    title = "自动刷新（${Config.AUTO_REFRESH_INTERVAL_MS / 60_000} 分钟）",
                    icon = Icons.Outlined.Sync,
                    checked = state.autoRefresh,
                    onCheckedChange = { viewModel.toggleAutoRefresh() },
                )
                SettingsItem(title = "立即刷新列表", icon = Icons.Outlined.Refresh, onClick = viewModel::retryLatest)
                SettingsItem(
                    title = "记录推送与角标状态",
                    icon = Icons.Outlined.Troubleshoot,
                    onClick = {
                        NotificationHelper.recordDiagnosticSnapshot(context)
                        Toast.makeText(context, "推送与角标状态已记录到下方日志", Toast.LENGTH_SHORT).show()
                    },
                )
                SettingsItem(
                    title = "复制当前日志",
                    icon = Icons.Outlined.ContentCopy,
                    onClick = {
                        val latestLines = viewModel.diagnostics.readLatestLines(300)
                        context.getSystemService(android.content.ClipboardManager::class.java)
                            ?.setPrimaryClip(android.content.ClipData.newPlainText("LinuxDo 诊断日志", latestLines.joinToString("\n")))
                        Toast.makeText(context, "已复制最新${latestLines.size}行日志", Toast.LENGTH_SHORT).show()
                    },
                )
                SettingsItem(
                    title = "登出并清除 Cookie",
                    icon = Icons.Outlined.DeleteForever,
                    titleColor = MaterialTheme.colorScheme.error,
                    iconTint = MaterialTheme.colorScheme.error,
                    onClick = viewModel::logout,
                )
            }
        }

        item(key = "session") {
            SettingsGroup(title = "会话诊断") {
                InfoRow("当前用户", state.username ?: "未确认")
                InfoRow("最近路径", stats.lastPath)
                InfoRow("最近状态码", stats.lastStatus?.toString() ?: "-")
                InfoRow("静默重验次数", stats.silentRevalidations.toString())
                InfoRow("需交互验证次数", stats.interactiveRequired.toString())
                InfoRow("Cookie", state.cookieNames.joinToString(", ").ifEmpty { "无" })
                InfoRow("Cookie 更新于", savedAt)
                InfoRow("User-Agent", state.userAgent)
            }
        }

        item(key = "go-ech") {
            var refresh by remember { mutableStateOf(0) }
            val goStats = remember(refresh) { viewModel.doh.goEchStats()?.let { runCatching { org.json.JSONObject(it) }.getOrNull() } }
            SettingsGroup(title = "自研 ECH 内核") {
                if (goStats == null) {
                    InfoRow("状态", "未启用（关闭了开关，或当前没走 DoH 直连）")
                } else {
                    InfoRow("请求 / 失败", "${goStats.optLong("requests")} / ${goStats.optLong("failures")}")
                    InfoRow("DoH 节点", goStats.optString("lastServer").ifBlank { "-" })
                    InfoRow("连接 IP · 握手", "${goStats.optString("lastIP").ifBlank { "-" }} · ${goStats.optLong("handshakeMs")} ms")
                    InfoRow("ECH 配置刷新次数", goStats.optLong("echRetries").toString())
                    InfoRow("最近错误", goStats.optString("lastError").ifBlank { "无" })
                }
                SettingsItem(title = "刷新统计", icon = Icons.Outlined.Refresh, onClick = { refresh++ })
            }
        }

        item(key = "log-title") { SectionTitle("日志") }
        // 日志文本可能重复,不能拿内容当 key。
        items(lines.asReversed()) { line ->
            Text(
                text = line,
                style = CodeTextStyle.copy(fontSize = 10.sp, lineHeight = 14.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.screenPadding + 16.dp, vertical = 2.dp),
            )
        }
        item(key = "log-tail") { Spacer(Modifier.height(32.dp)) }
    }

    zeroDohPocReport?.let { report ->
        AlertDialog(
            onDismissRequest = { zeroDohPocReport = null },
            title = { Text("零 DoH 可行性验证报告") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "完整报告同时写入 logcat，可用 adb logcat -s ZeroDohPoc:I 取回",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(report, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton(onClick = { zeroDohPocReport = null }) { Text("关闭") } },
        )
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    SettingsItem(title = label, summary = value)
}
