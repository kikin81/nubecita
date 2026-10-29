import org.gradle.testing.jacoco.tasks.JacocoReport

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.screenshot) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.sort.dependencies) apply false
    alias(libs.plugins.spotless) apply false
    jacoco
}

jacoco {
    toolVersion = "0.8.16"
}

// Single aggregated coverage report consumed by CI's madrapps/jacoco-report
// step. Each Android module contributes its own jacocoTestReport via the
// nubecita.android.jacoco convention plugin and self-registers into this
// task's classDirectories / sourceDirectories / executionData inputs — so
// adding a module is zero-touch here.
tasks.register<JacocoReport>("jacocoTestReportAggregated") {
    group = "verification"
    description = "Aggregates JaCoCo unit-test coverage across every module."

    reports {
        xml.required.set(true)
        html.required.set(true)
        xml.outputLocation.set(
            layout.buildDirectory.file(
                "reports/jacoco/aggregated/jacocoTestReportAggregated.xml",
            ),
        )
        html.outputLocation.set(
            layout.buildDirectory.dir("reports/jacoco/aggregated/html"),
        )
    }
}

allprojects {
    // Implicit container projects (:core, :feature, :feature:chats, …) have no
    // build script and no sources of their own. Configuring them with a
    // recursive target made every module's tree — build/ outputs included — get
    // snapshotted once per ancestor, which dominated spotlessApply's runtime.
    if (this != rootProject && !buildFile.exists()) return@allprojects
    apply(plugin = "com.diffplug.spotless")
    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            // Scope each project to its own sources by rooting the tree at the
            // source directory, and keep the exclude inside that tree. String
            // target("src/**/*.kt") / targetExclude(...) patterns are each
            // resolved by walking the whole project dir (build/ included) —
            // at the root, the entire repo. The root owns only the build-logic
            // included build, which no subproject covers. (openspec/references/
            // — copies of external code kept for inspiration, not built or
            // linted — stays out because nothing targets it.)
            val sourceRoot = if (this@allprojects == rootProject) "build-logic/convention/src" else "src"
            target(
                fileTree(sourceRoot) {
                    include("**/*.kt")
                    exclude("**/generated/**")
                },
            )
            ktlint(libs.versions.ktlint.get())
                .customRuleSets(
                    listOf(
                        "io.nlopez.compose.rules:ktlint:${libs.versions.composeKtlintRules.get()}",
                    ),
                )
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktlint(libs.versions.ktlint.get())
        }
    }
}
