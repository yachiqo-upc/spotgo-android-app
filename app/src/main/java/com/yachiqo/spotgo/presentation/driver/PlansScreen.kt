package com.yachiqo.spotgo.presentation.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yachiqo.spotgo.R
import com.yachiqo.spotgo.data.model.SubscriptionPlan
import com.yachiqo.spotgo.data.repository.PaymentRepository
import com.yachiqo.spotgo.presentation.common.FigmaIcon
import com.yachiqo.spotgo.presentation.driver.components.*
import com.yachiqo.spotgo.ui.theme.SpotGoAccent
import com.yachiqo.spotgo.ui.theme.SpotGoSuccess
import com.yachiqo.spotgo.ui.theme.SpotGoTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Prices of mockup 13 (soles, shown in the cards). */
private const val MONTHLY_PRICE = 14.90
private const val ANNUAL_PRICE = 149.00

/**
 * Mockup 13 — Plans & subscription (US20):
 *  - scenario 1: switch to Free (immediate, no charge, 0% discount);
 *  - scenarios 2 / 3: Plus Monthly (30 days, 10%) and Plus Annual
 *    (12 months, 15%) paid with the simulated digital payment (1.5 s);
 *  - scenario 5: cancel the renewal — the benefits stay until `end_at` and
 *    no new charge is generated.
 *
 * The "Cancel renewal" control and the renewal status line are UI extras:
 * they are not drawn in mockup 13 but they are required by US20.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlansScreen(
    onBack: () -> Unit,
    onNotice: (String) -> Unit,
) {
    // remember() keeps the subscription alive while the screen is open; a
    // payment changes it and the recomposition reads the new value.
    var revision by remember { mutableStateOf(0) }
    val subscription = remember(revision) { PaymentRepository.subscription() }
    val currentPlan = subscription?.plan ?: SubscriptionPlan.FREE
    val autoRenew = subscription?.auto_renew ?: true
    val endLabel = subscription?.end_at?.takeIf { it.isNotBlank() }
        ?.let { PaymentRepository.shortDateLabel(it) }
        ?: stringResource(R.string.plans_no_end)

    var paying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Strings resolved before the callbacks (they run outside composition).
    val noticeFree = stringResource(R.string.plans_notice_free)
    val noticeMonthly = stringResource(R.string.plans_notice_monthly)
    val noticeAnnual = stringResource(R.string.plans_notice_annual)
    val noticeFailed = stringResource(R.string.plans_notice_failed)
    val noticeRenewalCancelled = stringResource(R.string.plans_notice_renewal_cancelled)
    val noticeRenewalResumed = stringResource(R.string.plans_notice_renewal_resumed)

    /** Simulated approval of the plan payment (the user asked for 1.5 s). */
    fun purchase(plan: String, price: Double) {
        if (paying) return
        paying = true
        scope.launch {
            delay(1500)
            val payment = PaymentRepository.subscribe_plan(plan, price)
            paying = false
            revision++
            if (payment != null) {
                onNotice(if (plan == SubscriptionPlan.ANNUAL) noticeAnnual else noticeMonthly)
            } else {
                onNotice(noticeFailed)
            }
        }
    }

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

            Text(
                stringResource(R.string.plans_current_label, currentPlan),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            PlanCard(
                name = stringResource(R.string.plans_free_name),
                price = stringResource(R.string.plans_free_price),
                priceAccent = true,
                discount = stringResource(R.string.plans_free_discount),
                badge = stringResource(R.string.plans_badge_current),
                badgeKind = if (currentPlan == SubscriptionPlan.FREE) PlanBadge.CURRENT else PlanBadge.NONE,
                buttonLabel = stringResource(
                    if (currentPlan == SubscriptionPlan.FREE) R.string.plans_current_button
                    else R.string.plans_choose_free
                ),
                primary = false,
                enabled = currentPlan != SubscriptionPlan.FREE && !paying,
                onClick = {
                    // US20 scenario 1 — immediate, no charge.
                    PaymentRepository.activate_free_plan()
                    revision++
                    onNotice(noticeFree)
                },
            )

            PlanCard(
                name = stringResource(R.string.plans_monthly_name),
                price = stringResource(R.string.plans_monthly_price),
                priceAccent = true,
                discount = stringResource(R.string.plans_monthly_discount),
                badge = stringResource(R.string.plans_badge_plus),
                badgeKind = if (currentPlan == SubscriptionPlan.MONTHLY) PlanBadge.CURRENT else PlanBadge.PLUS,
                buttonLabel = stringResource(
                    if (currentPlan == SubscriptionPlan.MONTHLY) R.string.plans_current_button
                    else R.string.plans_choose_monthly
                ),
                primary = currentPlan != SubscriptionPlan.MONTHLY,
                enabled = currentPlan != SubscriptionPlan.MONTHLY && !paying,
                paying = paying && currentPlan != SubscriptionPlan.MONTHLY,
                onClick = { purchase(SubscriptionPlan.MONTHLY, MONTHLY_PRICE) },
            )

            PlanCard(
                name = stringResource(R.string.plans_annual_name),
                price = stringResource(R.string.plans_annual_price),
                priceAccent = false,
                discount = stringResource(R.string.plans_annual_discount),
                badge = stringResource(R.string.plans_badge_save),
                badgeKind = if (currentPlan == SubscriptionPlan.ANNUAL) PlanBadge.CURRENT else PlanBadge.PLUS,
                buttonLabel = stringResource(
                    if (currentPlan == SubscriptionPlan.ANNUAL) R.string.plans_current_button
                    else R.string.plans_choose_annual
                ),
                primary = false,
                enabled = currentPlan != SubscriptionPlan.ANNUAL && !paying,
                paying = paying && currentPlan != SubscriptionPlan.ANNUAL,
                onClick = { purchase(SubscriptionPlan.ANNUAL, ANNUAL_PRICE) },
            )

            // US20 scenario 5 — cancel / resume the renewal (UI extra).
            if (currentPlan != SubscriptionPlan.FREE) {
                DriverPanel {
                    Column(Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            stringResource(
                                if (autoRenew) R.string.plans_renewal_active else R.string.plans_renewal_cancelled,
                                endLabel,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (autoRenew) MaterialTheme.colorScheme.onSurfaceVariant else SpotGoAccent,
                        )
                        if (autoRenew) {
                            DriverOutlineButton(
                                label = stringResource(R.string.plans_cancel_renewal),
                                onClick = {
                                    PaymentRepository.cancel_renewal()
                                    revision++
                                    onNotice(noticeRenewalCancelled)
                                },
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            DriverOutlineButton(
                                label = stringResource(R.string.plans_renewal_resume),
                                onClick = {
                                    PaymentRepository.resume_renewal()
                                    revision++
                                    onNotice(noticeRenewalResumed)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Badge style of the plan cards (mockup 13: Current · Plus · Save). */
private enum class PlanBadge { NONE, CURRENT, PLUS }

@Composable
private fun PlanCard(
    name: String,
    price: String,
    priceAccent: Boolean,
    discount: String,
    badge: String,
    badgeKind: PlanBadge,
    buttonLabel: String,
    primary: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    paying: Boolean = false,
) {
    DriverPanel(outlined = true) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                if (badgeKind != PlanBadge.NONE) PlanBadgeChip(badge, badgeKind)
            }
            Text(
                price,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp),
                color = if (priceAccent) SpotGoAccent else MaterialTheme.colorScheme.onSurface,
            )
            Text(discount, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (primary) {
                DriverPrimaryButton(
                    label = if (paying) stringResource(R.string.plans_processing) else buttonLabel,
                    onClick = { if (enabled) onClick() },
                )
            } else {
                DriverOutlineButton(
                    label = if (paying) stringResource(R.string.plans_processing) else buttonLabel,
                    onClick = { if (enabled) onClick() },
                )
            }
        }
    }
}

@Composable
private fun PlanBadgeChip(label: String, kind: PlanBadge) {
    val container = if (kind == PlanBadge.PLUS) Color(0xFF18332A) else MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (kind == PlanBadge.PLUS) SpotGoSuccess else SpotGoAccent
    Surface(color = container, shape = RoundedCornerShape(100.dp)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).background(content, CircleShape))
            Text(label, style = MaterialTheme.typography.bodySmall, color = content)
        }
    }
}

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun PlansPreview() {
    SpotGoTheme { PlansScreen({}, {}) }
}
