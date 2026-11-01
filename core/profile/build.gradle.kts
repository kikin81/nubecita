plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
    alias(libs.plugins.nubecita.android.flavors)
}

android {
    namespace = "net.kikin.nubecita.core.profile"

    // The `environment` dimension swaps the production
    // CurrentAccountBindingsModule for a bench-flavor parallel that binds a
    // fixed, offline account (BenchCurrentAccountRepository).
}

dependencies {
    api(libs.kotlinx.serialization.json)

    implementation(project(":core:auth"))
    implementation(project(":core:common"))
    implementation(libs.atproto.models)
    implementation(libs.atproto.runtime)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.timber)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.mockk)
}
