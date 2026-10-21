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
    implementation(project(":core:post-interactions"))
    implementation(project(":core:post-interactions-ui"))
    // Single-post read surface (full PostUi: video embed + author/stats/viewer);
    // replaces the module-local getPosts resolver (nubecita-6rdb.2).
    implementation(project(":core:posts"))
    implementation(project(":core:video"))
    implementation(project(":data:models"))
    implementation(project(":designsystem"))
    implementation(project(":feature:composer:api"))
    implementation(project(":feature:postdetail:api"))
    implementation(project(":feature:profile:api"))
    implementation(libs.media3.ui.compose)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
