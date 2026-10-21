package org.linuxdo.android.ui.controller

import android.app.Application
import android.widget.Toast
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.linuxdo.android.AppContainer
import org.linuxdo.android.BuildConfig
import org.linuxdo.android.Config
import org.linuxdo.android.net.HttpStatusException
import org.linuxdo.android.service.ApkUpdateInstaller
import org.linuxdo.android.ui.ApkUpdateStage
import org.linuxdo.android.ui.UpdateInfo
import org.linuxdo.android.ui.UpdateUiState
import org.linuxdo.android.ui.controller.UpdateManifestParser.compareVersions

/** 应用版本检查与应用内下载安装。 */
class UpdateController(
    private val app: Application,
    private val scope: CoroutineScope,
    private val container: AppContainer,
) {
    private val diagnostics = container.diagnostics

    private val _state = MutableStateFlow(UpdateUiState(updateInfo = loadCachedUpdateInfo()))
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var apkUpdateJob: Job? = null

    private fun loadCachedUpdateInfo(): UpdateInfo? {
        val prefs = container.preferences
        val latestVersion = prefs.getString("cached_update_version", null)?.takeIf { it.isNotBlank() } ?: return null
        val currentVersion = BuildConfig.VERSION_NAME.substringBefore("-")
        if (compareVersions(latestVersion, currentVersion) <= 0) {
            clearCachedUpdateInfo()
            return null
        }
        return UpdateInfo(
            latestVersion = latestVersion,
            title = prefs.getString("cached_update_title", "").orEmpty(),
            changelog = prefs.getString("cached_update_changelog", "").orEmpty(),
            downloadUrl = prefs.getString("cached_update_download_url", "").orEmpty(),
            releasePageUrl = prefs.getString("cached_update_release_url", "").orEmpty(),
            mandatory = prefs.getBoolean("cached_update_mandatory", false),
            apkSha256 = prefs.getString("cached_update_apk_sha256", null),
        )
    }

    private fun saveCachedUpdateInfo(info: UpdateInfo) {
        container.preferences.edit {
            putString("cached_update_version", info.latestVersion)
            putString("cached_update_title", info.title)
            putString("cached_update_changelog", info.changelog)
            putString("cached_update_download_url", info.downloadUrl)
            putString("cached_update_release_url", info.releasePageUrl)
            putBoolean("cached_update_mandatory", info.mandatory)
            putString("cached_update_apk_sha256", info.apkSha256)
            putLong("cached_update_check_time", System.currentTimeMillis())
        }
    }

    private fun clearCachedUpdateInfo() {
        container.preferences.edit {
            remove("cached_update_version")
            remove("cached_update_title")
            remove("cached_update_changelog")
            remove("cached_update_download_url")
            remove("cached_update_release_url")
            remove("cached_update_mandatory")
            remove("cached_update_apk_sha256")
            remove("cached_update_check_time")
        }
    }

    fun onVersionItemClick() {
        if (_state.value.updateInfo != null) {
            _state.update { it.copy(showDialog = true) }
        } else {
            checkForUpdates(manual = true)
        }
    }

    fun dismissUpdateDialog(ignoreThisVersion: Boolean = false) {
        val info = _state.value.updateInfo ?: return
        if (info.mandatory) return
        if (ignoreThisVersion) {
            container.preferences.edit { putString("ignored_update_version", info.latestVersion) }
        }
        // 关掉对话框就不该再占着带宽继续下。
        cancelApkUpdate()
        _state.update { it.copy(showDialog = false) }
    }

    /**
     * 应用内下载新版本安装包并拉起系统安装器。
     *
     * 跑在 ViewModel 作用域而不是前台服务:包体不大,正常网络数秒完成,用户就停在对话框上。
     * 进程被杀则下载中断,重开再下即可,不值得为此多拉一个常驻载体。
     *
     * Android 不允许第三方静默安装,所以这里最多做到「下完直接弹安装确认」。
     */
    fun startApkUpdate() {
        val info = _state.value.updateInfo ?: return
        if (apkUpdateJob?.isActive == true) return

        // 「安装未知应用」是 Android 8.0 起的特殊权限,Manifest 声明不等于已授予。
        if (!ApkUpdateInstaller.canInstall(app)) {
            _state.update { it.copy(apkStage = ApkUpdateStage.AwaitingPermission, apkError = null) }
            return
        }

        apkUpdateJob = scope.launch {
            _state.update {
                it.copy(
                    apkStage = ApkUpdateStage.Downloading,
                    apkDownloadedBytes = 0L,
                    apkTotalBytes = 0L,
                    apkError = null,
                )
            }
            val result = ApkUpdateInstaller.download(
                context = app,
                url = info.downloadUrl,
                expectedSha256 = info.apkSha256,
            ) { downloaded, total ->
                // 字节读完但方法还没返回,这段时间正是在算摘要,如实显示为校验中。
                val verifying = total > 0L && downloaded >= total && !info.apkSha256.isNullOrBlank()
                _state.update {
                    it.copy(
                        apkStage = if (verifying) ApkUpdateStage.Verifying else ApkUpdateStage.Downloading,
                        apkDownloadedBytes = downloaded,
                        apkTotalBytes = total,
                    )
                }
            }
            when (result) {
                is ApkUpdateInstaller.Result.Success -> {
                    _state.update { it.copy(apkStage = ApkUpdateStage.Installing, apkError = null) }
                    if (!ApkUpdateInstaller.install(app, result.file)) {
                        _state.update {
                            it.copy(apkStage = ApkUpdateStage.Failed, apkError = "无法拉起安装程序，请改用浏览器下载。")
                        }
                    }
                }
                is ApkUpdateInstaller.Result.Failure -> {
                    _state.update { it.copy(apkStage = ApkUpdateStage.Failed, apkError = result.message) }
                }
            }
        }
    }

    /** 用户返回授权页后重新发起;权限仍未授予时会再次回到等待授权态。 */
    fun retryApkUpdateAfterPermission() {
        _state.update { it.copy(apkStage = ApkUpdateStage.Idle, apkError = null) }
        startApkUpdate()
    }

    fun cancelApkUpdate() {
        apkUpdateJob?.cancel()
        apkUpdateJob = null
        _state.update {
            it.copy(
                apkStage = ApkUpdateStage.Idle,
                apkDownloadedBytes = 0L,
                apkTotalBytes = 0L,
                apkError = null,
            )
        }
    }

    /**
     * 检查最新版本:优先读取公开仓库根目录的 `version.json` 静态清单,
     * 不可达时回退到 GitHub Releases 接口,再不行就从 Releases 页面的跳转里取 tag。
     * 必升级的判定规则见 [UpdateManifestParser]。
     */
    fun checkForUpdates(manual: Boolean = false) {
        if (_state.value.checking) {
            if (manual) scope.launch { toast("正在检查更新中，请稍候...") }
            return
        }
        scope.launch {
            _state.update {
                it.copy(
                    checking = true,
                    manualChecking = manual,
                    statusMessage = if (manual) "正在检查新版本..." else it.statusMessage,
                )
            }
            try {
                val currentVersion = _state.value.currentVersion
                val currentCode = _state.value.currentVersionCode
                val info = withContext(Dispatchers.IO) { fetchLatest(currentVersion, currentCode) }
                if (info != null) {
                    saveCachedUpdateInfo(info)
                    val ignoredVersion = container.preferences.getString("ignored_update_version", null)
                    val shouldPopup = info.mandatory || manual || ignoredVersion != info.latestVersion
                    diagnostics.log("检测到新版本: v${info.latestVersion} (必升=${info.mandatory})")
                    _state.update {
                        it.copy(
                            checking = false,
                            manualChecking = false,
                            updateInfo = info,
                            showDialog = shouldPopup,
                            statusMessage = "发现新版本 v${info.latestVersion}",
                        )
                    }
                } else {
                    clearCachedUpdateInfo()
                    diagnostics.log("版本检查完成: 当前已是最新 (v$currentVersion)")
                    _state.update {
                        it.copy(
                            checking = false,
                            manualChecking = false,
                            updateInfo = null,
                            showDialog = false,
                            statusMessage = if (manual) "当前已是最新版本 (v$currentVersion)" else null,
                        )
                    }
                    if (manual) toast("当前已是最新版本 (v$currentVersion)")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                diagnostics.log("检查更新失败: ${error.message}")
                _state.update {
                    it.copy(
                        checking = false,
                        manualChecking = false,
                        statusMessage = if (manual) "检查更新失败，请稍后重试" else it.statusMessage,
                    )
                }
                if (manual) toast("检查更新失败，请检查网络")
            }
        }
    }

    private fun fetchLatest(currentVersion: String, currentCode: Int): UpdateInfo? {
        for (manifestUrl in Config.UPDATE_VERSION_JSON_URLS) {
            val manifestJson = runCatching {
                get(manifestUrl, "application/vnd.github.raw+json, application/json;q=0.9", noCache = true)
            }.getOrNull()
            if (!manifestJson.isNullOrBlank()) {
                val (parsed, manifestInfo) = UpdateManifestParser.parseVersionManifest(manifestJson, currentVersion, currentCode)
                if (parsed) return manifestInfo
            }
        }

        val (_, localManifestInfo) = UpdateManifestParser.parseVersionManifest(
            Config.FALLBACK_VERSION_MANIFEST_JSON,
            currentVersion,
            currentCode,
        )
        if (localManifestInfo != null) return localManifestInfo

        val apiJson = runCatching { get(Config.UPDATE_RELEASES_API_URL, "application/vnd.github+json") }.getOrNull()
        if (!apiJson.isNullOrBlank()) {
            return UpdateManifestParser.parseReleaseUpdateInfo(apiJson, currentVersion, currentCode)
        }

        // 兜底:api.github.com 匿名限流或连接受阻时,直接请求 Releases 页面,借 302 跳转取最新 tag。
        val req = request(Config.UPDATE_RELEASES_PAGE_URL, "text/html,application/xhtml+xml")
        return container.httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw HttpStatusException(resp.code, "GitHub Releases HTTP ${resp.code}")
            val finalUrl = resp.request.url.toString()
            val html = resp.body?.string().orEmpty()
            UpdateManifestParser.parseReleaseHtmlFallback(finalUrl, html, currentVersion, currentCode)
        }
    }

    private fun request(url: String, accept: String, noCache: Boolean = false): Request =
        Request.Builder()
            .url(url)
            .header("Accept", accept)
            .apply { if (noCache) header("Cache-Control", "no-cache") }
            .header("User-Agent", container.userAgent)
            .get()
            .build()

    private fun get(url: String, accept: String, noCache: Boolean = false): String =
        container.httpClient.newCall(request(url, accept, noCache)).execute().use { resp ->
            if (!resp.isSuccessful) throw HttpStatusException(resp.code, "HTTP ${resp.code}: $url")
            resp.body?.string().orEmpty()
        }

    private suspend fun toast(message: String) = withContext(Dispatchers.Main) {
        Toast.makeText(app, message, Toast.LENGTH_SHORT).show()
    }
}
