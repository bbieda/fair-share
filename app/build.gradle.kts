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
        // The call goes through our server (api.php?action=gemini), which holds the key: no key is ever built into
        // the APK, because anyone with the APK could unpack it. These models must be allowed in the server config.
        buildConfigField("String", "GEMINI_MODELS", "\"gemini-3.5-flash-lite,gemini-3.1-flash-lite\"")
        // Cloud sync: address of server/api/api.php (docs/chmura.md). Set fairshare.apiUrl in gradle.properties
        // (shared by the team) or in local.properties (your own test server); empty = the app cannot log in.
        buildConfigField("String", "API_BASE_URL", "\"${apiBaseUrl()}\"")
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

/** URL of the FairShare API: local.properties `fairshare.apiUrl`, else the gradle property of the same name. */
fun apiBaseUrl(): String {
    val local = Properties()
    rootProject.file("local.properties").takeIf { it.isFile }?.inputStream()?.use { local.load(it) }
    val url = local.getProperty("fairshare.apiUrl")?.trim()?.takeIf { it.isNotEmpty() }
        ?: (findProperty("fairshare.apiUrl") as String?)?.trim().orEmpty()
    // nothing that could break out of the generated Java string
    return url.filter { it.code in 0x21..0x7e && it != '"' && it != '\\' }
}
