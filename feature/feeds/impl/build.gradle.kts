plugins {
    alias(libs.plugins.nubecita.android.feature)
}

android {
    namespace = "net.kikin.nubecita.feature.feeds.impl"

    defaultConfig {
        testInstrumentationRunner = "net.kikin.nubecita.core.testing.android.HiltTestRunner"
    }
}

dependencies {
    api(project(":feature:feeds:api"))

    implementation(project(":core:common"))
    // PinnedFeedsRepository (observePinnedFeeds / unpinFeed / reorderPinnedFeeds)
    // — consumed by the ManageFeeds ViewModel in later slices of the epic.
    implementation(project(":core:feeds"))
    implementation(project(":data:models"))
    implementation(project(":designsystem"))
    // Drag-to-reorder for the pinned-feeds list (ManageFeedsScreen).
    implementation(libs.reorderable)

    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
