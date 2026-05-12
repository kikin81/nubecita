plugins {
    alias(libs.plugins.nubecita.android.feature)
}

android {
    namespace = "net.kikin.nubecita.feature.profile.impl"
}

dependencies {
    api(project(":feature:profile:api"))

    implementation(project(":core:auth"))
    implementation(project(":core:common"))
    implementation(project(":core:feed-mapping"))
    implementation(project(":data:models"))
    implementation(project(":designsystem"))
    implementation(libs.atproto.models)
    implementation(libs.atproto.runtime)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
