package org.linuxdo.android.ui.screen.settings

import androidx.compose.material.icons.outlined.Memory
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoMode
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import org.linuxdo.android.net.DohServer
import org.linuxdo.android.ui.design.AppTheme
import org.linuxdo.android.ui.design.LoadingIndicator
import org.linuxdo.android.ui.screen.ConfirmDialog

@Composable
internal fun NetworkSettings(
    dohEnabled: Boolean,
    onDohEnabled: (Boolean) -> Unit,
    servers: List<DohServer>,
    selectedServerUrl: String,
    onServerSelected: (DohServer) -> Unit,
    autoFailover: Boolean,
    onAutoFailover: (Boolean) -> Unit,
    autoOptimize: Boolean,
    onAutoOptimize: (Boolean) -> Unit,
    latencies: Map<String, Long?>,
    testingAll: Boolean,
    onTestAll: () -> Unit,
    onAddServer: (url: String, name: String) -> Unit,
    onUpdateServer: (oldServer: DohServer, url: String, name: String) -> Unit,
    onDeleteServer: (DohServer) -> Unit,
    onResetServers: () -> Unit,
    goEchEnabled: Boolean = true,
    onGoEchEnabled: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var editingServer by remember { mutableStateOf<DohServer?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }
    val vpnActive = remember(context) { isVpnActive(context) }

    SettingsGroup(title = "DoH 安全直连") {
        if (vpnActive) {
            SettingsItem(
                title = "已检测到 VPN / 系统代理",
                icon = Icons.Outlined.VpnKey,
                summary = "系统代理已接管全局网络，直连会自动暂停；断开代理后可继续使用 DoH 直连",
                titleColor = AppTheme.colors.warning,
                iconTint = AppTheme.colors.warning,
            )
        }
        SettingsSwitchItem(
            title = "DNS over HTTPS",
            icon = Icons.Outlined.Security,
            summary = if (dohEnabled) "已开启 · 仅解析 linux.do，其余走系统 DNS" else "关闭 · 使用系统默认 DNS",
            checked = dohEnabled,
            onCheckedChange = onDohEnabled,
        )
        if (dohEnabled) {
            SettingsSwitchItem(
                title = "自动故障切换",
                icon = Icons.Outlined.SwapHoriz,
                summary = "当前节点不可用时自动换到其他健康节点",
                checked = autoFailover,
                onCheckedChange = onAutoFailover,
            )
            SettingsSwitchItem(
                title = "自研 ECH 内核（实验）",
                icon = Icons.Outlined.Memory,
                summary = if (goEchEnabled) "已开启 · linux.do 走开源的 Go ECH 直连，失败时自动退回原内核"
                else "关闭 · 使用原来的闭源直连内核",
                checked = goEchEnabled,
                onCheckedChange = onGoEchEnabled,
            )
            SettingsSwitchItem(
                title = "智能定时测速切优",
                icon = Icons.Outlined.AutoMode,
                summary = "每 10 分钟监测各节点延迟，自动切换到最快的健康节点",
                checked = autoOptimize,
                onCheckedChange = onAutoOptimize,
            )
        }
    }

    if (dohEnabled) {
        SettingsGroup(title = "节点") {
            servers.forEach { server ->
                ServerRow(
                    server = server,
                    selected = server.url == selectedServerUrl,
                    latency = latencies[server.id],
                    testing = testingAll || latencies.containsKey(server.id),
                    onSelect = { onServerSelected(server) },
                    onEdit = { editingServer = server },
                    onDelete = { onDeleteServer(server) },
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("添加节点")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { showResetConfirm = true }) { Text("恢复默认") }
                    TextButton(onClick = onTestAll, enabled = !testingAll) {
                        if (testingAll) LoadingIndicator(diameter = 16.dp)
                        else Icon(Icons.Outlined.Speed, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("测速")
                    }
                }
            }
        }
    }

    CacheSettings()

    if (showAddDialog) {
        ServerDialog(
            title = "添加 DoH 节点",
            confirmText = "添加",
            initialUrl = "",
            initialName = "",
            onDismiss = { showAddDialog = false },
            onConfirm = { url, name ->
                onAddServer(url, name.ifBlank { url })
                showAddDialog = false
            },
        )
    }
    editingServer?.let { server ->
        ServerDialog(
            title = "编辑 DoH 节点",
            confirmText = "保存",
            initialUrl = server.url,
            initialName = server.name,
            onDismiss = { editingServer = null },
            onConfirm = { url, name ->
                onUpdateServer(server, url, name.ifBlank { url })
                editingServer = null
            },
        )
    }
    if (showResetConfirm) {
        ConfirmDialog(
            title = "恢复默认预设",
            text = "确定要将 DoH 节点列表恢复为默认预设吗？此操作将清除自定义节点与修改。",
            confirmText = "恢复默认",
            onDismiss = { showResetConfirm = false },
            onConfirm = {
                onResetServers()
                showResetConfirm = false
            },
        )
    }
}

