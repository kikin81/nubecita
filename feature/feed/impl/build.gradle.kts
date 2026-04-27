plugins {
    alias(libs.plugins.nubecita.android.feature)
}

android {
    namespace = "net.kikin.nubecita.feature.feed.impl"

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
    implementation(project(":data:models"))
    implementation(libs.androidx.browser)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.atproto.models)
    implementation(libs.atproto.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui.compose)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)

    // Compose UI tests for FeedScreen — mirrors :app's setup. Declared inline rather
    // than promoted into AndroidFeatureConventionPlugin until a second feature module
    // adopts createComposeRule() (per add-feature-feed-screen tasks 1.3).
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
