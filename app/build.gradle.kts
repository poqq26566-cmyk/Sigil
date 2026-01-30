plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.animeshvarma.sigil"
    compileSdk = 36 // Android 16 (Baklava)

    defaultConfig {
        applicationId = "dev.animeshvarma.sigil"
        minSdk = 26 // Android 8 (Oreo)
        targetSdk = 36 // Android 16 (Baklava)
        // Schema: Positional logic (Major*10000 + Minor*100 + Patch).
        // Ensures strictly increasing, parseable codes (Implemented in v0.4.5).
        versionCode = 405
        /* v0.5.0 Scope Split:
         * - v0.4.5 ships Profiles & Engine updates (Current).
         * - v0.5.0 defers Steganography & remaining features (Planned).
         * Context: Maintains consistent update size and monthly cadence.
         */
        versionName = "0.4.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    dependenciesInfo {
        // Disables dependency metadata when building APKs (for IzzyOnDroid/F-Droid)
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles (for Google Play)
        includeInBundle = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // --- Core Android ---
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.appcompat:appcompat:1.7.1")

    // --- Compose (UI) ---
    implementation(platform("androidx.compose:compose-bom:2025.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material3:material3:1.5.0-alpha11")

    // --- Logic & Data ---
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    // --- Crypto ---
    implementation("org.bouncycastle:bcpkix-jdk18on:1.83")

    // --- Biometric ---
    implementation("androidx.biometric:biometric:1.4.0-alpha05")

    // --- Coroutines ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation(libs.androidx.foundation)

    // --- Testing ---
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}