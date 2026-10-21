package org.linuxdo.android.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 回顶请求由各 Tab 独立递增;保存消费位置,避免切回页面时再次回顶。 */
@Composable
internal fun ScrollToTopOnRequest(listState: LazyListState, request: Int) {
    var handled by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(request) {
        if (request > 0 && request != handled) {
            listState.scrollToItem(0)
            handled = request
        }
    }
}

/**
 * 页面骨架:Material 顶栏 + 列表。
 *
 * - 一级页面(无返回键):普通顶栏,向下滚动时收起、向上滚动立即出现。
 * - 二级页面且 [showLargeTitle]:中号顶栏,标题随滚动从大号收缩到常规尺寸。
 * - 其余:固定的普通顶栏,滚动后切换为抬升色。
 *
 * @param customTitle 自定义标题区(如分类的等级切换),设置后总是用普通顶栏。
 * @param trailing 顶栏右侧操作,放在 actions 槽里。
 * @param topBarBottom 紧贴顶栏下方、不随列表滚动的区域(如首页的 feed 标签)。
 * @param onRefresh 不为 null 时列表支持下拉刷新;[showRefreshIndicator] 为 false 时只刷新不画指示器
 *   (整页首次加载已有居中的进度圈,不要再叠一个)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onBack: (() -> Unit)? = null,
    showLargeTitle: Boolean = onBack != null,
    scrollToTopRequest: Int = 0,
    trailing: @Composable RowScope.() -> Unit = {},
    customTitle: (@Composable () -> Unit)? = null,
    topBarBottom: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    refreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
    showRefreshIndicator: Boolean = true,
    floatingOverlay: (@Composable BoxScope.() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    ScrollToTopOnRequest(listState, scrollToTopRequest)
    val large = showLargeTitle && customTitle == null
    val scrollBehavior = when {
        large -> TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        onBack == null -> TopAppBarDefaults.enterAlwaysScrollBehavior()
        else -> TopAppBarDefaults.pinnedScrollBehavior()
    }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
    ) {
        if (large) {
            MediumTopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onBack) },
                actions = trailing,
                windowInsets = WindowInsets(0),
                colors = appTopBarColors(),
                scrollBehavior = scrollBehavior,
            )
        } else {
            AppTopBar(title, Modifier, onBack, trailing, customTitle, scrollBehavior)
        }
        topBarBottom?.invoke()
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (onRefresh != null) {
                val pullState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = refreshing,
                    onRefresh = onRefresh,
                    state = pullState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        if (showRefreshIndicator) {
                            PullToRefreshDefaults.Indicator(
                                state = pullState,
                                isRefreshing = refreshing,
                                modifier = Modifier.align(Alignment.TopCenter),
                            )
                        }
                    },
                ) {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = contentPadding, content = content)
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = contentPadding, content = content)
            }
            floatingOverlay?.invoke(this)
        }
    }
}

/** 普通顶栏。用于不需要随列表折叠标题的页面(详情、搜索、草稿)。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    customTitle: (@Composable () -> Unit)? = null,
) = AppTopBar(title, modifier, onBack, trailing, customTitle, scrollBehavior = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    title: String,
    modifier: Modifier,
    onBack: (() -> Unit)?,
    trailing: @Composable RowScope.() -> Unit,
    customTitle: (@Composable () -> Unit)?,
    scrollBehavior: TopAppBarScrollBehavior?,
) {
    TopAppBar(
        title = {
            if (customTitle != null) customTitle()
            else Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        modifier = modifier,
        navigationIcon = { BackButton(onBack) },
        actions = trailing,
        windowInsets = WindowInsets(0),
        colors = appTopBarColors(),
        scrollBehavior = scrollBehavior,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun appTopBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.surface,
    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
)

@Composable
private fun BackButton(onBack: (() -> Unit)?) {
    if (onBack == null) return
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
    }
}
