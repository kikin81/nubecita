plugins {
    alias(libs.plugins.nubecita.android.feature)
}

android {
    namespace = "net.kikin.nubecita.feature.videos.impl"

    defaultConfig {
        testInstrumentationRunner = "net.kikin.nubecita.core.testing.android.HiltTestRunner"
    }
}

dependencies {
    api(project(":feature:videos:api"))

    implementation(project(":core:common"))
    implementation(project(":core:video"))
    implementation(project(":core:video-feed"))
    implementation(project(":data:models"))
    implementation(project(":designsystem"))
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.media3.ui.compose)
    implementation(libs.timber)

    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
