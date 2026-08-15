plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "net.kikin.nubecita.core.videoupload"
}

dependencies {
    // atproto types appear in the public surface: `VideoUploadState.Ready`
    // carries the `Blob` that `app.bsky.embed.video` needs and the
    // `AspectRatio` derived from the source's rotation-corrected dimensions.
    // Both must be `api` so `:core:posting` and the composer can read them
    // off the terminal state without re-declaring the dependency.
    api(libs.atproto.models)
    api(libs.atproto.runtime)

    implementation(project(":core:auth"))
    implementation(project(":core:common"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    // Client-side transcode. Bluesky caps an uploaded video at 100MB, but
    // 1080p30 phone capture runs ~20 Mbps — three minutes is roughly 450MB —
    // so without re-encoding the feature would reject most real recordings.
    // Unused until the compression stage lands (nubecita-uu6c.3); declared
    // here so the catalog entry and version resolution are proven now.
    implementation(libs.media3.transformer)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}
