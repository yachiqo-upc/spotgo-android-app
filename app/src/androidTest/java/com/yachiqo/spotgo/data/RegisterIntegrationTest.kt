package com.yachiqo.spotgo.data

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.yachiqo.spotgo.MainActivity
import com.yachiqo.spotgo.data.repository.AuthRepository
import java.io.File
import org.junit.After
import org.junit.Rule
import org.junit.Test

class RegisterIntegrationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    // Every test starts from the seed accounts of SpotGoDatabase.
    private val database = DatabaseSnapshot()

    @After
    fun restoreDatabase() {
        AuthRepository.sign_out()
        database.restore()
    }

    @Test
    fun registerStartsEmptyAndReturnsToLoginThroughBothControls() {
        openRegister()
        capture("02-register")
        listOf("First name", "Last name", "Phone", "Email", "Password").forEach {
            field(it).performScrollTo().assert(emptyField())
        }
        compose.onNodeWithText("I agree to the Terms and Privacy Policy")
            .performScrollTo().assertIsOff()
        capture("02-register-actions")
        compose.onNodeWithText("Sign in").performScrollTo().performClick()
        compose.onNodeWithText("Welcome back").assertIsDisplayed()
        openRegister()
        compose.onNodeWithContentDescription("Back to login").performScrollTo().performClick()
        compose.onNodeWithText("Welcome back").assertIsDisplayed()
        openRegister()
        pressBack()
        compose.onNodeWithText("Welcome back").assertIsDisplayed()
    }

    @Test
    fun registerRestoresNonSensitiveInputsAndClearsTemporaryPassword() {
        openRegister()
        field("First name").performTextInput("Sample")
        field("Last name").performScrollTo().performTextInput("Driver")
        field("Phone").performScrollTo().performTextInput("123456789")
        field("Email").performScrollTo().performTextInput("sample@example.com")
        field("Password").performScrollTo().performTextInput("temporary-password")
        compose.onNodeWithContentDescription("Clear password").performClick()
        field("Password").assert(emptyField()).performTextInput("temporary-password")
        closeSoftKeyboard()
        compose.onNodeWithText("I agree to the Terms and Privacy Policy").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("Create your Driver account").performScrollTo().assertIsDisplayed()
        field("First name").performScrollTo().assertTextContains("Sample")
        field("Last name").performScrollTo().assertTextContains("Driver")
        field("Phone").performScrollTo().assertTextContains("123456789")
        field("Email").performScrollTo().assertTextContains("sample@example.com")
        field("Password").performScrollTo().assert(emptyField())
        compose.onNodeWithText("I agree to the Terms and Privacy Policy").performScrollTo().assertIsOn()
        compose.onNodeWithText("Sign in").performScrollTo().performClick()
        openRegister()
        field("First name").assert(emptyField())
        compose.onNodeWithText("I agree to the Terms and Privacy Policy").performScrollTo().assertIsOff()
    }

    @Test
    fun registrationValidatesTheFormAndVerifiesTheEmailBeforeSignIn() {
        openRegister()
        // Empty form: the invalid-form state explains what is missing.
        compose.onNodeWithText("Create account").performScrollTo().performClick()
        compose.waitForText("Check your information")
        capture("02-register-invalid")
        compose.onNodeWithText("Review form").performClick()
        compose.onNodeWithText("Check your information").assertDoesNotExist()

        field("First name").performTextInput("Prueba")
        field("Last name").performScrollTo().performTextInput("Conductor")
        field("Phone").performScrollTo().performTextInput("987654321")
        field("Email").performScrollTo().performTextInput("qa.driver@example.com")
        field("Password").performScrollTo().performTextInput("SpotGo2026")
        closeSoftKeyboard()
        compose.onNodeWithText("I agree to the Terms and Privacy Policy").performScrollTo().performClick()
        compose.onNodeWithText("Create account").performScrollTo().performClick()

        // US13 — the account only exists after the e-mail code, and the demo
        // mode shows the code because there is no backend to e-mail it.
        compose.waitForText("Verify your email")
        compose.onNode(hasText("Demo mode: your code is", substring = true)).assertIsDisplayed()
        capture("02-register-verify")

        // "Edit form" keeps everything typed so far.
        compose.onNodeWithText("Edit form").performScrollTo().performClick()
        compose.waitForText("Create your Driver account")
        field("First name").performScrollTo().assertTextContains("Prueba")
        field("Last name").performScrollTo().assertTextContains("Conductor")
        field("Phone").performScrollTo().assertTextContains("987654321")
        field("Email").performScrollTo().assertTextContains("qa.driver@example.com")

        compose.onNodeWithText("Sign in").performScrollTo().performClick()
        compose.waitForText("Welcome back")

        compose.signIn()
        compose.onNode(hasText("Profile") and isSelectable()).performClick()
        compose.onNodeWithText("Briguite Carhuaz").assertIsDisplayed()
        compose.onNodeWithText("Sign out").performScrollTo().performClick()
        compose.waitForText("Welcome back")
    }

    private fun openRegister() {
        compose.onNodeWithText("Create account").performScrollTo().performClick()
        compose.onNodeWithText("Create your Driver account").assertIsDisplayed()
    }

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))

    private fun emptyField() = SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(""))

    private fun capture(name: String) {
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(600)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        SystemClock.sleep(750)
        val context = instrumentation.targetContext
        val directory = File(context.getExternalFilesDir(null), "register-validation").apply { mkdirs() }
        val width = context.resources.configuration.screenWidthDp
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("screencap -p"))
            .use { input -> File(directory, "$name-$width.png").outputStream().use { input.copyTo(it) } }
    }
}
