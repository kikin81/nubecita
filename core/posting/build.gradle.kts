plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.nubecita.android.flavors)
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
    // The `environment` dimension + `production`/`bench` flavors are declared
    // by the `nubecita.android.flavors` convention plugin (applied above).
}

dependencies {
    // :core:image is `api` (not `implementation`) because EncodedImage appears in
    // ExternalLinkMetadataRepository's public signature (downloadThumb).
    api(project(":core:image"))
    api(project(":data:models"))
    api(libs.atproto.models)
    api(libs.atproto.runtime)

    implementation(project(":core:analytics"))
    implementation(project(":core:auth"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.mockk)

    // Instrumented tests for the SharedMediaStore file-I/O + Uri/ContentResolver
    // glue (its pure core, MediaCopy, is JVM-unit-tested). No Hilt: the impl is
    // constructed directly with the instrumentation targetContext.
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
