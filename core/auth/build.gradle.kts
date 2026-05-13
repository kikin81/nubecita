plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "net.kikin.nubecita.core.auth"
    // Needed so HttpClientModule can gate Ktor's request/response Logging plugin
    // on BuildConfig.DEBUG. Defense in depth: even if a release Timber tree is
    // planted later (Crashlytics/Sentry), the install itself is build-type-gated
    // so DPoP/Authorization headers can never reach release logcat or remote
    // crash reports — independent of which Timber trees are planted at runtime.
    buildFeatures.buildConfig = true
}

dependencies {
    api(libs.atproto.oauth)
    api(libs.atproto.runtime)

    implementation(project(":core:common"))
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.tink)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.timber)
    implementation(libs.tink.android)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
