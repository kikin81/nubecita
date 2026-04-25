package net.kikin.nubecita.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * Meta-plugin for `:feature:*:impl` modules — applies library + compose +
 * hilt and wires in the dep set every feature module needs: `:core:common`
 * (MVI base classes), `:designsystem` (Compose theme + tokens), and the
 * common feature dep set (lifecycle-viewmodel-compose, hilt-navigation-compose,
 * navigation3, collections-immutable).
 *
 * Also enables Compose Preview screenshot tests — every `@PreviewTest`-
 * annotated preview is captured into `src/screenshotTest/.../reference` and
 * validated on subsequent runs. `:designsystem` doesn't apply this plugin
 * yet (no `@PreviewTest`s there), so the screenshot wiring lives here
 * rather than in `nubecita.android.library.compose`.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("nubecita.android.library")
            pluginManager.apply("nubecita.android.library.compose")
            pluginManager.apply("nubecita.android.hilt")
            pluginManager.apply("com.android.compose.screenshot")

            extensions.configure<LibraryExtension> {
                @Suppress("UnstableApiUsage")
                experimentalProperties["android.experimental.enableScreenshotTest"] = true
            }

            dependencies {
                "implementation"(project(":core:common"))
                "implementation"(project(":designsystem"))
                "implementation"(libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
                "implementation"(libs.findLibrary("androidx-lifecycle-runtime-compose").get())
                "implementation"(libs.findLibrary("androidx-hilt-navigation-compose").get())
                "implementation"(libs.findLibrary("androidx-navigation3-runtime").get())
                "implementation"(libs.findLibrary("androidx-navigation3-ui").get())
                "implementation"(libs.findLibrary("kotlinx-collections-immutable").get())
                "screenshotTestImplementation"(libs.findLibrary("androidx-compose-ui-tooling").get())
                "screenshotTestImplementation"(libs.findLibrary("screenshot-validation-api").get())
            }
        }
    }
}
