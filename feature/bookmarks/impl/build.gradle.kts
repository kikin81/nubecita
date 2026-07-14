plugins {
    alias(libs.plugins.nubecita.android.feature)
}

android {
    namespace = "net.kikin.nubecita.feature.bookmarks.impl"

    defaultConfig {
        // Hilt-aware runner from :core:testing-android so @HiltAndroidTest
        // tests in src/androidTest/ can boot HiltTestApplication.
        testInstrumentationRunner = "net.kikin.nubecita.core.testing.android.HiltTestRunner"
    }
}

dependencies {
    api(project(":feature:bookmarks:api"))

    // interact_post source_surface attribution for taps made on this list.
    implementation(project(":core:analytics"))
    // BookmarkRepository / BookmarksPage — the getBookmarks read boundary.
    implementation(project(":core:bookmarks"))
    implementation(project(":core:common"))
    // Shared optimistic interactions cache + PostInteractionHandler delegation.
    implementation(project(":core:post-interactions"))
    implementation(project(":core:post-interactions-ui"))
    implementation(project(":data:models"))
    implementation(project(":designsystem"))
    // Tap-to-open a bookmarked post pushes a PostDetailRoute onto the MainShell
    // back stack. The api module ships just the NavKey — never depend on :impl.
    implementation(project(":feature:postdetail:api"))
    // Tap-to-open a post author pushes a Profile(handle) onto the MainShell
    // back stack. The api module ships just the NavKey.
    implementation(project(":feature:profile:api"))
    // Custom Tabs: tap an inline link in a bookmarked post's body → in-app
    // browser (same Custom Tab path as Feed / Search / PostDetail).
    implementation(libs.androidx.browser)
    implementation(libs.kotlinx.collections.immutable)

    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(project(":core:testing-android"))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.ext.junit)

    kspAndroidTest(libs.hilt.android.compiler)
}
