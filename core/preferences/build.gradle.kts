plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
}

android {
    namespace = "net.kikin.nubecita.core.preferences"

    // Mirrors `:core:auth`'s split: the `environment` dimension swaps the
    // production UserPreferencesBindingsModule for a benchmark-flavor parallel
    // that binds FakeUserPreferencesRepository. Consumers without the dimension
    // pick up `production` via the missingDimensionStrategy in the library
    // convention plugin. See `bd show nubecita-crmi.6`.
    flavorDimensions += "environment"
    productFlavors {
        create("production") { dimension = "environment" }
        create("benchmark") { dimension = "environment" }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
