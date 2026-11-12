package com.yachiqo.spotgo.data.remote

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.yachiqo.spotgo.BuildConfig
import com.yachiqo.spotgo.R
import org.json.JSONObject
import java.util.concurrent.Executors

/** Optional Firebase client setup and authenticated device registration. */
object FcmDeviceRegistration {
    private const val DEVICE_PATH = "/api/v1/notifications/devices"
    private const val PREFERENCES = "spotgo_fcm"
    private const val DEVICE_ID_KEY = "device_token_id"
    private const val INSTALLATION_ID_KEY = "firebase_installation_id"
    private const val CHANNEL_ID = "spotgo_updates"

    private val executor = Executors.newSingleThreadExecutor()
    @Volatile private var applicationContext: Context? = null
    @Volatile private var firebaseApp: FirebaseApp? = null

    fun isEnabled(): Boolean = BuildConfig.FCM_ENABLED && hasFirebaseConfiguration()

    /** Safe to call on every app start; empty config leaves Firebase disabled. */
    fun initialize(context: Context) {
        val appContext = context.applicationContext
        applicationContext = appContext
        SpotGoApi.initialize(appContext)
        if (!isEnabled()) return

        val app = getOrInitializeFirebaseApp(appContext) ?: return
        FirebaseMessaging.getInstance().register()
    }

    /** Register after a password or Google session has been established. */
    fun registerCurrentInstallation() {
        if (!isEnabled()) return
        val context = applicationContext ?: return
        val installationId = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(INSTALLATION_ID_KEY, null)
        if (!installationId.isNullOrBlank()) {
            registerInstallationIfAuthenticated(context, installationId)
        } else {
            val app = firebaseApp ?: getOrInitializeFirebaseApp(context) ?: return
            FirebaseMessaging.getInstance().register()
        }
    }

    /** Firebase callback for a registered or refreshed Firebase Installation ID. */
    fun onRegistered(context: Context, installationId: String) {
        if (!isEnabled() || installationId.isBlank()) return
        val appContext = context.applicationContext
        applicationContext = appContext
        SpotGoApi.initialize(appContext)
        appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putString(INSTALLATION_ID_KEY, installationId).apply()
        registerInstallationIfAuthenticated(appContext, installationId)
    }

    /** Remove the device before revoking the backend session; local logout stays immediate. */
    fun signOut(accessToken: String?) {
        val bearer = accessToken?.takeIf(String::isNotBlank) ?: return
        val context = applicationContext
        executor.execute {
            val deviceId = context?.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                ?.getString(DEVICE_ID_KEY, null)
            val removed = if (!deviceId.isNullOrBlank()) {
                when (val response = SpotGoApi.deleteWithBearer("$DEVICE_PATH/$deviceId", bearer)) {
                    is ApiResult.Success -> response.status in 200..299 || response.status == 404
                    ApiResult.NotConfigured, is ApiResult.Failure -> false
                }
            } else true

            SpotGoApi.postWithBearer("/api/v1/authentication/sign-out", "{}", bearer)
            if (removed) {
                context?.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                    ?.edit()?.remove(DEVICE_ID_KEY)?.apply()
            }
        }
    }

    fun showNotification(context: Context, title: String?, body: String?) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.fcm_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_spotgo)
            .setContentTitle(title?.takeIf(String::isNotBlank) ?: context.getString(R.string.fcm_default_title))
            .setContentText(body?.takeIf(String::isNotBlank) ?: context.getString(R.string.fcm_default_body))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    body?.takeIf(String::isNotBlank) ?: context.getString(R.string.fcm_default_body),
                ),
            )
            .setAutoCancel(true)
            .build()
        manager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }

    private fun registerInstallationIfAuthenticated(context: Context, installationId: String) {
        if (installationId.isBlank() || !SpotGoApi.is_configured() ||
            SpotGoApi.current_access_token().isNullOrBlank()
        ) return

        executor.execute {
            val request = JSONObject().put("installationId", installationId).put("platform", "ANDROID").toString()
            val response = SpotGoApi.post(DEVICE_PATH, request)
            if (response is ApiResult.Success && response.status in 200..299) {
                val deviceId = runCatching { JSONObject(response.body).optString("deviceTokenId") }
                    .getOrNull()?.takeIf(String::isNotBlank) ?: return@execute
                context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                    .edit().putString(DEVICE_ID_KEY, deviceId).apply()
            }
        }
    }

    private fun getOrInitializeFirebaseApp(context: Context): FirebaseApp? {
        firebaseApp?.let { return it }
        val existing = runCatching { FirebaseApp.getInstance() }.getOrNull()
        if (existing != null) {
            firebaseApp = existing
            return existing
        }
        val options = FirebaseOptions.Builder()
            .setProjectId(BuildConfig.FCM_PROJECT_ID)
            .setApplicationId(BuildConfig.FCM_APPLICATION_ID)
            .setApiKey(BuildConfig.FCM_API_KEY)
            .setGcmSenderId(BuildConfig.FCM_SENDER_ID)
            .build()
        return runCatching { FirebaseApp.initializeApp(context, options) }
            .getOrNull()
            ?.also { firebaseApp = it }
    }

    private fun hasFirebaseConfiguration(): Boolean =
        BuildConfig.FCM_PROJECT_ID.isNotBlank() &&
            BuildConfig.FCM_APPLICATION_ID.isNotBlank() &&
            BuildConfig.FCM_API_KEY.isNotBlank() &&
            BuildConfig.FCM_SENDER_ID.isNotBlank()
}
