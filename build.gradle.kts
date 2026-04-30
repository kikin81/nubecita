// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.compose.screenshot) apply false
    alias(libs.plugins.firebase.appdistribution) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.sort.dependencies) apply false
    alias(libs.plugins.spotless) apply false
}

allprojects {
    apply(plugin = "com.diffplug.spotless")
    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("**/*.kt")
            // openspec/references/ is a handoff package: copies of code from external
            // sources (UI kits, sample apps) checked in for inspiration, intended to
            // be ported into modules with adjusted package names — not built or
            // linted in-place.
            targetExclude(
                "**/build/**/*.kt",
                "**/generated/**/*.kt",
                "openspec/references/**/*.kt",
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
