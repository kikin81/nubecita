import java.util.Properties

plugins {
    alias(libs.plugins.nubecita.android.application)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
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

// local.properties is gitignored and the conventional home for machine-local
// secrets (alongside sdk.dir). `providers.gradleProperty(...)` does NOT read it,
// so load it explicitly for the RevenueCat key fallback below.
val localProperties =
    Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) f.inputStream().use(::load)
    }

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

        // Gradle-property overrides for the two OAuth identity fields, so a one-off
        // build can point at a test client_id (or a staging PDS, or anything else with
        // a different metadata URL / redirect target) via -PoauthClientMetadataUrl=...
        // -PoauthRedirectUri=... without editing this file. Defaults below are the
        // current production values.
        val oauthClientMetadataUrl =
            providers.gradleProperty("oauthClientMetadataUrl").orNull
                ?: "https://nubecita.app/oauth/client-metadata.json"
        // Trailing slash is load-bearing: GitHub Pages serves /oauth-redirect as a
        // directory and 301s the no-slash form to /oauth-redirect/, which corrupted
        // the verified App Link handoff and dropped the OAuth `code`. Requesting the
        // slash canonical makes bsky redirect straight there with no 301 so the code
        // arrives intact (nubecita-o4rv.1). Must be an allowed redirect_uri in the
        // hosted client-metadata.json (added in nubecita-web#22).
        val oauthRedirectUri =
            providers.gradleProperty("oauthRedirectUri").orNull
                ?: "https://nubecita.app/oauth-redirect/"
        buildConfigField(
            type = "String",
            name = "OAUTH_CLIENT_METADATA_URL",
            value = "\"$oauthClientMetadataUrl\"",
        )
        buildConfigField(
            type = "String",
            name = "OAUTH_REDIRECT_URI",
            value = "\"$oauthRedirectUri\"",
        )
        buildConfigField(
            type = "String",
            name = "OAUTH_SCOPE",
            value = "\"atproto transition:generic transition:chat.bsky\"",
        )

        // RevenueCat public SDK (Google Play) key. Resolution order:
        //   1. -PrevenueCatApiKey=… (or ~/.gradle/gradle.properties)
        //   2. REVENUECAT_API_KEY env var — CI injects the `release`-environment
        //      secret here (release.yaml's fastlane + FAD build steps).
        //   3. local.properties — accepts either `revenueCatApiKey=…` or
        //      `REVENUECAT_API_KEY=…` (gitignored; local dev).
        // Empty default keeps it out of git (secret-scan safe) and makes a
        // keyless build a no-op (RevenueCatInitializer skips configure).
        val revenueCatApiKey =
            providers.gradleProperty("revenueCatApiKey").orNull?.takeIf(String::isNotEmpty)
                ?: providers.environmentVariable("REVENUECAT_API_KEY").orNull?.takeIf(String::isNotEmpty)
                ?: localProperties.getProperty("revenueCatApiKey")?.takeIf(String::isNotEmpty)
                ?: localProperties.getProperty("REVENUECAT_API_KEY")?.takeIf(String::isNotEmpty)
                ?: ""
        buildConfigField(
            type = "String",
            name = "REVENUECAT_API_KEY",
            value = "\"$revenueCatApiKey\"",
        )
    }

    // The `environment` flavor dimension is the consumer side of the split
    // declared in `:core:auth` and `:core:preferences`. The `production`
    // variant boots through the real OAuth + Tink-encrypted DataStore stack;
    // the `bench` variant binds in-process fakes (see
    // `core/auth/src/bench/.../FakeSessionStateProvider`,
    // `core/preferences/src/bench/.../FakeUserPreferencesRepository`) so
    // Macrobenchmark + baseline-profile journeys can run against a
    // deterministic, network-free stack.
    //
    // The flavor is named `bench` (not `benchmark` per the bd ticket's
    // original wording) to avoid colliding with the androidx.baselineprofile
    // plugin's auto-generated `benchmarkRelease` build type — flavor ×
    // build-type compose into task names, and `benchmark` × `Release`
    // collides with the plugin's existing `assembleBenchmarkRelease`.
    //
    // `applicationIdSuffix` is deliberately NOT set — google-services.json
    // is keyed by applicationId and only registers `net.kikin.nubecita`.
    // Both flavors keep that applicationId; the bench APK simply replaces
    // a production install on the same device. Sideload-coexistence via
    // an explicit `.bench` suffix is a follow-up that requires either
    // a parallel Firebase project (google-services.json under
    // `src/bench/`) or skipping google-services for the bench flavor
    // entirely.
    //
    // See `bd show nubecita-crmi.6` Section A for the full scope.
    flavorDimensions += "environment"
    productFlavors {
        create("production") { dimension = "environment" }
        create("bench") { dimension = "environment" }
    }

    buildTypes {
        debug {
            // When the release keystore is resolvable (CI exports the env vars, or
            // a local developer has set up keystore.properties), sign debug APKs
            // with it too. This is what lets CI-built FAD APKs pass the OS-level
            // Digital Asset Links verification for the nubecita.app App Link:
            // assetlinks.json only lists the release / Play / maintainer-debug
            // fingerprints, not the runner's auto-generated debug keystore.
            // When the env vars aren't present, debug builds fall back to AGP's
            // standard auto-generated debug keystore — local dev workflow is
            // unchanged unless the developer opts in via keystore.properties.
            if (signingConfigs.getByName("release").storeFile != null) {
                signingConfig = signingConfigs.getByName("release")
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
        // Macrobenchmark variant matrix is owned by the
        // androidx.baselineprofile plugin (applied above). It auto-generates
        // `benchmarkRelease` (R8-minified + profileable — used for actual
        // benchmarks) and `nonMinifiedRelease` (used by
        // `:benchmark/BaselineProfileGenerator` to capture the startup
        // profile). Hand-rolling a `benchmark` build type here would only
        // add a duplicate (the plugin's variant is already what we want)
        // and collides with the plugin's naming. Production `release`
        // stays non-profileable, non-debuggable.
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // JUnit 5 jars (junit-platform-commons, junit-jupiter-api) on the
            // androidTest classpath (via :core:testing) each ship these, which
            // collide at androidTest resource-merge. Drop the duplicates.
            excludes += "/META-INF/LICENSE.md"
            excludes += "/META-INF/LICENSE-notice.md"
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
    implementation(project(":core:analytics"))
    implementation(project(":core:auth"))
    implementation(project(":core:billing"))
    implementation(project(":core:common"))
    // FeedViewPreferencesCoordinator — the production bootstrap wires its
    // start() into the AppInitializer set so the viewer's feed-view prefs
    // (hide replies by unfollowed, etc.) refresh once the session is signed in.
    implementation(project(":core:feeds"))
    implementation(project(":core:logging"))
    // ModerationPreferencesCoordinator — the production bootstrap wires its
    // start() into the AppInitializer set so content-filter prefs refresh once
    // the session is signed in.
    implementation(project(":core:moderation"))
    // :core:posting types (ComposerError, ComposerAttachment) surface in
    // :feature:composer:impl's public ComposerScreenContent signature —
    // :app needs them on its compile classpath to construct overlay
    // screenshots that call ComposerScreenContent directly. `ActorUi`
    // used to live here as `ActorTypeaheadUi` but was promoted to
    // :data:models in nubecita-vrba.4 and is now reachable transitively.
    implementation(project(":core:posting"))
    implementation(project(":core:preferences"))
    implementation(project(":core:push"))
    implementation(project(":core:review"))
    implementation(project(":core:update"))
    implementation(project(":core:video"))
    // Aggregated here so Hilt validates the video-upload graph now rather than
    // when :feature:composer first injects VideoUploadRepository (uu6c.5).
    implementation(project(":core:video-upload"))
    implementation(project(":core:widget-sync"))
    implementation(project(":designsystem"))
    implementation(project(":feature:bookmarks:api"))
    implementation(project(":feature:bookmarks:impl"))
    implementation(project(":feature:chats:api"))
    implementation(project(":feature:chats:impl"))
    implementation(project(":feature:composer:api"))
    implementation(project(":feature:composer:impl"))
    implementation(project(":feature:feed:api"))
    implementation(project(":feature:feed:impl"))
    implementation(project(":feature:feeds:api"))
    // Hosts the @MainShell EntryProviderInstaller for the Feeds NavKey
    // (pinned-feeds management). Replaces the deleted FeedsPlaceholderModule.
    implementation(project(":feature:feeds:impl"))
    implementation(project(":feature:login:api"))
    implementation(project(":feature:login:impl"))
    implementation(project(":feature:mediaviewer:impl"))
    // Hosts the @MainShell EntryProviderInstaller that registers the Report /
    // Block / BlockedAccounts routes. No other module depends on :impl (they use
    // :moderation:api NavKeys only), so :app must aggregate it or those routes
    // crash with "Unknown screen" when pushed (nubecita — moderation routes).
    implementation(project(":feature:moderation:impl"))
    implementation(project(":feature:notifications:api"))
    implementation(project(":feature:notifications:impl"))
    implementation(project(":feature:onboarding:api"))
    implementation(project(":feature:onboarding:impl"))
    implementation(project(":feature:paywall:impl"))
    implementation(project(":feature:postdetail:api"))
    implementation(project(":feature:postdetail:impl"))
    implementation(project(":feature:profile:api"))
    implementation(project(":feature:profile:impl"))
    implementation(project(":feature:search:api"))
    implementation(project(":feature:search:impl"))
    implementation(project(":feature:settings:api"))
    implementation(project(":feature:settings:impl"))
    implementation(project(":feature:videoplayer:api"))
    implementation(project(":feature:videoplayer:impl"))
    implementation(project(":feature:videos:impl"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    // Configuration.Provider + HiltWorkerFactory for on-demand WorkManager init
    // (the background DM-poll worker, nubecita-1fy.15). NubecitaApplication owns
    // the Configuration; the default WorkManagerInitializer is disabled in the
    // manifest so the Hilt factory is used.
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.atproto.models)
    implementation(libs.atproto.runtime)
    implementation(libs.coil.core)
    implementation(libs.coil.gif)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.video)
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
    testImplementation(libs.mockk)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(project(":core:testing"))
    androidTestImplementation(project(":core:testing-android"))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.espresso.intents)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.uiautomator)
    // fastlane screengrab: marketing screenshot capture driven by the bench
    // flavor (deterministic, logged-in fake data). Test-only — never ships in
    // the app APK. The `screenshots` fastlane lane runs the bench androidTest.
    androidTestImplementation(libs.screengrab)

    // Baseline profile producer wiring — `:benchmark`'s
    // `BaselineProfileGenerator` writes startup-prof.txt + baseline-prof.txt
    // into `app/src/<flavor>Release/generated/baselineProfiles/` (plugin
    // default `saveInSrc = true`), and the androidx.baselineprofile plugin
    // picks them up at release assembly.
    //
    // productionRelease/ IS committed; benchRelease/ is gitignored and
    // regenerated (nightly in CI, on demand locally). Production regen is
    // manual and on-device — see benchmark/README.md for cadence.
    "baselineProfile"(project(":benchmark"))

    // Bench also ships the widget, rendered offline against fakes — see the note
    // on the productionImplementation line below (nubecita-epe3).
    "benchImplementation"(project(":feature:widgets:impl"))

    kspAndroidTest(libs.hilt.android.compiler)

    // Glance widgets ship in production; the bench flavor also includes them but
    // renders offline against in-process fakes (nubecita-epe3) — the bench
    // `FakeFeedRepository` + `FakeSessionStateProvider` mean the widget shows
    // sample posts with zero network, so it can be smoke/screenshot-tested and
    // used to reproduce the Android-11 list-adapter trampoline crash
    // (nubecita-ew77) without OAuth. Network-silence is preserved: the fakes
    // issue no XRPC.
    "productionImplementation"(project(":feature:widgets:impl"))
}

