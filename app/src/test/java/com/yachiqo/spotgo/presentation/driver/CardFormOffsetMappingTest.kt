package com.yachiqo.spotgo.presentation.driver

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cursor/selection mapping of the card form fields: typing, deleting or
 * pasting in the middle of the number must never move the caret to a wrong
 * position or outside the field.
 */
class CardFormOffsetMappingTest {

    /* ------------------------------------------------------------------ */
    /* Round trip: original → transformed → original (and back)            */
    /* ------------------------------------------------------------------ */

    @Test
    fun regularCardNumberOffsetsRoundTripForEveryLengthFromZeroToSixteen() {
        val sample = "4111111111111111"
        for (length in 0..16) {
            val digits = sample.take(length)
            assertRoundTrip(digits, CardFormFormat.formatNumber(digits))
        }
    }

    @Test
    fun amexCardNumberOffsetsRoundTripForEveryLengthFromZeroToFifteen() {
        val sample = "378282246310005"
        for (length in 0..15) {
            val digits = sample.take(length)
            assertRoundTrip(digits, CardFormFormat.formatNumber(digits))
        }
    }

    @Test
    fun expiryOffsetsRoundTripForEveryLengthFromZeroToFour() {
        val sample = "1230"
        for (length in 0..4) {
            val digits = sample.take(length)
            assertRoundTrip(digits, CardFormFormat.formatExpiration(digits))
        }
    }

    /**
     * Walks every possible caret position of [formatted] (which is built from
     * [digits]) in both directions and asserts the invariants the text field
     * relies on.
     */
    private fun assertRoundTrip(digits: String, formatted: String) {
        val mapping = CardFormOffsetMapping(formatted)
        val originalLength = digits.length
        val separatorCount = formatted.count { !it.isDigit() }
        val where = "digits=\"$digits\" formatted=\"$formatted\""

        // Ida: every raw position maps inside the field and comes back exactly.
        var previousTransformed = -1
        for (original in 0..originalLength) {
            val transformed = mapping.originalToTransformed(original)
            assertTrue("$where: original $original → $transformed out of 0..${formatted.length}",
                transformed in 0..formatted.length)
            assertTrue("$where: mapping must not move backwards", transformed >= previousTransformed)
            previousTransformed = transformed
            assertEquals("$where: round trip of original $original",
                original, mapping.transformedToOriginal(transformed))
        }

        // Vuelta: every caret position maps to a digit position and back to a
        // spot right next to it — never onto a digit (which would skip one)
        // and never past the caret.
        var previousOriginal = -1
        for (transformed in 0..formatted.length) {
            val original = mapping.transformedToOriginal(transformed)
            assertTrue("$where: transformed $transformed → $original out of 0..$originalLength",
                original in 0..originalLength)
            assertTrue("$where: mapping must not move backwards", original >= previousOriginal)
            previousOriginal = original

            val back = mapping.originalToTransformed(original)
            assertTrue("$where: caret must not jump forward ($transformed → $back)", back <= transformed)
            assertTrue("$where: caret drift $transformed → $back exceeds the separators",
                transformed - back <= separatorCount)
            assertEquals("$where: re-mapping must be stable", original, mapping.transformedToOriginal(back))

            // Exact when the caret sits directly after a digit (or at the start).
            if (transformed == 0 || formatted[transformed - 1].isDigit()) {
                assertEquals("$where: caret after a digit must stay put", transformed, back)
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* Formatting: state keeps digits only, screen shows the groups        */
    /* ------------------------------------------------------------------ */

    @Test
    fun regularNumbersAreGroupedInFoursAndAmexInFourSixFive() {
        assertEquals("4111 1111 1111 1111", CardFormFormat.formatNumber("4111111111111111"))
        assertEquals("3782 822463 10005", CardFormFormat.formatNumber("378282246310005"))
        assertEquals("4111 11", CardFormFormat.formatNumber("411111"))
        assertEquals("", CardFormFormat.formatNumber(""))
    }

    @Test
    fun expiryIsShownAsMonthSlashYear() {
        assertEquals("", CardFormFormat.formatExpiration(""))
        assertEquals("1", CardFormFormat.formatExpiration("1"))
        assertEquals("12", CardFormFormat.formatExpiration("12"))
        assertEquals("12/3", CardFormFormat.formatExpiration("123"))
        assertEquals("12/30", CardFormFormat.formatExpiration("1230"))
    }

    @Test
    fun theStateIsCappedToDigitsAndToTheBrandLength() {
        assertEquals("4111111111111111", CardFormFormat.capNumber("4111 1111 1111 1111"))
        assertEquals("4111111111111111", CardFormFormat.capNumber("41111111111111119999"))
        // Amex (34/37) tops out at 15 digits.
        assertEquals("378282246310005", CardFormFormat.capNumber("3782822463100056"))
        assertEquals("1230", CardFormFormat.capExpiration("12/30"))
        assertEquals("1230", CardFormFormat.capExpiration("123055"))
    }

    @Test
    fun cappingTheNumberKeepsTheCaretAtItsDigitPosition() {
        val input = TextFieldValue("41111111911111111", selection = TextRange(9))

        val capped = CardFormFormat.capNumber(input)

        assertEquals("4111111191111111", capped.text)
        assertEquals(TextRange(9), capped.selection)
    }

    @Test
    fun cappingTheExpiryKeepsTheCaretAtItsDigitPosition() {
        val input = TextFieldValue("123045", selection = TextRange(4))

        val capped = CardFormFormat.capExpiration(input)

        assertEquals("1230", capped.text)
        assertEquals(TextRange(4), capped.selection)
    }

    /* ------------------------------------------------------------------ */
    /* The transformations the fields actually receive                     */
    /* ------------------------------------------------------------------ */

    @Test
    fun theNumberTransformationGroupsTheDisplayAndKeepsTheStateDigitsOnly() {
        val transformed = CardNumberVisualTransformation.filter(AnnotatedString("4111111111111111"))
        assertEquals("4111 1111 1111 1111", transformed.text.text)
        assertEquals(4, transformed.offsetMapping.originalToTransformed(4))
        assertEquals(6, transformed.offsetMapping.originalToTransformed(5))
        assertEquals(16, transformed.offsetMapping.transformedToOriginal(19))
    }

    @Test
    fun theAmexTransformationUsesTheFourSixFiveGroups() {
        val transformed = CardNumberVisualTransformation.filter(AnnotatedString("378282246310005"))
        assertEquals("3782 822463 10005", transformed.text.text)
        assertEquals(11, transformed.offsetMapping.originalToTransformed(10))
        assertEquals(15, transformed.offsetMapping.transformedToOriginal(17))
    }

    @Test
    fun theExpirationTransformationAddsTheSlashWithoutTouchingTheState() {
        val transformed = CardExpirationVisualTransformation.filter(AnnotatedString("1230"))
        assertEquals("12/30", transformed.text.text)
        assertEquals(2, transformed.offsetMapping.originalToTransformed(2))
        assertEquals(4, transformed.offsetMapping.originalToTransformed(3))
        assertEquals(3, transformed.offsetMapping.transformedToOriginal(4))
        assertEquals(4, transformed.offsetMapping.transformedToOriginal(5))
    }
}
