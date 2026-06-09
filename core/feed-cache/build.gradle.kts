plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
}

android {
    namespace = "net.kikin.nubecita.core.feedcache"
}

dependencies {
    // PostUi / FeedKey / FeedType and friends are the public surface of this
    // module's repositories, so consumers see the data-models types transitively.
    api(project(":data:models"))

    implementation(project(":core:auth"))
    implementation(project(":core:database"))
    implementation(project(":core:feed-mapping"))
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
