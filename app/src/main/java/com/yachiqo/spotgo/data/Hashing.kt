package com.yachiqo.spotgo.data

import java.security.MessageDigest

/** Demo-only hashing. The backend will replace this with its own secret hashing. */
fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}
