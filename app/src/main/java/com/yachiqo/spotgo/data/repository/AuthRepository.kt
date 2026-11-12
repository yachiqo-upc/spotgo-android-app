package com.yachiqo.spotgo.data.repository

import android.util.Log
import com.yachiqo.spotgo.BuildConfig
import com.yachiqo.spotgo.data.local.SpotGoDatabase
import com.yachiqo.spotgo.data.model.Account
import com.yachiqo.spotgo.data.model.AccountStatus
import com.yachiqo.spotgo.data.model.AuthMethod
import com.yachiqo.spotgo.data.model.Credential
import com.yachiqo.spotgo.data.model.Profile
import com.yachiqo.spotgo.data.model.ProfileType
import com.yachiqo.spotgo.data.model.Session
import com.yachiqo.spotgo.data.model.SessionStatus
import com.yachiqo.spotgo.data.remote.ApiResult
import com.yachiqo.spotgo.data.remote.FcmDeviceRegistration
import com.yachiqo.spotgo.data.remote.SpotGoApi
import com.yachiqo.spotgo.data.sha256
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/** Outcome of an access operation (US09 / US13). */
sealed interface AuthResult {
    data class Success(val session: Session, val profile: Profile) : AuthResult
    data class Failure(val error: AuthError) : AuthResult
}

/**
 * Access failures modelled after the Figma state sheets:
 * - INVALID_FORM: required fields missing or invalid (register).
 * - DUPLICATE_EMAIL: e-mail already registered (register).
 * - INVALID_CREDENTIALS: wrong e-mail/password or inactive account (sign in).
 * - ACCOUNT_NOT_FOUND / INVALID_TOKEN: password-reset flow (demo).
 * - API_ERROR: the backend call of the e-mail verification failed
 *   (Pendiente 5 — only while SPOTGO_API_BASE_URL is configured).
 */
enum class AuthError {
    INVALID_FORM,
    DUPLICATE_EMAIL,
    INVALID_CREDENTIALS,
    ACCOUNT_NOT_FOUND,
    INVALID_TOKEN,
    API_ERROR,
    PROFILE_REQUIRED,
}

/** Outcome of the password-reset flow (demo: the token never leaves the device). */
sealed interface ResetResult {
    /** A reset code was generated for the account in the local-only flow. */
    data class CodeSent(val token: String) : ResetResult
    object Success : ResetResult
    data class Failure(val error: AuthError) : ResetResult
}

/**
 * Outcome of the e-mail verification step of the registration (US13 —
 * registering a Driver account requires a verified e-mail).
 */
sealed interface VerificationResult {
    /**
     * The 6-digit code is generated locally while email verification is not
     * part of the backend sign-up contract. The UI shows it for local testing.
     */
    data class CodeSent(val displayed_code: String?) : VerificationResult
    data class Failure(val error: AuthError) : VerificationResult
}

/**
 * In-memory authentication for the Driver app while the backend does not
 * exist. Behaviour follows the userflow: validate data and unique e-mail,
 * create account + user profile, enable Driver functions with a valid
 * session, reject invalid or expired access.
 */
object AuthRepository {

    /** "Keep me signed in" extends the session; otherwise it expires quickly. */
    private const val SESSION_TTL_KEEP_SIGNED_IN_SECONDS = 7 * 24 * 60 * 60L
    // Demo-only: short TTL so the "Sign in again" state is easy to exercise.
    private const val SESSION_TTL_DEFAULT_SECONDS = 2 * 60L

    private var active_session_id: String? = null
    private var backend_profile: Profile? = null
    private var pending_backend_user: JSONObject? = null
    private var backend_refresh_expires_at: String? = null

    /** Pending password-reset token (demo, never persisted). */
    private data class PasswordResetToken(
        val account_id: String,
        val token: String,
        val expires_at: Instant,
    )

    private var pending_reset: PasswordResetToken? = null

    private const val RESET_TOKEN_TTL_SECONDS = 15 * 60L

