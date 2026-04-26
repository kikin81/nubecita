plugins {
    alias(libs.plugins.nubecita.android.library)
}

android {
    namespace = "net.kikin.nubecita.data.models"
}

dependencies {
    // AT Protocol wire-data primitives are explicitly allowed in :data:models per
    // openspec change add-postcard-component, design Decision 2 + the data-models
    // capability spec. atproto:models exposes Facet, Did, Handle, AtUri, Datetime
    // — lexicon primitive types that downstream UI models consume directly.
    api(libs.atproto.models)
    api(platform(libs.androidx.compose.bom))
    // Compose runtime is the ONLY Compose dep here: needed for @Stable on UI
    // model data classes so Compose's stability inference doesn't mark them
    // unstable (which would defeat LazyColumn skip behavior on a feed list).
    api(libs.androidx.compose.runtime)
    api(libs.kotlinx.collections.immutable)
    api(libs.kotlinx.datetime)
}
