package org.linuxdo.android.ui.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 「清简」字阶:基于 Material 3,字形走系统字体(Roboto + 系统中文字体)。
 * 中文正文行高略放宽一点,长帖阅读更松快。
 */
val AppTypography: Typography = Typography().run {
    copy(
        // 顶栏标题与页面标题略加粗,层级靠字重而不是颜色块
        titleLarge = titleLarge.copy(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        headlineSmall = headlineSmall.copy(fontSize = 22.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
        bodyLarge = bodyLarge.copy(fontSize = 15.sp, lineHeight = 26.sp),
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
