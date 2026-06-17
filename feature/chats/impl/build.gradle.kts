plugins {
    alias(libs.plugins.nubecita.android.feature)
    alias(libs.plugins.kotlin.serialization)
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

    flavorDimensions += "environment"
    productFlavors {
        create("production") { dimension = "environment" }
        create("bench") { dimension = "environment" }
    }
}

dependencies {
    api(project(":feature:chats:api"))

    implementation(project(":core:actors"))
    implementation(project(":core:auth"))
    implementation(project(":core:common"))
    // Record-embed wire → UI mapping (RecordView → EmbedUi.RecordOrUnavailable)
    // is shared with :feature:feed:impl. The chat lexicon
    // (`chat.bsky.convo.defs#messageView.embed`) only admits
    // `app.bsky.embed.record#view`; we reuse the existing helpers rather
    // than duplicate the JsonObject decode + recursion-bound logic.
    implementation(project(":core:feed-mapping"))
    // DM-poll cursor + the message-checking opt-in toggle (the background
    // worker's gate). nubecita-1fy.15.
    implementation(project(":core:preferences"))
    implementation(project(":core:profile"))
    implementation(project(":data:models"))
    // Report / Block NavKeys for the contextual-action menu — :api only
    // (the dialogs live in :feature:moderation:impl).
    implementation(project(":feature:moderation:api"))
    // Tap-to-open the quoted-post embed inside a message bubble pushes a
    // PostDetailRoute onto the MainShell back stack. The api module ships
    // just the NavKey — :feature:chats:impl never depends on :impl, matching
    // the Profile / Feed pattern.
    implementation(project(":feature:postdetail:api"))
    // Profile NavKey for the "Go to profile" contextual action — :api only.
    implementation(project(":feature:profile:api"))
    // BackHandler — exits multi-select mode on a back press before the press
    // falls through to inner-NavDisplay back navigation.
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
    // NotificationCompat / MessagingStyle / NotificationManagerCompat for the
    // background DM notifier (nubecita-1fy.15).
    implementation(libs.androidx.core.ktx)
    // WorkManager + Hilt-work for the background DM-poll worker (v2,
    // nubecita-1fy.15). Periodic, Doze-cooperative. androidx.hilt.compiler (KSP)
    // generates the @HiltWorker factory binding.
    implementation(libs.androidx.hilt.work)
    // ProcessLifecycleOwner + repeatOnLifecycle — the chats unread-count
    // polling observer registers against the process lifecycle so polling
    // pauses on backgrounding (foreground-only, battery-safe). Mirrors
    // :feature:notifications:impl.
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.work.runtime.ktx)
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
    // Ktor MockEngine for the ChatSettingsRepository getRecord/putRecord
    // write-path tests — stands up a real XrpcClient over deterministic
    // responses (same pattern as :feature:profile:impl).
    testImplementation(libs.ktor.client.mock)
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
    // WorkManagerTestInitHelper + TestDriver + TestListenableWorkerBuilder for
    // the DmPollWorker instrumentation tests (added by a later task group).
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.kotlinx.collections.immutable)

    // @HiltWorker factory binding generator (androidx.hilt), in addition to the
    // dagger hilt-android-compiler the convention plugin already wires.
    ksp(libs.androidx.hilt.compiler)

    kspAndroidTest(libs.hilt.android.compiler)
}
