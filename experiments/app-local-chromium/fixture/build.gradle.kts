plugins {
    kotlin("jvm") version "2.2.21"
    application
}
kotlin { jvmToolchain(17) }
application { mainClass.set("com.code2hack.eyebrowse.chromiumfixture.RepairFixtureKt") }
