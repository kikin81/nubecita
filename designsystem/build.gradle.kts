plugins {
    alias(libs.plugins.nubecita.android.library.compose)
}

android {
    namespace = "net.kikin.nubecita.designsystem"
}

dependencies {
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
