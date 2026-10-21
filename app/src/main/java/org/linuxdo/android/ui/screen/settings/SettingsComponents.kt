package org.linuxdo.android.ui.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import org.linuxdo.android.ui.design.IosRow
import org.linuxdo.android.ui.design.IosTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import android.graphics.Canvas
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import org.linuxdo.android.ui.*

@Composable
internal fun SettingIconBadge(
    bgColor: Color,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** 设置首页的品目行：只放图标、名称与当前状态，点击进入子页。 */
@Composable
internal fun SettingsEntryRow(
    title: String,
    badgeColor: Color,
    onClick: () -> Unit,
    value: String? = null,
    icon: @Composable () -> Unit,
) {
    IosRow(onClick = onClick) {
        SettingIconBadge(bgColor = badgeColor, content = icon)
        Spacer(Modifier.width(12.dp))
        Text(title, style = IosTheme.type.body, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, style = IosTheme.type.subheadline, color = IosTheme.colors.secondaryLabel)
            Spacer(Modifier.width(8.dp))
        }
        Text("›", color = IosTheme.colors.tertiaryLabel, style = IosTheme.type.title3)
    }
}

@Composable
internal fun SettingAppearanceIcon(tint: Color) {
    Canvas(Modifier.size(18.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            // 半实心圆：浅色 / 深色主题
            drawCircle(tint, radius = 8.5f, center = Offset(12f, 12f), style = Stroke(2f))
            drawArc(tint, startAngle = -90f, sweepAngle = 180f, useCenter = true, topLeft = Offset(3.5f, 3.5f), size = Size(17f, 17f))
        }
    }
}

