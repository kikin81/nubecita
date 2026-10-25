plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "net.kikin.nubecita.core.wearcontract"
}

// Shared by the phone app and the Wear app: the phone<->watch Data Layer
// contract. Deliberately free of atproto runtime / OAuth / Play services deps
// (same rule as :data:models) — the watch never talks to Bluesky, and the
// Data Layer client types stay in the modules that own the transport.
dependencies {
    api(libs.kotlinx.serialization.json)

    testImplementation(project(":core:testing"))
}
