package org.linuxdo.android.ui.controller

import android.app.Application
import android.content.res.Configuration
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.linuxdo.android.AppContainer
import org.linuxdo.android.data.AutoRefreshInterval
import org.linuxdo.android.data.NotificationPollInterval
import org.linuxdo.android.data.NotificationScope
import org.linuxdo.android.service.NotificationHelper
import org.linuxdo.android.service.NotificationKeepAliveService
import org.linuxdo.android.service.NotificationWorker
import org.linuxdo.android.ui.ThemeMode

/**
 * 本地偏好:外观、阅读、新话题探测、后台通知与保活、悬浮球。
 *
 * 只管读写与系统服务的启停;改动波及列表或未读的,通过回调交给 ViewModel 处理。
 */
class SettingsController(
    private val app: Application,
    private val container: AppContainer,
    private val onAutoRefreshChanged: () -> Unit,
    private val onNotificationScopeChanged: () -> Unit,
) {
    private val store = container.sessionStore
    private val prefs = container.preferences

    private val _themeMode = MutableStateFlow(
        ThemeMode.entries.firstOrNull { it.name == prefs.getString("theme_mode", ThemeMode.System.name) } ?: ThemeMode.System
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    /** Android 12+ 跟随壁纸取色(Material You);默认关闭,用「清简」色板。换了键名,让老用户也先看到新色板。 */
    private val _dynamicColor = MutableStateFlow(prefs.getBoolean("dynamic_color_qj", false))
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    private val _treeView = MutableStateFlow(prefs.getBoolean("tree_view", true))
    val treeView: StateFlow<Boolean> = _treeView.asStateFlow()

    private val _autoRefreshInterval = MutableStateFlow(store.getAutoRefreshInterval())
    val autoRefreshInterval = _autoRefreshInterval.asStateFlow()
    private val _autoRefreshIncoming = MutableStateFlow(store.isAutoRefreshIncomingEnabled())
    val autoRefreshIncoming = _autoRefreshIncoming.asStateFlow()

    private val _notificationPollInterval = MutableStateFlow(store.getNotificationPollInterval())
    val notificationPollInterval = _notificationPollInterval.asStateFlow()
    private val _notificationScope = MutableStateFlow(store.getNotificationScope())
    val notificationScope = _notificationScope.asStateFlow()
    private val _backgroundNotification = MutableStateFlow(store.isBackgroundNotificationEnabled())
    val backgroundNotification = _backgroundNotification.asStateFlow()
    private val _customRealtimeSeconds = MutableStateFlow(store.getCustomRealtimeSeconds())
    val customRealtimeSeconds = _customRealtimeSeconds.asStateFlow()
    private val _keepAliveService = MutableStateFlow(store.isKeepAliveServiceEnabled())
    val keepAliveService = _keepAliveService.asStateFlow()

    /** 启动时对齐通知渠道、后台轮询与常驻服务。 */
    fun start() {
        NotificationHelper.ensureNotificationChannels(app)
        if (store.isBackgroundNotificationEnabled()) NotificationWorker.enqueue(app)
        syncKeepAliveService()
    }

    fun isDarkMode(): Boolean = when (_themeMode.value) {
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
        ThemeMode.System ->
            (app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit { putString("theme_mode", mode.name) }
    }

    fun setDynamicColor(enabled: Boolean) {
        _dynamicColor.value = enabled
        prefs.edit { putBoolean("dynamic_color_qj", enabled) }
    }

    fun setTreeView(enabled: Boolean) {
        _treeView.value = enabled
        prefs.edit { putBoolean("tree_view", enabled) }
    }

    fun setAutoRefreshIncoming(enabled: Boolean) {
        setAutoRefreshInterval(if (enabled) AutoRefreshInterval.MIN_1 else AutoRefreshInterval.OFF)
    }

    fun setAutoRefreshInterval(interval: AutoRefreshInterval) {
        store.setAutoRefreshInterval(interval)
        _autoRefreshInterval.value = store.getAutoRefreshInterval()
        _autoRefreshIncoming.value = store.isAutoRefreshIncomingEnabled()
        onAutoRefreshChanged()
    }

    fun setBackgroundNotification(enabled: Boolean) {
        store.setBackgroundNotificationEnabled(enabled)
        syncPolling()
    }

    fun setNotificationPollInterval(interval: NotificationPollInterval) {
        store.setNotificationPollInterval(interval)
        syncPolling()
    }

    /** 两个入口改的是同一组存储;常驻通知上写着检测频率,开着保活就重启一次让文案跟上。 */
    private fun syncPolling() {
        val interval = store.getNotificationPollInterval()
        _notificationPollInterval.value = interval
        _backgroundNotification.value = store.isBackgroundNotificationEnabled()
        if (interval != NotificationPollInterval.OFF) NotificationWorker.enqueue(app) else NotificationWorker.cancel(app)
        syncKeepAliveService()
    }

    fun setCustomRealtimeSeconds(seconds: Int) {
        store.setCustomRealtimeSeconds(seconds.coerceIn(1, 60))
        _customRealtimeSeconds.value = store.getCustomRealtimeSeconds()
        if (store.getNotificationPollInterval() == NotificationPollInterval.REALTIME) {
            NotificationWorker.syncSilentLoop(app)
            syncKeepAliveService()
        }
    }

    fun setNotificationScope(scope: NotificationScope) {
        store.setNotificationScope(scope)
        _notificationScope.value = store.getNotificationScope()
        // 未读口径跟着作用域变,立刻按新口径重算铃铛与桌面角标。
        onNotificationScopeChanged()
    }

    fun setKeepAliveService(enabled: Boolean) {
        store.setKeepAliveServiceEnabled(enabled)
        _keepAliveService.value = store.isKeepAliveServiceEnabled()
        syncKeepAliveService()
    }

    /** 档位关掉时不该留着常驻通知,所以按「后台检测是否开启」决定服务启停。 */
    private fun syncKeepAliveService() {
        if (store.isBackgroundNotificationEnabled()) {
            NotificationKeepAliveService.start(app)
        } else {
            NotificationKeepAliveService.stop(app)
        }
    }

}
