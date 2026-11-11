plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
    // Runs the @Serializable compiler plugin for the bench flavor's
    // BenchTimelineDto, which the offline fixture loader decodes. The
    // kotlinx-serialization-json runtime is already on the implementation
    // classpath for the cached blobs, but without the plugin the codegen for
    // this module's own @Serializable types never runs.
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.nubecita.android.flavors)
}

android {
    namespace = "net.kikin.nubecita.core.feedcache"
}

dependencies {
    // PostUi / FeedKey / FeedType and friends are the public surface of this
    // module's repositories, so consumers see the data-models types transitively.
    api(project(":data:models"))

    implementation(project(":core:analytics"))
    implementation(project(":core:auth"))
    implementation(project(":core:common"))
    implementation(project(":core:database"))
    implementation(project(":core:feed-mapping"))
    implementation(project(":core:feeds"))
    implementation(project(":core:moderation"))
    implementation(project(":core:post-interactions"))
    implementation(libs.androidx.paging.runtime)
    implementation(libs.atproto.models)
    implementation(libs.atproto.runtime)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.room.ktx)
    implementation(libs.timber)

    // Bench only: BenchFeedPostSource answers the bench PostRepository.getPost
    // from the feed fixtures, so a media viewer opened from a feed image shows
    // that post rather than the video fallback.
    benchImplementation(project(":core:posts"))

    testImplementation(project(":core:testing"))
    testImplementation(libs.androidx.paging.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
