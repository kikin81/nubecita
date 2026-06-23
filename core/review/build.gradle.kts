plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
}

android {
    namespace = "net.kikin.nubecita.core.review"

    // The `environment` flavor dimension splits the production `ReviewManager`
    // (real Google Play in-app review) from a bench no-op, so keyless /
    // macrobenchmark builds make zero Play calls and never prompt. Consumers
    // resolve the `production` variant by default via the
    // `missingDimensionStrategy` plumbing in `AndroidLibraryConventionPlugin`;
    // the bench app flavor consumes the matching variant. Mirrors `:core:actors`.
    flavorDimensions += "environment"
    productFlavors {
        create("production") { dimension = "environment" }
        create("bench") { dimension = "environment" }
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.google.play.review)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.datetime)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.google.play.review.ktx)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
