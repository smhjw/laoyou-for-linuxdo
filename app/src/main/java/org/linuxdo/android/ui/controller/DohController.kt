package org.linuxdo.android.ui.controller

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.linuxdo.android.AppContainer
import org.linuxdo.android.net.DohPresets
import org.linuxdo.android.net.DohProbeEngine
import org.linuxdo.android.net.DohServer
import org.linuxdo.android.net.loadDohServers

/** 安全直连(DoH + ECH)的节点列表、开关与测速。 */
class DohController(
    private val scope: CoroutineScope,
    private val container: AppContainer,
) {
    private val store = container.sessionStore

    private val _enabled = MutableStateFlow(store.isDohEnabled())
    val enabled = _enabled.asStateFlow()

    private val _selectedServerUrl = MutableStateFlow(store.getDohServerUrl() ?: DohPresets.DEFAULT.url)
    val selectedServerUrl = _selectedServerUrl.asStateFlow()

    private val _autoFailover = MutableStateFlow(store.isDohAutoFailoverEnabled())
    val autoFailover = _autoFailover.asStateFlow()

    private val _autoOptimize = MutableStateFlow(store.isDohAutoOptimizeEnabled())
    val autoOptimize = _autoOptimize.asStateFlow()

    private val _goEchEnabled = MutableStateFlow(store.isGoEchEnabled())
    val goEchEnabled = _goEchEnabled.asStateFlow()

    /** 节点延迟:key = DohServer.id,value = 毫秒;null 表示测速中,-1 表示超时。 */
    private val _latencies = MutableStateFlow<Map<String, Long?>>(emptyMap())
    val latencies = _latencies.asStateFlow()

    /** 节点探测摘要(如 ECH 已接受 / 站点访问成功)。 */
    private val _summaries = MutableStateFlow<Map<String, String>>(emptyMap())
    val summaries = _summaries.asStateFlow()

    private val _testingAll = MutableStateFlow(false)
    val testingAll = _testingAll.asStateFlow()

    private val _servers = MutableStateFlow(loadDohServers(store))
    val servers = _servers.asStateFlow()

    init {
        container.dohCoordinator.onLatenciesUpdated = { latencies: Map<String, Long?>, summaries: Map<String, String>, selectedUrl: String ->
            _latencies.value = latencies
            _summaries.value = summaries
            _selectedServerUrl.value = selectedUrl
        }
    }

    fun setEnabled(enabled: Boolean) {
        store.setDohEnabled(enabled)
        _enabled.value = enabled
        container.rebuildHttpClient()
    }

    fun select(server: DohServer) {
        store.setDohServerUrl(server.url)
        _selectedServerUrl.value = server.url
        container.dohCoordinator.manualSelect(server)
    }

    fun setAutoFailover(enabled: Boolean) {
        store.setDohAutoFailoverEnabled(enabled)
        _autoFailover.value = enabled
        container.dohCoordinator.refresh()
    }

    /** 切换自研 ECH 内核;重建客户端后新请求立即生效。 */
    fun setGoEchEnabled(enabled: Boolean) {
        store.setGoEchEnabled(enabled)
        _goEchEnabled.value = enabled
        container.rebuildHttpClient()
    }

    /** 自研内核的诊断 JSON;还没初始化时为 null。 */
    fun goEchStats(): String? = container.goEchStatsOrNull()

    fun setAutoOptimize(enabled: Boolean) {
        store.setDohAutoOptimizeEnabled(enabled)
        _autoOptimize.value = enabled
        container.dohCoordinator.setAutoOptimizeEnabled(enabled)
    }

    fun test(server: DohServer) {
        scope.launch(Dispatchers.IO) {
            pausedReason()?.let { reason ->
                _latencies.update { it + (server.id to -1L) }
                _summaries.update { it + (server.id to reason) }
                return@launch
            }
            probe(server)
        }
    }

    fun testAll() {
        scope.launch(Dispatchers.IO) {
            _testingAll.value = true
            try {
                pausedReason()?.let { reason ->
                    _latencies.update { it + _servers.value.associate { s -> s.id to -1L } }
                    _summaries.update { it + _servers.value.associate { s -> s.id to reason } }
                    return@launch
                }
                _servers.value.map { server -> launch { probe(server) } }.forEach { it.join() }
            } finally {
                _testingAll.value = false
            }
        }
    }

    /** VPN / 系统代理开着时直连自动暂停,测速没有意义。 */
    private fun pausedReason(): String? {
        val netState = container.networkMonitor.detect()
        if (!netState.shouldPauseDoh) return null
        return if (netState.isVpn) "VPN 已开启，直连已暂停" else "系统代理已开启，直连已暂停"
    }

    private suspend fun probe(server: DohServer) {
        _latencies.update { it + (server.id to null) }
        val result = DohProbeEngine.probeDetailed(server)
        _latencies.update { it + (server.id to (result.latencyMs ?: -1L)) }
        _summaries.update { it + (server.id to result.summary) }
    }

    fun addCustom(url: String, name: String) {
        val existing = _servers.value
        val cleanUrl = url.trim()
        if (existing.any { it.url.trimEnd('/') == cleanUrl.trimEnd('/') }) return
        save(existing + DohServer(url = cleanUrl, name = name.trim().ifBlank { cleanUrl }, isCustom = true))
    }

    fun update(oldServer: DohServer, newUrl: String, newName: String) {
        val cleanUrl = newUrl.trim()
        val updatedServer = oldServer.copy(url = cleanUrl, name = newName.trim().ifBlank { cleanUrl })
        save(_servers.value.map { if (it.id == oldServer.id) updatedServer else it })
        if (_selectedServerUrl.value == oldServer.url) select(updatedServer)
    }

    fun delete(server: DohServer) {
        val remaining = _servers.value.filter { it.id != server.id }
        val finalList = remaining.ifEmpty { DohPresets.ALL }
        save(finalList)
        if (_selectedServerUrl.value == server.url) select(finalList.first())
    }

    fun resetToDefault() {
        store.setDohServersJson(null)
        store.setDohCustomServersJson(null)
        _servers.value = DohPresets.ALL
        select(DohPresets.DEFAULT)
        container.rebuildHttpClient()
    }

    private fun save(servers: List<DohServer>) {
        val json = JSONArray().apply {
            servers.forEach { s ->
                put(JSONObject().apply {
                    put("url", s.url)
                    put("name", s.name)
                    put("isCustom", s.isCustom)
                    if (s.bootstrapIps.isNotEmpty()) put("bootstrapIps", JSONArray(s.bootstrapIps))
                })
            }
        }
        store.setDohServersJson(json.toString())
        _servers.value = servers
        container.rebuildHttpClient()
    }
}
