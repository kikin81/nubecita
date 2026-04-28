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
    api(libs.androidx.compose.runtime.saveable)
    api(libs.androidx.lifecycle.viewmodel.ktx)
    api(libs.androidx.navigation3.runtime)
    api(libs.androidx.savedstate.compose)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.datetime)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
}
