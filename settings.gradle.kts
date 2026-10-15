pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "EyeBrowse"

include(":core:browser")
include(":core:link")
include(":app-phone")
include(":app-rg")
include(":renderer-editor")
include(":experiments:rg-local-probe")
include(":experiments:rg-local-probe:fixture")
include(":experiments:hud-g1-reference")

// Opt-in spike: the verified upstream source stays outside the repository.
if (providers.gradleProperty("chromiumLoaderSource").isPresent) {
    include(":experiments:app-local-chromium:loader")
    include(":experiments:app-local-chromium:fixture")
}
