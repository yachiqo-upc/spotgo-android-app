package com.yachiqo.spotgo.data.repository

import com.yachiqo.spotgo.TestDatabase
import com.yachiqo.spotgo.data.local.SpotGoDatabase
import com.yachiqo.spotgo.data.model.AccountStatus
import com.yachiqo.spotgo.data.model.AuthMethod
import com.yachiqo.spotgo.data.model.ProfileType
import com.yachiqo.spotgo.data.sha256
import java.time.Instant
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Access flow (US09 sign-in / US13 registration + verified e-mail, password
 * reset and federated Google sign-in) against the in-memory database.
 */
class AuthRepositoryTest {

    private lateinit var db: TestDatabase

    @Before
    fun setUp() {
        db = TestDatabase()
        AuthRepository.sign_out()
        AuthRepository.cancel_email_verification()
    }

    @After
    fun tearDown() {
        AuthRepository.sign_out()
        AuthRepository.cancel_email_verification()
        db.restore()
    }

    /** A fresh e-mail per test so no case depends on another one. */
    private fun uniqueEmail(): String = "driver-${UUID.randomUUID().toString().take(8)}@example.com"

    /** A 6-digit code that is guaranteed to differ from [code]. */
    private fun wrongCodeOf(code: String): String =
        if (code == "111111") "222222" else "111111"

    private fun failure(result: AuthResult): AuthError =
        (result as AuthResult.Failure).error

    private fun failure(result: VerificationResult): AuthError =
        (result as VerificationResult.Failure).error

    /* ------------------------------------------------------------------ */
    /* US09 — sign in                                                       */
    /* ------------------------------------------------------------------ */

    @Test
    fun signInWithDemoCredentialsStartsASession() {
        val result = AuthRepository.sign_in("brigitte.carhuaz@example.com", "SpotGo2026", true)
        assertTrue(result is AuthResult.Success)
        assertFalse(AuthRepository.session_expired())
        assertEquals("Brigitte", AuthRepository.current_profile()?.first_name)
        assertEquals("Carhuaz", AuthRepository.current_profile()?.last_name)
        assertEquals("brigitte.carhuaz@example.com", AuthRepository.current_profile()?.email)
    }

    @Test
    fun signInWithWrongPasswordIsRejected() {
        val result = AuthRepository.sign_in("brigitte.carhuaz@example.com", "not-the-password", false)
        assertTrue(result is AuthResult.Failure)
        assertEquals(AuthError.INVALID_CREDENTIALS, failure(result))
        assertNull(AuthRepository.current_profile())
    }

    @Test
    fun signInWithUnknownAccountIsRejected() {
        val result = AuthRepository.sign_in(uniqueEmail(), "Whatever2026", true)
        assertEquals(AuthError.INVALID_CREDENTIALS, failure(result))
        assertTrue(AuthRepository.session_expired())
    }

    @Test
    fun signOutEndsTheSession() {
        assertTrue(AuthRepository.sign_in("brigitte.carhuaz@example.com", "SpotGo2026", true) is AuthResult.Success)
        AuthRepository.sign_out()
        assertTrue(AuthRepository.session_expired())
        assertNull(AuthRepository.current_profile())
    }

    @Test
    fun anExpiredSessionIsDetectedAndHidesTheProfile() {
        assertTrue(AuthRepository.sign_in("brigitte.carhuaz@example.com", "SpotGo2026", true) is AuthResult.Success)
        val account = SpotGoDatabase.accounts.first { it.email == "brigitte.carhuaz@example.com" }
        SpotGoDatabase.sessions.indices
            .filter { SpotGoDatabase.sessions[it].account_id == account.account_id }
            .forEach { index ->
                SpotGoDatabase.sessions[index] = SpotGoDatabase.sessions[index]
                    .copy(expires_at = Instant.now().minusSeconds(60).toString())
            }
        assertTrue(AuthRepository.session_expired())
        assertNull(AuthRepository.current_profile())
    }

    /* ------------------------------------------------------------------ */
    /* US13 — registration                                                  */
    /* ------------------------------------------------------------------ */

    @Test
    fun registerCreatesAccountProfileCredentialAndSession() {
        val email = uniqueEmail()
        val result = AuthRepository.register(
            first_name = "  Ana ",
            last_name = "Torres",
            phone = "+51 987 654 321",
            email = email,
            password = "Secret2026!",
            accepted_terms = true,
        )
        assertTrue(result is AuthResult.Success)
        assertEquals(email, AuthRepository.current_profile()?.email)
        assertEquals("Ana", AuthRepository.current_profile()?.first_name)
        assertEquals(ProfileType.DRIVER, AuthRepository.current_profile()?.profile_type)

        assertTrue(SpotGoDatabase.accounts.any { it.email == email && it.status == AccountStatus.ACTIVE })
        assertTrue(SpotGoDatabase.credentials.any { it.secret_hash == sha256("Secret2026!") })
        assertTrue(SpotGoDatabase.profiles.any { it.email == email })

        // The account can be used right away.
        AuthRepository.sign_out()
        assertTrue(AuthRepository.sign_in(email, "Secret2026!", true) is AuthResult.Success)
    }