    /**
     * Registration waiting for its e-mail code (US13 — verified e-mail).
     * The account does not exist yet: it is created by
     * confirm_email_verification() with the code already verified.
     */
    private data class PendingRegistration(
        val first_name: String,
        val last_name: String,
        val phone: String,
        val email: String,
        val password: String,
        val accepted_terms: Boolean,
        var token: String,
        var expires_at: Instant,
    )

    private var pending_registration: PendingRegistration? = null

    private const val EMAIL_TOKEN_TTL_SECONDS = 15 * 60L
    const val GOOGLE_DEMO_EMAIL = "brigitte.carhuaz@gmail.com"

    /**
     * US13 — step 1 of the registration with a verified e-mail: validates
     * the form and the unique e-mail, generates the 6-digit code and hands
     * it to the backend. The account is only created by
     * confirm_email_verification(), so every account is born verified.
     *
     * The backend creates Driver credentials directly through /sign-up and
     * has no email-verification endpoint. Keep this local code step for
     * academic testing without attempting a request to a missing route.
     */
    fun begin_email_verification(
        first_name: String,
        last_name: String,
        phone: String,
        email: String,
        password: String,
        accepted_terms: Boolean,
    ): VerificationResult {
        val cleanEmail = email.trim()
        if (!isValidRegistration(
                first_name = first_name,
                last_name = last_name,
                phone = phone,
                email = cleanEmail,
                password = password,
                accepted_terms = accepted_terms,
            )
        ) {
            return VerificationResult.Failure(AuthError.INVALID_FORM)
        }
        if (accountsByEmail(cleanEmail).isNotEmpty()) {
            return VerificationResult.Failure(AuthError.DUPLICATE_EMAIL)
        }
        pending_registration = PendingRegistration(
            first_name = first_name,
            last_name = last_name,
            phone = phone,
            email = cleanEmail,
            password = password,
            accepted_terms = accepted_terms,
            token = newVerificationCode(),
            expires_at = Instant.now().plusSeconds(EMAIL_TOKEN_TTL_SECONDS),
        )
        return dispatch_verification_code()
    }

    /** Regenerates and dispatches a new code for the pending registration. */
    fun resend_email_verification(): VerificationResult {
        val pending = pending_registration ?: return VerificationResult.Failure(AuthError.INVALID_TOKEN)
        pending.token = newVerificationCode()
        pending.expires_at = Instant.now().plusSeconds(EMAIL_TOKEN_TTL_SECONDS)
        return dispatch_verification_code()
    }

    /** Code shown to the user while registration email delivery is disabled. */
    fun pending_verification_code(): String? = pending_registration?.token

    /** E-mail being verified, used by the verification screen header. */
    fun pending_verification_email(): String? = pending_registration?.email

    /** US13 — step 2: the entered code creates the account (born verified). */
    fun confirm_email_verification(code: String): AuthResult {
        val pending = pending_registration ?: return AuthResult.Failure(AuthError.INVALID_TOKEN)
        if (pending.token != code.trim() || pending.expires_at.isBefore(Instant.now())) {
            return AuthResult.Failure(AuthError.INVALID_TOKEN)
        }
        pending_registration = null
        return register(
            first_name = pending.first_name,
            last_name = pending.last_name,
            phone = pending.phone,
            email = pending.email,
            password = pending.password,
            accepted_terms = pending.accepted_terms,
        )
    }

    /** Drops the pending registration (the user left the sign-up flow). */
    fun cancel_email_verification() {
        pending_registration = null
    }

    private fun dispatch_verification_code(): VerificationResult {
        val pending = pending_registration ?: return VerificationResult.Failure(AuthError.INVALID_TOKEN)
        return VerificationResult.CodeSent(pending.token)
    }

    private fun newVerificationCode(): String =
        (0..999_999).random().toString().padStart(6, '0')

