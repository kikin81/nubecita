plugins {
    alias(libs.plugins.nubecita.android.library)
    alias(libs.plugins.nubecita.android.hilt)
}

android {
    namespace = "net.kikin.nubecita.core.analytics"

    // FirebaseAnalyticsClient gates its debug-only GA4 name validator on
    // BuildConfig.DEBUG so a malformed event/param/property name fails loudly
    // in debug + unit tests but costs nothing in release.
    buildFeatures.buildConfig = true

    // Mirrors `:core:auth`/`:core:preferences`: the `environment` dimension
    // swaps the production AnalyticsModule (binds the real
    // FirebaseAnalyticsClient + provides FirebaseAnalytics) for a bench-flavor
    // parallel that binds NoOpAnalyticsClient, so screenshot/baseline/bench
    // runs send zero analytics. The two AnalyticsModule files share an FQN so
    // downstream feature instrumentation tests can swap either via
    // `@TestInstallIn(replaces = [AnalyticsModule::class])`. Consumers without
    // the dimension pick up `production` via the missingDimensionStrategy in
    // the library convention plugin. See `bd show nubecita-049f.1`.
    flavorDimensions += "environment"
    productFlavors {
        create("production") { dimension = "environment" }
        create("bench") { dimension = "environment" }
    }
}

dependencies {
    // :core:testing brings the JUnit Jupiter runner (api) + the
    // MainDispatcherExtension toolkit. This module's unit tests are pure-JVM
    // assertions over the typed model + name validator — no flows (Turbine) or
    // mocks (MockK) to exercise — so those toolkit deps are intentionally omitted.
    testImplementation(project(":core:testing"))

    // Firebase is a production-only dependency: the `bench` flavor binds
    // NoOpAnalyticsClient and never links (or transitively pulls) Firebase.
    "productionImplementation"(platform(libs.firebase.bom))
    "productionImplementation"(libs.firebase.analytics)
    // Task.await() for the Firebase app-instance-id lookup — the same bridge
    // :core:push uses for the FCM token (FirebaseFcmTokenProvider).
    "productionImplementation"(libs.kotlinx.coroutines.play.services)
}
