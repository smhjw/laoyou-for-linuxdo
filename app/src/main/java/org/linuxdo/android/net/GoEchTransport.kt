package org.linuxdo.android.net

import android.os.Build
import android.util.Log
import java.io.IOException
import java.net.ProtocolException
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.laoyou.echnet.Client as EchClient
import org.laoyou.echnet.Echnet
import org.laoyou.echnet.Request as EchRequest
import org.linuxdo.android.BuildConfig
import org.linuxdo.android.Config

/**
 * 自研 ECH 内核:linux.do 的请求交给 Go 写的 echnet(`native/echnet`,DoH 取 ECH 配置 → TLS 1.3 + ECH)。
 *
 * 只接管必须走 ECH 的站点;别的域名、以及 echnet 失败时,照常交给 OkHttp 原来的链路,
 * 所以切换期间任何请求都不会比以前更差。
 */
class GoEchEngine(dohServers: List<String>) {
    private val client: EchClient = Echnet.newClient(dohServers.joinToString("\n"))

    fun setDohServers(servers: List<String>) = client.setDohServers(servers.joinToString("\n"))

    /** 诊断用的 JSON:请求数、失败数、最近一次的 DoH 服务器 / IP / 握手耗时 / 错误。 */
    fun stats(): String = client.stats()

    internal fun send(request: EchRequest) = client.send(request)

    fun close() = client.close()

    companion object {
        /** 只有被 SNI 拦截的站点走 ECH;子域名的 HTTPS 记录不一定带 ECH 配置,先不接管。 */
        fun handles(request: Request): Boolean = request.url.isHttps && request.url.host == Config.HOST

        /** 让 Go 改用纯软件密码学实现的 GODEBUG 选项。 */
        const val SOFTWARE_CRYPTO_GODEBUG = "cpu.aes=off,cpu.pmull=off,cpu.sha1=off,cpu.sha2=off,cpu.sha512=off,cpu.sha3=off"

        /**
         * 这台设备能不能加载 echnet。
         *
         * x86 设备(模拟器、MuMu 等)上 arm64 代码是翻译执行的:翻译层声称支持 AES / SHA / PMULL 扩展,
         * Go 一用就 SIGILL,而且 Go 只认进程**启动时**的环境变量,应用里再 setenv 也来不及。
         * 所以 x86 上默认不启用自研内核(退回原内核);只有进程启动时就带着 [SOFTWARE_CRYPTO_GODEBUG]
         * (debug 包可用 `adb shell setprop wrap.<包名> "GODEBUG=…"` 注入)才放行,用于在模拟器上测试。
         * 真机(arm64)不受影响,照常使用硬件加速。
         */
        fun supportedOnThisDevice(): Boolean {
            if (Build.SUPPORTED_ABIS.firstOrNull()?.startsWith("x86") != true) return true
            val godebug = System.getenv("GODEBUG").orEmpty()
            // cpu.all=off 关掉全部可选特性,也覆盖了上面几项(属性值有 91 字符上限,测试时用这个短写法)
            return "cpu.all=off" in godebug || SOFTWARE_CRYPTO_GODEBUG.split(',').all { it in godebug }
        }
    }
}

/**
 * 把 linux.do 的请求改走 [GoEchEngine]。放在应用拦截器里,所以要自己补上
 * [withSessionHeaders](原本由网络拦截器添加)并按 OkHttp 的规则跟随同站重定向。
 */
internal class GoEchInterceptor(
    private val engine: GoEchEngine,
    private val sessionHeaders: (Request) -> Request,
    private val followRedirects: Boolean,
    private val onFallback: (Request, IOException) -> Unit = { _, _ -> },
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        var followUps = 0
        while (true) {
            if (!GoEchEngine.handles(request)) return chain.proceed(request)
            val response = try {
                send(chain, request)
            } catch (e: IOException) {
                if (chain.call().isCanceled()) throw e
                // 过渡期兜底:echnet 失败时交回原链路(开着 DoH 时那边仍是原来的 ECH 传输层)。
                // 只重放幂等请求:POST 可能已经送达服务器,再发一次会重复发帖 / 回复。
                if (request.method != "GET" && request.method != "HEAD") throw e
                onFallback(request, e)
                return chain.proceed(request)
            }
            if (!followRedirects || !response.isRedirect) return response
            val next = followUp(request, response) ?: return response
            if (++followUps > MAX_FOLLOW_UPS) {
                response.close()
                throw ProtocolException("Too many follow-up requests: $followUps")
            }
            response.close()
            request = next
        }
    }

    private fun send(chain: Interceptor.Chain, original: Request): Response {
        if (chain.call().isCanceled()) throw IOException("Canceled")
        val request = sessionHeaders(original)
        val body = request.body
        val headerText = buildString {
            for ((name, value) in request.headers) {
                // 压缩由 echnet 自己协商并解压;长度由它按请求体计算。
                if (name.equals("Accept-Encoding", true) || name.equals("Content-Length", true)) continue
                append(name).append(": ").append(value).append('\n')
            }
            val contentType = body?.contentType()
            if (contentType != null && request.header("Content-Type") == null) {
                append("Content-Type: ").append(contentType).append('\n')
            }
        }
        val echRequest = EchRequest().apply {
            method = request.method
            url = request.url.toString()
            headers = headerText
            if (body != null) this.body = Buffer().also(body::writeTo).readByteArray()
            timeoutMs = (chain.connectTimeoutMillis() + chain.readTimeoutMillis()).toLong()
        }

        val sentAt = System.currentTimeMillis()
        val result = try {
            engine.send(echRequest)
        } catch (e: Exception) {
            throw IOException("ECH 直连失败: ${e.message}", e)
        }
        if (chain.call().isCanceled()) throw IOException("Canceled")
        if (BuildConfig.DEBUG) {
            Log.d("GoEch", "${result.status} ${request.method} ${request.url.encodedPath} ${result.proto} ech=${result.echAccepted} ip=${result.remoteIP}")
        }

        val headers = Headers.Builder().apply {
            for (line in result.headers.lineSequence()) {
                val colon = line.indexOf(':')
                if (colon > 0) addUnsafeNonAscii(line.substring(0, colon).trim(), line.substring(colon + 1).trim())
            }
        }.build()
        return Response.Builder()
            .request(request)
            .protocol(if (result.proto.startsWith("HTTP/2")) Protocol.HTTP_2 else Protocol.HTTP_1_1)
            .code(result.status.toInt())
            .message(result.reason.ifBlank { "" })
            .headers(headers)
            .body((result.body ?: ByteArray(0)).toResponseBody(headers["Content-Type"]?.toMediaTypeOrNull()))
            .sentRequestAtMillis(sentAt)
            .receivedResponseAtMillis(System.currentTimeMillis())
            .build()
    }

    /** 与 OkHttp 的 RetryAndFollowUpInterceptor 一致:307/308 保留方法与请求体,其余改成无体 GET。 */
    private fun followUp(request: Request, response: Response): Request? {
        val location = response.header("Location") ?: return null
        val url = request.url.resolve(location) ?: return null
        val builder = request.newBuilder().url(url)
        val keepMethod = response.code == 307 || response.code == 308
        if (!keepMethod && request.method != "GET" && request.method != "HEAD") {
            builder.method("GET", null)
                .removeHeader("Transfer-Encoding")
                .removeHeader("Content-Length")
                .removeHeader("Content-Type")
        }
        if (url.host != request.url.host) builder.removeHeader("Authorization")
        return builder.build()
    }

    private companion object {
        const val MAX_FOLLOW_UPS = 20
    }
}
