package com.yachiqo.spotgo.presentation.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.yachiqo.spotgo.R
import com.yachiqo.spotgo.data.repository.AuthError
import com.yachiqo.spotgo.data.repository.AuthRepository
import com.yachiqo.spotgo.data.repository.AuthResult
import com.yachiqo.spotgo.presentation.common.StateBottomSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Completes the required SpotGo personal profile after first-time Google auth. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompleteProfileScreen(onCompleted: () -> Unit, onCancel: () -> Unit) {
    val firstName = rememberTextFieldState()
    val lastName = rememberTextFieldState()
    val phone = rememberTextFieldState()
    var pending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<AuthError?>(null) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).safeDrawingPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.register_title), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.register_intro, "Google"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                state = firstName,
                label = { Text(stringResource(R.string.register_first_name)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                lineLimits = TextFieldLineLimits.SingleLine,
            )
            OutlinedTextField(
                state = lastName,
                label = { Text(stringResource(R.string.register_last_name)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                lineLimits = TextFieldLineLimits.SingleLine,
            )
            OutlinedTextField(
                state = phone,
                label = { Text(stringResource(R.string.register_phone)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
            Button(
                enabled = !pending,
                onClick = {
                    scope.launch {
                        pending = true
                        val result = withContext(Dispatchers.IO) {
                            AuthRepository.complete_backend_profile(
                                firstName.text.toString(),
                                lastName.text.toString(),
                                phone.text.toString(),
                            )
                        }
                        pending = false
                        when (result) {
                            is AuthResult.Success -> onCompleted()
                            is AuthResult.Failure -> error = result.error
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(stringResource(if (pending) R.string.register_sending else R.string.login_create_account))
            }
        }
    }

    when (error) {
        AuthError.INVALID_FORM -> StateBottomSheet(
            title = stringResource(R.string.state_invalid_form_title),
            message = stringResource(R.string.state_invalid_form_message),
            tag = stringResource(R.string.state_invalid_form_tag),
            primaryText = stringResource(R.string.state_action_review_form),
            onPrimary = { error = null },
            secondaryText = stringResource(R.string.back_to_login),
            onSecondary = { error = null; onCancel() },
            onDismissRequest = { error = null },
        )
        AuthError.API_ERROR, AuthError.PROFILE_REQUIRED -> StateBottomSheet(
            title = stringResource(R.string.register_api_error_title),
            message = stringResource(R.string.register_api_error_message),
            tag = stringResource(R.string.register_api_error_tag),
            primaryText = stringResource(R.string.state_action_try_again),
            onPrimary = { error = null },
            secondaryText = stringResource(R.string.back_to_login),
            onSecondary = { error = null; onCancel() },
            onDismissRequest = { error = null },
        )
        else -> Unit
    }
}
