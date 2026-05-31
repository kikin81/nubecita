plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
}

android {
    namespace = "net.kikin.nubecita.core.billing"
}

dependencies {
    // The billing boundary is expressed over our own :data:models subscription
    // types — no provider (RevenueCat) type is ever exposed. The provider impl
    // stays `internal` to this module, so consumers can only reach it through
    // the public repository interfaces. `api` because both :data:models types
    // and StateFlow appear in those public interfaces' signatures.
    api(project(":data:models"))
    api(libs.kotlinx.coroutines.core)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
}
