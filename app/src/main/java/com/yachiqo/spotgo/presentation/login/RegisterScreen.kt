package com.yachiqo.spotgo.presentation.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yachiqo.spotgo.R
import com.yachiqo.spotgo.data.repository.AuthError
import com.yachiqo.spotgo.data.repository.AuthRepository
import com.yachiqo.spotgo.data.repository.AuthResult
import com.yachiqo.spotgo.data.repository.VerificationResult
import com.yachiqo.spotgo.data.remote.SpotGoApi
import com.yachiqo.spotgo.presentation.common.FigmaIcon
import com.yachiqo.spotgo.presentation.common.SpotGoWordmark
import com.yachiqo.spotgo.presentation.common.StateBottomSheet
import com.yachiqo.spotgo.ui.theme.SpotGoAccent
import com.yachiqo.spotgo.ui.theme.SpotGoAccentContainer
import com.yachiqo.spotgo.ui.theme.SpotGoSupportingText
import com.yachiqo.spotgo.ui.theme.SpotGoTheme
import com.yachiqo.spotgo.ui.theme.SpotGoWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onBackToLogin: () -> Unit,
    onRegistered: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstName = rememberTextFieldState()
    val lastName = rememberTextFieldState()
    val phone = rememberTextFieldState()
    val email = rememberTextFieldState()
    // Keep the password in temporary UI state, outside saved instance state.
    val password = remember { TextFieldState() }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }
    var acceptedTerms by rememberSaveable { mutableStateOf(false) }
    var authError by remember { mutableStateOf<AuthError?>(null) }
    // US13 — step 1 form, step 2 e-mail verification (the account is only
    // created once the 6-digit code is confirmed).
    var step by rememberSaveable { mutableStateOf(1) }
    val code = remember { TextFieldState() }
    var displayedCode by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val backToLogin = {
        focusManager.clearFocus()
        AuthRepository.cancel_email_verification()
        onBackToLogin()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).safeDrawingPadding()
                .imePadding().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
                    .heightIn(min = 188.dp).padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledIconButton(
                        onClick = if (step == 2) {
                            { step = 1; code.edit { replace(0, length, "") } }
                        } else backToLogin,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        FigmaIcon(
                            R.drawable.ic_back,
                            stringResource(if (step == 2) R.string.register_verify_back else R.string.back_to_login),
                        )
                    }
                    SpotGoWordmark()
                }
                Text(
                    stringResource(if (step == 2) R.string.register_verify_title else R.string.register_title),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    stringResource(
                        if (step == 2) R.string.register_verify_intro else R.string.register_intro,
                        email.text.toString(),
                    ),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Normal),
                    color = SpotGoSupportingText,
                )
            }
            Column(
                modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (step == 1) {
                    RegisterField(firstName, R.string.register_first_name, R.string.register_first_name_placeholder)
                    RegisterField(lastName, R.string.register_last_name, R.string.register_last_name_placeholder)
                    RegisterField(phone, R.string.register_phone, R.string.register_phone_placeholder, KeyboardType.Phone)
                    RegisterField(email, R.string.login_email, R.string.login_email_placeholder, KeyboardType.Email)
                    OutlinedSecureTextField(
                        state = password,
                        labelPosition = TextFieldLabelPosition.Attached(alwaysMinimize = true),
                        label = { Text(stringResource(R.string.login_password)) },
                        placeholder = { Text(stringResource(R.string.register_password_placeholder)) },
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
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                            value = acceptedTerms, role = Role.Checkbox, onValueChange = { acceptedTerms = it },
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(checked = acceptedTerms, onCheckedChange = null, modifier = Modifier.size(48.dp))
                        Text(stringResource(R.string.register_terms), style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f))
                    }
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            if (!pending) {
                                pending = true
                                scope.launch {
                                    // Blocking HTTP when the backend exists: off the main thread.
                                    if (SpotGoApi.is_configured()) {
                                        val result = withContext(Dispatchers.IO) {
                                            AuthRepository.register(
                                                first_name = firstName.text.toString(),
                                                last_name = lastName.text.toString(),
                                                phone = phone.text.toString(),
                                                email = email.text.toString(),
                                                password = password.text.toString(),
                                                accepted_terms = acceptedTerms,
                                            )
                                        }
                                        pending = false
                                        when (result) {
                                            is AuthResult.Success -> onRegistered()
                                            is AuthResult.Failure -> authError = result.error
                                        }
                                    } else {
                                        val result = withContext(Dispatchers.IO) {
                                            AuthRepository.begin_email_verification(
                                                first_name = firstName.text.toString(),
                                                last_name = lastName.text.toString(),
                                                phone = phone.text.toString(),
                                                email = email.text.toString(),
                                                password = password.text.toString(),
                                                accepted_terms = acceptedTerms,
                                            )
                                        }
                                        pending = false
                                        when (result) {
                                            is VerificationResult.CodeSent -> {
                                                displayedCode = result.displayed_code
                                                code.edit { replace(0, length, "") }
                                                step = 2
                                            }
                                            is VerificationResult.Failure -> authError = result.error
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(
                            stringResource(if (pending) R.string.register_sending else R.string.login_create_account),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                } else {
                    // US13 — the account is created only after this code.
                    displayedCode?.let { displayed ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = SpotGoAccentContainer,
                        ) {
                            Text(
                                stringResource(R.string.register_verify_code, displayed),
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = SpotGoAccent,
                            )
                        }
                    }
                    OutlinedTextField(
                        state = code,
                        labelPosition = TextFieldLabelPosition.Attached(alwaysMinimize = true),
                        label = { Text(stringResource(R.string.forgot_token)) },
                        placeholder = { Text(stringResource(R.string.forgot_token_placeholder)) },
                        modifier = Modifier.fillMaxWidth().figmaFieldBounds(),
                        shape = RoundedCornerShape(16.dp),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                        colors = loginFieldColors(),
                    )
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            if (!pending) {
                                pending = true
                                scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        AuthRepository.confirm_email_verification(code.text.toString())
                                    }
                                    pending = false
                                    when (result) {
                                        is AuthResult.Success -> onRegistered()
                                        is AuthResult.Failure -> authError = result.error
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(
                            stringResource(if (pending) R.string.register_sending else R.string.forgot_verify),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(
                            onClick = {
                                if (!pending) scope.launch {
                                    val result = withContext(Dispatchers.IO) {
                                        AuthRepository.resend_email_verification()
                                    }
                                    when (result) {
                                        is VerificationResult.CodeSent -> {
                                            displayedCode = result.displayed_code
                                            code.edit { replace(0, length, "") }
                                        }
                                        is VerificationResult.Failure -> authError = result.error
                                    }
                                }
                            },
                        ) { Text(stringResource(R.string.register_verify_resend)) }
                        TextButton(onClick = { step = 1; code.edit { replace(0, length, "") } }) {
                            Text(stringResource(R.string.register_verify_back))
                        }
                    }
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalArrangement = Arrangement.Center,
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.register_existing_account),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = backToLogin, contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Text(stringResource(R.string.login_sign_in))
                    }
                }
            }
        }
    }

    when (authError) {
        AuthError.INVALID_FORM -> StateBottomSheet(
            title = stringResource(R.string.state_invalid_form_title),
            message = stringResource(R.string.state_invalid_form_message),
            tag = stringResource(R.string.state_invalid_form_tag),
            primaryText = stringResource(R.string.state_action_review_form),
            onPrimary = { authError = null },
            secondaryText = stringResource(R.string.state_action_close),
            onSecondary = { authError = null },
            onDismissRequest = { authError = null },
        )
        AuthError.DUPLICATE_EMAIL -> StateBottomSheet(
            title = stringResource(R.string.state_duplicate_email_title),
            message = stringResource(R.string.state_duplicate_email_message),
            tag = stringResource(R.string.state_duplicate_email_tag),
            primaryText = stringResource(R.string.login_sign_in),
            onPrimary = { authError = null; backToLogin() },
            secondaryText = stringResource(R.string.state_action_edit_email),
            onSecondary = { authError = null },
            onDismissRequest = { authError = null },
            tagColor = SpotGoWarning,
        )
        AuthError.INVALID_TOKEN -> StateBottomSheet(
            title = stringResource(R.string.forgot_invalid_token_title),
            message = stringResource(R.string.forgot_invalid_token_message),
            tag = stringResource(R.string.forgot_invalid_token_tag),
            primaryText = stringResource(R.string.state_action_try_again),
            onPrimary = { authError = null },
            secondaryText = stringResource(R.string.register_verify_back),
            onSecondary = { authError = null; step = 1 },
            onDismissRequest = { authError = null },
        )
        AuthError.API_ERROR, AuthError.PROFILE_REQUIRED -> StateBottomSheet(
            title = stringResource(R.string.register_api_error_title),
            message = stringResource(R.string.register_api_error_message),
            tag = stringResource(R.string.register_api_error_tag),
            primaryText = stringResource(R.string.state_action_try_again),
            onPrimary = { authError = null },
            secondaryText = stringResource(R.string.state_action_close),
            onSecondary = { authError = null },
            onDismissRequest = { authError = null },
        )
        AuthError.INVALID_CREDENTIALS, AuthError.ACCOUNT_NOT_FOUND, null -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegisterField(
    state: TextFieldState,
    @androidx.annotation.StringRes label: Int,
    @androidx.annotation.StringRes placeholder: Int,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        state = state,
        labelPosition = TextFieldLabelPosition.Attached(alwaysMinimize = true),
        label = { Text(stringResource(label)) },
        placeholder = { Text(stringResource(placeholder)) },
        modifier = Modifier.fillMaxWidth().figmaFieldBounds(),
        shape = RoundedCornerShape(16.dp),
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        colors = loginFieldColors(),
    )
}

@Preview(name = "02 · Register", widthDp = 412, heightDp = 915)
@Preview(name = "Register · Compact", widthDp = 320, heightDp = 800)
@Composable
private fun RegisterPreview() {
    SpotGoTheme { RegisterScreen(onBackToLogin = {}, onRegistered = {}) }
}
