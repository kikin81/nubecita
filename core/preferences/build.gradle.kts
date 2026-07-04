plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
    alias(libs.plugins.nubecita.android.flavors)
}

android {
    namespace = "net.kikin.nubecita.core.preferences"

    // Mirrors `:core:auth`'s split: the `environment` dimension swaps the
    // production UserPreferencesBindingsModule for a bench-flavor parallel
    // that binds FakeUserPreferencesRepository. Consumers without the dimension
    // pick up `production` via the missingDimensionStrategy in the library
    // convention plugin. See `bd show nubecita-crmi.6`.
    // The `environment` dimension + `production`/`bench` flavors are declared
    // by the `nubecita.android.flavors` convention plugin (applied above).
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
