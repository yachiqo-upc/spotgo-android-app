package com.yachiqo.spotgo.data.remote

import com.yachiqo.spotgo.BuildConfig
import java.net.HttpURLConnection
import java.net.URL

/** Outcome of a call to the future SpotGo REST API. */
sealed interface ApiResult {
    /** HTTP response received (2xx must be checked by the caller). */
    data class Success(val status: Int, val body: String) : ApiResult
    /** Transport error (no connectivity, timeout, malformed URL…). */
    data class Failure(val reason: String) : ApiResult
    /** SPOTGO_API_BASE_URL is empty: the backend does not exist yet. */
    data object NotConfigured : ApiResult
}

/**
 * Minimal HTTP client for the future SpotGo backend.
 *
 * Every endpoint is a placeholder until the API exists: the path is taken
 * from 41-sprint-1.md when it is documented and marked TODO(backend) when it
 * is not. Nothing here is called while SPOTGO_API_BASE_URL is empty, so the
 * current behaviour of the app never changes by accident.
 *
 * TODO(backend): replace with the real client (Retrofit/OkHttp + auth
 * headers) once the contract is final.
 *
 * NOTE: all calls are blocking — run them on Dispatchers.IO.
 */
object SpotGoApi {

    /** Base URL configured in local.properties ("" while there is no backend). */
    val base_url: String
        get() = BuildConfig.SPOTGO_API_BASE_URL.trim().trimEnd('/')

    /** True when the backend has been configured for this build. */
    fun is_configured(): Boolean = base_url.isNotEmpty()

    /** POST `path` with a JSON body; blocking, call it off the main thread. */
    fun post(path: String, jsonBody: String): ApiResult = request("POST", path, jsonBody)

    private fun request(method: String, path: String, jsonBody: String): ApiResult {
        if (!is_configured()) return ApiResult.NotConfigured
        return try {
            val connection = URL(base_url + path).openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            connection.disconnect()
            ApiResult.Success(status, body)
        } catch (error: Exception) {
            ApiResult.Failure(error.javaClass.simpleName)
        }
    }

    /** Escapes and quotes a string so it can be embedded in a JSON body. */
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
}
