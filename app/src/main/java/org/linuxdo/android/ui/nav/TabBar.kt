package org.linuxdo.android.ui.nav

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

enum class MainTab(
    val label: String,
    val root: Route,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Latest("最新", Route.TopicList, Icons.Outlined.Forum, Icons.Filled.Forum),
    Categories("分类", Route.CategoryList, Icons.Outlined.GridView, Icons.Filled.GridView),
    Search("搜索", Route.Search, Icons.Outlined.Search, Icons.Filled.Search),
    Profile("我的", Route.Profile, Icons.Outlined.AccountCircle, Icons.Filled.AccountCircle),
}

/** 底部导航栏。首页的标签跟随当前选中的 feed(最新 / 热门 / 未读…)。 */
@Composable
fun TabBar(
    selected: MainTab,
    homeFeedLabel: String = "最新",
    onSelect: (MainTab) -> Unit,
) {
    NavigationBar(windowInsets = WindowInsets(0)) {
        MainTab.entries.forEach { tab ->
            val isSelected = tab == selected
            val label = if (tab == MainTab.Latest) homeFeedLabel else tab.label
            NavigationBarItem(
                selected = isSelected,
                onClick = { onSelect(tab) },
                icon = { Icon(if (isSelected) tab.selectedIcon else tab.icon, contentDescription = null) },
                label = { Text(label) },
            )
        }
    }
}