    /** US13 — register with SpotGo credentials. Creates account + profile + session. */
    fun register(
        first_name: String,
        last_name: String,
        phone: String,
        email: String,
        password: String,
        accepted_terms: Boolean,
    ): AuthResult {
        val cleanEmail = email.trim()
        if (!isValidRegistration(
                first_name = first_name,
                last_name = last_name,
                phone = phone,
                email = cleanEmail,
                password = password,
                accepted_terms = accepted_terms,
            )
        ) {
            return AuthResult.Failure(AuthError.INVALID_FORM)
        }
        if (SpotGoApi.is_configured()) {
            return registerWithBackend(first_name, last_name, phone, cleanEmail, password)
        }
        if (accountsByEmail(cleanEmail).isNotEmpty()) {
            return AuthResult.Failure(AuthError.DUPLICATE_EMAIL)
        }

        val now = now()
        val account = Account(
            account_id = newId("acc"),
            email = cleanEmail,
            status = AccountStatus.ACTIVE,
            created_at = now,
            updated_at = now,
        )
        SpotGoDatabase.accounts += account
        SpotGoDatabase.credentials += Credential(
            credential_id = newId("cred"),
            account_id = account.account_id,
            authentication_method = AuthMethod.SPOTGO_CREDENTIALS,
            secret_hash = sha256(password),
            external_subject = null,
            last_used_at = now,
            status = AccountStatus.ACTIVE,
        )
        val profile = Profile(
            profile_id = newId("prf"),
            identity_ref = account.account_id,
            profile_type = ProfileType.DRIVER,
            first_name = first_name.trim(),
            last_name = last_name.trim(),
            email = cleanEmail,
            phone = phone.trim(),
            status = AccountStatus.ACTIVE,
            created_at = now,
            updated_at = now,
        )
        SpotGoDatabase.profiles += profile

        // Valid data and unique e-mail: the account is usable right away.
        // A fresh registration keeps the driver signed in (same as "keep me signed in").
        return startSession(account, profile, SESSION_TTL_KEEP_SIGNED_IN_SECONDS)
    }

    /** US09 — sign in with SpotGo credentials. */
    fun sign_in(email: String, password: String, keep_signed_in: Boolean): AuthResult {
        val cleanEmail = email.trim()
        if (SpotGoApi.is_configured()) return signInWithBackend(cleanEmail, password)
        val account = accountsByEmail(cleanEmail).firstOrNull()
        val credential = account?.let { acc ->
            SpotGoDatabase.credentials.firstOrNull {
                it.account_id == acc.account_id &&
                    it.authentication_method == AuthMethod.SPOTGO_CREDENTIALS
            }
        }
        if (account == null ||
            credential == null ||
            credential.secret_hash != sha256(password) ||
            account.status != AccountStatus.ACTIVE
        ) {
            return AuthResult.Failure(AuthError.INVALID_CREDENTIALS)
        }

        val now = now()
        SpotGoDatabase.credentials[SpotGoDatabase.credentials.indexOf(credential)] =
            credential.copy(last_used_at = now)
        val profile = profileFor(account) ?: return AuthResult.Failure(AuthError.INVALID_CREDENTIALS)
        val ttl = if (keep_signed_in) SESSION_TTL_KEEP_SIGNED_IN_SECONDS else SESSION_TTL_DEFAULT_SECONDS
        return startSession(account, profile, ttl)
    }

