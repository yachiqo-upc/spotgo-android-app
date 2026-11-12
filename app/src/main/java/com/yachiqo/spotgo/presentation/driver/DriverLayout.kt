package com.yachiqo.spotgo.presentation.driver

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yachiqo.spotgo.R
import com.yachiqo.spotgo.data.remote.SpotGoApi
import com.yachiqo.spotgo.presentation.common.BackendUnavailableScreen
import com.yachiqo.spotgo.presentation.common.FigmaIcon
import com.yachiqo.spotgo.ui.theme.SpotGoTheme
import kotlinx.coroutines.launch

// Presentation destinations, not domain entities.
enum class DriverDestination(@get:StringRes val label: Int, @get:DrawableRes val icon: Int) {
    EXPLORE(R.string.driver_explore, R.drawable.ic_explore),
    RESERVATIONS(R.string.driver_reservations, R.drawable.ic_reservations),
    PAYMENTS(R.string.driver_payments, R.drawable.ic_payments),
    PROFILE(R.string.driver_profile, R.drawable.ic_profile),
}

private enum class DriverFlow {
    MAIN, ZONE_DETAILS, CONFIRMED, VEHICLES, VEHICLE_ADD, RESERVATION_DETAILS, RESERVATION_EDIT,
    PAYMENT_BALANCE, RECEIPTS, RECEIPT_DETAIL, PLANS,
}