/**
 * Fails the build when a release variant's merged ART profile carries no
 * meaningful app rules.
 *
 * This exists because `CompilationMode.Partial(BaselineProfileMode.Require)`
 * does NOT catch it: `Require` only asserts that *a* profile installed, and a
 * library-only profile satisfies it. That gap let benchBenchmarkRelease ship 0
 * app rules into every nightly measurement (nubecita-6row).
 *
 * Floor is absence-only by design: it catches a broken wiring, the wrong
 * variant, or a profile that was never generated, while staying immune to the
 * ~4% run-to-run non-determinism of the generator.
 */
abstract class VerifyBaselineProfileRulesTask : DefaultTask() {
    // ConfigurableFileCollection + @InputFiles rather than the @InputDirectory
    // the spec sketches: `tasks.named(...).map { it.outputs.files }` yields a
    // file collection, and this form consumes it directly while still carrying
    // the task dependency. Same Configuration Cache guarantee, less plumbing.
    @get:InputFiles
    abstract val artProfile: ConfigurableFileCollection

    @get:Input
    abstract val variantName: Property<String>

    @TaskAction
    fun verify() {
        val file =
            artProfile.files.firstOrNull { it.name == "baseline-prof.txt" }
                ?: error(
                    "Baseline-profile guard could not find baseline-prof.txt for " +
                        "${variantName.get()}. This is a FAILURE, not a skip: an AGP " +
                        "upgrade may have moved or renamed the merged-ART-profile " +
                        "output. Fix the wiring in app/build.gradle.kts — do not " +
                        "disable this check (nubecita-6row.2).",
                )
        val appRules =
            file.useLines { lines ->
                lines.count { it.contains("net/kikin/nubecita") }
            }
        if (appRules < MIN_APP_PROFILE_RULES) {
            error(
                "Variant ${variantName.get()} has only $appRules app baseline-profile " +
                    "rules (floor $MIN_APP_PROFILE_RULES). Its APK would be measured or " +
                    "shipped with a library-only profile.\n" +
                    "  Fix: ./gradlew :app:generateBenchReleaseBaselineProfile\n" +
                    "  Background: benchmark/README.md",
            )
        }
        logger.lifecycle("Baseline profile OK for ${variantName.get()}: $appRules app rules")
    }

