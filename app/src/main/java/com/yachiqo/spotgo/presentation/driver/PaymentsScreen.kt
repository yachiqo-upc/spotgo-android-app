package com.yachiqo.spotgo.presentation.driver

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.yachiqo.spotgo.R
import com.yachiqo.spotgo.data.repository.RemoteAdditionalCharge
import com.yachiqo.spotgo.data.repository.RemoteBillingDocumentEntry
import com.yachiqo.spotgo.data.repository.RemoteBillingRepository
import com.yachiqo.spotgo.data.repository.RemoteDigitalPayment
import com.yachiqo.spotgo.data.repository.RemoteOutstandingBalance
import com.yachiqo.spotgo.data.repository.RemotePaymentMethod
import com.yachiqo.spotgo.data.repository.RemotePaymentMethodsResult
import com.yachiqo.spotgo.data.repository.isoDateLabel
import com.yachiqo.spotgo.data.repository.money
import com.yachiqo.spotgo.presentation.driver.components.*
import com.yachiqo.spotgo.presentation.paymentStatusResource
import com.yachiqo.spotgo.ui.theme.SpotGoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun PaymentsScreen(
    onProfile: () -> Unit,
    onOpenBalance: () -> Unit,
    onOpenReceipts: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onNotice: (String) -> Unit,
) {
    var payments by remember { mutableStateOf<List<RemoteDigitalPayment>?>(null) }
    var balances by remember { mutableStateOf<List<RemoteOutstandingBalance>?>(null) }
    var charges by remember { mutableStateOf<List<RemoteAdditionalCharge>?>(null) }
    var methods by remember { mutableStateOf<List<RemotePaymentMethod>?>(null) }
    var documents by remember { mutableStateOf<List<RemoteBillingDocumentEntry>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var showAddMethodDialog by remember { mutableStateOf(false) }
    var simulatedBrand by remember { mutableStateOf("visa") }
    var simulatedLastFour by remember { mutableStateOf("4242") }
    var addingMethod by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        loading = true
        val result = withContext(Dispatchers.IO) {
            listOf(
                RemoteBillingRepository.loadPayments(),
                RemoteBillingRepository.loadBalances(),
                RemoteBillingRepository.loadCharges(),
                (RemoteBillingRepository.loadPaymentMethods() as? RemotePaymentMethodsResult.Success)?.methods,
                RemoteBillingRepository.loadDocuments(),
            )
        }
        @Suppress("UNCHECKED_CAST")
        payments = result[0] as? List<RemoteDigitalPayment>
        @Suppress("UNCHECKED_CAST")
        balances = result[1] as? List<RemoteOutstandingBalance>
        @Suppress("UNCHECKED_CAST")
        charges = result[2] as? List<RemoteAdditionalCharge>
        @Suppress("UNCHECKED_CAST")
        methods = result[3] as? List<RemotePaymentMethod>
        @Suppress("UNCHECKED_CAST")
        documents = result[4] as? List<RemoteBillingDocumentEntry>
        loading = false
    }

    val pageFailed = !loading && (payments == null || balances == null || charges == null || methods == null || documents == null)
    val openBalances = balances.orEmpty().filter { it.status == "OPEN" }
    val openAmount = openBalances.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
    val documentPayments = documents.orEmpty().mapTo(mutableSetOf()) { it.payment.paymentId }
    val latestPayments = payments.orEmpty().sortedByDescending { it.createdAt }.take(3)
    val noReceipt = stringResource(R.string.payments_no_receipt)
    val methodAddedMessage = stringResource(R.string.payments_method_added)
    val favoriteUpdatedMessage = stringResource(R.string.payments_favorite_updated)
    val methodSaveFailedMessage = stringResource(R.string.payments_method_save_failed)

    DriverPage(stringResource(R.string.driver_payments), stringResource(R.string.payments_subtitle), onProfile) {
        when {
            loading -> DriverPanel {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(22.dp))
                    Text(stringResource(R.string.payments_loading), style = MaterialTheme.typography.bodyMedium)
                }
            }
            pageFailed -> DriverPanel {
                Text(stringResource(R.string.payments_backend_error), Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            else -> {
                DriverPanel {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 104.dp).padding(16.dp)
                            .then(if (openBalances.isNotEmpty()) Modifier.clickable(onClick = onOpenBalance) else Modifier),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(stringResource(R.string.payments_balance), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary)
                            Text(if (openBalances.isNotEmpty()) money(openAmount) else stringResource(R.string.payments_no_balance),
                                style = MaterialTheme.typography.headlineMedium)
                        }
                        if (openBalances.isNotEmpty()) {
                            StatusBadge(stringResource(R.string.payments_pending), positive = false)
                        } else {
                            StatusBadge(stringResource(R.string.payments_up_to_date), positive = true)
                        }
                    }
                }

                DriverSectionTitle(stringResource(R.string.payments_methods))
                DriverPanel(outlined = true) {
                    if (methods.orEmpty().isEmpty()) {
                        ListItem(
                            headlineContent = {
                                Text(stringResource(R.string.payments_no_method), style = MaterialTheme.typography.titleMedium)
                            },
                            supportingContent = {
                                Text(stringResource(R.string.payments_method_not_registered), style = MaterialTheme.typography.bodyMedium)
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        )
                    } else {
                        methods.orEmpty().forEach { method ->
                            ListItem(
                                headlineContent = { Text(method.label, style = MaterialTheme.typography.titleMedium) },
                                supportingContent = {
                                    Text(stringResource(R.string.payments_expiration, method.expirationMonth, method.expirationYear),
                                        style = MaterialTheme.typography.bodyMedium)
                                },
                                trailingContent = {
                                    if (method.isDefault) {
                                        StatusBadge(stringResource(R.string.payments_favorite), positive = true)
                                    } else {
                                        TextButton(onClick = {
                                            scope.launch {
                                                val saved = withContext(Dispatchers.IO) {
                                                    RemoteBillingRepository.setDefaultPaymentMethod(method.tokenId)
                                                }
                                                if (saved) {
                                                    val refreshed = withContext(Dispatchers.IO) {
                                                        RemoteBillingRepository.loadPaymentMethods()
                                                    }
                                                    val currentMethods = methods.orEmpty()
                                                    methods = (refreshed as? RemotePaymentMethodsResult.Success)?.methods
                                                        ?: currentMethods.map { it.copy(isDefault = it.tokenId == method.tokenId) }
                                                    onNotice(favoriteUpdatedMessage)
                                                } else {
                                                    onNotice(methodSaveFailedMessage)
                                                }
                                            }
                                        }) {
                                            Text(stringResource(R.string.payments_set_favorite))
                                        }
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            )
                        }
                    }
                }
                DriverOutlineButton(
                    label = stringResource(R.string.payments_add_method),
                    onClick = { showAddMethodDialog = true },
                    enabled = !addingMethod,
                )
                Text(stringResource(R.string.payments_tokenization_notice),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { DriverSectionTitle(stringResource(R.string.payments_transactions)) }
                    TextButton(onClick = onOpenReceipts) { Text(stringResource(R.string.payments_see_all)) }
                }
                if (latestPayments.isEmpty()) {
                    Text(stringResource(R.string.payments_no_transactions),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    DriverPanel {
                        latestPayments.forEach { payment ->
                            val summary = paymentSummary(payment)
                            ListItem(
                                headlineContent = { Text(summary, style = MaterialTheme.typography.titleMedium) },
                                supportingContent = {
                                    Text("${isoDateLabel(payment.createdAt)} · ${stringResource(paymentStatusResource(payment.status))}",
                                        style = MaterialTheme.typography.bodyMedium)
                                },
                                trailingContent = {
                                    Text("${payment.currency} ${payment.amount}", style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                },
                                modifier = Modifier.heightIn(min = 72.dp).clickable {
                                    if (payment.paymentId in documentPayments) onOpenDocument(payment.paymentId)
                                    else onNotice(noReceipt)
                                },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                            )
                        }
                    }
                }

                if (showAddMethodDialog) {
                    AlertDialog(
                        onDismissRequest = { if (!addingMethod) showAddMethodDialog = false },
                        title = { Text(stringResource(R.string.payments_add_method)) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(stringResource(R.string.payments_simulated_method_help),
                                    style = MaterialTheme.typography.bodyMedium)
                                OutlinedTextField(
                                    value = simulatedBrand,
                                    onValueChange = { simulatedBrand = it.trim().lowercase() },
                                    label = { Text(stringResource(R.string.payments_card_brand)) },
                                    supportingText = { Text(stringResource(R.string.payments_card_brands_hint)) },
                                    singleLine = true,
                                )
                                OutlinedTextField(
                                    value = simulatedLastFour,
                                    onValueChange = { value -> simulatedLastFour = value.filter(Char::isDigit).take(4) },
                                    label = { Text(stringResource(R.string.payments_last_four)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                )
                            }
                        },
                        confirmButton = {
                            TextButton(
                                enabled = !addingMethod && simulatedBrand in setOf("visa", "mastercard", "amex", "discover") && simulatedLastFour.length == 4,
                                onClick = {
                                    addingMethod = true
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            RemoteBillingRepository.addSimulatedPaymentMethod(simulatedBrand, simulatedLastFour)
                                        }
                                        if (result is com.yachiqo.spotgo.data.repository.RemotePaymentMethodMutationResult.Success) {
                                            val refreshed = withContext(Dispatchers.IO) {
                                                RemoteBillingRepository.loadPaymentMethods()
                                            }
                                            methods = (refreshed as? RemotePaymentMethodsResult.Success)?.methods
                                                ?: (methods.orEmpty() + result.method)
                                            showAddMethodDialog = false
                                            simulatedBrand = "visa"
                                            simulatedLastFour = "4242"
                                            onNotice(methodAddedMessage)
                                        } else {
                                            onNotice(methodSaveFailedMessage)
                                        }
                                        addingMethod = false
                                    }
                                },
                            ) {
                                if (addingMethod) CircularProgressIndicator(Modifier.size(18.dp))
                                else Text(stringResource(R.string.payments_add_method_confirm))
                            }
                        },
                        dismissButton = {
                            TextButton(enabled = !addingMethod, onClick = { showAddMethodDialog = false }) {
                                Text(stringResource(R.string.payments_cancel))
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun paymentSummary(payment: RemoteDigitalPayment): String = when (payment.operationType) {
    "RESERVATION", "RESERVATION_ADJUSTMENT" -> stringResource(R.string.payments_reservation)
    "SUBSCRIPTION", "SUBSCRIPTION_ADJUSTMENT" -> stringResource(R.string.payments_subscription)
    "ADDITIONAL_CHARGE" -> stringResource(R.string.payments_additional_charge)
    else -> stringResource(R.string.payments_operation)
}

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun PaymentsPreview() {
    SpotGoTheme { PaymentsScreen({}, {}, {}, {}, {}) }
}
