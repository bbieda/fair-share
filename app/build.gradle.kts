import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.fairshare"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fairshare"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"

        // Receipt scanner, AI step: Gemini reads the photo only when the items do not add up to the total.
        // Key from local.properties (gemini.apiKey=...) or the GEMINI_API_KEY environment variable; without it the
        // scanner works with ML Kit + rules only. The key ends up inside the APK: OK for a test build on your own
        // phone, never for a published app (there the call must go through our backend).
        buildConfigField("String", "GEMINI_API_KEY", "\"${geminiApiKey()}\"")
        buildConfigField("String", "GEMINI_MODELS", "\"gemini-3.5-flash-lite,gemini-3.1-flash-lite\"")
        // Keep every scanned photo with its reading on the phone (scanner/ScanArchive.kt): debug builds only
        buildConfigField("boolean", "SAVE_SCANS", "false")
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("boolean", "SAVE_SCANS", "true")
        }
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

ksp {
    // Room writes the database schema (JSON) here, so every DB version is versioned in git
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.mlkit.text.recognition)
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.ui.tooling)
}

/** Gemini API key for the receipt scanner: local.properties `gemini.apiKey`, else env GEMINI_API_KEY, else empty. */
fun geminiApiKey(): String {
    val local = Properties()
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { local.load(it) }
    val key = local.getProperty("gemini.apiKey")?.trim()?.takeIf { it.isNotEmpty() }
        ?: System.getenv("GEMINI_API_KEY")?.trim().orEmpty()
    // only characters an API key has, so nothing can break out of the generated Java string
    return key.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
}
