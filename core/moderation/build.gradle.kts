plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
}

android {
    namespace = "net.kikin.nubecita.core.moderation"

    // Mirrors :core:feeds — the `environment` split lets a future bench
    // parallel bind a deterministic fake preferences repository.
    flavorDimensions += "environment"
    productFlavors {
        create("production") { dimension = "environment" }
        create("bench") { dimension = "environment" }
    }
}

dependencies {
    implementation(project(":core:auth"))
    implementation(project(":core:common"))
    implementation(libs.atproto.models)
    implementation(libs.atproto.runtime)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.atproto.runtime)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
