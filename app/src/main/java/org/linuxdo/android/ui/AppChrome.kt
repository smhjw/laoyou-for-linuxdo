package org.linuxdo.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.linuxdo.android.ui.design.IosMetrics
import org.linuxdo.android.ui.design.IosTheme
import android.graphics.Canvas
import androidx.compose.ui.geometry.Size

@Composable
fun UnreadBell(count: Int, onClick: () -> Unit) {
    val iconColor = IosTheme.colors.secondaryLabel
    Box(Modifier.size(44.dp).semantics { contentDescription = "通知，${count}条回复或私信未读" }
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(22.dp)) {
            scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
                val bell = Path().apply {
                    moveTo(5f, 17f); lineTo(7f, 14f); lineTo(7f, 9f)
                    cubicTo(7f, 2f, 17f, 2f, 17f, 9f)
                    lineTo(17f, 14f); lineTo(19f, 17f); close()
                }
                drawPath(bell, iconColor, style = Stroke(1.7f))
                drawArc(iconColor, 0f, 180f, false, Offset(10f, 18f), androidx.compose.ui.geometry.Size(4f, 3f), style = Stroke(1.7f))
            }
        }
        if (count > 0) Text(if (count > 99) "99+" else count.toString(), color = Color.White,
            fontSize = 10.sp, lineHeight = 12.sp,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = 2.dp)
                .background(Color(0xFFFF3B30), RoundedCornerShape(20.dp)).padding(horizontal = 4.dp, vertical = 1.dp))
    }
}

/** 登录/验证页顶部的说明条;WebView 本体由 AppShell 抬到它下方显示。 */
@Composable
fun WebTopBar(screen: Screen) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(IosMetrics.navBarHeight)
            .background(IosTheme.colors.card),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (screen == Screen.Login) "登录 linux.do" else "需要安全验证(通过后自动返回)",
            style = IosTheme.type.navTitle,
            color = IosTheme.colors.label,
        )
    }
}
