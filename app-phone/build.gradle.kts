plugins {
    id("com.android.application")
}

android {
    namespace = "com.code2hack.eyebrowse.phone"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.code2hack.eyebrowse.phone"
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
}

dependencies {
    implementation(project(":core:browser"))
    implementation("androidx.activity:activity:1.9.3")
    implementation("androidx.core:core:1.15.0")

    constraints {
        // androidx.lifecycle 2.6.2 -> kotlinx-coroutines-android 1.6.4 still requests the split
        // kotlin-stdlib-jdk7/jdk8 artifacts, while androidx.core requests the merged
        // kotlin-stdlib 1.8.22. Aligning the jdk7/jdk8 shims to the same 1.8.22 removes the
        // duplicate-class packaging failure without changing any pinned product dependency.
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22")
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.8.22")
    }

    testImplementation("junit:junit:4.13.2")

    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.test.espresso:espresso-web:3.6.1")
}
