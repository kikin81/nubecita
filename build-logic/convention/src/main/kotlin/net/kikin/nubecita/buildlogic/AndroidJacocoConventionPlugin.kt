package net.kikin.nubecita.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport

/**
 * Wires the JaCoCo plugin into an Android module so its unit tests emit
 * coverage data that the root `jacocoTestReportAggregated` task can merge.
 *
 * Per-module: applies `jacoco`, pins the tool version, configures the
 * Test task's coverage agent, and registers `jacocoTestReport` over the
 * `debug` variant. Module wiring of `enableUnitTestCoverage = true` lives
 * in `AndroidLibraryConventionPlugin` / `AndroidApplicationConventionPlugin`
 * since those already hold the typed AGP extension handle.
 */
class AndroidJacocoConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("jacoco")

            extensions.configure<JacocoPluginExtension> {
                toolVersion = JACOCO_TOOL_VERSION
            }

            tasks.withType<Test>().configureEach {
                extensions.configure(JacocoTaskExtension::class.java) {
                    // Required for Robolectric / inline-class-heavy code paths;
                    // without this, jacoco drops classes with no SourceFile attribute.
                    isIncludeNoLocationClasses = true
                    excludes = listOf("jdk.internal.*")
                }
            }

            val moduleReport =
                tasks.register<JacocoReport>("jacocoTestReport") {
                    group = "verification"
                    description = "Generates a JaCoCo unit-test coverage report for the debug variant."
                    dependsOn("testDebugUnitTest")

                    reports {
                        xml.required.set(true)
                        html.required.set(true)
                    }

                    classDirectories.setFrom(jacocoClassDirectories())
                    sourceDirectories.setFrom(jacocoSourceDirectories())
                    executionData.setFrom(jacocoExecutionData())
                }

            // Self-register this module's coverage into the root aggregated
            // report. Gradle evaluates :root before subprojects, so by the
            // time this plugin runs the root jacocoTestReportAggregated task
            // is already registered. Doing the wiring here (instead of a
            // root-side `subprojects { plugins.withId(...) }` block) avoids
            // an ordering bug where the withId callback fires during jacoco's
            // own apply() — before this plugin has registered jacocoTestReport.
            rootProject.tasks.named<JacocoReport>("jacocoTestReportAggregated") {
                dependsOn(moduleReport)
                classDirectories.from(moduleReport.map { it.classDirectories })
                sourceDirectories.from(moduleReport.map { it.sourceDirectories })
                executionData.from(moduleReport.map { it.executionData })
            }
        }
    }
}

internal const val JACOCO_TOOL_VERSION = "0.8.14"

internal val jacocoClassExcludes =
    listOf(
        "**/R.class",
        "**/R\$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*",
        "**/*\$Lambda\$*.*",
        "**/*\$inlined\$*.*",
        "**/*ComposableSingletons*.*",
        "**/Hilt_*.class",
        "**/*_HiltModules*.*",
        "**/*_Factory*.*",
        "**/*_MembersInjector*.*",
        "**/*_Provide*Factory*.*",
        "**/*_GeneratedInjector*.*",
        "**/hilt_aggregated_deps/**",
        "**/dagger/hilt/internal/**",
    )

internal fun Project.jacocoClassDirectories(): ConfigurableFileCollection =
    files(
        // AGP 9 emits Kotlin compile output here. The pre-AGP-9 path
        // (build/tmp/kotlin-classes/debug) is no longer populated, so
        // referencing it produces empty per-module reports.
        fileTree(layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes")) {
            exclude(jacocoClassExcludes)
        },
        fileTree(layout.buildDirectory.dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes")) {
            exclude(jacocoClassExcludes)
        },
    )

internal fun Project.jacocoSourceDirectories(): ConfigurableFileCollection =
    files(
        "src/main/java",
        "src/main/kotlin",
    )

internal fun Project.jacocoExecutionData(): ConfigurableFileCollection =
    files(
        fileTree(layout.buildDirectory) {
            include(
                "jacoco/testDebugUnitTest.exec",
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
            )
        },
    )
