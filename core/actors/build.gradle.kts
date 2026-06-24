plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
}

android {
    namespace = "net.kikin.nubecita.core.actors"

    // The `environment` flavor dimension splits the production
    // `ActorRepository` (network-backed `DefaultActorRepository`) from a
    // bench parallel that binds a deterministic fake
    // (`BenchFakeActorRepository`), so the search typeahead dropdown and the
    // People results tab have people on the bench build. Consumers resolve
    // the `production` variant by default via the `missingDimensionStrategy`
    // plumbing in `AndroidLibraryConventionPlugin`; the bench app flavor
    // consumes the matching variant. Mirrors `:core:posts`.
    flavorDimensions += "environment"
    productFlavors {
        create("production") { dimension = "environment" }
        create("bench") { dimension = "environment" }
    }
}

dependencies {
    api(project(":data:models"))

    implementation(project(":core:auth"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:profile"))
    implementation(libs.atproto.models)
    implementation(libs.atproto.runtime)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
