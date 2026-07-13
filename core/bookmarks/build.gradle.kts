plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
    alias(libs.plugins.nubecita.android.flavors)
}

android {
    namespace = "net.kikin.nubecita.core.bookmarks"

    // The `environment` flavor dimension splits the production write repository
    // (network-backed `DefaultBookmarkRepository`) from a bench parallel that
    // binds an offline no-op fake (`BenchFakeBookmarkRepository`), so bookmark
    // toggles work on the bench build without hitting the network (the
    // `XrpcClientProvider` throws when signed out / offline). Mirrors the
    // production/bench write split in `:core:post-interactions`. The dimension +
    // `production`/`bench` flavors are declared by the `nubecita.android.flavors`
    // convention plugin (applied above).
}

dependencies {
    api(project(":data:models"))

    implementation(project(":core:auth"))
    implementation(project(":core:common"))
    implementation(project(":core:feed-mapping"))
    implementation(libs.atproto.models)
    implementation(libs.atproto.runtime)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
