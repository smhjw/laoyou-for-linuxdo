package org.linuxdo.android.ui.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Material 3 默认字阶,字形走系统字体(Roboto + 系统中文字体)。
 * 中文正文行高略放宽一点,长帖阅读更松快。
 */
val AppTypography: Typography = Typography().run {
    copy(
        bodyLarge = bodyLarge.copy(lineHeight = 26.sp),
        bodyMedium = bodyMedium.copy(lineHeight = 22.sp),
    )
}

/** 代码块与行内代码。 */
val CodeTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    lineHeight = 20.sp,
    fontWeight = FontWeight.Normal,
)
