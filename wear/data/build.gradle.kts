plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
    alias(libs.plugins.nubecita.android.room)
}

android {
    namespace = "net.kikin.nubecita.wear.data"
}

// The Wear app's offline store (nubecita-jh9c.3): what the phone has synced,
// in a Room database separate from the phone's :core:database. Ingested from
// Data Layer items by a listener service; read by the watch UI.
dependencies {
    implementation(project(":core:wear-contract"))
    implementation(libs.google.play.services.wearable)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
