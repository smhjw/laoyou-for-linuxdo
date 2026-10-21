package org.linuxdo.android.ui.screen.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.linuxdo.android.data.FloatingAction
import org.linuxdo.android.ui.design.AppIcons

/** 悬浮球最多容纳的功能数。 */
private const val MAX_ACTIONS = 5

@Composable
internal fun FloatingBallSettings(
    enabled: Boolean,
    onEnabled: (Boolean) -> Unit,
    actions: List<FloatingAction>,
    onActions: (List<FloatingAction>) -> Unit,
) {
    val context = LocalContext.current
    SettingsGroup(title = "辅助功能") {
        SettingsSwitchItem(
            title = "开启辅助悬浮球",
            icon = Icons.Outlined.TouchApp,
            summary = "单功能点击直达，多功能展开选择；可拖动并记住位置",
            checked = enabled,
            onCheckedChange = onEnabled,
        )
    }
    if (!enabled) return

    // 已启用的按顺序排在前面,未启用的跟在后面。
    val allOptions = remember(actions) { actions + FloatingAction.entries.filter { it !in actions } }
    SettingsGroup(title = "功能配置与排序（${actions.size}/$MAX_ACTIONS）") {
        allOptions.forEach { action ->
            val isEnabled = action in actions
            val index = actions.indexOf(action)
            ListItem(
                headlineContent = { Text(action.title) },
                supportingContent = { Text(action.description) },
                leadingContent = {
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(
                                if (isEnabled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            AppIcons.floatingAction(action),
                            contentDescription = null,
                            tint = if (isEnabled) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.outline,
                        )
                    }
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isEnabled) {
                            IconButton(onClick = { onActions(actions.swapped(index, index - 1)) }, enabled = index > 0) {
                                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "上移")
                            }
                            IconButton(onClick = { onActions(actions.swapped(index, index + 1)) }, enabled = index < actions.lastIndex) {
                                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "下移")
                            }
                        }
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { checked ->
                                when {
                                    checked -> if (!isEnabled) onActions((actions + action).take(MAX_ACTIONS))
                                    actions.size > 1 -> onActions(actions - action)
                                    else -> Toast.makeText(context, "悬浮球至少需要保留 1 项功能", Toast.LENGTH_SHORT).show()
                                }
                            },
                        )
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        if (actions != FloatingAction.DEFAULT_ACTIONS) {
            TextButton(onClick = { onActions(FloatingAction.DEFAULT_ACTIONS) }, modifier = Modifier.align(Alignment.End)) {
                Text("恢复默认")
            }
        }
    }
}

private fun <T> List<T>.swapped(i: Int, j: Int): List<T> {
    if (i !in indices || j !in indices) return this
    return toMutableList().also { list ->
        val tmp = list[i]
        list[i] = list[j]
        list[j] = tmp
    }
}
