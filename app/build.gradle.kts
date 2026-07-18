plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.animeshvarma.sigil"
    compileSdk = 36 // Android 16 (Baklava)

    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("SIGIL_KEYSTORE_PATH")
            if (!storeFilePath.isNullOrBlank()) {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("SIGIL_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SIGIL_KEY_ALIAS")
                keyPassword = System.getenv("SIGIL_KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        applicationId = "dev.animeshvarma.sigil"
        minSdk = 26 // Android 8 (Oreo)
        targetSdk = 36 // Android 16 (Baklava)
        versionCode = 500
        versionName = "0.5.0"

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
            if (!System.getenv("SIGIL_KEYSTORE_PATH").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    dependenciesInfo {
        includeInApk = false
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
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.10.0")
    implementation("androidx.activity:activity-compose:1.12.2")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.documentfile:documentfile:1.1.0")

    implementation(platform("androidx.compose:compose-bom:2025.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material3:material3:1.5.0-alpha11")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    implementation("org.bouncycastle:bcpkix-jdk18on:1.83")

    implementation("androidx.biometric:biometric:1.4.0-alpha05")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation(libs.androidx.foundation)

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.12.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
