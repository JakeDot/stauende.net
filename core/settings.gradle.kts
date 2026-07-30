// Lets :core be built standalone (e.g. `gradle --project-dir core test`)
// without pulling in the root project's Android-specific settings.
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "StauEnde-core"
