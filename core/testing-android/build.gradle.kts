plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
}

android {
    namespace = "net.kikin.nubecita.core.testing.android"
}

dependencies {
    // Consumers depend on this module via `androidTestImplementation`. These
    // deps are exposed as `api` so the consumer's androidTest classpath has
    // direct access to HiltAndroidRule, MockEngine builders, ComponentActivity,
    // etc. without re-declaring them at every consumer site.
    api(project(":core:auth")) // NetworkEngineModule::class for @TestInstallIn(replaces = ...)
    api(libs.androidx.activity.compose) // ComponentActivity (HiltTestActivity superclass)
    api(libs.androidx.test.runner) // AndroidJUnitRunner (HiltTestRunner superclass)
    api(libs.hilt.android.testing) // HiltAndroidRule, @HiltAndroidTest, HiltTestApplication
    api(libs.ktor.client.mock) // MockEngine, MockRequestHandleScope
}
