plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
}

android {
    namespace = "net.kikin.nubecita.core.logging"

    // Mirrors `:core:analytics`: the `environment` dimension swaps the production
    // LoggingModule (binds the real FirebaseCrashReporter) for a bench-flavor
    // parallel that binds NoOpCrashReporter, so screenshot / baseline-profile /
    // Macrobenchmark runs never link (or transitively pull) Firebase Crashlytics.
    // The two LoggingModule files share an FQN so downstream feature
    // instrumentation tests can swap either via
    // `@TestInstallIn(replaces = [LoggingModule::class])`. Consumers without the
    // dimension pick up `production` via the missingDimensionStrategy in the
    // library convention plugin.
    flavorDimensions += "environment"
    productFlavors {
        create("production") { dimension = "environment" }
        create("bench") { dimension = "environment" }
    }
}

dependencies {
    // CrashlyticsTree is a Timber.Tree; the seam is provider-agnostic Timber.
    implementation(libs.timber)

    // :core:testing brings the JUnit Jupiter runner. The unit tests here are
    // pure-JVM assertions over the tree's priority filter + synthetic-exception
    // path using a fake CrashReporter — no flows or mocks.
    testImplementation(project(":core:testing"))

    // Firebase is a production-only dependency: the `bench` flavor binds
    // NoOpCrashReporter and never links (or transitively pulls) Firebase.
    "productionImplementation"(platform(libs.firebase.bom))
    "productionImplementation"(libs.firebase.crashlytics)
}
