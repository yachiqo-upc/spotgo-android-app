import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// ---------------------------------------------------------------------------
// SpotGo integration keys — read from local.properties (never committed).
// Fill them in when they exist; every empty value keeps the feature in
// "backend not configured" mode and the app keeps working:
//   SPOTGO_API_BASE_URL        e.g. https://api.spotgo.pe   (Pendientes 3 y 5)
//   SPOTGO_MAPS_API_KEY        Google Maps key              (Pendiente 2)
//   SPOTGO_GOOGLE_WEB_CLIENT_ID Google Sign-In web client   (Pendiente 2)
// ---------------------------------------------------------------------------
val spotGoLocalProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun spotGoBuildConfigValue(key: String): String =
    (spotGoLocalProperties.getProperty(key) ?: "")
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
        buildConfigField("String", "SPOTGO_MAPS_API_KEY", "\"${spotGoBuildConfigValue("SPOTGO_MAPS_API_KEY")}\"")
        buildConfigField("String", "SPOTGO_GOOGLE_WEB_CLIENT_ID", "\"${spotGoBuildConfigValue("SPOTGO_GOOGLE_WEB_CLIENT_ID")}\"")
        // AndroidManifest reads the Maps key from here (com.google.android.geo.API_KEY).
        manifestPlaceholders["SPOTGO_MAPS_API_KEY"] = spotGoBuildConfigValue("SPOTGO_MAPS_API_KEY")
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

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // Pendiente 2 — Google Maps (Explore) and Google Identity (Sign-In).
    implementation(libs.google.play.services.maps)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}