import org.jetbrains.kotlin.gradle.dsl.JsModuleKind

plugins {
    kotlin("multiplatform") version "2.2.21"
}

kotlin {
    js(IR) {
        browser()
        binaries.executable()
        compilerOptions {
            moduleKind.set(JsModuleKind.MODULE_PLAIN)
            target.set("es2015")
            sourceMap.set(false)
        }
    }
}
