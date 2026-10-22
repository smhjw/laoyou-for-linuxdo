package org.linuxdo.android.ui.design

import android.app.Activity
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }
private val LocalIsDark = staticCompositionLocalOf { false }

/**
 * 应用主题入口:Material 3 配色、字体、形状,外加 M3 没有的几种语义色。
 *
 * 颜色一律取语义角色(`MaterialTheme.colorScheme.primary` 等),不要在界面里写死色值 ——
 * 动态取色开启时整套配色随壁纸变化,写死的颜色会和周围格格不入。
 */
object AppTheme {
    /** 成功 / 警告 / 点赞等 M3 色板之外的语义色。 */
    val colors: ExtendedColors
        @Composable @ReadOnlyComposable get() = LocalExtendedColors.current

    val isDark: Boolean
        @Composable @ReadOnlyComposable get() = LocalIsDark.current
}

/**
 * @param dynamicColor Android 12+ 上跟随壁纸取色(Material You);低版本或关闭时用「清简」色板。
 */
@Composable
fun AppTheme(
    dark: Boolean,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                // targetSdk 35+ 强制全面屏,状态栏 / 导航栏透明,由界面底色透出;这里只管图标深浅。
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
    }
    CompositionLocalProvider(
        LocalExtendedColors provides if (dark) DarkExtendedColors else LightExtendedColors,
        LocalIsDark provides dark,
    ) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography) {
            // 页面根节点不是 Surface,不显式提供的话未指定颜色的文字会落回纯黑,深色模式下看不见。
            CompositionLocalProvider(LocalContentColor provides scheme.onSurface, content = content)
        }
    }
}

// 「清简」色板:中性灰 + 唯一强调色(取自 Logo 的蓝)。分割线用 outlineVariant,
// 列表背景就是 surface,不再给卡片额外铺底。

private val LightScheme = lightColorScheme(
    primary = Color(0xFF3451D1),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEEF1FD),
    onPrimaryContainer = Color(0xFF2C45B8),
    secondary = Color(0xFF545B68),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3E8FC),
    onSecondaryContainer = Color(0xFF2C45B8),
    tertiary = Color(0xFF1F8A8A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDDF3F3),
    onTertiaryContainer = Color(0xFF0E4F4F),
    error = Color(0xFFC62828),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFDE7E7),
    onErrorContainer = Color(0xFF8C1515),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF111318),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111318),
    surfaceVariant = Color(0xFFF1F3F6),
    onSurfaceVariant = Color(0xFF545B68),
    outline = Color(0xFF697080),
    outlineVariant = Color(0xFFECEEF2),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF23262C),
    inverseOnSurface = Color(0xFFF1F3F6),
    inversePrimary = Color(0xFF8FA4FF),
    surfaceDim = Color(0xFFE6E8EC),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F8FA),
    surfaceContainer = Color(0xFFF4F5F7),
    surfaceContainerHigh = Color(0xFFF1F3F6),
    surfaceContainerHighest = Color(0xFFECEEF2),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF8FA4FF),
    onPrimary = Color(0xFF0F1114),
    primaryContainer = Color(0xFF1B2240),
    onPrimaryContainer = Color(0xFFAEBBFF),
    secondary = Color(0xFFA9AFBA),
    onSecondary = Color(0xFF0F1114),
    secondaryContainer = Color(0xFF262D4A),
    onSecondaryContainer = Color(0xFFC9D2FF),
    tertiary = Color(0xFF7FD4D4),
    onTertiary = Color(0xFF0F1114),
    tertiaryContainer = Color(0xFF173838),
    onTertiaryContainer = Color(0xFFBDEDED),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF3B0A0A),
    errorContainer = Color(0xFF4A1515),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1114),
    onBackground = Color(0xFFECEEF1),
    surface = Color(0xFF0F1114),
    onSurface = Color(0xFFECEEF1),
    surfaceVariant = Color(0xFF1C1F24),
    onSurfaceVariant = Color(0xFFA9AFBA),
    outline = Color(0xFF8E95A1),
    outlineVariant = Color(0xFF23262C),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFECEEF1),
    inverseOnSurface = Color(0xFF1C1F24),
    inversePrimary = Color(0xFF3451D1),
    surfaceDim = Color(0xFF0F1114),
    surfaceBright = Color(0xFF2A2D33),
    surfaceContainerLowest = Color(0xFF0B0D10),
    surfaceContainerLow = Color(0xFF15171B),
    surfaceContainer = Color(0xFF181B1F),
    surfaceContainerHigh = Color(0xFF1C1F24),
    surfaceContainerHighest = Color(0xFF23262C),
)
