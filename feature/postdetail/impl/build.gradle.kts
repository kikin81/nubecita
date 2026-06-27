plugins {
    alias(libs.plugins.nubecita.android.feature)
}

android {
    namespace = "net.kikin.nubecita.feature.postdetail.impl"
}

dependencies {
    api(project(":feature:postdetail:api"))

    implementation(project(":core:analytics"))
    implementation(project(":core:auth"))
    implementation(project(":core:post-interactions"))
    // Thread read surface (PostThreadRepository → ThreadItem); lifted here
    // from this module (nubecita-6rdb.3).
    implementation(project(":core:posts"))
    implementation(project(":data:models"))
    // ComposerRoute NavKey — pushed onto the inner back stack from a reply tap
    // in a thread post (tagged adaptiveDialog() so it presents as a dialog on
    // tablets). :api only.
    implementation(project(":feature:composer:api"))
    implementation(project(":feature:mediaviewer:api"))
    // Report NavKey + Report.forPost factory — pushed onto the inner
    // back stack from the PostCard overflow "Report post" row inside
    // the thread (oftc.3.1). `:api` only — `:feature:moderation:impl`
    // owns the dialog, VM, and repository.
    implementation(project(":feature:moderation:api"))
    // Profile NavKey — pushed onto the back stack when an author handle
    // is tapped inside a thread post. Imports `:api` only; never `:impl`.
    implementation(project(":feature:profile:api"))
    // VideoPlayerRoute NavKey — pushed onto the outer back stack when a
    // video embed inside a thread PostCard is tapped. Routes through the
    // outer Navigator (the route is `@OuterShell`-qualified) so the
    // player escapes MainShell's NavigationSuiteScaffold chrome.
    implementation(project(":feature:videoplayer:api"))
    // Chrome Custom Tab launcher for tappable external (web link) embeds in
    // thread posts — mirrors :feature:feed:impl's onExternalEmbedTap (nubecita-thom).
    implementation(libs.androidx.browser)
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
    implementation(libs.atproto.runtime)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
