package net.kikin.nubecita.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Convention plugin for the Wear OS app (`:wear:app`, epic nubecita-sn52).
 *
 * Mirrors `nubecita.android.application` (Compose, Hilt, KSP, JaCoCo, the
 * screenshot test suite, release minification) with the Wear-specific differences:
 *
 * - `minSdk = 33` (Wear OS 4) instead of the phone's 28. `targetSdk` stays at
 *   the phone's 37 — Play requires ≥ 34 for new Wear apps.
 * - The UI toolkit is Wear Compose Material 3, not phone Material 3: no
 *   `androidx.compose.material3` and no `:designsystem`. The Compose BOM still
 *   aligns the shared `androidx.compose.*` artifacts (ui, runtime, foundation)
 *   that Wear Compose builds on; the Wear artifacts themselves are pinned in
 *   the catalog (`wearCompose`), since the BOM doesn't cover them.
 * - The same `environment` dimension as `:app` (`production` + `bench`).
 *   `bench` swaps the watch's phone-facing data sources for fakes (a signed-in
 *   session, no Data Layer) and pins the clock, so the Play Store screenshot
 *   lane captures deterministic, signed-in screens without a paired phone.
 * - Registers the `validateScreenshots` / `updateScreenshots` aliases, so CI's
 *   screenshot job validates the Wear baselines.
 * - Fails the build if a phone-only SDK (Jetpack XR, ARCore) reaches the
 *   watch's release classpath ([registerWearForbiddenDependencyCheck]).
 *
 * Versioning and release signing live here too: the same semantic version as
 * the phone with a Wear-band `versionCode` ([wearVersionCode]), and the
 * phone's upload key ([configureWearReleaseSigning]). `applicationId` stays in
 * the module's build file: it must equal the phone's (Data Layer pairing,
 * Play `WO-G7`).
 */
class AndroidWearApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("com.squareup.sort-dependencies")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            pluginManager.apply("com.google.dagger.hilt.android")
            pluginManager.apply("com.google.devtools.ksp")
            pluginManager.apply("nubecita.android.jacoco")

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)

                defaultConfig {
                    minSdk = WEAR_MIN_SDK
                    targetSdk = 37
                    // Same semantic version as the phone (gradle.properties
                    // `version`, bumped by semantic-release), in the Wear band.
                    val resolvedVersion = project.property("version").toString()
                    versionName = resolvedVersion
                    versionCode = wearVersionCode(resolvedVersion)
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }

                flavorDimensions += "environment"
                productFlavors {
                    create("production") { dimension = "environment" }
                    create("bench") { dimension = "environment" }
                }

                buildFeatures.compose = true

                buildTypes.getByName("debug").apply {
                    enableUnitTestCoverage = true
                }

                configureWearReleaseSigning(this)

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

            // Wear preview annotations (@WearPreviewDevices / @WearPreviewFontScales)
            // must also reach the screenshot suite.
            configureScreenshotTests("androidx-wear-compose-ui-tooling")
            registerScreenshotAliasTasks()
            configureComposeCompilerReports()
            registerWearForbiddenDependencyCheck()

            configureComposePreview()
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
