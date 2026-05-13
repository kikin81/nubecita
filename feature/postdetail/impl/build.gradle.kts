plugins {
    alias(libs.plugins.nubecita.android.feature)
}

android {
    namespace = "net.kikin.nubecita.feature.postdetail.impl"
}

dependencies {
    api(project(":feature:postdetail:api"))

    implementation(project(":core:auth"))
    implementation(project(":core:post-interactions"))
    implementation(project(":core:feed-mapping"))
    implementation(project(":data:models"))
    implementation(project(":feature:mediaviewer:api"))
    // Profile NavKey — pushed onto the back stack when an author handle
    // is tapped inside a thread post. Imports `:api` only; never `:impl`.
    implementation(project(":feature:profile:api"))
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
    implementation(libs.atproto.models)
    implementation(libs.atproto.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