    /**
     * Federated Google sign-in (US09 scenario 3) creating the account + the
     * profile on first use like a real federated sign-up.
     *
     * With a real id token (Pendiente 2) the e-mail, name and subject come
     * from Google; the defaults reproduce the simulated chooser used while
     * SPOTGO_GOOGLE_WEB_CLIENT_ID is empty.
     *
     * TODO(backend): verify the id token (signature / issuer / audience)
     * before creating the session.
     */
    fun sign_in_with_google(
        keep_signed_in: Boolean,
        email: String = GOOGLE_DEMO_EMAIL,
        display_name: String? = "Brigitte Carhuaz",
        external_subject: String = "demo-google-subject",
        id_token: String? = null,
    ): AuthResult {
        if (SpotGoApi.is_configured()) {
            val token = id_token?.takeIf(String::isNotBlank)
                ?: return AuthResult.Failure(AuthError.API_ERROR)
            val body = JSONObject().put("idToken", token).toString()
            return when (val result = SpotGoApi.post("/api/v1/authentication/google", body)) {
                is ApiResult.Success -> when {
                    result.status in 200..299 -> authenticateBackendUser(JSONObject(result.body))
                    result.status == 409 -> AuthResult.Failure(AuthError.DUPLICATE_EMAIL)
                    result.status == 401 -> AuthResult.Failure(AuthError.INVALID_CREDENTIALS)
                    else -> AuthResult.Failure(AuthError.API_ERROR)
                }
                ApiResult.NotConfigured, is ApiResult.Failure -> AuthResult.Failure(AuthError.API_ERROR)
            }
        }
        val googleEmail = email.ifBlank { GOOGLE_DEMO_EMAIL }
        val nameParts = display_name?.trim()?.split(" ", limit = 2).orEmpty()
        val firstName = nameParts.getOrElse(0) { googleEmail.substringBefore('@') }
        val lastName = nameParts.getOrElse(1) { "" }
        val now = now()
        val existing = accountsByEmail(googleEmail).firstOrNull()
        var profile = existing?.let { profileFor(it) }
        val account = existing ?: Account(
            account_id = newId("acc"),
            email = googleEmail,
            status = AccountStatus.ACTIVE,
            created_at = now,
            updated_at = now,
        ).also { SpotGoDatabase.accounts += it }
        if (existing == null) {
            profile = Profile(
                profile_id = newId("prf"),
                identity_ref = account.account_id,
                profile_type = ProfileType.DRIVER,
                first_name = firstName,
                last_name = lastName,
                email = googleEmail,
                phone = "+51 999 123 456",
                status = AccountStatus.ACTIVE,
                created_at = now,
                updated_at = now,
            )
            SpotGoDatabase.profiles += profile
        }
        val resolvedProfile = profile ?: return AuthResult.Failure(AuthError.INVALID_CREDENTIALS)

        val credentialIndex = SpotGoDatabase.credentials.indexOfFirst {
            it.account_id == account.account_id && it.authentication_method == AuthMethod.GOOGLE
        }
        if (credentialIndex >= 0) {
            SpotGoDatabase.credentials[credentialIndex] =
                SpotGoDatabase.credentials[credentialIndex].copy(last_used_at = now)
        } else {
            SpotGoDatabase.credentials += Credential(
                credential_id = newId("cred"),
                account_id = account.account_id,
                authentication_method = AuthMethod.GOOGLE,
                secret_hash = "",
                external_subject = external_subject,
                last_used_at = now,
                status = AccountStatus.ACTIVE,
            )
        }

        val ttl = if (keep_signed_in) SESSION_TTL_KEEP_SIGNED_IN_SECONDS else SESSION_TTL_DEFAULT_SECONDS
        return startSession(account, resolvedProfile, ttl)
    }

    /** Step 1 of the password reset: generates a 6-digit token for the account. */
    fun request_password_reset(email: String): ResetResult {
        if (SpotGoApi.is_configured()) {
            if (!email.trim().matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$"))) {
                return ResetResult.Failure(AuthError.INVALID_FORM)
            }
            val body = JSONObject().put("email", email.trim()).toString()
            return when (val result = SpotGoApi.post("/api/v1/authentication/password-reset/request", body)) {
                is ApiResult.Success -> if (result.status == 204) ResetResult.CodeSent("")
                    else ResetResult.Failure(AuthError.API_ERROR)
                ApiResult.NotConfigured, is ApiResult.Failure -> ResetResult.Failure(AuthError.API_ERROR)
            }
        }
        val account = accountsByEmail(email.trim()).firstOrNull()
            ?: return ResetResult.Failure(AuthError.ACCOUNT_NOT_FOUND)
        val token = (0..999_999).random().toString().padStart(6, '0')
        pending_reset = PasswordResetToken(
            account_id = account.account_id,
            token = token,
            expires_at = Instant.now().plusSeconds(RESET_TOKEN_TTL_SECONDS),
        )
        // No backend: the "sent" token is surfaced by the UI (demo mode).
        return ResetResult.CodeSent(token)
    }

