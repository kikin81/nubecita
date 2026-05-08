plugins {
    alias(libs.plugins.nubecita.android.feature)
}

android {
    namespace = "net.kikin.nubecita.feature.mediaviewer.impl"

    defaultConfig {
        // Hilt-aware runner from :core:testing-android so @HiltAndroidTest
        // tests in src/androidTest/ can boot HiltTestApplication.
        testInstrumentationRunner = "net.kikin.nubecita.core.testing.android.HiltTestRunner"
    }
}

dependencies {
    api(project(":feature:mediaviewer:api"))

    implementation(project(":core:auth"))
    implementation(project(":core:common"))
    implementation(project(":core:posts"))
    implementation(libs.atproto.runtime)
    implementation(project(":data:models"))
    implementation(project(":designsystem"))
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.coil.compose)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.telephoto.zoomable.image.coil3)
    implementation(libs.timber)

    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(project(":core:testing-android"))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.ext.junit)

    kspAndroidTest(libs.hilt.android.compiler)
}
