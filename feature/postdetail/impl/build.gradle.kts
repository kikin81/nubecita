plugins {
    alias(libs.plugins.nubecita.android.feature)
}

android {
    namespace = "net.kikin.nubecita.feature.postdetail.impl"
}

dependencies {
    api(project(":feature:postdetail:api"))

    implementation(project(":core:auth"))
    implementation(project(":data:models"))
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.atproto.models)
    implementation(libs.atproto.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
