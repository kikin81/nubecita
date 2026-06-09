plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
}

android {
    namespace = "net.kikin.nubecita.core.widgetsync"
}

dependencies {
    implementation(project(":core:auth"))
    implementation(project(":core:common"))
    implementation(project(":core:feed-cache"))
    // WorkManager + Hilt-work for the background widget-refresh worker (PR2).
    // androidx.hilt.compiler (KSP) generates the @HiltWorker factory binding.
    // No androidx.glance — B stays Glance-free by construction (D-B1/D-B5).
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.timber)

    // @HiltWorker factory binding generator (androidx.hilt), in addition to the
    // dagger hilt-android-compiler the convention plugin already wires.
    ksp(libs.androidx.hilt.compiler)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
