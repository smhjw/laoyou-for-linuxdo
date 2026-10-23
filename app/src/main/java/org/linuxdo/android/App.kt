package org.linuxdo.android

import android.app.Application
import android.content.Context
import android.webkit.WebSettings
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import java.io.File
import org.linuxdo.android.data.Diagnostics
import org.linuxdo.android.data.ReadingStore
import org.linuxdo.android.data.SessionStore
import org.linuxdo.android.net.BrowserSession
import org.linuxdo.android.net.NetworkPath
import org.linuxdo.android.net.OkHttpTransport
import org.linuxdo.android.net.Transport
import org.linuxdo.android.net.UserApiKeyAuthService
import org.linuxdo.android.net.WebViewFetchTransport
import org.linuxdo.android.net.AdaptiveDohDns
import org.linuxdo.android.net.DohFailoverCoordinator
import org.linuxdo.android.net.DohPresets
import org.linuxdo.android.net.DohTransport
import org.linuxdo.android.net.GoEchEngine
import org.linuxdo.android.net.loadDohServers
import org.linuxdo.android.net.NetworkStateMonitor
import org.linuxdo.android.net.buildBootstrapClient
import org.linuxdo.android.net.buildSharedHttpClient

/** 实现 ImageLoaderFactory,Coil 的 AsyncImage 就会自动用上带凭据的共享客户端。 */
class App : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        org.linuxdo.android.push.LinuxDoPushSdk.init(
            org.linuxdo.android.push.PushConfig(
                targetActivityClass = MainActivity::class.java,
                smallIconResId = R.drawable.ic_launcher_foreground,
                baseUrl = Config.BASE_URL,
                httpClientProvider = { container.httpClient },
                sessionProvider = { ctx ->
                    val store = if (ctx === this) container.sessionStore else SessionStore(ctx)
                    object : org.linuxdo.android.push.PushSessionProvider {
                        override fun hasSession(): Boolean = store.hasSession()
                        override fun isBackgroundNotificationEnabled(): Boolean = store.isBackgroundNotificationEnabled()
                        override fun getCookie(): String = store.storedCookies().entries.joinToString("; ") { "${it.key}=${it.value}" }
                        override fun getUserAgent(): String = container.userAgent
                        override fun getUserId(): Long = store.currentUserId()
                        override fun saveUserId(id: Long) = store.saveCurrentUserId(id)
                        override fun getLastSeenNotificationId(): Long = store.getLastSeenNotificationId()
                        override fun setLastSeenNotificationId(id: Long) = store.setLastSeenNotificationId(id)
                        override fun getLastBadgeCount(): Int = store.getLastBadgeCount()
                        override fun setLastBadgeCount(count: Int, force: Boolean) = store.setLastBadgeCount(count, force)
                        override fun incrementLastBadgeCount(): Int = store.incrementLastBadgeCount()
                        override fun isBadgeTestHolding(): Boolean = store.isBadgeTestHolding()
                        override fun setBadgeTestHoldUntil(timestamp: Long) = store.setBadgeTestHoldUntil(timestamp)
                        override fun getPendingTestReplayTag(): String? = store.getPendingTestReplayTag()
                        override fun setPendingTestReplayTag(tag: String?) = store.setPendingTestReplayTag(tag)
                        override fun isTestWaitScreenOnArmed(): Boolean = store.isTestWaitScreenOnArmed()
                        override fun setTestWaitScreenOnArmed(armed: Boolean) = store.setTestWaitScreenOnArmed(armed)
                        override fun isAppInForeground(): Boolean = store.isAppInForeground()
                        override fun setAppInForeground(inForeground: Boolean) = store.setAppInForeground(inForeground)
                        override fun getNotificationScope(): org.linuxdo.android.push.PushNotificationScope =
                            org.linuxdo.android.push.PushNotificationScope.fromRaw(store.getNotificationScope().code)
                        override fun getCustomRealtimeSeconds(): Int = store.getCustomRealtimeSeconds()
                    }
                },
                logger = { key, message, force ->
                    container.diagnostics.logThrottled(key, message, force)
                }
            )
        )
    }

    override fun newImageLoader(): ImageLoader = container.imageLoader
}

class AppContainer(context: Context) {
    val preferences = context.getSharedPreferences("display_preferences", Context.MODE_PRIVATE)
    val diagnostics = Diagnostics(File(context.filesDir, "diagnostics.log"))
    val sessionStore = SessionStore(context)
    val reading = ReadingStore(context)
    val userAgent: String = resolveUserAgent(context, sessionStore)
    val browser = BrowserSession(context, userAgent, sessionStore, diagnostics)

    // 必须在 dohCoordinator 之前初始化:协调器构造时就可能调用 clientFactory。
    private var goEch: GoEchEngine? = null

    val networkMonitor = NetworkStateMonitor(context) {
        // 当开启/关闭 VPN 或系统代理时，通知协调器刷新代次以暂停或恢复 DoH 直连
        dohCoordinator.refresh()
    }

