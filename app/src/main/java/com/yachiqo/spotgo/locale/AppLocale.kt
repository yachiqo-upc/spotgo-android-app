package com.yachiqo.spotgo.locale

import android.app.Activity
import android.app.Application
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/** Keeps the app language to the two locales offered by SpotGo Driver. */
object AppLocale {
    const val ENGLISH_US = "en-US"
    const val SPANISH_LATAM = "es-419"

    private const val PREFERENCES = "spotgo_language"
    private const val LANGUAGE_KEY = "language_tag"

    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base

        val tag = base.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getString(LANGUAGE_KEY, ENGLISH_US) ?: ENGLISH_US
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        return base.createConfigurationContext(configuration)
    }

    fun ensureDefault(application: Application) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = application.getSystemService(LocaleManager::class.java)
            if (localeManager.applicationLocales.isEmpty) {
                localeManager.applicationLocales = LocaleList.forLanguageTags(ENGLISH_US)
            }
        } else {
            val preferences = application.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            if (!preferences.contains(LANGUAGE_KEY)) {
                preferences.edit().putString(LANGUAGE_KEY, ENGLISH_US).apply()
            }
        }
        Locale.setDefault(Locale.forLanguageTag(currentLanguageTag(application)))
    }

    fun currentLanguageTag(context: Context): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales
                .toLanguageTags().takeIf(String::isNotBlank) ?: ENGLISH_US
        } else {
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(LANGUAGE_KEY, ENGLISH_US) ?: ENGLISH_US
        }

    fun isSpanish(context: Context): Boolean =
        currentLanguageTag(context).startsWith("es", ignoreCase = true)

    fun select(activity: Activity, languageTag: String) {
        require(languageTag == ENGLISH_US || languageTag == SPANISH_LATAM)
        Locale.setDefault(Locale.forLanguageTag(languageTag))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(languageTag)
        } else {
            activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .edit().putString(LANGUAGE_KEY, languageTag).apply()
            activity.recreate()
        }
    }
}

class SpotGoApplication : Application() {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLocale.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        AppLocale.ensureDefault(this)
    }
}