@Composable
fun DriverLayout(
    onBackToLogin: () -> Unit,
    content: (@Composable (DriverDestination) -> Unit)? = null,
) {
    if (!SpotGoApi.is_configured()) {
        BackendUnavailableScreen()
        return
    }
    var destination by rememberSaveable { mutableStateOf(DriverDestination.EXPLORE) }
    var flow by rememberSaveable { mutableStateOf(DriverFlow.MAIN) }
    var zoneId by rememberSaveable { mutableStateOf("") }
    var reservationId by rememberSaveable { mutableStateOf("") }
    var documentId by rememberSaveable { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val unavailableMessage = stringResource(R.string.driver_action_unavailable)
    val unavailableAction: () -> Unit = { scope.launch { snackbar.showSnackbar(unavailableMessage) }; Unit }
    val notice: (String) -> Unit = { message -> scope.launch { snackbar.showSnackbar(message) }; Unit }
    BackHandler(enabled = flow != DriverFlow.MAIN) {
        flow = when (flow) {
            DriverFlow.CONFIRMED -> DriverFlow.ZONE_DETAILS
            DriverFlow.VEHICLE_ADD -> DriverFlow.VEHICLES
            DriverFlow.VEHICLES -> DriverFlow.MAIN
            DriverFlow.RESERVATION_EDIT -> DriverFlow.RESERVATION_DETAILS
            DriverFlow.RECEIPT_DETAIL -> DriverFlow.RECEIPTS
            else -> DriverFlow.MAIN
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (flow == DriverFlow.MAIN || flow == DriverFlow.CONFIRMED) {
                NavigationBar(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars).height(64.dp),
                    windowInsets = WindowInsets(0), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    DriverDestination.entries.forEach { item ->
                        NavigationBarItem(
                            selected = item == if (flow == DriverFlow.CONFIRMED) DriverDestination.RESERVATIONS else destination,
                            onClick = { destination = item; flow = DriverFlow.MAIN },
                            icon = { FigmaIcon(item.icon) },
                            label = { Text(stringResource(item.label), style = MaterialTheme.typography.labelSmall,
                                maxLines = 1, softWrap = false) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            if (flow == DriverFlow.ZONE_DETAILS) ZoneDetailsScreen(
                zoneId = zoneId,
                onBack = { flow = DriverFlow.MAIN },
                onPaid = { createdReservationId ->
                    if (createdReservationId != null) {
                        reservationId = createdReservationId
                        flow = DriverFlow.RESERVATION_DETAILS
                    } else flow = DriverFlow.CONFIRMED
                },
                onMyVehicles = { flow = DriverFlow.VEHICLES },
                onOpenBalance = { flow = DriverFlow.PAYMENT_BALANCE },
                onUnavailableAction = unavailableAction,
            )
            else if (flow == DriverFlow.CONFIRMED) ReservationConfirmedScreen(
                reservationId = reservationId,
                onMyReservations = { destination = DriverDestination.RESERVATIONS; flow = DriverFlow.MAIN },
                onUnavailableAction = unavailableAction,
            )
            else if (flow == DriverFlow.PAYMENT_BALANCE) OutstandingBalanceScreen(
                onBack = { flow = DriverFlow.MAIN },
                onPaid = { flow = DriverFlow.MAIN },
                onNotice = notice,
            )
            else if (flow == DriverFlow.RECEIPTS) ReceiptsScreen(
                onBack = { flow = DriverFlow.MAIN },
                onOpenDocument = { documentId = it; flow = DriverFlow.RECEIPT_DETAIL },
            )
            else if (flow == DriverFlow.RECEIPT_DETAIL) ReceiptDetailScreen(
                documentId = documentId,
                onBack = { flow = DriverFlow.RECEIPTS },
                onNotice = notice,
            )
            else if (flow == DriverFlow.PLANS) PlansScreen(
                onBack = { flow = DriverFlow.MAIN },
                onNotice = notice,
            )
            else if (flow == DriverFlow.VEHICLES) MyVehiclesScreen(
                onBack = { flow = DriverFlow.MAIN },
                onAddVehicle = { flow = DriverFlow.VEHICLE_ADD },
            )
            else if (flow == DriverFlow.VEHICLE_ADD) AddVehicleScreen(
                onBack = { flow = DriverFlow.VEHICLES },
                onSaved = { flow = DriverFlow.VEHICLES },
            )
            else if (flow == DriverFlow.RESERVATION_DETAILS) ReservationDetailsScreen(
                reservationId = reservationId,
                onBack = { flow = DriverFlow.MAIN },
                onEdit = { flow = DriverFlow.RESERVATION_EDIT },
                onNotice = notice,
                onUnavailableAction = unavailableAction,
                onOpenReservation = { reservationId = it; flow = DriverFlow.RESERVATION_DETAILS },
            )
            else if (flow == DriverFlow.RESERVATION_EDIT) EditReservationScreen(
                reservationId = reservationId,
                onBack = { flow = DriverFlow.RESERVATION_DETAILS },
                onNotice = notice,
            )
            else if (content != null) content(destination)
            else when (destination) {
                DriverDestination.EXPLORE -> ExploreParkingScreen(
                    onOpenZone = { zoneId = it; flow = DriverFlow.ZONE_DETAILS },
                    onProfile = { destination = DriverDestination.PROFILE },
                    onUnavailableAction = unavailableAction,
                )
                DriverDestination.RESERVATIONS -> ReservationsScreen(
                    onProfile = { destination = DriverDestination.PROFILE },
                    onOpenReservation = { reservationId = it; flow = DriverFlow.RESERVATION_DETAILS },
                )
                DriverDestination.PAYMENTS -> PaymentsScreen(
                    onProfile = { destination = DriverDestination.PROFILE },
                    onOpenBalance = { flow = DriverFlow.PAYMENT_BALANCE },
                    onOpenReceipts = { flow = DriverFlow.RECEIPTS },
                    onOpenDocument = { documentId = it; flow = DriverFlow.RECEIPT_DETAIL },
                    onNotice = notice,
                )
                DriverDestination.PROFILE -> ProfileScreen(
                    onSignOut = onBackToLogin,
                    onMyVehicles = { flow = DriverFlow.VEHICLES },
                    onPlans = { flow = DriverFlow.PLANS },
                    onUnavailableAction = unavailableAction,
                )
            }
        }
    }
}

@Preview(name = "Driver", widthDp = 412, heightDp = 915)
@Composable
private fun DriverLayoutPreview() {
    SpotGoTheme { DriverLayout(onBackToLogin = {}) }
}
