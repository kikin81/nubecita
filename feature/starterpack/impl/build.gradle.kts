plugins {
    alias(libs.plugins.nubecita.android.feature)
    alias(libs.plugins.nubecita.android.flavors)
}

android {
    namespace = "net.kikin.nubecita.feature.starterpack.impl"

    // The `environment` flavor dimension splits the production
    // StarterPackRepositoryModule (real `app.bsky.graph.getStarterPack` /
    // `getList` XRPC calls via `DefaultStarterPackRepository`) from a bench
    // parallel that binds a deterministic, network-free
    // `BenchFakeStarterPackRepository`, so the offline bench build can open a
    // pack. Same split as :feature:search:impl.
    // The `environment` dimension + `production`/`bench` flavors are declared
    // by the `nubecita.android.flavors` convention plugin (applied above).
}

dependencies {
    api(project(":feature:starterpack:api"))

    implementation(project(":core:auth"))
    implementation(project(":core:common"))
    implementation(project(":core:feed-mapping"))
    // FollowToggle / FollowOverlay: the one follow mechanism shared with search.
    implementation(project(":core:post-interactions"))
    implementation(project(":data:models"))
    // Tap-to-open a member pushes Profile, a pack feed pushes FeedView. The
    // api modules ship just the NavKeys.
    implementation(project(":feature:feed:api"))
    implementation(project(":feature:profile:api"))
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
    implementation(libs.atproto.models)
    implementation(libs.atproto.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
