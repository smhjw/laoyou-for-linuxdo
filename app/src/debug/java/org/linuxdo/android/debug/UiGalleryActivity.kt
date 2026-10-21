package org.linuxdo.android.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.linuxdo.android.data.Category
import org.linuxdo.android.data.DraftItem
import org.linuxdo.android.data.HomeFeedType
import org.linuxdo.android.data.NotificationFilter
import org.linuxdo.android.data.Post
import org.linuxdo.android.data.PostStream
import org.linuxdo.android.data.ProfileEntry
import org.linuxdo.android.data.ProfilePage
import org.linuxdo.android.data.ProfileSection
import org.linuxdo.android.data.Reaction
import org.linuxdo.android.data.Topic
import org.linuxdo.android.data.TopicDetail
import org.linuxdo.android.data.TopicListItem
import org.linuxdo.android.ui.CategoriesUiState
import org.linuxdo.android.ui.ComposerUiState
import org.linuxdo.android.ui.DetailUiState
import org.linuxdo.android.ui.DraftsUiState
import org.linuxdo.android.ui.LoginUiState
import org.linuxdo.android.ui.ProfileUiState
import org.linuxdo.android.ui.SearchUiState
import org.linuxdo.android.ui.ThemeMode
import org.linuxdo.android.ui.TopicCreatorUiState
import org.linuxdo.android.ui.UpdateInfo
import org.linuxdo.android.ui.ApkUpdateStage
import org.linuxdo.android.ui.design.AppTheme
import org.linuxdo.android.ui.nav.MainTab
import org.linuxdo.android.ui.nav.SettingsPage
import org.linuxdo.android.ui.nav.TabBar
import org.linuxdo.android.ui.screen.CategoryListScreen
import org.linuxdo.android.ui.screen.DraftsScreen
import org.linuxdo.android.ui.screen.LoginScreen
import org.linuxdo.android.ui.screen.ProfileScreen
import org.linuxdo.android.ui.screen.SearchScreen
import org.linuxdo.android.ui.screen.TopicCreatorSheet
import org.linuxdo.android.ui.screen.TopicDetailScreen
import org.linuxdo.android.ui.screen.TopicListScreen
import org.linuxdo.android.ui.screen.TopicListUiState
import org.linuxdo.android.ui.screen.UpdateDialog
import org.linuxdo.android.ui.screen.settings.SettingsScreen

/**
 * 界面画廊(仅 debug)。用示例数据渲染单个页面,方便在不登录的情况下检查视觉效果:
 *
 *     adb shell am start -n org.linuxdo.android/.debug.UiGalleryActivity --es screen list --ez dark false
 *
 * screen 可选:list detail categories search profile notifications settings settings-<page>
 * drafts login creator update。
 */
class UiGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val screen = intent.getStringExtra("screen") ?: "list"
        val dark = intent.getBooleanExtra("dark", false)
        val dynamic = intent.getBooleanExtra("dynamic", false)
        setContent {
            AppTheme(dark = dark, dynamicColor = dynamic) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .systemBarsPadding(),
                ) { Gallery(screen) }
            }
        }
    }
}

