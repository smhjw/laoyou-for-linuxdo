package org.linuxdo.android.ui.screen

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import org.linuxdo.android.ui.design.*

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.scale
import org.linuxdo.android.ui.LoginUiState
import org.linuxdo.android.ui.design.LoadingIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab

/** 原生登录页面:Material 3 表单,双 Tab(账号密码 / 验证码)。 */
@Composable
fun LoginScreen(
    state: LoginUiState,
    onPasswordLogin: (login: String, password: String, secondFactorToken: String?, secondFactorMethod: Int, rememberPassword: Boolean) -> Unit,
    onSendEmailCode: (email: String) -> Unit,
    onEmailCodeLogin: (email: String, token: String, secondFactorToken: String?, secondFactorMethod: Int) -> Unit,
    onDismissError: () -> Unit,
    onResetSecondFactor: () -> Unit = {},
    onOpenWebLogin: (login: String?, password: String?, secondFactorToken: String?, secondFactorMethod: Int, rememberPassword: Boolean) -> Unit = { _, _, _, _, _ -> },
    onStartWebAuth: () -> Unit = {},
    onDismissRiskControl: () -> Unit = {},
    onCancelWebAuth: () -> Unit = {},
) {
    // Tab 选择：0=账号密码，1=验证码
    var tab by rememberSaveable { mutableStateOf(0) }
    var showForgotDialog by rememberSaveable { mutableStateOf(false) }

    // 账号密码 Tab 的状态
    var rememberPassword by rememberSaveable { mutableStateOf(state.rememberPassword) }
    var loginText by rememberSaveable { mutableStateOf(state.savedLogin) }
    var passwordText by rememberSaveable { mutableStateOf(state.savedPassword) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    // 验证码 Tab 的状态
    var emailText by rememberSaveable { mutableStateOf("") }
    var codeText by rememberSaveable { mutableStateOf("") }

    // 两步验证 (2FA) 状态：1=TOTP 6位动态口令，2=备用恢复码
    var secondFactorText by rememberSaveable { mutableStateOf("") }
    var secondFactorMethod by rememberSaveable { mutableStateOf(1) }
    val secondFactorFocus = remember { FocusRequester() }

    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(state.secondFactorRequired) {
        if (state.secondFactorRequired) {
            runCatching { secondFactorFocus.requestFocus() }
        } else {
            secondFactorText = ""
            secondFactorMethod = 1
        }
    }

    // 错误自动消除
    LaunchedEffect(state.error) {
        if (state.error != null) { /* 由对话框展示 */ }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(indication = null, interactionSource = remember {
                androidx.compose.foundation.interaction.MutableInteractionSource()
            }) { focusManager.clearFocus() }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(72.dp))

            // Logo
            AppIconBadge()

            Spacer(Modifier.height(24.dp))

            // 标题
            Text(
                "欢迎登录",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "登录你的 LINUX DO 账号",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(32.dp))

            // Tab 切换
            LoginTabBar(selected = tab, onSelect = {
                if (tab != it) {
                    tab = it
                    secondFactorText = ""
                    onResetSecondFactor()
                }
            })

            Spacer(Modifier.height(24.dp))

            if (tab == 0) {
                // ── 账号密码登录 ──
                LoginField(
                    value = loginText,
                    onValueChange = {
                        loginText = it
                        if (state.secondFactorRequired) onResetSecondFactor()
                    },
                    placeholder = "用户名 / 手机号 / 邮箱",
                    leadingIcon = { UserIcon() },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )

                Spacer(Modifier.height(16.dp))

                LoginField(
                    value = passwordText,
                    onValueChange = {
                        passwordText = it
                        if (state.secondFactorRequired) onResetSecondFactor()
                    },
                    placeholder = "密码",
                    leadingIcon = { LockIcon() },
                    trailingContent = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            EyeIcon(open = passwordVisible)
                        }
                    },

                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = if (state.secondFactorRequired) ImeAction.Next else ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = {
                        keyboard?.hide(); focusManager.clearFocus()
                        if (loginText.isNotBlank() && passwordText.isNotBlank()) {
                            onPasswordLogin(
                                loginText.trim(),
                                passwordText,
                                secondFactorText.takeIf { state.secondFactorRequired },
                                secondFactorMethod,
                                rememberPassword,
                            )
                        }
                    }),
                )

                if (state.secondFactorRequired) {
                    Spacer(Modifier.height(16.dp))
                    SecondFactorSection(
                        value = secondFactorText,
                        onValueChange = { secondFactorText = it },
                        method = secondFactorMethod,
                        onMethodChange = {
                            secondFactorMethod = it
                            secondFactorText = ""
                        },
                        focusRequester = secondFactorFocus,
                        onDone = {
                            keyboard?.hide(); focusManager.clearFocus()
                            if (loginText.isNotBlank() && passwordText.isNotBlank() && secondFactorText.isNotBlank()) {
                                onPasswordLogin(
                                    loginText.trim(),
                                    passwordText,
                                    secondFactorText.trim(),
                                    secondFactorMethod,
                                    rememberPassword,
                                )
                            }
                        },
                    )
                }

                // 记住密码与忘记密码
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .toggleable(value = rememberPassword, role = Role.Checkbox) { rememberPassword = it }
                            .padding(end = 8.dp),
                    ) {
                        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                            RememberCheckBox(checked = rememberPassword)
                        }
                        Text("记住密码", style = MaterialTheme.typography.bodyMedium)
                    }

                    TextButton(onClick = { showForgotDialog = true }) { Text("忘记密码？") }
                }

                Spacer(Modifier.height(20.dp))

                val canSubmitPwd = loginText.isNotBlank() &&
                    passwordText.isNotBlank() &&
                    (!state.secondFactorRequired || secondFactorText.isNotBlank()) &&
                    !state.loading

                LoginButton(
                    text = if (state.secondFactorRequired) "验证并登录" else "登录",
                    loading = state.loading,
                    enabled = canSubmitPwd,
                    onClick = {
                        keyboard?.hide(); focusManager.clearFocus()
                        onPasswordLogin(
                            loginText.trim(),
                            passwordText,
                            secondFactorText.trim().takeIf { state.secondFactorRequired },
                            secondFactorMethod,
                            rememberPassword,
                        )
                    },
                )
            } else {
                // ── 验证码登录 ──
                LoginField(
                    value = emailText,
                    onValueChange = { emailText = it },
                    placeholder = "邮箱",
                    leadingIcon = { EmailIcon() },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next,
                    ),
                )

                Spacer(Modifier.height(16.dp))

                // 验证码行：输入框 + 获取按钮
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    LoginField(
                        value = codeText,
                        onValueChange = {
                            codeText = it
                            if (state.secondFactorRequired) onResetSecondFactor()
                        },
                        placeholder = "验证码或登录链接",
                        leadingIcon = { ShieldIcon() },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Ascii,
                            imeAction = if (state.secondFactorRequired) ImeAction.Next else ImeAction.Done,
                        ),

                        keyboardActions = KeyboardActions(onDone = {
                            keyboard?.hide(); focusManager.clearFocus()
                        }),
                    )

                    Spacer(Modifier.width(8.dp))

                    // 获取验证码按钮
                    SendCodeButton(
                        loading = state.sendingCode,
                        enabled = emailText.isNotBlank() && !state.sendingCode,
                        onClick = {
                            keyboard?.hide(); focusManager.clearFocus()
                            onSendEmailCode(emailText.trim())
                        },
                    )
                }

                if (state.secondFactorRequired) {
                    Spacer(Modifier.height(16.dp))
                    SecondFactorSection(
                        value = secondFactorText,
                        onValueChange = { secondFactorText = it },
                        method = secondFactorMethod,
                        onMethodChange = {
                            secondFactorMethod = it
                            secondFactorText = ""
                        },
                        focusRequester = secondFactorFocus,
                        onDone = {
                            keyboard?.hide(); focusManager.clearFocus()
                            if (emailText.isNotBlank() && codeText.isNotBlank() && secondFactorText.isNotBlank()) {
                                onEmailCodeLogin(
                                    emailText.trim(),
                                    codeText.trim(),
                                    secondFactorText.trim(),
                                    secondFactorMethod,
                                )
                            }
                        },
                    )
                }

                Spacer(Modifier.height(12.dp))

                // 提示文字
                Text(
                    "验证码将通过邮件发送。可直接粘贴邮件里的完整登录链接，或其中 /session/email-login/ 后面的 token",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                )

                Spacer(Modifier.height(20.dp))

                val canSubmitEmail = emailText.isNotBlank() &&
                    codeText.isNotBlank() &&
                    (!state.secondFactorRequired || secondFactorText.isNotBlank()) &&
                    !state.loading

                LoginButton(
                    text = if (state.secondFactorRequired) "验证并登录" else "登录",
                    loading = state.loading,
                    enabled = canSubmitEmail,
                    onClick = {
                        keyboard?.hide(); focusManager.clearFocus()
                        onEmailCodeLogin(
                            emailText.trim(),
                            codeText.trim(),
                            secondFactorText.trim().takeIf { state.secondFactorRequired },
                            secondFactorMethod,
                        )
                    },
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── 或 分割线 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = "或",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
            }

            Spacer(Modifier.height(20.dp))

            // ── 网页授权登录按钮 ──
            WebAuthButton(
                loading = state.webAuthLoading,
                enabled = !state.loading && !state.webAuthLoading,
                onClick = {
                    keyboard?.hide()
                    focusManager.clearFocus()
                    onStartWebAuth()
                },
            )

            Spacer(Modifier.height(40.dp))
        }
    }

    // 忘记密码提示对话框
    if (showForgotDialog) {
        AlertDialog(
            onDismissRequest = { showForgotDialog = false },
            title = { Text("忘记密码") },
            text = { Text("请在 web 端执行忘记密码操作。\n\n访问 linux.do 网页版，使用忘记密码功能重置后再回来登录。") },
            confirmButton = {
                TextButton(onClick = { showForgotDialog = false }) { Text("知道了") }
            },
        )
    }

    // 错误提示对话框
    if (state.error != null) {
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text(if (state.isRiskOrCaptchaBlocked) "安全验证提示" else "登录失败") },
            text = { Text(state.error) },
            confirmButton = {
                if (state.isRiskOrCaptchaBlocked) {
                    TextButton(onClick = {
                        onDismissError()
                        val u = if (tab == 0) loginText.trim() else emailText.trim()
                        val p = if (tab == 0) passwordText else null
                        val sf = secondFactorText.trim().takeIf { state.secondFactorRequired && it.isNotBlank() }
                        onOpenWebLogin(u, p, sf, secondFactorMethod, rememberPassword)
                    }) {
                        Text("进入安全验证登录")
                    }
                } else {
                    TextButton(onClick = onDismissError) { Text("确定") }
                }
            },
            dismissButton = if (state.isRiskOrCaptchaBlocked) {
                { TextButton(onClick = onDismissError) { Text("取消") } }
            } else null,
        )
    }

    // 邮件已发送提示
    if (state.emailSent) {
        AlertDialog(
            onDismissRequest = onDismissError,
            title = { Text("邮件已发送") },
            text = { Text("登录邮件已发送至您的邮箱。\n\n可打开邮件直接复制整条登录链接，粘贴到验证码输入框后点击登录（也可以只粘贴 /session/email-login/ 后面的 token）。") },
            confirmButton = {
                TextButton(onClick = onDismissError) { Text("知道了") }
            },
        )
    }

    // L站安全风控提示对话框（403）
    if (state.showRiskControlDialog) {
        AlertDialog(
            onDismissRequest = onDismissRiskControl,
            title = { Text("安全验证未通过") },
            text = {
                Text(
                    "已尝试在应用内完成人机验证，但站点仍在拦截当前请求。\n\n" +
                        "可稍后重试，或改用网页授权认证完成登录。",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDismissRiskControl()
                    onStartWebAuth()
                }) {
                    Text("网页授权认证")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissRiskControl) {
                    Text("取消")
                }
            },
        )
    }

    // 网页授权进行中提示
    if (state.webAuthLoading) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("网页授权认证") },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                ) {
                    LoadingIndicator(diameter = 28.dp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        state.webAuthMessage ?: "正在进行网页授权认证...",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onCancelWebAuth) {
                    Text("取消")
                }
            },
        )
    }
}

