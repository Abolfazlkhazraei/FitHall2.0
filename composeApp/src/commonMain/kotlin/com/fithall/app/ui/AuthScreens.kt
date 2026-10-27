package com.fithall.app.ui

import com.fithall.app.auth.SocialSignIn
import com.fithall.app.auth.SocialProvider
import com.fithall.app.auth.NoSocialSignIn
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fithall.app.auth.AuthUiState
import com.fithall.app.auth.AuthUser
import com.fithall.app.auth.AuthValidation
import com.fithall.app.auth.AuthViewModel
import com.fithall.app.platform.nowMillis
import kotlinx.coroutines.delay

/** Full-screen gradient with a centred column no wider than a phone, so tablets look right too. */
@Composable
private fun GradientPage(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Brand.gradient), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .widthIn(max = 480.dp)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

/** A white card on the gradient for forms. */
@Composable
private fun FormCard(content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.material3.Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun Logo() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.FitnessCenter, null, tint = Color.White)
        }
        HSpace(12.dp)
        Text("FitHall", style = MaterialTheme.typography.headlineSmall, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

// ---- Welcome ----

private data class WelcomePage(val icon: ImageVector, val title: String, val body: String)

private val welcomePages = listOf(
    WelcomePage(Icons.Filled.CameraAlt, "Snap your plate", "Take a photo, say what you ate or scan a barcode. FitHall estimates the calories and macros for you."),
    WelcomePage(Icons.Filled.AutoAwesome, "Plans built for you", "Your AI coach designs workouts and meal plans around your goal, experience, equipment and injuries."),
    WelcomePage(Icons.Filled.Insights, "Adjusts as you go", "Log workouts and weigh-ins. Each week FitHall reviews your progress and suggests changes you approve."),
)

/** First launch: three short pages about what FitHall does, then sign up or log in. */
@Composable
fun WelcomeScreen(onGetStarted: () -> Unit, onLogIn: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val last = page == welcomePages.lastIndex
    GradientPage {
        Logo()
        Spacer(Modifier.height(24.dp))
        AnimatedContent(targetState = page, label = "welcome") { index ->
            val item = welcomePages[index]
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.size(96.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(item.icon, null, tint = Color.White, modifier = Modifier.size(48.dp))
                }
                Text(item.title, style = MaterialTheme.typography.displaySmall, color = Color.White, fontWeight = FontWeight.Bold)
                Text(item.body, style = MaterialTheme.typography.bodyLarge, color = Brand.onGradientMuted)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            welcomePages.indices.forEach { i ->
                Box(
                    Modifier
                        .size(width = if (i == page) 24.dp else 8.dp, height = 8.dp)
                        .clip(CircleShape)
                        .background(if (i == page) Color.White else Color.White.copy(alpha = 0.4f))
                        .clickable { page = i },
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = { if (last) onGetStarted() else page++ },
            colors = ButtonDefaults.buttonColors(containerColor = Palette.lime, contentColor = Palette.onLime),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(if (last) "Create an account" else "Next", fontWeight = FontWeight.SemiBold)
        }
        TextButton(onClick = onLogIn, modifier = Modifier.fillMaxWidth()) {
            Text("I already have an account", color = Color.White)
        }
    }
}

// ---- Log in / sign up / reset ----

enum class AuthMode { LOG_IN, SIGN_UP, RESET }

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    initialMode: AuthMode,
    privacyPolicyUrl: String,
    termsUrl: String,
    social: SocialSignIn = NoSocialSignIn,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var mode by rememberSaveable { mutableStateOf(initialMode) }
    var email by rememberSaveable { mutableStateOf("") }
    // Passwords aren't saved across process death on purpose.
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var acceptedTerms by rememberSaveable { mutableStateOf(false) }
    // Field errors appear once the user has tried to submit, not while they're still typing.
    var submitted by rememberSaveable(mode) { mutableStateOf(false) }

    fun switchTo(next: AuthMode) {
        mode = next
        password = ""
        confirm = ""
        viewModel.clearMessages()
    }

    fun submit() {
        submitted = true
        when (mode) {
            AuthMode.LOG_IN -> viewModel.signIn(email, password)
            AuthMode.SIGN_UP -> viewModel.signUp(email, password, confirm, acceptedTerms)
            AuthMode.RESET -> viewModel.sendPasswordReset(email)
        }
    }

    GradientPage {
        Logo()
        Text(
            when (mode) {
                AuthMode.LOG_IN -> "Welcome back"
                AuthMode.SIGN_UP -> "Create your account"
                AuthMode.RESET -> "Reset your password"
            },
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
        if (mode == AuthMode.RESET) {
            Text("Enter your account's email and we'll send you a link to choose a new password.", color = Brand.onGradientMuted)
        }
        if (mode != AuthMode.RESET && social.available.isNotEmpty()) {
            social.available.forEach { provider ->
                SocialButton(provider, enabled = !ui.busy) {
                    submitted = false
                    viewModel.signInWith(provider, social)
                }
            }
            LegalLine(termsUrl, privacyPolicyUrl)
            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.weight(1f), color = Palette.line)
                Text("or with email", style = MaterialTheme.typography.labelMedium, color = Brand.onGradientMuted, modifier = Modifier.padding(horizontal = 12.dp))
                HorizontalDivider(Modifier.weight(1f), color = Palette.line)
            }
        }
        FormCard {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it.take(254) },
                label = { Text("Email") },
                leadingIcon = { Icon(Icons.Filled.Email, null) },
                singleLine = true,
                isError = submitted && AuthValidation.email(email) != null,
                supportingText = if (submitted) AuthValidation.email(email)?.let { { Text(it) } } else null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = if (mode == AuthMode.RESET) ImeAction.Done else ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                enabled = !ui.busy,
                modifier = Modifier.fillMaxWidth(),
            )
            if (mode != AuthMode.RESET) {
                PasswordField(
                    value = password,
                    onChange = { password = it },
                    label = "Password",
                    error = when {
                        !submitted -> null
                        mode == AuthMode.SIGN_UP -> AuthValidation.newPassword(password)
                        password.isEmpty() -> "Enter your password."
                        else -> null
                    },
                    hint = if (mode == AuthMode.SIGN_UP) "At least ${AuthValidation.MIN_PASSWORD} characters, with letters and numbers" else null,
                    last = mode == AuthMode.LOG_IN,
                    onDone = ::submit,
                    enabled = !ui.busy,
                )
            }
            if (mode == AuthMode.SIGN_UP) {
                PasswordField(
                    value = confirm,
                    onChange = { confirm = it },
                    label = "Confirm password",
                    error = if (submitted) AuthValidation.confirmation(password, confirm) else null,
                    last = true,
                    onDone = ::submit,
                    enabled = !ui.busy,
                )
                TermsCheckbox(acceptedTerms, { acceptedTerms = it }, termsUrl, privacyPolicyUrl, showError = submitted && !acceptedTerms)
            }
            if (mode == AuthMode.LOG_IN) {
                TextButton(onClick = { switchTo(AuthMode.RESET) }, modifier = Modifier.align(Alignment.End)) {
                    Text("Forgot password?")
                }
            }
            // Field problems already show under their field; this is for everything else.
            val fieldErrors = if (submitted) listOfNotNull(
                AuthValidation.email(email),
                if (mode == AuthMode.SIGN_UP) AuthValidation.newPassword(password) else null,
                if (mode == AuthMode.SIGN_UP) AuthValidation.confirmation(password, confirm) else null,
                if (mode == AuthMode.LOG_IN && password.isEmpty()) "Enter your password." else null,
            ) else emptyList()
            Messages(if (ui.error in fieldErrors) ui.copy(error = null) else ui)
            Button(onClick = ::submit, enabled = !ui.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                if (ui.busy) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text(
                        when (mode) {
                            AuthMode.LOG_IN -> "Log in"
                            AuthMode.SIGN_UP -> "Create account"
                            AuthMode.RESET -> "Send reset link"
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        val (question, action, target) = when (mode) {
            AuthMode.LOG_IN -> Triple("New to FitHall?", "Create an account", AuthMode.SIGN_UP)
            AuthMode.SIGN_UP -> Triple("Already have an account?", "Log in", AuthMode.LOG_IN)
            AuthMode.RESET -> Triple("Remembered it?", "Back to log in", AuthMode.LOG_IN)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(question, color = Brand.onGradientMuted)
            TextButton(onClick = { switchTo(target) }, enabled = !ui.busy) {
                Text(action, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** "Continue with Google" / "Continue with Facebook", in each brand's own colours. */
@Composable
private fun SocialButton(provider: SocialProvider, enabled: Boolean, onClick: () -> Unit) {
    val (container, content) = when (provider) {
        // Google's dark-theme button: near-black with a light outline.
        SocialProvider.GOOGLE -> Color(0xFF131314) to Color(0xFFE3E3E3)
        SocialProvider.FACEBOOK -> Color(0xFF1877F2) to Color.White
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(26.dp),
        color = container,
        contentColor = content,
        border = if (provider == SocialProvider.GOOGLE) BorderStroke(1.dp, Color(0xFF8E918F)) else null,
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            when (provider) {
                SocialProvider.GOOGLE -> Icon(GoogleLogo, null, tint = Color.Unspecified, modifier = Modifier.size(20.dp))
                SocialProvider.FACEBOOK -> Box(
                    Modifier.size(22.dp).clip(CircleShape).background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("f", color = Color(0xFF1877F2), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, modifier = Modifier.offset(x = 1.dp, y = 1.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Text("Continue with ${provider.label}", style = MaterialTheme.typography.titleSmall)
        }
    }
}

/** Google's "G", from its sign-in branding guidelines. */
private val GoogleLogo: ImageVector by lazy {
    ImageVector.Builder("GoogleLogo", 20.dp, 20.dp, 48f, 48f).apply {
        addPath(addPathNodes("M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"), fill = SolidColor(Color(0xFFEA4335)))
        addPath(addPathNodes("M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"), fill = SolidColor(Color(0xFF4285F4)))
        addPath(addPathNodes("M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"), fill = SolidColor(Color(0xFFFBBC05)))
        addPath(addPathNodes("M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"), fill = SolidColor(Color(0xFF34A853)))
    }.build()
}

/** Signing up with Google or Facebook skips the terms checkbox, so the same consent is stated here. */
@Composable
private fun LegalLine(termsUrl: String, privacyUrl: String) {
    val uriHandler = LocalUriHandler.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            "By continuing with Google or Facebook, you confirm you're 18 or older and agree to the Terms of Use and Privacy Policy.",
            style = MaterialTheme.typography.bodySmall,
            color = Brand.onGradientMuted,
            textAlign = TextAlign.Center,
        )
        Row {
            if (termsUrl.isNotBlank()) TextButton(onClick = { uriHandler.openUri(termsUrl) }) { Text("Terms") }
            if (privacyUrl.isNotBlank()) TextButton(onClick = { uriHandler.openUri(privacyUrl) }) { Text("Privacy") }
        }
    }
}

@Composable
private fun PasswordField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    error: String?,
    last: Boolean,
    onDone: () -> Unit,
    enabled: Boolean,
    hint: String? = null,
) {
    var visible by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.take(AuthValidation.MAX_PASSWORD)) },
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = (error ?: hint)?.let { { Text(it) } },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "Hide password" else "Show password",
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = if (last) ImeAction.Done else ImeAction.Next),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TermsCheckbox(checked: Boolean, onChange: (Boolean) -> Unit, termsUrl: String, privacyUrl: String, showError: Boolean) {
    val uriHandler = LocalUriHandler.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }) {
            Checkbox(checked = checked, onCheckedChange = onChange)
            Text(
                "I'm 18 or older and agree to the Terms of Use and Privacy Policy.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (showError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        Row(Modifier.padding(start = 40.dp)) {
            if (termsUrl.isNotBlank()) TextButton(onClick = { uriHandler.openUri(termsUrl) }) { Text("Read the terms") }
            if (privacyUrl.isNotBlank()) TextButton(onClick = { uriHandler.openUri(privacyUrl) }) { Text("Privacy policy") }
        }
    }
}

@Composable
private fun Messages(ui: AuthUiState) {
    ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    ui.info?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
}

// ---- Email verification ----

@Composable
fun VerifyEmailScreen(user: AuthUser, viewModel: AuthViewModel, onUseAnotherAccount: () -> Unit) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    // Ticks once a second so the resend button counts down.
    var now by remember { mutableStateOf(nowMillis()) }
    LaunchedEffect(ui.resendAllowedAtMillis) {
        while (nowMillis() < ui.resendAllowedAtMillis) {
            now = nowMillis()
            delay(1_000)
        }
        now = nowMillis()
    }
    val wait = ((ui.resendAllowedAtMillis - now + 999) / 1000).coerceAtLeast(0)

    GradientPage {
        Logo()
        Box(Modifier.size(96.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.MarkEmailRead, null, tint = Color.White, modifier = Modifier.size(48.dp))
        }
        Text("Check your email", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
        Text(
            "We sent a verification link to ${user.email}. Tap it to confirm your address, then come back here.",
            style = MaterialTheme.typography.bodyLarge,
            color = Brand.onGradientMuted,
        )
        FormCard {
            Messages(ui)
            Button(onClick = viewModel::checkVerified, enabled = !ui.busy, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                if (ui.busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("I've verified my email", fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(onClick = viewModel::resendVerification, enabled = !ui.busy && wait == 0L, modifier = Modifier.fillMaxWidth()) {
                Text(if (wait > 0) "Resend email in ${wait}s" else "Resend email")
            }
            Text(
                "Can't find it? Check your spam or promotions folder.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        TextButton(onClick = onUseAnotherAccount, modifier = Modifier.fillMaxWidth()) {
            Text("Use a different email", color = Color.White)
        }
    }
}

/** Shown while the app works out who's signed in. */
@Composable
fun LoadingScreen() {
    Box(Modifier.fillMaxSize().background(Brand.gradient), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.White)
    }
}

// ---- Profile → Account ----

@Composable
fun AccountSection(
    email: String,
    authViewModel: AuthViewModel,
    onLogOut: () -> Unit,
    wipeLocalData: () -> Unit,
    /** Set for Google and Facebook accounts, which confirm deletion with that provider instead of a password. */
    provider: SocialProvider? = null,
    social: SocialSignIn = NoSocialSignIn,
) {
    val ui by authViewModel.ui.collectAsStateWithLifecycle()
    var confirmLogOut by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Account", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Filled.Email, MaterialTheme.colorScheme.primary, size = 36.dp)
            HSpace(12.dp)
            Column(Modifier.weight(1f)) {
                Text(email, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                Text(
                    provider?.let { "Signed in with ${it.label}" } ?: "Signed in",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedButton(onClick = { confirmLogOut = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Filled.Logout, null)
            Spacer(Modifier.width(8.dp))
            Text("Log out")
        }
        TextButton(
            onClick = { authViewModel.clearMessages(); password = ""; deleting = true },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.DeleteForever, null)
            Spacer(Modifier.width(8.dp))
            Text("Delete account")
        }
    }

    if (confirmLogOut) {
        AlertDialog(
            onDismissRequest = { confirmLogOut = false },
            title = { Text("Log out?") },
            text = { Text("Your logs stay on this phone and will be here when you log back in with $email.") },
            confirmButton = { TextButton(onClick = { confirmLogOut = false; onLogOut() }) { Text("Log out") } },
            dismissButton = { TextButton(onClick = { confirmLogOut = false }) { Text("Cancel") } },
        )
    }

    if (deleting) {
        AlertDialog(
            onDismissRequest = { if (!ui.busy) deleting = false },
            title = { Text("Delete your account?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "This permanently deletes your FitHall account and everything FitHall stores on this phone for it: " +
                            "profile, meals, workouts, weigh-ins, plans, check-ins and coach chat. It can't be undone.",
                    )
                    if (provider != null) {
                        Text("To confirm, sign in with ${provider.label} again.", fontWeight = FontWeight.SemiBold)
                        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    } else {
                        PasswordField(
                            value = password,
                            onChange = { password = it },
                            label = "Password",
                            error = ui.error,
                            last = true,
                            onDone = { authViewModel.deleteAccount(password, wipeLocalData) },
                            enabled = !ui.busy,
                        )
                        Text(
                            "Forgot your password? Log out, tap \"Forgot password?\" to reset it, then come back here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (provider != null) authViewModel.deleteAccount(provider, social, wipeLocalData)
                        else authViewModel.deleteAccount(password, wipeLocalData)
                    },
                    enabled = !ui.busy,
                ) {
                    if (ui.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text(provider?.let { "Confirm with ${it.label}" } ?: "Delete account", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleting = false }, enabled = !ui.busy) { Text("Cancel") } },
        )
    }
}
