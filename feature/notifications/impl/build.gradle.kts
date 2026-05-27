plugins {
    alias(libs.plugins.nubecita.android.feature)
}

android {
    namespace = "net.kikin.nubecita.feature.notifications.impl"

    defaultConfig {
        // Override the convention-plugin default (AndroidJUnitRunner) with the
        // Hilt-aware runner from :core:testing-android so @HiltAndroidTest
        // tests in src/androidTest/ can boot HiltTestApplication and drive
        // the @TestInstallIn-replaced component graph.
        testInstrumentationRunner = "net.kikin.nubecita.core.testing.android.HiltTestRunner"
    }
}

dependencies {
    api(project(":feature:notifications:api"))

    implementation(project(":core:auth"))
    implementation(project(":core:feed-mapping"))
    implementation(project(":data:models"))
    implementation(project(":feature:postdetail:api"))
    implementation(project(":feature:profile:api"))
    // ProcessLifecycleOwner — the unread-count polling observer registers
    // against the process-wide lifecycle so polling pauses on backgrounding
    // and resumes on foregrounding.
    implementation(libs.androidx.lifecycle.process)
    // repeatOnLifecycle extension — needed by NotificationsPollingObserver.
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.atproto.models)
    implementation(libs.atproto.runtime)
    implementation(libs.timber)

    // Required by createAndroidComposeRule<HiltTestActivity>() — the
    // manifest dep registers the empty Activity stub Compose needs for
    // ActivityScenario to launch under instrumentation. Without it,
    // connected tests fail at ComposeRule activity launch with a
    // ClassNotFoundException on ComponentActivity. Matches the wiring
    // in :feature:feed:impl and :feature:composer:impl.
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.turbine)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(project(":core:testing-android"))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    // Pin espresso-core explicitly — otherwise compose-ui-test-junit4 pulls a
    // stale transitive version whose InputManagerEventInjectionStrategy uses
    // a hidden API removed in Android 16 / API 36 (NoSuchMethodException on
    // InputManager.getInstance during ComposeRule activity launch).
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.ext.junit)

    kspAndroidTest(libs.hilt.android.compiler)
}
