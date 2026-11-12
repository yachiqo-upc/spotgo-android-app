import java.util.Properties
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// ---------------------------------------------------------------------------
// SpotGo integration settings — environment variables override local.properties.
// Keep values out of source control. Empty optional settings keep the feature in
// "backend not configured" mode and the app keeps working:
//   SPOTGO_API_BASE_URL          e.g. http://10.0.2.2:18080
//   SPOTGO_GOOGLE_WEB_CLIENT_ID  Google Sign-In web client
//   RECEIPT_EMAIL_ENABLED        set true only for a deployment that sends receipts
//   FCM_ENABLED                  enable only when the Firebase client app is configured
//   FCM_PROJECT_ID, FCM_APPLICATION_ID, FCM_API_KEY, FCM_SENDER_ID
// ---------------------------------------------------------------------------
val spotGoLocalProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun spotGoBuildConfigValue(key: String): String =
    (System.getenv(key) ?: spotGoLocalProperties.getProperty(key) ?: "")
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

android {
    namespace = "com.yachiqo.spotgo"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.yachiqo.spotgo"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SPOTGO_API_BASE_URL", "\"${spotGoBuildConfigValue("SPOTGO_API_BASE_URL")}\"")
        buildConfigField("String", "SPOTGO_GOOGLE_WEB_CLIENT_ID", "\"${spotGoBuildConfigValue("SPOTGO_GOOGLE_WEB_CLIENT_ID")}\"")
        buildConfigField("boolean", "FCM_ENABLED", spotGoBuildConfigValue("FCM_ENABLED").toBoolean().toString())
        buildConfigField("String", "FCM_PROJECT_ID", "\"${spotGoBuildConfigValue("FCM_PROJECT_ID")}\"")
        buildConfigField("String", "FCM_APPLICATION_ID", "\"${spotGoBuildConfigValue("FCM_APPLICATION_ID")}\"")
        buildConfigField("String", "FCM_API_KEY", "\"${spotGoBuildConfigValue("FCM_API_KEY")}\"")
        buildConfigField("String", "FCM_SENDER_ID", "\"${spotGoBuildConfigValue("FCM_SENDER_ID")}\"")
        val receiptEmailEnabled = spotGoBuildConfigValue("RECEIPT_EMAIL_ENABLED").ifBlank { "false" }
        buildConfigField("boolean", "RECEIPT_EMAIL_ENABLED", receiptEmailEnabled.toBoolean().toString())
        manifestPlaceholders["SPOTGO_USES_CLEARTEXT_TRAFFIC"] =
            spotGoBuildConfigValue("SPOTGO_API_BASE_URL").trim().startsWith("http://").toString()
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

tasks.withType<Test>().configureEach {
    // Unit tests exercise local/demo repositories and must stay isolated from developer credentials.
    systemProperty("spotgo.test.api.base.url", "")
    systemProperty("spotgo.test.google.web.client.id", "")
}

dependencies {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // Integrated OpenStreetMap map without a Google Maps Platform key/card.
    implementation(libs.maplibre.android)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    testImplementation(libs.junit)
    // Use a real JSON implementation in local JVM tests; android.jar stubs org.json methods.
    testImplementation("org.json:json:20260814")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
