plugins {
    alias(libs.plugins.nubecita.android.feature)
    alias(libs.plugins.nubecita.android.flavors)
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

    // The `environment` dimension is still declared here because `:app`'s
    // bench flavor resolves a matching variant of every feature module it
    // consumes. This module no longer owns a repository to swap, though: the
    // feed's offline fixtures moved to `:core:feed-cache`'s bench source set
    // with the repository that now serves them (D1).
}

dependencies {
    api(project(":feature:feed:api"))

    implementation(project(":core:actors"))
    implementation(project(":core:analytics"))
    implementation(project(":core:auth"))
    // The feed reads its pages from the offline-first cache (D1) — this module
    // no longer owns a repository or a network call of its own.
    implementation(project(":core:feed-cache"))
    implementation(project(":core:feed-mapping"))
    implementation(project(":core:feeds"))
    // ModerationPreferencesRepository (cached prefs) + applyModeration, to
    // drop/cover NSFW-labelled posts as the timeline maps.
    implementation(project(":core:moderation"))
    implementation(project(":core:post-interactions"))
    implementation(project(":core:post-interactions-ui"))
    implementation(project(":core:posting"))
    implementation(project(":core:preferences"))
    implementation(project(":core:video"))
    implementation(project(":core:video-feed"))
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
    implementation(project(":feature:search:api"))
    // VideoPlayerRoute NavKey — pushed onto the back stack when a video
    // embed in a feed PostCard is tapped (skip the PostDetail detour).
    // Imports `:api` only; never `:impl`.
    implementation(project(":feature:videoplayer:api"))
    implementation(project(":feature:videos:api"))
    implementation(libs.androidx.browser)
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
    implementation(libs.androidx.paging.compose)
    implementation(libs.atproto.models)
    implementation(libs.atproto.runtime)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui.compose)
    implementation(libs.timber)

    // Compose UI tests for FeedScreen — mirrors :app's setup. Declared inline rather
    // than promoted into AndroidFeatureConventionPlugin until a second feature module
    // adopts createComposeRule() (per add-feature-feed-screen tasks 1.3).
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(project(":core:testing"))
    testImplementation(libs.androidx.paging.testing)
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
