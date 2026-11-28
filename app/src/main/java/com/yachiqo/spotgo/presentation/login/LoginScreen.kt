package com.yachiqo.spotgo.presentation.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yachiqo.spotgo.R
import com.yachiqo.spotgo.data.repository.AuthError
import com.yachiqo.spotgo.data.repository.AuthResult
import com.yachiqo.spotgo.data.repository.AuthRepository
import com.yachiqo.spotgo.data.repository.GoogleLinkResult
import com.yachiqo.spotgo.presentation.common.SpotGoWordmark
import com.yachiqo.spotgo.presentation.common.FigmaIcon
import com.yachiqo.spotgo.presentation.common.StateBottomSheet
import com.yachiqo.spotgo.ui.theme.SpotGoSupportingText
import com.yachiqo.spotgo.ui.theme.SpotGoTheme
import com.yachiqo.spotgo.ui.theme.SpotGoWarning
import com.yachiqo.spotgo.data.remote.EncryptedPreferences

import kotlinx.coroutines.launch
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onOpenDriver: () -> Unit,
    onCreateAccount: () -> Unit,
    onForgotPassword: (String) -> Unit,
    onCompleteProfile: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val credentialStore = remember(context) {
        EncryptedPreferences(context.applicationContext, "spotgo_remembered_login", "spotgo.login.v1")
    }
    val savedLogin = remember(credentialStore) {
        credentialStore.read("login")?.let { runCatching { JSONObject(it) }.getOrNull() }
    }
    val email = rememberTextFieldState(initialText = savedLogin?.optString("email").orEmpty())
    // Keep the visible password out of Android's saved instance state.
    val password = remember { TextFieldState(initialText = savedLogin?.optString("password").orEmpty()) }
    var keepSignedIn by rememberSaveable { mutableStateOf(savedLogin?.optBoolean("remember", true) ?: true) }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var authError by remember { mutableStateOf<AuthError?>(null) }
    // Pendiente 2 — real Google sign-in runs while the account sheet opens.
    var googleSigningIn by remember { mutableStateOf(false) }
    // 409: the e-mail signs in with a password, so Google has to be linked later.
    var googlePasswordGuidance by remember { mutableStateOf(false) }
    var googleLinkSheet by remember { mutableStateOf(false) }
    var signingIn by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Without SPOTGO_GOOGLE_WEB_CLIENT_ID the Google button stays disabled.
    val googleConfigured = GoogleIdentity.isConfigured()
    val googleFailedMessage = stringResource(R.string.google_signin_failed)
    val googleUnauthorizedMessage = stringResource(R.string.google_unauthorized)
    val googleBackendMessage = stringResource(R.string.google_backend_not_configured)
    val googleLinkFailedMessage = stringResource(R.string.google_link_failed)
    val googleLinkedMessage = stringResource(R.string.google_linked)
    val loginBackendErrorMessage = stringResource(R.string.login_backend_error)

    // One place maps the /authentication/google failures to a message or a sheet.
    suspend fun handleGoogleFailure(error: AuthError) {
        when (error) {
            AuthError.PROFILE_REQUIRED -> onCompleteProfile()
            AuthError.GOOGLE_ACCOUNT_WITH_PASSWORD -> googlePasswordGuidance = true
            AuthError.GOOGLE_UNAUTHORIZED -> snackbar.showSnackbar(googleUnauthorizedMessage)
            AuthError.GOOGLE_NOT_CONFIGURED -> snackbar.showSnackbar(googleBackendMessage)
            else -> snackbar.showSnackbar(googleFailedMessage)
        }
    }

    // Links Google to the account that just signed in with e-mail and password.
    suspend fun linkGoogleAccount() {
        when (val result = GoogleIdentity.signIn(context)) {
            is GoogleSignInResult.Success -> {
                val linked = withContext(Dispatchers.IO) { AuthRepository.link_google(result.id_token) }
                when (linked) {
                    GoogleLinkResult.Success -> {
                        AuthRepository.clear_google_link_pending()
                        snackbar.showSnackbar(googleLinkedMessage)
                        onOpenDriver()
                    }
                    GoogleLinkResult.Unauthorized -> {
                        AuthRepository.clear_google_link_pending()
                        snackbar.showSnackbar(googleUnauthorizedMessage)
                        onOpenDriver()
                    }
                    GoogleLinkResult.NotConfigured -> {
                        AuthRepository.clear_google_link_pending()
                        snackbar.showSnackbar(googleBackendMessage)
                        onOpenDriver()
                    }
                    // Keep the offer: the user can retry from the next sign-in.
                    GoogleLinkResult.Failure -> {
                        snackbar.showSnackbar(googleLinkFailedMessage)
                        onOpenDriver()
                    }
                }
            }
            // Dismissing the account sheet is not an error: enter the app and retry later.
            GoogleSignInResult.Cancelled -> onOpenDriver()
            GoogleSignInResult.NotConfigured -> {
                AuthRepository.clear_google_link_pending()
                onOpenDriver()
            }
            is GoogleSignInResult.Failed -> {
                snackbar.showSnackbar(googleLinkFailedMessage)
                onOpenDriver()
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar, Modifier.safeDrawingPadding()) },
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .safeDrawingPadding()
                .imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            val availableHeight = maxHeight
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = availableHeight),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp)
                        .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.height(40.dp), contentAlignment = Alignment.CenterStart) {
                        SpotGoWordmark()
                    }
                    Text(stringResource(R.string.login_welcome), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(R.string.login_intro),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Normal),
                        color = SpotGoSupportingText,
                    )
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(15.dp),
                ) {
                    Text(stringResource(R.string.login_sign_in), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.login_account_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        state = email,
                        labelPosition = TextFieldLabelPosition.Attached(alwaysMinimize = true),
                        label = { Text(stringResource(R.string.login_email)) },
                        placeholder = { Text(stringResource(R.string.login_email_placeholder)) },
                        modifier = Modifier.fillMaxWidth().figmaFieldBounds(),
                        shape = RoundedCornerShape(16.dp),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        colors = loginFieldColors(),
                    )
                    OutlinedSecureTextField(
                        state = password,
                        labelPosition = TextFieldLabelPosition.Attached(alwaysMinimize = true),
                        label = { Text(stringResource(R.string.login_password)) },
                        placeholder = { Text(stringResource(R.string.login_password_placeholder)) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    painter = painterResource(
                                        if (passwordVisible) R.drawable.ic_visibility_off else R.drawable.ic_visibility,
                                    ),
                                    contentDescription = stringResource(
                                        if (passwordVisible) R.string.login_hide_password else R.string.login_show_password,
                                    ),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().figmaFieldBounds(),
                        shape = RoundedCornerShape(16.dp),
                        textObfuscationMode = if (passwordVisible) TextObfuscationMode.Visible else TextObfuscationMode.Hidden,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        onKeyboardAction = { focusManager.clearFocus() },
                        colors = loginFieldColors(),
                    )
                    // Wrapping the two controls independently also works with larger accessibility fonts.
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Row(
                            modifier = Modifier.heightIn(min = 48.dp).toggleable(
                                value = keepSignedIn,
                                role = Role.Checkbox,
                                onValueChange = { checked ->
                                    keepSignedIn = checked
                                    if (!checked) {
                                        credentialStore.clear()
                                        credentialStore.write("login", """{"remember":false}""")
                                    }
                                },
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Checkbox(checked = keepSignedIn, onCheckedChange = null, modifier = Modifier.size(48.dp))
                            Text(stringResource(R.string.login_remember), style = MaterialTheme.typography.bodyMedium)
                        }
                        TextButton(onClick = { focusManager.clearFocus(); onForgotPassword(email.text.toString().trim()) }) {
                            Text(stringResource(R.string.login_forgot_password))
                        }
                    }
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            if (!signingIn) scope.launch {
                                val submittedEmail = email.text.toString()
                                val submittedPassword = password.text.toString()
                                val rememberLogin = keepSignedIn
                                signingIn = true
                                val result = try {
                                    withContext(Dispatchers.IO) {
                                        AuthRepository.sign_in(
                                            email = submittedEmail,
                                            password = submittedPassword,
                                            keep_signed_in = rememberLogin,
                                        )
                                    }
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    AuthResult.Failure(AuthError.API_ERROR)
                                } finally {
                                    signingIn = false
                                }
                                when (result) {
                                    is AuthResult.Success -> {
                                        if (rememberLogin && keepSignedIn) {
                                            credentialStore.write(
                                                "login",
                                                JSONObject().put("email", submittedEmail.trim())
                                                    .put("password", submittedPassword).toString(),
                                            )
                                        }
                                        // 409 → sign in with the password first, then offer linking Google.
                                        if (AuthRepository.google_link_pending()) googleLinkSheet = true
                                        else onOpenDriver()
                                    }
                                    is AuthResult.Failure -> if (result.error == AuthError.PROFILE_REQUIRED) {
                                        onCompleteProfile()
                                    } else if (result.error == AuthError.API_ERROR) {
                                        snackbar.showSnackbar(loginBackendErrorMessage)
                                    } else authError = result.error
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(
                            stringResource(if (signingIn) R.string.login_signing_in else R.string.login_sign_in),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.login_new_here),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(
                            onClick = { focusManager.clearFocus(); onCreateAccount() },
                            modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                        ) {
                            Text(stringResource(R.string.login_create_account))
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            focusManager.clearFocus()
                            when {
                                googleSigningIn -> Unit
                                // No client id yet: the button is disabled, nothing to run.
                                !googleConfigured -> Unit
                                // Pendiente 2 — real Google Identity when the client id exists.
                                else -> {
                                    googleSigningIn = true
                                    scope.launch {
                                        when (val result = GoogleIdentity.signIn(context)) {
                                            is GoogleSignInResult.Success -> {
                                                googleSigningIn = false
                                                val auth = withContext(Dispatchers.IO) {
                                                    AuthRepository.sign_in_with_google(
                                                        keep_signed_in = keepSignedIn,
                                                        email = result.email,
                                                        display_name = result.display_name,
                                                        external_subject = GoogleIdentity.subjectOf(result.id_token)
                                                            .ifBlank { result.id_token },
                                                        id_token = result.id_token,
                                                    )
                                                }
                                                if (auth is AuthResult.Success) onOpenDriver()
                                                else if (auth is AuthResult.Failure) handleGoogleFailure(auth.error)
                                            }
                                            // Cancel is silent: the user can retry or use e-mail + password.
                                            GoogleSignInResult.Cancelled -> googleSigningIn = false
                                            GoogleSignInResult.NotConfigured -> googleSigningIn = false
                                            is GoogleSignInResult.Failed -> {
                                                googleSigningIn = false
                                                snackbar.showSnackbar(googleFailedMessage)
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        enabled = googleConfigured && !googleSigningIn,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) {
                        Text(
                            stringResource(if (googleSigningIn) R.string.google_connecting else R.string.login_google),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    if (!googleConfigured) {
                        // Clear message while SPOTGO_GOOGLE_WEB_CLIENT_ID is empty;
                        // e-mail + password sign-in above is unaffected.
                        Text(
                            stringResource(R.string.google_unavailable_client_id),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.weight(1f))
            }
        }
    }

    if (authError == AuthError.INVALID_CREDENTIALS) {
        StateBottomSheet(
            title = stringResource(R.string.state_invalid_login_title),
            message = stringResource(R.string.state_invalid_login_message),
            tag = stringResource(R.string.state_invalid_login_tag),
            primaryText = stringResource(R.string.state_action_try_again),
            onPrimary = { authError = null },
            secondaryText = stringResource(R.string.state_action_close),
            onSecondary = { authError = null },
            onDismissRequest = { authError = null },
        )
    }

    // 409 → guide the user to the e-mail + password form of this same screen.
    if (googlePasswordGuidance) {
        StateBottomSheet(
            title = stringResource(R.string.google_account_exists_title),
            message = stringResource(R.string.google_account_exists_message),
            tag = stringResource(R.string.google_account_exists_tag),
            tagColor = SpotGoWarning,
            primaryText = stringResource(R.string.login_sign_in),
            onPrimary = { googlePasswordGuidance = false; focusManager.clearFocus() },
            secondaryText = stringResource(R.string.state_action_close),
            onSecondary = { googlePasswordGuidance = false },
            onDismissRequest = { googlePasswordGuidance = false },
        )
    }

    // The account was signed in with its password: offer to link Google to it.
    if (googleLinkSheet) {
        StateBottomSheet(
            title = stringResource(R.string.google_link_title),
            message = stringResource(R.string.google_link_message),
            tag = stringResource(R.string.google_link_tag),
            tagColor = SpotGoWarning,
            primaryText = stringResource(R.string.google_link_action),
            onPrimary = { googleLinkSheet = false; scope.launch { linkGoogleAccount() } },
            secondaryText = stringResource(R.string.google_link_later),
            onSecondary = {
                AuthRepository.clear_google_link_pending()
                googleLinkSheet = false
                onOpenDriver()
            },
            onDismissRequest = {
                AuthRepository.clear_google_link_pending()
                googleLinkSheet = false
                onOpenDriver()
            },
        )
    }
}

@Composable
internal fun Modifier.figmaFieldBounds(): Modifier {
    // Figma measures the 56 dp outline, with the floating label extending above it.
    // Material reserves half a label line above that outline; keep the artwork's bounds.
    val labelInset = MaterialTheme.typography.bodySmall.lineHeight / 2
    return layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val inset = labelInset.toPx().roundToInt()
        layout(placeable.width, placeable.height - inset) {
            placeable.placeRelative(0, -inset)
        }
    }
}

@Composable
internal fun loginFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Preview(name = "01 · Login", widthDp = 412, heightDp = 915, showBackground = true)
@Preview(name = "Login · Compact", widthDp = 360, heightDp = 800, showBackground = true)
@Composable
private fun LoginPreview() {
    SpotGoTheme { LoginScreen(onOpenDriver = {}, onCreateAccount = {}, onForgotPassword = {}) }
}
