package org.linuxdo.android.ui.controller

import android.app.Application
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.linuxdo.android.AppContainer
import org.linuxdo.android.Config
import org.linuxdo.android.data.NotificationFilter
import org.linuxdo.android.data.ProfilePage
import org.linuxdo.android.data.ProfileSection
import org.linuxdo.android.data.TopicRepository
import org.linuxdo.android.service.NotificationHelper
import org.linuxdo.android.ui.ProfileUiState
import org.linuxdo.android.ui.Screen
import org.linuxdo.android.ui.UiState

/**
 * 「我的」各分区、通知列表与未读计数(铃铛 + 桌面角标)。
 *
 * 未读数写在共享的 [UiState] 里,因为首页铃铛和底栏也要读它。
 */
class NotificationsController(
    private val app: Application,
    private val scope: CoroutineScope,
    private val container: AppContainer,
    private val repository: TopicRepository,
    private val jobs: JobRegistry,
    private val onPageFailure: PageFailureHandler,
    private val appState: MutableStateFlow<UiState>,
    /** 首页列表在加载时不做预取,免得和用户的操作抢仓库的串行锁。 */
    private val feedsLoading: () -> Boolean,
    /** 「总结」分区顺带刷新草稿箱。 */
    private val loadDrafts: (refresh: Boolean) -> Unit,
) {
    private val store = container.sessionStore
    private val diagnostics = container.diagnostics

    private val _profile = MutableStateFlow<Map<ProfileSection, ProfileUiState>>(emptyMap())
    val profile: StateFlow<Map<ProfileSection, ProfileUiState>> = _profile.asStateFlow()

    private val _filter = MutableStateFlow(NotificationFilter.All)
    val filter: StateFlow<NotificationFilter> = _filter.asStateFlow()

    /**
     * 通知各分类的数据。`_profile` 的键是 ProfileSection,四个分类挤同一个槽位,
     * 所以额外按分类留一份,切标签才能命中缓存直接显示。
     */
    private val notificationCache = mutableMapOf<NotificationFilter, ProfileUiState>()

    data class NotificationTarget(val topicId: Long, val postNumber: Int?)
    private val _pendingTarget = MutableStateFlow<NotificationTarget?>(null)
    /** 点系统通知进来时要打开的楼层,界面消费后调 [clearPendingTarget]。 */
    val pendingTarget: StateFlow<NotificationTarget?> = _pendingTarget.asStateFlow()

    private var unreadJob: Job? = null
    private var nextUnreadRefreshAt = 0L

    fun clear() {
        unreadJob?.cancel()
        _profile.value = emptyMap()
        notificationCache.clear()
        _filter.value = NotificationFilter.All
    }

    fun navigateToTopicFromNotification(
        topicId: Long,
        postNumber: Int?,
        notificationId: Long? = null,
        notificationType: Int? = null,
    ) {
        _pendingTarget.value = NotificationTarget(topicId, postNumber)
        if (notificationId != null && notificationId > 0L) readNotification(notificationId, notificationType)
        markNotificationsReadForTopic(topicId)
    }

    fun clearPendingTarget() {
        _pendingTarget.value = null
    }

    private fun markNotificationsReadForTopic(topicId: Long) {
        val pages = listOfNotNull(_profile.value[ProfileSection.Notifications]) + notificationCache.values
        pages.flatMap { it.content.entries }
            .filter { it.topicId == topicId && it.unread }
            .mapNotNull { entry -> entry.id.toLongOrNull()?.let { it to entry.notificationType } }
            .toSet()
            .forEach { (id, type) -> readNotification(id, type) }
    }

    fun loadProfile(section: ProfileSection, refresh: Boolean = false, more: Boolean = false) {
        val username = appState.value.username ?: return
        if (section == ProfileSection.Summary && !more) loadDrafts(refresh)
        val filter = _filter.value
        val previous = _profile.value[section] ?: ProfileUiState()
        if (previous.loading) return
        if (!refresh && !more && previous.loaded && isCacheUsable(section, filter, previous)) return
        if (more && !previous.content.hasMore) return
        jobs["profile:$section"] = scope.launch {
            _profile.update { it + (section to previous.copy(loading = true, error = null)) }
            try {
                val fresh = fetchProfilePage(username, section, filter, previous, more)
                _profile.update { it + (section to fresh) }
                if (section == ProfileSection.Notifications) notificationCache[filter] = fresh
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _profile.update { it + (section to previous.copy(error = error.message ?: "加载失败")) }
                onPageFailure(error) { loadProfile(section, refresh = true) }
            } finally {
                _profile.update { states -> states[section]?.let { states + (section to it.copy(loading = false)) } ?: states }
            }
        }
    }

    /** 拉一页并组装成新状态。前台加载和后台预取共用这一条路径,避免两套逻辑走偏。 */
    private suspend fun fetchProfilePage(
        username: String,
        section: ProfileSection,
        filter: NotificationFilter,
        previous: ProfileUiState,
        more: Boolean,
    ): ProfileUiState {
        val cursor = if (more) previous.content.nextCursor else 0
        val excludeIds = if (more && section == ProfileSection.Notifications) {
            previous.content.entries.map { it.id }.toSet()
        } else emptySet()
        val unreadNow = unreadBaseline(section, filter)
        val result = repository.fetchProfile(username, section, cursor, filter, excludeIds)
        val entries = if (more) (previous.content.entries + result.entries).distinctBy { it.id } else result.entries
        return ProfileUiState(
            // 防死循环的守卫看的是游标有没有往前走,而不是条目有没有变多 ——
            // 低频通知类型完全可能某一轮一条都没命中,但后面还有。
            content = result.copy(entries = entries,
                hasMore = result.hasMore && (!more || result.nextCursor > previous.content.nextCursor)),
            loaded = true,
            loadedAt = SystemClock.elapsedRealtime(),
            unreadAtLoad = unreadNow,
        )
    }

    /**
     * 这个分区该拿哪个未读数做基准。通知按分类分项取;
     * 总结 / 活动 / 账号这些和未读无关的分区返回 0,等于只受 TTL 约束。
     */
    private fun unreadBaseline(section: ProfileSection, filter: NotificationFilter): Int = when (section) {
        ProfileSection.Notifications -> appState.value.unreadByFilter[filter] ?: 0
        ProfileSection.Messages -> appState.value.unreadByFilter[NotificationFilter.Messages] ?: 0
        else -> 0
    }

    /**
     * 缓存还能不能直接显示:该分区对应的未读没涨 **且** 没到 TTL。
     * 未读必须按分类取 —— 铃铛总数只算回复和私信,拿它判断会让点赞分类一直吃旧缓存。
     * 未读变少是用户自己读掉的,列表已经就地更新过,所以用 `<=`。
     */
    private fun isCacheUsable(section: ProfileSection, filter: NotificationFilter, state: ProfileUiState): Boolean {
        if (!state.loaded) return false
        if (SystemClock.elapsedRealtime() - state.loadedAt >= Config.PROFILE_CACHE_TTL_MS) return false
        return unreadBaseline(section, filter) <= state.unreadAtLoad
    }

    /**
     * 切换通知分类。命中仍新鲜的分类缓存就直接贴回去;再次点击当前分类时强制刷新。
     */
    fun setFilter(filter: NotificationFilter) {
        if (_filter.value == filter) {
            loadProfile(ProfileSection.Notifications, refresh = true)
            return
        }
        jobs["profile:${ProfileSection.Notifications}"]?.cancel()
        _filter.value = filter
        val cached = notificationCache[filter]
        if (cached != null && isCacheUsable(ProfileSection.Notifications, filter, cached)) {
            _profile.update { it + (ProfileSection.Notifications to cached.copy(loading = false, error = null)) }
            return
        }
        notificationCache.remove(filter)
        _profile.update { it - ProfileSection.Notifications }
        loadProfile(ProfileSection.Notifications)
    }

    fun refreshUnread(force: Boolean = false, reloadNotifications: Boolean = true) {
        if (unreadJob?.isActive == true || appState.value.screen != Screen.List) return
        if (force) {
            nextUnreadRefreshAt = 0L
        } else if (SystemClock.elapsedRealtime() < nextUnreadRefreshAt) {
            return
        }
        unreadJob = scope.launch {
            try {
                val beforeByFilter = appState.value.unreadByFilter
                val summary = repository.fetchUnreadSummary(store.getNotificationScope())
                nextUnreadRefreshAt = 0L
                appState.update {
                    it.copy(unreadCount = summary.count, unreadFilters = summary.unreadFilters, unreadByFilter = summary.unreadByFilter)
                }
                publishBadge(summary.count, "前台未读刷新")
                // 任一分类的未读变多才需要动。变少是用户自己读掉的,列表已经就地更新过。
                val grew = summary.unreadByFilter.any { (filter, now) -> now > (beforeByFilter[filter] ?: 0) }
                if (reloadNotifications && grew) prefetchUnreadNotifications()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                nextUnreadRefreshAt = SystemClock.elapsedRealtime() + 5_000L
                diagnostics.log("未读数量刷新失败: ${error.javaClass.simpleName}")
            }
        }
    }

    /**
     * 预热有未读的通知分类,让用户点标签时直接有内容。
     *
     * 一轮只预热一个分类:仓库的 gate 是全局串行锁,预取一旦占住它,
     * 用户紧接着的操作就得排在后面等。当前正在看的分类优先,其余按声明顺序补。
     */
    private fun prefetchUnreadNotifications() {
        if (feedsLoading() || jobs.anyActive()) return
        val username = appState.value.username ?: return
        val current = _filter.value
        val unread = appState.value.unreadFilters
        val target = (listOf(current) + NotificationFilter.entries).distinct()
            .filter { it == current || it in unread }
            .firstOrNull { filter ->
                val cached = notificationCache[filter]
                cached == null || !isCacheUsable(ProfileSection.Notifications, filter, cached)
            } ?: return

        jobs["prefetch:notifications"] = scope.launch {
            // 预取的正好是当前看的那一类时把 loading 立起来:既有反馈,也挡住 loadProfile 重复请求。
            val showing = _filter.value == target
            if (showing) {
                _profile.update { states ->
                    states + (ProfileSection.Notifications to
                        (states[ProfileSection.Notifications] ?: ProfileUiState()).copy(loading = true, error = null))
                }
            }
            try {
                val fresh = fetchProfilePage(username, ProfileSection.Notifications, target, ProfileUiState(), more = false)
                notificationCache[target] = fresh
                if (_filter.value == target) _profile.update { it + (ProfileSection.Notifications to fresh) }
                diagnostics.log("已预取通知分类: ${target.label}")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // 用户还没点进来,预取失败不该弹错误;真点进去时会正常重试并展示错误。
                diagnostics.log("预取通知失败: ${error.javaClass.simpleName}")
            } finally {
                if (showing) {
                    _profile.update { states ->
                        states[ProfileSection.Notifications]?.let {
                            states + (ProfileSection.Notifications to it.copy(loading = false))
                        } ?: states
                    }
                }
            }
        }
    }

    fun readNotification(id: Long) = readNotification(id, null)

    fun readNotification(id: Long, knownType: Int?) {
        if (jobs["notification:$id"]?.isActive == true) return
        // 先本地扣减再发请求,否则用户点进去再退回来会看到角标还亮着。
        val type = knownType
            ?: (listOfNotNull(_profile.value[ProfileSection.Notifications]) + notificationCache.values)
                .firstNotNullOfOrNull { page -> page.content.entries.firstOrNull { it.id == id.toString() }?.notificationType }
        markReadLocally(id, type)
        jobs["notification:$id"] = scope.launch {
            try {
                repository.markNotificationRead(id)
                // 等正在进行的分页统计结束,避免已读后的强制刷新被 isActive 守卫丢掉。
                unreadJob?.join()
                // 这条已就地标成已读,不要整段重拉 —— 那会丢掉用户翻过的页面和滚动位置。
                refreshUnread(force = true, reloadNotifications = false)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                diagnostics.log("通知已读状态未同步: ${error.javaClass.simpleName}")
            }
        }
    }

    /**
     * 就地把一条通知标为已读,并从未读计数里扣掉。**按条减 1,不是清零。**
     * 拿不到类型(不在已加载列表里)就扣主铃铛数,随后的 refreshUnread 会用服务端数据覆盖。
     */
    private fun markReadLocally(id: Long, type: Int?) {
        val key = id.toString()
        fun markRead(page: ProfilePage) = page.copy(entries = page.entries.map { if (it.id == key) it.copy(unread = false) else it })
        _profile.update { panels ->
            panels[ProfileSection.Notifications]?.let { notices ->
                panels + (ProfileSection.Notifications to notices.copy(content = markRead(notices.content)))
            } ?: panels
        }
        // 按分类的缓存也要跟着改,否则切出去再切回来这条又变回未读。
        notificationCache.replaceAll { _, cached -> cached.copy(content = markRead(cached.content)) }
        if (type == null) {
            appState.update { it.copy(unreadCount = (it.unreadCount - 1).coerceAtLeast(0)) }
        } else {
            appState.update { state ->
                val reduced = state.unreadByFilter.mapValues { (filter, count) ->
                    if (filter.includes(type)) (count - 1).coerceAtLeast(0) else count
                }
                state.copy(
                    unreadByFilter = reduced,
                    unreadFilters = reduced.filterValues { it > 0 }.keys,
                    // 口径跟随通知作用域,与桌面角标、推送下发共用 NotificationScope.includes。
                    unreadCount = if (store.getNotificationScope().includes(type)) {
                        (state.unreadCount - 1).coerceAtLeast(0)
                    } else state.unreadCount,
                )
            }
        }
        publishBadge(appState.value.unreadCount, "本地已读")
    }

    /** 角标实测期间桌面要保持人造数字,不覆盖。 */
    private fun publishBadge(count: Int, source: String) {
        if (store.isBadgeTestHolding()) return
        store.setLastBadgeCount(count)
        NotificationHelper.updateBadgeCount(app, count, source = source)
    }

    fun testPushNotification() {
        NotificationHelper.showNotification(
            context = app,
            notificationId = 999999,
            title = "LINUX DO 通知测试",
            content = "这是一条本地测试通知，您的通知推送功能工作正常！",
            badgeCount = appState.value.unreadCount.takeIf { it > 0 },
        )
    }
}
