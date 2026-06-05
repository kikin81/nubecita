package net.kikin.nubecita.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("com.squareup.sort-dependencies")
            pluginManager.apply("nubecita.android.jacoco")

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)

                defaultConfig {
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    consumerProguardFiles("consumer-rules.pro")

                    // Default fallback for the `environment` product-flavor dimension
                    // introduced by `:core:auth` and `:core:preferences` (and any
                    // future module that ships a fake-network swap for the `:app`
                    // `bench` flavor). Libraries that don't declare the `environment`
                    // dimension themselves pick the `production` variant of any
                    // flavored dependency. Libraries that DO declare it (the two
                    // named above + future cohorts) ignore this — AGP applies
                    // missingDimensionStrategy only when the dimension is missing
                    // from the current module.
                    //
                    // See `bd show nubecita-crmi.6` for the broader rationale and
                    // the source-set substitution pattern this enables.
                    missingDimensionStrategy("environment", "production")
                }

                buildTypes {
                    debug {
                        // Routes test execution through JaCoCo's runtime agent so
                        // testDebugUnitTest emits .exec files that the per-module
                        // jacocoTestReport task (and the root aggregated task) can
                        // read. Without this flag, AGP runs unit tests but no
                        // coverage data is produced.
                        enableUnitTestCoverage = true
                    }
                    release {
                        isMinifyEnabled = false
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro",
                        )
                    }
                }

                lint {
                    // XML-only; doesn't catch Compose Text("literal") calls. Compose hardcoded
                    // strings are caught by Slack's compose-lint-checks (added in
                    // nubecita.android.library.compose + nubecita.android.application).
                    error += "HardcodedText"
                    // Once a module ships values-b+es+419 / values-pt-rBR, every default
                    // string must be translated in each — fail the build on a gap so new
                    // strings can't ship untranslated (the translations are repo-owned, not
                    // Play-cloud). Modules with no locale folders are unaffected.
                    error += "MissingTranslation"
                }
            }

            configureJunitPlatform()
        }
    }
}
