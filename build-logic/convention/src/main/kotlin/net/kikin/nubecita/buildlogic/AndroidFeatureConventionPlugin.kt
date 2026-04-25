package net.kikin.nubecita.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * Meta-plugin for `:feature:*:impl` modules — applies library + compose +
 * hilt and wires in the dep set every feature module needs: `:core:common`
 * (MVI base classes), `:designsystem` (Compose theme + tokens), and the
 * common feature dep set (lifecycle-viewmodel-compose, hilt-navigation-compose,
 * navigation3, collections-immutable).
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("nubecita.android.library")
            pluginManager.apply("nubecita.android.library.compose")
            pluginManager.apply("nubecita.android.hilt")

            dependencies {
                "implementation"(project(":core:common"))
                "implementation"(project(":designsystem"))
                "implementation"(libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
                "implementation"(libs.findLibrary("androidx-lifecycle-runtime-compose").get())
                "implementation"(libs.findLibrary("androidx-hilt-navigation-compose").get())
                "implementation"(libs.findLibrary("androidx-navigation3-runtime").get())
                "implementation"(libs.findLibrary("androidx-navigation3-ui").get())
                "implementation"(libs.findLibrary("kotlinx-collections-immutable").get())
            }
        }
    }
}
