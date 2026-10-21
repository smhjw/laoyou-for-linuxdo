package org.linuxdo.android.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** M3 色板之外、业务上需要的语义色。浅色 / 深色各一套,保证在两种底色上都清楚。 */
data class ExtendedColors(
    /** 成功、已开启、已解决。 */
    val success: Color,
    val onSuccessContainer: Color,
    val successContainer: Color,
    /** 警告、待处理、置顶。 */
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    /** 点赞 / 回应的心形。 */
    val like: Color,
    /** 站长皇冠、高等级徽章等金色点缀。 */
    val gold: Color,
    /** 代码块底色。 */
    val codeBackground: Color,
)

val LightExtendedColors = ExtendedColors(
    success = Color(0xFF2E7D32),
    successContainer = Color(0xFFC8E6C9),
    onSuccessContainer = Color(0xFF0B3D0E),
    warning = Color(0xFFB26A00),
    warningContainer = Color(0xFFFFE0B2),
    onWarningContainer = Color(0xFF4A2800),
    like = Color(0xFFD81B60),
    gold = Color(0xFFB8860B),
    codeBackground = Color(0xFFEDEDF4),
)

val DarkExtendedColors = ExtendedColors(
    success = Color(0xFF81C784),
    successContainer = Color(0xFF1B5E20),
    onSuccessContainer = Color(0xFFC8E6C9),
    warning = Color(0xFFFFB74D),
    warningContainer = Color(0xFF5D3A00),
    onWarningContainer = Color(0xFFFFE0B2),
    like = Color(0xFFF48FB1),
    gold = Color(0xFFFFD54F),
    codeBackground = Color(0xFF1D2024),
)

/** 间距与尺寸。遵循 Material 的 4dp 网格。 */
object Dimens {
    val screenPadding: Dp = 16.dp
    val rowMinHeight: Dp = 56.dp
    val cardCorner: Dp = 16.dp
    val tabBarHeight: Dp = 80.dp
    val navBarHeight: Dp = 64.dp
    val avatarSize: Dp = 40.dp
}
