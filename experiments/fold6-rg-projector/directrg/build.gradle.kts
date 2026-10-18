plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.code2hack.eyebrowse.projector.directrg"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.code2hack.eyebrowse.projector.directrg"
        minSdk = 32
        targetSdk = 35
        versionCode = 1
        versionName = "0.0.1-pav67"
        testInstrumentationRunner = "com.code2hack.eyebrowse.projector.directrg.ProjectionInstrumentation"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies { implementation(project(":protocol")) }

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
