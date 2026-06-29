plugins {
    alias(libs.plugins.nubecita.android.library.compose)
}

android {
    namespace = "net.kikin.nubecita.core.postinteractions.ui"
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(project(":core:common"))
    implementation(project(":core:post-interactions"))
    implementation(project(":designsystem"))
    implementation(project(":feature:composer:api"))
    implementation(project(":feature:moderation:api"))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation3.runtime)
}
