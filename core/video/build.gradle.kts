plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
}

android {
    namespace = "net.kikin.nubecita.core.video"

    // Required so JVM unit tests can call android.media.AudioAttributes.Builder()
    // without "Method ... not mocked" failures from the platform stub jar.
    // The Media3 audio-attributes path runs through java.net stubs that
    // need default-values to be tolerated. Same fix as :feature:feed:impl.
    testOptions.unitTests.isReturnDefaultValues = true
}

dependencies {
    // ExoPlayer types (Player, MediaItem, etc.) are part of the public surface
    // of `:core:video` — `VideoPlayerCoordinator` accepts a `Player` and exposes
    // ExoPlayer-shaped state to feature modules. The HLS module stays
    // `implementation` because no public surface here returns HLS-specific
    // types; consumers see plain MediaItem URIs.
    api(libs.media3.exoplayer)

    implementation(project(":core:billing"))
    implementation(project(":core:common"))
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
