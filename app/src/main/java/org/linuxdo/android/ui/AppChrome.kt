package org.linuxdo.android.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import org.linuxdo.android.ui.design.*
import androidx.compose.material3.MaterialTheme

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
import org.linuxdo.android.ui.design.Dimens
import android.graphics.Canvas
import androidx.compose.ui.geometry.Size

@Composable
fun UnreadBell(count: Int, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = "通知，" + count + "条回复或私信未读" },
    ) {
        BadgedBox(badge = {
            if (count > 0) Badge { Text(if (count > 99) "99+" else count.toString()) }
        }) {
            Icon(Icons.Outlined.Notifications, contentDescription = null)
        }
    }
}

/** 登录/验证页顶部的说明条;WebView 本体由 AppShell 抬到它下方显示。 */
@Composable
fun WebTopBar(screen: Screen) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(Dimens.navBarHeight)
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (screen == Screen.Login) "登录 linux.do" else "需要安全验证(通过后自动返回)",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
