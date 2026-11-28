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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import com.yachiqo.spotgo.R
import com.yachiqo.spotgo.data.repository.CardForm
import com.yachiqo.spotgo.data.repository.CardFormError
import com.yachiqo.spotgo.data.repository.CardFormInput
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
    // Transient card form: lives only while the dialog is open, never saved
    // (toString is masked) and discarded on save or close.
    var cardForm by remember { mutableStateOf(CardFormInput("", "", "", "")) }
    var cardNumberField by remember { mutableStateOf(TextFieldValue("")) }
    var cardExpirationField by remember { mutableStateOf(TextFieldValue("")) }
    var cardError by remember { mutableStateOf<CardFormError?>(null) }
    var addingMethod by remember { mutableStateOf(false) }
    var methodPendingRemoval by remember { mutableStateOf<RemotePaymentMethod?>(null) }
    var removingMethod by remember { mutableStateOf(false) }
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
    val methodRemovedMessage = stringResource(R.string.payments_method_removed)
    val methodRemoveFailedMessage = stringResource(R.string.payments_method_remove_failed)

    DriverPage(stringResource(R.string.driver_payments), stringResource(R.string.payments_subtitle)) {
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
                                // Only brand + last four: the backend token carries no real expiry.
                                supportingContent = {
                                    Text(stringResource(R.string.payments_test_mode),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                                        IconButton(
                                            onClick = { methodPendingRemoval = method },
                                            enabled = !removingMethod,
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_delete),
                                                contentDescription = stringResource(R.string.payments_remove_method),
                                                tint = MaterialTheme.colorScheme.error,
                                            )
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
                        onDismissRequest = {
                            if (!addingMethod) {
                                showAddMethodDialog = false
                                cardForm = CardFormInput("", "", "", "")
                                cardNumberField = TextFieldValue("")
                                cardExpirationField = TextFieldValue("")
                                cardError = null
                            }
                        },
                        title = { Text(stringResource(R.string.payments_add_method)) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                // Visible label: this is a test environment, no real card is charged.
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    shape = MaterialTheme.shapes.small,
                                ) {
                                    Text(
                                        stringResource(R.string.payments_test_mode),
                                        Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Text(stringResource(R.string.payments_simulated_method_help),
                                    style = MaterialTheme.typography.bodyMedium)
                                OutlinedTextField(
                                    value = cardForm.holder,
                                    onValueChange = { cardForm = cardForm.with(holder = it); cardError = null },
                                    label = { Text(stringResource(R.string.payments_card_holder)) },
                                    isError = cardError == CardFormError.HOLDER_REQUIRED,
                                    supportingText = if (cardError == CardFormError.HOLDER_REQUIRED) {
                                        { Text(stringResource(R.string.payments_card_holder_required)) }
                                    } else null,
                                    singleLine = true,
                                )
                                OutlinedTextField(
                                    value = cardNumberField,
                                    onValueChange = { value ->
                                        // Raw digits only; the grouping is applied on
                                        // screen by the VisualTransformation so the
                                        // state rewrite never drags the cursor.
                                        cardNumberField = CardFormFormat.capNumber(value)
                                        cardForm = cardForm.with(number = cardNumberField.text)
                                        cardError = null
                                    },
                                    visualTransformation = CardNumberVisualTransformation,
                                    label = { Text(stringResource(R.string.payments_card_number)) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    isError = cardError == CardFormError.NUMBER_INVALID ||
                                        cardError == CardFormError.BRAND_UNSUPPORTED,
                                    supportingText = when (cardError) {
                                        CardFormError.NUMBER_INVALID -> {
                                            { Text(stringResource(R.string.payments_card_number_invalid)) }
                                        }
                                        CardFormError.BRAND_UNSUPPORTED -> {
                                            { Text(stringResource(R.string.payments_card_brand_unsupported)) }
                                        }
                                        else -> null
                                    },
                                    singleLine = true,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = cardExpirationField,
                                        onValueChange = { value ->
                                            // Raw "MMYY" digits; "MM/YY" only on screen.
                                            cardExpirationField = CardFormFormat.capExpiration(value)
                                            cardForm = cardForm.with(expiration = cardExpirationField.text)
                                            cardError = null
                                        },
                                        visualTransformation = CardExpirationVisualTransformation,
                                        label = { Text(stringResource(R.string.payments_card_expiry)) },
                                        placeholder = { Text(stringResource(R.string.payments_card_expiry_hint)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        isError = cardError == CardFormError.EXPIRY_INVALID,
                                        supportingText = if (cardError == CardFormError.EXPIRY_INVALID) {
                                            { Text(stringResource(R.string.payments_card_expiry_invalid)) }
                                        } else null,
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                    OutlinedTextField(
                                        value = cardForm.cvv,
                                        onValueChange = { value ->
                                            cardForm = cardForm.with(cvv = value.filter(Char::isDigit).take(4))
                                            cardError = null
                                        },
                                        label = { Text(stringResource(R.string.payments_card_cvv)) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                        isError = cardError == CardFormError.CVV_INVALID,
                                        supportingText = if (cardError == CardFormError.CVV_INVALID) {
                                            { Text(stringResource(R.string.payments_card_cvv_invalid)) }
                                        } else null,
                                        singleLine = true,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                enabled = !addingMethod,
                                onClick = {
                                    val error = CardForm.validate(cardForm)
                                    if (error != null) {
                                        cardError = error
                                        return@TextButton
                                    }
                                    val providerTokenRef = CardForm.providerTokenRef(cardForm)
                                    if (providerTokenRef == null) {
                                        cardError = CardFormError.BRAND_UNSUPPORTED
                                        return@TextButton
                                    }
                                    addingMethod = true
                                    scope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            RemoteBillingRepository.addPaymentToken(providerTokenRef)
                                        }
                                        if (result is com.yachiqo.spotgo.data.repository.RemotePaymentMethodMutationResult.Success) {
                                            val refreshed = withContext(Dispatchers.IO) {
                                                RemoteBillingRepository.loadPaymentMethods()
                                            }
                                            methods = (refreshed as? RemotePaymentMethodsResult.Success)?.methods
                                                ?: (methods.orEmpty() + result.method)
                                            showAddMethodDialog = false
                                            // The transient form is discarded the moment the token is saved.
                                            cardForm = CardFormInput("", "", "", "")
                                            cardNumberField = TextFieldValue("")
                                            cardExpirationField = TextFieldValue("")
                                            cardError = null
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
                            TextButton(enabled = !addingMethod, onClick = {
                                showAddMethodDialog = false
                                cardForm = CardFormInput("", "", "", "")
                                cardNumberField = TextFieldValue("")
                                cardExpirationField = TextFieldValue("")
                                cardError = null
                            }) {
                                Text(stringResource(R.string.payments_cancel))
                            }
                        },
                    )
                }
            }
        }
    }

    methodPendingRemoval?.let { method ->
        AlertDialog(
            onDismissRequest = { if (!removingMethod) methodPendingRemoval = null },
            title = { Text(stringResource(R.string.payments_remove_method_title)) },
            text = { Text(stringResource(R.string.payments_remove_method_message, method.label)) },
            confirmButton = {
                TextButton(
                    enabled = !removingMethod,
                    onClick = {
                        removingMethod = true
                        scope.launch {
                            val removed = withContext(Dispatchers.IO) {
                                RemoteBillingRepository.removePaymentMethod(method.tokenId)
                            }
                            if (removed) {
                                val refreshed = withContext(Dispatchers.IO) {
                                    RemoteBillingRepository.loadPaymentMethods()
                                }
                                methods = (refreshed as? RemotePaymentMethodsResult.Success)?.methods
                                    ?: methods.orEmpty().filterNot { it.tokenId == method.tokenId }
                                methodPendingRemoval = null
                                onNotice(methodRemovedMessage)
                            } else {
                                onNotice(methodRemoveFailedMessage)
                            }
                            removingMethod = false
                        }
                    },
                ) {
                    if (removingMethod) CircularProgressIndicator(Modifier.size(18.dp))
                    else Text(stringResource(R.string.payments_remove_method))
                }
            },
            dismissButton = {
                TextButton(enabled = !removingMethod, onClick = { methodPendingRemoval = null }) {
                    Text(stringResource(R.string.payments_cancel))
                }
            },
        )
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
    SpotGoTheme { PaymentsScreen({}, {}, {}, {}) }
}
