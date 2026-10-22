package org.linuxdo.android.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

/**
 * LINUX DO 社区特色定制头像形状:
 * 论坛创始人 / 站长 neo 的头像是微圆角方形,其他用户统一为圆形。
 */
fun avatarShape(username: String?, avatarUrl: String? = null, cornerRadius: Dp = 4.dp): Shape {
    val isNeo = username?.equals("neo", ignoreCase = true) == true ||
        avatarUrl?.contains("/neo/", ignoreCase = true) == true ||
        avatarUrl?.contains("user_avatar/linux.do/neo", ignoreCase = true) == true
    return if (isNeo) RoundedCornerShape(cornerRadius) else CircleShape
}

/**
 * 统一的用户头像组件:
 * 1. 优先加载动图 / 高优头像 avatarUrl(如 GIF 动画头像);
 * 2. 失败或为空时无缝降级到静态头像 fallbackUrl;
 * 3. 都不可用时显示用户名首字母占位,绝不出现空白方块;
 * 4. 自动应用站长 neo 方形角与普通用户圆形裁切。
 */
@Composable
fun UserAvatar(
    avatarUrl: String?,
    fallbackUrl: String? = null,
    username: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 4.dp,
    contentScale: ContentScale = ContentScale.Crop,
) {
    var failedPrimary by remember(avatarUrl) { mutableStateOf(false) }
    var failedFallback by remember(fallbackUrl) { mutableStateOf(false) }
    val effectiveUrl = if (failedPrimary || avatarUrl.isNullOrBlank()) fallbackUrl else avatarUrl
    val shape = avatarShape(username, effectiveUrl ?: avatarUrl, cornerRadius)

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (!effectiveUrl.isNullOrBlank() && !failedFallback) {
            AsyncImage(
                model = effectiveUrl,
                contentDescription = username,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                onError = {
                    if (!failedPrimary && !fallbackUrl.isNullOrBlank() && fallbackUrl != avatarUrl) {
                        failedPrimary = true
                    } else {
                        failedFallback = true
                    }
                },
            )
        } else {
            val initial = username?.trimStart('@')?.firstOrNull()?.uppercase() ?: "★"
            Text(
                text = initial,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** 列表分隔线,默认左侧内缩到正文起点。 */
@Composable
fun ListDivider(startIndent: Dp = Dimens.screenPadding, modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(start = startIndent),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    )
}

/**
 * 一组内容的容器。「清简」里不再铺圆角色块:内容直接落在页面底色上,组尾一条发丝线收口。
 * 设置分组、分类列表的每一行都用它,所以改这一处两边一起变平。
 */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        content()
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** 列表行:最小 56dp 高,可点击时带水波纹。底色由父层提供。 */
@Composable
fun ListRow(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .defaultMinSize(minHeight = Dimens.rowMinHeight)
            .padding(horizontal = Dimens.screenPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** 圆形进度指示器。线宽随尺寸缩放,小尺寸(按钮内、下拉刷新)也不显得笨重。 */
@Composable
fun LoadingIndicator(
    modifier: Modifier = Modifier,
    diameter: Dp = 24.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    CircularProgressIndicator(
        modifier = modifier.size(diameter),
        color = color,
        strokeWidth = (diameter.value / 9f).coerceIn(2f, 4f).dp,
    )
}

/** 居中的加载占位,用于整页首次加载。 */
@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        LoadingIndicator(diameter = 32.dp)
    }
}
