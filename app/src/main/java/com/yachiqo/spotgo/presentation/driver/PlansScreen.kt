package com.yachiqo.spotgo.presentation.driver

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yachiqo.spotgo.R
import com.yachiqo.spotgo.data.repository.RemoteBillingRepository
import com.yachiqo.spotgo.data.repository.RemotePaymentMethod
import com.yachiqo.spotgo.data.repository.RemotePaymentMethodsResult
import com.yachiqo.spotgo.data.repository.RemotePaymentResult
import com.yachiqo.spotgo.data.repository.RemoteSubscription
import com.yachiqo.spotgo.data.repository.RemoteSubscriptionPlan
import com.yachiqo.spotgo.data.repository.isoDateLabel
import com.yachiqo.spotgo.data.repository.money
import com.yachiqo.spotgo.presentation.common.FigmaIcon
import com.yachiqo.spotgo.presentation.driver.components.*
import com.yachiqo.spotgo.ui.theme.SpotGoAccent
import com.yachiqo.spotgo.ui.theme.SpotGoSuccess
import com.yachiqo.spotgo.ui.theme.SpotGoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlansScreen(
    onBack: () -> Unit,
    onNotice: (String) -> Unit,
) {
    var plans by remember { mutableStateOf<List<RemoteSubscriptionPlan>?>(null) }
    var subscriptions by remember { mutableStateOf<List<RemoteSubscription>?>(null) }
    var methods by remember { mutableStateOf<List<RemotePaymentMethod>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var busyPlanCode by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        loading = true
        val (loadedPlans, loadedSubscriptions, tokenResult) = withContext(Dispatchers.IO) {
            Triple(
                RemoteBillingRepository.loadPlans(),
                RemoteBillingRepository.loadSubscriptions(),
                RemoteBillingRepository.loadPaymentMethods(),
            )
        }
        plans = loadedPlans
        subscriptions = loadedSubscriptions
        methods = (tokenResult as? RemotePaymentMethodsResult.Success)?.methods
        loading = false
    }

    val failure = !loading && (plans == null || subscriptions == null || methods == null)
    val activeSubscription = subscriptions.orEmpty().firstOrNull { it.status == "ACTIVE" }
    val defaultMethod = methods.orEmpty().firstOrNull { it.isDefault } ?: methods.orEmpty().firstOrNull()
    val successMessage = stringResource(R.string.plans_payment_approved)
    val tokenMessage = stringResource(R.string.plans_token_required)
    val errorMessage = stringResource(R.string.plans_backend_error)
    val paymentStatusPrefix = stringResource(R.string.plans_payment_status_prefix)
    val cancelledMessage = stringResource(R.string.plans_cancelled)

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.plans_title)) },
            windowInsets = WindowInsets(0), navigationIcon = {
                IconButton(onClick = onBack) { FigmaIcon(R.drawable.ic_back, stringResource(R.string.zone_back)) }
            })

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp).padding(top = 6.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(stringResource(R.string.plans_heading), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.plans_subtitle), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            when {
                loading -> DriverPanel {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(22.dp))
                        Text(stringResource(R.string.plans_loading), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                failure -> DriverPanel {
                    Text(errorMessage, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error)
                }
                else -> {
                    DriverPanel(outlined = true) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(stringResource(R.string.plans_current_label), style = MaterialTheme.typography.labelLarge)
                            Text(activeSubscription?.let {
                                stringResource(planDisplayNameResource(it.planCode))
                            } ?: stringResource(R.string.plans_no_active),
                                style = MaterialTheme.typography.titleMedium, color = SpotGoAccent)
                            activeSubscription?.endAt?.let {
                                Text(stringResource(R.string.plans_end_date, isoDateLabel(it)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    if (plans.orEmpty().isEmpty()) {
                        Text(stringResource(R.string.plans_catalog_empty), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    plans.orEmpty().forEach { plan ->
                        val isCurrent = activeSubscription?.planCode == plan.code
                        val planName = stringResource(planDisplayNameResource(plan.code))
                        PlanCard(
                            name = planName,
                            price = plan.price.toDoubleOrNull()?.let(::money)
                                ?: stringResource(R.string.plans_price_unavailable),
                            period = stringResource(R.string.plans_billing_period, plan.billingPeriodMonths),
                            discount = stringResource(R.string.plans_discount, plan.discountPercent),
                            current = isCurrent,
                            processing = busy && busyPlanCode == plan.code,
                            enabled = !busy && activeSubscription == null && defaultMethod != null,
                            buttonLabel = when {
                                isCurrent -> stringResource(R.string.plans_current_button)
                                activeSubscription != null -> stringResource(R.string.plans_cancel_first)
                                else -> stringResource(R.string.plans_subscribe)
                            },
                            onClick = {
                                val method = defaultMethod
                                if (method == null) {
                                    onNotice(tokenMessage)
                                } else if (!busy) {
                                    busy = true
                                    busyPlanCode = plan.code
                                    scope.launch {
                                        val initialPayment = withContext(Dispatchers.IO) {
                                            RemoteBillingRepository.paySubscription(plan.code, method.tokenId)
                                        }
                                        val payment = RemoteBillingRepository.awaitPaymentResolution(initialPayment)
                                        busy = false
                                        busyPlanCode = ""
                                        when (payment) {
                                            is RemotePaymentResult.Success -> {
                                                if (payment.status == "APPROVED") {
                                                    subscriptions = withContext(Dispatchers.IO) {
                                                        RemoteBillingRepository.loadSubscriptions()
                                                    }
                                                    onNotice(successMessage)
                                                } else onNotice("$paymentStatusPrefix ${context.getString(com.yachiqo.spotgo.presentation.paymentStatusResource(payment.status))}")
                                            }
                                            RemotePaymentResult.Failure -> onNotice(errorMessage)
                                        }
                                    }
                                }
                            },
                        )
                    }

                    if (activeSubscription != null) {
                        DriverOutlineButton(
                            label = stringResource(R.string.plans_cancel_subscription),
                            enabled = !busy,
                            onClick = {
                                if (!busy) scope.launch {
                                    busy = true
                                    val cancelled = withContext(Dispatchers.IO) {
                                        RemoteBillingRepository.cancelSubscription(activeSubscription.subscriptionId)
                                    }
                                    if (cancelled) {
                                        subscriptions = withContext(Dispatchers.IO) {
                                            RemoteBillingRepository.loadSubscriptions()
                                        }
                                    }
                                    busy = false
                                    onNotice(if (cancelled) cancelledMessage else errorMessage)
                                }
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (defaultMethod == null && plans.orEmpty().isNotEmpty()) {
                        Text(stringResource(R.string.plans_tokenization_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
    }
}

@Composable
private fun PlanCard(
    name: String,
    price: String,
    period: String,
    discount: String,
    current: Boolean,
    processing: Boolean,
    enabled: Boolean,
    buttonLabel: String,
    onClick: () -> Unit,
) {
    DriverPanel(outlined = true) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (current) StatusBadge(stringResource(R.string.plans_badge_current))
            }
            Text(price, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp), color = SpotGoAccent)
            Text(period, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(discount, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DriverPrimaryButton(
                label = if (processing) stringResource(R.string.plans_processing) else buttonLabel,
                onClick = onClick,
                enabled = enabled,
            )
        }
    }
}

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun PlansPreview() {
    SpotGoTheme { PlansScreen({}, {}) }
}
