package com.yachiqo.spotgo.data

import android.graphics.Bitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry
import com.yachiqo.spotgo.MainActivity
import com.yachiqo.spotgo.data.repository.AuthRepository
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Rule
import org.junit.Test

class SpotGoNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    // Every test starts from the seed data of SpotGoDatabase.
    private val database = DatabaseSnapshot()

    @After
    fun restoreDatabase() {
        AuthRepository.sign_out()
        database.restore()
    }

    @Test
    fun loginShowsPlaceholdersAndCanClearPassword() {
        compose.onNodeWithText("you@example.com").assertIsDisplayed()
        compose.onNodeWithText("Enter your password").assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasText("Password")).performTextInput("sprint1-preview")
        compose.onNodeWithContentDescription("Clear password").performClick()
        compose.onNodeWithText("Enter your password").assertIsDisplayed()
    }

    @Test
    fun driverTabsAndSystemBackWorkAfterSignIn() {
        compose.signIn()
        compose.onNodeWithText("Reservations").performClick()
        compose.onNodeWithText("My reservations").assertIsDisplayed()
        compose.onNode(hasText("Reservations") and isSelectable()).assertIsSelected()
        pressBack()
        compose.onNodeWithText("Welcome back").assertIsDisplayed()
    }

    @Test
    fun signedInTabAndProfileSurviveActivityRecreation() {
        compose.signIn()
        compose.onNode(hasText("Profile") and isSelectable()).performClick()
        compose.activityRule.scenario.recreate()
        compose.onAllNodesWithText("Profile").assertCountEquals(2)
        compose.onNode(hasText("Profile") and isSelectable()).assertIsSelected()
        compose.onNodeWithText("Briguite Carhuaz").assertIsDisplayed()
        compose.onNodeWithText("Sign out").performScrollTo().performClick()
        compose.onNodeWithText("Welcome back").assertIsDisplayed()
    }

    @Test
    fun driverScreensAreReachableAndSignOutReturnsToLogin() {
        compose.signIn()
        captureScreenshot("03-explore")
        compose.onNodeWithText("View parking").performScrollTo().assertIsDisplayed()
        captureScreenshot("03-explore-card")
        compose.onNodeWithText("View parking").performClick()
        compose.onNodeWithText("Zone details").assertIsDisplayed()
        captureScreenshot("04-zone")

        // US19 scenario 6 — the seeded unpaid balance blocks new reservations.
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        compose.waitForText("Outstanding balance")
        captureScreenshot("04-zone-blocked")
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Zone details").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to Explore").performClick()
        compose.onNodeWithText("Find parking nearby").assertIsDisplayed()

        // Mockup 08 — the balance card shows the seeded amount as pending.
        compose.onNode(hasText("Payments") and isSelectable()).performClick()
        compose.onNodeWithText("S/ 8.00").assertIsDisplayed()
        compose.onNodeWithText("Pending").assertIsDisplayed()
        captureScreenshot("08-payments")
        compose.onNodeWithText("S/ 8.00").performClick()

        // Mockup 38 — retry the failed charge (1.5 s approval).
        compose.onNodeWithText("Retry payment").assertIsDisplayed()
        captureScreenshot("38-balance")
        compose.onNodeWithText("Retry payment").performClick()
        compose.waitForText("S/ 0.00")
        compose.onNodeWithText("Up to date").assertIsDisplayed()
        compose.waitForText("Additional charge paid")
        compose.waitUntilGone("Additional charge paid")

        // US18 — reserve flow once the balance is settled.
        compose.onNode(hasText("Explore") and isSelectable()).performClick()
        compose.onNodeWithText("View parking").performScrollTo().performClick()
        compose.onNodeWithText("Zone details").assertIsDisplayed()
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        compose.waitForText("Select an available spot")
        // The sheet's "Continue" shares its label with the zone form button
        // and stays disabled until a spot is chosen.
        spotSheetContinue().assertIsNotEnabled()
        compose.onNodeWithText("B-10").performClick()
        spotSheetContinue().performClick()
        compose.waitForText("Review & pay")
        captureScreenshot("04-review")
        compose.onNodeWithText("Confirm payment").performClick()
        compose.onNode(hasText("Processing payment", substring = true)).assertIsDisplayed()
        compose.waitForText("Reservation confirmed")
        compose.onNodeWithText("Payment successful").assertIsDisplayed()
        compose.onNodeWithText("Booking code").assertIsDisplayed()
        captureScreenshot("05-confirmed")

        compose.onNodeWithText("My reservations").performScrollTo().performClick()
        compose.onNodeWithText("My reservations").assertIsDisplayed()
        captureScreenshot("06-reservations")

        compose.onNode(hasText("Profile") and isSelectable()).performClick()
        compose.onNodeWithText("Briguite Carhuaz").assertIsDisplayed()
        captureScreenshot("10-profile")
        compose.onNodeWithText("Sign out").performScrollTo().performClick()
        compose.onNodeWithText("Welcome back").assertIsDisplayed()
    }

    @Test
    fun nestedDriverScreensSurviveRecreationAndBackReturnsToExplore() {
        compose.signIn()
        compose.onNodeWithText("View parking").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Zone details").assertIsDisplayed()
        // The reserve form is still blocked by the seeded unpaid balance.
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        compose.waitForText("Outstanding balance")
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Zone details").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Zone details").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to Explore").performClick()
        compose.onNodeWithText("Find parking nearby").assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("Welcome back").assertIsDisplayed()
    }

    @Test
    fun unfinishedActionsStayWithinTheStaticScreen() {
        compose.signIn()
        compose.onNode(hasText("Payments") and isSelectable()).performClick()
        compose.onNodeWithText("Add payment method").performScrollTo().performClick()
        compose.onNodeWithText("This action is not available yet.").assertIsDisplayed()
        compose.onNode(hasText("Payments") and isSelectable()).assertIsSelected()
    }

    /**
     * The "Continue" of the spot sheet: it shares its label with the button of
     * the zone form that stays behind the sheet, so it is scoped by ancestor.
     */
    private fun spotSheetContinue() = compose.onNode(
        hasText("Continue") and hasAnyAncestor(hasAnyDescendant(hasText("Select an available spot")))
    )

    private fun captureScreenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "driver-validation")
        check(directory.exists() || directory.mkdirs())
        val width = instrumentation.targetContext.resources.configuration.screenWidthDp
        val frameCommitted = CountDownLatch(1)
        compose.runOnUiThread {
            val rootView = compose.activity.window.decorView
            rootView.viewTreeObserver.registerFrameCommitCallback { frameCommitted.countDown() }
            rootView.invalidate()
        }
        check(frameCommitted.await(5, TimeUnit.SECONDS)) { "The screenshot frame was not committed." }
        instrumentation.uiAutomation.waitForIdle(1_000, 5_000)
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name-$width.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
