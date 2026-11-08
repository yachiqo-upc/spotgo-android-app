package com.yachiqo.spotgo.presentation.driver

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yachiqo.spotgo.R
import com.yachiqo.spotgo.data.model.BillingDocumentType
import com.yachiqo.spotgo.data.repository.PaymentRepository
import com.yachiqo.spotgo.presentation.common.FigmaIcon
import com.yachiqo.spotgo.presentation.driver.components.*
import com.yachiqo.spotgo.ui.theme.SpotGoAccent
import com.yachiqo.spotgo.ui.theme.SpotGoOnAccent
import com.yachiqo.spotgo.ui.theme.SpotGoTheme

/** Document filters of mockup 14 (All · Receipts · Invoices). */
private enum class DocumentFilter { ALL, RECEIPTS, INVOICES }

/**
 * Mockup 14 — Receipts &amp; invoices (US21 scenarios 1-3): the payment
 * documents of the account with the All / Receipts / Invoices filter; each
 * row opens the detail of mockup 40.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ReceiptsScreen(
    onBack: () -> Unit,
    onOpenDocument: (String) -> Unit,
) {
    var filter by remember { mutableStateOf(DocumentFilter.ALL) }
    val documents = remember(filter) {
        PaymentRepository.documents().filter {
            when (filter) {
                DocumentFilter.ALL -> true
                DocumentFilter.RECEIPTS -> it.document_type == BillingDocumentType.ELECTRONIC_RECEIPT
                DocumentFilter.INVOICES -> it.document_type == BillingDocumentType.ELECTRONIC_INVOICE
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.receipts_title)) },
            windowInsets = WindowInsets(0), navigationIcon = {
                IconButton(onClick = onBack) { FigmaIcon(R.drawable.ic_back, stringResource(R.string.zone_back)) }
            })

        Column(
            Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState())
                .padding(horizontal = 18.dp).padding(top = 6.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(stringResource(R.string.receipts_heading), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.receipts_subtitle), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            DocumentFilterRow(
                selected = filter,
                onSelect = { filter = it },
            )

            if (documents.isEmpty()) {
                Text(stringResource(R.string.receipts_empty), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                DriverPanel {
                    documents.forEach { document ->
                        val payment = PaymentRepository.payment_by_id(document.payment_id)
                        val isInvoice = document.document_type == BillingDocumentType.ELECTRONIC_INVOICE
                        val subtitle = if (isInvoice) {
                            stringResource(R.string.receipts_row_invoice,
                                PaymentRepository.shortDateLabel(document.issued_at), document.fiscal_reference)
                        } else {
                            stringResource(R.string.receipts_row_receipt,
                                PaymentRepository.shortDateLabel(document.issued_at), document.fiscal_reference)
                        }
                        ListItem(
                            headlineContent = { Text(payment?.summary ?: document.fiscal_reference,
                                style = MaterialTheme.typography.titleMedium) },
                            supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            trailingContent = {
                                Text("S/ ${payment?.amount ?: "0.00"}", style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            },
                            modifier = Modifier.heightIn(min = 72.dp)
                                .clickable { onOpenDocument(document.document_id) },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        )
                    }
                }
            }

            // Info card of the mockup.
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.receipts_need_invoice), style = MaterialTheme.typography.titleSmall,
                        color = SpotGoAccent)
                    Text(stringResource(R.string.receipts_need_invoice_message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Three-way filter that reproduces the segmented control of mockup 14. */
@Composable
private fun DocumentFilterRow(selected: DocumentFilter, onSelect: (DocumentFilter) -> Unit) {
    val options = listOf(
        DocumentFilter.ALL to stringResource(R.string.receipts_filter_all),
        DocumentFilter.RECEIPTS to stringResource(R.string.receipts_filter_receipts),
        DocumentFilter.INVOICES to stringResource(R.string.receipts_filter_invoices),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        options.forEachIndexed { index, (key, label) ->
            val active = key == selected
            val shape = when (index) {
                0 -> RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp)
                options.lastIndex -> RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp)
                else -> RoundedCornerShape(0.dp)
            }
            Row(
                Modifier.weight(1f).height(44.dp)
                    .background(if (active) SpotGoAccent else MaterialTheme.colorScheme.surfaceContainer, shape)
                    .clickable { onSelect(key) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (active) {
                    Icon(painterResource(R.drawable.status_available), contentDescription = null,
                        Modifier.size(14.dp), tint = SpotGoOnAccent)
                    Spacer(Modifier.width(6.dp))
                }
                Text(label, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                    color = if (active) SpotGoOnAccent else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, softWrap = false, fontSize = 14.sp)
            }
        }
    }
}

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun ReceiptsPreview() {
    SpotGoTheme { ReceiptsScreen({}, {}) }
}
