plugins {
    id("com.android.application")
}

android {
    namespace = "com.code2hack.eyebrowse.lockprobe"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.code2hack.eyebrowse.lockprobe"
        minSdk = 32
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}
