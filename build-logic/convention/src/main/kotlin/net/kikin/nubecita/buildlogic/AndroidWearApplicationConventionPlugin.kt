package net.kikin.nubecita.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/**
 * Convention plugin for the Wear OS app (`:wear:app`, epic nubecita-sn52).
 *
 * Mirrors `nubecita.android.application` (Compose, Hilt, KSP, JaCoCo, the AGP
 * screenshot plugin, release minification) with the Wear-specific differences:
 *
 * - `minSdk = 33` (Wear OS 4) instead of the phone's 28. `targetSdk` stays at
 *   the phone's 37 — Play requires ≥ 34 for new Wear apps.
 * - The UI toolkit is Wear Compose Material 3, not phone Material 3: no
 *   `androidx.compose.material3` and no `:designsystem`. The Compose BOM still
 *   aligns the shared `androidx.compose.*` artifacts (ui, runtime, foundation)
 *   that Wear Compose builds on; the Wear artifacts themselves are pinned in
 *   the catalog (`wearCompose`), since the BOM doesn't cover them.
 * - No product flavors: the watch never talks to the network, so there is no
 *   bench/production split to make.
 * - Registers the `validateScreenshots` / `updateScreenshots` aliases, so CI's
 *   screenshot job validates the Wear baselines.
 *
 * `applicationId` and versioning stay in the module's build file: the Wear app
 * must share the phone's applicationId and signing key (Data Layer pairing,
 * Play `WO-G7`), and its version-code scheme is owned by the release pipeline.
 */
class AndroidWearApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("com.squareup.sort-dependencies")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.apply("com.android.compose.screenshot")
            pluginManager.apply("com.google.dagger.hilt.android")
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("nubecita.android.jacoco")

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)

                defaultConfig {
                    minSdk = WEAR_MIN_SDK
                    targetSdk = 37
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }

                buildFeatures.compose = true

                @Suppress("UnstableApiUsage")
                experimentalProperties["android.experimental.enableScreenshotTest"] = true

                buildTypes.getByName("debug").apply {
                    enableUnitTestCoverage = true
                }

                buildTypes.getByName("release").apply {
                    isMinifyEnabled = true
                    isShrinkResources = true
                    proguardFiles(
                        getDefaultProguardFile("proguard-android-optimize.txt"),
                        "proguard-rules.pro",
                    )
                    if ((findProperty("debugSignedRelease") as? String).toBoolean()) {
                        signingConfig = signingConfigs.getByName("debug")
                    }
                }

                lint {
                    error += "HardcodedText"
                    error += "MissingTranslation"
                    error += "StringFormatCount"
                    error += "StringFormatInvalid"
                    error += "StringFormatMatches"
                }
            }

            tasks.withType<Test>().configureEach {
                if (name in CANONICAL_SCREENSHOT_TASKS) {
                    failOnNoDiscoveredTests.set(false)
                }
            }

            registerScreenshotAliasTasks()
            configureComposeCompilerReports()
            configureJunitPlatform()

            dependencies {
                val bom = libs.findLibrary("androidx-compose-bom").get()
                "implementation"(platform(bom))
                "implementation"(libs.findLibrary("androidx-wear-compose-foundation").get())
                "implementation"(libs.findLibrary("androidx-wear-compose-material3").get())
                "implementation"(libs.findLibrary("hilt-android").get())
                "debugImplementation"(libs.findLibrary("androidx-compose-ui-tooling").get())
                // Wear preview annotations (@WearPreviewDevices / @WearPreviewFontScales):
                // previews and screenshot tests only, never the release app.
                "debugImplementation"(libs.findLibrary("androidx-wear-compose-ui-tooling").get())
                "screenshotTestImplementation"(libs.findLibrary("androidx-wear-compose-ui-tooling").get())
                "screenshotTestImplementation"(libs.findLibrary("androidx-compose-ui-tooling").get())
                "screenshotTestImplementation"(libs.findLibrary("screenshot-validation-api").get())
                "ksp"(libs.findLibrary("hilt-android-compiler").get())
                "lintChecks"(libs.findLibrary("slack-compose-lints").get())
            }
        }
    }

    private companion object {
        /** Wear OS 4. */
        const val WEAR_MIN_SDK = 33
    }
}
