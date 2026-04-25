package net.kikin.nubecita.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/**
 * SDK + JVM configuration shared by every Android module in the repo —
 * compileSdk, minSdk, Java 17 source/target, JVM 17 toolchain. Called
 * from inside each module-specific extension configure block.
 */
internal fun Project.configureKotlinAndroid(commonExtension: CommonExtension) {
    commonExtension.compileSdk = 37
    // Bumped 26 -> 28 to consume io.github.kikin81.atproto:compose-material3:5.1.0,
    // which targets minSdk 28. nubecita is pre-launch so no installed-base impact;
    // Android 8.0/8.1 (API 26-27) market share is < 2% as of 2026.
    commonExtension.defaultConfig.minSdk = 28
    commonExtension.compileOptions.sourceCompatibility = JavaVersion.VERSION_17
    commonExtension.compileOptions.targetCompatibility = JavaVersion.VERSION_17

    extensions.configure<KotlinAndroidProjectExtension> {
        jvmToolchain {
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }
}
