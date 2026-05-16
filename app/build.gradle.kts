import com.google.firebase.appdistribution.gradle.firebaseAppDistribution
import java.util.Properties

plugins {
    alias(libs.plugins.nubecita.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.appdistribution)
    alias(libs.plugins.firebase.crashlytics)
}

// Release signing material is loaded from `keystore.properties` (gitignored)
// when present, otherwise from env vars so CI can inject secrets without
// writing a file. Both sources are optional — when neither resolves a
// storeFile, the release signingConfig stays unconfigured and `bundleRelease`
// will fail with AGP's standard "no signing config" error rather than silently
// producing a debug-signed release.
val keystoreProps =
    Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use(::load)
    }

fun keystoreValue(
    propKey: String,
    envKey: String,
): String? =
    keystoreProps.getProperty(propKey)?.takeIf(String::isNotEmpty)
        ?: providers.environmentVariable(envKey).orNull?.takeIf(String::isNotEmpty)

/**
 * Derives Android's `versionCode` (a strictly-increasing 32-bit integer
 * required by Play Store) from the project's semantic `versionName`
 * (`MAJOR.MINOR.PATCH`).
 *
 * Scheme: `versionCode = MAJOR * 1_000_000 + MINOR * 1_000 + PATCH`
 *
 * Examples:
 *   1.37.1 → 1_037_001
 *   1.99.999 → 1_099_999  (per-band ceiling)
 *   2.0.0  → 2_000_000   (always > any 1.x.x — no collision on breaking releases)
 *
 * Why fixed-width bands and not the naive concatenation
 * (`MAJOR*100 + MINOR*10 + PATCH`)? The naive scheme breaks on major
 * bumps: 2.0.0 → 200, but 1.15.0 → 1150, so Play Store rejects 2.0.0
 * as ≤ the prior published 1.15.0. The 1_000_000-band scheme leaves
 * 999 minors and 999 patches per major and ~2100 majors before
 * Android's 2.1B versionCode ceiling — plenty of headroom for the
 * lifetime of the project.
 *
 * Pre-release suffixes (`1.38.0-rc.1`, `1.37.1-SNAPSHOT`) are stripped
 * at the first `-`. SemVer 2.0 build-metadata suffixes (`1.2.3+build.4`)
 * are stripped at the first `+`. RC vs final ordering at the same
 * versionName is out of scope — handle via separate `applicationId`
 * for beta tracks.
 *
 * Out-of-band components fail fast at configuration time so the build
 * halts before producing a malformed AAB:
 *   - major must be in `0..2146` (`2147 * 1_000_000` overflows `Int`;
 *     2146 is the largest major that leaves room for any 0..999/0..999
 *     minor+patch and still fits in a 32-bit signed `versionCode`)
 *   - minor and patch must each be in `0..999`
 *   - the arithmetic is done in `Long` and the result is bounds-checked
 *     against `Int.MAX_VALUE` before narrowing, so any future scheme
 *     change can't silently produce a negative `versionCode`
 */
fun parseVersionCode(versionName: String): Int {
    val core = versionName.substringBefore('-').substringBefore('+')
    val parts = core.split('.')
    require(parts.size == 3) {
        "Expected MAJOR.MINOR.PATCH, got: $versionName"
    }
    val major = parts[0].toInt()
    val minor = parts[1].toInt()
    val patch = parts[2].toInt()
    require(major in 0..2146) {
        "Major must be in 0..2146 to fit a 32-bit signed versionCode: $versionName"
    }
    require(minor in 0..999 && patch in 0..999) {
        "Minor and patch must be in 0..999 to fit the 1_000_000-band scheme: $versionName"
    }
    val code = major.toLong() * 1_000_000 + minor.toLong() * 1_000 + patch
    check(code in 0..Int.MAX_VALUE) {
        "versionCode $code overflowed Int.MAX_VALUE; tighten the per-component bounds"
    }
    return code.toInt()
}

