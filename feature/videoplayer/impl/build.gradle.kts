plugins {
    alias(libs.plugins.nubecita.android.feature)
}

android {
    namespace = "net.kikin.nubecita.feature.videoplayer.impl"
}

dependencies {
    api(project(":feature:videoplayer:api"))

    implementation(project(":core:analytics"))
    implementation(project(":core:common"))
    // Single-post read surface (full PostUi: video embed + author/stats/viewer);
    // replaces the module-local getPosts resolver (nubecita-6rdb.2).
    implementation(project(":core:posts"))
    implementation(project(":core:video"))
    implementation(project(":data:models"))
    // PaywallRoute — the non-Pro pop-out tap routes to the paywall (nubecita-q5ge.8).
    implementation(project(":feature:paywall:api"))
    implementation(libs.media3.ui.compose)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
