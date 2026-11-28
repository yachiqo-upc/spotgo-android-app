package com.yachiqo.spotgo.presentation.driver

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Display formatting of the transient card form (mockup 36).
 *
 * The state of both fields stores **only digits** — see [CardFormInput.number]
 * and [CardFormInput.expiration]. The visible grouping ("4111 1111 …") and the
 * expiry slash ("12/30") are produced on screen by a [VisualTransformation],
 * never written back into the state: rewriting the state with the formatted
 * text is what makes the cursor jump while typing.
 */
internal object CardFormFormat {

    /** Maximum digits of a regular brand (Visa, Mastercard, Discover). */
    const val MAX_DIGITS = 16

    /** Amex numbers hold 15 digits and are grouped 4-6-5. */
    const val AMEX_MAX_DIGITS = 15

    /** Maximum digits of the expiry field ("MMYY"). */
    const val MAX_EXPIRATION_DIGITS = 4

    /**
     * Digits only, capped for the brand: 16 normally, 15 when the number
     * starts with 34/37 (Amex). Used by the field's `onValueChange`.
     */
    fun capNumber(input: String): String {
        val digits = input.filter(Char::isDigit)
        return if (isAmex(digits)) digits.take(AMEX_MAX_DIGITS) else digits.take(MAX_DIGITS)
    }

    /** Keep the selection anchored to digit positions when pasted input is capped. */
    fun capNumber(input: TextFieldValue): TextFieldValue {
        val start = input.text.take(input.selection.start.coerceIn(0, input.text.length))
            .count(Char::isDigit)
        val end = input.text.take(input.selection.end.coerceIn(0, input.text.length))
            .count(Char::isDigit)
        val digits = capNumber(input.text)
        return input.copy(
            text = digits,
            selection = TextRange(start.coerceAtMost(digits.length), end.coerceAtMost(digits.length)),
            composition = null,
        )
    }

    /** Digits only, capped to "MMYY". */
    fun capExpiration(input: String): String =
        input.filter(Char::isDigit).take(MAX_EXPIRATION_DIGITS)

    /** Keep the selection anchored to digit positions when pasted input is capped. */
    fun capExpiration(input: TextFieldValue): TextFieldValue {
        val start = input.text.take(input.selection.start.coerceIn(0, input.text.length))
            .count(Char::isDigit)
        val end = input.text.take(input.selection.end.coerceIn(0, input.text.length))
            .count(Char::isDigit)
        val digits = capExpiration(input.text)
        return input.copy(
            text = digits,
            selection = TextRange(start.coerceAtMost(digits.length), end.coerceAtMost(digits.length)),
            composition = null,
        )
    }

    /** Amex numbers start with 34 or 37. */
    fun isAmex(digits: String): Boolean =
        digits.startsWith("34") || digits.startsWith("37")

    /** Raw digits → "4111 1111 1111 1111", or 4-6-5 grouping for Amex. */
    fun formatNumber(digits: String): String {
        val clean = digits.filter(Char::isDigit)
        val groups = if (isAmex(clean)) intArrayOf(4, 6, 5) else intArrayOf(4, 4, 4, 4)
        return group(clean, groups)
    }

    /** Raw "MMYY" → "MM/YY"; one or two digits are shown as typed. */
    fun formatExpiration(digits: String): String {
        val clean = digits.filter(Char::isDigit)
        return if (clean.length <= 2) clean else clean.substring(0, 2) + "/" + clean.substring(2)
    }

    private fun group(digits: String, groups: IntArray): String {
        val builder = StringBuilder()
        var index = 0
        for (size in groups) {
            if (index >= digits.length) break
            if (builder.isNotEmpty()) builder.append(' ')
            builder.append(digits, index, minOf(index + size, digits.length))
            index += size
        }
        // Defensive: only reachable if the state ever exceeds the brand cap.
        if (index < digits.length) {
            if (builder.isNotEmpty()) builder.append(' ')
            builder.append(digits, index, digits.length)
        }
        return builder.toString()
    }
}

/**
 * Cursor/selection mapping between the raw digits (field state) and the
 * formatted text on screen.
 *
 * Both directions walk the formatted string instead of doing arithmetic on
 * separator indexes, so they stay exact for every length:
 *
 * - [originalToTransformed] stops right after the requested digit, so a caret
 *   never lands past the end or skips a digit when typing or deleting in the
 *   middle of the field.
 * - [transformedToOriginal] counts only real digits, so a caret parked next
 *   to a space or the expiry slash maps back to the digit it belongs to.
 */
internal class CardFormOffsetMapping(private val formatted: String) : OffsetMapping {

    /** Length of the raw state: the formatted string without separators. */
    private val originalLength = formatted.count(Char::isDigit)

    override fun originalToTransformed(offset: Int): Int {
        val target = offset.coerceIn(0, originalLength)
        var index = 0
        var digits = 0
        while (index < formatted.length && digits < target) {
            if (formatted[index].isDigit()) digits++
            index++
        }
        return index
    }

    override fun transformedToOriginal(offset: Int): Int {
        val end = offset.coerceIn(0, formatted.length)
        var digits = 0
        for (index in 0 until end) {
            if (formatted[index].isDigit()) digits++
        }
        return digits
    }
}

/** Shows the raw card number grouped (4-4-4-4, or 4-6-5 for Amex). */
internal object CardNumberVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val formatted = CardFormFormat.formatNumber(text.text)
        return TransformedText(AnnotatedString(formatted), CardFormOffsetMapping(formatted))
    }
}

/** Shows the raw expiry digits as "MM/YY". */
internal object CardExpirationVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val formatted = CardFormFormat.formatExpiration(text.text)
        return TransformedText(AnnotatedString(formatted), CardFormOffsetMapping(formatted))
    }
}
