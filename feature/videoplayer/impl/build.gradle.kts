plugins {
    alias(libs.plugins.nubecita.android.feature)
}

android {
    namespace = "net.kikin.nubecita.feature.videoplayer.impl"
}

dependencies {
    api(project(":feature:videoplayer:api"))

    implementation(project(":core:auth"))
    implementation(project(":core:common"))
    implementation(project(":core:feed-mapping"))
    implementation(project(":core:video"))
    implementation(project(":data:models"))
    implementation(libs.atproto.models)
    implementation(libs.atproto.runtime)
    implementation(libs.media3.ui.compose)
    implementation(libs.stavfx.nav3hiltvm)
    implementation(libs.timber)

    ksp(libs.stavfx.nav3hiltvm)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
