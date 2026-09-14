plugins {
    id("com.android.application")
}

android {
    namespace = "com.code2hack.eyebrowse.rg"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.code2hack.eyebrowse.rg"
        minSdk = 32
        targetSdk = 35
        versionCode = 1
        versionName = "0.0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
