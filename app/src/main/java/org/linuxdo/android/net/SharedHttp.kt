package org.linuxdo.android.net

import android.content.Context
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.linuxdo.android.Config
import org.linuxdo.android.data.SessionStore

/**
 * 全应用共用的 HTTP 客户端。
 *
 * 图片(Coil)和 JSON 请求必须走同一个客户端:cf_clearance 与 UA 绑定,
 * 图片请求若用了 Coil 默认客户端(不同 UA、没有 Cookie),会被 Cloudflare 直接拦掉,
 * 结果就是列表里头像全空。
 *
 * @param store     会话 Cookie 存储
 * @param userAgent 全局统一 UA
 * @param dns          可选的自定义 DNS 实现（注入 DoH 时传入 AdaptiveDohDns 实例；null 表示使用系统默认 DNS）
 * @param dohTransport 可选的原生 DoH + ECH TLS 传输层（注入后替换底层 Socket 与 TLS 握手）
 * @param goEch        自研 ECH 内核;给了就由它接管 linux.do 的请求,其余请求与它失败时仍走上面的传输层
 */
fun buildSharedHttpClient(
    store: SessionStore,
    userAgent: String,
    dns: Dns? = null,
    dohTransport: DohTransport? = null,
    failoverCoordinator: DohFailoverCoordinator? = null,
    goEch: GoEchEngine? = null,
    onGoEchFallback: (okhttp3.Request, java.io.IOException) -> Unit = { _, _ -> },
): OkHttpClient {
    val dispatcher = Dispatcher().apply {
        maxRequests = 64
        maxRequestsPerHost = 16
    }
    val connectionPool = ConnectionPool(16, 5, TimeUnit.MINUTES)
    val builder = OkHttpClient.Builder()
        .dispatcher(dispatcher)
        .connectionPool(connectionPool)
        .connectTimeout(Config.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        // 读超时保持较长:Coil 的图片请求复用同一个客户端,大图需要时间读完。
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)

    if (failoverCoordinator != null) {
        // 应用级故障上报与幂等单次重试拦截器（置于最外层，感知 DNS/连接/TLS 全阶段）
        builder.addInterceptor(DohFailoverInterceptor(failoverCoordinator))
    }

    if (goEch != null) {
        // 放在故障转移拦截器之后:echnet 抛出的 IOException 同样会被它感知
        builder.addInterceptor(
            GoEchInterceptor(
                engine = goEch,
                sessionHeaders = { withSessionHeaders(it, store, userAgent) },
                followRedirects = true,
                onFallback = onGoEchFallback,
            ),
        )
    }

    if (dohTransport != null) {
        // 原生 ECH + TLS 传输配置：注入 DNS、SocketFactory、SSLSocketFactory (Rustls ECH)
        dohTransport.configure(builder)
    } else if (dns != null) {
        builder.dns(dns)
    }

    return builder
        .addNetworkInterceptor(SessionHeaderInterceptor(store, userAgent))
        .build()
}

/**
 * 结合设置开关以及是否开启 VPN/代理综合判断，解析出当前应走哪个 HTTP 客户端。
 *
 * 判定矩阵：
 * 1. 优先复用 [AppContainer] 的当前活跃客户端（若实例存在）；
 *    - 进程存活时，协调器已持续监听 VPN 状态、用户开关与代次自动故障转移，享有活跃连接池和 ECH 会话复用；
 * 2. 兜底与静态独立唤醒路径：
 *    - 用户开关未开启 DoH (`!sessionStore.isDohEnabled()`) -> 走普通系统网络；
 *    - 检测到 VPN 或系统代理开启 (`networkMonitor.detect().shouldPauseDoh`) -> 自动暂停 DoH，走系统代理网络；
 *    - 开启 DoH 且处于无代理直连网络 -> 必须走原生 DoH + ECH TLS 传输通道（构建失败则降级纯 DNS）。
 */
fun resolveActiveHttpClient(
    context: Context,
    sessionStore: SessionStore,
): OkHttpClient {
    val app = context.applicationContext as? org.linuxdo.android.App
    val container = app?.container
    if (container != null) {
        return container.httpClient
    }

    val userAgent = org.linuxdo.android.AppContainer.resolveUserAgent(context, sessionStore)
    val isDohSwitchOn = sessionStore.isDohEnabled()
    val isVpnOrProxyActive = NetworkStateMonitor(context).detect().shouldPauseDoh

    // 综合判断：只有设置开启且无 VPN/代理直连时，才走 DoH + ECH
    val shouldUseDoh = isDohSwitchOn && !isVpnOrProxyActive

    val transport = if (shouldUseDoh) {
        buildActiveDohTransport(sessionStore)
    } else {
        null
    }

    val dns = if (shouldUseDoh && transport == null) {
        buildDohDns(sessionStore)
    } else {
        null
    }

    return buildSharedHttpClient(
        store = sessionStore,
        userAgent = userAgent,
        dns = dns,
        dohTransport = transport,
    )
}

/**
 * 构建仅用于 DoH 探测与 Bootstrap 连接的底层客户端。
 *
 * 此客户端：
 * - 不注入 DoH（避免递归依赖）
 * - 不注入 Cookie/UA（减少指纹）
 * - 超时参数与探测需求匹配（连接 5s，读 5s）
 *
 * 由 [AppContainer] 在初始化 DoH 时传入 [AdaptiveDohDns] 使用。
 */
fun buildBootstrapClient(): OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

/** 给站内请求统一注入固定 UA 与会话 Cookie。 */
private class SessionHeaderInterceptor(
    private val store: SessionStore,
    private val userAgent: String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(withSessionHeaders(chain.request(), store, userAgent))
}

/**
 * 站内请求补上固定 UA、Referer 与会话 Cookie;站外请求去掉论坛 Cookie 与 CSRF 头。
 * 重定向后的每一跳都要重新过一遍,避免站内图片跳转到 CDN 时携带论坛 Cookie。
 */
internal fun withSessionHeaders(original: okhttp3.Request, store: SessionStore, userAgent: String): okhttp3.Request {
    if (original.url.host != Config.HOST || !original.url.isHttps) {
        return original.newBuilder().removeHeader("Cookie").removeHeader("X-CSRF-Token").build()
    }
    val builder = original.newBuilder()
        .header("User-Agent", userAgent)
        .header("Referer", "${Config.BASE_URL}/")
    // 这里运行在 OkHttp 的 IO 线程,不能调 syncFromWebView() —— 它要在主线程访问
    // CookieManager。只读加密存储里的快照,快照由 Transport 在每次 JSON 请求前刷新。
    val cookies = store.storedCookies()
    if (cookies.isNotEmpty()) {
        builder.header("Cookie", SessionStore.toCookieHeader(cookies))
    }
    return builder.build()
}
