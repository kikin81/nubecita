plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
}

android {
    namespace = "net.kikin.nubecita.core.translation"
}

dependencies {
    api(project(":data:models"))

    implementation(project(":core:common"))
    implementation(project(":core:preferences"))
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.atproto.models)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.mlkit.language.id)
    implementation(libs.mlkit.translate)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
