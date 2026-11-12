package com.yachiqo.spotgo.presentation

import com.yachiqo.spotgo.R

fun paymentStatusResource(status: String): Int = when (status.uppercase()) {
    "PENDING", "PENDING_PAYMENT" -> R.string.payment_status_pending
    "INITIATED", "PROCESSING" -> R.string.payment_status_processing
    "APPROVED", "PAID", "SUCCEEDED", "SUCCESS" -> R.string.payment_status_approved
    "REJECTED", "DECLINED" -> R.string.payment_status_rejected
    "FAILED" -> R.string.payment_status_failed
    "REFUNDED" -> R.string.payment_status_refunded
    "REFUND_SCHEDULED" -> R.string.payment_status_refund_scheduled
    "REFUND_REQUESTED" -> R.string.payment_status_refund_requested
    "REFUND_FAILED" -> R.string.payment_status_refund_failed
    "RECONCILIATION_REQUIRED" -> R.string.payment_status_reconciliation_required
    "ISSUED" -> R.string.payment_status_issued
    "CANCELLED", "CANCELED" -> R.string.payment_status_cancelled
    "COMPLETED" -> R.string.payment_status_completed
    "ACTIVE" -> R.string.details_status_active
    else -> R.string.payment_status_unknown
}

fun reservationStatusResource(status: String): Int = when (status.uppercase()) {
    "RESERVED", "CONFIRMED" -> R.string.details_status_confirmed
    "ACTIVE" -> R.string.details_status_active
    "PENDING_PAYMENT" -> R.string.details_status_pending_payment
    "CANCELLED", "PAYMENT_REJECTED" -> R.string.details_status_cancelled
    "NO_SHOW" -> R.string.reservation_status_no_show
    "OVERSTAYED" -> R.string.reservation_status_overstayed
    "COMPLETED" -> R.string.details_status_completed
    else -> R.string.reservation_status_unknown
}

fun documentStatusResource(status: String): Int = when (status.uppercase()) {
    "ISSUED" -> R.string.payment_status_issued
    "CANCELLED", "CANCELED", "VOIDED" -> R.string.payment_status_cancelled
    "PENDING" -> R.string.payment_status_pending
    else -> R.string.document_status_unknown
}
