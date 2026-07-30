plugins {
    kotlin("jvm") version "2.0.21"
}

kotlin {
    jvmToolchain(21)
}

// No repositories {} block here on purpose: when :core is configured as
// part of the root multi-project build (e.g. because :app depends on it),
// the root's FAIL_ON_PROJECT_REPOS policy forbids project-level repository
// declarations. Repositories come from the root's dependencyResolutionManagement
// in that case, and from this module's own settings.gradle.kts when :core
// is built standalone (see README / CI usage).

dependencies {
    // api, not implementation: RouteCalculator's public constructor takes an
    // OkHttpClient parameter, so consumers (e.g. :app) need this type on
    // their own compile classpath too, not just :core's internal one.
    api("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