    /** Step 2: the entered code must match the pending, unexpired token. */
    fun verify_reset_token(email: String, token: String): ResetResult {
        if (SpotGoApi.is_configured()) {
            return if (email.isNotBlank() && token.trim().matches(Regex("^\\d{6}$"))) {
                ResetResult.Success
            } else ResetResult.Failure(AuthError.INVALID_TOKEN)
        }
        val account = accountsByEmail(email.trim()).firstOrNull()
            ?: return ResetResult.Failure(AuthError.ACCOUNT_NOT_FOUND)
        val pending = pending_reset
        if (pending == null ||
            pending.account_id != account.account_id ||
            pending.token != token.trim() ||
            pending.expires_at.isBefore(Instant.now())
        ) {
            return ResetResult.Failure(AuthError.INVALID_TOKEN)
        }
        return ResetResult.Success
    }

    /** Step 3: replaces the credential secret and revokes the account's sessions. */
    fun reset_password(email: String, token: String, new_password: String): ResetResult {
        if (SpotGoApi.is_configured()) {
            if (!isPasswordValid(new_password)) return ResetResult.Failure(AuthError.INVALID_FORM)
            val body = JSONObject()
                .put("email", email.trim())
                .put("code", token.trim())
                .put("newPassword", new_password)
                .toString()
            return when (val result = SpotGoApi.post("/api/v1/authentication/password-reset/confirm", body)) {
                is ApiResult.Success -> when {
                    result.status == 204 -> ResetResult.Success
                    result.status == 400 || result.status == 401 -> ResetResult.Failure(AuthError.INVALID_TOKEN)
                    else -> ResetResult.Failure(AuthError.API_ERROR)
                }
                ApiResult.NotConfigured, is ApiResult.Failure -> ResetResult.Failure(AuthError.API_ERROR)
            }
        }
        if (!isPasswordValid(new_password)) return ResetResult.Failure(AuthError.INVALID_FORM)
        val verified = verify_reset_token(email, token)
        if (verified is ResetResult.Failure) return verified

        val account = accountsByEmail(email.trim()).first()
        val now = now()
        val index = SpotGoDatabase.credentials.indexOfFirst {
            it.account_id == account.account_id &&
                it.authentication_method == AuthMethod.SPOTGO_CREDENTIALS
        }
        if (index >= 0) {
            SpotGoDatabase.credentials[index] =
                SpotGoDatabase.credentials[index].copy(secret_hash = sha256(new_password))
        }
        // Changing the password invalidates every active session of the account.
        SpotGoDatabase.sessions.indices
            .filter { SpotGoDatabase.sessions[it].account_id == account.account_id }
            .forEach { i ->
                val session = SpotGoDatabase.sessions[i]
                if (session.status == SessionStatus.ACTIVE) {
                    SpotGoDatabase.sessions[i] =
                        session.copy(status = SessionStatus.REVOKED, revoked_at = now)
                }
            }
        if (active_session_id?.let { id -> SpotGoDatabase.sessions.any { it.session_id == id && it.account_id == account.account_id } } == true) {
            active_session_id = null
        }
        pending_reset = null
        return ResetResult.Success
    }

    /** Revokes the current session (sign out or expired-session cleanup). */
    fun sign_out() {
        if (SpotGoApi.is_configured()) {
            FcmDeviceRegistration.signOut(SpotGoApi.current_access_token())
            SpotGoApi.clear_tokens()
            backend_profile = null
            pending_backend_user = null
            backend_refresh_expires_at = null
        }
        val sessionId = active_session_id ?: return
        val index = SpotGoDatabase.sessions.indexOfFirst { it.session_id == sessionId }
        if (index >= 0) {
            val session = SpotGoDatabase.sessions[index]
            SpotGoDatabase.sessions[index] = session.copy(
                status = SessionStatus.REVOKED,
                revoked_at = now(),
            )
        }
        active_session_id = null
    }

