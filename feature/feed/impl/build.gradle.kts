plugins {
    alias(libs.plugins.nubecita.android.feature)
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
}

dependencies {
    api(project(":feature:feed:api"))

    implementation(project(":core:auth"))
    implementation(project(":core:feed-mapping"))
    implementation(project(":data:models"))
    // PostDetailRoute NavKey — pushed onto the back stack when a feed
    // post body is tapped. Imports `:api` only; never `:impl`.
    implementation(project(":feature:postdetail:api"))
    implementation(libs.androidx.browser)
    implementation(libs.androidx.compose.material.icons.extended)
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
