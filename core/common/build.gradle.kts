plugins {
    alias(libs.plugins.nubecita.android.library.compose)
    alias(libs.plugins.nubecita.android.hilt)
}

android {
    namespace = "net.kikin.nubecita.core.common"
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.runtime)
    api(libs.androidx.lifecycle.viewmodel.ktx)
    api(libs.androidx.navigation3.runtime)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.datetime)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
