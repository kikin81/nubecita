plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
}

android {
    namespace = "net.kikin.nubecita.core.widgetsync"

    defaultConfig {
        // Hilt-aware runner from :core:testing-android so the @HiltAndroidTest
        // work-testing instrumentation test (§8) can boot HiltTestApplication
        // and build WidgetRefreshWorker through the @HiltWorker factory.
        testInstrumentationRunner = "net.kikin.nubecita.core.testing.android.HiltTestRunner"
    }
}

dependencies {
    implementation(project(":core:auth"))
    implementation(project(":core:common"))
    implementation(project(":core:feed-cache"))
    // WorkManager + Hilt-work for the background widget-refresh worker (PR2).
    // androidx.hilt.compiler (KSP) generates the @HiltWorker factory binding.
    // No androidx.glance — B stays Glance-free by construction (D-B1/D-B5).
    implementation(libs.androidx.hilt.work)
    // ProcessLifecycleOwner for the foreground guard (D-B4) — the runner skips
    // the cache write while the app is foregrounded. Local AppForegroundSignal
    // mirrors :feature:chats:impl (shared extraction to :core:common is a future
    // DRY follow-up).
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)

    // work-testing instrumentation (§8): WorkManagerTestInitHelper +
    // TestListenableWorkerBuilder exercise the real WorkManager enqueue and the
    // @HiltWorker factory path. Needs the Hilt-aware runner (HiltTestRunner).
    androidTestImplementation(project(":core:testing-android"))
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)

    // @HiltWorker factory binding generator (androidx.hilt), in addition to the
    // dagger hilt-android-compiler the convention plugin already wires.
    ksp(libs.androidx.hilt.compiler)

    kspAndroidTest(libs.hilt.android.compiler)
}