    @Test
    fun registerRejectsAnInvalidForm() {
        val email = uniqueEmail()
        val valid = Triple("+51 987 654 321", email, "Secret2026!")

        val missingName = AuthRepository.register("", "Torres", valid.first, valid.second, valid.third, true)
        assertEquals(AuthError.INVALID_FORM, failure(missingName))

        val shortPhone = AuthRepository.register("Ana", "Torres", "12", valid.second, valid.third, true)
        assertEquals(AuthError.INVALID_FORM, failure(shortPhone))

        val badEmail = AuthRepository.register("Ana", "Torres", valid.first, "not-an-email", valid.third, true)
        assertEquals(AuthError.INVALID_FORM, failure(badEmail))

        val shortPassword = AuthRepository.register("Ana", "Torres", valid.first, valid.second, "short", true)
        assertEquals(AuthError.INVALID_FORM, failure(shortPassword))

        val noSymbolPassword = AuthRepository.register("Ana", "Torres", valid.first, valid.second, "Secret2026", true)
        assertEquals(AuthError.INVALID_FORM, failure(noSymbolPassword))

        val withoutTerms = AuthRepository.register("Ana", "Torres", valid.first, valid.second, valid.third, false)
        assertEquals(AuthError.INVALID_FORM, failure(withoutTerms))

        assertFalse(SpotGoDatabase.accounts.any { it.email == valid.second })
    }

    @Test
    fun passwordPolicyMatchesBackendRequirements() {
        assertTrue(AuthRepository.isPasswordValid("Secret2026!"))
        assertFalse(AuthRepository.isPasswordValid("secret2026!"))
        assertFalse(AuthRepository.isPasswordValid("SECRET2026!"))
        assertFalse(AuthRepository.isPasswordValid("SecretPass!"))
        assertFalse(AuthRepository.isPasswordValid("Secret2026"))
    }

    @Test
    fun registerRejectsADuplicatedEmail() {
        val result = AuthRepository.register(
            first_name = "Other",
            last_name = "Driver",
            phone = "+51 911 111 111",
            email = "brigitte.carhuaz@example.com",
            password = "Secret2026!",
            accepted_terms = true,
        )
        assertEquals(AuthError.DUPLICATE_EMAIL, failure(result))
        assertEquals(1, SpotGoDatabase.accounts.count { it.email == "brigitte.carhuaz@example.com" })
    }

    /* ------------------------------------------------------------------ */
    /* US13 — e-mail verification (the account is born verified)            */
    /* ------------------------------------------------------------------ */

    @Test
    fun registrationNeedsTheEmailedCodeBeforeTheAccountExists() {
        val email = uniqueEmail()
        val sent = AuthRepository.begin_email_verification(
            first_name = "Ana",
            last_name = "Torres",
            phone = "+51 987 654 321",
            email = email,
            password = "Secret2026!",
            accepted_terms = true,
        )
        assertTrue(sent is VerificationResult.CodeSent)
        // No SPOTGO_API_BASE_URL in the demo build: there is no service to
        // e-mail the code, so the UI shows it (demo mode).
        val code = (sent as VerificationResult.CodeSent).displayed_code
        assertNotNull("the demo build must surface the code", code)
        assertTrue(code!!.matches(Regex("^[0-9]{6}$")))
        assertEquals(email, AuthRepository.pending_verification_email())
        assertFalse("the account only exists after the code", SpotGoDatabase.accounts.any { it.email == email })

        // A wrong code keeps the registration pending.
        val wrong = AuthRepository.confirm_email_verification(wrongCodeOf(code))
        assertEquals(AuthError.INVALID_TOKEN, failure(wrong))
        assertFalse(SpotGoDatabase.accounts.any { it.email == email })
        assertEquals(email, AuthRepository.pending_verification_email())

        // "Send a new code" regenerates it.
        val resent = AuthRepository.resend_email_verification()
        assertTrue(resent is VerificationResult.CodeSent)
        val newCode = (resent as VerificationResult.CodeSent).displayed_code
        assertNotNull(newCode)
        assertTrue(newCode!!.matches(Regex("^[0-9]{6}$")))

        // The right code creates the account and the session.
        val confirmed = AuthRepository.confirm_email_verification(newCode)
        assertTrue(confirmed is AuthResult.Success)
        assertTrue(SpotGoDatabase.accounts.any { it.email == email })
        assertEquals(email, AuthRepository.current_profile()?.email)
        assertNull(AuthRepository.pending_verification_code())
        assertNull(AuthRepository.pending_verification_email())
    }

    @Test
    fun cancellingTheVerificationDropsThePendingRegistration() {
        val email = uniqueEmail()
        AuthRepository.begin_email_verification("Ana", "Torres", "+51 987 654 321", email, "Secret2026!", true)
        assertNotNull(AuthRepository.pending_verification_code())

        AuthRepository.cancel_email_verification()
        assertNull(AuthRepository.pending_verification_code())
        assertNull(AuthRepository.pending_verification_email())
        val confirm = AuthRepository.confirm_email_verification("123456")
        assertEquals(AuthError.INVALID_TOKEN, failure(confirm))
        assertFalse(SpotGoDatabase.accounts.any { it.email == email })
    }

