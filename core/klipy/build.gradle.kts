import java.util.Properties

plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// KLIPY API key. The key is a path segment of the base URL, so the whole base
// URL is a secret — it must never be committed or logged (see KlipyKeyRedactingLogger).
// Resolution order mirrors :app: -P gradle property → env var (CI) → local.properties → empty.
val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.inputStream().use(::load)
    }
val klipyApiKey =
    providers.gradleProperty("klipyApiKey").orNull?.takeIf(String::isNotEmpty)
        ?: providers.environmentVariable("KLIPY_API_KEY").orNull?.takeIf(String::isNotEmpty)
        ?: localProperties.getProperty("klipyApiKey")?.takeIf(String::isNotEmpty)
        ?: localProperties.getProperty("KLIPY_API_KEY")?.takeIf(String::isNotEmpty)
        ?: ""

android {
    namespace = "net.kikin.nubecita.core.klipy"

    defaultConfig {
        buildConfigField("String", "KLIPY_API_KEY", "\"$klipyApiKey\"")
    }

    buildFeatures.buildConfig = true

    // Mirrors `:core:preferences`'s split: the `environment` dimension swaps the
    // production KlipyBindingsModule (binds DefaultKlipyRepository) for a bench
    // parallel that binds BenchFakeKlipyRepository, so the picker is exercisable
    // offline with no key/network. Consumers without the dimension pick up
    // `production` via the library convention plugin's missingDimensionStrategy.
    flavorDimensions += "environment"
    productFlavors {
        create("production") { dimension = "environment" }
        create("bench") { dimension = "environment" }
    }
}

dependencies {
    api(project(":data:models"))

    implementation(project(":core:common"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.mockk)
}
