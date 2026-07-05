plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
}

android {
    namespace = "net.kikin.nubecita.core.testing.android"
}

dependencies {
    // `MockEngineModule` and `TestLoggingModule` reference `NetworkEngineModule::class`
    // and `LoggingModule::class` in their `@TestInstallIn(replaces = ...)` clauses.
    // Exposed via `api` (not `implementation`) because Hilt's annotation processor
    // must resolve each replaced class on the *consumer's* androidTest compile
    // classpath when it generates that module's test component — and most consumers
    // don't declare `:core:auth` / `:core:logging` directly. Since this module is
    // consumed only via `androidTestImplementation`, `api` reaches the consumers'
    // androidTest compile classpath only; it never leaks into their main compile
    // classpath or production ABI.
    api(project(":core:auth"))
    api(project(":core:logging"))
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
}
