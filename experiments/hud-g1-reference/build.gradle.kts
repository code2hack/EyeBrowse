plugins {
    kotlin("jvm") version "2.2.21"
    application
}
kotlin { jvmToolchain(17) }
application {
    mainClass.set("com.code2hack.eyebrowse.hudreference.HudReferenceKt")
    applicationDefaultJvmArgs = listOf("-Djava.awt.headless=true")
}
