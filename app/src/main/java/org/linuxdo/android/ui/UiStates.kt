package org.linuxdo.android.ui

import org.linuxdo.android.BuildConfig
import org.linuxdo.android.data.Category
import org.linuxdo.android.data.DiscourseTagInfo
import org.linuxdo.android.data.DraftItem
import org.linuxdo.android.data.AttachedMedia
import org.linuxdo.android.data.HomeFeedType
import org.linuxdo.android.data.NotificationFilter
import org.linuxdo.android.data.Post
import org.linuxdo.android.data.ProfilePage
import org.linuxdo.android.data.SearchOrder
import org.linuxdo.android.data.TopicDetail
import org.linuxdo.android.data.TopicNotificationLevel
import org.linuxdo.android.net.NetworkPath
import org.linuxdo.android.service.ApkUpdateInstaller
import org.linuxdo.android.ui.screen.TopicListUiState

enum class Screen { Boot, Login, Verify, List }

data class UpdateInfo(
    val latestVersion: String,
    val title: String,
    val changelog: String,
    val downloadUrl: String,
    val releasePageUrl: String,
    val mandatory: Boolean,
    /** 发布清单里的可选校验和；为空表示不校验，只有 version.json 路径会带上它。 */
    val apkSha256: String? = null,
)

/** 应用内下载安装的阶段。 */
enum class ApkUpdateStage { Idle, Downloading, Verifying, AwaitingPermission, Installing, Failed }

data class UpdateUiState(
    val currentVersion: String = BuildConfig.VERSION_NAME.substringBefore("-"),
    val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    val checking: Boolean = false,
    val manualChecking: Boolean = false,
    val updateInfo: UpdateInfo? = null,
    val showDialog: Boolean = false,
    val statusMessage: String? = null,
    val apkStage: ApkUpdateStage = ApkUpdateStage.Idle,
    val apkDownloadedBytes: Long = 0L,
    /** 总字节数；[ApkUpdateInstaller.TOTAL_UNKNOWN] 表示服务端没给 Content-Length。 */
    val apkTotalBytes: Long = 0L,
    val apkError: String? = null,
)

data class LoginUiState(
    val loading: Boolean = false,
    val sendingCode: Boolean = false,
    val error: String? = null,
    val emailSent: Boolean = false,
    val secondFactorRequired: Boolean = false,
    val backupEnabled: Boolean = false,
    val showWebLoginDialog: Boolean = false,
    val isRiskOrCaptchaBlocked: Boolean = false,
    val showRiskControlDialog: Boolean = false,
    val webAuthLoading: Boolean = false,
    val webAuthMessage: String? = null,
    val savedLogin: String = "",
    val savedPassword: String = "",
    val rememberPassword: Boolean = false,
)
enum class ThemeMode(val label: String) { System("跟随系统"), Light("浅色"), Dark("深色") }
enum class DetailLoad { Topic, Next, Previous }

data class DetailUiState(
    val topic: TopicDetail? = null,
    val posts: List<Post> = emptyList(),
    val consumedIds: Set<Long> = emptySet(),
    val category: Category? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val replyCursors: Map<Long, Int> = emptyMap(),
    val completedReplies: Set<Long> = emptySet(),
    val loadingReplies: Set<Long> = emptySet(),
    val reactingPosts: Set<Long> = emptySet(),
    val reactionErrors: Map<Long, String> = emptyMap(),
    val emojiUrls: Map<String, String> = emptyMap(),
    val windowStartId: Long? = null,
    val failedLoad: DetailLoad = DetailLoad.Topic,
    /**
     * 刚发出去的楼层 id。界面据此滚过去并高亮,用完调 clearCreatedPost 清掉。
     * 放在这里而不是让界面自己猜"楼层数变多了":它和新楼层是同一次状态更新送达的,
     * 界面不会读到"编辑器已关、但列表还没更新"的中间态。
     */
    val createdPostId: Long? = null,
    /** 评论区是否倒序(最新一级主评论在前)。1 楼主贴永远置顶,不参与翻转。 */
    val descending: Boolean = false,
    val noticeMessage: String? = null,
    val flagDialogPost: Post? = null,
    val flagError: String? = null,
    val flaggedPosts: Set<Long> = emptySet(),
    val notificationLevelUpdating: Boolean = false,
    /**
     * 定位版本号。每次带 postNumber 进入话题时（无论是否命中缓存）都递增，
     * TopicDetailScreen 据此重置 located 标志并强制重新执行滚动定位，
     * 彻底消除"已读过的帖子点通知后停在旧缓存位置"的问题。
     */
    val locateVersion: Int = 0,
) {
    val notificationLevel: TopicNotificationLevel
        get() = TopicNotificationLevel.fromCode(topic?.details?.notificationLevel)

    private val windowStart: Int get() = topic?.postStream?.stream.orEmpty().indexOf(windowStartId).coerceAtLeast(0)
    val remaining: List<Long> get() {
        val stream = topic?.postStream?.stream.orEmpty()
        // 倒序下"加载更多"是往更早的楼层回溯,不受向下窗口起点的约束。
        return if (descending) stream.filterNot { it in consumedIds }
        else stream.drop(windowStart).filterNot { it in consumedIds }
    }
    /** 倒序时不提供"加载之前的楼层"缺口 —— 那和倒序的回溯加载是同一个方向,会互相抢楼。 */
    val previousIds: List<Long> get() = if (descending) emptyList()
        else topic?.postStream?.stream.orEmpty().take(windowStart).filterNot { it in consumedIds }
}

