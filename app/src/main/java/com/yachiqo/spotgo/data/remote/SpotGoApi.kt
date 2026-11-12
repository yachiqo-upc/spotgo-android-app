package com.yachiqo.spotgo.data.remote

import android.content.Context
import android.os.Build
import android.util.Base64
import android.util.Log
import com.yachiqo.spotgo.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Result of a SpotGo HTTP request. Non-2xx HTTP responses are still Success. */
sealed interface ApiResult {
    data class Success(val status: Int, val body: String) : ApiResult
    data class Failure(val reason: String) : ApiResult
    data object NotConfigured : ApiResult
}

/** Small synchronous REST client; call it from Dispatchers.IO, never the UI thread. */
object SpotGoApi {
    private const val ACCESS_KEY = "access_token"
    private const val REFRESH_KEY = "refresh_token"
    private const val REFRESH_PATH = "/api/v1/authentication/refresh"
    private val tokenLock = Any()

    @Volatile private var accessToken: String? = null
    @Volatile private var refreshToken: String? = null
    private var tokenStore: EncryptedTokenStore? = null

    val base_url: String
        get() {
            val testOverride = if (BuildConfig.DEBUG) {
                runCatching { java.lang.System.getProperty("spotgo.test.api.base.url") }.getOrNull()
            } else null
            return (testOverride ?: BuildConfig.SPOTGO_API_BASE_URL).trim().trimEnd('/')
        }

    fun is_configured(): Boolean = base_url.isNotEmpty()