@Composable
internal fun SettingBookIcon(tint: Color) {
    Canvas(Modifier.size(18.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val book = Path().apply {
                moveTo(12f, 6.5f)
                cubicTo(10f, 5f, 6.5f, 4.5f, 3f, 5f)
                lineTo(3f, 18.5f)
                cubicTo(6.5f, 18f, 10f, 18.5f, 12f, 20f)
                cubicTo(14f, 18.5f, 17.5f, 18f, 21f, 18.5f)
                lineTo(21f, 5f)
                cubicTo(17.5f, 4.5f, 14f, 5f, 12f, 6.5f)
                close()
            }
            drawPath(book, tint, style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawLine(tint, Offset(12f, 6.5f), Offset(12f, 20f), strokeWidth = 2f)
        }
    }
}

@Composable
internal fun SettingBellIcon(tint: Color) {
    Canvas(Modifier.size(18.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val bell = Path().apply {
                moveTo(5f, 17f); lineTo(7f, 14f); lineTo(7f, 9f)
                cubicTo(7f, 2.5f, 17f, 2.5f, 17f, 9f)
                lineTo(17f, 14f); lineTo(19f, 17f); close()
            }
            drawPath(bell, tint, style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawCircle(tint, radius = 1.8f, center = Offset(12f, 20f))
        }
    }
}

@Composable
internal fun SettingFloatingBallIcon(tint: Color) {
    Canvas(Modifier.size(18.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            drawCircle(tint, radius = 8.5f, center = Offset(12f, 12f), style = Stroke(2.2f))
            drawCircle(tint, radius = 3.5f, center = Offset(12f, 12f))
        }
    }
}

@Composable
internal fun SettingTreeIcon(tint: Color) {
    Canvas(Modifier.size(18.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            // 组织架构树/分支树
            drawRoundRect(tint, Offset(9.5f, 3f), androidx.compose.ui.geometry.Size(5f, 5f), androidx.compose.ui.geometry.CornerRadius(1.2f, 1.2f))
            drawLine(tint, Offset(12f, 8f), Offset(12f, 13f), strokeWidth = 2f)
            drawLine(tint, Offset(6f, 13f), Offset(18f, 13f), strokeWidth = 2f)
            drawLine(tint, Offset(6f, 13f), Offset(6f, 16f), strokeWidth = 2f)
            drawLine(tint, Offset(18f, 13f), Offset(18f, 16f), strokeWidth = 2f)
            drawRoundRect(tint, Offset(3.5f, 16f), androidx.compose.ui.geometry.Size(5f, 5f), androidx.compose.ui.geometry.CornerRadius(1.2f, 1.2f))
            drawRoundRect(tint, Offset(15.5f, 16f), androidx.compose.ui.geometry.Size(5f, 5f), androidx.compose.ui.geometry.CornerRadius(1.2f, 1.2f))
        }
    }
}

@Composable
internal fun SettingBatteryIcon(tint: Color) {
    Canvas(Modifier.size(18.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            drawRoundRect(tint, Offset(3f, 7f), androidx.compose.ui.geometry.Size(15f, 10f), androidx.compose.ui.geometry.CornerRadius(2f, 2f), style = Stroke(2f))
            drawRoundRect(tint, Offset(18f, 10f), androidx.compose.ui.geometry.Size(2.5f, 4f), androidx.compose.ui.geometry.CornerRadius(1f, 1f))
            val bolt = Path().apply {
                moveTo(11.5f, 9f); lineTo(8.5f, 12f); lineTo(11f, 12f); lineTo(9.5f, 15f); lineTo(13.5f, 11.5f); lineTo(11f, 11.5f); close()
            }
            drawPath(bolt, tint)
        }
    }
}

@Composable
internal fun SettingRocketIcon(tint: Color) {
    Canvas(Modifier.size(18.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val rocket = Path().apply {
                moveTo(14.5f, 2.5f)
                cubicTo(11f, 3.5f, 7.5f, 7f, 6.5f, 10.5f)
                lineTo(4f, 13f); lineTo(7.5f, 14f); lineTo(8.5f, 17.5f)
                lineTo(11f, 15f)
                cubicTo(14.5f, 14f, 18f, 10.5f, 19f, 7f)
                lineTo(14.5f, 2.5f); close()
            }
            drawPath(rocket, tint)
            drawCircle(Color.White, radius = 1.8f, center = Offset(13.5f, 8f))
        }
    }
}

@Composable
internal fun SettingClockIcon(tint: Color) {
    Canvas(Modifier.size(18.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            drawCircle(tint, radius = 8.5f, center = Offset(12f, 12f), style = Stroke(2f))
            drawLine(tint, Offset(12f, 7.5f), Offset(12f, 12f), strokeWidth = 2f, cap = StrokeCap.Round)
            drawLine(tint, Offset(12f, 12f), Offset(15.5f, 12f), strokeWidth = 2f, cap = StrokeCap.Round)
        }
    }
}

@Composable
internal fun SettingBroomIcon(tint: Color) {
    Canvas(Modifier.size(18.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val brush = Path().apply {
                moveTo(16f, 3f); lineTo(20f, 7f); lineTo(14f, 13f); lineTo(10f, 9f); close()
                moveTo(9f, 10f); lineTo(13f, 14f); lineTo(9f, 19f); lineTo(4f, 19f); lineTo(4f, 14f); close()
            }
            drawPath(brush, tint)
        }
    }
}

@Composable
internal fun SettingInfoIcon(tint: Color) {
    Canvas(Modifier.size(18.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            drawCircle(tint, radius = 8.5f, center = Offset(12f, 12f), style = Stroke(2f))
            drawCircle(tint, radius = 1.2f, center = Offset(12f, 8f))
            drawLine(tint, Offset(12f, 11f), Offset(12f, 16f), strokeWidth = 2.2f, cap = StrokeCap.Round)
        }
    }
}

@Composable
internal fun SettingDohIcon(tint: Color) {
    Canvas(Modifier.size(18.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            // 盾牌形状：代表 DNS 安全保护
            val shield = Path().apply {
                moveTo(12f, 3f)
                lineTo(4f, 6.5f)
                lineTo(4f, 12f)
                cubicTo(4f, 16.5f, 7.5f, 20f, 12f, 21f)
                cubicTo(16.5f, 20f, 20f, 16.5f, 20f, 12f)
                lineTo(20f, 6.5f)
                close()
            }
            drawPath(shield, tint, style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            // 内部勾：表示已验证
            val check = Path().apply {
                moveTo(9f, 12f); lineTo(11f, 14f); lineTo(15f, 10f)
            }
            drawPath(check, tint, style = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
internal fun DohGearIcon(tint: Color = Color(0xFF007AFF), modifier: Modifier = Modifier) {
    Canvas(modifier.size(15.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val outerR = size.width * 0.46f
        val innerR = size.width * 0.35f
        val holeR = size.width * 0.16f
        val teeth = 6
        val path = Path()
        for (i in 0 until teeth * 2) {
            val angle = (i * 360f / (teeth * 2)) * (Math.PI / 180.0)
            val r = if (i % 2 == 0) outerR else innerR
            val x = cx + (r * Math.cos(angle)).toFloat()
            val y = cy + (r * Math.sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, color = tint, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color = tint, radius = holeR, center = Offset(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.6.dp.toPx()))
    }
}

@Composable
internal fun DohTrashIcon(tint: Color = Color(0xFFFF3B30), modifier: Modifier = Modifier) {
    Canvas(modifier.size(15.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 1.6.dp.toPx()

        // 盖子横线
        val lidY = h * 0.28f
        drawLine(color = tint, start = Offset(w * 0.16f, lidY), end = Offset(w * 0.84f, lidY), strokeWidth = stroke, cap = StrokeCap.Round)
        // 提手
        val handlePath = Path().apply {
            moveTo(w * 0.38f, lidY)
            lineTo(w * 0.38f, h * 0.14f)
            lineTo(w * 0.62f, h * 0.14f)
            lineTo(w * 0.62f, lidY)
        }
        drawPath(handlePath, color = tint, style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // 桶身外框（微倒梯形）
        val bodyPath = Path().apply {
            moveTo(w * 0.24f, lidY + stroke * 0.8f)
            lineTo(w * 0.28f, h * 0.88f)
            lineTo(w * 0.72f, h * 0.88f)
            lineTo(w * 0.76f, lidY + stroke * 0.8f)
        }
        drawPath(bodyPath, color = tint, style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))

        // 桶身内 2 条竖纹
        drawLine(color = tint, start = Offset(w * 0.42f, lidY + h * 0.18f), end = Offset(w * 0.42f, h * 0.76f), strokeWidth = stroke * 0.8f, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(w * 0.58f, lidY + h * 0.18f), end = Offset(w * 0.58f, h * 0.76f), strokeWidth = stroke * 0.8f, cap = StrokeCap.Round)
    }
}

@Composable
private fun SettingSyncIcon(tint: Color) {
    Canvas(Modifier.size(18.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            drawArc(tint, startAngle = 30f, sweepAngle = 200f, useCenter = false, topLeft = Offset(4f, 4f), size = androidx.compose.ui.geometry.Size(16f, 16f), style = Stroke(2.2f, cap = StrokeCap.Round))
            drawArc(tint, startAngle = 210f, sweepAngle = 200f, useCenter = false, topLeft = Offset(4f, 4f), size = androidx.compose.ui.geometry.Size(16f, 16f), style = Stroke(2.2f, cap = StrokeCap.Round))
        }
    }
}

@Composable
internal fun SettingTerminalIcon(tint: Color) {
    Canvas(Modifier.size(18.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val chevron = Path().apply {
                moveTo(6f, 7f); lineTo(11f, 12f); lineTo(6f, 17f)
            }
            drawPath(chevron, tint, style = Stroke(2.4f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            drawLine(tint, Offset(13f, 17f), Offset(19f, 17f), strokeWidth = 2.4f, cap = StrokeCap.Round)
        }
    }
}

@Composable
internal fun SettingLogoutIcon(tint: Color) {
    Canvas(Modifier.size(18.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            // 登出图标: 左边方框开口 + 向右箭头
            val door = Path().apply {
                moveTo(13f, 4f)
                lineTo(5f, 4f)
                cubicTo(4f, 4f, 3f, 5f, 3f, 6f)
                lineTo(3f, 18f)
                cubicTo(3f, 19f, 4f, 20f, 5f, 20f)
                lineTo(13f, 20f)
            }
            drawPath(door, tint, style = Stroke(2.2f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
            drawLine(tint, Offset(9f, 12f), Offset(20f, 12f), strokeWidth = 2.2f, cap = StrokeCap.Round)
            val arrow = Path().apply {
                moveTo(16f, 8f); lineTo(20f, 12f); lineTo(16f, 16f)
            }
            drawPath(arrow, tint, style = Stroke(2.2f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
    }
}
