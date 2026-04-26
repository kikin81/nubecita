plugins {
    alias(libs.plugins.nubecita.android.library)
}

android {
    namespace = "net.kikin.nubecita.core.testing"
}

dependencies {
    // Test fixtures consumed under src/test/ in downstream modules. Promoted to
    // `api` so consumers' `testImplementation(project(":core:testing"))` lines
    // pull these onto the test classpath transitively without re-declaring them.
    api(libs.junit.jupiter.api)
    api(libs.kotlinx.coroutines.test)
}
