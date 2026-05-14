plugins {
    alias(libs.plugins.nubecita.android.feature)
}

android {
    namespace = "net.kikin.nubecita.feature.chats.impl"

    defaultConfig {
        // Override the convention-plugin default (AndroidJUnitRunner) with the
        // Hilt-aware runner from :core:testing-android so @HiltAndroidTest
        // tests in src/androidTest/ can boot HiltTestApplication and drive
        // the @TestInstallIn-replaced component graph. Mirrors the setup in
        // :feature:composer:impl and :feature:feed:impl.
        testInstrumentationRunner = "net.kikin.nubecita.core.testing.android.HiltTestRunner"
    }
}

dependencies {
    api(project(":feature:chats:api"))

    implementation(project(":core:auth"))
    implementation(project(":core:common"))
    // Record-embed wire → UI mapping (RecordView → EmbedUi.RecordOrUnavailable)
    // is shared with :feature:feed:impl. The chat lexicon
    // (`chat.bsky.convo.defs#messageView.embed`) only admits
    // `app.bsky.embed.record#view`; we reuse the existing helpers rather
    // than duplicate the JsonObject decode + recursion-bound logic.
    implementation(project(":core:feed-mapping"))
    implementation(project(":data:models"))
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
    implementation(libs.atproto.models)
    implementation(libs.atproto.runtime)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)

    // Compose UI tests for ChatScreen — required so
    // createAndroidComposeRule<HiltTestActivity>() can drive the screen
    // composition path under instrumentation. Mirrors :feature:composer:impl.
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(project(":core:testing-android"))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    // Pin espresso-core explicitly — same reason as :feature:composer:impl
    // and :feature:feed:impl: compose-ui-test-junit4 otherwise pulls a stale
    // transitive that hits a removed hidden API on Android 16 / API 36
    // during ComposeRule activity launch.
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.kotlinx.collections.immutable)

    kspAndroidTest(libs.hilt.android.compiler)
}
