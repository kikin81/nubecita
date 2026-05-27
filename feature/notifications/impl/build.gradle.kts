plugins {
    alias(libs.plugins.nubecita.android.feature)
}

android {
    namespace = "net.kikin.nubecita.feature.notifications.impl"
}

dependencies {
    api(project(":feature:notifications:api"))

    implementation(project(":core:auth"))
    implementation(project(":core:feed-mapping"))
    implementation(project(":data:models"))
    implementation(libs.atproto.models)
    implementation(libs.atproto.runtime)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}
