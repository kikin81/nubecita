package net.kikin.nubecita.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Declares the `environment` product-flavor dimension (`production` + `bench`)
 * on a library module.
 *
 * Apply this ONLY to modules that ship a `src/bench/` source set — i.e. modules
 * that provide a network-free / deterministic parallel implementation for the
 * offline `:app:benchBenchmarkRelease` build that Macrobenchmark + baseline-profile
 * journeys run against. Each such module keeps its own module-specific comment (in
 * its `android {}` block) documenting *what* its bench flavor fakes; this plugin is
 * the single source of truth for the flavor/dimension *mechanics* that were
 * otherwise copy-pasted across ~18 build files.
 *
 * Modules WITHOUT a bench source set do NOT apply this — they stay single-variant
 * and pick up the `production` variant of any flavored dependency via the
 * `missingDimensionStrategy("environment", "production")` fallback in
 * [AndroidLibraryConventionPlugin]. Do not flavor a module that has no per-flavor
 * code: it would double its variant matrix for zero benefit.
 *
 * `:app` and `:benchmark` declare the dimension through their own convention
 * plugins (they carry extra flavor-scoped config — applicationId / google-services
 * handling on `:app`, the baseline-profile target on `:benchmark`); this add-on is
 * for the library/feature modules only, so it configures [LibraryExtension].
 *
 * The `withPlugin` guard makes application order irrelevant: whether this is listed
 * before or after `nubecita.android.library` in a module's `plugins {}` block, the
 * dimension is declared once `com.android.library` is present.
 */
class AndroidFlavorsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.withPlugin("com.android.library") {
                extensions.configure<LibraryExtension> {
                    flavorDimensions += "environment"
                    productFlavors {
                        create("production") { dimension = "environment" }
                        create("bench") { dimension = "environment" }
                    }
                }
            }
        }
    }
}
