plugins {
    alias(libs.plugins.nubecita.android.feature)
}

android {
    namespace = "net.kikin.nubecita.feature.paywall.impl"
}

dependencies {
    api(project(":feature:paywall:api"))

    implementation(project(":core:billing"))
    implementation(project(":data:models"))
    // LocalActivity — the purchase flow hands the hosting Activity to
    // BillingRepository.purchase (design D5: the Composable supplies the
    // Activity, never the ViewModel).
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(libs.timber)

    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
