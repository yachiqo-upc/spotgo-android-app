package com.yachiqo.spotgo.presentation.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yachiqo.spotgo.R
import com.yachiqo.spotgo.data.repository.PaymentRepository
import com.yachiqo.spotgo.presentation.common.FigmaIcon
import com.yachiqo.spotgo.presentation.driver.components.*
import com.yachiqo.spotgo.ui.theme.SpotGoAccent
import com.yachiqo.spotgo.ui.theme.SpotGoError
import com.yachiqo.spotgo.ui.theme.SpotGoOnAccent
import com.yachiqo.spotgo.ui.theme.SpotGoTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Dark red card of the failed additional charge (mockup 38). */
private val BalanceCardBackground = Color(0xFF33161B)
private val BalancePendingContainer = Color(0xFF4A1F24)
private val BalanceNoticeBackground = Color(0xFF2C2612)

/**
 * Mockup 38 — Outstanding balance (US19 scenarios 4-6): the failed additional
 * charge, the selected payment method, the notice that new reservations are
 * blocked and the simulated retry (1.5 s approval) with the chosen token.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutstandingBalanceScreen(
    onBack: () -> Unit,
    onPaid: () -> Unit,
    onNotice: (String) -> Unit,
) {
    val view = PaymentRepository.outstanding_charge()
    val tokens = remember { PaymentRepository.tokens() }
    var tokenId by remember { mutableStateOf(PaymentRepository.default_token()?.token_id ?: "") }
    var paying by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val paidMessage = stringResource(R.string.balance_paid_notice)
    val emptyMessage = stringResource(R.string.balance_empty)

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.balance_title)) },
            windowInsets = WindowInsets(0), navigationIcon = {
                IconButton(onClick = onBack) { FigmaIcon(R.drawable.ic_back, stringResource(R.string.zone_back)) }
            })

        if (view == null) {
            Text(stringResource(R.string.balance_empty), Modifier.padding(18.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }

        Column(
            Modifier.fillMaxSize().padding(horizontal = 18.dp).padding(top = 6.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Failed additional charge card.
            Surface(color = BalanceCardBackground, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.balance_charge_failed), Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium)
                        Surface(color = BalancePendingContainer, shape = RoundedCornerShape(100.dp)) {
                            Row(Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(Modifier.size(6.dp).background(SpotGoError, CircleShape))
                                Text(stringResource(R.string.balance_pending), style = MaterialTheme.typography.bodySmall,
                                    color = SpotGoError)
                            }
                        }
                    }
                    Text("S/ ${view.balance.amount}", style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 30.sp, fontWeight = FontWeight.Bold), color = SpotGoError)
                    Text(stringResource(R.string.balance_charge_line, view.label),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            DriverSectionTitle(stringResource(R.string.balance_payment_method))
            DriverPanel {
                val token = tokens.firstOrNull { it.token_id == tokenId }
                ListItem(
                    headlineContent = { Text(token?.display_label ?: stringResource(R.string.payments_card),
                        style = MaterialTheme.typography.titleMedium) },
                    supportingContent = { Text(token?.display_details ?: stringResource(R.string.payments_card_details),
                        style = MaterialTheme.typography.bodyMedium) },
                    trailingContent = {
                        TextButton(onClick = { picking = true }) {
                            Text(stringResource(R.string.balance_change), style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                )
            }

            // Blocking notice.
            Surface(color = BalanceNoticeBackground, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.balance_notice_title), style = MaterialTheme.typography.titleSmall,
                        color = SpotGoAccent)
                    Text(stringResource(R.string.balance_notice_message), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            DriverPrimaryButton(
                label = stringResource(if (paying) R.string.balance_processing else R.string.balance_retry),
                onClick = {
                    paying = true
                    scope.launch {
                        delay(1500) // simulated digital payment approval (US19)
                        val payment = PaymentRepository.pay_outstanding_balance(tokenId)
                        paying = false
                        if (payment != null) {
                            onNotice(paidMessage)
                            onPaid()
                        } else {
                            onNotice(emptyMessage)
                        }
                    }
                },
            )
            DriverOutlineButton(
                label = stringResource(R.string.balance_choose_other),
                onClick = { picking = true },
            )
        }
    }

    if (picking) {
        ModalBottomSheet(
            onDismissRequest = { picking = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.balance_select_token), style = MaterialTheme.typography.titleMedium)
                tokens.forEach { token ->
                    DriverPanel(outlined = token.token_id != tokenId) {
                        ListItem(
                            headlineContent = { Text(token.display_label, style = MaterialTheme.typography.titleMedium) },
                            supportingContent = { Text(token.display_details, style = MaterialTheme.typography.bodyMedium) },
                            trailingContent = {
                                if (token.token_id == tokenId) {
                                    Icon(painterResource(R.drawable.status_available),
                                        contentDescription = null, Modifier.size(18.dp), tint = SpotGoAccent)
                                }
                            },
                            modifier = Modifier.clickable {
                                tokenId = token.token_id
                                picking = false
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        )
                    }
                }
            }
        }
    }
}

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun OutstandingBalancePreview() {
    SpotGoTheme { OutstandingBalanceScreen({}, {}, {}) }
}
