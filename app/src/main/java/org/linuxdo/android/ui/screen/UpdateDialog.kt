package org.linuxdo.android.ui.screen

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.linuxdo.android.service.NotificationHelper
import org.linuxdo.android.service.ApkUpdateInstaller
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import org.linuxdo.android.ui.design.IosTheme
import androidx.compose.ui.text.font.FontWeight
import java.util.Locale
import org.linuxdo.android.ui.*

@Composable
fun UpdateDialog(
    currentVersion: String,
    info: UpdateInfo,
    stage: ApkUpdateStage,
    downloadedBytes: Long,
    totalBytes: Long,
    errorMessage: String?,
    onStartDownload: () -> Unit,
    onRetryAfterPermission: () -> Unit,
    onCancelDownload: () -> Unit,
    onDismiss: (ignoreThisVersion: Boolean) -> Unit,
) {
    val context = LocalContext.current
    // 下载或安装进行中就不让点外部/返回键关掉，避免把进度丢掉。
    val busy = stage == ApkUpdateStage.Downloading ||
        stage == ApkUpdateStage.Verifying ||
        stage == ApkUpdateStage.Installing
    val closable = !info.mandatory && !busy
    Dialog(
        onDismissRequest = { if (closable) onDismiss(false) },
        properties = DialogProperties(
            dismissOnBackPress = closable,
            dismissOnClickOutside = closable,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(IosTheme.colors.card)
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (info.mandatory) {
                                IosTheme.colors.destructive.copy(alpha = 0.14f)
                            } else {
                                IosTheme.colors.accent.copy(alpha = 0.14f)
                            },
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = if (info.mandatory) "必升版本" else "发现新版本",
                        style = IosTheme.type.footnote,
                        color = if (info.mandatory) IosTheme.colors.destructive else IosTheme.colors.accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = "v$currentVersion → v${info.latestVersion}",
                    style = IosTheme.type.footnote,
                    color = IosTheme.colors.secondaryLabel,
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = info.title,
                style = IosTheme.type.title3,
                color = IosTheme.colors.label,
            )

            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp, max = 220.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(IosTheme.colors.fieldBackground)
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = info.changelog,
                    style = IosTheme.type.footnote,
                    color = IosTheme.colors.label,
                    lineHeight = 19.sp,
                )
            }

            if (info.mandatory) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "当前版本包含关键协议或安全更新，请升级至最新版本后继续使用。",
                    style = IosTheme.type.footnote,
                    color = IosTheme.colors.destructive,
                )
            }

            Spacer(Modifier.height(16.dp))

            // 授权页返回后自动续下，不用用户再点一次升级。
            val installPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult(),
            ) { onRetryAfterPermission() }

            when (stage) {
                ApkUpdateStage.Downloading, ApkUpdateStage.Verifying -> {
                    val fraction = if (totalBytes > 0L) {
                        (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    Text(
                        text = when {
                            stage == ApkUpdateStage.Verifying -> "正在校验安装包…"
                            totalBytes > 0L ->
                                "正在下载 ${(fraction * 100).toInt()}%　${formatUpdateBytes(downloadedBytes)} / ${formatUpdateBytes(totalBytes)}"
                            else -> "正在下载　${formatUpdateBytes(downloadedBytes)}"
                        },
                        style = IosTheme.type.footnote,
                        color = IosTheme.colors.secondaryLabel,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(IosTheme.colors.fieldBackground),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(3.dp))
                                .background(IosTheme.colors.accent),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onCancelDownload() }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "取消下载",
                            style = IosTheme.type.subheadline,
                            color = IosTheme.colors.secondaryLabel,
                        )
                    }
                }

                ApkUpdateStage.Installing -> {
                    Text(
                        text = "已唤起系统安装程序，请在系统弹窗中确认安装。",
                        style = IosTheme.type.footnote,
                        color = IosTheme.colors.secondaryLabel,
                    )
                    Spacer(Modifier.height(12.dp))
                    UpdateSecondaryAction(text = "改用浏览器下载") {
                        openInDefaultBrowser(context, info.downloadUrl)
                    }
                }

                ApkUpdateStage.AwaitingPermission -> {
                    Text(
                        text = "系统需要你允许本应用「安装未知应用」。开启后会自动继续下载并安装。",
                        style = IosTheme.type.footnote,
                        color = IosTheme.colors.secondaryLabel,
                    )
                    Spacer(Modifier.height(12.dp))
                    UpdatePrimaryAction(text = "前往开启授权") {
                        val intent = ApkUpdateInstaller.unknownSourceSettingsIntent(context)
                        if (intent != null) {
                            runCatching { installPermissionLauncher.launch(intent) }
                                .onFailure { NotificationHelper.openAppDetailsSettings(context) }
                        } else {
                            NotificationHelper.openAppDetailsSettings(context)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    UpdateSecondaryAction(text = "改用浏览器下载") {
                        openInDefaultBrowser(context, info.downloadUrl)
                    }
                }

                else -> {
                    if (!errorMessage.isNullOrBlank()) {
                        Text(
                            text = errorMessage,
                            style = IosTheme.type.footnote,
                            color = IosTheme.colors.destructive,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    UpdatePrimaryAction(
                        text = if (stage == ApkUpdateStage.Failed) {
                            "重试下载 (v${info.latestVersion})"
                        } else {
                            "立即升级 (v${info.latestVersion})"
                        },
                        onClick = onStartDownload,
                    )
                    if (stage == ApkUpdateStage.Failed) {
                        Spacer(Modifier.height(10.dp))
                        UpdateSecondaryAction(text = "改用浏览器下载") {
                            openInDefaultBrowser(context, info.downloadUrl)
                        }
                    }
                }
            }

            if (closable) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onDismiss(true) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "忽略此版本",
                            style = IosTheme.type.subheadline,
                            color = IosTheme.colors.secondaryLabel,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onDismiss(false) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "稍后提醒",
                            style = IosTheme.type.subheadline,
                            color = IosTheme.colors.accent,
                        )
                    }
                }
            }
        }
    }
}

