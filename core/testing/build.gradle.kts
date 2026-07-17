plugins {
    alias(libs.plugins.nubecita.android.library)
}

android {
    namespace = "net.kikin.nubecita.core.testing"
}

dependencies {
    // RecordingAnalyticsClient implements AnalyticsClient and exposes lists of
    // AnalyticsEvent / AnalyticsScreen / UserProperty in its public API, so the
    // analytics types must reach consumers transitively — hence `api`. No cycle:
    // :core:analytics only depends back on :core:testing in its test config. The
    // `environment` flavor resolves to `production` via the library convention
    // plugin's missingDimensionStrategy.
    api(project(":core:analytics"))
    // Test fixtures consumed under src/test/ in downstream modules. Promoted to
    // `api` so consumers' `testImplementation(project(":core:testing"))` lines
    // pull these onto the test classpath transitively without re-declaring them.
    api(libs.junit.jupiter.api)
    api(libs.kotlinx.coroutines.test)
}
