plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "net.kikin.nubecita.feature.moderation.api"
}

dependencies {
    api(libs.androidx.navigation3.runtime)
    api(libs.kotlinx.serialization.json)

    // PostUi — referenced by the `Report.forPost(post: PostUi)`
    // companion factory so call sites in feature host VMs (Feed,
    // PostDetail, Profile) collapse from the verbose 4-arg construction
    // to one line. Mirrors `:core:post-interactions`'s `:data:models`
    // dependency for the `PostUi.toShareIntent()` extension precedent.
    implementation(project(":data:models"))
}
