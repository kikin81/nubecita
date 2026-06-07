plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "net.kikin.nubecita.core.posting"

    // The `environment` flavor dimension splits the production PostingRepository
    // binding (real `createRecord` XRPC via `DefaultPostingRepository`) from a
    // bench-flavor parallel that binds a network-free `BenchFakePostingRepository`
    // (returns a synthetic AtUri). The `:app` bench flavor consumes the matching
    // variant via the missingDimensionStrategy plumbing in
    // `AndroidLibraryConventionPlugin`; everything that imports `:core:posting`
    // resolves the production variant by default. Mirrors `:core:posts` /
    // `:core:auth`. Lets the offline bench build exercise post/quote submission,
    // which otherwise hits the throwing FakeXrpcClientProvider. Refs: nubecita-8g28.8.
    flavorDimensions += "environment"
    productFlavors {
        create("production") { dimension = "environment" }
        create("bench") { dimension = "environment" }
    }
}

dependencies {
    api(project(":data:models"))
    api(libs.atproto.models)
    api(libs.atproto.runtime)

    implementation(project(":core:analytics"))
    implementation(project(":core:auth"))
    implementation(project(":core:common"))
    implementation(project(":core:image"))
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.mockk)
}
