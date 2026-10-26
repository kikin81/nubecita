plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
}

android {
    namespace = "net.kikin.nubecita.core.wearsync"
}

// Phone side of the Wear Data Layer: publishes snapshots to the watch and
// answers its requests. The watch never talks to Bluesky; this module is the
// only place the phone touches the Data Layer.
dependencies {
    implementation(project(":core:auth"))
    implementation(project(":core:common"))
    implementation(project(":core:wear-contract"))
    implementation(libs.google.play.services.wearable)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
}
