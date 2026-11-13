package com.yachiqo.spotgo.data

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.pressBack
import com.yachiqo.spotgo.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

private const val TEST_PASSWORD = "SpotGoTest2026!"

private data class DriverCredentials(val email: String, val password: String)

/** Creates one isolated API-backed Driver account for this instrumentation run. */
private object BackendTestDriver {
    @Volatile private var cachedCredentials: DriverCredentials? = null

    @Synchronized
    fun credentials(): DriverCredentials {
        cachedCredentials?.let { return it }
        val baseUrl = BuildConfig.SPOTGO_API_BASE_URL.trim().trimEnd('/')
        check(baseUrl.isNotBlank()) { "SPOTGO_API_BASE_URL must be configured for instrumentation tests." }

        val credentials = DriverCredentials(
            email = "spotgo-test-${UUID.randomUUID()}@example.com",
            password = TEST_PASSWORD,
        )
        val auth = request(
            baseUrl,
            "/api/v1/authentication/sign-up",
            JSONObject().put("email", credentials.email).put("password", credentials.password),
        )
        check(auth.first in 200..299) { "Could not provision the instrumentation test Driver (HTTP ${auth.first})." }
        val accessToken = JSONObject(auth.second).getString("accessToken")
        val phone = "+519${System.currentTimeMillis().toString().takeLast(8)}"
        val profile = request(
            baseUrl,
            "/api/v1/profiles",
            JSONObject().put("firstName", "Test").put("lastName", "Driver").put("phone", phone),
            accessToken,
        )
        check(profile.first in 200..299) { "Could not provision the instrumentation test profile (HTTP ${profile.first})." }
        runCatching {
            request(baseUrl, "/api/v1/authentication/sign-out", JSONObject(), accessToken)
        }
        cachedCredentials = credentials
        return credentials
    }

    private fun request(
        baseUrl: String,
        path: String,
        body: JSONObject,
        bearer: String? = null,
    ): Pair<Int, String> {
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            bearer?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            return status to (stream?.use { String(it.readBytes(), Charsets.UTF_8) } ?: "")
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * Provisions and signs in with a test Driver account, then waits for the workspace.
 * "Keep me signed in" is checked by default on the login screen, so
 * the session lasts longer than the test.
 */
internal fun ComposeTestRule.signIn() {
    signOutIfSignedIn()
    val credentials = BackendTestDriver.credentials()
    onNode(hasSetTextAction() and hasText("Email")).performTextInput(credentials.email)
    onNode(hasSetTextAction() and hasText("Password")).performTextInput(credentials.password)
    closeSoftKeyboard()
    // The section title and the button share the "Sign in" label: only the
    // button has a click action.
    onNode(hasText("Sign in") and hasClickAction()).performClick()
    onNodeWithText("Find parking nearby").assertIsDisplayed()
}

/** Leaves the target app on Login so test cases never share an authenticated UI session. */
internal fun ComposeTestRule.signOutIfSignedIn() {
    if (onAllNodesWithText("Find parking nearby").fetchSemanticsNodes().isEmpty()) return
    for (step in 0 until 5) {
        if (onAllNodesWithText("Profile").fetchSemanticsNodes().isNotEmpty()) break
        pressBack()
    }
    if (onAllNodesWithText("Profile").fetchSemanticsNodes().isNotEmpty()) {
        onNodeWithText("Profile").performClick()
        onNodeWithText("Sign out").performScrollTo().performClick()
        waitForText("Welcome back")
    }
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