@Composable
private fun Gallery(screen: String) {
    when {
        screen == "list" -> WithTabs(MainTab.Latest) {
            TopicListScreen(
                title = "最新",
                state = TopicListUiState(items = Samples.topics, initialLoading = false, hasMore = true),
                onRefresh = {}, onLoadMore = {}, onOpenTopic = {},
                currentFeed = HomeFeedType.Latest, onFeedSelect = {},
                onCreateTopic = {},
            )
        }
        screen == "category-topics" -> TopicListScreen(
            title = "开发调优",
            state = TopicListUiState(items = Samples.topics, initialLoading = false, hasMore = false),
            onRefresh = {}, onLoadMore = {}, onOpenTopic = {}, onBack = {},
        )
        screen == "detail" -> TopicDetailScreen(
            title = Samples.detail.title,
            state = DetailUiState(
                topic = Samples.detail,
                posts = Samples.posts,
                consumedIds = Samples.posts.map { it.id }.toSet(),
                category = Samples.categories[1],
            ),
            onBack = {}, onRetry = {}, onLoadMore = {}, onLoadPrevious = {}, onUnknownTag = {},
            treeView = true, onLoadReplies = {}, onReact = { _, _ -> }, onBoost = { _, _ -> },
            onDeleteBoost = { _, _ -> }, onRefreshPost = {}, onSolution = {},
            composer = ComposerUiState(), onOpenComposer = {}, onDraftChange = {}, onSubmitComposer = {},
            onCloseComposer = {}, onDeletePost = {}, onReloadFromStart = {}, onCreatedPostShown = {},
            onAttach = {}, topicId = Samples.detail.id, signedIn = true,
        )
        screen == "categories" -> WithTabs(MainTab.Categories) {
            CategoryListScreen(
                state = CategoriesUiState(items = Samples.categories, loaded = true),
                onRetry = {}, onOpen = {},
            )
        }
        screen == "search" -> WithTabs(MainTab.Search) {
            SearchScreen(
                state = SearchUiState(
                    query = "Claude",
                    results = TopicListUiState(items = Samples.topics.take(3), initialLoading = false, hasMore = false),
                    excerpts = Samples.topics.associate { it.topic.id to "关于 <b>Claude</b> 的讨论摘要……" },
                ),
                onQuery = {}, onLoadMore = {}, onOpenTopic = {}, onOrder = {},
            )
        }
        screen == "profile" -> WithTabs(MainTab.Profile) {
            ProfileScreen(
                username = "hjw",
                states = mapOf(ProfileSection.Summary to ProfileUiState(content = Samples.summary, loaded = true)),
                onLoad = { _, _, _ -> }, onOpenTopic = { _, _, _ -> }, onDiagnostics = {}, onSettings = {},
                active = false, onOpenDrafts = {}, draftCount = "2",
            )
        }
        screen == "notifications" -> ProfileScreen(
            username = "hjw",
            states = mapOf(ProfileSection.Notifications to ProfileUiState(content = Samples.notifications, loaded = true)),
            onLoad = { _, _, _ -> }, onOpenTopic = { _, _, _ -> }, onDiagnostics = {}, onSettings = {},
            active = false, notificationOnly = true, onBack = {},
            notificationFilter = NotificationFilter.All, unreadFilters = setOf(NotificationFilter.All),
        )
        screen.startsWith("settings") -> SettingsScreen(
            treeView = true, onTreeView = {}, onBack = {}, themeMode = ThemeMode.System, onThemeMode = {},
            page = SettingsPage.entries.firstOrNull { "settings-" + it.name.lowercase() == screen },
        )
        screen == "drafts" -> DraftsScreen(
            state = DraftsUiState(items = Samples.drafts, loaded = true),
            categories = Samples.categories, onBack = {}, onRefresh = {}, onEditDraft = {}, onDeleteDraft = {},
        )
        screen == "login" -> LoginScreen(
            state = LoginUiState(),
            onPasswordLogin = { _, _, _, _, _ -> }, onSendEmailCode = {},
            onEmailCodeLogin = { _, _, _, _ -> }, onDismissError = {},
        )
        screen == "creator" -> TopicCreatorSheet(
            state = TopicCreatorUiState(open = true, title = "Material 3 重构进展", raw = "这是正文……", categoryId = 4),
            categories = Samples.categories,
            onTitleChange = {}, onRawChange = {}, onCategoryChange = {}, onTagsChange = {}, onAttach = {},
            onSaveDraft = {}, onSubmit = {}, onClose = {}, onClearNotice = {},
        )
        screen == "update" -> UpdateDialog(
            currentVersion = "1.3.1",
            info = UpdateInfo("1.4.0", "v1.4.0 版本更新", "1、全新 Material 3 界面\n2、性能优化", "", "", mandatory = false),
            stage = ApkUpdateStage.Idle, downloadedBytes = 0, totalBytes = 0, errorMessage = null,
            onStartDownload = {}, onRetryAfterPermission = {}, onCancelDownload = {}, onDismiss = {},
        )
    }
}

@Composable
private fun WithTabs(tab: MainTab, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { content() }
        TabBar(selected = tab, onSelect = {})
    }
}

private object Samples {
    val categories = listOf(
        Category(id = 4, name = "开发调优", slug = "develop", color = "32C3C3", topicCount = 52310, description = "包括但不限于编程、调优等技术交流"),
        Category(id = 11, name = "搞七捻三", slug = "gossip", color = "ED207B", topicCount = 98232, description = "闲聊吹水"),
        Category(id = 14, name = "资源荟萃", slug = "resource", color = "12A89D", topicCount = 30211),
        Category(id = 34, name = "人工智能", slug = "ai", color = "6A5ACD", topicCount = 41234),
        Category(id = 36, name = "福利羊毛", slug = "welfare", color = "F7941D", topicCount = 18213),
    )

