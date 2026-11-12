package com.yachiqo.spotgo.presentation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.yachiqo.spotgo.R
import com.yachiqo.spotgo.data.repository.AuthRepository
import com.yachiqo.spotgo.data.remote.FcmDeviceRegistration
import com.yachiqo.spotgo.data.remote.SpotGoApi
import com.yachiqo.spotgo.presentation.common.BackendUnavailableScreen
import com.yachiqo.spotgo.presentation.common.StateBottomSheet
import com.yachiqo.spotgo.presentation.driver.DriverLayout
import com.yachiqo.spotgo.presentation.login.ForgotPasswordScreen
import com.yachiqo.spotgo.presentation.login.CompleteProfileScreen
import com.yachiqo.spotgo.presentation.login.LoginScreen
import com.yachiqo.spotgo.presentation.login.RegisterScreen
import com.yachiqo.spotgo.ui.theme.SpotGoWarning
import kotlinx.coroutines.delay

// UI destinations. Authentication state lives in AuthRepository (in-memory).
private enum class AppDestination { LOGIN, REGISTER, FORGOT_PASSWORD, COMPLETE_PROFILE, DRIVER }

@Composable
fun SpotGoApp() {
    if (!SpotGoApi.is_configured()) {
        BackendUnavailableScreen()
        return
    }
    val context = LocalContext.current
    val notificationPermissionRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    var destination by rememberSaveable { mutableStateOf(AppDestination.LOGIN) }
    var sessionExpiredSheet by remember { mutableStateOf(false) }
    val backToLogin = {
        AuthRepository.sign_out()
        sessionExpiredSheet = false
        destination = AppDestination.LOGIN
    }
    val enterDriver = {
        if (
            FcmDeviceRegistration.isEnabled() &&
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        destination = AppDestination.DRIVER
    }

    // US09 — require authentication after the session expires.
    LaunchedEffect(destination) {
        if (destination == AppDestination.DRIVER) {
            while (true) {
                delay(5_000)
                if (AuthRepository.session_expired()) {
                    sessionExpiredSheet = true
                    break
                }
            }
        }
    }

    BackHandler(enabled = destination != AppDestination.LOGIN, onBack = backToLogin)

    Surface(modifier = Modifier.fillMaxSize()) {
        when (destination) {
            AppDestination.LOGIN -> LoginScreen(
                onOpenDriver = enterDriver,
                onCreateAccount = { destination = AppDestination.REGISTER },
                onForgotPassword = { destination = AppDestination.FORGOT_PASSWORD },
                onCompleteProfile = { destination = AppDestination.COMPLETE_PROFILE },
            )
            AppDestination.REGISTER -> RegisterScreen(
                onBackToLogin = backToLogin,
                onRegistered = enterDriver,
            )
            AppDestination.FORGOT_PASSWORD -> ForgotPasswordScreen(
                onBackToLogin = { destination = AppDestination.LOGIN },
            )
            AppDestination.COMPLETE_PROFILE -> CompleteProfileScreen(
                onCompleted = enterDriver,
                onCancel = backToLogin,
            )
            AppDestination.DRIVER -> DriverLayout(onBackToLogin = backToLogin)
        }

        if (sessionExpiredSheet && destination == AppDestination.DRIVER) {
            StateBottomSheet(
                title = stringResource(R.string.state_session_expired_title),
                message = stringResource(R.string.state_session_expired_message),
                tag = stringResource(R.string.state_session_expired_tag),
                tagColor = SpotGoWarning,
                primaryText = stringResource(R.string.login_sign_in),
                onPrimary = backToLogin,
                secondaryText = stringResource(R.string.state_action_close),
                onSecondary = { sessionExpiredSheet = false },
                onDismissRequest = { sessionExpiredSheet = false },
            )
        }
    }
}
