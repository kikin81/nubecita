plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "net.kikin.nubecita.core.auth"
}

dependencies {
    api(libs.atproto.oauth)

    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.tink)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.tink.android)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
}