    /** True when there is no valid session or the session past its expires_at. */
    fun session_expired(): Boolean {
        if (SpotGoApi.is_configured()) {
            if (SpotGoApi.current_refresh_token().isNullOrBlank()) return true
            return runCatching {
                backend_refresh_expires_at?.let { Instant.parse(it).isBefore(Instant.now()) } ?: false
            }.getOrDefault(true)
        }
        val sessionId = active_session_id ?: return true
        val session = SpotGoDatabase.sessions.firstOrNull { it.session_id == sessionId }
            ?: return true
        if (session.status != SessionStatus.ACTIVE) return true
        return runCatching {
            Instant.parse(session.expires_at).isBefore(Instant.now())
        }.getOrDefault(true)
    }

    /** Profile linked to the active session, if any. */
    fun current_profile(): Profile? {
        if (SpotGoApi.is_configured()) {
            if (session_expired()) return null
            return backend_profile
        }
        val sessionId = active_session_id ?: return null
        val session = SpotGoDatabase.sessions.firstOrNull { it.session_id == sessionId }
            ?: return null
        if (session_expired()) return null
        val account = SpotGoDatabase.accounts.firstOrNull { it.account_id == session.account_id }
            ?: return null
        return profileFor(account)
    }

    private fun startSession(account: Account, profile: Profile, ttlSeconds: Long = SESSION_TTL_DEFAULT_SECONDS): AuthResult {
        val nowInstant = Instant.now()
        val session = Session(
            session_id = newId("ses"),
            account_id = account.account_id,
            refresh_token_hash = sha256(UUID.randomUUID().toString()),
            created_at = nowInstant.toString(),
            expires_at = nowInstant.plusSeconds(ttlSeconds).toString(),
            last_activity_at = nowInstant.toString(),
            status = SessionStatus.ACTIVE,
            revoked_at = null,
        )
        SpotGoDatabase.sessions += session
        active_session_id = session.session_id
        return AuthResult.Success(session, profile)
    }

    private fun signInWithBackend(email: String, password: String): AuthResult {
        if (email.isBlank() || password.isBlank()) return AuthResult.Failure(AuthError.INVALID_FORM)
        val body = JSONObject().put("email", email).put("password", password).toString()
        return when (val result = SpotGoApi.post("/api/v1/authentication/sign-in", body).also {
            if (BuildConfig.DEBUG) Log.d("SpotGoAuth", "Password sign-in result: ${it.statusName()}")
        }) {
            is ApiResult.Success -> when {
                result.status in 200..299 -> authenticateBackendUser(JSONObject(result.body))
                result.status == 401 -> AuthResult.Failure(AuthError.INVALID_CREDENTIALS)
                else -> AuthResult.Failure(AuthError.API_ERROR)
            }
            ApiResult.NotConfigured, is ApiResult.Failure -> AuthResult.Failure(AuthError.API_ERROR)
        }
    }

