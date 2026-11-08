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
import com.yachiqo.spotgo.R
import com.yachiqo.spotgo.data.model.BillingDocumentView
import com.yachiqo.spotgo.data.repository.AuthRepository
import com.yachiqo.spotgo.data.repository.EmailSendResult
import com.yachiqo.spotgo.data.repository.PaymentRepository
import com.yachiqo.spotgo.presentation.common.FigmaIcon
import com.yachiqo.spotgo.presentation.driver.components.*
import com.yachiqo.spotgo.ui.theme.SpotGoTheme
import com.yachiqo.spotgo.ui.theme.SpotGoWarning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Mockup 40 — Receipt / invoice detail (US21): header with the paid status,
 * the Parking zone / Reservation / Payment method / Subtotal / Total paid
 * card, the Refund and Outstanding adjustments, the simulated PDF download
 * and the e-mail delivery to the registered account (US21 scenario 2,
 * through the backend — see PaymentRepository.send_document_by_email).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ReceiptDetailScreen(
    documentId: String,
    onBack: () -> Unit,
    onNotice: (String) -> Unit,
) {
    val view = PaymentRepository.document_view(documentId)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sending by remember { mutableStateOf(false) }
    // Every registered Driver has an e-mail (credentials or Google Identity).
    val recipient = AuthRepository.current_profile()?.email ?: ""
    val downloadedMessage = stringResource(R.string.document_downloaded)
    val downloadFailed = stringResource(R.string.document_download_failed)
    val emailedMessage = stringResource(R.string.document_emailed, recipient)
    val emailFailed = stringResource(R.string.document_email_failed)
    val emailSending = stringResource(R.string.document_email_sending)
    val emailBackendMissing = stringResource(R.string.document_email_backend_missing)

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text(stringResource(R.string.document_title)) },
            windowInsets = WindowInsets(0), navigationIcon = {
                IconButton(onClick = onBack) { FigmaIcon(R.drawable.ic_back, stringResource(R.string.zone_back)) }
            })

        if (view == null) {
            Text(stringResource(R.string.receipts_empty), Modifier.padding(18.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp).padding(top = 6.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header: "Receipt SG-4821" + paid badge (mockup 40).
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(view.title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold, fontSize = 22.sp))
                    StatusBadge(view.status_label, positive = view.status_label == "Paid")
                }
                Text(view.subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            DriverPanel {
                view.rows.forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider(
                        Modifier.padding(horizontal = 14.dp),
                        color = MaterialTheme.colorScheme.outline)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
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

            // Adjustments: Refund (green) and Outstanding (orange).
            DriverSectionTitle(stringResource(R.string.document_adjustments))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                view.adjustments.forEach { adjustment ->
                    val color = if (adjustment.positive) DriverAvailable else SpotGoWarning
                    Surface(
                        Modifier.weight(1f), color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(adjustment.value, style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold), color = color)
                            Text(adjustment.label, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            DriverPrimaryButton(
                label = stringResource(R.string.document_download),
                onClick = {
                    val ok = saveReceiptPdf(context, view)
                    PaymentRepository.mark_downloaded(view.document.document_id)
                    onNotice(if (ok) downloadedMessage else downloadFailed)
                },
            )
            DriverOutlineButton(
                label = if (sending) emailSending else stringResource(R.string.document_send_email),
                onClick = {
                    if (!sending) {
                        sending = true
                        scope.launch {
                            val subject = view.title
                            val body = receiptBody(view)
                            val result = withContext(Dispatchers.IO) {
                                PaymentRepository.send_document_by_email(
                                    view.document.document_id, subject, body,
                                )
                            }
                            sending = false
                            onNotice(
                                when (result) {
                                    is EmailSendResult.Sent -> emailedMessage
                                    EmailSendResult.BackendMissing -> emailBackendMissing
                                    is EmailSendResult.Failed -> emailFailed
                                },
                            )
                        }
                    }
                },
            )
        }
    }
}

/**
 * US21 scenario 1 — renders a one-page PDF with the summary of the document
 * and saves it into Downloads/SpotGo (MediaStore, no permission needed on
 * API 29+).
 */
private fun saveReceiptPdf(context: android.content.Context, view: BillingDocumentView): Boolean {
    val document = PdfDocument()
    return try {
        val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val canvas = page.canvas
        val title = Paint().apply {
            textSize = 20f; color = AndroidColor.BLACK; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val body = Paint().apply { textSize = 12f; color = AndroidColor.DKGRAY }
        val value = Paint().apply {
            textSize = 14f; color = AndroidColor.BLACK; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        var y = 70f
        canvas.drawText("SpotGo", 50f, y, title); y += 34f
        canvas.drawText(view.title, 50f, y, title); y += 24f
        canvas.drawText(view.subtitle, 50f, y, body); y += 36f
        view.rows.forEach { row ->
            canvas.drawText(row.label, 50f, y, value)
            canvas.drawText(row.tag, 500f, y, body)
            y += 20f
            canvas.drawText(row.value, 50f, y, body)
            y += 26f
        }
        y += 10f
        view.adjustments.forEach { adjustment ->
            canvas.drawText("${adjustment.label}: ${adjustment.value}", 50f, y, body)
            y += 22f
        }
        canvas.drawText("Total paid: ${view.payment.amount} ${view.payment.currency}", 50f, y + 10f, value)
        document.finishPage(page)

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "${view.document.fiscal_reference}.pdf")
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
    } catch (error: Exception) {
        false
    } finally {
        document.close()
    }
}

/**
 * US21 scenario 2 — body of the e-mail sent to the registered account: the
 * same summary the screen shows (title, subtitle, rows, adjustments, total).
 */
private fun receiptBody(view: BillingDocumentView): String = buildString {
    appendLine(view.title)
    appendLine(view.subtitle)
    appendLine()
    view.rows.forEach { appendLine("${it.label}: ${it.value} (${it.tag})") }
    view.adjustments.forEach { appendLine("${it.label}: ${it.value}") }
    appendLine("Total paid: ${view.payment.amount} ${view.payment.currency}")
}

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun ReceiptDetailPreview() {
    SpotGoTheme { ReceiptDetailScreen("", {}, {}) }
}
