package com.yachiqo.spotgo.data

import android.graphics.Bitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
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
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Rule
import org.junit.Test

class SpotGoNavigationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val database = DatabaseSnapshot()

    @After
    fun restoreDatabase() {
        compose.signOutIfSignedIn()
        database.restore()
    }

    @Test
    fun loginShowsPlaceholdersAndCanTogglePasswordVisibility() {
        compose.onNodeWithText("you@example.com").assertIsDisplayed()
        compose.onNodeWithText("Enter your password").assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasText("Password")).performTextInput("sprint1-preview")
        compose.onNodeWithContentDescription("Show password").performClick()
        compose.onNodeWithContentDescription("Hide password").assertIsDisplayed()
        compose.onNodeWithContentDescription("Hide password").performClick()
        compose.onNodeWithContentDescription("Show password").assertIsDisplayed()
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
        compose.onNodeWithText("Test Driver").assertIsDisplayed()
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
        compose.onNodeWithContentDescription("Back to Explore").performClick()
        compose.onNodeWithText("Find parking nearby").assertIsDisplayed()

        compose.onNode(hasText("Payments") and isSelectable()).performClick()
        compose.onNodeWithText("No outstanding balance").assertIsDisplayed()
        captureScreenshot("08-payments")
        compose.onNodeWithText("Add payment method").performScrollTo().performClick()
        compose.onNode(hasText("Add a test card reference", substring = true)).assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()

        compose.onNode(hasText("Reservations") and isSelectable()).performClick()
        compose.onNodeWithText("My reservations").assertIsDisplayed()
        captureScreenshot("06-reservations")

        compose.onNode(hasText("Profile") and isSelectable()).performClick()
        compose.onNodeWithText("Test Driver").assertIsDisplayed()
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
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Zone details").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to Explore").performClick()
        compose.onNodeWithText("Find parking nearby").assertIsDisplayed()
        pressBack()
        compose.onNodeWithText("Welcome back").assertIsDisplayed()
    }

    @Test
    fun simulatedPaymentMethodDialogCanBeOpenedAndDismissed() {
        compose.signIn()
        compose.onNode(hasText("Payments") and isSelectable()).performClick()
        compose.onNodeWithText("Add payment method").performScrollTo().performClick()
        compose.onNode(hasText("Add a test card reference", substring = true)).assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNode(hasText("Payments") and isSelectable()).assertIsSelected()
    }

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
