package com.yachiqo.spotgo.presentation.driver

import android.content.ContentValues
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.provider.MediaStore
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yachiqo.spotgo.BuildConfig
import com.yachiqo.spotgo.R
import com.yachiqo.spotgo.data.model.Reservation
import com.yachiqo.spotgo.data.repository.RemoteBillingDocumentDetails
import com.yachiqo.spotgo.data.repository.RemoteBillingRepository
import com.yachiqo.spotgo.data.repository.RemoteParkingRepository
import com.yachiqo.spotgo.data.repository.RemoteReservationRepository
import com.yachiqo.spotgo.data.repository.ReservationListResult
import com.yachiqo.spotgo.data.repository.RemoteReservationResult
import com.yachiqo.spotgo.data.repository.RemoteSubscription
import com.yachiqo.spotgo.data.repository.isoDateLabel
import com.yachiqo.spotgo.data.repository.isoRange
import com.yachiqo.spotgo.data.repository.money
import com.yachiqo.spotgo.presentation.common.FigmaIcon
import com.yachiqo.spotgo.presentation.documentStatusResource
import com.yachiqo.spotgo.presentation.paymentStatusResource
import com.yachiqo.spotgo.presentation.driver.components.*
import com.yachiqo.spotgo.ui.theme.SpotGoTheme
import com.yachiqo.spotgo.ui.theme.SpotGoWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ReceiptLine(val label: String, val value: String, val tag: String)
private data class LoadedReceipt(
    val details: RemoteBillingDocumentDetails,
    val reservation: Reservation?,
    val subscription: RemoteSubscription?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptDetailScreen(
    documentId: String,
    onBack: () -> Unit,
    onNotice: (String) -> Unit,
) {
    // The route parameter is a payment id: the backend retrieves its document by paymentId.
    var receipt by remember(documentId) { mutableStateOf<LoadedReceipt?>(null) }
    var loading by remember(documentId) { mutableStateOf(true) }
    var failed by remember(documentId) { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(documentId) {
        loading = true
        val loaded = withContext(Dispatchers.IO) {
            val details = RemoteBillingRepository.loadDocumentDetails(documentId)
            if (details == null) return@withContext null
            val reservations = RemoteReservationRepository.loadReservations()
            RemoteParkingRepository.loadFacilities()
            val charge = details.charges.firstOrNull { it.paymentId == documentId }
            val matchingReservation = (reservations as? ReservationListResult.Success)?.reservations
                ?.firstOrNull { it.payment_ref == documentId }
            val reservation = matchingReservation ?: charge?.reservationId?.let { id ->
                (RemoteReservationRepository.loadReservation(id) as? RemoteReservationResult.Success)?.reservation
            }
            val subscription = RemoteBillingRepository.loadSubscriptions()?.firstOrNull { it.paymentId == documentId }
            LoadedReceipt(details, reservation, subscription)
        }
        receipt = loaded
        failed = loaded == null
        loading = false
    }

    val detail = receipt?.details
    val reservation = receipt?.reservation
    val subscription = receipt?.subscription
    val zone = reservation?.let { current ->
        RemoteParkingRepository.facilityForZone(current.zone_id)?.zones
            ?.firstOrNull { zone -> zone.zone_id == current.zone_id }
    }
    val spot = reservation?.let { RemoteParkingRepository.spotById(it.spot_id) }
    val charge = detail?.charges?.firstOrNull { it.paymentId == detail.payment.paymentId }
    val linkedBalance = charge?.let { charge -> detail?.balances?.firstOrNull { it.chargeId == charge.chargeId && it.status == "OPEN" } }
    val completedRefund = detail?.refunds.orEmpty().filter { it.status == "COMPLETED" }
        .sumOf { it.amount.toDoubleOrNull() ?: 0.0 }
    val paymentLabel = stringResource(R.string.document_payment)
    val zoneLabel = stringResource(R.string.document_parking_zone)
    val reservationLabel = stringResource(R.string.document_reservation)
    val spotLabel = stringResource(R.string.document_spot)
    val subscriptionLabel = stringResource(R.string.document_subscription)
    val chargeLabel = stringResource(R.string.document_charge)
    val operationLabel = stringResource(R.string.document_operation)
    val paymentMethodLabel = stringResource(R.string.document_payment_method)
    val noPaymentMethodLabel = stringResource(R.string.document_no_payment_method)
    val totalPaidLabel = stringResource(R.string.document_total_paid)
    val localizedPaymentStatus = detail?.let { context.getString(paymentStatusResource(it.payment.status)) }.orEmpty()
    val rows = if (detail == null) emptyList() else buildList {
        add(ReceiptLine(paymentLabel, detail.payment.paymentId, localizedPaymentStatus))
        when {
            reservation != null -> {
                add(ReceiptLine(zoneLabel, zone?.name ?: reservation.zone_id,
                    zone?.location.orEmpty()))
                add(ReceiptLine(reservationLabel, reservation.reservation_id,
                    isoRange(reservation.start_at, reservation.end_at)))
                spot?.let { add(ReceiptLine(spotLabel, it.code, "")) }
            }
            subscription != null -> add(ReceiptLine(subscriptionLabel,
                subscription.planCode, context.getString(paymentStatusResource(subscription.status))))
            charge != null -> add(ReceiptLine(chargeLabel, charge.reason, charge.chargeId))
            else -> add(ReceiptLine(operationLabel, detail.payment.operationType,
                detail.payment.resultReference.orEmpty()))
        }
        add(ReceiptLine(paymentMethodLabel, detail.paymentMethod?.label ?: noPaymentMethodLabel, ""))
        add(ReceiptLine(totalPaidLabel,
            "${detail.payment.currency} ${detail.payment.amount}", localizedPaymentStatus))
    }
    val documentTypeLabel = if (detail?.document?.documentType == "ELECTRONIC_INVOICE")
        stringResource(R.string.receipts_filter_invoices) else stringResource(R.string.receipts_filter_receipts)
    val title = detail?.let { "$documentTypeLabel ${it.document.reference}" }.orEmpty()
    val subtitle = detail?.let { "${isoDateLabel(it.document.issuedAt)} · ${context.getString(documentStatusResource(it.document.status))}" }.orEmpty()
    val noDocumentEmail = stringResource(R.string.document_email_unavailable)
    val emailed = stringResource(R.string.document_reservation_emailed)
    val emailFailed = stringResource(R.string.document_email_failed)
    val downloaded = stringResource(R.string.document_downloaded)
    val downloadFailed = stringResource(R.string.document_download_failed)

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.document_title)) },
            windowInsets = WindowInsets(0), navigationIcon = {
                IconButton(onClick = onBack) { FigmaIcon(R.drawable.ic_back, stringResource(R.string.zone_back)) }
            })

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            failed || detail == null -> Text(stringResource(R.string.document_load_error), Modifier.padding(18.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            else -> Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp).padding(top = 6.dp, bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold, fontSize = 22.sp))
                        StatusBadge(context.getString(documentStatusResource(detail.document.status)), positive = detail.document.status == "ISSUED")
                    }
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                DriverPanel {
                    rows.forEachIndexed { index, row ->
                        if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 14.dp),
                            color = MaterialTheme.colorScheme.outline)
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(row.label, style = MaterialTheme.typography.titleMedium)
                                Text(row.value, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(row.tag, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                DriverSectionTitle(stringResource(R.string.document_adjustments))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AdjustmentCard(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.document_refund),
                        value = if (completedRefund > 0.0) "−${money(completedRefund)}" else money(0.0),
                        positive = true,
                    )
                    AdjustmentCard(
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.document_outstanding),
                        value = linkedBalance?.amount?.toDoubleOrNull()?.let(::money) ?: money(0.0),
                        positive = false,
                    )
                }

                Text(stringResource(R.string.document_not_fiscal), style = MaterialTheme.typography.bodySmall,
                    color = SpotGoWarning)
                DriverPrimaryButton(
                    label = stringResource(R.string.document_download),
                    enabled = !sending,
                    onClick = {
                        val ok = saveDocumentPdf(context, title, subtitle, rows,
                            detail.payment.amount, detail.payment.currency)
                        onNotice(if (ok) downloaded else downloadFailed)
                    },
                )
                if (BuildConfig.RECEIPT_EMAIL_ENABLED) {
                    DriverOutlineButton(
                        label = if (sending) stringResource(R.string.document_email_sending)
                            else stringResource(if (reservation != null) R.string.document_send_reservation_email
                                else R.string.document_send_email),
                        enabled = !sending && reservation != null,
                        onClick = {
                            val reservationId = reservation?.reservation_id
                            if (reservationId == null) onNotice(noDocumentEmail)
                            else if (!sending) scope.launch {
                                sending = true
                                val sent = withContext(Dispatchers.IO) {
                                    RemoteReservationRepository.emailReceipt(reservationId)
                                }
                                sending = false
                                onNotice(if (sent) emailed else emailFailed)
                            }
                        },
                    )
                    if (reservation == null) {
                        Text(noDocumentEmail, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (sending) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun AdjustmentCard(modifier: Modifier, label: String, value: String, positive: Boolean) {
    val color = if (positive) DriverAvailable else SpotGoWarning
    Surface(
        modifier, color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = color)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun saveDocumentPdf(
    context: android.content.Context,
    title: String,
    subtitle: String,
    rows: List<ReceiptLine>,
    amount: String,
    currency: String,
): Boolean {
    val document = PdfDocument()
    return try {
        val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val canvas = page.canvas
        val heading = Paint().apply {
            textSize = 20f
            color = AndroidColor.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val body = Paint().apply { textSize = 12f; color = AndroidColor.DKGRAY }
        val value = Paint().apply {
            textSize = 14f
            color = AndroidColor.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        var y = 70f
        canvas.drawText(context.getString(R.string.pdf_informational_document), 50f, y, heading)
        y += 34f
        canvas.drawText(title.take(70), 50f, y, heading)
        y += 24f
        canvas.drawText(subtitle.take(80), 50f, y, body)
        y += 36f
        rows.forEach { row ->
            canvas.drawText(row.label.take(50), 50f, y, value)
            canvas.drawText(row.tag.take(30), 430f, y, body)
            y += 20f
            canvas.drawText(row.value.take(80), 50f, y, body)
            y += 26f
        }
        canvas.drawText(context.getString(R.string.pdf_total, currency, amount), 50f, y + 10f, value)
        y += 34f
        canvas.drawText(context.getString(R.string.pdf_informational_disclaimer), 50f, y, body)
        document.finishPage(page)

        val fileName = title.replace(Regex("[^A-Za-z0-9_-]"), "_").take(48).ifBlank { "spotgo-document" } + ".pdf"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/SpotGo")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return false
        resolver.openOutputStream(uri)?.use { document.writeTo(it) } ?: return false
        values.clear()
        values.put(MediaStore.Downloads.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        true
    } catch (_: Exception) {
        false
    } finally {
        document.close()
    }
}

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun ReceiptDetailPreview() {
    SpotGoTheme { ReceiptDetailScreen("", {}, {}) }
}
