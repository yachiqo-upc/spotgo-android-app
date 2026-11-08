package com.yachiqo.spotgo.presentation.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yachiqo.spotgo.R
import com.yachiqo.spotgo.data.repository.AuthError
import com.yachiqo.spotgo.data.repository.AuthRepository
import com.yachiqo.spotgo.data.repository.ResetResult
import com.yachiqo.spotgo.presentation.common.FigmaIcon
import com.yachiqo.spotgo.presentation.common.SpotGoWordmark
import com.yachiqo.spotgo.presentation.common.StateBottomSheet
import com.yachiqo.spotgo.ui.theme.SpotGoAccent
import com.yachiqo.spotgo.ui.theme.SpotGoAccentContainer
import com.yachiqo.spotgo.ui.theme.SpotGoSuccess
import com.yachiqo.spotgo.ui.theme.SpotGoSupportingText
import com.yachiqo.spotgo.ui.theme.SpotGoTheme

/** Bottom sheet states of the password-reset flow. */
private enum class ForgotSheet { ACCOUNT_NOT_FOUND, INVALID_TOKEN, PASSWORD_MISMATCH, WEAK_PASSWORD, SUCCESS }

/**
 * 3-step password recovery (demo): email → 6-digit code → new password.
 * The code is displayed on screen because there is no backend to send e-mail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    onBackToLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by rememberSaveable { mutableStateOf(1) }
    val email = rememberTextFieldState()
    // Codes and passwords stay in temporary UI state, outside saved instance state.
    val token = remember { TextFieldState() }
    val newPassword = remember { TextFieldState() }
    val confirmPassword = remember { TextFieldState() }
    var sentToken by remember { mutableStateOf("") }
    var sheet by remember { mutableStateOf<ForgotSheet?>(null) }
    val focusManager = LocalFocusManager.current
    val backToLogin = { focusManager.clearFocus(); onBackToLogin() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .height(188.dp)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilledIconButton(
                        onClick = when (step) {
                            1 -> backToLogin
                            2 -> {
                                { step = 1; token.edit { replace(0, length, "") } }
                            }
                            else -> {
                                { step = 2 }
                            }
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) { FigmaIcon(R.drawable.ic_back, stringResource(R.string.back_to_login)) }
                    SpotGoWordmark()
                }
                Text(
                    stringResource(
                        when (step) {
                            2 -> R.string.forgot_step2_title
                            3 -> R.string.forgot_step3_title
                            else -> R.string.forgot_title
                        },
                    ),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    stringResource(
                        when (step) {
                            2 -> R.string.forgot_step2_intro
                            3 -> R.string.forgot_step3_intro
                            else -> R.string.forgot_step1_intro
                        },
                        email.text.toString().ifBlank { AuthRepository.GOOGLE_DEMO_EMAIL },
                    ),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Normal),
                    color = SpotGoSupportingText,
                )
            }

            Column(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (step) {
                    1 -> OutlinedTextField(
                        state = email,
                        labelPosition = TextFieldLabelPosition.Attached(alwaysMinimize = true),
                        label = { Text(stringResource(R.string.login_email)) },
                        placeholder = { Text(stringResource(R.string.login_email_placeholder)) },
                        modifier = Modifier.fillMaxWidth().figmaFieldBounds(),
                        shape = RoundedCornerShape(16.dp),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                        colors = loginFieldColors(),
                    )

                    2 -> {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = SpotGoAccentContainer,
                        ) {
                            Text(
                                stringResource(R.string.forgot_demo_code, sentToken),
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = SpotGoAccent,
                            )
                        }
                        OutlinedTextField(
                            state = token,
                            labelPosition = TextFieldLabelPosition.Attached(alwaysMinimize = true),
                            label = { Text(stringResource(R.string.forgot_token)) },
                            placeholder = { Text(stringResource(R.string.forgot_token_placeholder)) },
                            modifier = Modifier.fillMaxWidth().figmaFieldBounds(),
                            shape = RoundedCornerShape(16.dp),
                            lineLimits = TextFieldLineLimits.SingleLine,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                            colors = loginFieldColors(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            TextButton(
                                onClick = {
                                    when (val result = AuthRepository.request_password_reset(email.text.toString())) {
                                        is ResetResult.CodeSent -> {
                                            sentToken = result.token
                                            token.edit { replace(0, length, "") }
                                        }
                                        else -> sheet = ForgotSheet.ACCOUNT_NOT_FOUND
                                    }
                                },
                            ) { Text(stringResource(R.string.forgot_resend)) }
                            TextButton(onClick = { step = 1 }) {
                                Text(stringResource(R.string.forgot_change_email))
                            }
                        }
                    }

                    else -> {
                        OutlinedSecureTextField(
                            state = newPassword,
                            labelPosition = TextFieldLabelPosition.Attached(alwaysMinimize = true),
                            label = { Text(stringResource(R.string.forgot_new_password)) },
                            placeholder = { Text(stringResource(R.string.register_password_placeholder)) },
                            modifier = Modifier.fillMaxWidth().figmaFieldBounds(),
                            shape = RoundedCornerShape(16.dp),
                            textObfuscationMode = TextObfuscationMode.Hidden,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                            colors = loginFieldColors(),
                        )
                        OutlinedSecureTextField(
                            state = confirmPassword,
                            labelPosition = TextFieldLabelPosition.Attached(alwaysMinimize = true),
                            label = { Text(stringResource(R.string.forgot_confirm_password)) },
                            placeholder = { Text(stringResource(R.string.register_password_placeholder)) },
                            trailingIcon = {
                                androidx.compose.material3.IconButton(
                                    onClick = { confirmPassword.edit { replace(0, length, "") } },
                                ) {
                                    FigmaIcon(R.drawable.ic_cancel, stringResource(R.string.login_clear_password))
                                }
                            },
                            modifier = Modifier.fillMaxWidth().figmaFieldBounds(),
                            shape = RoundedCornerShape(16.dp),
                            textObfuscationMode = TextObfuscationMode.Hidden,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            onKeyboardAction = { focusManager.clearFocus() },
                            colors = loginFieldColors(),
                        )
                    }
                }

                when (step) {
                    1 -> Button(
                        onClick = {
                            focusManager.clearFocus()
                            when (val result = AuthRepository.request_password_reset(email.text.toString())) {
                                is ResetResult.CodeSent -> {
                                    sentToken = result.token
                                    token.edit { replace(0, length, "") }
                                    step = 2
                                }
                                else -> sheet = ForgotSheet.ACCOUNT_NOT_FOUND
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text(stringResource(R.string.forgot_send_code), style = MaterialTheme.typography.bodyLarge) }

                    2 -> Button(
                        onClick = {
                            focusManager.clearFocus()
                            when (AuthRepository.verify_reset_token(email.text.toString(), token.text.toString())) {
                                is ResetResult.Success -> step = 3
                                else -> sheet = ForgotSheet.INVALID_TOKEN
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text(stringResource(R.string.forgot_verify), style = MaterialTheme.typography.bodyLarge) }

                    else -> Button(
                        onClick = {
                            focusManager.clearFocus()
                            val password = newPassword.text.toString()
                            val confirm = confirmPassword.text.toString()
                            when {
                                password.length < 8 -> sheet = ForgotSheet.WEAK_PASSWORD
                                password != confirm -> sheet = ForgotSheet.PASSWORD_MISMATCH
                                else -> when (
                                    AuthRepository.reset_password(
                                        email = email.text.toString(),
                                        token = token.text.toString(),
                                        new_password = password,
                                    )
                                ) {
                                    is ResetResult.Success -> sheet = ForgotSheet.SUCCESS
                                    else -> sheet = ForgotSheet.INVALID_TOKEN
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) { Text(stringResource(R.string.forgot_update_password), style = MaterialTheme.typography.bodyLarge) }
                }

                TextButton(onClick = backToLogin, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.forgot_back_to_sign_in))
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    when (sheet) {
        ForgotSheet.ACCOUNT_NOT_FOUND -> StateBottomSheet(
            title = stringResource(R.string.forgot_account_not_found_title),
            message = stringResource(R.string.forgot_account_not_found_message),
            tag = stringResource(R.string.forgot_account_not_found_tag),
            primaryText = stringResource(R.string.state_action_try_again),
            onPrimary = { sheet = null },
            secondaryText = stringResource(R.string.forgot_back_to_sign_in),
            onSecondary = { sheet = null; backToLogin() },
            onDismissRequest = { sheet = null },
        )

        ForgotSheet.INVALID_TOKEN -> StateBottomSheet(
            title = stringResource(R.string.forgot_invalid_token_title),
            message = stringResource(R.string.forgot_invalid_token_message),
            tag = stringResource(R.string.forgot_invalid_token_tag),
            primaryText = stringResource(R.string.state_action_try_again),
            onPrimary = { sheet = null },
            secondaryText = stringResource(R.string.forgot_back_to_sign_in),
            onSecondary = { sheet = null; backToLogin() },
            onDismissRequest = { sheet = null },
        )

        ForgotSheet.PASSWORD_MISMATCH -> StateBottomSheet(
            title = stringResource(R.string.forgot_password_mismatch_title),
            message = stringResource(R.string.forgot_password_mismatch_message),
            tag = stringResource(R.string.state_invalid_form_tag),
            primaryText = stringResource(R.string.state_action_review_form),
            onPrimary = { sheet = null },
            secondaryText = stringResource(R.string.state_action_close),
            onSecondary = { sheet = null },
            onDismissRequest = { sheet = null },
        )

        ForgotSheet.WEAK_PASSWORD -> StateBottomSheet(
            title = stringResource(R.string.forgot_password_weak_title),
            message = stringResource(R.string.forgot_password_weak_message),
            tag = stringResource(R.string.state_invalid_form_tag),
            primaryText = stringResource(R.string.state_action_review_form),
            onPrimary = { sheet = null },
            secondaryText = stringResource(R.string.state_action_close),
            onSecondary = { sheet = null },
            onDismissRequest = { sheet = null },
        )

        ForgotSheet.SUCCESS -> StateBottomSheet(
            title = stringResource(R.string.forgot_success_title),
            message = stringResource(R.string.forgot_success_message),
            tag = stringResource(R.string.forgot_success_tag),
            tagColor = SpotGoSuccess,
            primaryText = stringResource(R.string.login_sign_in),
            onPrimary = { sheet = null; backToLogin() },
            secondaryText = stringResource(R.string.state_action_close),
            onSecondary = {
                sheet = null
                step = 1
                token.edit { replace(0, length, "") }
                newPassword.edit { replace(0, length, "") }
                confirmPassword.edit { replace(0, length, "") }
            },
            onDismissRequest = { sheet = null },
        )

        null -> Unit
    }
}

@Preview(name = "01b · Forgot password", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
private fun ForgotPasswordPreview() {
    SpotGoTheme { ForgotPasswordScreen(onBackToLogin = {}) }
}
