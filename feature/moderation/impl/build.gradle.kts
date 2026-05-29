plugins {
    alias(libs.plugins.nubecita.android.feature)
}

android {
    namespace = "net.kikin.nubecita.feature.moderation.impl"
}

dependencies {
    api(project(":feature:moderation:api"))

    implementation(project(":core:auth"))
    implementation(project(":core:common"))
    implementation(libs.atproto.models)
    implementation(libs.atproto.runtime)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.stavfx.nav3hiltvm.annotations)
    implementation(libs.timber)

    ksp(libs.stavfx.nav3hiltvm.compiler)

    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)

    // Instrumentation tests for the report dialog flow (the
    // `run-instrumented` PR label is required to exercise these in CI).
    // Pin espresso-core explicitly per the convention used by every
    // other feature module — compose-ui-test-junit4 otherwise pulls a
    // stale transitive Espresso that breaks `Espresso.pressBack()`.
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
