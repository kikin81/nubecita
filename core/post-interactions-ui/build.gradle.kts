plugins {
    alias(libs.plugins.nubecita.android.library.compose)
    alias(libs.plugins.nubecita.android.hilt)
}

android {
    namespace = "net.kikin.nubecita.core.postinteractions.ui"
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(project(":core:common"))
    implementation(project(":core:post-interactions"))
    implementation(project(":core:posts"))
    implementation(project(":data:models"))
    implementation(project(":designsystem"))
    implementation(project(":feature:composer:api"))
    implementation(project(":feature:moderation:api"))
    implementation(libs.androidx.browser)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.kotlinx.collections.immutable)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