    @Test
    fun verificationRejectsAnIncompleteFormAndADuplicatedEmail() {
        val invalid = AuthRepository.begin_email_verification("", "", "", uniqueEmail(), "", false)
        assertEquals(AuthError.INVALID_FORM, failure(invalid))

        val duplicated = AuthRepository.begin_email_verification(
            "Ana", "Torres", "+51 987 654 321", "brigitte.carhuaz@example.com", "Secret2026!", true,
        )
        assertEquals(AuthError.DUPLICATE_EMAIL, failure(duplicated))
    }

    @Test
    fun confirmingWithoutAPendingRegistrationIsRejected() {
        val confirm = AuthRepository.confirm_email_verification("123456")
        assertEquals(AuthError.INVALID_TOKEN, failure(confirm))
    }

    /* ------------------------------------------------------------------ */
    /* Password reset (demo: the token never leaves the device)             */
    /* ------------------------------------------------------------------ */

    @Test
    fun passwordResetFollowsRequestVerifyAndReset() {
        val email = uniqueEmail()
        assertTrue(AuthRepository.register("Ana", "Torres", "+51 987 654 321", email, "Secret2026!", true) is AuthResult.Success)
        AuthRepository.sign_out()

        val sent = AuthRepository.request_password_reset(email)
        assertTrue(sent is ResetResult.CodeSent)
        val token = (sent as ResetResult.CodeSent).token
        assertTrue(token.matches(Regex("^[0-9]{6}$")))

        val unknown = AuthRepository.request_password_reset(uniqueEmail())
        assertTrue(unknown is ResetResult.Failure)
        assertEquals(AuthError.ACCOUNT_NOT_FOUND, (unknown as ResetResult.Failure).error)

        val wrong = AuthRepository.verify_reset_token(email, wrongCodeOf(token))
        assertEquals(AuthError.INVALID_TOKEN, (wrong as ResetResult.Failure).error)

        val tooShort = AuthRepository.reset_password(email, token, "short")
        assertEquals(AuthError.INVALID_FORM, (tooShort as ResetResult.Failure).error)

        val noSymbol = AuthRepository.reset_password(email, token, "NewPass2026")
        assertEquals(AuthError.INVALID_FORM, (noSymbol as ResetResult.Failure).error)

        val reset = AuthRepository.reset_password(email, token, "NewPass2026!")
        assertTrue(reset is ResetResult.Success)

        assertTrue(AuthRepository.sign_in(email, "Secret2026!", false) is AuthResult.Failure)
        assertTrue(AuthRepository.sign_in(email, "NewPass2026!", false) is AuthResult.Success)
    }

    @Test
    fun resetPasswordWithAnUnknownAccountIsRejected() {
        val result = AuthRepository.reset_password(uniqueEmail(), "123456", "NewPass2026!")
        assertTrue(result is ResetResult.Failure)
        assertEquals(AuthError.ACCOUNT_NOT_FOUND, (result as ResetResult.Failure).error)
    }

    /* ------------------------------------------------------------------ */
    /* US09 scenario 3 — Google federated sign-in                           */
    /* ------------------------------------------------------------------ */

    @Test
    fun googleSignInCreatesTheAccountOnceAndKeepsTheSubject() {
        val email = uniqueEmail()
        val subject = "google-sub-$email"

        val first = AuthRepository.sign_in_with_google(
            keep_signed_in = true, email = email, display_name = "Ana Torres", external_subject = subject,
        )
        assertTrue(first is AuthResult.Success)
        val profile = AuthRepository.current_profile()!!
        assertEquals("Ana", profile.first_name)
        assertEquals("Torres", profile.last_name)
        assertEquals(email, profile.email)

        // A second federated sign-in reuses the same account + profile.
        val accountsBefore = SpotGoDatabase.accounts.count { it.email == email }
        val profilesBefore = SpotGoDatabase.profiles.count { it.email == email }
        AuthRepository.sign_in_with_google(
            keep_signed_in = true, email = email, display_name = "Ana Torres", external_subject = subject,
        )
        assertEquals(accountsBefore, SpotGoDatabase.accounts.count { it.email == email })
        assertEquals(profilesBefore, SpotGoDatabase.profiles.count { it.email == email })
        assertTrue(
            SpotGoDatabase.credentials.any {
                it.authentication_method == AuthMethod.GOOGLE && it.external_subject == subject
            },
        )
    }

    @Test
    fun googleSignInFallsBackToTheDemoAccountWhenGoogleOmitsTheEmail() {
        val result = AuthRepository.sign_in_with_google(
            keep_signed_in = true, email = "", display_name = null, external_subject = "sub-without-email",
        )
        assertTrue(result is AuthResult.Success)
        assertEquals(AuthRepository.GOOGLE_DEMO_EMAIL, AuthRepository.current_profile()?.email)
    }
}
