plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "net.kikin.nubecita.core.feedmapping"
}

dependencies {
    // PostUi / EmbedUi / AuthorUi / ViewerStateUi appear in the public
    // signatures of the helpers, so consumers must see the data-models
    // types transitively. Same-shape `api` exposure as :data:models'
    // own surface.
    api(project(":data:models"))
    // PostView / ProfileViewBasic / ViewerState / PostViewEmbedUnion are
    // parameter / return types on the public helpers (toPostUiCore,
    // toEmbedUi, toAuthorUi, toViewerStateUi). Consumers in
    // :feature:feed:impl and :feature:postdetail:impl see them directly.
    api(libs.atproto.models)

    // AtField + UnknownOpenUnionMember are private to this module's
    // implementation; consumers don't need to see them.
    implementation(libs.atproto.runtime)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(project(":core:testing"))
}
