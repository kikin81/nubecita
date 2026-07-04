plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
    alias(libs.plugins.nubecita.android.flavors)
}

android {
    namespace = "net.kikin.nubecita.core.update"

    // The `environment` flavor dimension splits the production InAppUpdateController
    // (real Google Play in-app updates) from a bench no-op, so keyless /
    // macrobenchmark builds make zero Play calls and never prompt. Mirrors `:core:review`.
    // The `environment` dimension + `production`/`bench` flavors are declared
    // by the `nubecita.android.flavors` convention plugin (applied above).
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.google.play.app.update)
    implementation(libs.google.play.app.update.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)

    androidTestImplementation(project(":core:testing-android"))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