/** 升级对话框的主操作按钮。 */
@Composable
private fun UpdatePrimaryAction(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(IosTheme.colors.accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = IosTheme.type.body,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 升级对话框的次要操作，用于浏览器兜底这类退路。 */
@Composable
private fun UpdateSecondaryAction(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = IosTheme.type.subheadline,
            color = IosTheme.colors.accent,
        )
    }
}

/** 下载进度用的字节展示：不足 1 MB 时用 KB，避免一直停在 0.0 MB。 */
private fun formatUpdateBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 KB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb < 1.0) {
        String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    } else {
        String.format(Locale.US, "%.1f MB", mb)
    }
}

/**
 * 优先调用系统设置中的“默认浏览器”打开升级链接，避免被装有特定下载接管过滤器的第三方应用（如某些下载器）误拦截。
 */
fun openInDefaultBrowser(context: android.content.Context, url: String) {
    val uri = Uri.parse(url)
    // 1. 构造一个纯网页意图，向系统 PackageManager 查询系统设置中指定的真正“默认浏览器”
    val probeIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://linux.do")).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
    }
    val defaultBrowserPkg = runCatching {
        context.packageManager
            .resolveActivity(probeIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
            ?.takeIf { it != "android" && it != "com.google.android.packageinstaller" }
    }.getOrNull()

    val targetIntent = Intent(Intent.ACTION_VIEW, uri).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!defaultBrowserPkg.isNullOrBlank()) {
            setPackage(defaultBrowserPkg)
        }
    }
    val launched = runCatching {
        context.startActivity(targetIntent)
        true
    }.getOrDefault(false)

    if (!launched) {
        // 兜底：若指定包名启动失败，移除包名并使用通用选择器唤起
        runCatching {
            val fallback = Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
        }
    }
}
