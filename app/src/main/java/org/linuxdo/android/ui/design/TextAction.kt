package org.linuxdo.android.ui.design

import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

/** 顶栏、卡片里的轻量文字操作。 */
@Composable
fun TextAction(
    text: String,
    color: Color = MaterialTheme.colorScheme.primary,
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, colors = ButtonDefaults.textButtonColors(contentColor = color)) {
        Text(text = text, style = textStyle)
    }
}
