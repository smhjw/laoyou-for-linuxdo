package org.linuxdo.android.ui.controller

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.linuxdo.android.AppContainer
import org.linuxdo.android.Config
import org.linuxdo.android.ui.LoginUiState

/**
 * 登录:账号密码、邮件登录、网页授权(User API Key)。
 *
 * 原生界面只收集凭据;账号密码、hCaptcha、Cloudflare 与 2FA 均在同一个认证 WebView 里提交。
 * 先走 OkHttp 再让 WebView 重提会拆开浏览器指纹和 h_captcha_temp_id,导致过盾后无法续交。
 */
class AuthController(
    private val app: Application,
    private val scope: CoroutineScope,
    private val container: AppContainer,
    private val isDarkMode: () -> Boolean,
    /** 会话已建立。[enterList] 为 true 时先切到列表页再重新启动流程(邮件链接登录)。 */
    private val onSignedIn: (enterList: Boolean) -> Unit,
) {
    private val store = container.sessionStore
    private val browser = container.browser
    private val userApiKeyAuthService = container.userApiKeyAuthService
    private val diagnostics = container.diagnostics

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private fun initialState() = LoginUiState(
        savedLogin = store.savedLogin().orEmpty(),
        savedPassword = store.savedPassword().orEmpty(),
        rememberPassword = store.isRememberPassword(),
    )

    /** 登录完成后清空表单状态。 */
    fun reset() {
        _state.value = LoginUiState()
    }

    /** 登出后回到带「记住密码」回填的初始表单。 */
    fun resetAfterLogout() {
        _state.value = initialState()
    }

    fun dismissError() {
        _state.update { it.copy(error = null, emailSent = false, isRiskOrCaptchaBlocked = false) }
    }

    fun openWebLoginModal(
        login: String? = null,
        password: String? = null,
        secondFactorToken: String? = null,
        secondFactorMethod: Int = 1,
        rememberPassword: Boolean = false,
    ) {
        val u = login.orEmpty()
        val p = password.orEmpty()
        _state.update {
            it.copy(loading = false, sendingCode = false, error = null, isRiskOrCaptchaBlocked = false, showWebLoginDialog = true)
        }
        val onResult: (Boolean, String?, Boolean) -> Unit = { success, errorMsg, need2FA ->
            scope.launch {
                when {
                    success -> {
                        diagnostics.log("安全验证与网页登录全流程成功，正在同步会话...")
                        android.webkit.CookieManager.getInstance().flush()
                        store.syncFromWebView()
                        if (u.isNotBlank()) store.saveCredentials(u, p, rememberPassword)
                        _state.value = LoginUiState(
                            savedLogin = u,
                            savedPassword = if (rememberPassword) p else "",
                            rememberPassword = rememberPassword,
                        )
                        onSignedIn(false)
                    }
                    need2FA -> {
                        diagnostics.log("安全验证通过，需要进行二步验证 (2FA)")
                        _state.update { it.copy(showWebLoginDialog = false, secondFactorRequired = true, error = null) }
                    }
                    else -> {
                        diagnostics.log("安全验证或登录失败: $errorMsg")
                        _state.update {
                            it.copy(showWebLoginDialog = false, loading = false, error = errorMsg ?: "登录失败，请重试。")
                        }
                    }
                }
            }
        }
        if (!secondFactorToken.isNullOrBlank() && browser.resumeSecondFactorLogin(
                secondFactorToken = secondFactorToken,
                secondFactorMethod = secondFactorMethod,
                onResult = onResult,
            )
        ) {
            diagnostics.log("在原网页认证会话中续交 2FA 验证码")
            return
        }

        diagnostics.log("拉起内联安全认证弹框 (hCaptcha / CF / 2FA 全流程)")
        browser.loadCaptchaLogin(
            login = u,
            password = p,
            secondFactorToken = secondFactorToken,
            secondFactorMethod = secondFactorMethod,
            isDark = isDarkMode(),
            onResult = onResult,
        )
    }

    fun closeWebLoginModal() {
        browser.cancelCaptchaLogin()
        _state.update { it.copy(showWebLoginDialog = false) }
    }

    fun resetSecondFactor() {
        browser.discardPendingSecondFactorLogin()
        _state.update { it.copy(secondFactorRequired = false, backupEnabled = false, error = null) }
    }

    fun loginWithPassword(
        login: String,
        password: String,
        secondFactorToken: String? = null,
        secondFactorMethod: Int = 1,
        rememberPassword: Boolean = false,
    ) {
        if (_state.value.showWebLoginDialog) return
        diagnostics.log("原生表单已提交，转入同一 WebView 认证会话")
        openWebLoginModal(login, password, secondFactorToken, secondFactorMethod, rememberPassword)
    }

    /**
     * 发送邮箱一次性登录链接。与账号密码登录同构,**不先走原生 OkHttp**:
     * 站点的邮件接口在触发人机验证时返回裸 403,原生栈无法回答,CF 拦截时还要先耗满 20 秒超时。
     * 直接进入内联认证 WebView,由页面内 JS 依次完成 CSRF → hCaptcha → CF → 发信。
     */
    fun sendLoginEmail(email: String) {
        if (_state.value.sendingCode || _state.value.showWebLoginDialog) return
        if (email.isBlank()) {
            _state.update { it.copy(error = "请输入邮箱") }
            return
        }
        diagnostics.log("转入内联安全认证会话发送登录邮件")
        _state.update { it.copy(showWebLoginDialog = true, sendingCode = false, emailSent = false, error = null) }
        val onResult: (Boolean, String?, Boolean) -> Unit = { success, errorMsg, _ ->
            scope.launch {
                _state.update {
                    it.copy(
                        showWebLoginDialog = false,
                        sendingCode = false,
                        emailSent = success,
                        error = if (success) null else (errorMsg ?: "登录邮件发送失败，请重试。"),
                    )
                }
                if (success) diagnostics.log("登录邮件已发送（内联安全认证链路）")
            }
        }
        browser.loadCaptchaLogin(login = email, password = "", isDark = isDarkMode(), emailSendTo = email, onResult = onResult)
    }

    /**
     * 用邮件里的 token 完成登录。不在原生 OkHttp 里硬扛 CF/hCaptcha,
     * 而是让认证 WebView 整页打开完整链接,由站点自己的页面完成验证并种下 _t。
     */
    fun loginWithEmailCode(
        email: String,
        token: String,
        secondFactorToken: String? = null,
        secondFactorMethod: Int = 1,
    ) {
        if (_state.value.loading || _state.value.showWebLoginDialog) return
        val sanitizedToken = extractEmailLoginToken(token)
        if (sanitizedToken.isBlank()) {
            _state.update { it.copy(loading = false, error = "请输入邮件链接中的 token") }
            return
        }
        if (!sanitizedToken.matches(Regex("^[0-9a-fA-F]+$"))) {
            _state.update { it.copy(loading = false, error = "登录链接里的 token 格式不正确，请重新复制完整链接") }
            return
        }
        diagnostics.log("转入认证 WebView 整页打开邮件登录链接")
        _state.update { it.copy(showWebLoginDialog = true, loading = false, error = null) }
        val onResult: (Boolean, String?, Boolean) -> Unit = { success, errorMsg, _ ->
            scope.launch {
                if (success) {
                    diagnostics.log("邮件登录链接已在应用内完成兑换")
                    reset()
                    onSignedIn(true)
                } else {
                    _state.update {
                        it.copy(
                            showWebLoginDialog = false,
                            loading = false,
                            error = errorMsg ?: "登录链接打开后仍未完成登录，请重试或重新获取验证码。",
                        )
                    }
                }
            }
        }
        browser.loadEmailLinkLogin(
            url = "${Config.BASE_URL}/session/email-login/$sanitizedToken",
            isDark = isDarkMode(),
            onResult = onResult,
        )
    }

    fun dismissRiskControlDialog() {
        _state.update { it.copy(showRiskControlDialog = false) }
    }

    /** 发起网页授权认证(拉起系统浏览器或外部应用)。 */
    fun startWebAuth(context: Context? = null) {
        val targetContext = context ?: app
        scope.launch {
            _state.update {
                it.copy(showRiskControlDialog = false, webAuthLoading = true, webAuthMessage = "正在准备网页授权...", error = null)
            }
            try {
                val url = userApiKeyAuthService.buildAuthorizeUrl()
                targetContext.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                )
                _state.update { it.copy(webAuthLoading = true, webAuthMessage = "已调起浏览器，请在网页中完成授权...") }
            } catch (e: Exception) {
                diagnostics.log("拉起网页授权失败: ${e.message}")
                _state.update {
                    it.copy(webAuthLoading = false, webAuthMessage = null, error = "无法打开浏览器，请检查是否安装了浏览器应用")
                }
            }
        }
    }

    fun handleWebAuthCallback(callbackUri: String) {
        val uri = runCatching { Uri.parse(callbackUri) }.getOrNull() ?: return
        handleWebAuthCallback(uri)
    }

    /** 接收深链回调并兑换令牌。 */
    fun handleWebAuthCallback(uri: Uri) {
        scope.launch {
            _state.update { it.copy(webAuthLoading = true, webAuthMessage = "正在兑换登录令牌并验证...", error = null) }
            try {
                userApiKeyAuthService.handleAuthRedirect(uri)
                diagnostics.log("网页授权登录成功，重新加载会话")
                reset()
                onSignedIn(false)
            } catch (e: Exception) {
                diagnostics.log("网页授权登录兑换失败: ${e.message}")
                _state.update {
                    it.copy(webAuthLoading = false, webAuthMessage = null, error = e.message ?: "网页授权登录失败，请重试")
                }
            }
        }
    }

    fun cancelWebAuth() {
        userApiKeyAuthService.cancel()
        _state.update { it.copy(webAuthLoading = false, webAuthMessage = null) }
    }

    /**
     * 从用户粘贴的内容里取出邮件登录 token。用户多半直接复制整条链接
     * `https://linux.do/session/email-login/<token>`(可能带查询串或末尾斜杠);
     * 含标记就取标记之后的部分,否则原样当作 token。
     */
    private fun extractEmailLoginToken(raw: String): String {
        val text = raw.trim()
        if (text.isEmpty()) return ""
        val marker = "/session/email-login/"
        if (!text.contains(marker)) return text
        return text.substringAfterLast(marker).substringBefore('?').substringBefore('#').trim().trim('/')
    }
}
