plugins {
    alias(libs.plugins.nubecita.android.benchmark)
}

android {
    namespace = "net.kikin.nubecita.benchmark"
}

// `androidx.baselineprofile` (producer side) is applied by the
// convention plugin. The extension is configured per-test via
// `BaselineProfileRule` in the test classes; no module-level DSL block
// is needed for the producer.
