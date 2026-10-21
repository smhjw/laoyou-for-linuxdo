package org.linuxdo.android.ui.screen.settings

import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.runtime.Composable
import org.linuxdo.android.ui.ThemeMode

@Composable
internal fun AppearanceSettings(
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    dynamicColor: Boolean,
    onDynamicColor: (Boolean) -> Unit,
) {
    SettingsGroup(title = "主题") {
        ThemeMode.entries.forEach { mode ->
            SettingsRadioItem(
                title = mode.label,
                selected = themeMode == mode,
                onClick = { onThemeMode(mode) },
            )
        }
    }
    val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    SettingsGroup(title = "配色") {
        SettingsSwitchItem(
            title = "动态取色",
            icon = Icons.Outlined.ColorLens,
            summary = if (supported) "跟随系统壁纸生成配色（Material You）" else "需要 Android 12 及以上",
            checked = dynamicColor && supported,
            onCheckedChange = onDynamicColor,
            enabled = supported,
        )
    }
}