// ── 两步验证 (2FA) 输入区域 ──────────────────────────────────────────

@Composable
private fun SecondFactorSection(
    value: String,
    onValueChange: (String) -> Unit,
    method: Int,
    onMethodChange: (Int) -> Unit,
    focusRequester: FocusRequester,
    onDone: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (method == 1) "两步验证 · 动态口令 (TOTP)" else "两步验证 · 备用恢复码",
                    style = MaterialTheme.typography.titleSmall,
                )
                TextButton(onClick = { onMethodChange(if (method == 1) 2 else 1) }) {
                    Text(if (method == 1) "改用备用码" else "改用动态口令")
                }
            }
            LoginField(
                value = value,
                onValueChange = onValueChange,
                placeholder = if (method == 1) "6 位身份验证器验证码" else "备用恢复码",
                leadingIcon = { ShieldIcon() },
                modifier = Modifier.focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (method == 1) KeyboardType.NumberPassword else KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
            )
        }
    }
}

// ── Tab 切换栏 ──────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoginTabBar(selected: Int, onSelect: (Int) -> Unit) {
    PrimaryTabRow(
        selectedTabIndex = selected,
        containerColor = Color.Transparent,
        divider = {},
    ) {
        listOf("账号密码登录", "验证码登录").forEachIndexed { index, label ->
            Tab(
                selected = selected == index,
                onClick = { onSelect(index) },
                text = { Text(label, style = MaterialTheme.typography.titleSmall) },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── 输入框 ─────────────────────────────────────────────────────────

@Composable
private fun LoginField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(placeholder) },
        leadingIcon = leadingIcon,
        trailingIcon = trailingContent,
        singleLine = true,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.fillMaxWidth(),
    )
}