    private val titles = listOf(
        "【分享】用 Kotlin 协程重写网络层后的一些心得",
        "大家日常都用什么输入法？",
        "Claude 新模型实测：长上下文表现如何",
        "求推荐一台适合开发的轻薄本",
        "开源一个 Android 端的 Discourse 客户端",
        "今天的羊毛：某云服务器限时免费",
    )

    val topics: List<TopicListItem> = titles.mapIndexed { i, title ->
        val category = categories[i % categories.size]
        TopicListItem(
            topic = Topic(
                id = 1000L + i, title = title, postsCount = 12 + i * 37, replyCount = 8 + i * 11,
                views = 1200 + i * 915, likeCount = 30 + i * 4, categoryId = category.id,
                createdAt = "2026-10-21T0${i}:30:00.000Z", lastPostedAt = "2026-10-21T10:0${i}:00.000Z",
                pinned = i == 0, unseen = i % 3 == 1, tags = if (i % 2 == 0) listOf("kotlin", "android") else emptyList(),
            ),
            authorUsername = listOf("neo", "alice", "bob", "carol", "dave", "eve")[i],
            authorName = null,
            avatarUrl = null,
            category = category,
            trustLevel = i % 5,
        )
    }

    val posts: List<Post> = listOf(
        Post(
            id = 1, username = "neo", postNumber = 1, createdAt = "2026-10-21T08:00:00.000Z", topicId = 2000,
            cooked = """
                <p>最近把客户端整体改成了 <strong>Material 3</strong> 风格，顺手把 ViewModel 拆成了多个控制器。</p>
                <blockquote><p>界面要像安卓，就该用安卓自己的设计语言。</p></blockquote>
                <p>主要改动：</p>
                <ul><li>动态取色</li><li>预测性返回</li><li>底部导航栏</li></ul>
                <pre><code>val scheme = dynamicLightColorScheme(context)</code></pre>
            """.trimIndent(),
            reactions = listOf(Reaction("heart", 12), Reaction("+1", 3)), replyCount = 2,
        ),
        Post(id = 2, username = "alice", postNumber = 2, createdAt = "2026-10-21T09:00:00.000Z", topicId = 2000,
            cooked = "<p>看起来不错！动态取色在 Android 12 以上效果很好。</p>", reactions = listOf(Reaction("heart", 2))),
        Post(id = 3, username = "bob", postNumber = 3, createdAt = "2026-10-21T09:30:00.000Z", topicId = 2000,
            replyToPostNumber = 2, cooked = "<p>同意，就是低版本要准备一套兜底配色。</p>"),
        Post(id = 4, username = "carol", postNumber = 4, createdAt = "2026-10-21T10:00:00.000Z", topicId = 2000,
            cooked = "<p>预测性返回的动画能分享一下实现吗？</p>", yours = true, canEdit = true, canDelete = true),
    )

    val detail = TopicDetail(
        id = 2000, title = "客户端界面全面改为 Material 3 风格", postsCount = 4, categoryId = 11,
        postStream = PostStream(posts = posts, stream = posts.map { it.id }),
        validReactions = listOf("heart", "+1", "laughing"),
    )

    val summary = ProfilePage(
        fields = listOf("获赞" to "1.2k", "话题" to "36", "回复" to "842", "访问天数" to "365", "阅读时长" to "120h", "我的收藏" to "18"),
    )

    val notifications = ProfilePage(
        entries = listOf(
            ProfileEntry("1", "alice 回复了你的帖子", "看起来不错！", topicId = 2000, unread = true, notificationType = 2, actor = "alice"),
            ProfileEntry("2", "bob 赞了你的帖子", "客户端界面全面改为 Material 3 风格", topicId = 2000, notificationType = 5, actor = "bob"),
            ProfileEntry("3", "carol 提到了你", "@hjw 预测性返回的动画能分享一下实现吗？", topicId = 2000, notificationType = 1, actor = "carol"),
        ),
    )

    val drafts = listOf(
        DraftItem(draftKey = "new_topic", title = "Material 3 重构进展", raw = "这是正文……", excerpt = "这是正文……", categoryId = 4, createdAt = "2026-10-21T08:00:00.000Z"),
        DraftItem(draftKey = "topic_2000", raw = "回复草稿内容", excerpt = "回复草稿内容", action = "reply", topicId = 2000, createdAt = "2026-10-20T08:00:00.000Z"),
    )
}
