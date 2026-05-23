package net.kikin.nubecita.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the **startup baseline profile** for Nubecita's cold-start
 * critical path: launch → `Splash` → `MainShell` → first frame of
 * `Feed`.
 *
 * Two output files, both consumed by AGP at build time:
 *
 * - `startup-prof.txt` — fed to R8 to lay out the listed classes
 *   contiguously in the DEX, so the cold-start path stops faulting
 *   across pages.
 * - `baseline-prof.txt` — also includes the captured journey
 *   (`includeInStartupProfile = true` means "in both files"). Crmi.3
 *   extends `baseline-prof.txt` with the post-startup journeys
 *   (Feed scroll, PostDetail, Composer, Profile) that don't belong in
 *   the startup file.
 *
 * Both land in `app/src/release/generated/baselineProfiles/` under the
 * `androidx.baselineprofile` plugin's default `saveInSrc = true`, and
 * are picked up automatically by the next release assembly.
 *
 * ## Pre-requisite: the bench device must be signed in
 *
 * The generator drives the **signed-in** cold-start shape (Splash →
 * MainShell → Feed). A fresh install with no OAuth session routes
 * Splash → Login instead, the `feed_list` UIAutomator selector never
 * appears, and the test fails fast with a pointer back to this
 * requirement. Sign in once on the bench device before running
 * `./gradlew :app:generateReleaseBaselineProfile`. The OAuth session is
 * stored in the app's `EncryptedSharedPreferences` (inside the app
 * data dir), so it survives `nonMinifiedRelease` reinstalls as long
 * as the new APK is signed by the same certificate as the currently-
 * installed APK; otherwise the OS forces an uninstall + data wipe and
 * the operator has to sign in again before the next regen.
 *
 * ## Regeneration cadence
 *
 * Manual, not on every release. Regenerate when the cold-start path
 * meaningfully changes — major Feed, Splash, or Login feature merges —
 * and post the post-regen `StartupBenchmark` numbers to the
 * `nubecita-crmi` epic comment thread for the historical trend.
 *
 * Iteration count: the rule's default (15 max with 3 stable). Pixel
 * 10 Pro XL converges in ~6–8 iterations for this journey, so the
 * default's headroom is plenty and bumping it just burns wall-clock.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() =
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()

            // Wait for FeedScreen's LazyColumn to render. The `feed_list`
            // resource id surfaces via Compose's
            // `testTagsAsResourceId = true` (root semantics flag on
            // `MainActivity`); see `:feature:feed:impl/FeedTestTagsTest`
            // for the contract pinning. The single-arg `By.res(id)` is
            // intentional — Compose tags surface as bare resource-id
            // values with no package qualifier, so the two-arg form
            // silently never matches.
            //
            // A `false` return from `device.wait(Until.hasObject(...))`
            // means the cold-start path did NOT reach the signed-in feed
            // surface within the timeout. (The `hasObject` condition
            // returns Boolean, not nullable — that's why we route through
            // `check(found)` below rather than `?: throw`.) Most likely
            // cause: no OAuth session on the bench device, so `Splash`
            // routed to `Login` instead of `Main`. Fail fast with a
            // message that points at the sign-in pre-requisite (see
            // KDoc above) so the operator doesn't burn a 5-minute
            // generation run and then puzzle over an "empty profile"
            // result.
            device
                .wait(
                    Until.hasObject(By.res(FEED_LIST_RES_ID)),
                    FEED_LIST_WAIT_MS,
                ).let { found ->
                    check(found) {
                        "FeedScreen's LazyColumn (res id '$FEED_LIST_RES_ID') did not appear " +
                            "within ${FEED_LIST_WAIT_MS}ms. The bench device is not signed in, so " +
                            "the cold-start path routed Splash → Login instead of Splash → MainShell → " +
                            "Feed. Sign in once on the device, then re-run " +
                            "`./gradlew :app:generateReleaseBaselineProfile`."
                    }
                }
        }

    private companion object {
        const val FEED_LIST_WAIT_MS: Long = 15_000
    }
}
