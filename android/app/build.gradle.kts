plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.auraride.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.auraride.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-frontend"

        // Per-developer backend URL (§ android/CLAUDE.md). Override in local.properties
        // or here; adb reverse maps localhost:8000 to the dev machine.
        buildConfigField("String", "BASE_URL", "\"http://172.16.25.165:8000/api/v1/\"")
    }

    buildTypes {
        debug {
            // Debug builds must allow cleartext HTTP for local backend (eng review OV3).
            // Cleartext is scoped in res/xml/network_security_config.xml, not global.
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
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
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Networking (REST + polling) — no WebSocket (constraint).
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.logging)

    // Device-bound biometric (§17.1)
    implementation(libs.androidx.biometric)

    // On-device camera + ML Kit (liveness + OCR), all free/no-key.
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.mlkit.face)
    implementation(libs.mlkit.text)

    // Maps — MapLibre GL Native + OpenFreeMap tiles (OSM, no key, no billing).
    implementation(libs.maplibre)

    debugImplementation(libs.androidx.ui.tooling)
}