android {
    namespace = "net.kikin.nubecita"

    signingConfigs {
        create("release") {
            keystoreValue("storeFile", "KEYSTORE_FILE")?.let { raw ->
                // Allow `~/...` in keystore.properties so the file stays
                // portable across machines without hard-coding $HOME.
                val expanded =
                    if (raw.startsWith("~")) {
                        raw.replaceFirst("~", System.getProperty("user.home"))
                    } else {
                        raw
                    }
                storeFile = file(expanded)
                storePassword = keystoreValue("storePassword", "KEYSTORE_PASSWORD")
                keyAlias = keystoreValue("keyAlias", "KEY_ALIAS")
                keyPassword = keystoreValue("keyPassword", "KEY_PASSWORD")
            }
        }
    }

    defaultConfig {
        // Resolve the version once from the project property and use it
        // for both versionName and the parser-derived versionCode. Avoids
        // reading back AGP's nullable DSL property and forcing it
        // non-null with `!!`, and removes any reliance on the order of
        // the two DSL setters.
        val resolvedVersion = project.property("version").toString()
        applicationId = "net.kikin.nubecita"
        versionName = resolvedVersion
        versionCode = parseVersionCode(resolvedVersion)

        // Override the convention-plugin default (AndroidJUnitRunner) with the
        // Hilt-aware runner from :core:testing-android so @HiltAndroidTest tests
        // can boot HiltTestApplication. :app currently has no @HiltAndroidTest
        // tests itself (FirebaseInitTest + MainShellPersistenceTest are plain
        // AndroidJUnit4); the runner stays Hilt-compatible for forthcoming
        // cross-feature E2E tests.
        testInstrumentationRunner = "net.kikin.nubecita.core.testing.android.HiltTestRunner"

        buildConfigField(
            type = "String",
            name = "OAUTH_CLIENT_METADATA_URL",
            value = "\"https://nubecita.app/oauth/client-metadata.json\"",
        )
        buildConfigField(
            type = "String",
            name = "OAUTH_SCOPE",
            value = "\"atproto transition:generic transition:chat.bsky\"",
        )
    }

    buildTypes {
        debug {
            firebaseAppDistribution {
                groups = "internal-testers"
                // providers.environmentVariable(...) is configuration-cache-aware:
                // Gradle tracks the env var as an input and invalidates the cache
                // when it changes between runs, unlike System.getenv() which bakes
                // the value into the cache silently.
                releaseNotes = providers.environmentVariable("APP_DISTRIBUTION_RELEASE_NOTES").orElse("").get()
            }
        }
        release {
            // Only attach the signing config when a real keystore was resolved.
            // Leaving it null otherwise means `bundleRelease` fails fast on a
            // missing config rather than silently shipping an unsigned (or
            // debug-signed via -PdebugSignedRelease) artifact.
            if (signingConfigs.getByName("release").storeFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        baseline = file("lint-baseline.xml")
        warningsAsErrors = false
        abortOnError = true
    }
}

dependencies {
    implementation(platform(libs.coil.bom))
    implementation(platform(libs.firebase.bom))
    implementation(project(":core:auth"))
    implementation(project(":core:common"))
    // :core:posting types (ComposerError, ComposerAttachment) surface in
    // :feature:composer:impl's public ComposerScreenContent signature —
    // :app needs them on its compile classpath to construct overlay
    // screenshots that call ComposerScreenContent directly. `ActorUi`
    // used to live here as `ActorTypeaheadUi` but was promoted to
    // :data:models in nubecita-vrba.4 and is now reachable transitively.
    implementation(project(":core:posting"))
    implementation(project(":designsystem"))
    implementation(project(":feature:chats:api"))
    implementation(project(":feature:chats:impl"))
    implementation(project(":feature:composer:api"))
    implementation(project(":feature:composer:impl"))
    implementation(project(":feature:feed:api"))
    implementation(project(":feature:feed:impl"))
    implementation(project(":feature:login:api"))
    implementation(project(":feature:login:impl"))
    implementation(project(":feature:mediaviewer:impl"))
    implementation(project(":feature:postdetail:api"))
    implementation(project(":feature:postdetail:impl"))
    implementation(project(":feature:profile:api"))
    implementation(project(":feature:profile:impl"))
    implementation(project(":feature:search:api"))
    implementation(project(":feature:search:impl"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.atproto.models)
    implementation(libs.atproto.runtime)
    implementation(libs.coil.core)
    implementation(libs.coil.network.okhttp)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.crashlytics)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.timber)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.firebase.appcheck.debug)

    testImplementation(project(":core:testing"))
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(project(":core:testing-android"))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.espresso.intents)
    androidTestImplementation(libs.androidx.test.ext.junit)

    kspAndroidTest(libs.hilt.android.compiler)
}

tasks.register("publish") {
    description = "No-op publish task to satisfy semantic-release verification in CI"
    group = "publishing"
}
