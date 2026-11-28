package com.yachiqo.spotgo.data.repository

import java.time.YearMonth

/**
 * Errors of the card form of mockup 36 (add payment method).
 * Each one maps to a user-facing message in `strings_driver_payments.xml`.
 */
enum class CardFormError {
    HOLDER_REQUIRED,
    NUMBER_INVALID,
    EXPIRY_INVALID,
    CVV_INVALID,
    BRAND_UNSUPPORTED,
}

/**
 * Transient input of the "add payment method" form — test environment only.
 *
 * This object exists while the dialog is open and is discarded the moment the
 * token is saved or the dialog closes. It is deliberately *not* a data class:
 * the generated `toString()` would print the card number and the CVV, and this
 * value can end up in logs, crash reports or previews. Only [toString] decides
 * what is visible, and it never shows any card data.
 *
 * Nothing here is persisted, sent to a repository or stored in a model:
 * only [CardForm.providerTokenRef] leaves the form, as
 * "simulated-&lt;brand&gt;-&lt;last four&gt;".
 *
 * [number] and [expiration] hold **only digits** ("4111111111111111",
 * "1230"); the visible grouping and the expiry slash are rendered by a
 * VisualTransformation, never stored here.
 */
class CardFormInput(
    val holder: String,
    val number: String,
    val expiration: String,
    val cvv: String,
) {
    /** Copy with one field replaced — the form never mutates a saved object. */
    fun with(
        holder: String = this.holder,
        number: String = this.number,
        expiration: String = this.expiration,
        cvv: String = this.cvv,
    ): CardFormInput = CardFormInput(holder, number, expiration, cvv)

    /** Safe for logs and crash reports: no holder, number, expiry or CVV. */
    override fun toString(): String =
        "CardFormInput(holder=***, number=***, expiration=***, cvv=***)"
}

/**
 * Local validation and token reference of the simulated card form.
 *
 * There is no payment gateway SDK in this app: the backend only accepts
 * `providerTokenRef = "simulated-(visa|mastercard|amex|discover)-NNNN"` and
 * answers with the brand + last four of the token (expiry always 12/2099),
 * so the form validates here and sends nothing else.
 */
object CardForm {

    /** Brands the simulated backend accepts (lowercase). */
    val SUPPORTED_BRANDS = listOf("visa", "mastercard", "amex", "discover")

    /** Brand detected from the number, or null when it is not a test brand. */
    fun detectBrand(number: String): String? {
        val digits = number.filter(Char::isDigit)
        return when {
            AMEX.matches(digits) -> "amex"
            VISA.matches(digits) -> "visa"
            MASTERCARD.matches(digits) -> "mastercard"
            DISCOVER.matches(digits) -> "discover"
            else -> null
        }
    }

    /** null when the whole form is valid, otherwise the first error to show. */
    fun validate(input: CardFormInput, today: YearMonth = YearMonth.now()): CardFormError? {
        if (input.holder.isBlank()) return CardFormError.HOLDER_REQUIRED
        val digits = input.number.filter(Char::isDigit)
        if (!luhn(digits)) return CardFormError.NUMBER_INVALID
        val brand = detectBrand(digits) ?: return CardFormError.BRAND_UNSUPPORTED
        val expires = expirationYearMonth(input.expiration) ?: return CardFormError.EXPIRY_INVALID
        // A card is usable until the end of its expiration month.
        if (expires.isBefore(today)) return CardFormError.EXPIRY_INVALID
        val expectedCvv = if (brand == "amex") 4 else 3
        if (input.cvv.length != expectedCvv || input.cvv.any { !it.isDigit() }) {
            return CardFormError.CVV_INVALID
        }
        return null
    }

    /**
     * The only value the repository receives: "simulated-&lt;brand&gt;-&lt;last four&gt;".
     * null when the number does not belong to a supported test brand.
     */
    fun providerTokenRef(input: CardFormInput): String? {
        val digits = input.number.filter(Char::isDigit)
        val brand = detectBrand(digits) ?: return null
        val lastFour = digits.takeLast(4)
        if (lastFour.length != 4) return null
        return "simulated-$brand-$lastFour"
    }

    /** Expiration of [expiration] as "MM/YY" or "MM/YYYY", null when malformed. */
    fun expirationYearMonth(expiration: String): YearMonth? {
        val digits = expiration.filter(Char::isDigit)
        val month: Int
        val year: Int
        when (digits.length) {
            4 -> {
                month = digits.substring(0, 2).toIntOrNull() ?: return null
                year = 2000 + digits.substring(2, 4).toInt()
            }
            6 -> {
                month = digits.substring(0, 2).toIntOrNull() ?: return null
                year = digits.substring(2, 6).toIntOrNull() ?: return null
            }
            else -> return null
        }
        if (month !in 1..12) return null
        return runCatching { YearMonth.of(year, month) }.getOrNull()
    }

    /** Standard Luhn checksum of the account number. */
    private fun luhn(digits: String): Boolean {
        if (digits.length < 12) return false
        var sum = 0
        for (index in digits.indices.reversed()) {
            var value = digits[index] - '0'
            // Every second digit from the right is doubled.
            if ((digits.length - index) % 2 == 0) {
                value *= 2
                if (value > 9) value -= 9
            }
            sum += value
        }
        return sum % 10 == 0
    }

    private val AMEX = Regex("""^3[47][0-9]{13}$""")
    private val VISA = Regex("""^4[0-9]{12}([0-9]{3})?$""")
    private val MASTERCARD = Regex(
        """^(5[1-5][0-9]{14}|2(2[2-9]|[3-6][0-9]|7[01]|720)[0-9]{12})$""",
    )
    private val DISCOVER = Regex("""^6(011|5[0-9]{2}|4[4-9][0-9])[0-9]{12}$""")
}
