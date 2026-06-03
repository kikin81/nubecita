package net.kikin.nubecita.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("nubecita.android.library")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            // Compose Preview screenshot test plugin — every @PreviewTest-annotated
            // preview function is captured into src/screenshotTest/.../reference and
            // diffed on subsequent runs. Lives here (not in nubecita.android.library)
            // so non-Compose libraries don't pay the wiring cost. Consumers that don't
            // ship @PreviewTests pay nothing — empty src/screenshotTest/ is fine.
            pluginManager.apply("com.android.compose.screenshot")

            extensions.configure<LibraryExtension> {
                buildFeatures.compose = true

                @Suppress("UnstableApiUsage")
                experimentalProperties["android.experimental.enableScreenshotTest"] = true
            }

            // Gradle's Test task defaults to failing when no tests are discovered.
            // Compose-using libs that don't (yet) ship a @PreviewTest legitimately
            // have an empty screenshotTest source set; let those modules pass for
            // both the validate and update tasks so the aggregate screenshot tasks
            // stay usable across the whole build. Covers the canonical debug
            // variant for both flavored (`production`) and non-flavored modules —
            // the two name sets are disjoint per module.
            tasks.withType<Test>().configureEach {
                if (name in CANONICAL_SCREENSHOT_TASKS) {
                    failOnNoDiscoveredTests.set(false)
                }
            }

            registerScreenshotAliasTasks()

            configureComposeCompilerReports()

            dependencies {
                val bom = libs.findLibrary("androidx-compose-bom").get()
                "implementation"(platform(bom))
                "implementation"(libs.findLibrary("androidx-compose-ui").get())
                "implementation"(libs.findLibrary("androidx-compose-material3").get())
                "implementation"(libs.findLibrary("androidx-compose-ui-tooling-preview").get())
                "debugImplementation"(libs.findLibrary("androidx-compose-ui-tooling").get())
                "screenshotTestImplementation"(libs.findLibrary("androidx-compose-ui-tooling").get())
                "screenshotTestImplementation"(libs.findLibrary("screenshot-validation-api").get())
                // Slack's Compose lint rules — flags hardcoded Text("…") calls, missing
                // Modifier defaults, ViewModel-injection-in-Composable issues, preview-naming
                // violations, etc. Pinned 1.4.2 (last tagged release Oct 2024); the repo is
                // actively tracking AGP 9 + Kotlin 2.3 via dep bumps. Catches the Compose
                // hardcoded-string regression that the platform lint HardcodedText rule misses.
                "lintChecks"(libs.findLibrary("slack-compose-lints").get())
            }
        }
    }
}

/**
 * The canonical debug-variant screenshot tasks. A module is either flavored
 * (`production` / `bench` `environment` dimension → `…ProductionDebug…`) or
 * not (`…Debug…`); the two name sets are disjoint per module, and neither set
 * includes the throwaway `bench` flavor or the `release` build type — so the
 * aggregate aliases below never validate the fake-network or release variants.
 */
private val CANONICAL_SCREENSHOT_TASKS =
    setOf(
        "validateDebugScreenshotTest",
        "updateDebugScreenshotTest",
        "validateProductionDebugScreenshotTest",
        "updateProductionDebugScreenshotTest",
    )

/**
 * Register flavor-agnostic `validateScreenshots` / `updateScreenshots` lifecycle
 * aliases that fan out to whichever canonical debug-variant screenshot task this
 * module actually has. CI then runs a single `./gradlew validateScreenshots`
 * (and the baseline workflow a single `./gradlew updateScreenshots`) across
 * every Compose library + feature module — flavored or not — instead of
 * hand-listing each flavored module's `validateProductionDebugScreenshotTest`.
 *
 * `tasks.matching { … }` is a live collection, so the dependency resolves once
 * the AGP screenshot plugin has registered its per-variant tasks; depending on
 * whichever of the disjoint debug names exists selects exactly the right one.
 *
 * Deliberately NOT applied to `:app` (its plugin doesn't call this): `:app`'s
 * screenshot baselines are Mac-generated and its CI validation is a separately-
 * tracked deferred gap — pulling it into the aggregate would surface that drift.
 */
private fun Project.registerScreenshotAliasTasks() {
    tasks.register("validateScreenshots") {
        group = "verification"
        description = "Validate Compose screenshot baselines for this module's canonical debug variant."
        dependsOn(
            tasks.matching {
                it.name == "validateProductionDebugScreenshotTest" ||
                    it.name == "validateDebugScreenshotTest"
            },
        )
    }
    tasks.register("updateScreenshots") {
        group = "verification"
        description = "Regenerate Compose screenshot baselines for this module's canonical debug variant."
        dependsOn(
            tasks.matching {
                it.name == "updateProductionDebugScreenshotTest" ||
                    it.name == "updateDebugScreenshotTest"
            },
        )
    }
}
