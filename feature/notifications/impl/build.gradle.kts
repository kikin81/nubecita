plugins {
    alias(libs.plugins.nubecita.android.feature)
}

android {
    namespace = "net.kikin.nubecita.feature.notifications.impl"
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

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.turbine)
}
