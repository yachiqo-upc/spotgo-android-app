package com.yachiqo.spotgo.presentation

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.res.stringResource
import com.yachiqo.spotgo.R
import com.yachiqo.spotgo.data.repository.AuthRepository
import com.yachiqo.spotgo.presentation.common.StateBottomSheet
import com.yachiqo.spotgo.presentation.driver.DriverLayout
import com.yachiqo.spotgo.presentation.login.ForgotPasswordScreen
import com.yachiqo.spotgo.presentation.login.LoginScreen
import com.yachiqo.spotgo.presentation.login.RegisterScreen
import com.yachiqo.spotgo.presentation.parking_admin.ParkingAdminLayout
import com.yachiqo.spotgo.ui.theme.SpotGoWarning
import kotlinx.coroutines.delay

// UI destinations. Authentication state lives in AuthRepository (in-memory).
// PARKING_ADMIN is kept for reference only: the Android app ships the Driver
// view; the Parking Admin role belongs to the Flutter client.
private enum class AppDestination { LOGIN, REGISTER, FORGOT_PASSWORD, DRIVER, PARKING_ADMIN }

@Composable
fun SpotGoApp() {
    var destination by rememberSaveable { mutableStateOf(AppDestination.LOGIN) }
    var sessionExpiredSheet by remember { mutableStateOf(false) }
    val backToLogin = {
        AuthRepository.sign_out()
        sessionExpiredSheet = false
        destination = AppDestination.LOGIN
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
                onOpenDriver = { destination = AppDestination.DRIVER },
                onCreateAccount = { destination = AppDestination.REGISTER },
                onForgotPassword = { destination = AppDestination.FORGOT_PASSWORD },
            )
            AppDestination.REGISTER -> RegisterScreen(
                onBackToLogin = backToLogin,
                onRegistered = { destination = AppDestination.DRIVER },
            )
            AppDestination.FORGOT_PASSWORD -> ForgotPasswordScreen(
                onBackToLogin = { destination = AppDestination.LOGIN },
            )
            AppDestination.DRIVER -> DriverLayout(onBackToLogin = backToLogin)
            AppDestination.PARKING_ADMIN -> ParkingAdminLayout(onBackToLogin = backToLogin)
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
