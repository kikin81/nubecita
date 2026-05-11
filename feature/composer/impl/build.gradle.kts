plugins {
    alias(libs.plugins.nubecita.android.feature)
}

android {
    namespace = "net.kikin.nubecita.feature.composer.impl"

    defaultConfig {
        // Override the convention-plugin default (AndroidJUnitRunner) with the
        // Hilt-aware runner from :core:testing-android so @HiltAndroidTest
        // tests in src/androidTest/ can boot HiltTestApplication and drive
        // the @TestInstallIn-replaced component graph. Mirrors the setup in
        // :feature:feed:impl and :feature:login:impl.
        testInstrumentationRunner = "net.kikin.nubecita.core.testing.android.HiltTestRunner"
    }
}

dependencies {
    api(project(":feature:composer:api"))

    implementation(project(":core:auth"))
    implementation(project(":core:common"))
    implementation(project(":core:posting"))
    implementation(libs.androidx.activity.compose)
    // currentWindowAdaptiveInfoV2() + WindowSizeClass — used by
    // ComposerDiscardDialog to branch its visual treatment on width
    // class (BasicAlertDialog at Compact, Popup-wrapped Surface at
    // Medium / Expanded). The base `material3-adaptive` artifact
    // brings both APIs in transitively without pulling in any
    // navigation3 surface that the composer module doesn't need.
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.atproto.runtime)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.timber)

    // Compose UI tests for ComposerScreen — mirrors :feature:feed:impl's
    // setup. Required so the BackHandler / discard-dialog gate can be
    // exercised under createAndroidComposeRule<HiltTestActivity>().
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
    // Pin espresso-core explicitly — same reason as :feature:feed:impl
    // (compose-ui-test-junit4 otherwise pulls a stale transitive that
    // hits a removed hidden API on Android 16 / API 36 during ComposeRule
    // activity launch).
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.ext.junit)

    kspAndroidTest(libs.hilt.android.compiler)
}
