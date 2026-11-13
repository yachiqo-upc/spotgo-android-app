package com.yachiqo.spotgo.presentation.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yachiqo.spotgo.R
import com.yachiqo.spotgo.data.repository.RemoteAdditionalCharge
import com.yachiqo.spotgo.data.repository.RemoteBillingRepository
import com.yachiqo.spotgo.data.repository.RemoteOutstandingBalance
import com.yachiqo.spotgo.data.repository.RemotePaymentMethod
import com.yachiqo.spotgo.data.repository.RemotePaymentMethodsResult
import com.yachiqo.spotgo.data.repository.RemotePaymentResult
import com.yachiqo.spotgo.data.repository.money
import com.yachiqo.spotgo.presentation.common.FigmaIcon
import com.yachiqo.spotgo.presentation.driver.components.*
import com.yachiqo.spotgo.ui.theme.SpotGoAccent
import com.yachiqo.spotgo.ui.theme.SpotGoError
import com.yachiqo.spotgo.ui.theme.SpotGoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BalanceCardBackground = Color(0xFF33161B)
private val BalancePendingContainer = Color(0xFF4A1F24)
private val BalanceNoticeBackground = Color(0xFF2C2612)

private data class LoadedBalanceData(
    val balances: List<RemoteOutstandingBalance>?,
    val charges: List<RemoteAdditionalCharge>?,
    val tokenResult: RemotePaymentMethodsResult,
    val paymentStatuses: Map<String, String>,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutstandingBalanceScreen(
    onBack: () -> Unit,
    onPaid: () -> Unit,
    onNotice: (String) -> Unit,
) {
    var balances by remember { mutableStateOf<List<RemoteOutstandingBalance>?>(null) }
    var charges by remember { mutableStateOf<List<RemoteAdditionalCharge>?>(null) }
    var methods by remember { mutableStateOf<List<RemotePaymentMethod>?>(null) }
    var paymentStatuses by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var busyBalanceId by remember { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    var tokenId by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        loading = true
        val loaded = withContext(Dispatchers.IO) {
            val loadedBalances = RemoteBillingRepository.loadBalances()
            val loadedCharges = RemoteBillingRepository.loadCharges()
            val tokenResult = RemoteBillingRepository.loadPaymentMethods()
            val statuses = loadPaymentStatuses(loadedBalances, loadedCharges)
            LoadedBalanceData(loadedBalances, loadedCharges, tokenResult, statuses)
        }
        balances = loaded.balances
        charges = loaded.charges
        methods = (loaded.tokenResult as? RemotePaymentMethodsResult.Success)?.methods
        paymentStatuses = loaded.paymentStatuses
        tokenId = methods.orEmpty().firstOrNull { it.isDefault }?.tokenId
            ?: methods.orEmpty().firstOrNull()?.tokenId.orEmpty()
        loading = false
    }

    val openBalances = balances.orEmpty().filter { it.status == "OPEN" }
    val openTotal = openBalances.sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
    val selectedMethod = methods.orEmpty().firstOrNull { it.tokenId == tokenId }
    val backendFailed = !loading && (balances == null || charges == null || methods == null)
    val errorMessage = stringResource(R.string.balance_backend_error)
    val paidMessage = stringResource(R.string.balance_paid_notice)
    val paymentStatusPrefix = stringResource(R.string.balance_payment_status_prefix)

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.balance_title)) },
            windowInsets = WindowInsets(0), navigationIcon = {
                IconButton(onClick = onBack) { FigmaIcon(R.drawable.ic_back, stringResource(R.string.zone_back)) }
            })

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            backendFailed -> Text(errorMessage, Modifier.padding(18.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            openBalances.isEmpty() -> Text(stringResource(R.string.balance_empty), Modifier.padding(18.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp).padding(top = 6.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(color = BalanceCardBackground, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stringResource(R.string.balance_total_open), style = MaterialTheme.typography.titleMedium)
                        Text(money(openTotal), style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 30.sp, fontWeight = FontWeight.Bold), color = SpotGoError)
                        Text(stringResource(R.string.balance_open_count, openBalances.size),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                DriverSectionTitle(stringResource(R.string.balance_payment_method))
                if (methods.orEmpty().isEmpty()) {
                    DriverPanel(outlined = true) {
                        Text(stringResource(R.string.payments_method_not_registered), Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    DriverPanel {
                        ListItem(
                            headlineContent = { Text(selectedMethod?.label ?: stringResource(R.string.payments_no_method),
                                style = MaterialTheme.typography.titleMedium) },
                            supportingContent = { Text(stringResource(R.string.balance_select_method_hint),
                                style = MaterialTheme.typography.bodyMedium) },
                            trailingContent = {
                                TextButton(onClick = { picking = true }) {
                                    Text(stringResource(R.string.balance_change), style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        )
                    }
                }

                openBalances.forEach { balance ->
                    val charge = charges.orEmpty().firstOrNull { it.chargeId == balance.chargeId }
                    val paymentId = charge?.paymentId
                    val paymentStatus = paymentId?.let(paymentStatuses::get).orEmpty()
                    Surface(color = BalanceCardBackground, shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.balance_charge_failed), Modifier.weight(1f),
                                    style = MaterialTheme.typography.titleMedium)
                                Surface(color = BalancePendingContainer, shape = RoundedCornerShape(100.dp)) {
                                    Row(Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Box(Modifier.size(6.dp).background(SpotGoError, CircleShape))
                                        Text(balance.status, style = MaterialTheme.typography.bodySmall, color = SpotGoError)
                                    }
                                }
                            }
                            Text(money(balance.amount.toDoubleOrNull() ?: 0.0),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = 28.sp, fontWeight = FontWeight.Bold), color = SpotGoError)
                            Text(charge?.reason?.let { stringResource(R.string.balance_charge_line, it) }
                                ?: stringResource(R.string.balance_charge_unavailable),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (paymentStatus.isNotBlank()) {
                                Text(stringResource(R.string.balance_payment_status, paymentStatus),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            val actionLabel = when {
                                busyBalanceId == balance.balanceId -> stringResource(R.string.balance_processing)
                                paymentStatus == "REJECTED" -> stringResource(R.string.balance_retry)
                                paymentId != null -> stringResource(R.string.balance_check_payment)
                                else -> stringResource(R.string.balance_pay)
                            }
                            DriverPrimaryButton(
                                label = actionLabel,
                                enabled = busyBalanceId == null && charge != null && tokenId.isNotBlank(),
                                onClick = {
                                    if (charge != null && busyBalanceId == null && tokenId.isNotBlank()) scope.launch {
                                        busyBalanceId = balance.balanceId
                                        val initialResult = withContext(Dispatchers.IO) {
                                            when {
                                                paymentId == null -> RemoteBillingRepository.payAdditionalCharge(charge.chargeId, tokenId)
                                                paymentStatus == "REJECTED" -> RemoteBillingRepository.retry(paymentId, tokenId)
                                                paymentStatus == "INITIATED" -> RemoteBillingRepository.process(paymentId)
                                                else -> RemoteBillingRepository.reconcile(paymentId)
                                            }
                                        }
                                        val result = RemoteBillingRepository.awaitPaymentResolution(initialResult)
                                        val (updatedBalances, updatedCharges, statuses) = withContext(Dispatchers.IO) {
                                            val nextBalances = RemoteBillingRepository.loadBalances()
                                            val nextCharges = RemoteBillingRepository.loadCharges()
                                            Triple(nextBalances, nextCharges, loadPaymentStatuses(nextBalances, nextCharges))
                                        }
                                        balances = updatedBalances
                                        charges = updatedCharges
                                        paymentStatuses = statuses
                                        busyBalanceId = null
                                        if (result is RemotePaymentResult.Success && result.status == "APPROVED" &&
                                            updatedBalances?.none { it.status == "OPEN" } == true) {
                                            onNotice(paidMessage)
                                            onPaid()
                                        } else if (result is RemotePaymentResult.Success) {
                                            onNotice("$paymentStatusPrefix ${context.getString(com.yachiqo.spotgo.presentation.paymentStatusResource(result.status))}")
                                        } else {
                                            onNotice(errorMessage)
                                        }
                                    }
                                },
                            )
                        }
                    }
                }

                Surface(color = BalanceNoticeBackground, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.balance_notice_title), style = MaterialTheme.typography.titleSmall,
                            color = SpotGoAccent)
                        Text(stringResource(R.string.balance_notice_message), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (methods.orEmpty().isEmpty()) {
                    Text(stringResource(R.string.balance_tokenization_notice),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (picking && methods.orEmpty().isNotEmpty()) {
        ModalBottomSheet(onDismissRequest = { picking = false }, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.balance_select_token), style = MaterialTheme.typography.titleMedium)
                methods.orEmpty().forEach { method ->
                    DriverPanel(outlined = method.tokenId != tokenId) {
                        ListItem(
                            headlineContent = { Text(method.label, style = MaterialTheme.typography.titleMedium) },
                            supportingContent = { Text(stringResource(R.string.payments_expiration,
                                method.expirationMonth, method.expirationYear), style = MaterialTheme.typography.bodyMedium) },
                            trailingContent = {
                                if (method.tokenId == tokenId) Icon(painterResource(R.drawable.status_available),
                                    contentDescription = null, Modifier.size(18.dp), tint = SpotGoAccent)
                            },
                            modifier = Modifier.clickable { tokenId = method.tokenId; picking = false },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        )
                    }
                }
            }
        }
    }
}

private fun loadPaymentStatuses(
    balances: List<RemoteOutstandingBalance>?,
    charges: List<RemoteAdditionalCharge>?,
): Map<String, String> {
    if (balances == null || charges == null) return emptyMap()
    return buildMap {
        balances.filter { it.status == "OPEN" }.forEach { balance ->
            val paymentId = charges.firstOrNull { it.chargeId == balance.chargeId }?.paymentId ?: return@forEach
            val status = (RemoteBillingRepository.payment(paymentId) as? RemotePaymentResult.Success)?.status
            if (status != null) put(paymentId, status)
        }
    }
}

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun OutstandingBalancePreview() {
    SpotGoTheme { OutstandingBalanceScreen({}, {}, {}) }
}
