plugins {
    alias(libs.plugins.nubecita.android.feature)
    // `kotlin.serialization` runs the @Serializable compiler plugin that
    // generates the `<Type>.serializer()` companions read by the bench
    // flavor's `BenchTimelineDto` loader in `BenchFakeFeedRepository`. The
    // `kotlinx-serialization-json` runtime is already on the
    // implementation classpath (used by `DefaultFeedRepository` /
    // `FeedRepository`'s wire integration in `:core:feed-mapping`),
    // but without the plugin the codegen never runs and the bench DTOs
    // fail to compile.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "net.kikin.nubecita.feature.feed.impl"

    defaultConfig {
        // Override the convention-plugin default (AndroidJUnitRunner) with the
        // Hilt-aware runner from :core:testing-android so @HiltAndroidTest
        // tests in src/androidTest/ can boot HiltTestApplication and drive
        // the @TestInstallIn-replaced component graph.
        testInstrumentationRunner = "net.kikin.nubecita.core.testing.android.HiltTestRunner"
    }

    // FeedVideoPlayerCoordinator's audio-focus contract tests construct
    // android.media.AudioAttributes.Builder() in the production path.
    // Without isReturnDefaultValues, Android's unit-test stub jar
    // throws "Method setUsage ... not mocked". Tests verify side
    // effects on a mocked AudioManager + StateFlow transitions, so
    // letting AudioAttributes calls no-op is safe.
    testOptions.unitTests.isReturnDefaultValues = true

    // The `environment` flavor dimension splits the production
    // FeedRepositoryModule (real `app.bsky.feed.getTimeline` XRPC call via
    // `DefaultFeedRepository`) from a bench-flavor parallel that binds an
    // asset-backed `BenchFakeFeedRepository`. The `:app` module's `bench` flavor
    // consumes the matching variant via the missingDimensionStrategy plumbing
    // in `AndroidLibraryConventionPlugin`; everything that imports
    // `:feature:feed:impl` resolves the production variant by default. See
    // `bd show nubecita-xh99` for the broader scope (crmi.6 Section A2) and
    // `:core:auth/build.gradle.kts` for the precedent established in
    // Section A1 (#330).
    flavorDimensions += "environment"
    productFlavors {
        create("production") { dimension = "environment" }
        create("bench") { dimension = "environment" }
    }
}

dependencies {
    api(project(":feature:feed:api"))

    implementation(project(":core:auth"))
    implementation(project(":core:feed-mapping"))
    implementation(project(":core:feeds"))
    implementation(project(":core:post-interactions"))
    implementation(project(":core:preferences"))
    implementation(project(":core:video"))
    implementation(project(":data:models"))
    // ComposerRoute NavKey — pushed onto the back stack when the
    // compose-new-post FAB is tapped. Imports `:api` only; never `:impl`.
    implementation(project(":feature:composer:api"))
    // Feeds NavKey — pushed onto the back stack when the manage feeds button is tapped.
    implementation(project(":feature:feeds:api"))
    // MediaViewerRoute NavKey — pushed onto the back stack when an
    // image in a feed PostCard is tapped (skip the PostDetail detour).
    implementation(project(":feature:mediaviewer:api"))
    // Report NavKey — pushed onto the inner MainShell back stack when
    // the PostCard overflow's "Report post" row is tapped. Imports
    // `:api` only; never `:impl` (the :impl provider that resolves the
    // NavKey is wired through Hilt's @MainShell multibinding from the
    // moderation module, not via a direct dep here).
    implementation(project(":feature:moderation:api"))
    // PostDetailRoute NavKey — pushed onto the back stack when a feed
    // post body is tapped. Imports `:api` only; never `:impl`.
    implementation(project(":feature:postdetail:api"))
    // Profile NavKey — pushed onto the back stack when an author handle
    // is tapped inside a PostCard. Imports `:api` only; never `:impl`.
    implementation(project(":feature:profile:api"))
    // VideoPlayerRoute NavKey — pushed onto the back stack when a video
    // embed in a feed PostCard is tapped (skip the PostDetail detour).
    // Imports `:api` only; never `:impl`.
    implementation(project(":feature:videoplayer:api"))
    implementation(libs.androidx.browser)
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
    implementation(libs.atproto.models)
    implementation(libs.atproto.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui.compose)
    implementation(libs.timber)

    // Compose UI tests for FeedScreen — mirrors :app's setup. Declared inline rather
    // than promoted into AndroidFeatureConventionPlugin until a second feature module
    // adopts createComposeRule() (per add-feature-feed-screen tasks 1.3).
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(project(":core:testing-android"))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    // Pin espresso-core explicitly — otherwise compose-ui-test-junit4 pulls a
    // stale transitive version whose InputManagerEventInjectionStrategy uses
    // a hidden API removed in Android 16 / API 36 (NoSuchMethodException on
    // InputManager.getInstance during ComposeRule activity launch).
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.ext.junit)

    kspAndroidTest(libs.hilt.android.compiler)
}
