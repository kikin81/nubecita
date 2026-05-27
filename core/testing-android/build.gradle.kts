plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
}

android {
    namespace = "net.kikin.nubecita.core.testing.android"
}

dependencies {
    // Consumers depend on this module via `androidTestImplementation`. The
    // remaining deps are exposed as `api` so the consumer's androidTest
    // classpath has direct access to HiltAndroidRule, MockEngine builders,
    // ComponentActivity, etc. without re-declaring them at every consumer
    // site.
    //
    // ComponentActivity (HiltTestActivity superclass) — non-Compose variant;
    // consumers pull Compose UI testing themselves.
    api(libs.androidx.activity)
    // AndroidJUnitRunner (HiltTestRunner superclass).
    api(libs.androidx.test.runner)
    // HiltAndroidRule, @HiltAndroidTest, HiltTestApplication.
    api(libs.hilt.android.testing)
    api(libs.ktor.client.mock)

    // The `:core:auth` reference is needed only for this module's own
    // compilation — `MockEngineModule` uses `NetworkEngineModule::class` in
    // its `@TestInstallIn(replaces = ...)` clause. Consumers' main code
    // already pulls `:core:auth` directly, so exposing it via `api` here
    // would just leak ABI without benefit.
    implementation(project(":core:auth"))
}
