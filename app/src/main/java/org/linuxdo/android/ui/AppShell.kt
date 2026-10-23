package org.linuxdo.android.ui

import org.linuxdo.android.R
import androidx.core.graphics.ColorUtils
import androidx.compose.ui.graphics.toArgb
import android.widget.ImageView
import android.content.res.ColorStateList
import org.linuxdo.android.ui.design.*
import androidx.compose.material3.MaterialTheme

import org.linuxdo.android.ui.screen.DiagnosticsScreen
import org.linuxdo.android.ui.screen.DraftsScreen
import org.linuxdo.android.ui.screen.ProfileScreen
import org.linuxdo.android.ui.screen.TopicCreatorSheet
import org.linuxdo.android.ui.screen.UpdateDialog
import org.linuxdo.android.ui.screen.settings.SettingsScreen
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView as AndroidTextView
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import org.linuxdo.android.net.BrowserSession
import org.linuxdo.android.Config
import org.linuxdo.android.data.NotificationPollInterval
import org.linuxdo.android.data.compactCategoryGroups
import org.linuxdo.android.ui.design.LoadingIndicator
import org.linuxdo.android.ui.design.Dimens
import org.linuxdo.android.ui.nav.AppNavHost
import org.linuxdo.android.ui.nav.NavStack
import org.linuxdo.android.ui.nav.Route
import org.linuxdo.android.ui.screen.TopicListScreen
import org.linuxdo.android.ui.screen.TopicDetailScreen
import org.linuxdo.android.ui.screen.CategoryListScreen
import org.linuxdo.android.ui.screen.SearchScreen
import org.linuxdo.android.ui.screen.TopicListUiState
import org.linuxdo.android.ui.screen.LoginScreen
import org.linuxdo.android.ui.screen.categoryLevelOf
import org.linuxdo.android.ui.nav.MainTab
import org.linuxdo.android.ui.nav.TabBar

/**
 * 应用外壳。
 *
 * 分层顺序是硬约束:全局单例 WebView 必须留在导航之外的最外层,靠 zIndex 切换显隐。
 * Login 屏使用原生 Compose，安全验证只在独立对话框中显示轻量 WebView。
 */
