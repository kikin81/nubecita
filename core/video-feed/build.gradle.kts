plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
    alias(libs.plugins.nubecita.android.flavors)
}

android {
    namespace = "net.kikin.nubecita.core.videofeed"
}

dependencies {
    api(project(":core:feed-mapping"))
    implementation(project(":core:auth"))
    implementation(project(":core:common"))
    implementation(libs.atproto.models)
    implementation(libs.atproto.runtime)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.collections.immutable)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}