data class CategoriesUiState(
    val items: List<Category> = emptyList(),
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val order: List<Long> = emptyList(),
)

/** 编辑器要做的事。三种目标共用一个输入框,区别只在提交时调哪个接口。 */
sealed interface ComposerTarget {
    val topicId: Long

    /**
     * 回复整个话题。不带 reply_to_post_number,所以不挂在任何楼层下面,
     * 发出去的楼层直接排在话题末尾 —— 这是网页端"回复"按钮的行为。
     */
    data class NewReply(override val topicId: Long) : ComposerTarget

    /** 回复某一楼层,结果会挂到该楼层下面。 */
    data class ReplyTo(
        override val topicId: Long,
        val postNumber: Int,
        val username: String,
    ) : ComposerTarget

    /** 编辑自己的楼层。 */
    data class Edit(
        override val topicId: Long,
        val postId: Long,
        val postNumber: Int,
    ) : ComposerTarget
}

data class ComposerUiState(
    val target: ComposerTarget? = null,
    val draft: String = "",
    /** 编辑要先取回原文,这期间输入框不可编辑。 */
    val loadingDraft: Boolean = false,
    val submitting: Boolean = false,
    /** 正在上传附件。期间不许提交 —— 不然短码还没插进草稿就发出去了。 */
    val uploading: Boolean = false,
    val attachments: List<AttachedMedia> = emptyList(),
    val error: String? = null,
) {
    val open: Boolean get() = target != null
    val canSubmit: Boolean get() = (draft.isNotBlank() || attachments.isNotEmpty()) && !submitting && !loadingDraft && !uploading
}

data class TopicCreatorUiState(
    val open: Boolean = false,
    val draftKey: String = "new_topic",
    val sequence: Long = 0L,
    val action: String = "createTopic",
    val replyTopicId: Long? = null,
    val replyToPostNumber: Int? = null,
    val title: String = "",
    val raw: String = "",
    val categoryId: Int? = null,
    val tags: List<String> = emptyList(),
    val availableTags: List<DiscourseTagInfo> = emptyList(),
    val requiredTagGroupName: String? = null,
    val requiredTagMinCount: Int = 1,
    val loadingTags: Boolean = false,
    val attachments: List<AttachedMedia> = emptyList(),
    val savingDraft: Boolean = false,
    val submitting: Boolean = false,
    val uploading: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val savedSnapshot: String = "",
    val emojiUrls: Map<String, String> = emptyMap(),
) {
    val isReplyDraft: Boolean
        get() = action != "createTopic" && replyTopicId != null && replyTopicId > 0L

    val currentSnapshot: String
        get() = "${title.trim()}\u0000${raw.trim()}\u0000${categoryId ?: ""}\u0000${tags.joinToString(",")}\u0000${attachments.joinToString(",") { it.id }}"

    val hasUnsavedChanges: Boolean
        get() = (title.trim().isNotEmpty() || raw.trim().isNotEmpty() || attachments.isNotEmpty()) && currentSnapshot != savedSnapshot

    val canSubmit: Boolean
        get() = !submitting && !uploading && !savingDraft &&
            (raw.trim().isNotEmpty() || attachments.isNotEmpty()) && (isReplyDraft || title.trim().isNotEmpty())

    val canSaveDraft: Boolean
        get() = !submitting && !uploading && !savingDraft &&
            (raw.trim().isNotEmpty() || title.trim().isNotEmpty() || attachments.isNotEmpty())
}

data class DraftsUiState(
    val items: List<DraftItem> = emptyList(),
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val deletingKeys: Set<String> = emptySet(),
    val error: String? = null,
)

data class SearchUiState(
    val query: String = "",
    val results: TopicListUiState = TopicListUiState(initialLoading = false, hasMore = false),
    val excerpts: Map<Long, String> = emptyMap(),
    val order: SearchOrder = SearchOrder.Relevance,
)

data class ProfileUiState(
    val content: ProfilePage = ProfilePage(),
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    /** 这份数据拉完的时刻(elapsedRealtime),用来判断缓存有没有过期。 */
    val loadedAt: Long = 0L,
    /** 拉这份数据时的未读数。未读没涨说明列表大概率没变,可以接着用缓存。 */
    val unreadAtLoad: Int = 0,
)

data class UiState(
    val screen: Screen = Screen.Boot,
    val username: String? = null,
    val homeFeed: HomeFeedType = HomeFeedType.Latest,
    val latest: TopicListUiState = TopicListUiState(),
    val path: NetworkPath = NetworkPath.OKHTTP,
    val autoRefresh: Boolean = false,
    val cookieNames: List<String> = emptyList(),
    val cookieSavedAt: Long = 0L,
    val userAgent: String = "",
    val unreadCount: Int = 0,
    val unreadFilters: Set<NotificationFilter> = emptySet(),
    /** 各通知分类的未读条数。用于判断某个分类的缓存是否还新鲜。 */
    val unreadByFilter: Map<NotificationFilter, Int> = emptyMap(),
)