@Composable
fun AppShell(viewModel: MainViewModel, browser: BrowserSession) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val loginUiState by viewModel.auth.state.collectAsStateWithLifecycle()
    val updateState by viewModel.updates.state.collectAsStateWithLifecycle()
    // 全局 WebView 在 CF 验证时显示；登录人机验证使用独立对话框。
    val showWeb = state.screen == Screen.Verify
    val showWebLoginModal = state.screen == Screen.Login && loginUiState.showWebLoginDialog
    val themeMode by viewModel.settings.themeMode.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.settings.dynamicColor.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    val notificationPollInterval by viewModel.settings.notificationPollInterval.collectAsStateWithLifecycle()
    val customRealtimeSeconds by viewModel.settings.customRealtimeSeconds.collectAsStateWithLifecycle()

    LaunchedEffect(lifecycle, state.screen, state.latest.initialLoading, notificationPollInterval, customRealtimeSeconds) {
        if (state.screen == Screen.List && !state.latest.initialLoading) lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.notifications.refreshUnread(force = true)
            val pollDelayMs = when (notificationPollInterval) {
                NotificationPollInterval.REALTIME -> (customRealtimeSeconds * 1000L).coerceAtLeast(1000L)
                NotificationPollInterval.OFF -> Config.UNREAD_POLL_INTERVAL_MS
                else -> (notificationPollInterval.seconds * 1000L).coerceIn(5_000L, Config.UNREAD_POLL_INTERVAL_MS)
            }
            while (true) {
                delay(pollDelayMs)
                viewModel.notifications.refreshUnread()
            }
        }
    }

    LaunchedEffect(showWeb) {
        if (showWeb) {
            while (true) {
                delay(1_000)
                viewModel.pollVerification()
            }
        }
    }

    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    AppTheme(dark = dark, dynamicColor = dynamicColor) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .systemBarsPadding()
                .imePadding()
                .clipToBounds(),
        ) {
            // 验证期间保留导航的组合身份，返回后恢复原页面与滚动位置。
            if (state.username != null || state.screen == Screen.List) {
                key(state.username) { MainNavigation(state, viewModel, lifecycle) }
            }

            if (state.screen == Screen.Boot) BootScreen()

            // 原生登录页面。
            if (state.screen == Screen.Login) {
                LoginScreen(
                    state = loginUiState,
                    onPasswordLogin = viewModel.auth::loginWithPassword,
                    onSendEmailCode = viewModel.auth::sendLoginEmail,
                    onEmailCodeLogin = viewModel.auth::loginWithEmailCode,
                    onDismissError = viewModel.auth::dismissError,
                    onResetSecondFactor = viewModel.auth::resetSecondFactor,
                    onOpenWebLogin = viewModel.auth::openWebLoginModal,
                    onStartWebAuth = viewModel.auth::startWebAuth,
                    onDismissRiskControl = viewModel.auth::dismissRiskControlDialog,
                    onCancelWebAuth = viewModel.auth::cancelWebAuth,
                )
            }

            // CF 的全屏验证和后台 WebView 使用全局页面内 AndroidView；登录人机验证在弹框中。
            if (!showWebLoginModal) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(if (showWeb) 2f else -1f)
                        .then(if (showWeb) Modifier.background(MaterialTheme.colorScheme.surface) else Modifier),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Column(
                        modifier = if (showWeb) {
                            Modifier
                                .fillMaxSize()
                                .padding(top = Dimens.navBarHeight)
                        } else {
                            Modifier.fillMaxSize()
                        },
                    ) {
                        if (showWeb) WebTopBar(state.screen)
                        val context = LocalContext.current
                        AndroidView(
                            factory = { browser.attach(context) },
                            onRelease = { browser.detach() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    }
                }
            }
            else {
                NativeWebLoginDialog(browser, viewModel.auth::closeWebLoginModal, dark)
            }

            val updateInfo = updateState.updateInfo
            if (updateState.showDialog && updateInfo != null) {
                UpdateDialog(
                    currentVersion = updateState.currentVersion,
                    info = updateInfo,
                    stage = updateState.apkStage,
                    downloadedBytes = updateState.apkDownloadedBytes,
                    totalBytes = updateState.apkTotalBytes,
                    errorMessage = updateState.apkError,
                    onStartDownload = viewModel.updates::startApkUpdate,
                    onRetryAfterPermission = viewModel.updates::retryApkUpdateAfterPermission,
                    onCancelDownload = viewModel.updates::cancelApkUpdate,
                    onDismiss = viewModel.updates::dismissUpdateDialog,
                )
            }
        }
    }
}

@Composable
private fun NativeWebLoginDialog(
    browser: BrowserSession,
    onClose: () -> Unit,
    dark: Boolean,
) {
    val context = LocalContext.current
    // WebView 必须挂在 View 体系的 Dialog 里;颜色取自当前 Material 主题,外观与 ModalBottomSheet 一致。
    val scheme = MaterialTheme.colorScheme
    val surfaceColor = scheme.surfaceContainerLow.toArgb()
    val labelColor = scheme.onSurface.toArgb()
    val secondaryColor = scheme.onSurfaceVariant.toArgb()
    val dividerColor = scheme.outlineVariant.toArgb()
    val accentColor = scheme.primary.toArgb()
    DisposableEffect(browser, context, dark, surfaceColor) {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val dialog = Dialog(context)
        var dismissedByComposition = false

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(surfaceColor)
                val r = dp(28).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
        }
        // 拖动条只是视觉提示,和 Material 底部弹窗保持一致
        val handle = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(2).toFloat()
                setColor(ColorUtils.setAlphaComponent(secondaryColor, 0x66))
            }
        }
        root.addView(handle, LinearLayout.LayoutParams(dp(32), dp(4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            topMargin = dp(16)
        })
        val header = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(24), 0, dp(12), 0)
        }
        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_verified_user_24)
            imageTintList = ColorStateList.valueOf(accentColor)
        }
        val title = AndroidTextView(context).apply {
            text = "完成人机验证"
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(labelColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setPadding(dp(16), 0, 0, 0)
        }
        val close = ImageView(context).apply {
            setImageResource(R.drawable.ic_close_24)
            imageTintList = ColorStateList.valueOf(secondaryColor)
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = "关闭"
            val ripple = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)
            setBackgroundResource(ripple.resourceId)
            setOnClickListener { dialog.dismiss() }
        }
        header.addView(icon, LinearLayout.LayoutParams(dp(24), dp(24)))
        header.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        header.addView(close, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
        root.addView(View(context).apply { setBackgroundColor(dividerColor) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1))

        val webView = browser.attachAuth(context)
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(root)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnDismissListener {
            if (!dismissedByComposition) onClose()
        }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (context.resources.displayMetrics.heightPixels * 0.92f).toInt(),
            )
            setGravity(Gravity.BOTTOM)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.5f }
        }

        onDispose {
            dismissedByComposition = true
            dialog.setOnDismissListener(null)
            root.removeView(webView)
            dialog.dismiss()
            browser.detachAuth(webView)
        }
    }
}