    companion object {
        const val MIN_APP_PROFILE_RULES = 500
    }
}

// Registered for the two benchmark variants AND productionRelease. Guarding
// only the benchmark variants would protect the measurement while leaving the
// shipped product unguarded.
//
// Wrapped in afterEvaluate: the androidx.baselineprofile plugin registers
// `merge<Variant>ArtProfile` for each variant from its own afterEvaluate
// callback (fired once the AGP variant API finishes creating variant tasks),
// which runs after this script body finishes executing. Calling
// `tasks.named("merge${capitalized}ArtProfile")` directly at script-body
// scope fails with "Task with name ... not found" because the task isn't
// registered yet at that point. Our own afterEvaluate registers after AGP's
// (afterEvaluate callbacks run in registration order, and AGP's plugin
// applies — and registers its callback — before this script body runs), so
// by the time this block runs the merge tasks already exist.
afterEvaluate {
    listOf(
        "benchBenchmarkRelease",
        "productionBenchmarkRelease",
        "productionRelease",
    ).forEach { variant ->
        val capitalized = variant.replaceFirstChar { it.uppercase() }
        val verify =
            tasks.register<VerifyBaselineProfileRulesTask>("verify${capitalized}BaselineProfileRules") {
                // flatMap the producing task's output — never resolve a path at
                // configuration time, or Configuration Cache rejects the build.
                artProfile.from(
                    tasks.named("merge${capitalized}ArtProfile").map { it.outputs.files },
                )
                variantName.set(variant)
            }
        // finalizedBy the MERGE task, not the package task.
        //
        // The release pipeline ships an AAB (`fastlane/Fastfile:118` runs
        // `bundleProductionRelease`), and `packageProductionRelease` is NOT in the
        // bundle task graph — verified with `bundleProductionRelease --dry-run`.
        // Hooking the package task would leave the artifact users actually receive
        // unguarded, which is the whole point of registering productionRelease.
        //
        // `merge<Variant>ArtProfile` IS in both the APK and AAB graphs, so this
        // runs as soon as the profile is merged. Note `finalizedBy` orders this
        // task only against the merge task — with `org.gradle.parallel` enabled,
        // packaging may run concurrently. That is safe: a failure here fails the
        // build, so nothing is published or measured.
        tasks.named("merge${capitalized}ArtProfile") { finalizedBy(verify) }
    }
}

tasks.register("publish") {
    description = "No-op publish task to satisfy semantic-release verification in CI"
    group = "publishing"
}

// The bench-flavor baseline profile is gitignored and regenerated on demand,
// so it survives branch switches. A stale profile from another branch can mask
// newly-broken generation on this one — `clean` must reset it. See
// nubecita-6row.1.
tasks.named<Delete>("clean") {
    delete(layout.projectDirectory.dir("src/benchRelease/generated"))
}
