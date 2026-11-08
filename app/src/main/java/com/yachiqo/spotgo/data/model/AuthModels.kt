package com.yachiqo.spotgo.data.model

/**
 * In-memory mirrors of the SpotGo database tables involved in the Driver
 * access flow (US09 / US13). Field names match the database diagram
 * one-to-one (snake_case) so the future backend keeps the same contract.
 */

data class Account(
    val account_id: String,
    val email: String,
    val status: String,
    val created_at: String,
    val updated_at: String,
)

data class Credential(
    val credential_id: String,
    val account_id: String,
    val authentication_method: String,
    val secret_hash: String,
    val external_subject: String?,
    val last_used_at: String?,
    val status: String,
)

data class Session(
    val session_id: String,
    val account_id: String,
    val refresh_token_hash: String,
    val created_at: String,
    val expires_at: String,
    val last_activity_at: String,
    val status: String,
    val revoked_at: String?,
)

data class Profile(
    val profile_id: String,
    val identity_ref: String,
    val profile_type: String,
    val first_name: String,
    val last_name: String,
    val email: String,
    val phone: String,
    val status: String,
    val created_at: String,
    val updated_at: String,
)

/** Values of accounts.status / Profile.status. */
object AccountStatus {
    const val ACTIVE = "ACTIVE"
    const val INACTIVE = "INACTIVE"
}

/** Values of sessions.status. */
object SessionStatus {
    const val ACTIVE = "ACTIVE"
    const val REVOKED = "REVOKED"
    const val EXPIRED = "EXPIRED"
}

/** Values of Profile.profile_type. The Android app only serves Drivers. */
object ProfileType {
    const val DRIVER = "DRIVER"
}

/** Values of Credentials.authentication_method. */
object AuthMethod {
    const val SPOTGO_CREDENTIALS = "SPOTGO_CREDENTIALS"
    const val GOOGLE = "GOOGLE"
}