@Composable
private fun MainNavigation(state: UiState, viewModel: MainViewModel, lifecycle: Lifecycle) {
    val stacks = MainTab.entries.associateWith { tab ->
        key(tab) { rememberSaveable(saver = NavStack.Saver) { NavStack(tab.root) } }
    }
    val scrollRequests = MainTab.entries.associateWith { tab ->
        key(tab) { rememberSaveable { mutableIntStateOf(0) } }
    }
    var selected by rememberSaveable { mutableStateOf(MainTab.Latest) }
    val holder = rememberSaveableStateHolder()
    val stack = stacks.getValue(selected)
    val scrollToTopRequest = scrollRequests.getValue(selected).intValue
    val details by viewModel.detail.state.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val categoryTopics by viewModel.categoryTopics.collectAsStateWithLifecycle()
    val userTopicStates by viewModel.userTopicStates.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val profile by viewModel.notifications.profile.collectAsStateWithLifecycle()
    val notificationFilter by viewModel.notifications.filter.collectAsStateWithLifecycle()
    val composer by viewModel.editor.composer.collectAsStateWithLifecycle()
    val topicCreator by viewModel.editor.topicCreator.collectAsStateWithLifecycle()
    val drafts by viewModel.editor.drafts.collectAsStateWithLifecycle()
    val topicSortOrder by viewModel.topicSortOrder.collectAsStateWithLifecycle()
    val topicSortAscending by viewModel.topicSortAscending.collectAsStateWithLifecycle()
    val treeView by viewModel.settings.treeView.collectAsStateWithLifecycle()
    val syncReadProgress by viewModel.reading.syncEnabled.collectAsStateWithLifecycle()
    val themeMode by viewModel.settings.themeMode.collectAsStateWithLifecycle()
    val updateState by viewModel.updates.state.collectAsStateWithLifecycle()
    val dynamicColorPref by viewModel.settings.dynamicColor.collectAsStateWithLifecycle()
    val autoRefreshIncoming by viewModel.settings.autoRefreshIncoming.collectAsStateWithLifecycle()
    val autoRefreshInterval by viewModel.settings.autoRefreshInterval.collectAsStateWithLifecycle()
    val backgroundNotification by viewModel.settings.backgroundNotification.collectAsStateWithLifecycle()
    val notificationPollInterval by viewModel.settings.notificationPollInterval.collectAsStateWithLifecycle()
    val notificationScope by viewModel.settings.notificationScope.collectAsStateWithLifecycle()
    val customRealtimeSeconds by viewModel.settings.customRealtimeSeconds.collectAsStateWithLifecycle()
    val keepAliveService by viewModel.settings.keepAliveService.collectAsStateWithLifecycle()
    val pendingNotificationTarget by viewModel.notifications.pendingTarget.collectAsStateWithLifecycle()
    // ── DoH 状态 ─────────────────────────────────────────────────────────────────
    val dohEnabled by viewModel.doh.enabled.collectAsStateWithLifecycle()
    val dohServers by viewModel.doh.servers.collectAsStateWithLifecycle()
    val selectedDohServerUrl by viewModel.doh.selectedServerUrl.collectAsStateWithLifecycle()
    val dohAutoFailover by viewModel.doh.autoFailover.collectAsStateWithLifecycle()
    val dohAutoOptimize by viewModel.doh.autoOptimize.collectAsStateWithLifecycle()
    val goEchEnabled by viewModel.doh.goEchEnabled.collectAsStateWithLifecycle()
    val dohLatencies by viewModel.doh.latencies.collectAsStateWithLifecycle()
    val dohSummaries by viewModel.doh.summaries.collectAsStateWithLifecycle()
    val isTestingAllDoh by viewModel.doh.testingAll.collectAsStateWithLifecycle()

    LaunchedEffect(pendingNotificationTarget) {
        val target = pendingNotificationTarget ?: return@LaunchedEffect
        selected = MainTab.Latest
        stacks.getValue(MainTab.Latest).push(Route.TopicDetail(target.topicId, "", target.postNumber))
        viewModel.notifications.clearPendingTarget()
    }

    val browsing = state.screen == Screen.List
    var detailOverlay by rememberSaveable { mutableStateOf(false) }
    val openNotifications = {
        if (stack.current != Route.Notifications) stack.push(Route.Notifications)
        viewModel.notifications.loadProfile(org.linuxdo.android.data.ProfileSection.Notifications, refresh = true)
        viewModel.notifications.refreshUnread(force = true)
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            holder.SaveableStateProvider(selected.name) {
                key(selected) {
                    AppNavHost(stack, interactive = browsing && !topicCreator.open) { route ->
                        val active = browsing && stack.current == route
                        LaunchedEffect(route, active) {
                            if (active) when (route) {
                                is Route.TopicDetail -> viewModel.detail.loadTopic(route.topicId, postNumber = route.postNumber)
                                Route.CategoryList -> viewModel.loadCategories()
                                is Route.CategoryTopics -> {
                                    if (!categories.loaded && !categories.loading) viewModel.loadCategories()
                                    viewModel.loadCategory(route.categoryId, route.slug)
                                }
                                is Route.UserTopics -> viewModel.loadUserTopics(route.username, route.isBookmark)
                                Route.Drafts -> {
                                    if (!categories.loaded && !categories.loading) viewModel.loadCategories()
                                    viewModel.editor.loadDrafts(refresh = true)
                                }
                                else -> Unit
                            }
                        }
                        when (route) {
                            Route.TopicList -> TopicListScreen(
                                title = state.homeFeed.label,
                                state = state.latest,
                                onRefresh = viewModel::refreshLatest,
                                onLoadMore = viewModel::loadMoreLatest,
                                onOpenTopic = { topic -> stack.push(Route.TopicDetail(topic.id, topic.title)) },
                                active = active,
                                scrollToTopRequest = scrollToTopRequest,
                                trailing = { UnreadBell(state.unreadCount, openNotifications) },
                                currentFeed = state.homeFeed,
                                onFeedSelect = viewModel::switchHomeFeed,
                                hasUnread = state.unreadCount > 0,
                                onApplyIncoming = { viewModel.applyIncomingTopics(state.homeFeed) },
                                onSetNotificationLevel = { topic, level ->
                                    viewModel.setTopicNotificationLevel(topic.id, level)
                                },
                                onCreateTopic = if (state.username != null) ({ viewModel.editor.openTopicCreator(null) }) else null,
                                onBookmarkTopic = { topic, onResult ->
                                    viewModel.detail.bookmarkTopicFromList(topic.id, onResult)
                                },
                                onFlagTopic = { topic, reasonId, message, onSuccess, onError ->
                                    viewModel.detail.submitTopicFlagFromList(topic.id, reasonId, message, onSuccess, onError)
                                },
                                sortOrder = topicSortOrder,
                                sortAscending = topicSortAscending,
                                onSortChange = { viewModel.setTopicSort(it) },
                            )

                            is Route.TopicDetail -> {
                                val lifecycleState by lifecycle.currentStateFlow.collectAsStateWithLifecycle()
                                val isForeground = active && lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
                                LaunchedEffect(route.topicId, isForeground) {
                                    viewModel.reading.setTopicActive(route.topicId, isForeground)
                                }
                                // 离开这篇帖子时结算一次阅读进度(仅在用户开启同步后才会真正发请求)。
                                DisposableEffect(route.topicId) {
                                    onDispose {
                                        viewModel.reading.setTopicActive(route.topicId, false)
                                        viewModel.reading.leave(route.topicId)
                                    }
                                }
                                TopicDetailScreen(
                                    title = route.title,
                                    state = details[route.topicId] ?: DetailUiState(loading = true),
                                    onBack = { stack.pop() },
                                    onRetry = { viewModel.detail.retryTopic(route.topicId, route.postNumber) },
                                    onRefresh = { viewModel.detail.refreshTopic(route.topicId) },
                                    onLoadMore = { viewModel.detail.loadTopic(route.topicId, more = true) },
                                    onLoadPrevious = { viewModel.detail.loadTopic(route.topicId, previous = true) },
                                    initialPostNumber = route.postNumber,
                                    onUnknownTag = { viewModel.diagnostics.log("未知 cooked 标签: $it") },
                                    treeView = treeView,
                                    onLoadReplies = { viewModel.detail.loadReplies(route.topicId, it) },
                                    onReact = { postId, reaction -> viewModel.detail.react(route.topicId, postId, reaction) },
                                    onBoost = { postId, raw -> viewModel.detail.createBoost(route.topicId, postId, raw) },
                                    onDeleteBoost = { postId, boostId -> viewModel.detail.deleteBoost(route.topicId, postId, boostId) },
                                    onRefreshPost = { viewModel.detail.refreshPost(route.topicId, it) },
                                    onSolution = { viewModel.detail.setSolution(route.topicId, it) },
                                    composer = composer,
                                    onOpenComposer = viewModel.editor::openComposer,
                                    onDraftChange = viewModel.editor::updateDraft,
                                    onSubmitComposer = viewModel.editor::submitComposer,
                                    onCloseComposer = viewModel.editor::closeComposer,
                                    onDeletePost = { viewModel.detail.deletePost(route.topicId, it) },
                                    onReloadFromStart = { viewModel.detail.reloadFromStart(route.topicId) },
                                    onCreatedPostShown = { viewModel.detail.clearCreatedPost(route.topicId) },
                                    onAttach = viewModel.editor::attachToComposer,
                                    onRemoveComposerAttachment = viewModel.editor::removeComposerAttachment,
                                    topicId = route.topicId,
                                    onOpenTopic = { id, title, postNumber ->
                                        stack.push(Route.TopicDetail(id, title, postNumber))
                                    },
                                    active = active,
                                    trailing = { UnreadBell(state.unreadCount, openNotifications) },
                                    onToggleOrder = { viewModel.detail.toggleCommentOrder(route.topicId) },
                                    onPostsVisible = { viewModel.reading.onPostsVisible(route.topicId, it) },
                                    onOverlayActive = { detailOverlay = it },
                                    onSetNotificationLevel = { viewModel.setTopicNotificationLevel(route.topicId, it) },
                                    signedIn = state.username != null,
                                    onToggleBookmark = { viewModel.detail.toggleBookmark(route.topicId, it) },
                                    onOpenFlagDialog = { viewModel.detail.openFlagDialog(route.topicId, it) },
                                    onCloseFlagDialog = { viewModel.detail.closeFlagDialog(route.topicId) },
                                    onSubmitFlag = { postId, reasonId, message ->
                                        viewModel.detail.submitFlag(route.topicId, postId, reasonId, message)
                                    },
                                )
                            }
                            Route.CategoryList -> {
                                val customSection by viewModel.customSection.collectAsStateWithLifecycle()
                                val context = LocalContext.current
                                CategoryListScreen(
                                    state = categories,
                                    customSection = customSection,
                                    onRetry = { viewModel.loadCategories(retry = true) },
                                    onOpen = { stack.push(Route.CategoryTopics(it.id, it.slug, it.name)) },
                                    onOpenTopic = { topicId, title, postNumber ->
                                        stack.push(Route.TopicDetail(topicId, title, postNumber))
                                    },
                                    onOpenWebUrl = { url ->
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        runCatching { context.startActivity(intent) }
                                    },
                                    onSaveCustomSection = viewModel::saveCustomSection,
                                    onDeleteCustomSection = viewModel::deleteCustomSection,
                                    scrollToTopRequest = scrollToTopRequest,
                                    onSaveOrder = viewModel::saveCategoryOrder,
                                    onResetOrder = viewModel::resetCategoryOrder,
                                    trailing = { UnreadBell(state.unreadCount, openNotifications) },
                                )
                            }

                            is Route.CategoryTopics -> {
                                val variants = remember(categories.items, route.categoryId) {
                                    val group = compactCategoryGroups(categories.items).firstOrNull { (parent, levels) ->
                                        levels.isNotEmpty() && (parent.id == route.categoryId || levels.any { it.id == route.categoryId })
                                    }
                                    if (group != null) {
                                        (listOf(group.first) + group.second.sortedBy { categoryLevelOf(it) })
                                            .filter { categoryLevelOf(it) in 0..3 }
                                    } else {
                                        emptyList()
                                    }
                                }
                                DisposableEffect(route.categoryId, route.slug, active) {
                                    if (active) {
                                        viewModel.setActiveCategory(route.categoryId, route.slug)
                                    }
                                    onDispose {
                                        viewModel.setActiveCategory(null)
                                    }
                                }
                                TopicListScreen(
                                    title = route.name,
                                    state = categoryTopics[route.categoryId] ?: TopicListUiState(),
                                    onRefresh = { viewModel.loadCategory(route.categoryId, route.slug, refresh = true) },
                                    onLoadMore = { viewModel.loadCategory(route.categoryId, route.slug, more = true) },
                                    onOpenTopic = { stack.push(Route.TopicDetail(it.id, it.title)) },
                                    onBack = { stack.pop() },
                                    active = active,
                                    trailing = { UnreadBell(state.unreadCount, openNotifications) },
                                    onApplyIncoming = { viewModel.applyIncomingTopics(state.homeFeed, categoryId = route.categoryId) },
                                    categoryVariants = variants,
                                    currentCategoryId = route.categoryId,
                                    onSelectCategoryVariant = { target ->
                                        if (target.id != route.categoryId) {
                                            stack.replaceCurrent(Route.CategoryTopics(target.id, target.slug, target.name))
                                            viewModel.loadCategory(target.id, target.slug)
                                        }
                                    },
                                    onSetNotificationLevel = { topic, level ->
                                        viewModel.setTopicNotificationLevel(topic.id, level)
                                    },
                                    onCreateTopic = if (state.username != null) ({ viewModel.editor.openTopicCreator(route.categoryId.toInt()) }) else null,
                                    onBookmarkTopic = { topic, onResult ->
                                        viewModel.detail.bookmarkTopicFromList(topic.id, onResult)
                                    },
                                    onFlagTopic = { topic, reasonId, message, onSuccess, onError ->
                                        viewModel.detail.submitTopicFlagFromList(topic.id, reasonId, message, onSuccess, onError)
                                    },
                                    sortOrder = topicSortOrder,
                                    sortAscending = topicSortAscending,
                                    onSortChange = { viewModel.setTopicSort(it, currentCategoryId = route.categoryId, currentCategorySlug = route.slug) },
                                )
                            }

                            Route.Search -> SearchScreen(search, viewModel::updateSearch, viewModel::loadMoreSearch,
                                onOpenTopic = { stack.push(Route.TopicDetail(it.id, it.title)) },
                                onOrder = viewModel::setSearchOrder, active = active,
                                scrollToTopRequest = scrollToTopRequest,
                                trailing = { UnreadBell(state.unreadCount, openNotifications) })

                            Route.Profile -> ProfileScreen(state.username, profile, viewModel.notifications::loadProfile,
                                onOpenTopic = { id, title, postNumber -> stack.push(Route.TopicDetail(id, title, postNumber)) },
                                onOpenUserTopics = { username, title, isBookmark ->
                                    stack.push(Route.UserTopics(username, title, isBookmark))
                                },
                                draftCount = drafts.items.size.toString(),
                                onOpenDrafts = { stack.push(Route.Drafts) },
                                onDiagnostics = { stack.push(Route.Diagnostics) },
                                onSettings = { stack.push(Route.Settings) },
                                onLogout = viewModel::logout,
                                active = active,
                                onReadNotification = viewModel.notifications::readNotification,
                                notificationFilter = notificationFilter,
                                onNotificationFilter = viewModel.notifications::setFilter,
                                unreadFilters = state.unreadFilters,
                                scrollToTopRequest = scrollToTopRequest,
                                trailing = { UnreadBell(state.unreadCount, openNotifications) })

                            Route.Drafts -> DraftsScreen(
                                state = drafts,
                                categories = categories.items,
                                onBack = { stack.pop() },
                                onRefresh = { viewModel.editor.loadDrafts(refresh = true) },
                                onEditDraft = { draft ->
                                    if (draft.topicId != null && draft.topicId > 0L) {
                                        stack.pop()
                                        stack.push(Route.TopicDetail(draft.topicId, draft.title, draft.replyToPostNumber))
                                        viewModel.editor.openReplyWithDraft(draft)
                                    } else {
                                        viewModel.editor.openDraftInCreator(draft)
                                    }
                                },
                                onDeleteDraft = viewModel.editor::deleteDraft,
                                trailing = { UnreadBell(state.unreadCount, openNotifications) },
                            )

                            is Route.UserTopics -> {
                                val key = "${if (route.isBookmark) "bookmark" else "created"}:${route.username}"
                                TopicListScreen(
                                    title = route.title,
                                    state = userTopicStates[key] ?: TopicListUiState(),
                                    onRefresh = { viewModel.loadUserTopics(route.username, route.isBookmark, refresh = true) },
                                    onLoadMore = { viewModel.loadUserTopics(route.username, route.isBookmark, more = true) },
                                    onOpenTopic = { stack.push(Route.TopicDetail(it.id, it.title)) },
                                    onBack = { stack.pop() },
                                    active = active,
                                    trailing = { UnreadBell(state.unreadCount, openNotifications) },
                                    onSetNotificationLevel = { topic, level ->
                                        viewModel.setTopicNotificationLevel(topic.id, level)
                                    },
                                    onBookmarkTopic = { topic, onResult ->
                                        viewModel.detail.bookmarkTopicFromList(topic.id, onResult)
                                    },
                                    onFlagTopic = { topic, reasonId, message, onSuccess, onError ->
                                        viewModel.detail.submitTopicFlagFromList(topic.id, reasonId, message, onSuccess, onError)
                                    },
                                    sortOrder = topicSortOrder,
                                    sortAscending = topicSortAscending,
                                    onSortChange = { viewModel.setTopicSort(it) },
                                )
                            }


                            Route.Settings, is Route.SettingsDetail -> SettingsScreen(
                                page = (route as? Route.SettingsDetail)?.page,
                                onOpenPage = { stack.push(Route.SettingsDetail(it)) },
                                treeView = treeView,
                                onTreeView = viewModel.settings::setTreeView,
                                onBack = { stack.pop() },
                                themeMode = themeMode,
                                onThemeMode = viewModel.settings::setThemeMode,
                                dynamicColor = dynamicColorPref,
                                onDynamicColor = viewModel.settings::setDynamicColor,
                                updateState = updateState,
                                onCheckUpdate = viewModel.updates::onVersionItemClick,
                                syncReadProgress = syncReadProgress,
                                onSyncReadProgress = viewModel.reading::setSyncEnabled,
                                onDiagnostics = { stack.push(Route.Diagnostics) },
                                onLogout = viewModel::logout,
                                autoRefreshIncoming = autoRefreshIncoming,
                                onAutoRefreshIncoming = viewModel.settings::setAutoRefreshIncoming,
                                autoRefreshInterval = autoRefreshInterval,
                                onAutoRefreshInterval = viewModel.settings::setAutoRefreshInterval,
                                backgroundNotification = backgroundNotification,
                                onBackgroundNotification = viewModel.settings::setBackgroundNotification,
                                notificationPollInterval = notificationPollInterval,
                                onNotificationPollInterval = viewModel.settings::setNotificationPollInterval,
                                customRealtimeSeconds = customRealtimeSeconds,
                                onCustomRealtimeSeconds = viewModel.settings::setCustomRealtimeSeconds,
                                notificationScope = notificationScope,
                                onNotificationScope = viewModel.settings::setNotificationScope,
                                keepAliveService = keepAliveService,
                                onKeepAliveService = viewModel.settings::setKeepAliveService,
                                onTestNotification = viewModel.notifications::testPushNotification,
                                // ── DoH ──────────────────────────────────────────
                                dohEnabled = dohEnabled,
                                onDohEnabled = viewModel.doh::setEnabled,
                                dohServers = dohServers,
                                selectedDohServerUrl = selectedDohServerUrl,
                                onDohServerSelected = viewModel.doh::select,
                                dohAutoFailover = dohAutoFailover,
                                onDohAutoFailover = viewModel.doh::setAutoFailover,
                                dohAutoOptimize = dohAutoOptimize,
                                onDohAutoOptimize = viewModel.doh::setAutoOptimize,
                                goEchEnabled = goEchEnabled,
                                onGoEchEnabled = viewModel.doh::setGoEchEnabled,
                                dohLatencies = dohLatencies,
                                dohSummaries = dohSummaries,
                                isTestingAllDoh = isTestingAllDoh,
                                onTestDohServer = viewModel.doh::test,
                                onTestAllDohServers = viewModel.doh::testAll,
                                onAddCustomDohServer = viewModel.doh::addCustom,
                                onUpdateDohServer = viewModel.doh::update,
                                onDeleteDohServer = viewModel.doh::delete,
                                onResetDohServers = viewModel.doh::resetToDefault,
                            )

                            Route.Notifications -> ProfileScreen(state.username, profile, viewModel.notifications::loadProfile,
                                onOpenTopic = { id, title, postNumber -> stack.push(Route.TopicDetail(id, title, postNumber)) },
                                onDiagnostics = { stack.push(Route.Diagnostics) }, onSettings = { stack.push(Route.Settings) },
                                active = active, notificationOnly = true, onBack = { stack.pop() },
                                onReadNotification = viewModel.notifications::readNotification,
                                notificationFilter = notificationFilter,
                                onNotificationFilter = viewModel.notifications::setFilter,
                                unreadFilters = state.unreadFilters)


                            Route.Diagnostics -> DiagnosticsScreen(
                                state = state,
                                viewModel = viewModel,
                                onBack = { stack.pop() },
                            )

                        }
                    }
                }
            }

            if (topicCreator.open) {
                TopicCreatorSheet(
                    state = topicCreator,
                    categories = categories.items,
                    onTitleChange = viewModel.editor::updateTopicCreatorTitle,
                    onRawChange = viewModel.editor::updateTopicCreatorRaw,
                    onCategoryChange = viewModel.editor::updateTopicCreatorCategory,
                    onTagsChange = viewModel.editor::updateTopicCreatorTags,
                    onSearchTags = viewModel.editor::searchTopicCreatorTags,
                    onAttach = viewModel.editor::attachToTopicCreator,
                    onRemoveAttachment = viewModel.editor::removeTopicCreatorAttachment,
                    onSaveDraft = viewModel.editor::saveTopicDraft,
                    onSubmit = {
                        viewModel.editor.submitTopicCreator { topicId, title, postNumber ->
                            stack.push(Route.TopicDetail(topicId, title, postNumber))
                        }
                    },
                    onClose = viewModel.editor::closeTopicCreator,
                    onClearNotice = viewModel.editor::clearTopicCreatorNotice,
                    onOpenDrafts = {
                        viewModel.editor.closeTopicCreator()
                        stack.push(Route.Drafts)
                    },
                )
            }
        }
        val isKeyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        if (!composer.open && !topicCreator.open && !detailOverlay && !isKeyboardOpen) {
            TabBar(selected, homeFeedLabel = state.homeFeed.label) { tab ->
                if (browsing) {
                    if (tab == selected) {
                        stacks.getValue(tab).popToRoot()
                        scrollRequests.getValue(tab).intValue++
                    } else selected = tab
                }
            }
        }
    }
}

@Composable
private fun BootScreen() {
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator(diameter = 28.dp)
    }
}
