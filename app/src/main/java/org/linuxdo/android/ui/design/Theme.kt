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
 * @param dynamicColor Android 12+ 上跟随壁纸取色(Material You);低版本或关闭时用品牌蓝色板。
 */
@Composable
fun AppTheme(
    dark: Boolean,
    dynamicColor: Boolean = true,
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

// 品牌蓝色板,由 Material Theme Builder 以种子色 #769CDF 生成。

private val LightScheme = lightColorScheme(
    primary = Color(0xFF415F91),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF284777),
    secondary = Color(0xFF565F71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDAE2F9),
    onSecondaryContainer = Color(0xFF3E4759),
    tertiary = Color(0xFF705575),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFAD8FD),
    onTertiaryContainer = Color(0xFF573E5C),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF9F9FF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF9F9FF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF44474E),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2E3036),
    inverseOnSurface = Color(0xFFF0F0F7),
    inversePrimary = Color(0xFFAAC7FF),
    surfaceDim = Color(0xFFD9D9E0),
    surfaceBright = Color(0xFFF9F9FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F3FA),
    surfaceContainer = Color(0xFFEDEDF4),
    surfaceContainerHigh = Color(0xFFE7E8EE),
    surfaceContainerHighest = Color(0xFFE2E2E9),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFAAC7FF),
    onPrimary = Color(0xFF0A305F),
    primaryContainer = Color(0xFF284777),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFBEC6DC),
    onSecondary = Color(0xFF283141),
    secondaryContainer = Color(0xFF3E4759),
    onSecondaryContainer = Color(0xFFDAE2F9),
    tertiary = Color(0xFFDDBCE0),
    onTertiary = Color(0xFF3F2844),
    tertiaryContainer = Color(0xFF573E5C),
    onTertiaryContainer = Color(0xFFFAD8FD),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E9),
    surfaceVariant = Color(0xFF44474E),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474E),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE2E2E9),
    inverseOnSurface = Color(0xFF2E3036),
    inversePrimary = Color(0xFF415F91),
    surfaceDim = Color(0xFF111318),
    surfaceBright = Color(0xFF37393E),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
)
