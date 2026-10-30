package net.kikin.nubecita.buildlogic

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.AndroidComponentsExtension
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType

/** The AGP screenshot test suite's name — also its `src/<name>/kotlin` source-set dir. */
internal const val SCREENSHOT_SUITE = "screenshotTest"

/**
 * Wires Compose Preview screenshot tests through AGP's test-suite DSL
 * (`testOptions.screenshotTests`), the replacement for the deprecated
 * standalone `com.android.compose.screenshot` plugin. Every
 * `@PreviewTest`-annotated preview in `src/screenshotTest/kotlin` is captured
 * into `src/screenshotTestDefault<Variant>/reference` and diffed on
 * subsequent runs.
 *
 * The suite targets exactly one variant: the canonical debug one —
 * `productionDebug` for modules carrying the `environment` dimension,
 * `debug` otherwise — so the throwaway `bench` flavor and the `release`
 * build type are never captured. `nubecita.android.flavors` is applied
 * after this plugin, so the choice waits for `finalizeDsl`, when the
 * flavors are known and the DSL is still mutable.
 *
 * Engine 0.0.1-alpha17 is the floor: alpha16 under test suites leaves the
 * merged R.jar off the renderer classpath, so every render fails with
 * `NoClassDefFoundError: …/R$id`.
 *
 * [extraDependencies] are catalog aliases added to the suite's
 * `implementation` on top of `ui-tooling` + the validation API.
 */
internal fun Project.configureScreenshotTests(vararg extraDependencies: String) {
    val catalogDeps =
        listOf(*extraDependencies, "androidx-compose-ui-tooling", "screenshot-validation-api")
    extensions.configure<CommonExtension> {
        testOptions.screenshotTests.create(SCREENSHOT_SUITE) {
            engineVersion = libs.findVersion("screenshot").get().requiredVersion
            dependencies {
                catalogDeps.forEach { implementation.add(libs.findLibrary(it).get()) }
            }
        }
    }
    extensions.getByType(AndroidComponentsExtension::class.java).finalizeDsl { extension ->
        val android = extension as CommonExtension
        val flavored = android.productFlavors.any { it.name == "production" }
        android.testOptions.screenshotTests
            .getByName(SCREENSHOT_SUITE)
            .targetVariants
            .add(if (flavored) "productionDebug" else "debug")
    }

    // Gradle's Test task defaults to failing when no tests are discovered.
    // Compose-using modules that don't (yet) ship a @PreviewTest legitimately
    // have an empty screenshotTest source set; let those pass for both the
    // test and update tasks so the aggregate aliases stay usable across the
    // whole build.
    tasks.withType<Test>().configureEach {
        if (name in CANONICAL_SCREENSHOT_TASKS) {
            failOnNoDiscoveredTests.set(false)
        }
    }
}

/**
 * The canonical debug-variant screenshot suite tasks, `<test|update><Variant>`
 * + `ScreenshotTestDefaultTestSuite`. A module is either flavored
 * (`production` / `bench` `environment` dimension → `…ProductionDebug…`) or
 * not (`…Debug…`); the suite only ever targets one of them (see
 * [configureScreenshotTests]), so the two name sets are disjoint per module.
 */
internal val CANONICAL_SCREENSHOT_TASKS =
    setOf(
        "testDebugScreenshotTestDefaultTestSuite",
        "updateDebugScreenshotTestDefaultTestSuite",
        "testProductionDebugScreenshotTestDefaultTestSuite",
        "updateProductionDebugScreenshotTestDefaultTestSuite",
    )

/**
 * Register flavor-agnostic `validateScreenshots` / `updateScreenshots` lifecycle
 * aliases that fan out to whichever canonical debug-variant suite task this
 * module actually has. CI then runs a single `./gradlew validateScreenshots`
 * (and the baseline workflow a single `./gradlew updateScreenshots`) across
 * every Compose library + feature module — flavored or not — instead of
 * hand-listing each flavored module's task.
 *
 * `tasks.matching { … }` is a live collection, so the dependency resolves once
 * AGP has registered the suite tasks; depending on whichever of the disjoint
 * debug names exists selects exactly the right one.
 *
 * Applied by `nubecita.android.library.compose` and
 * `nubecita.android.wear.application` (its baselines are CI-generated).
 * Deliberately NOT applied to `:app`: its screenshot baselines are
 * Mac-generated and its CI validation is a separately-tracked deferred gap —
 * pulling it into the aggregate would surface that drift.
 */
internal fun Project.registerScreenshotAliasTasks() {
    tasks.register("validateScreenshots") {
        group = "verification"
        description = "Validate Compose screenshot baselines for this module's canonical debug variant."
        dependsOn(tasks.matching { it.name in CANONICAL_SCREENSHOT_TASKS && it.name.startsWith("test") })
    }
    tasks.register("updateScreenshots") {
        group = "verification"
        description = "Regenerate Compose screenshot baselines for this module's canonical debug variant."
        dependsOn(tasks.matching { it.name in CANONICAL_SCREENSHOT_TASKS && it.name.startsWith("update") })
    }
}
