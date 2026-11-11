plugins {
    alias(libs.plugins.nubecita.android.library.compose)
}

android {
    namespace = "net.kikin.nubecita.core.posttranslation.ui"
}

dependencies {
    // api: a surface passes the store it got from its ViewModel straight
    // into rememberPostTranslations, so the type is part of this module's API.
    api(project(":core:post-translation"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(project(":core:common"))
    implementation(project(":data:models"))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
