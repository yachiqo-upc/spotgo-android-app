package com.yachiqo.spotgo.presentation.driver

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.yachiqo.spotgo.R
import com.yachiqo.spotgo.data.repository.PaymentRepository
import com.yachiqo.spotgo.presentation.driver.components.*
import com.yachiqo.spotgo.ui.theme.SpotGoTheme

/**
 * Mockup 08 — Payments (US19 / US21): outstanding balance card (opens the
 * retry flow of mockup 38), saved payment methods, the three most recent
 * transactions (each one opens its receipt / invoice) and "See all" that
 * opens the documents list of mockup 14.
 */
@Composable
fun PaymentsScreen(
    onProfile: () -> Unit,
    onOpenBalance: () -> Unit,
    onOpenReceipts: () -> Unit,
    onOpenDocument: (String) -> Unit,
    onNotice: (String) -> Unit,
    onUnavailableAction: () -> Unit,
) {
    val balance = PaymentRepository.open_balance()
    val token = PaymentRepository.default_token()
    val transactions = PaymentRepository.transactions().take(3)
    val noReceipt = stringResource(R.string.payments_no_receipt)

    DriverPage(stringResource(R.string.driver_payments), stringResource(R.string.payments_subtitle), onProfile) {
        DriverPanel {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 104.dp).padding(16.dp)
                    .then(if (balance != null) Modifier.clickable(onClick = onOpenBalance) else Modifier),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(stringResource(R.string.payments_balance), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary)
                    Text(
                        if (balance != null) "S/ ${balance.amount}"
                        else stringResource(R.string.payments_balance_value),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
                StatusBadge(
                    stringResource(if (balance != null) R.string.payments_pending else R.string.payments_up_to_date),
                    positive = balance == null,
                )
            }
        }

        DriverSectionTitle(stringResource(R.string.payments_methods))
        DriverPanel(outlined = true) {
            ListItem(
                headlineContent = { Text(token?.display_label ?: stringResource(R.string.payments_card),
                    style = MaterialTheme.typography.titleMedium) },
                supportingContent = { Text(token?.display_details ?: stringResource(R.string.payments_card_details),
                    style = MaterialTheme.typography.bodyMedium) },
                trailingContent = { Text(stringResource(R.string.payments_chevron)) },
                modifier = Modifier.heightIn(min = 80.dp).clickable(onClick = onUnavailableAction),
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            )
        }
        DriverOutlineButton(stringResource(R.string.payments_add_method), onUnavailableAction)

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { DriverSectionTitle(stringResource(R.string.payments_transactions)) }
            TextButton(onClick = onOpenReceipts) {
                Text(stringResource(R.string.payments_see_all), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (transactions.isEmpty()) {
            Text(stringResource(R.string.payments_no_transactions),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            DriverPanel {
                transactions.forEach { payment ->
                    ListItem(
                        headlineContent = { Text(payment.summary, style = MaterialTheme.typography.titleMedium) },
                        supportingContent = { Text(payment.context_label, style = MaterialTheme.typography.bodyMedium) },
                        trailingContent = {
                            Text("S/ ${payment.amount}", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        modifier = Modifier.heightIn(min = 72.dp).clickable {
                            val document = PaymentRepository.document_of_payment(payment.payment_id)
                            if (document != null) onOpenDocument(document.document_id) else onNotice(noReceipt)
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun PaymentsPreview() {
    SpotGoTheme { PaymentsScreen({}, {}, {}, {}, {}, {}) }
}