    /** Android 17 blocks local-network connections for apps targeting API 37+. */
    fun requiresLocalNetworkPermission(): Boolean {
        if (Build.VERSION.SDK_INT < 37 || !is_configured()) return false
        val host = runCatching { URL(base_url).host.lowercase() }.getOrNull() ?: return false
        if (host == "localhost" || host.endsWith(".local") || host == "::1") return true
        val octets = host.split('.').mapNotNull(String::toIntOrNull)
        if (octets.size != 4 || octets.any { it !in 0..255 }) return false
        val (first, second) = octets
        return first == 10 || first == 127 ||
            (first == 169 && second == 254) ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168)
    }

    /** Initialize once with application context to restore encrypted session tokens. */
    fun initialize(context: Context) {
        synchronized(tokenLock) {
            if (tokenStore != null) return
            tokenStore = EncryptedTokenStore(context.applicationContext)
            accessToken = tokenStore?.read(ACCESS_KEY)
            refreshToken = tokenStore?.read(REFRESH_KEY)
        }
    }

    fun current_access_token(): String? = accessToken
    fun current_refresh_token(): String? = refreshToken

    fun set_tokens(access: String, refresh: String) {
        synchronized(tokenLock) {
            accessToken = access
            refreshToken = refresh
            tokenStore?.write(ACCESS_KEY, access)
            tokenStore?.write(REFRESH_KEY, refresh)
        }
    }

    fun clear_tokens() {
        synchronized(tokenLock) {
            accessToken = null
            refreshToken = null
            tokenStore?.clear()
        }
    }

    fun get(path: String): ApiResult = request("GET", path, null, authenticated = true)

    fun getPdf(path: String): ApiResult = request("GET", path, null, authenticated = true, accept = "application/pdf")

    fun post(path: String, jsonBody: String): ApiResult =
        request("POST", path, jsonBody, authenticated = path !in PUBLIC_POST_PATHS)

    fun put(path: String): ApiResult = request("PUT", path, null, authenticated = true)

    fun delete(path: String): ApiResult = request("DELETE", path, null, authenticated = true)

    /** Cleanup request using the session token captured before local sign-out. */
    internal fun deleteWithBearer(path: String, bearer: String): ApiResult {
        if (!is_configured()) return ApiResult.NotConfigured
        return requestOnce("DELETE", path, null, bearer)
    }

    /** Session cleanup request using a token captured before local sign-out. */
    internal fun postWithBearer(path: String, jsonBody: String, bearer: String): ApiResult {
        if (!is_configured()) return ApiResult.NotConfigured
        return requestOnce("POST", path, jsonBody, bearer)
    }

    private fun request(
        method: String,
        path: String,
        jsonBody: String?,
        authenticated: Boolean,
        accept: String = "application/json",
    ): ApiResult {
        if (!is_configured()) return ApiResult.NotConfigured
        val first = requestOnce(method, path, jsonBody, if (authenticated) accessToken else null, accept)
        if (authenticated && first is ApiResult.Success && first.status == 401 && path != REFRESH_PATH) {
            if (refreshSession()) {
                return requestOnce(method, path, jsonBody, accessToken, accept)
            }
        }
        return first
    }

    private fun refreshSession(): Boolean = synchronized(tokenLock) {
        val refresh = refreshToken ?: return@synchronized false
        val response = requestOnce(
            "POST",
            REFRESH_PATH,
            "{\"refreshToken\":${quote(refresh)}}",
            bearer = null,
        )
        if (response !is ApiResult.Success || response.status !in 200..299) {
            clear_tokens()
            return@synchronized false
        }
        runCatching {
            val json = JSONObject(response.body)
            val access = json.getString("accessToken")
            val nextRefresh = json.getString("refreshToken")
            accessToken = access
            refreshToken = nextRefresh
            tokenStore?.write(ACCESS_KEY, access)
            tokenStore?.write(REFRESH_KEY, nextRefresh)
        }.getOrElse {
            clear_tokens()
            return@synchronized false
        }
        true
    }

    private fun requestOnce(method: String, path: String, jsonBody: String?, bearer: String?, accept: String = "application/json"): ApiResult {
        var phase = "opening connection"
        return try {
            val connection = URL(base_url + path).openConnection() as HttpURLConnection
            try {
                phase = "configuring request"
                connection.requestMethod = method
                connection.connectTimeout = 8_000
                connection.readTimeout = 8_000
                connection.setRequestProperty("Accept", accept)
                bearer?.takeIf(String::isNotBlank)?.let {
                    connection.setRequestProperty("Authorization", "Bearer $it")
                }
                if (jsonBody != null) {
                    phase = "writing request body"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
                }
                phase = "waiting for response status"
                val status = connection.responseCode
                phase = "reading response body"
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
                val charset = if (accept == "application/pdf") Charsets.ISO_8859_1 else Charsets.UTF_8
                ApiResult.Success(status, String(bytes, charset))
            } finally {
                connection.disconnect()
            }
        } catch (error: Exception) {
            if (BuildConfig.DEBUG) {
                Log.w("SpotGoApi", "$method $path failed while $phase: ${error.javaClass.simpleName}")
            }
            ApiResult.Failure(error.javaClass.simpleName)
        }
    }

    /** JSON-escape a string safely, including control characters. */
    fun quote(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (char < ' ') append("\\u%04x".format(char.code)) else append(char)
            }
        }
        append('"')
    }

    private val PUBLIC_POST_PATHS = setOf(
        "/api/v1/authentication/sign-in",
        "/api/v1/authentication/google",
        "/api/v1/authentication/sign-up",
        REFRESH_PATH,
        "/api/v1/authentication/password-reset/request",
        "/api/v1/authentication/password-reset/confirm",
    )
}

/** AES/GCM token storage backed by Android Keystore, with ciphertext in app-private preferences. */
private class EncryptedTokenStore(context: Context) {
    private val preferences = context.getSharedPreferences("spotgo_session", Context.MODE_PRIVATE)
    private val alias = "spotgo.session.tokens.v1"

    fun write(name: String, value: String) {
        runCatching {
            val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, key())
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val packed = cipher.iv + encrypted
            preferences.edit().putString(name, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
        }
    }

    fun read(name: String): String? = runCatching {
        val packed = Base64.decode(preferences.getString(name, null), Base64.NO_WRAP)
        require(packed.size > GCM_IV_BYTES)
        val iv = packed.copyOfRange(0, GCM_IV_BYTES)
        val encrypted = packed.copyOfRange(GCM_IV_BYTES, packed.size)
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, key(), javax.crypto.spec.GCMParameterSpec(128, iv))
        String(cipher.doFinal(encrypted), Charsets.UTF_8)
    }.getOrNull()

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun key(): java.security.Key {
        val keyStore = java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(alias, null) as? java.security.Key)?.let { return it }
        val generator = javax.crypto.KeyGenerator.getInstance(
            android.security.keystore.KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore",
        )
        generator.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                alias,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val GCM_IV_BYTES = 12
    }
}
