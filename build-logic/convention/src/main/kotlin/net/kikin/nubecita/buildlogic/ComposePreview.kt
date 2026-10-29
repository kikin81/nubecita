package net.kikin.nubecita.buildlogic

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.HasHostTestsBuilder
import com.android.build.api.variant.HostTestBuilder
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Satisfies AGP 9.5's Compose Preview runfiles task, which AGP registers for
 * every variant with `buildFeatures.compose` on. Realizing that task throws
 * unless the variant has a unit-test component that packages compiled Android
 * resources (Layoutlib resolves themes and layout XML from them), so any build
 * that realizes every task — `./gradlew tasks`, which semantic-release runs,
 * or the `validateScreenshots` aliases — fails on variants that lack one.
 *
 * AGP 9 only creates unit tests for the tested (debug) build type by default,
 * and `androidx.baselineprofile` disables them on the `nonMinified*` /
 * `benchmark*` variants it adds, so re-enable the unit-test component on every
 * variant. Nothing in CI runs the non-debug unit-test tasks this creates.
 */
internal fun Project.configureComposePreview() {
    extensions.configure<CommonExtension> {
        testOptions.unitTests.isIncludeAndroidResources = true
    }
    val enableUnitTests = {
        val androidComponents = extensions.getByType(AndroidComponentsExtension::class.java)
        androidComponents.beforeVariants(androidComponents.selector().all()) { variant ->
            (variant as? HasHostTestsBuilder)
                ?.hostTests
                ?.get(HostTestBuilder.UNIT_TEST_TYPE)
                ?.enable = true
        }
    }
    enableUnitTests()
    // `:app` applies androidx.baselineprofile after this plugin; register
    // again once it's applied so this callback runs after the one that
    // disables unit tests on its variants.
    pluginManager.withPlugin("androidx.baselineprofile") { enableUnitTests() }
}
