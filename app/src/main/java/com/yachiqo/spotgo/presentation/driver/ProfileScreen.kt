package com.yachiqo.spotgo.presentation.driver

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yachiqo.spotgo.R
import com.yachiqo.spotgo.data.repository.AuthRepository
import com.yachiqo.spotgo.data.repository.RemoteBillingRepository
import com.yachiqo.spotgo.data.repository.RemoteSubscription
import com.yachiqo.spotgo.data.repository.RemoteVehicleRepository
import com.yachiqo.spotgo.data.repository.VehicleListResult
import com.yachiqo.spotgo.locale.AppLocale
import com.yachiqo.spotgo.presentation.driver.components.*
import com.yachiqo.spotgo.ui.theme.SpotGoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ProfileScreen(
    onSignOut: () -> Unit,
    onMyVehicles: () -> Unit,
    onPlans: () -> Unit,
    onPersonalInformation: () -> Unit,
    onUnavailableAction: () -> Unit,
) {
    val profile = AuthRepository.current_profile()
    var remoteVehicleCount by remember { mutableStateOf<Int?>(null) }
    var vehicleLoadFailed by remember { mutableStateOf(false) }
    var activeSubscription by remember { mutableStateOf<RemoteSubscription?>(null) }
    var subscriptionLoadFailed by remember { mutableStateOf(false) }
    var languagePickerVisible by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val (vehicles, subscriptions) = withContext(Dispatchers.IO) {
            RemoteVehicleRepository.loadVehicles() to RemoteBillingRepository.loadSubscriptions()
        }
        when (vehicles) {
            is VehicleListResult.Success -> remoteVehicleCount = vehicles.vehicles.size
            VehicleListResult.Failure -> vehicleLoadFailed = true
        }
        if (subscriptions == null) subscriptionLoadFailed = true
        else activeSubscription = subscriptions.firstOrNull { it.status == "ACTIVE" }
    }

    val displayName = listOfNotNull(
        profile?.first_name?.takeIf(String::isNotBlank),
        profile?.last_name?.takeIf(String::isNotBlank),
    ).joinToString(" ").ifBlank { stringResource(R.string.profile_name_unavailable) }
    val displayEmail = profile?.email?.takeIf(String::isNotBlank)
        ?: stringResource(R.string.profile_email_fallback)
    val vehicleSummary = when {
        vehicleLoadFailed -> stringResource(R.string.profile_data_unavailable)
        remoteVehicleCount != null -> stringResource(R.string.profile_vehicles_summary, remoteVehicleCount!!)
        else -> stringResource(R.string.profile_vehicles_backend_loading)
    }
    val subscriptionSummary = when {
        subscriptionLoadFailed -> stringResource(R.string.profile_data_unavailable)
        activeSubscription != null -> stringResource(planDisplayNameResource(activeSubscription!!.planCode))
        else -> stringResource(R.string.profile_subscription_none)
    }

    DriverPage(stringResource(R.string.driver_profile), stringResource(R.string.profile_subtitle)) {
        DriverPanel(outlined = true) {
            Row(Modifier.fillMaxWidth().heightIn(min = 104.dp)
                .clickable(role = Role.Button, onClick = onPersonalInformation)
                .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(painterResource(R.drawable.ic_profile), contentDescription = null, modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(displayName, style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp))
                    Text(displayEmail, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(stringResource(R.string.payments_chevron), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DriverSectionTitle(stringResource(R.string.profile_account))
        DriverPanel {
            ProfileRow(stringResource(R.string.profile_vehicles), vehicleSummary, onMyVehicles)
            ProfileRow(
                stringResource(R.string.profile_language),
                stringResource(
                    if (AppLocale.isSpanish(context)) R.string.profile_language_spanish_latam
                    else R.string.profile_language_english_us,
                ),
            ) { languagePickerVisible = true }
            ProfileRow(stringResource(R.string.profile_subscription), subscriptionSummary, onPlans)
            ProfileRow(stringResource(R.string.profile_permitted_zones), stringResource(R.string.profile_zones_summary),
                onUnavailableAction)
        }
        DriverOutlineButton(stringResource(R.string.profile_sign_out), onSignOut, color = Color(0xFFFF4D5A))
    }

    if (languagePickerVisible) {
        val activity = context as? Activity
        val selectedLanguage = AppLocale.currentLanguageTag(context)
        AlertDialog(
            onDismissRequest = { languagePickerVisible = false },
            title = { Text(stringResource(R.string.profile_language)) },
            text = {
                Column {
                    LanguageChoice(
                        label = stringResource(R.string.profile_language_english_us),
                        selected = selectedLanguage.startsWith("en", ignoreCase = true),
                        onClick = {
                            activity?.let { AppLocale.select(it, AppLocale.ENGLISH_US) }
                            languagePickerVisible = false
                        },
                    )
                    LanguageChoice(
                        label = stringResource(R.string.profile_language_spanish_latam),
                        selected = selectedLanguage.startsWith("es", ignoreCase = true),
                        onClick = {
                            activity?.let { AppLocale.select(it, AppLocale.SPANISH_LATAM) }
                            languagePickerVisible = false
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { languagePickerVisible = false }) {
                    Text(stringResource(R.string.payments_cancel))
                }
            },
        )
    }
}

@Composable
private fun LanguageChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ProfileRow(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodyMedium) },
        trailingContent = { Text(stringResource(R.string.payments_chevron)) },
        modifier = Modifier.heightIn(min = 72.dp).clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    )
}

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun ProfilePreview() {
    SpotGoTheme { ProfileScreen({}, {}, {}, {}, {}) }
}
