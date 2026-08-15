plugins {
    alias(libs.plugins.nubecita.android.library.compose)
    alias(libs.plugins.nubecita.android.hilt)
}

android {
    namespace = "net.kikin.nubecita.core.image"
}

dependencies {
    implementation(platform(libs.coil.bom))
    implementation(project(":core:common"))
    implementation(libs.androidx.activity.compose)
    // ImageSaver reads the already-downloaded bytes out of the shared Coil
    // disk cache rather than re-fetching them; see the change design's D2.
    implementation(libs.coil.core)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))

    // Instrumented tests for the MediaStore write glue (its pure cores —
    // content-type sniffing and filename generation — are JVM-unit-tested).
    // No Hilt: the impl is constructed directly with the instrumentation
    // targetContext, mirroring :core:posting's DefaultSharedMediaStoreTest.
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
