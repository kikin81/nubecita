plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
    alias(libs.plugins.nubecita.android.flavors)
}

android {
    namespace = "net.kikin.nubecita.core.posts"

    // The `environment` flavor dimension splits the production
    // PostRepositoryModule (real `app.bsky.feed.getPosts` XRPC call via
    // `DefaultPostRepository`) from a bench-flavor parallel that binds a
    // deterministic, network-free `BenchFakePostRepository`. The `:app`
    // module's `bench` flavor consumes the matching variant via the
    // missingDimensionStrategy plumbing in `AndroidLibraryConventionPlugin`;
    // everything that imports `:core:posts` resolves the production variant
    // by default. Mirrors the precedent established in `:feature:feed:impl`
    // (crmi.6 Section A2) and `:core:auth` (Section A1, #330).
    // The `environment` dimension + `production`/`bench` flavors are declared
    // by the `nubecita.android.flavors` convention plugin (applied above).
}

dependencies {
    api(project(":data:models"))

    implementation(project(":core:auth"))
    implementation(project(":core:common"))
    implementation(project(":core:feed-mapping"))
    // ModerationPreferencesRepository + applyModeration — cover (never drop)
    // NSFW media in the post-detail thread.
    implementation(project(":core:moderation"))
    implementation(libs.atproto.models)
    implementation(libs.atproto.runtime)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    // PostThreadMapperTest builds app.bsky.feed wire fixtures whose embed
    // records are raw JsonObjects (buildJsonObject) — lifted in with the
    // thread mapper (nubecita-6rdb.3).
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.mockk)
}