    private fun registerWithBackend(
        firstName: String,
        lastName: String,
        phone: String,
        email: String,
        password: String,
    ): AuthResult {
        if (!isPasswordValid(password)) {
            return AuthResult.Failure(AuthError.INVALID_FORM)
        }
        val signUpBody = JSONObject().put("email", email).put("password", password).toString()
        val auth = when (val result = SpotGoApi.post("/api/v1/authentication/sign-up", signUpBody)) {
            is ApiResult.Success -> when {
                result.status in 200..299 -> runCatching { JSONObject(result.body) }.getOrNull()
                    ?: return AuthResult.Failure(AuthError.API_ERROR)
                result.status == 409 -> return AuthResult.Failure(AuthError.DUPLICATE_EMAIL)
                result.status == 400 -> return AuthResult.Failure(AuthError.INVALID_FORM)
                else -> return AuthResult.Failure(AuthError.API_ERROR)
            }
            ApiResult.NotConfigured, is ApiResult.Failure -> return AuthResult.Failure(AuthError.API_ERROR)
        }
        val access = auth.optString("accessToken")
        val refresh = auth.optString("refreshToken")
        if (access.isBlank() || refresh.isBlank()) return AuthResult.Failure(AuthError.API_ERROR)
        SpotGoApi.set_tokens(access, refresh)
        val profileBody = JSONObject()
            .put("firstName", firstName.trim())
            .put("lastName", lastName.trim())
            .put("phone", phone.trim())
            .toString()
        return when (val result = SpotGoApi.post("/api/v1/profiles", profileBody)) {
            is ApiResult.Success -> if (result.status in 200..299) {
                val profile = runCatching { JSONObject(result.body) }.getOrNull()
                    ?: return AuthResult.Failure(AuthError.API_ERROR)
                establishBackendSession(auth, profile)
            } else {
                SpotGoApi.clear_tokens()
                if (result.status == 409) AuthResult.Failure(AuthError.DUPLICATE_EMAIL)
                else AuthResult.Failure(AuthError.API_ERROR)
            }
            ApiResult.NotConfigured, is ApiResult.Failure -> {
                SpotGoApi.clear_tokens()
                AuthResult.Failure(AuthError.API_ERROR)
            }
        }
    }

    private fun authenticateBackendUser(auth: JSONObject): AuthResult {
        if (auth.optString("role") != "DRIVER") return AuthResult.Failure(AuthError.INVALID_CREDENTIALS)
        val access = auth.optString("accessToken")
        val refresh = auth.optString("refreshToken")
        if (access.isBlank() || refresh.isBlank()) return AuthResult.Failure(AuthError.API_ERROR)
        SpotGoApi.set_tokens(access, refresh)
        backend_refresh_expires_at = auth.optString("refreshExpiresAt").takeIf(String::isNotBlank)
        return when (val result = SpotGoApi.get("/api/v1/profiles/me").also {
            if (BuildConfig.DEBUG) Log.d("SpotGoAuth", "Profile lookup result: ${it.statusName()}")
        }) {
            is ApiResult.Success -> {
                when {
                    result.status in 200..299 -> {
                        val profile = runCatching { JSONObject(result.body) }.getOrNull()
                            ?: return AuthResult.Failure(AuthError.API_ERROR)
                        establishBackendSession(auth, profile)
                    }
                    result.status == 404 -> {
                        pending_backend_user = auth
                        AuthResult.Failure(AuthError.PROFILE_REQUIRED)
                    }
                    else -> AuthResult.Failure(AuthError.API_ERROR)
                }
            }
            ApiResult.NotConfigured, is ApiResult.Failure -> AuthResult.Failure(AuthError.API_ERROR)
        }
    }

    /** Creates the required personal profile after a first-time Google login. */
    fun complete_backend_profile(firstName: String, lastName: String, phone: String): AuthResult {
        if (firstName.isBlank() || lastName.isBlank() ||
            !phone.trim().matches(Regex("""^\\+?[0-9][0-9\\s]{6,18}$"""))) {
            return AuthResult.Failure(AuthError.INVALID_FORM)
        }
        val auth = pending_backend_user ?: return AuthResult.Failure(AuthError.API_ERROR)
        val body = JSONObject()
            .put("firstName", firstName.trim())
            .put("lastName", lastName.trim())
            .put("phone", phone.trim())
            .toString()
        return when (val result = SpotGoApi.post("/api/v1/profiles", body)) {
            is ApiResult.Success -> if (result.status in 200..299) {
                val profile = runCatching { JSONObject(result.body) }.getOrNull()
                    ?: return AuthResult.Failure(AuthError.API_ERROR)
                establishBackendSession(auth, profile)
            } else AuthResult.Failure(AuthError.API_ERROR)
            ApiResult.NotConfigured, is ApiResult.Failure -> AuthResult.Failure(AuthError.API_ERROR)
        }
    }

