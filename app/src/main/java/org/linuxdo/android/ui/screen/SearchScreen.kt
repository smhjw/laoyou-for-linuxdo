package org.linuxdo.android.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.linuxdo.android.data.SearchOrder
import org.linuxdo.android.data.Topic
import org.linuxdo.android.ui.SearchUiState
import org.linuxdo.android.ui.design.AppTopBar
import org.linuxdo.android.ui.design.LoadingBox
import org.linuxdo.android.ui.design.ScrollToTopOnRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    onQuery: (String) -> Unit,
    onLoadMore: () -> Unit,
    onOpenTopic: (Topic) -> Unit,
    onOrder: (SearchOrder) -> Unit,
    active: Boolean = true,
    scrollToTopRequest: Int = 0,
    trailing: @Composable () -> Unit = {},
) {
    val list = rememberLazyListState()
    ScrollToTopOnRequest(list, scrollToTopRequest)
    val focusManager = LocalFocusManager.current
    var showSort by remember { mutableStateOf(false) }

    val nearEnd by remember { derivedStateOf {
        (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= list.layoutInfo.totalItemsCount - 3
    } }
    LaunchedEffect(active, nearEnd, state.results) {
        if (active && nearEnd && state.results.items.isNotEmpty() && state.results.hasMore &&
            !state.results.loadingMore && !state.results.initialLoading && state.results.error == null) onLoadMore()
    }
    var previousQuery by rememberSaveable { mutableStateOf(state.query) }
    var previousOrder by rememberSaveable { mutableStateOf(state.order) }
    LaunchedEffect(state.query, state.order) {
        if (previousQuery != state.query || previousOrder != state.order) {
            previousQuery = state.query
            previousOrder = state.order
            list.scrollToItem(0)
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        AppTopBar(title = "搜索", trailing = { trailing() })

        // Material 3 搜索框:整条胶囊,左放大镜、右清除。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .height(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            BasicTextField(
                value = state.query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier.weight(1f),
                decorationBox = { field ->
                    if (state.query.isEmpty()) {
                        Text("搜索话题", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    field()
                },
            )
            if (state.query.isNotEmpty()) {
                IconButton(onClick = { onQuery(""); focusManager.clearFocus() }) {
                    Icon(Icons.Filled.Close, contentDescription = "清除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(
                onClick = {
                    focusManager.clearFocus()
                    showSort = true
                },
                label = { Text("排序：${state.order.label}") },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = null, modifier = Modifier.padding(0.dp)) },
                trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.padding(0.dp)) },
                colors = AssistChipDefaults.assistChipColors(
                    leadingIconContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }

        LazyColumn(
            state = list,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
        ) {
            if (state.results.initialLoading) item("loading") { LoadingBox() }
            state.results.error?.let { message ->
                item("error") { ErrorNotice(message) { onQuery(state.query) } }
            }
            if (state.query.isNotBlank() && !state.results.initialLoading && state.results.error == null && state.results.items.isEmpty()) {
                item("empty") { EmptyNotice() }
            }
            items(state.results.items, key = { it.topic.id }) { item ->
                val excerpt = state.excerpts[item.topic.id]
                val withExcerpt = remember(item, excerpt) {
                    if (excerpt != null) item.copy(topic = item.topic.copy(excerpt = excerpt)) else item
                }
                TopicRow(
                    item = withExcerpt,
                    query = state.query,
                    onClick = {
                        focusManager.clearFocus()
                        onOpenTopic(item.topic)
                    },
                )
            }
            if (state.results.loadingMore) item("more") { LoadingBox() }
        }
    }

    if (showSort) {
        ModalBottomSheet(onDismissRequest = { showSort = false }) {
            Text(
                "搜索排序",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            SearchOrder.entries.forEach { order ->
                val selected = state.order == order
                ListItem(
                    headlineContent = { Text(order.label) },
                    leadingContent = { Icon(searchOrderIcon(order), contentDescription = null) },
                    trailingContent = if (selected) {
                        { Icon(Icons.Filled.Check, contentDescription = "已选", tint = MaterialTheme.colorScheme.primary) }
                    } else null,
                    colors = ListItemDefaults.colors(
                        containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    ),
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .clip(MaterialTheme.shapes.large)
                        .clickable {
                            onOrder(order)
                            showSort = false
                        },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun searchOrderIcon(order: SearchOrder): ImageVector = when (order) {
    SearchOrder.Relevance -> Icons.Outlined.TrackChanges
    SearchOrder.Latest -> Icons.Outlined.Schedule
    SearchOrder.Likes -> Icons.Outlined.ThumbUp
    SearchOrder.Views -> Icons.Outlined.Visibility
    SearchOrder.LatestTopic -> Icons.Outlined.ChatBubbleOutline
    SearchOrder.Read -> Icons.AutoMirrored.Outlined.MenuBook
}
