plugins {
    alias(libs.plugins.nubecita.android.feature)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.nubecita.android.flavors)
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

    // The `environment` dimension + `production`/`bench` flavors are declared
    // by the `nubecita.android.flavors` convention plugin (applied above).
}

dependencies {
    api(project(":feature:profile:api"))

    implementation(project(":core:actors"))
    implementation(project(":core:analytics"))
    implementation(project(":core:auth"))
    // EntitlementRepository.isPro — gates the Pro "Supporter" badge on the
    // user's OWN profile hero. Provider-agnostic interface; the badge reacts
    // to the isPro StateFlow and never blocks on a synchronous check.
    implementation(project(":core:billing"))
    implementation(project(":core:common"))
    implementation(project(":core:feed-mapping"))
    implementation(project(":core:image"))
    // ModerationPreferencesRepository + applyModeration — drop/cover NSFW posts
    // across the Posts/Replies/Media profile tabs.
    implementation(project(":core:moderation"))
    implementation(project(":core:post-interactions"))
    implementation(project(":core:post-interactions-ui"))
    implementation(project(":core:posts"))
    implementation(project(":core:profile"))
    implementation(project(":data:models"))
    implementation(project(":designsystem"))
    // Bookmarks NavKey — pushed onto MainShell's inner back stack from the
    // Profile entry's `onNavigateToBookmarks` callback when the signed-in user
    // taps the Bookmarks button in the own-profile top bar. The Bookmarks list
    // screen lives in :feature:bookmarks:impl and contributes its own
    // @MainShell entry provider.
    implementation(project(":feature:bookmarks:api"))
    implementation(project(":feature:chats:api"))
    // ComposerRoute NavKey — reply taps on a PostCard push the composer in
    // reply mode (ComposerRoute(replyToUri = post.id)).
    implementation(project(":feature:composer:api"))
    // MediaViewerRoute NavKey — pushed onto the back stack when an
    // image (on a timeline PostCard) or a media-tab grid cell is tapped.
    implementation(project(":feature:mediaviewer:api"))
    // Report NavKey + ReportSubject sealed sum — pushed onto MainShell's
    // inner back stack from the ProfileHero overflow "Report account"
    // row (oftc.3 PR 4). :api only — :feature:moderation:impl owns the
    // dialog, VM, and repository and stays opt-in via Hilt multibinding.
    implementation(project(":feature:moderation:api"))
    implementation(project(":feature:postdetail:api"))
    // Settings NavKey — pushed onto MainShell's inner back stack from
    // the Profile entry's `onNavigateToSettings` callback when the user
    // taps Settings in the actions row. The Settings screen itself lives
    // in :feature:settings:impl (graduated in nubecita-77l) and is
    // contributed to the @MainShell multibinding by its own nav module.
    implementation(project(":feature:settings:api"))
    // VideoPlayerRoute NavKey — pushed onto the outer back stack when
    // a video media-grid cell is tapped (the MediaViewer can't render
    // video embeds, so the route here splits image vs video taps).
    implementation(project(":feature:videoplayer:api"))
    implementation(libs.androidx.browser)
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
    // Ktor MockEngine for the updateProfile write-path tests — stands up
    // a real XrpcClient over deterministic getRecord/putRecord/uploadBlob
    // responses (mirrors :core:posting/DefaultPostingRepositoryTest).
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
    // mockk-android needed for the relaxed mocks of session / cache
    // collaborators in ProfileScreenOverflowReportInstrumentationTest and
    // ProfileScreenPostsTabOverflowReportInstrumentationTest.
    androidTestImplementation(libs.mockk.android)

    kspAndroidTest(libs.hilt.android.compiler)
}
