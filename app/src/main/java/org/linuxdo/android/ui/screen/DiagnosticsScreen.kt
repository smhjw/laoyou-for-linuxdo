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
import org.linuxdo.android.ui.screen.settings.SettingIconBadge
import org.linuxdo.android.ui.screen.settings.SettingInfoIcon

/**
 * 开发者选项。承接原型界面的全部调试能力:双路径切换、自动刷新、会话诊断与日志。
 * 这些是排查 Cloudflare 与会话保持问题的主要手段,所以界面改版时只换位置、不削功能。
 */
@Composable
fun DiagnosticsScreen(state: UiState, viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val stats by viewModel.diagnostics.stats.collectAsStateWithLifecycle()
    val lines by viewModel.diagnostics.lines.collectAsStateWithLifecycle()
    val savedAt = if (state.cookieSavedAt == 0L) {
        "-"
    } else {
        DateFormat.getDateTimeInstance().format(Date(state.cookieSavedAt))
    }
    var zeroDohPocReport by remember { mutableStateOf<String?>(null) }
    var zeroDohPocRunning by remember { mutableStateOf(false) }
    val zeroDohPocScope = androidx.compose.runtime.rememberCoroutineScope()

    AppScaffold(title = "开发者选项", onBack = onBack) {
        item(key = "paths") {
            SectionTitle("网络路径")
            SectionCard {
                NetworkPath.entries.forEachIndexed { index, path ->
                    ListRow(onClick = { viewModel.setPath(path) }) {
                        Text(
                            text = path.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (state.path == path) {
                            Text("使用中", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (index < NetworkPath.entries.lastIndex) ListDivider()
                }
            }
        }

        item(key = "zero-doh-lab") {
            SectionTitle("网络直连实验")
            SectionCard {
                ListRow(onClick = {
                    if (zeroDohPocRunning) {
                        Toast.makeText(context, "正在验证中，请稍候…", Toast.LENGTH_SHORT).show()
                    } else {
                        zeroDohPocRunning = true
                        zeroDohPocScope.launch {
                            zeroDohPocReport = "未集成实验模块"
                            zeroDohPocRunning = false
                        }
                    }
                }) {
                    SettingIconBadge(bgColor = Color(0xFFE8F5E9)) {
                        SettingInfoIcon(tint = Color(0xFF43A047))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("零 DoH 直连可行性验证", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "验证 Anycast 零 DNS 直连、ECH 存活性、WebView 代理导流与 ClientHello 分片",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 3.dp),
                        )
                    }
                    Text(
                        if (zeroDohPocRunning) "验证中…" else "开始验证 ›",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        item(key = "actions") {
            SectionTitle("操作")
            SectionCard {
                ListRow {
                    Text(
                        text = "自动刷新(${Config.AUTO_REFRESH_INTERVAL_MS / 60_000} 分钟)",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = state.autoRefresh,
                        onCheckedChange = { viewModel.toggleAutoRefresh() },
                        colors = SwitchDefaults.colors(
                            // iOS 的开关轨道是 systemGreen。
                            checkedTrackColor = AppTheme.colors.success,
                            checkedThumbColor = Color.White,
                        ),
                    )
                }
                ListDivider()
                ListRow(onClick = viewModel::retryLatest) {
                    Text("立即刷新列表", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                }
                ListDivider()
                ListRow(onClick = {
                    NotificationHelper.recordDiagnosticSnapshot(context)
                    Toast.makeText(context, "推送与角标状态已记录到下方日志", Toast.LENGTH_SHORT).show()
                }) {
                    Text("记录推送与角标状态", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                }
                ListDivider()
                ListRow(onClick = {
                    val latestLines = viewModel.diagnostics.readLatestLines(300)
                    context.getSystemService(android.content.ClipboardManager::class.java)
                        ?.setPrimaryClip(android.content.ClipData.newPlainText("LinuxDo 诊断日志", latestLines.joinToString("\n")))
                    Toast.makeText(context, "已复制最新${latestLines.size}行日志", Toast.LENGTH_SHORT).show()
                }) {
                    Text("复制当前日志", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                }
                ListDivider()
                ListRow(onClick = viewModel::logout) {
                    Text("登出并清除 Cookie", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        item(key = "session") {
            SectionTitle("会话诊断")
            SectionCard {
                InfoRow("当前用户", state.username ?: "未确认")
                ListDivider()
                InfoRow("最近路径", stats.lastPath)
                ListDivider()
                InfoRow("最近状态码", stats.lastStatus?.toString() ?: "-")
                ListDivider()
                InfoRow("静默重验次数", stats.silentRevalidations.toString())
                ListDivider()
                InfoRow("需交互验证次数", stats.interactiveRequired.toString())
                ListDivider()
                InfoRow("Cookie", state.cookieNames.joinToString(", ").ifEmpty { "无" })
                ListDivider()
                InfoRow("Cookie 更新于", savedAt)
                ListDivider()
                InfoRow("User-Agent", state.userAgent)
            }
        }

        item(key = "log-title") { SectionTitle("日志") }
        // 日志文本可能重复,不能拿内容当 key。
        items(lines.asReversed()) { line ->
            Text(
                text = line,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.screenPadding, vertical = 2.dp),
            )
        }
        item(key = "log-tail") { Spacer(Modifier.height(32.dp)) }
    }

    zeroDohPocReport?.let { report ->
        Dialog(onDismissRequest = { zeroDohPocReport = null }) {
            Column(
                modifier = Modifier
                    .widthIn(min = 290.dp, max = 350.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("零 DoH 可行性验证报告", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "完整报告同时写入 logcat，可用 adb logcat -s ZeroDohPoc:I 取回",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    report,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                )
                Text(
                    "关闭",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.End)
                        .clickable { zeroDohPocReport = null }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = Dimens.screenPadding + 16.dp,
            end = Dimens.screenPadding,
            top = 24.dp,
            bottom = 6.dp,
        ),
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    ListRow {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}
