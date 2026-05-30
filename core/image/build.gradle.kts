plugins {
    alias(libs.plugins.nubecita.android.library.compose)
    alias(libs.plugins.nubecita.android.hilt)
}

android {
    namespace = "net.kikin.nubecita.core.image"
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.core)
}
