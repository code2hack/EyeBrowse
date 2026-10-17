plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.code2hack.eyebrowse.projector.hostprobe"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.code2hack.eyebrowse.projector.hostprobe"
        minSdk = 32
        targetSdk = 35
        versionCode = 1
        versionName = "0.0.1-pav66"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
