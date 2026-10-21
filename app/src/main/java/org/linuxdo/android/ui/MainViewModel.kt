package org.linuxdo.android.ui

import android.app.Application
import android.widget.Toast
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.linuxdo.android.App
import org.linuxdo.android.Config
import org.linuxdo.android.data.AutoRefreshInterval
import org.linuxdo.android.data.Category
import org.linuxdo.android.data.CustomSection
import org.linuxdo.android.data.HomeFeedType
import org.linuxdo.android.data.SearchOrder
import org.linuxdo.android.data.TopicListItem
import org.linuxdo.android.data.TopicNotificationLevel
import org.linuxdo.android.data.TopicRepository
import org.linuxdo.android.data.TopicSortOrder
import org.linuxdo.android.net.HttpStatusException
import org.linuxdo.android.net.NeedsInteractiveVerification
import org.linuxdo.android.net.NetworkPath
import org.linuxdo.android.ui.controller.AuthController
import org.linuxdo.android.ui.controller.ComposerController
import org.linuxdo.android.ui.controller.DohController
import org.linuxdo.android.ui.controller.JobRegistry
import org.linuxdo.android.ui.controller.NotificationsController
import org.linuxdo.android.ui.controller.ReadingTracker
import org.linuxdo.android.ui.controller.SettingsController
import org.linuxdo.android.ui.controller.TopicDetailController
import org.linuxdo.android.ui.controller.UpdateController
import org.linuxdo.android.ui.screen.TopicListUiState

