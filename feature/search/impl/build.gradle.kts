plugins {
    alias(libs.plugins.nubecita.android.feature)
    // vrba.11: hand-written @Serializable request/response types in the
    // Feeds-tab data layer call XrpcClient.query() directly because the
    // upstream RPC (app.bsky.unspecced.getPopularFeedGenerators) isn't
    // covered by the lex-install pipeline. See
    // github.com/kikin81/atproto-kotlin/issues/108.
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "net.kikin.nubecita.feature.search.impl"

    defaultConfig {
        // Hilt-aware runner from :core:testing-android so @HiltAndroidTest
        // tests in src/androidTest/ boot HiltTestApplication and drive the
        // @TestInstallIn-replaced component graph (vrba.9 tap-through tests).
        testInstrumentationRunner = "net.kikin.nubecita.core.testing.android.HiltTestRunner"
    }
}

dependencies {
    api(project(":feature:search:api"))

    implementation(project(":core:auth"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:feed-mapping"))
    // vrba.10: the typeahead VM reuses ActorTypeaheadRepository (the composer's
    // searchActorsTypeahead reader). Promoting the repo to :core:posting's
    // public API was already done for the composer; the search typeahead is
    // the second consumer, not a copy.
    implementation(project(":core:posting"))
    // Tap-to-open the post hit pushes a PostDetailRoute onto the MainShell
    // back stack. The api module ships just the NavKey — :feature:search:impl
    // never depends on :impl, matching the Chats / Feed / Profile pattern.
    implementation(project(":feature:postdetail:api"))
    // Tap-to-open an actor row pushes a Profile(handle) onto the MainShell
    // back stack. The api module ships just the NavKey — :feature:search:impl
    // never depends on :impl, matching the Chats / Feed / Postdetail pattern.
    implementation(project(":feature:profile:api"))
    implementation(libs.atproto.models)
    implementation(libs.atproto.runtime)
    implementation(libs.timber)

    // vrba.9: Compose UI tap-through tests in androidTest/ need the test
    // manifest's empty Activity so createAndroidComposeRule can launch.
    // Matches :feature:feed:impl's setup until a second feature module
    // adopts the same pattern and the dep moves into the convention plugin.
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.turbine)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(project(":core:testing-android"))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    // Pin espresso-core explicitly — otherwise compose-ui-test-junit4 pulls a
    // stale transitive version whose InputManagerEventInjectionStrategy uses
    // a hidden API removed in Android 16 / API 36 (NoSuchMethodException on
    // InputManager.getInstance during ComposeRule activity launch). Mirrors
    // :feature:feed:impl.
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.ext.junit)

    kspAndroidTest(libs.hilt.android.compiler)
}
