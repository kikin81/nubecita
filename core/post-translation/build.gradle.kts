plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
}

android {
    namespace = "net.kikin.nubecita.core.posttranslation"
}

dependencies {
    api(project(":data:models"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(project(":core:analytics"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:preferences"))
    implementation(project(":core:translation"))
    // Runtime only, for snapshot state: the store is read per card during
    // composition, and a snapshot map recomposes just the card whose entry
    // changed. No UI dependency belongs here.
    implementation(libs.androidx.compose.runtime)
    implementation(libs.atproto.models)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