    private fun establishBackendSession(auth: JSONObject, profileJson: JSONObject): AuthResult {
        val accountId = auth.optString("accountId")
        val email = auth.optString("email")
        val profileId = profileJson.optString("id")
        val sessionId = auth.optString("sessionId")
        if (accountId.isBlank() || email.isBlank() || profileId.isBlank() || sessionId.isBlank()) {
            return AuthResult.Failure(AuthError.API_ERROR)
        }
        val now = now()
        val profile = Profile(
            profile_id = profileId,
            identity_ref = accountId,
            profile_type = ProfileType.DRIVER,
            first_name = profileJson.optString("firstName"),
            last_name = profileJson.optString("lastName"),
            email = email,
            phone = profileJson.optString("phone"),
            status = AccountStatus.ACTIVE,
            created_at = profileJson.optString("createdAt").ifBlank { now },
            updated_at = profileJson.optString("updatedAt").ifBlank { now },
        )
        val account = Account(
            account_id = accountId,
            email = email,
            status = AccountStatus.ACTIVE,
            created_at = now,
            updated_at = now,
        )
        val accessExpiry = auth.optString("accessExpiresAt").ifBlank { Instant.now().plusSeconds(900).toString() }
        val session = Session(
            session_id = sessionId,
            account_id = accountId,
            refresh_token_hash = "",
            created_at = now,
            expires_at = accessExpiry,
            last_activity_at = now,
            status = SessionStatus.ACTIVE,
            revoked_at = null,
        )
        SpotGoDatabase.accounts.removeAll { it.account_id == accountId }
        SpotGoDatabase.accounts += account
        SpotGoDatabase.profiles.removeAll { it.profile_id == profileId || it.identity_ref == accountId }
        SpotGoDatabase.profiles += profile
        SpotGoDatabase.sessions.removeAll { it.account_id == accountId }
        SpotGoDatabase.sessions += session
        active_session_id = sessionId
        backend_profile = profile
        pending_backend_user = null
        backend_refresh_expires_at = auth.optString("refreshExpiresAt").takeIf(String::isNotBlank)
        FcmDeviceRegistration.registerCurrentInstallation()
        return AuthResult.Success(session, profile)
    }

    private fun isValidRegistration(
        first_name: String,
        last_name: String,
        phone: String,
        email: String,
        password: String,
        accepted_terms: Boolean,
    ): Boolean {
        if (first_name.isBlank() || last_name.isBlank() || phone.isBlank() || email.isBlank() || password.isBlank()) {
            return false
        }
        if (!EMAIL_FORMAT.matches(email)) return false
        if (!phone.matches(Regex("""^\+?[0-9][0-9\s]{6,18}$"""))) return false
        if (!isPasswordValid(password)) return false
        if (!accepted_terms) return false
        return true
    }

    /** Matches the backend password policy for sign-up and password reset. */
    fun isPasswordValid(password: String): Boolean = PASSWORD_POLICY.matches(password)

    /**
     * Shape of a valid e-mail (same character classes as the Android
     * `Patterns.EMAIL_ADDRESS` used before). It is plain Kotlin on purpose:
     * `android.util.Patterns` only exists on a device, which would make the
     * access flow impossible to exercise in local unit tests.
     */
    private val EMAIL_FORMAT = Regex(
        """^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+""" +
            """@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?""" +
            """(?:\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$""",
    )

    private val PASSWORD_POLICY = Regex(
        """^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,}$""",
    )

    private fun accountsByEmail(email: String): List<Account> =
        SpotGoDatabase.accounts.filter { it.email.equals(email, ignoreCase = true) }

    private fun profileFor(account: Account): Profile? =
        SpotGoDatabase.profiles.firstOrNull {
            it.identity_ref == account.account_id || it.email.equals(account.email, ignoreCase = true)
        }

    private fun newId(prefix: String): String =
        "$prefix-${UUID.randomUUID().toString().take(12).uppercase()}"

    private fun now(): String = Instant.now().toString()
}

private fun ApiResult.statusName(): String = when (this) {
    is ApiResult.Success -> "HTTP $status"
    is ApiResult.Failure -> "network failure (${reason})"
    ApiResult.NotConfigured -> "backend not configured"
}
