import java.security.MessageDigest

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android") version "2.2.21"
}

val upstream = file(providers.gradleProperty("chromiumLoaderSource").get())
val pins = file("source.sha256").readLines().filter { it.isNotBlank() }.associate {
    it.substring(66) to it.substring(0, 64)
}
val verifyUpstream by tasks.registering {
    doLast {
        val actual = fileTree(upstream.resolve("src/main")).files
            .map { it.relativeTo(upstream).invariantSeparatorsPath }.toSet()
        check(actual == pins.keys) { "Loader source inventory differs from pinned upstream" }
        pins.forEach { (name, expected) ->
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(upstream.resolve(name).readBytes()).joinToString("") { "%02x".format(it) }
            check(digest == expected) { "Loader source digest mismatch: $name" }
        }
    }
}

val vendorSources = layout.buildDirectory.dir("verified-upstream-java")
val prepareUpstream by tasks.registering(Sync::class) {
    dependsOn(verifyUpstream)
    from(upstream.resolve("src/main/java"))
    exclude("**/ActivityManagerHook.java", "**/stub/StubSandboxedProcessService*.java")
    into(vendorSources)
}

android {
    namespace = "com.norman.webviewup.lib"
    compileSdk = 35
    defaultConfig { minSdk = 21 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets.getByName("main") {
        manifest.srcFile(upstream.resolve("src/main/AndroidManifest.xml"))
        // Kotlin also reads Java declarations: stage the verified subset for both compilers.
        java.setSrcDirs(listOf(vendorSources))
    }
    kotlinOptions { jvmTarget = "17" }
}

tasks.named("preBuild").configure { dependsOn(prepareUpstream) }
dependencies {
    compileOnly("androidx.annotation:annotation:1.8.2")
    testImplementation("junit:junit:4.13.2")
}
