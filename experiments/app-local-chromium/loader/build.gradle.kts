import java.security.MessageDigest

plugins { id("com.android.library") }

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

android {
    namespace = "com.norman.webviewup.lib"
    compileSdk = 35
    defaultConfig { minSdk = 21 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    sourceSets.getByName("main") {
        manifest.srcFile(upstream.resolve("src/main/AndroidManifest.xml"))
        java.setSrcDirs(listOf(upstream.resolve("src/main/java")))
    }
}

tasks.named("preBuild").configure { dependsOn(verifyUpstream) }
dependencies { compileOnly("androidx.annotation:annotation:1.8.2") }
