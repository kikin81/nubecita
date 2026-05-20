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
    // to one line. Exposed via `api(...)` (not `implementation`)
    // because `PostUi` appears in this module's public API surface;
    // consumers of `:feature:moderation:api` need it on their
    // compile/runtime classpath transitively. Same-shape `api`
    // exposure as `:core:feed-mapping`'s `:data:models` dependency.
    api(project(":data:models"))
}
