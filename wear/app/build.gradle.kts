plugins {
    alias(libs.plugins.nubecita.android.wear.application)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "net.kikin.nubecita.wear"
    buildFeatures.buildConfig = true

    defaultConfig {
        // Must match the phone app's applicationId (and signing key): the Wear
        // Data Layer only pairs apps with the same package and signature, and
        // Play requires it for a Wear app with a phone companion (WO-G7).
        applicationId = "net.kikin.nubecita"
        // versionName / versionCode come from nubecita.android.wear.application
        // (the phone's semantic version, in the Wear versionCode band).
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:wear-contract"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.wear.compose.navigation3)
    implementation(libs.androidx.wear.phone.interactions)
    implementation(libs.androidx.wear.remote.interactions)
    implementation(libs.google.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)

    // fastlane screengrab: Play Store screenshots from the bench flavor
    // (nubecita-sn52.5). Test-only; run by the `wear_screenshots` lane.
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.screengrab)
}
