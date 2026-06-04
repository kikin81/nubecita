plugins {
    alias(libs.plugins.nubecita.android.feature)
    // Generates the open-source library metadata (from the dependency graph)
    // consumed by the About → Open source licenses screen.
    alias(libs.plugins.aboutlibraries)
}

android {
    namespace = "net.kikin.nubecita.feature.settings.impl"

    defaultConfig {
        // Hilt-aware runner from :core:testing-android so @HiltAndroidTest
        // tests (SettingsInstrumentationTest) boot HiltTestApplication and
        // drive the @TestInstallIn-replaced component graph.
        testInstrumentationRunner = "net.kikin.nubecita.core.testing.android.HiltTestRunner"
    }
}

dependencies {
    api(project(":feature:settings:api"))

    implementation(project(":core:auth"))
    // EntitlementRepository.isPro / activeSubscription + BillingRepository
    // (restore, plan-price lookup) for the "Nubecita Pro" settings section.
    implementation(project(":core:billing"))
    implementation(project(":core:common"))
    implementation(project(":core:profile"))
    implementation(project(":data:models"))
    implementation(project(":designsystem"))
    // PaywallRoute NavKey — pushed onto MainShell's inner back stack when a
    // non-Pro user taps the "Nubecita Pro" upsell row. :api only.
    implementation(project(":feature:paywall:api"))
    // Profile NavKey — pushed onto MainShell's inner back stack when the user
    // taps the "Follow the developer" row (opens the developer's profile).
    // :api only (never :feature:profile:impl).
    implementation(project(":feature:profile:api"))
    implementation(libs.aboutlibraries.compose.m3)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
    implementation(libs.kotlinx.collections.immutable)
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
    // Needed by SettingsNotificationsRowInstrumentationTest to assert the
    // outgoing Intent shape (action + EXTRA_APP_PACKAGE) when tapping the
    // Notifications row, without actually launching the OS settings page.
    androidTestImplementation(libs.androidx.test.espresso.intents)
    androidTestImplementation(libs.androidx.test.ext.junit)
    // mockk-android needed for TestAuthRepositoryModule (relaxed mocks of
    // auth interfaces in the Hilt test graph — SettingsInstrumentationTest).
    androidTestImplementation(libs.mockk.android)

    kspAndroidTest(libs.hilt.android.compiler)
}
