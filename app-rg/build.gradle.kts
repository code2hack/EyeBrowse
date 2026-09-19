plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android") version "2.2.21"
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
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":core:link"))

    testImplementation("junit:junit:4.13.2")
    testImplementation(testFixtures(project(":core:link")))

    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
