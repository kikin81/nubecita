package net.kikin.nubecita.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

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
