package com.yachiqo.spotgo.presentation.driver

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yachiqo.spotgo.R
import com.yachiqo.spotgo.data.repository.VehicleRepository
import com.yachiqo.spotgo.presentation.driver.components.*
import com.yachiqo.spotgo.ui.theme.SpotGoTheme

@Composable
fun ProfileScreen(
    onSignOut: () -> Unit,
    onMyVehicles: () -> Unit,
    onPlans: () -> Unit,
    onUnavailableAction: () -> Unit,
) {
    DriverPage(stringResource(R.string.driver_profile), stringResource(R.string.profile_subtitle),
        onProfile = onUnavailableAction) {
        DriverPanel(outlined = true) {
            Row(Modifier.fillMaxWidth().heightIn(min = 104.dp).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(painterResource(R.drawable.ic_profile), contentDescription = null, modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(stringResource(R.string.profile_name), style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp))
                    Text(stringResource(R.string.profile_email), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        DriverSectionTitle(stringResource(R.string.profile_account))
        DriverPanel {
            ProfileRow(stringResource(R.string.profile_personal_information), stringResource(R.string.profile_phone), onUnavailableAction)
            ProfileRow(
                stringResource(R.string.profile_vehicles),
                stringResource(
                    R.string.profile_vehicles_summary,
                    VehicleRepository.vehicles_for_current_profile().size,
                ),
                onMyVehicles,
            )
            ProfileRow(stringResource(R.string.profile_language), stringResource(R.string.profile_language_value), onUnavailableAction)
            ProfileRow(stringResource(R.string.profile_subscription), stringResource(R.string.profile_subscription_summary), onPlans)
            ProfileRow(stringResource(R.string.profile_permitted_zones), stringResource(R.string.profile_zones_summary), onUnavailableAction)
        }
        DriverOutlineButton(stringResource(R.string.profile_sign_out), onSignOut, color = Color(0xFFFF4D5A))
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
    SpotGoTheme { ProfileScreen({}, {}, {}, {}) }
}
