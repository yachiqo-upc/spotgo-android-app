package com.yachiqo.spotgo.presentation.login

import android.content.Context
import android.util.Base64
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.yachiqo.spotgo.BuildConfig
import org.json.JSONObject

/** Outcome of the real Google sign-in (Pendiente 2 — Google Identity). */
sealed interface GoogleSignInResult {
    /** Google returned a signed id token for [email]. */
    data class Success(
        val email: String,
        val display_name: String?,
        val id_token: String,
    ) : GoogleSignInResult

    /** The user dismissed the account sheet. */
    data object Cancelled : GoogleSignInResult

    /** No token (no account on the device, network error, wrong client id…). */
    data class Failed(val reason: String) : GoogleSignInResult

    /** SPOTGO_GOOGLE_WEB_CLIENT_ID is empty: keep the simulated chooser. */
    data object NotConfigured : GoogleSignInResult
}

/** `sub` / `email` / `name` claims read from the payload of the id token. */
private data class IdTokenClaims(
    val sub: String = "",
    val email: String = "",
    val name: String = "",
)

/**
 * Google Identity (Sign-In through Credential Manager).
 *
 * Everything is wired and runs as soon as SPOTGO_GOOGLE_WEB_CLIENT_ID is set
 * in local.properties; while it is empty the Login screen keeps the
 * simulated account chooser of the mockups (GoogleAccountSheet).
 *
 * NOTE: googleid 1.1.1 exposes displayName / givenName / familyName / id but
 * not the e-mail, so `email` is read from the payload of the id token (the
 * same place the backend will read it after verifying the signature).
 *
 * TODO(backend): the id token must be verified server-side (signature,
 * issuer, audience) before a session is issued — US09 scenario 3.
 */
object GoogleIdentity {

    /** True when a Google web client id has been configured for this build. */
    fun isConfigured(): Boolean = BuildConfig.SPOTGO_GOOGLE_WEB_CLIENT_ID.isNotBlank()

    /** Opens the Google account sheet and returns the id token of the user. */
    suspend fun signIn(context: Context): GoogleSignInResult {
        if (!isConfigured()) return GoogleSignInResult.NotConfigured
        return try {
            val option = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(BuildConfig.SPOTGO_GOOGLE_WEB_CLIENT_ID)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()
            val response = CredentialManager.create(context).getCredential(context, request)
            val credential = response.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val google = GoogleIdTokenCredential.createFrom(credential.data)
                val claims = claimsOf(google.idToken)
                // Blank e-mail (scope not granted) falls back to the demo
                // account inside AuthRepository.sign_in_with_google().
                GoogleSignInResult.Success(
                    email = claims.email,
                    display_name = claims.name.ifBlank { google.displayName },
                    id_token = google.idToken,
                )
            } else {
                GoogleSignInResult.Failed("unexpected-credential")
            }
        } catch (error: GetCredentialCancellationException) {
            GoogleSignInResult.Cancelled
        } catch (error: Exception) {
            GoogleSignInResult.Failed(error.javaClass.simpleName)
        }
    }

    /** `sub` claim of the id token (the stable Google subject stored in
     *  credentials.external_subject). Falls back to "" when it cannot be read. */
    fun subjectOf(idToken: String): String = claimsOf(idToken).sub

    /** Reads `sub` / `email` / `name` from the (unverified) payload of the JWT. */
    private fun claimsOf(idToken: String): IdTokenClaims = try {
        val payload = idToken.split(".")[1]
        val json = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
        val claims = JSONObject(json)
        IdTokenClaims(
            sub = claims.optString("sub"),
            email = claims.optString("email"),
            name = claims.optString("name"),
        )
    } catch (error: Exception) {
        IdTokenClaims()
    }
}
