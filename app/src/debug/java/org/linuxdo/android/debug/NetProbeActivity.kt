package org.linuxdo.android.debug

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import okhttp3.Request
import org.linuxdo.android.App

/**
 * 仅 debug 构建:不登录也能验证网络层。用 App 真正在用的共享 OkHttp 客户端(含全部拦截器)
 * 请求几个公开地址,结果写进 logcat(tag NetProbe)和屏幕;配合 tag GoEch 可以看出是否走了自研内核。
 *
 * adb shell am start -n org.linuxdo.android/.debug.NetProbeActivity
 */
class NetProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = TextView(this).apply { setPadding(32, 96, 32, 32); textSize = 12f }
        setContentView(ScrollView(this).apply { addView(text) })

        val container = (application as App).container
        thread(name = "net-probe") {
            val client = container.httpClient.newBuilder().callTimeout(30, TimeUnit.SECONDS).build()
            val lines = mutableListOf<String>()
            fun emit(line: String) {
                Log.i("NetProbe", line)
                lines += line
                runOnUiThread { text.text = lines.joinToString("\n") }
            }
            for (path in PATHS) {
                val start = System.nanoTime()
                val line = runCatching {
                    client.newCall(Request.Builder().url("https://linux.do$path").build()).execute().use { r ->
                        val bytes = r.body?.bytes()?.size ?: 0
                        "${r.code} ${r.protocol} ${bytes}B ${r.header("Content-Type").orEmpty().substringBefore(';')}"
                    }
                }.getOrElse { "ERR ${it.javaClass.simpleName}: ${it.message}" }
                emit("$path -> $line (${(System.nanoTime() - start) / 1_000_000} ms)")
            }
            emit("goEch stats: ${container.goEchStatsOrNull()}")
            emit("DONE")
        }
    }

    private companion object {
        val PATHS = listOf(
            "/srv/status",
            "/latest.json",
            "/site.json",
            "/categories.json",
            "/user_avatar/linux.do/neo/96/12_2.png",
        )
    }
}