// ── 登录按钮 ───────────────────────────────────────────────────────

@Composable
private fun LoginButton(
    text: String,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        if (loading) {
            LoadingIndicator(diameter = 20.dp, color = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.width(12.dp))
        }
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

// ── 获取验证码按钮 ─────────────────────────────────────────────────

@Composable
private fun SendCodeButton(
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilledTonalButton(onClick = onClick, enabled = enabled, modifier = Modifier.height(56.dp)) {
        if (loading) LoadingIndicator(diameter = 18.dp) else Text("获取验证码")
    }
}

// ── App 图标徽章（复用 ic_launcher_foreground 路径） ───────────────

@Composable
private fun AppIconBadge() {
    val stemColor = MaterialTheme.colorScheme.onSurface
    Surface(
        modifier = Modifier.size(88.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // 与启动图标 ic_launcher_foreground 同一组路径;L 形主体跟随前景色,深色模式下也看得见。
            Canvas(Modifier.size(58.dp)) {
                scale(size.width / 108f, size.height / 108f, Offset.Zero) {
                    val p1 = Path().apply {
                        moveTo(34f, 27f); cubicTo(34f, 24f, 37f, 24f, 37f, 24f)
                        lineTo(44f, 24f); cubicTo(47f, 24f, 47f, 27f, 47f, 27f)
                        lineTo(47f, 63f); lineTo(70f, 63f); lineTo(49f, 84f)
                        lineTo(37f, 84f); cubicTo(34f, 84f, 34f, 81f, 34f, 81f); close()
                    }
                    drawPath(p1, stemColor)
                    val p2 = Path().apply {
                        moveTo(54f, 84f); lineTo(76f, 62f)
                        cubicTo(78f, 61f, 78f, 64f, 78f, 64f)
                        lineTo(78f, 81f); cubicTo(78f, 84f, 75f, 84f, 75f, 84f); close()
                    }
                    drawPath(p2, BrandBlue)
                }
            }
        }
    }
}

/** 启动图标里的品牌蓝。 */
private val BrandBlue = Color(0xFF4055DF)

// ── 小图标（Canvas 手绘，不依赖 Material Icons） ──────────────────

@Composable
private fun UserIcon() = FieldIcon(Icons.Outlined.Person)

@Composable
private fun FieldIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null)
}

@Composable
private fun LockIcon() = FieldIcon(Icons.Outlined.Lock)

@Composable
private fun EmailIcon() = FieldIcon(Icons.Outlined.Email)

@Composable
private fun ShieldIcon() = FieldIcon(Icons.Outlined.Key)

@Composable
private fun EyeIcon(open: Boolean) {
    Icon(
        if (open) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
        contentDescription = if (open) "隐藏密码" else "显示密码",
    )
}

// ── 网页授权登录相关组件 ──────────────────────────────────────────

@Composable
private fun WebAuthButton(
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        if (loading) LoadingIndicator(diameter = 18.dp)
        else Icon(Icons.Outlined.Language, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (loading) "正在授权..." else "网页授权登录", style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun RememberCheckBox(checked: Boolean) {
    // 整行(含文字)负责切换,复选框本身只显示状态。
    Checkbox(checked = checked, onCheckedChange = null)
}
