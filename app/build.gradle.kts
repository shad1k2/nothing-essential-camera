plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.shad1k2.essentialshutter"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.shad1k2.essentialshutter"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Provided by LSPosed at runtime. Note: de.robv.android.xposed:service is NOT
    // published anywhere for v82 — do not add it back.
    compileOnly("de.robv.android.xposed:api:82")
    implementation("com.google.android.material:material:1.12.0")
}
