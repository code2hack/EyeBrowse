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
        versionCode = 3
        versionName = "0.0.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        if (providers.gradleProperty("chromiumLoaderSource").isPresent) {
            create("chromium") {
                initWith(getByName("debug"))
                applicationIdSuffix = ".chromium"
                versionNameSuffix = "-chromium-spike"
                matchingFallbacks.add("debug")
                resValue("string", "app_name", "EyeBrowse Chromium experiment")
            }
            testBuildType = "chromium"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging.jniLibs {
        useLegacyPackaging = true // Execute the unmodified Android CLI from the installer-owned directory.
        keepDebugSymbols.add("**/libmihomo.so")
    }

    // Test-only evidence policy, compiled for both its JVM controls and device observer.
    sourceSets.getByName("test").java.srcDir("src/testShared/java")
    sourceSets.getByName("test").kotlin.srcDir("src/testShared/java")
    sourceSets.getByName("androidTest").java.srcDir("src/testShared/java")
    sourceSets.getByName("androidTest").kotlin.srcDir("src/testShared/java")
    sourceSets.getByName("test").resources.srcDir("src/testShared/resources")
    sourceSets.getByName("androidTest").assets.srcDir("src/testShared/resources")
}

dependencies {
    implementation(project(":core:browser"))
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.yaml:snakeyaml:2.5")
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")

    if (providers.gradleProperty("chromiumLoaderSource").isPresent) {
        add("chromiumImplementation", project(":experiments:app-local-chromium:loader"))
        // Preserve existing app-scoped fixture trust only in this debug-derived experiment.
        android.sourceSets.getByName("chromium").res.srcDir("src/debug/res")
        android.sourceSets.getByName("chromium").java.srcDir("src/debug/java")
    }
}