/**
 * 应用级 ViewModel:会话与启动流程、人机验证、首页 / 分类 / 用户 / 搜索各列表,
 * 以及要同时改动多张列表的操作(已读、通知级别、分类回填)。
 *
 * 各功能的状态与操作在 [org.linuxdo.android.ui.controller] 下的控制器里,这里只负责创建和串联。
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as App).container
    private val store = container.sessionStore
    private val browser = container.browser
    val diagnostics = container.diagnostics

    private val initialHomeFeed = HomeFeedType.fromName(store.homeFeed())
    private val _feedStates = MutableStateFlow<Map<HomeFeedType, TopicListUiState>>(emptyMap())
    val feedStates = _feedStates.asStateFlow()
    private val _topicSortOrder = MutableStateFlow(store.getTopicSortOrder())
    val topicSortOrder: StateFlow<TopicSortOrder> = _topicSortOrder.asStateFlow()
    private val _topicSortAscending = MutableStateFlow(store.getTopicSortAscending())
    val topicSortAscending: StateFlow<Boolean> = _topicSortAscending.asStateFlow()
    private val _state = MutableStateFlow(UiState(userAgent = container.userAgent, homeFeed = initialHomeFeed))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val repository = TopicRepository(
        transportProvider = { container.transport(_state.value.path) },
        diagnostics = diagnostics,
        reading = container.reading,
        sessionStore = store,
        fallbackTransportProvider = { container.transport(NetworkPath.WEBVIEW) },
    )

    private val jobs = JobRegistry()
    private var bootJob: Job? = null
    private var autoJob: Job? = null
    private var autoProbeJob: Job? = null
    private var searchJob: Job? = null
    private var verifying = false
    private var resumeAfterVerification: (() -> Unit)? = null
    private val loadingFeeds = java.util.concurrent.ConcurrentHashMap.newKeySet<HomeFeedType>()
    private var activeCategory: Pair<Int, String>? = null

    private val _categories = MutableStateFlow(CategoriesUiState(order = store.loadCategoryOrder()))
    val categories = _categories.asStateFlow()
    private val _customSection = MutableStateFlow(store.loadCustomSection())
    val customSection = _customSection.asStateFlow()
    private val _categoryTopics = MutableStateFlow<Map<Int, TopicListUiState>>(emptyMap())
    val categoryTopics = _categoryTopics.asStateFlow()
    private val _userTopicStates = MutableStateFlow<Map<String, TopicListUiState>>(emptyMap())
    val userTopicStates = _userTopicStates.asStateFlow()
    private val _search = MutableStateFlow(SearchUiState())
    val search = _search.asStateFlow()

    // ── 功能控制器 ───────────────────────────────────────────────────────────────

    val updates: UpdateController = UpdateController(application, viewModelScope, container)
    val doh: DohController = DohController(viewModelScope, container)
    val settings: SettingsController = SettingsController(
        app = application,
        container = container,
        onAutoRefreshChanged = ::restartAutoProbeLoop,
        onNotificationScopeChanged = { notifications.refreshUnread(force = true, reloadNotifications = false) },
    )
    val auth: AuthController = AuthController(
        app = application,
        scope = viewModelScope,
        container = container,
        isDarkMode = settings::isDarkMode,
        onSignedIn = { enterList ->
            if (enterList) _state.update { it.copy(screen = Screen.List) }
            boot()
        },
    )
    val reading: ReadingTracker = ReadingTracker(
        scope = viewModelScope,
        repository = repository,
        reading = container.reading,
        diagnostics = diagnostics,
        hasPosts = { topicId -> !detail.state.value[topicId]?.posts.isNullOrEmpty() },
        isBrowsing = { _state.value.screen == Screen.List },
    )
    val detail: TopicDetailController = TopicDetailController(
        scope = viewModelScope,
        repository = repository,
        jobs = jobs,
        onPageFailure = ::handlePageFailure,
        reading = reading,
        markSeen = { topicId, postsCount -> markTopicSeen(topicId, postsCount ?: knownPostsCount(topicId)) },
        treeView = { settings.treeView.value },
        username = { _state.value.username },
        composerSubmitting = { editor.composer.value.submitting },
    )
    val editor: ComposerController = ComposerController(
        app = application,
        scope = viewModelScope,
        repository = repository,
        jobs = jobs,
        onPageFailure = ::handlePageFailure,
        details = detail,
        username = { _state.value.username },
        ensureCategories = { if (!_categories.value.loaded && !_categories.value.loading) loadCategories() },
        refreshLatest = ::refreshLatest,
    )
    val notifications: NotificationsController = NotificationsController(
        app = application,
        scope = viewModelScope,
        container = container,
        repository = repository,
        jobs = jobs,
        onPageFailure = ::handlePageFailure,
        appState = _state,
        feedsLoading = { loadingFeeds.isNotEmpty() },
        loadDrafts = { refresh -> editor.loadDrafts(refresh = refresh, silent = true) },
    )

    init {
        settings.start()
        boot()
        restartAutoProbeLoop()
        updates.checkForUpdates(manual = false)
    }

    // ── 新话题探测 ───────────────────────────────────────────────────────────────

    fun setActiveCategory(categoryId: Int?, slug: String? = null) {
        activeCategory = if (categoryId != null && !slug.isNullOrBlank()) categoryId to slug else null
    }

    private fun restartAutoProbeLoop() {
        autoProbeJob?.cancel()
        val interval = store.getAutoRefreshInterval()
        if (interval == AutoRefreshInterval.OFF) return
        autoProbeJob = viewModelScope.launch {
            while (isActive) {
                delay(interval.seconds * 1000L)
                probeIncomingTopics(_state.value.homeFeed, categoryId = activeCategory?.first)
            }
        }
    }

    fun probeIncomingTopics(feed: HomeFeedType, categoryId: Int? = null) {
        if (!store.isAutoRefreshIncomingEnabled()) return
        viewModelScope.launch {
            try {
                val sortOrder = _topicSortOrder.value.apiValue
                val sortAscending = _topicSortAscending.value.takeIf { sortOrder != null }
                if (categoryId != null) {
                    val currentCategoryState = _categoryTopics.value[categoryId] ?: return@launch
                    if (currentCategoryState.items.isEmpty()) return@launch
                    val category = _categories.value.items.firstOrNull { it.id == categoryId }
                    val slug = category?.slug ?: activeCategory?.takeIf { it.first == categoryId }?.second ?: "c"
                    val freshPage = repository.fetchCategoryTopics(slug, categoryId, 0, present = false, order = sortOrder, ascending = sortAscending)
                    val incomingIds = incomingTopicIds(currentCategoryState.items, freshPage.items)
                    if (incomingIds.isNotEmpty()) {
                        _categoryTopics.update { current ->
                            val existing = current[categoryId] ?: return@update current
                            current + (categoryId to existing.copy(pendingPage = freshPage, incomingTopicIds = incomingIds))
                        }
                    }
                } else {
                    val currentFeedState = _feedStates.value[feed] ?: return@launch
                    if (currentFeedState.items.isEmpty()) return@launch
                    val freshPage = repository.fetchFeed(feed, 0, awaitCategories = false, present = false, order = sortOrder, ascending = sortAscending)
                    val incomingIds = incomingTopicIds(currentFeedState.items, freshPage.items)
                    if (incomingIds.isNotEmpty()) {
                        _feedStates.update { current ->
                            val existing = current[feed] ?: return@update current
                            current + (feed to existing.copy(pendingPage = freshPage, incomingTopicIds = incomingIds))
                        }
                        if (_state.value.homeFeed == feed) {
                            _state.update { it.copy(latest = it.latest.copy(pendingPage = freshPage, incomingTopicIds = incomingIds)) }
                        }
                    }
                }
            } catch (ignored: Exception) {
                // 静默忽略所有探测错误与 CF 挑战,绝不弹窗
            }
        }
    }

    /** 新出现的话题,加上排到旧列表首个非置顶话题之前、被新回复顶上来的旧话题。 */
    private fun incomingTopicIds(current: List<TopicListItem>, fresh: List<TopicListItem>): Set<Long> {
        val currentIds = current.map { it.topic.id }.toSet()
        val firstOldNonPinnedId = current.firstOrNull { !it.topic.pinned }?.topic?.id
        val firstOldIdx = if (firstOldNonPinnedId != null) fresh.indexOfFirst { it.topic.id == firstOldNonPinnedId } else -1
        return fresh.filterIndexed { idx, item ->
            item.topic.id !in currentIds || (firstOldIdx > 0 && idx < firstOldIdx && item.topic.id in currentIds)
        }.map { it.topic.id }.toSet()
    }

    fun applyIncomingTopics(feed: HomeFeedType, categoryId: Int? = null) {
        if (categoryId != null) {
            val current = _categoryTopics.value[categoryId] ?: return
            val pending = current.pendingPage ?: return
            val highlightJobKey = "incoming-highlight:category:$categoryId"
            jobs[highlightJobKey]?.cancel()
            _categoryTopics.update { map ->
                map + (categoryId to current.copy(
                    items = (pending.items + current.items).distinctBy { it.topic.id },
                    pendingPage = null,
                    incomingTopicIds = emptySet(),
                    highlightedTopicIds = pending.items.map { it.topic.id }.toSet(),
                ))
            }
            jobs[highlightJobKey] = viewModelScope.launch {
                delay(INCOMING_HIGHLIGHT_MS)
                _categoryTopics.update { map ->
                    val c = map[categoryId] ?: return@update map
                    map + (categoryId to c.copy(highlightedTopicIds = emptySet()))
                }
            }
        } else {
            val current = _feedStates.value[feed] ?: _state.value.latest
            val pending = current.pendingPage ?: return
            val highlightJobKey = "incoming-highlight:feed:${feed.name}"
            jobs[highlightJobKey]?.cancel()
            // 高亮表示本轮刷新覆盖的整批话题,不能复用仅用于提示计数的"新动态"子集。
            val updatedState = current.copy(
                items = (pending.items + current.items).distinctBy { it.topic.id },
                pendingPage = null,
                incomingTopicIds = emptySet(),
                highlightedTopicIds = pending.items.map { it.topic.id }.toSet(),
            )
            _feedStates.update { it + (feed to updatedState) }
            if (_state.value.homeFeed == feed) _state.update { it.copy(latest = updatedState) }
            jobs[highlightJobKey] = viewModelScope.launch {
                delay(INCOMING_HIGHLIGHT_MS)
                _feedStates.update { currentMap ->
                    val f = currentMap[feed] ?: return@update currentMap
                    currentMap + (feed to f.copy(highlightedTopicIds = emptySet()))
                }
                if (_state.value.homeFeed == feed) {
                    _state.update { it.copy(latest = it.latest.copy(highlightedTopicIds = emptySet())) }
                }
            }
        }
    }

    // ── 已读与通知级别(要同时改动多张列表) ───────────────────────────────────

    private fun knownPostsCount(topicId: Long): Int {
        detail.state.value[topicId]?.let { d ->
            val fromDetail = maxOf(
                d.topic?.postsCount ?: 0,
                d.topic?.postStream?.stream?.size ?: 0,
                d.posts.maxOfOrNull { it.postNumber } ?: 0,
            )
            if (fromDetail > 0) return fromDetail
        }
        val lists = listOf(_state.value.latest) + _feedStates.value.values + _categoryTopics.value.values + _search.value.results
        return lists.firstNotNullOfOrNull { list ->
            list.items.firstOrNull { it.topic.id == topicId }?.topic?.postsCount?.takeIf { it > 0 }
        } ?: 0
    }

    /**
     * 本地记下"这帖看过了"(连同当前总楼层数),就地把普通列表里的未读蓝点摘掉,
     * 并将该帖从首页「未读」列表中就地移除。
     *
     * 纯净模式下服务端压根不知道我们读过,所以不能等 unseen 从服务端变化 ——
     * 只有本地这份持久化集合能保证下拉刷新、切分类、重启后蓝点与已读帖都不再反弹。
     */
    private fun markTopicSeen(topicId: Long, postsCount: Int) {
        val changed = container.reading.markSeen(topicId, postsCount)
        fun clearUnseen(list: TopicListUiState): TopicListUiState {
            val patched = list.items.map { item ->
                if (item.topic.id == topicId && item.topic.unseen) item.copy(topic = item.topic.copy(unseen = false)) else item
            }
            return if (patched != list.items) list.copy(items = patched) else list
        }
        fun removeFromUnread(list: TopicListUiState): TopicListUiState {
            val filtered = list.items.filterNot { it.topic.id == topicId }
            return if (filtered.size != list.items.size) list.copy(items = filtered) else list
        }
        _feedStates.update { states ->
            states.mapValues { (feed, list) -> if (feed == HomeFeedType.Unread) removeFromUnread(list) else clearUnseen(list) }
        }
        _state.update { state ->
            val updatedLatest = if (state.homeFeed == HomeFeedType.Unread) removeFromUnread(state.latest) else clearUnseen(state.latest)
            if (updatedLatest != state.latest) state.copy(latest = updatedLatest) else state
        }
        if (!changed) return
        _categoryTopics.update { states -> states.mapValues { (_, list) -> clearUnseen(list) } }
        _search.update { it.copy(results = clearUnseen(it.results)) }
    }

    fun setTopicNotificationLevel(topicId: Long, level: TopicNotificationLevel) {
        if (_state.value.username == null || jobs["notification:$topicId"]?.isActive == true) return
        val detailLevel = detail.state.value[topicId]?.topic?.details?.notificationLevel
        val snapshot = NotificationLevelSnapshot(detailLevel, _state.value.homeFeed, _state.value.latest, _feedStates.value,
            _categoryTopics.value, _userTopicStates.value, _search.value.results)
        val items = _feedStates.value.values.flatMap { it.items } + _state.value.latest.items +
            _categoryTopics.value.values.flatMap { it.items } + _userTopicStates.value.values.flatMap { it.items } +
            _search.value.results.items
        val previousLevel = store.getTopicNotificationLevel(topicId)?.let(TopicNotificationLevel::fromCode)
            ?: items.firstOrNull { it.topic.id == topicId }?.notificationLevel
        if (previousLevel == level && (detailLevel == null || detailLevel == level.code) &&
            items.filter { it.topic.id == topicId }.all { it.notificationLevel == level }) return
        updateNotificationLevel(topicId, level, changing = true)
        jobs["notification:$topicId"] = viewModelScope.launch {
            var confirmed = false
            try {
                repository.setTopicNotificationLevel(topicId, level.code)
                confirmed = true
                detail.setNoticeMessage(topicId, "通知级别已设为「${level.title}」")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Toast.makeText(getApplication(), error.message ?: "通知级别设置失败，已恢复原状态", Toast.LENGTH_LONG).show()
                handlePageFailure(error) {}
            } finally {
                if (confirmed) {
                    updateNotificationLevel(topicId, level, changing = false)
                    if (level == TopicNotificationLevel.Muted) removeMutedTopicFromLatest(topicId)
                } else {
                    restoreNotificationLevel(topicId, snapshot)
                }
            }
        }
    }

    private data class NotificationLevelSnapshot(
        val detailLevel: Int?,
        val homeFeed: HomeFeedType,
        val latest: TopicListUiState,
        val feeds: Map<HomeFeedType, TopicListUiState>,
        val categories: Map<Int, TopicListUiState>,
        val userTopics: Map<String, TopicListUiState>,
        val search: TopicListUiState,
    )

    private fun restoreNotificationLevel(topicId: Long, snapshot: NotificationLevelSnapshot) {
        fun restoreList(current: TopicListUiState, previous: TopicListUiState?): TopicListUiState {
            fun restoreItems(items: List<TopicListItem>, originals: List<TopicListItem>): List<TopicListItem> {
                val original = originals.firstOrNull { it.topic.id == topicId }
                return items.map { item ->
                    if (item.topic.id != topicId || !item.notificationLevelUpdating) item
                    else item.copy(
                        topic = item.topic.copy(notificationLevel = original?.topic?.notificationLevel),
                        notificationLevel = original?.notificationLevel,
                        notificationLevelUpdating = false,
                    )
                }
            }
            return current.copy(
                items = restoreItems(current.items, previous?.items.orEmpty()),
                pendingPage = current.pendingPage?.let { page ->
                    page.copy(items = restoreItems(page.items, previous?.pendingPage?.items.orEmpty()))
                },
            )
        }
        detail.flow.update { states -> states[topicId]?.let { current ->
            states + (topicId to current.copy(
                topic = current.topic?.let { it.copy(details = it.details.copy(notificationLevel = snapshot.detailLevel ?: 1)) },
                notificationLevelUpdating = false,
            ))
        } ?: states }
        _feedStates.update { feeds -> feeds.mapValues { restoreList(it.value, snapshot.feeds[it.key]) } }
        _state.update { current -> current.copy(latest = restoreList(current.latest,
            if (current.homeFeed == snapshot.homeFeed) snapshot.latest else snapshot.feeds[current.homeFeed])) }
        _categoryTopics.update { categories -> categories.mapValues { restoreList(it.value, snapshot.categories[it.key]) } }
        _userTopicStates.update { users -> users.mapValues { restoreList(it.value, snapshot.userTopics[it.key]) } }
        _search.update { it.copy(results = restoreList(it.results, snapshot.search)) }
    }

    private fun updateNotificationLevel(topicId: Long, level: TopicNotificationLevel?, changing: Boolean) {
        fun updateList(list: TopicListUiState): TopicListUiState {
            fun updateItems(items: List<TopicListItem>) = items.map { item ->
                if (item.topic.id == topicId) item.copy(
                    topic = item.topic.copy(notificationLevel = level?.code),
                    notificationLevel = level,
                    notificationLevelUpdating = changing,
                ) else item
            }
            return list.copy(items = updateItems(list.items),
                pendingPage = list.pendingPage?.let { it.copy(items = updateItems(it.items)) })
        }
        detail.flow.update { states ->
            val current = states[topicId] ?: return@update states
            val currentTopic = current.topic ?: return@update states
            val updatedTopic = currentTopic.copy(details = currentTopic.details.copy(notificationLevel = level?.code ?: 1))
            states + (topicId to current.copy(topic = updatedTopic, notificationLevelUpdating = changing))
        }
        _feedStates.update { feeds -> feeds.mapValues { updateList(it.value) } }
        _state.update { it.copy(latest = updateList(it.latest)) }
        _categoryTopics.update { categories -> categories.mapValues { updateList(it.value) } }
        _userTopicStates.update { users -> users.mapValues { updateList(it.value) } }
        _search.update { it.copy(results = updateList(it.results)) }
    }

    private fun removeMutedTopicFromLatest(topicId: Long) {
        fun removeTopic(list: TopicListUiState) = list.copy(
            items = list.items.filterNot { it.topic.id == topicId },
            pendingPage = list.pendingPage?.let { page -> page.copy(items = page.items.filterNot { it.topic.id == topicId }) },
            incomingTopicIds = list.incomingTopicIds - topicId,
            highlightedTopicIds = list.highlightedTopicIds - topicId,
        )
        _feedStates.update { feeds -> feeds[HomeFeedType.Latest]?.let { feeds + (HomeFeedType.Latest to removeTopic(it)) } ?: feeds }
        if (_state.value.homeFeed == HomeFeedType.Latest) _state.update { it.copy(latest = removeTopic(it.latest)) }
    }

    // ── 分类与自定义版块 ─────────────────────────────────────────────────────────

    fun saveCategoryOrder(ids: List<Long>) {
        store.saveCategoryOrder(ids)
        _categories.update { it.copy(order = store.loadCategoryOrder()) }
    }

    fun resetCategoryOrder() {
        store.resetCategoryOrder()
        _categories.update { it.copy(order = emptyList()) }
    }

    fun saveCustomSection(section: CustomSection) {
        val oldServerLinkIds = _customSection.value?.links?.mapNotNull { it.serverId }.orEmpty()
        store.saveCustomSection(section)
        _customSection.value = section
        // 异步同步到 Discourse 服务端(与 PC 端打通)
        viewModelScope.launch {
            try {
                val updated = repository.saveCustomSidebarSection(section, oldServerLinkIds)
                store.saveCustomSection(updated)
                _customSection.value = updated
            } catch (e: Exception) {
                diagnostics.log("同步自定义版块至服务端失败: ${e.message}")
            }
        }
    }

    fun deleteCustomSection() {
        val serverId = _customSection.value?.serverId
        store.saveCustomSection(null)
        _customSection.value = null
        if (serverId != null && serverId > 0L) {
            viewModelScope.launch {
                try {
                    repository.deleteCustomSidebarSection(serverId)
                } catch (e: Exception) {
                    diagnostics.log("服务端删除自定义版块失败: ${e.message}")
                }
            }
        }
    }

    /**
     * 与服务端同步自定义版块:PC 端有配置就拉下来;
     * PC 端已删除且本地版块绑定过 serverId,则同步清理本地。
     */
    private fun syncCustomSectionFromServer() {
        viewModelScope.launch {
            try {
                val remoteSection = repository.fetchCustomSidebarSection()
                if (remoteSection != null) {
                    store.saveCustomSection(remoteSection)
                    _customSection.value = remoteSection
                } else {
                    val current = _customSection.value
                    if (current?.serverId != null && current.serverId > 0L) {
                        store.saveCustomSection(null)
                        _customSection.value = null
                    }
                }
            } catch (e: Exception) {
                diagnostics.log("同步远端自定义版块失败: ${e.message}")
            }
        }
    }

    fun loadCategories(retry: Boolean = false) {
        syncCustomSectionFromServer()
        val current = _categories.value
        if (current.loading || (current.loaded && !retry)) return
        jobs["categories"] = viewModelScope.launch {
            _categories.value = current.copy(loading = true, error = null)
            try {
                _categories.value = CategoriesUiState(items = repository.fetchCategories(), loaded = true, order = store.loadCategoryOrder())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _categories.value = current.copy(error = error.message ?: "加载失败")
                handlePageFailure(error) { loadCategories(retry = true) }
            } finally {
                _categories.update { it.copy(loading = false) }
            }
        }
    }

    // ── 列表 ─────────────────────────────────────────────────────────────────────

    fun setTopicSort(order: TopicSortOrder, currentCategoryId: Int? = null, currentCategorySlug: String? = null) {
        val currentOrder = _topicSortOrder.value
        val currentAscending = _topicSortAscending.value
        // 再点一次同一种排序就翻转升降序;换排序方式时回到降序。
        val newAscending = if (order != TopicSortOrder.Default && order == currentOrder) !currentAscending else false
        if (order == currentOrder && newAscending == currentAscending) return
        _topicSortOrder.value = order
        _topicSortAscending.value = newAscending
        store.setTopicSortOrder(order)
        store.setTopicSortAscending(newAscending)

        jobs.cancelWhere { it.startsWith("category:") || it.startsWith("feed:") || it.startsWith("bookmark:") || it.startsWith("created:") }
        _feedStates.value = emptyMap()
        _categoryTopics.value = emptyMap()
        _userTopicStates.value = emptyMap()
        if (currentCategoryId != null && !currentCategorySlug.isNullOrBlank()) {
            loadCategory(currentCategoryId, currentCategorySlug, refresh = true)
        }
        refreshLatest()
    }

    fun loadCategory(id: Int, slug: String, refresh: Boolean = false, more: Boolean = false) {
        val current = _categoryTopics.value[id]
        if (jobs["category:$id"]?.isActive == true) return
        if (!refresh && !more && current != null) return
        val previous = current ?: TopicListUiState()
        if (more && !previous.hasMore) return
        val page = if (more) previous.page + 1 else 0
        jobs["category:$id"] = viewModelScope.launch {
            _categoryTopics.update { it + (id to previous.copy(
                refreshing = refresh, loadingMore = more, initialLoading = previous.items.isEmpty(), error = null,
            )) }
            try {
                val sortOrder = _topicSortOrder.value.apiValue
                val sortAscending = _topicSortAscending.value.takeIf { sortOrder != null }
                val result = repository.fetchCategoryTopics(slug, id, page, order = sortOrder, ascending = sortAscending)
                val items = if (more) (previous.items + result.items).distinctBy { it.topic.id } else result.items
                _categoryTopics.update { it + (id to TopicListUiState(
                    items = items,
                    page = page,
                    hasMore = result.hasMore && (!more || items.size > previous.items.size),
                    initialLoading = false,
                    pendingPage = if (page == 0) null else previous.pendingPage,
                    incomingTopicIds = if (page == 0) emptySet() else previous.incomingTopicIds,
                )) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _categoryTopics.update { it + (id to previous.copy(initialLoading = false, error = error.message ?: "加载失败")) }
                handlePageFailure(error) { loadCategory(id, slug, refresh = !more, more = more) }
            } finally {
                _categoryTopics.update { states -> states[id]?.let {
                    states + (id to it.copy(initialLoading = false, refreshing = false, loadingMore = false))
                } ?: states }
            }
        }
    }

    fun loadUserTopics(username: String, isBookmark: Boolean, refresh: Boolean = false, more: Boolean = false) {
        val key = "${if (isBookmark) "bookmark" else "created"}:$username"
        val current = _userTopicStates.value[key]
        if (jobs[key]?.isActive == true) return
        if (!refresh && !more && current != null) return
        val previous = current ?: TopicListUiState()
        if (more && !previous.hasMore) return
        val page = if (more) previous.page + 1 else 0
        jobs[key] = viewModelScope.launch {
            _userTopicStates.update { it + (key to previous.copy(
                refreshing = refresh, loadingMore = more, initialLoading = previous.items.isEmpty(), error = null,
            )) }
            try {
                val result = if (isBookmark) repository.fetchUserBookmarks(username, page) else repository.fetchUserCreatedTopics(username, page)
                val items = if (more) (previous.items + result.items).distinctBy { it.topic.id } else result.items
                _userTopicStates.update { it + (key to TopicListUiState(items, page,
                    result.hasMore && (!more || items.size > previous.items.size), initialLoading = false)) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _userTopicStates.update { it + (key to previous.copy(initialLoading = false, error = error.message ?: "加载失败")) }
                handlePageFailure(error) { loadUserTopics(username, isBookmark, refresh = !more, more = more) }
            } finally {
                _userTopicStates.update { states -> states[key]?.let {
                    states + (key to it.copy(initialLoading = false, refreshing = false, loadingMore = false))
                } ?: states }
            }
        }
    }

    fun updateSearch(query: String) {
        searchJob?.cancel()
        val trimmed = query.trim()
        _search.value = SearchUiState(query, TopicListUiState(initialLoading = trimmed.isNotEmpty(), hasMore = false), order = _search.value.order)
        if (trimmed.isEmpty()) return
        searchJob = viewModelScope.launch {
            delay(500)
            performSearch(trimmed, 1)
        }
    }

    fun setSearchOrder(order: SearchOrder) {
        if (_search.value.order == order) return
        _search.update { it.copy(order = order) }
        updateSearch(_search.value.query)
    }

    fun loadMoreSearch() {
        val current = _search.value
        if (searchJob?.isActive == true || !current.results.hasMore) return
        searchJob = viewModelScope.launch { performSearch(current.query.trim(), current.results.page + 1) }
    }

    private suspend fun performSearch(query: String, page: Int) {
        val previous = _search.value
        _search.update { it.copy(results = it.results.copy(initialLoading = page == 1, loadingMore = page > 1, error = null)) }
        try {
            val result = repository.search(query, page, previous.order)
            val items = if (page == 1) result.items else (previous.results.items + result.items).distinctBy { it.topic.id }
            _search.update { it.copy(
                results = TopicListUiState(items, page, result.hasMore && (page == 1 || items.size > previous.results.items.size), initialLoading = false),
                excerpts = if (page == 1) result.excerpts else previous.excerpts + result.excerpts,
            ) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            _search.update { it.copy(results = it.results.copy(initialLoading = false, loadingMore = false, error = error.message ?: "搜索失败")) }
            handlePageFailure(error) { updateSearch(_search.value.query) }
        }
    }

    fun switchHomeFeed(feed: HomeFeedType) {
        if (_state.value.homeFeed == feed) return
        val cached = _feedStates.value[feed]
        _state.update { it.copy(homeFeed = feed, latest = cached ?: TopicListUiState(initialLoading = true)) }
        store.saveHomeFeed(feed.name)
        if (cached == null || (cached.items.isEmpty() && !cached.loadingMore && !cached.refreshing)) {
            viewModelScope.launch { loadFeed(feed, page = 0, pullRefresh = false) }
        }
    }

    fun refreshLatest() {
        val feed = _state.value.homeFeed
        viewModelScope.launch { loadFeed(feed, page = 0, pullRefresh = true) }
    }

    fun retryLatest() {
        val feed = _state.value.homeFeed
        viewModelScope.launch { loadFeed(feed, page = 0, pullRefresh = false) }
    }

    fun loadMoreLatest() {
        val feed = _state.value.homeFeed
        val currentPage = _feedStates.value[feed]?.page ?: _state.value.latest.page
        viewModelScope.launch { loadFeed(feed, page = currentPage + 1, pullRefresh = false) }
    }

    private suspend fun loadFeed(feed: HomeFeedType, page: Int, pullRefresh: Boolean) {
        val current = _feedStates.value[feed] ?: _state.value.latest
        if (!loadingFeeds.add(feed) || current.refreshing || current.loadingMore) {
            // 已有加载在途,不重复发请求。但下拉刷新不能静默吞掉:下拉指示器要等
            // refreshing 由 true 回落才收起,直接 return 会让它一直转。
            // 让这次下拉跟随在途加载走完一次完整跳变(带超时兜底,避免无限等待)。
            if (pullRefresh && page == 0 && !current.refreshing) {
                setFeedState(feed, current.copy(refreshing = true))
                var waitedMs = 0L
                while (loadingFeeds.contains(feed) && waitedMs < FEED_REFRESH_JOIN_TIMEOUT_MS) {
                    delay(50)
                    waitedMs += 50
                }
                setFeedState(feed, (_feedStates.value[feed] ?: _state.value.latest).copy(refreshing = false))
            }
            return
        }

        setFeedState(feed, current.copy(
            refreshing = pullRefresh && page == 0,
            loadingMore = page > 0,
            initialLoading = page == 0 && current.items.isEmpty() && !pullRefresh,
            error = null,
        ))

        try {
            val sortOrder = _topicSortOrder.value.apiValue
            val sortAscending = _topicSortAscending.value.takeIf { sortOrder != null }
            val result = repository.fetchFeed(feed, page, awaitCategories = false, order = sortOrder, ascending = sortAscending)
            if (page == 0 && jobs["sync:categories"]?.isActive != true && repository.cachedCategoryList() == null) {
                syncCategoriesInBackground()
            }
            val knownCategories = repository.cachedCategoryMap()
            val freshItems = if (knownCategories.isNotEmpty()) {
                result.items.map { item ->
                    val cat = item.category ?: item.topic.categoryId?.let { knownCategories[it] }
                    if (cat != item.category) item.copy(category = cat) else item
                }
            } else result.items
            val existing = _feedStates.value[feed]?.items ?: emptyList()
            val merged = if (page == 0) freshItems.distinctBy { it.topic.id } else (existing + freshItems).distinctBy { it.topic.id }
            setFeedState(feed, current.copy(
                items = merged,
                page = page,
                hasMore = result.hasMore && (page == 0 || merged.size > existing.size),
                refreshing = false,
                loadingMore = false,
                initialLoading = false,
                error = null,
                pendingPage = if (page == 0) null else current.pendingPage,
                incomingTopicIds = if (page == 0) emptySet() else current.incomingTopicIds,
            ))
            refreshCookieInfo()
        } catch (error: CancellationException) {
            throw error
        } catch (error: NeedsInteractiveVerification) {
            settleFeedLoading(feed)
            enterWebScreen(Screen.Verify, error.message.orEmpty())
        } catch (error: HttpStatusException) {
            val msg = if (error.status == 403 && (_state.value.username == null || (feed == HomeFeedType.Unread || feed == HomeFeedType.Posted))) {
                "请先登录账号后查看${feed.label}"
            } else {
                error.message ?: "加载失败 (HTTP ${error.status})"
            }
            diagnostics.log("加载 ${feed.label} 失败: $msg")
            settleFeedLoading(feed, msg)
        } catch (error: Exception) {
            diagnostics.log("加载 ${feed.label} 异常: ${error.message}")
            settleFeedLoading(feed, error.message ?: error.javaClass.simpleName)
        } finally {
            loadingFeeds.remove(feed)
        }
    }

    /** 首页当前选中的 feed 同时镜像在 [UiState.latest] 里,两处要一起写。 */
    private fun setFeedState(feed: HomeFeedType, next: TopicListUiState) {
        _feedStates.update { it + (feed to next) }
        if (_state.value.homeFeed == feed) _state.update { it.copy(latest = next) }
    }

    private fun settleFeedLoading(feed: HomeFeedType, message: String? = null) {
        val current = _feedStates.value[feed] ?: _state.value.latest
        setFeedState(feed, current.copy(refreshing = false, loadingMore = false, initialLoading = false, error = message))
    }

    // ── 会话、启动与人机验证 ─────────────────────────────────────────────────────

    private suspend fun handlePageFailure(error: Exception, retry: () -> Unit) {
        diagnostics.log("浏览页面加载失败: ${error.message}")
        if (error is NeedsInteractiveVerification) {
            resumeAfterVerification = retry
            enterWebScreen(Screen.Verify, error.message.orEmpty())
        }
    }

    fun boot() {
        if (repository.cachedCategoryList() == null) {
            container.preferences.getString("cached_categories_json", null)?.let { rawJson ->
                repository.seedCategoriesFromJson(rawJson)
                repository.cachedCategoryList()?.let { list ->
                    _categories.value = CategoriesUiState(items = list, loaded = true, order = store.loadCategoryOrder())
                    backfillCategories(repository.cachedCategoryMap())
                }
            }
        }
        bootJob?.cancel()
        bootJob = viewModelScope.launch {
            verifying = false
            _state.update { it.copy(screen = Screen.Boot, latest = it.latest.copy(error = null)) }
            store.restoreMissingToWebView()
            refreshCookieInfo()
            if (!store.hasSessionCookie()) {
                enterWebScreen(Screen.Login)
                return@launch
            }
            try {
                val cachedUser = container.preferences.getString("cached_username", null)?.takeIf { it.isNotBlank() }
                if (cachedUser != null) {
                    diagnostics.log("命中本地会话缓存($cachedUser),直拉首屏列表并后台复核")
                    enterList(cachedUser)
                    reconcileSessionAndCategories(cachedUser)
                    return@launch
                }
                val username = repository.fetchCurrentUser()
                if (username == null) {
                    diagnostics.log("会话 Cookie 存在但服务端判定未登录,转入登录")
                    // 服务端都说无效了就别留着它:同步是合并式的,不主动清掉的话
                    // 每次启动都会拿这个死 token 白跑一次校验。
                    store.clearSessionCookie()
                    enterWebScreen(Screen.Login)
                } else {
                    diagnostics.log("登录态有效: $username")
                    container.preferences.edit { putString("cached_username", username) }
                    enterList(username)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: NeedsInteractiveVerification) {
                enterWebScreen(Screen.Verify, error.message.orEmpty())
            } catch (error: Exception) {
                diagnostics.log("启动检查失败: ${error.message}")
                _state.update { it.copy(screen = Screen.List, latest = it.latest.copy(initialLoading = false, error = error.message)) }
            }
        }
    }

    private suspend fun enterList(username: String) {
        _state.update { it.copy(username = username, screen = Screen.List) }
        notifications.refreshUnread(force = true)
        resumeAfterVerification?.also { resumeAfterVerification = null }?.invoke()
        loadFeed(_state.value.homeFeed, page = 0, pullRefresh = false)
    }

    /**
     * 首屏列表上屏后,后台静默探测分类树更新并复核会话有效性(Stale-While-Revalidate)。
     * 既保证冷启动首屏 0 阻塞秒开,又确保服务端分类变动或会话失效能够及时同步。
     */
    private fun reconcileSessionAndCategories(cachedUser: String) {
        syncCategoriesInBackground()
        viewModelScope.launch {
            try {
                val realUser = repository.fetchCurrentUser()
                when {
                    realUser == null -> {
                        diagnostics.log("后台复核发现会话已失效,清除缓存并转入登录")
                        container.preferences.edit { remove("cached_username") }
                        store.clearSessionCookie()
                        enterWebScreen(Screen.Login)
                    }
                    realUser != cachedUser -> {
                        diagnostics.log("后台复核用户名更新: $cachedUser -> $realUser")
                        container.preferences.edit { putString("cached_username", realUser) }
                        _state.update { it.copy(username = realUser) }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: NeedsInteractiveVerification) {
                enterWebScreen(Screen.Verify, error.message.orEmpty())
            } catch (error: Exception) {
                diagnostics.log("后台会话复核跳过: ${error.javaClass.simpleName}")
            }
        }
    }

    /** 分类表到手后,给已经上屏但还缺分类信息的话题补上分类。 */
    private fun backfillCategories(known: Map<Int, Category>) {
        if (known.isEmpty()) return
        fun patch(list: TopicListUiState): TopicListUiState {
            val patched = list.items.map { item ->
                val cat = item.topic.categoryId?.let { known[it] }
                if (cat != item.category) item.copy(category = cat) else item
            }
            return if (patched != list.items) list.copy(items = patched) else list
        }
        _feedStates.update { it.mapValues { (_, list) -> patch(list) } }
        _state.update { state -> patch(state.latest).let { if (it !== state.latest) state.copy(latest = it) else state } }
        _categoryTopics.update { it.mapValues { (_, list) -> patch(list) } }
    }

    private fun syncCategoriesInBackground() {
        jobs["sync:categories"] = viewModelScope.launch {
            try {
                val updated = repository.refreshCategoriesFromServer()
                val known = repository.cachedCategoryMap()
                if (updated != null) {
                    val (freshList, rawJson) = updated
                    container.preferences.edit { putString("cached_categories_json", rawJson) }
                    _categories.value = CategoriesUiState(items = freshList, loaded = true, order = store.loadCategoryOrder())
                    diagnostics.log("分类表已从服务端同步更新(${known.size} 项)")
                }
                backfillCategories(known)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                diagnostics.log("后台分类探测跳过: ${error.javaClass.simpleName}")
            }
        }
    }

    fun setPath(path: NetworkPath) {
        if (path == _state.value.path) return
        _state.update { it.copy(path = path) }
        diagnostics.log("切换网络路径 -> ${path.label}")
        retryLatest()
    }

    fun toggleAutoRefresh() {
        val enable = !_state.value.autoRefresh
        _state.update { it.copy(autoRefresh = enable) }
        autoJob?.cancel()
        if (!enable) {
            diagnostics.log("自动刷新已关闭")
            return
        }
        diagnostics.log("自动刷新已开启(每 ${Config.AUTO_REFRESH_INTERVAL_MS / 60_000} 分钟)")
        autoJob = viewModelScope.launch {
            while (isActive) {
                delay(Config.AUTO_REFRESH_INTERVAL_MS)
                if (_state.value.screen == Screen.List) loadFeed(_state.value.homeFeed, page = 0, pullRefresh = false)
            }
        }
    }

    /**
     * 由界面在验证页每秒轮询一次;完成条件满足后回到启动流程重新校验会话。
     *
     * 这里**必须**以"拿到会话 Cookie"为完成条件。早先 `Screen.Verify -> true` 是无条件成立,
     * 只要 WebView 打开的页面不是 CF 挑战页就立刻判定验证完成 —— 而邮件登录的确认页
     * 域名和标题都正常,于是刚打开就被当成验证通过,随即 boot() 发现没有 _t 又弹回登录页。
     */
    suspend fun pollVerification() {
        if (verifying) return
        if (!browser.isPageCleared()) return
        val cookies = store.syncFromWebView()
        val hasSession = cookies.containsKey(Config.SESSION_COOKIE)
        when (_state.value.screen) {
            Screen.Verify -> when {
                resumeAfterVerification != null -> {
                    verifying = true
                    val resume = resumeAfterVerification
                    resumeAfterVerification = null
                    diagnostics.log("安全验证完成,恢复中断的操作")
                    _state.update { it.copy(screen = Screen.List) }
                    verifying = false
                    resume?.invoke()
                }
                hasSession -> {
                    verifying = true
                    diagnostics.log("安全验证完成,重新校验会话")
                    boot()
                }
                // 页面不再被挑战但仍无会话:本来就没登录,回登录页收场,不要在这里干等。
                else -> {
                    diagnostics.log("安全验证通过但无会话,回到登录页")
                    enterWebScreen(Screen.Login)
                }
            }
            Screen.Login -> if (hasSession && !verifying) {
                verifying = true
                diagnostics.log("检测到登录 Cookie _t，正在校验服务端真实会话...")
                val verifiedUser = runCatching { repository.fetchCurrentUser() }.getOrNull()
                if (!verifiedUser.isNullOrBlank()) {
                    diagnostics.log("服务端会话确认有效 (用户: $verifiedUser)，关闭弹层进入论坛")
                    auth.reset()
                    boot()
                } else {
                    diagnostics.log("Cookie 存在但服务端尚未建立有效会话，继续等待页面完成登录")
                    verifying = false
                }
            }
            else -> Unit
        }
    }

    fun logout() {
        settings.setBackgroundNotification(false)
        autoJob?.cancel()
        searchJob?.cancel()
        jobs.cancelAll()
        reading.clear()
        notifications.clear()
        editor.clear()
        detail.clear()
        resumeAfterVerification = null
        container.preferences.edit { remove("cached_username") }
        _categories.value = CategoriesUiState()
        _categoryTopics.value = emptyMap()
        _userTopicStates.value = emptyMap()
        _search.value = SearchUiState()
        viewModelScope.launch {
            store.clear()
            browser.reset()
            diagnostics.log("已登出并清除 Cookie")
            _feedStates.value = emptyMap()
            _state.update { UiState(path = it.path, userAgent = it.userAgent, homeFeed = initialHomeFeed) }
            auth.resetAfterLogout()
            enterWebScreen(Screen.Login)
        }
    }

    private suspend fun enterWebScreen(screen: Screen, reason: String = "") {
        if (screen == Screen.Verify) diagnostics.recordInteractiveRequired(reason.ifBlank { "未知" })
        _state.update { it.copy(screen = screen) }
        // Login 是原生 Compose 页面;Verify 时打开根 URL 触发 Cloudflare 人机验证。
        if (screen == Screen.Verify) browser.openUrl("${Config.BASE_URL}/")
    }

    private fun refreshCookieInfo() {
        val cookies = store.syncFromWebView()
        _state.update { it.copy(cookieNames = cookies.keys.sorted(), cookieSavedAt = store.savedAtMillis()) }
    }

    private companion object {
        /** 下拉刷新跟随在途加载的最长等待,超时就收起指示器,避免卡在转圈。 */
        const val FEED_REFRESH_JOIN_TIMEOUT_MS = 15_000L

        /** 新动态并入列表后高亮多久。 */
        const val INCOMING_HIGHLIGHT_MS = 2_500L
    }
}
