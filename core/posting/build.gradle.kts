plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "net.kikin.nubecita.core.posting"
}

dependencies {
    api(project(":data:models"))
    api(libs.atproto.models)
    api(libs.atproto.runtime)

    implementation(project(":core:auth"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.mockk)
}
