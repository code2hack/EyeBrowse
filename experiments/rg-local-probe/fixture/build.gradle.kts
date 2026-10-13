plugins {
    kotlin("jvm")
    application
}
kotlin { jvmToolchain(17) }
application { mainClass.set("com.code2hack.eyebrowse.rgfixture.FixtureServerKt") }
