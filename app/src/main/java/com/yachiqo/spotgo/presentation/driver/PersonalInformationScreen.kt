package com.yachiqo.spotgo.presentation.driver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yachiqo.spotgo.R
import com.yachiqo.spotgo.data.repository.AccountDetailsUpdateResult
import com.yachiqo.spotgo.data.repository.AuthRepository
import com.yachiqo.spotgo.data.repository.PasswordChangeResult
import com.yachiqo.spotgo.presentation.common.FigmaIcon
import com.yachiqo.spotgo.presentation.driver.components.DriverOutlineButton
import com.yachiqo.spotgo.presentation.driver.components.DriverPanel
import com.yachiqo.spotgo.presentation.driver.components.DriverPrimaryButton
import com.yachiqo.spotgo.presentation.driver.components.DriverSectionTitle
import com.yachiqo.spotgo.ui.theme.SpotGoSuccess
import com.yachiqo.spotgo.ui.theme.SpotGoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalInformationScreen(
    onBack: () -> Unit,
    onPasswordChanged: () -> Unit,
) {
    var profile by remember { mutableStateOf(AuthRepository.current_profile()) }
    var firstName by remember { mutableStateOf(profile?.first_name.orEmpty()) }
    var lastName by remember { mutableStateOf(profile?.last_name.orEmpty()) }
    var email by remember { mutableStateOf(profile?.email.orEmpty()) }
    var phone by remember { mutableStateOf(profile?.phone.orEmpty()) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var savingDetails by remember { mutableStateOf(false) }
    var detailsMessage by remember { mutableStateOf<Int?>(null) }
    var detailsMessageError by remember { mutableStateOf(false) }
    var passwordFormVisible by rememberSaveable { mutableStateOf(false) }
    var changingPassword by remember { mutableStateOf(false) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var currentPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var newPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var confirmPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<Int?>(null) }
    var passwordUpdatedDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val fullName = listOf(firstName.trim(), lastName.trim()).filter(String::isNotBlank)
        .joinToString(" ").ifBlank { stringResource(R.string.profile_name_unavailable) }
    val displayEmail = email.ifBlank { stringResource(R.string.profile_email_fallback) }
    val displayPhone = phone.ifBlank { stringResource(R.string.profile_phone_fallback) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.profile_personal_information)) },
            windowInsets = WindowInsets(0),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    FigmaIcon(R.drawable.ic_back, stringResource(R.string.zone_back))
                }
            },
        )
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp).padding(top = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.profile_personal_information_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            DriverPanel {
                if (editing) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = firstName,
                            onValueChange = { firstName = it; detailsMessage = null },
                            label = { Text(stringResource(R.string.profile_first_name)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = lastName,
                            onValueChange = { lastName = it; detailsMessage = null },
                            label = { Text(stringResource(R.string.profile_last_name)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it; detailsMessage = null },
                            label = { Text(stringResource(R.string.profile_email_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        )
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it; detailsMessage = null },
                            label = { Text(stringResource(R.string.profile_phone_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        )
                        DriverPrimaryButton(
                            label = stringResource(R.string.profile_save_changes),
                            onClick = {
                                if (!savingDetails) scope.launch {
                                    savingDetails = true
                                    detailsMessage = null
                                    val result = withContext(Dispatchers.IO) {
                                        AuthRepository.update_account_details(firstName, lastName, email, phone)
                                    }
                                    savingDetails = false
                                    when (result) {
                                        is AccountDetailsUpdateResult.Success -> {
                                            profile = result.profile
                                            firstName = result.profile.first_name
                                            lastName = result.profile.last_name
                                            email = result.profile.email
                                            phone = result.profile.phone
                                            editing = false
                                            detailsMessage = R.string.profile_details_saved
                                            detailsMessageError = false
                                        }
                                        AccountDetailsUpdateResult.EmailAlreadyInUse -> {
                                            detailsMessage = R.string.profile_email_in_use
                                            detailsMessageError = true
                                        }
                                        AccountDetailsUpdateResult.InvalidDetails -> {
                                            detailsMessage = R.string.profile_details_invalid
                                            detailsMessageError = true
                                        }
                                        AccountDetailsUpdateResult.Failure -> {
                                            detailsMessage = R.string.profile_update_failed
                                            detailsMessageError = true
                                        }
                                    }
                                }
                            },
                            enabled = !savingDetails,
                        )
                        DriverOutlineButton(
                            label = stringResource(R.string.profile_cancel),
                            onClick = {
                                firstName = profile?.first_name.orEmpty()
                                lastName = profile?.last_name.orEmpty()
                                email = profile?.email.orEmpty()
                                phone = profile?.phone.orEmpty()
                                editing = false
                                detailsMessage = null
                            },
                            enabled = !savingDetails,
                        )
                        if (savingDetails) CircularProgressIndicator()
                    }
                } else {
                    Column(Modifier.fillMaxWidth()) {
                        PersonalInformationRow(stringResource(R.string.profile_full_name), fullName)
                        PersonalInformationRow(stringResource(R.string.profile_email_label), displayEmail)
                        PersonalInformationRow(stringResource(R.string.profile_phone_label), displayPhone)
                        DriverOutlineButton(
                            label = stringResource(R.string.profile_edit_details),
                            onClick = { editing = true; detailsMessage = null },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }

            detailsMessage?.let { message ->
                Text(
                    stringResource(message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (detailsMessageError) MaterialTheme.colorScheme.error else SpotGoSuccess,
                )
            }

            DriverSectionTitle(stringResource(R.string.profile_password_section))
            DriverPanel {
                Column(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        stringResource(R.string.profile_password_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (passwordFormVisible) {
                        PasswordField(
                            value = currentPassword,
                            onValueChange = { currentPassword = it; passwordError = null },
                            label = stringResource(R.string.profile_current_password),
                            visible = currentPasswordVisible,
                            onVisibilityChange = { currentPasswordVisible = !currentPasswordVisible },
                        )
                        PasswordField(
                            value = newPassword,
                            onValueChange = { newPassword = it; passwordError = null },
                            label = stringResource(R.string.profile_new_password),
                            visible = newPasswordVisible,
                            onVisibilityChange = { newPasswordVisible = !newPasswordVisible },
                        )
                        PasswordField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it; passwordError = null },
                            label = stringResource(R.string.profile_confirm_password),
                            visible = confirmPasswordVisible,
                            onVisibilityChange = { confirmPasswordVisible = !confirmPasswordVisible },
                        )
                        passwordError?.let { error ->
                            Text(stringResource(error), color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        DriverPrimaryButton(
                            label = stringResource(R.string.profile_update_password),
                            onClick = {
                                if (newPassword != confirmPassword) {
                                    passwordError = R.string.profile_password_mismatch
                                } else if (!AuthRepository.isPasswordValid(newPassword)) {
                                    passwordError = R.string.profile_password_requirements
                                } else if (currentPassword == newPassword) {
                                    passwordError = R.string.profile_password_reused
                                } else if (!changingPassword) scope.launch {
                                    changingPassword = true
                                    passwordError = null
                                    val result = withContext(Dispatchers.IO) {
                                        AuthRepository.update_password(currentPassword, newPassword)
                                    }
                                    changingPassword = false
                                    when (result) {
                                        PasswordChangeResult.SUCCESS -> {
                                            currentPassword = ""
                                            newPassword = ""
                                            confirmPassword = ""
                                            passwordFormVisible = false
                                            passwordUpdatedDialog = true
                                        }
                                        PasswordChangeResult.INVALID_CURRENT_PASSWORD ->
                                            passwordError = R.string.profile_password_current_invalid
                                        PasswordChangeResult.PASSWORD_REQUIREMENTS ->
                                            passwordError = R.string.profile_password_requirements
                                        PasswordChangeResult.PASSWORD_REUSED ->
                                            passwordError = R.string.profile_password_reused
                                        PasswordChangeResult.LOCAL_PASSWORD_UNAVAILABLE ->
                                            passwordError = R.string.profile_password_local_unavailable
                                        PasswordChangeResult.FAILURE ->
                                            passwordError = R.string.profile_password_update_failed
                                    }
                                }
                            },
                            enabled = !changingPassword,
                        )
                        DriverOutlineButton(
                            label = stringResource(R.string.profile_cancel),
                            onClick = {
                                currentPassword = ""
                                newPassword = ""
                                confirmPassword = ""
                                passwordError = null
                                passwordFormVisible = false
                            },
                            enabled = !changingPassword,
                        )
                        if (changingPassword) CircularProgressIndicator()
                    } else {
                        DriverOutlineButton(
                            label = stringResource(R.string.profile_update_password),
                            onClick = { passwordFormVisible = true; passwordError = null },
                        )
                    }
                }
            }
        }
    }

    if (passwordUpdatedDialog) {
        AlertDialog(
            onDismissRequest = onPasswordChanged,
            title = { Text(stringResource(R.string.profile_password_updated_title)) },
            text = { Text(stringResource(R.string.profile_password_updated_message)) },
            confirmButton = {
                TextButton(onClick = onPasswordChanged) {
                    Text(stringResource(R.string.profile_sign_in_again))
                }
            },
        )
    }
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onVisibilityChange: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onVisibilityChange) {
                Icon(
                    painter = painterResource(if (visible) R.drawable.ic_visibility_off else R.drawable.ic_visibility),
                    contentDescription = stringResource(
                        if (visible) R.string.login_hide_password else R.string.login_show_password,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun PersonalInformationRow(label: String, value: String) {
    ListItem(
        headlineContent = { Text(label, style = MaterialTheme.typography.bodySmall) },
        supportingContent = { Text(value, style = MaterialTheme.typography.titleMedium) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    )
}

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun PersonalInformationPreview() {
    SpotGoTheme { PersonalInformationScreen({}, {}) }
}
