plugins {
    alias(libs.plugins.nubecita.android.library)
}

android {
    namespace = "net.kikin.nubecita.core.billing.api"
}

dependencies {
    // The billing boundary is expressed over our own :data:models subscription
    // types — no provider (RevenueCat) type ever crosses this api. Promoted to
    // `api` so consumers (features, :app) get the model + result types
    // transitively from a single `implementation(project(":core:billing:api"))`.
    api(project(":data:models"))
    api(libs.kotlinx.coroutines.core)
}