@Composable
private fun ServerRow(
    server: DohServer,
    selected: Boolean,
    latency: Long?,
    testing: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(server.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(server.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    latency != null -> LatencyChip(latency)
                    testing -> LoadingIndicator(diameter = 16.dp)
                }
                IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "编辑节点") }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "删除节点", tint = MaterialTheme.colorScheme.error)
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onSelect),
    )
}

/** 延迟标签:100ms 以内绿色,超时灰色。 */
@Composable
private fun LatencyChip(latencyMs: Long) {
    val (container, content, label) = when {
        latencyMs < 0L -> Triple(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant, "超时")
        latencyMs < 100L -> Triple(AppTheme.colors.successContainer, AppTheme.colors.onSuccessContainer, "${latencyMs}ms")
        else -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, "${latencyMs}ms")
    }
    Surface(shape = MaterialTheme.shapes.small, color = container) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = content, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}

@Composable
private fun ServerDialog(
    title: String,
    confirmText: String,
    initialUrl: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (url: String, name: String) -> Unit,
) {
    val context = LocalContext.current
    var url by remember { mutableStateOf(initialUrl) }
    var name by remember { mutableStateOf(initialName) }
    val urlValid = url.trim().startsWith("https://")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("服务器 URL") },
                    supportingText = { Text("必须以 https:// 开头") },
                    isError = url.isNotBlank() && !urlValid,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("节点名称（可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (urlValid) onConfirm(url.trim(), name.trim())
                else Toast.makeText(context, "URL 必须以 https:// 开头", Toast.LENGTH_SHORT).show()
            }) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun CacheSettings() {
    val context = LocalContext.current
    // 遍历缓存目录有 I/O 开销,只在进入这一页时算一次。
    var cacheSize by remember { mutableStateOf(cacheSizeText(context)) }
    SettingsGroup(title = "缓存") {
        SettingsItem(
            title = "清理本地缓存",
            icon = Icons.Outlined.CleaningServices,
            summary = "仅清理图片与网络临时资源，不影响账号和已读状态",
            value = cacheSize,
            onClick = {
                runCatching {
                    listOfNotNull(context.cacheDir, context.codeCacheDir, context.externalCacheDir).forEach { it.deleteRecursively() }
                }
                cacheSize = cacheSizeText(context)
                Toast.makeText(context, "已清理图片与网络临时缓存，账号与已读状态不受影响", Toast.LENGTH_SHORT).show()
            },
        )
    }
}

private fun cacheSizeText(context: Context): String = runCatching {
    val bytes = listOfNotNull(context.cacheDir, context.codeCacheDir, context.externalCacheDir)
        .filter { it.exists() }
        .sumOf { dir -> dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
    val mb = bytes / (1024.0 * 1024.0)
    if (mb < 0.1) "0.0 MB" else String.format(Locale.US, "%.1f MB", mb)
}.getOrDefault("0.0 MB")

private fun isVpnActive(context: Context): Boolean = runCatching {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    @Suppress("DEPRECATION")
    cm?.allNetworks?.any { network ->
        cm.getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
    } ?: false
}.getOrDefault(false)
