plugins {
    alias(libs.plugins.nubecita.android.library.compose)
}

android {
    namespace = "net.kikin.nubecita.designsystem"

    lint {
        lintConfig = file("lint.xml")
    }
}

dependencies {
    implementation(platform(libs.coil.bom))
    implementation(project(":core:common"))
    implementation(project(":data:models"))
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.core.ktx)
    implementation(libs.atproto.compose.material3)
    implementation(libs.coil.compose)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
