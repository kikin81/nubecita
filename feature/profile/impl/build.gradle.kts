plugins {
    alias(libs.plugins.nubecita.android.feature)
}

android {
    namespace = "net.kikin.nubecita.feature.profile.impl"

    defaultConfig {
        // Override the convention-plugin default (AndroidJUnitRunner) with the
        // Hilt-aware runner from :core:testing-android — mirrors :feature:feed:impl
        // and :feature:composer:impl. The current Profile instrumentation test
        // (ProfileScreenInstrumentationTest) does NOT use Hilt, but adopting the
        // shared runner keeps the module ready for future @HiltAndroidTest classes
        // without a second build.gradle edit.
        testInstrumentationRunner = "net.kikin.nubecita.core.testing.android.HiltTestRunner"
    }
}

dependencies {
    api(project(":feature:profile:api"))

    implementation(project(":core:auth"))
    implementation(project(":core:common"))
    implementation(project(":core:feed-mapping"))
    implementation(project(":core:post-interactions"))
    implementation(project(":data:models"))
    implementation(project(":designsystem"))
    implementation(project(":feature:chats:api"))
    implementation(project(":feature:postdetail:api"))
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
    implementation(libs.atproto.models)
    implementation(libs.atproto.runtime)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.timber)

    // Compose UI tests for ProfileScreen — mirrors :feature:feed:impl and
    // :feature:composer:impl. Required so createAndroidComposeRule<ComponentActivity>()
    // can launch its host activity under the Compose test manifest.
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
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
    // mockk-android needed for TestAuthRepositoryModule (relaxed mocks of
    // auth interfaces in the Hilt test graph — SettingsStubInstrumentationTest).
    androidTestImplementation(libs.mockk.android)

    kspAndroidTest(libs.hilt.android.compiler)
}
