package com.yachiqo.spotgo.data

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso.closeSoftKeyboard

/** Demo Driver of mockup 01 (seed account of SpotGoDatabase). */
private const val DEMO_EMAIL = "brigitte.carhuaz@example.com"
private const val DEMO_PASSWORD = "SpotGo2026"

/**
 * Signs in with the demo credentials and waits until the Driver workspace
 * opens. "Keep me signed in" is checked by default on the login screen, so
 * the session lasts longer than the test.
 */
internal fun ComposeTestRule.signIn() {
    onNode(hasSetTextAction() and hasText("Email")).performTextInput(DEMO_EMAIL)
    onNode(hasSetTextAction() and hasText("Password")).performTextInput(DEMO_PASSWORD)
    closeSoftKeyboard()
    // The section title and the button share the "Sign in" label: only the
    // button has a click action.
    onNode(hasText("Sign in") and hasClickAction()).performClick()
    onNodeWithText("Find parking nearby").assertIsDisplayed()
}

/**
 * Waits until [text] is on screen. The simulated payment approvals run on a
 * real 1.5 s clock instead of the Compose test clock, so the state they
 * produce has to be awaited instead of asserted right away.
 */
internal fun ComposeTestRule.waitForText(text: String, timeoutMillis: Long = 10_000) {
    waitUntil(timeoutMillis) {
        onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
}

/** Waits until no node shows [text] (e.g. a snackbar that auto-dismisses). */
internal fun ComposeTestRule.waitUntilGone(text: String, timeoutMillis: Long = 12_000) {
    waitUntil(timeoutMillis) {
        onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
    }
}
