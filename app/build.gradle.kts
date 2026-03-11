plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "net.stauende.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "net.stauende.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Google Maps API key – stored in local.properties as MAPS_API_KEY
        // and injected as a manifest placeholder at build time.
        val mapsApiKey: String = project.findProperty("MAPS_API_KEY") as? String ?: ""
        manifestPlaceholders["mapsApiKey"] = mapsApiKey
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("keystore/release.jks")
            storePassword = project.findProperty("RELEASE_STORE_PASSWORD") as? String ?: "stauende123"
            keyAlias = project.findProperty("RELEASE_KEY_ALIAS") as? String ?: "stauende_release"
            keyPassword = project.findProperty("RELEASE_KEY_PASSWORD") as? String ?: "stauende123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        // Android toolchain supports up to Java 21 language features on API 26+
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.maps)
    implementation(libs.play.services.location)
}
