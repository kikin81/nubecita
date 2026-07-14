plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
    alias(libs.plugins.nubecita.android.flavors)
}

android {
    namespace = "net.kikin.nubecita.core.postinteractions"

    // The `environment` flavor dimension splits the production write repositories
    // (network-backed `DefaultLikeRepostRepository` + `DefaultFollowRepository`)
    // from bench parallels that bind offline no-op fakes
    // (`BenchFakeLikeRepostRepository` + `BenchFakeFollowRepository`), so that
    // like/repost/follow interactions work (and stick) on the bench build without
    // hitting the network. Consumers resolve the `production` variant by default
    // via the `missingDimensionStrategy` plumbing in `AndroidLibraryConventionPlugin`;
    // the bench app flavor consumes the matching variant. Mirrors `:core:actors`.
    // The `environment` dimension + `production`/`bench` flavors are declared
    // by the `nubecita.android.flavors` convention plugin (applied above).
}

dependencies {
    api(project(":data:models"))

    implementation(project(":core:actors"))
    implementation(project(":core:analytics"))
    implementation(project(":core:auth"))
    implementation(project(":core:bookmarks"))
    implementation(project(":core:common"))
    implementation(project(":designsystem"))
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
