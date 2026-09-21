plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    `java-test-fixtures`
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

dependencies {
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    testImplementation("junit:junit:4.13.2")
    // Self-signed test certificates for the TLS engine tests only; never a product dependency.
    testImplementation("org.bouncycastle:bcpkix-jdk18on:1.81")
    testFixturesImplementation("org.bouncycastle:bcpkix-jdk18on:1.81")
    testFixturesImplementation(kotlin("stdlib"))
}