    /**
     * 原生 DoH + ECH 自动故障转移协调器。
     * 维护代次（Generation）、在途请求宽限期退役、单飞轮换与 Call.Factory 动态分发。
     */
    val dohCoordinator: DohFailoverCoordinator = DohFailoverCoordinator(
        context = context,
        store = sessionStore,
        userAgent = userAgent,
        networkMonitor = networkMonitor,
        clientFactory = { dns, transport, coordinator ->
            // 协调器判定要走 DoH + ECH(开关打开、没有 VPN / 代理)时,linux.do 交给自研内核
            val ownEch = if (transport != null && sessionStore.isGoEchEnabled()) goEchEngine() else null
            buildSharedHttpClient(
                store = sessionStore,
                userAgent = userAgent,
                dns = dns,
                dohTransport = transport,
                failoverCoordinator = coordinator,
                goEch = ownEch,
                onGoEchFallback = { request, error ->
                    diagnostics.logThrottled("go-ech-fallback", "自研 ECH 内核失败，改走原链路: ${request.url.encodedPath} ${error.message}")
                },
            )
        },
    )


    /** 自研内核的诊断 JSON;还没初始化(从没走过 DoH + ECH)时为 null。 */
    @Synchronized
    fun goEchStatsOrNull(): String? = goEch?.stats()

    /** 自研 ECH 内核(Go 写的 echnet)。每次重建客户端时同步 DoH 节点列表:当前选中的排第一,其余依次兜底。 */
    @Synchronized
    fun goEchEngine(): GoEchEngine? {
        if (!GoEchEngine.supportedOnThisDevice()) {
            diagnostics.logThrottled("go-ech-x86", "x86 设备上 arm64 翻译层不支持 Go 用到的指令，自研 ECH 内核停用，使用原内核")
            return null
        }
        val selected = sessionStore.getDohServerUrl() ?: DohPresets.DEFAULT.url
        val servers = (listOf(selected) + loadDohServers(sessionStore).map { it.url }).distinct()
        goEch?.let { it.setDohServers(servers); return it }
        return runCatching { GoEchEngine(servers) }
            .onFailure { diagnostics.log("自研 ECH 内核初始化失败: ${it.message}") }
            .getOrNull()
            ?.also { goEch = it }
    }

    /** 动态分发的全局 Call.Factory：新发起的请求永远自动获取当前最新代次的 Client */
    val dynamicCallFactory: okhttp3.Call.Factory = dohCoordinator

    /** 保持向后兼容：全局共享的当前活跃 Client */
    val httpClient: okhttp3.OkHttpClient
        get() = dohCoordinator.currentClient

    val userApiKeyAuthService: UserApiKeyAuthService = UserApiKeyAuthService(
        sessionStore,
        dohCoordinator.currentClient,
        diagnostics,
        // 网页授权兑换发生在 App 内的原生网络栈，外部浏览器里的 cf_clearance 带不过来。
        // 撞盾时复用共享 WebView 静默过一次，比直接抛"获取 CSRF 失败"可靠得多。
        silentRevalidate = { reason -> browser.clearChallenge(reason) },
    )

    val imageLoader: ImageLoader = ImageLoader.Builder(context)
        .callFactory(dynamicCallFactory)
        .components {
            if (Build.VERSION.SDK_INT >= 28) {
                add(ImageDecoderDecoder.Factory(enforceMinimumFrameDelay = true))
            } else {
                add(GifDecoder.Factory(enforceMinimumFrameDelay = true))
            }
            add(SvgDecoder.Factory())
        }
        .memoryCache {
            MemoryCache.Builder(context)
                .maxSizePercent(0.25)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(File(context.cacheDir, "image_cache"))
                .maxSizeBytes(256L * 1024 * 1024)
                .build()
        }
        .crossfade(true)
        .build()

    private val okHttpTransport: OkHttpTransport = OkHttpTransport(sessionStore, browser, dohCoordinator.currentClient, diagnostics)
    private val webViewTransport: WebViewFetchTransport = WebViewFetchTransport(browser, diagnostics)

    init {
        dohCoordinator.onEpochChanged = { epoch ->
            okHttpTransport.updateClient(epoch.client)
            userApiKeyAuthService.updateClient(epoch.client)
        }
    }

    fun transport(path: NetworkPath): Transport = when (path) {
        NetworkPath.OKHTTP -> okHttpTransport
        NetworkPath.WEBVIEW -> webViewTransport
    }

    /**
     * 重建或刷新 OkHttp 客户端（DoH 偏好设置变更或手动切换后调用）。
     */
    fun rebuildHttpClient() {
        dohCoordinator.refresh()
    }

    companion object {
        /**
         * 全应用共用同一个 UA。首次运行时由系统 WebView 的默认 UA 规整成 Chrome 移动端的形态
         * (去掉 "; wv" 与 "Version/4.0"),并持久化——WebView 升级也不改变它,
         * 因为 cf_clearance 与 UA 绑定,UA 漂移会触发重新挑战。
         */
        fun resolveUserAgent(context: Context, store: SessionStore): String {
            val normalized = normalize(WebSettings.getDefaultUserAgent(context))
            val stored = store.userAgentOrNull()
            // 旧版本把设备段强行改成固定的 Android 10; K。它会与 WebView 自动
            // 发送的 Client Hints 不一致，CF 可能将同一会话视为指纹漂移。
            if (!stored.isNullOrBlank() && !stored.contains("Android 10; K")) return stored
            store.saveUserAgent(normalized)
            return normalized
        }

        fun normalize(raw: String): String = raw
            .replace(Regex("[;\\s]*\\bwv\\b[;\\s]*(?=\\))"), "")
            .replace(Regex("Version/[^ ]+ *"), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }
}

