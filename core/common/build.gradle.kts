plugins {
    alias(libs.plugins.nubecita.android.library)
}

android {
    namespace = "net.kikin.nubecita.core.common"
}

dependencies {
    api(libs.androidx.lifecycle.viewmodel.ktx)
    api(libs.androidx.navigation3.runtime)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
