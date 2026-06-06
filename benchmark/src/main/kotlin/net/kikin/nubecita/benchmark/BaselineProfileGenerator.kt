package net.kikin.nubecita.benchmark

import android.os.SystemClock
import android.util.Log
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates Nubecita's **startup** and **baseline** profiles.
 *
 * Two outputs, both consumed by AGP at build time and landing in
 * `app/src/release/generated/baselineProfiles/`:
 *
 * - `startup-prof.txt` — the cold-start critical path only (launch →
 *   `Splash` → `MainShell` → first frame of `Feed`). Fed to R8 to lay the
 *   listed classes out contiguously in the DEX so cold start stops
 *   faulting across pages. Produced by [generateStartupProfile]
 *   (`includeInStartupProfile = true`).
 * - `baseline-prof.txt` — the startup path **plus** the hot post-startup
 *   journeys, so AOT compilation covers first-use of the surfaces users
 *   hit immediately. Produced by [generateJourneyProfile]
 *   (`includeInStartupProfile = false`, so these journeys stay OUT of the
 *   lean startup file).
 *
 * ## Journeys captured by [generateJourneyProfile]
 *
 * 1. Feed scroll (the 120hz fling path)
 * 2. Open a post → `PostDetail` (+ scroll the thread)
 * 3. `Profile` (You tab) → switch Posts/Replies/Media → scroll
 * 4. `Search` → type a query → results
 * 5. `Chats` → open a conversation thread (skipped if the account has none)
 * 6. `Composer` → open → type
 * 7. **Two-pane list-detail** — captured implicitly: run this same
 *    generator on a **tablet** and journeys 2–4 render through the
 *    `ListDetailSceneStrategy` + `ActiveTabScopedSceneStrategy` two-pane
 *    path. For full coverage, generate on a phone AND a tablet and let
 *    AGP merge the two profiles.
 *
 * Each post-startup step is defensive: a missing/renamed selector logs a
 * warning (tag `$TAG`) and the journey continues, so one flaky surface
 * never fails the whole multi-minute generation. After a run, check
 * logcat for the line `"baseline journey coverage: …"` to see which
 * journeys actually ran (`ok`) vs were skipped.
 *
 * ## Pre-requisite: the bench device must be signed in
 *
 * Both tests drive the **signed-in** shape (Splash → MainShell → Feed). A
 * fresh install with no OAuth session routes Splash → Login, the
 * `feed_list` selector never appears, and the run fails fast with a
 * pointer back to this requirement. Sign in once on the device before
 * running `./gradlew :app:generateReleaseBaselineProfile`. The OAuth
 * session lives in the app's `EncryptedSharedPreferences`, so it survives
 * `nonMinifiedRelease` reinstalls as long as the new APK is signed by the
 * same certificate as the installed one.
 *
 * ## Regeneration cadence
 *
 * Manual, on a real device — not on every release. Regenerate when the
 * cold-start path or a captured journey meaningfully changes, and post the
 * post-regen `StartupBenchmark` numbers to the `nubecita-crmi` epic thread.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    /** Cold-start critical path only — lands in both profile files. */
    @Test
    fun generateStartupProfile() =
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
            awaitSignedInFeed(device)
        }

    /** Hot post-startup journeys — land in `baseline-prof.txt` only. */
    @Test
    fun generateJourneyProfile() =
        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = false,
        ) {
            pressHome()
            startActivityAndWait()
            awaitSignedInFeed(device)

            val coverage = linkedMapOf<String, Boolean>()

            // 1. Feed scroll.
            coverage["feed_scroll"] = step("feed_scroll") { device.flingList(FEED_LIST_RES_ID) }

            // 2. Open a post → PostDetail → scroll → back.
            coverage["post_detail"] =
                step("post_detail") {
                    device.tapInList(FEED_LIST_RES_ID)
                    device.requireRes(POST_DETAIL_LIST_RES_ID)
                    device.flingList(POST_DETAIL_LIST_RES_ID)
                    device.pressBack()
                    device.requireRes(FEED_LIST_RES_ID)
                }

            // 3. Profile (You) → switch tabs → scroll.
            coverage["profile"] =
                step("profile") {
                    check(device.tapDesc(TAB_YOU_DESC)) { "You tab not found" }
                    device.requireRes(PROFILE_LIST_RES_ID)
                    device.tapText("Replies")
                    device.tapText("Media")
                    device.tapText("Posts")
                    device.flingList(PROFILE_LIST_RES_ID)
                }

            // 4. Search → type → results.
            coverage["search"] =
                step("search") {
                    check(device.tapDesc(TAB_SEARCH_DESC)) { "Search tab not found" }
                    val input = device.requireRes(SEARCH_INPUT_RES_ID)
                    input.click()
                    input.text = "blue"
                    device.waitForIdle()
                    // Let the typeahead query + results list render (network-backed).
                    SystemClock.sleep(RESULTS_SETTLE_MS)
                    device.pressBack() // collapse the search overlay
                }

            // 5. Chats → open a thread (optional: the account may have no convos).
            coverage["chats"] =
                step("chats") {
                    check(device.tapDesc(TAB_CHATS_DESC)) { "Chats tab not found" }
                    val convo = device.awaitRes(CHAT_CONVO_ITEM_RES_ID)
                    if (convo != null) {
                        convo.click()
                        device.requireRes(CHAT_THREAD_LIST_RES_ID)
                        device.pressBack()
                    } else {
                        Log.i(TAG, "no conversations on this account; chat-thread path skipped")
                    }
                }

            // 6. Composer → open → type.
            coverage["composer"] =
                step("composer") {
                    check(device.tapDesc(TAB_FEED_DESC)) { "Feed tab not found" }
                    device.requireRes(FEED_LIST_RES_ID)
                    check(device.tapDesc(COMPOSE_FAB_DESC)) { "Compose FAB not found" }
                    val field = device.requireRes(COMPOSER_TEXT_FIELD_RES_ID)
                    field.click()
                    field.text = "gm"
                    device.waitForIdle()
                    device.pressBack() // dismiss IME (or close composer if no IME shown)
                    // On devices without a soft IME (headless CI / hardware
                    // keyboard) the first back already closed the composer, so
                    // only back out again if the field is still up — otherwise
                    // we'd navigate out of Feed and exit the app.
                    if (device.hasObject(By.res(COMPOSER_TEXT_FIELD_RES_ID))) {
                        device.pressBack() // close the composer
                    }
                }

            Log.i(
                TAG,
                "baseline journey coverage: " +
                    coverage.entries.joinToString { "${it.key}=${if (it.value) "ok" else "SKIPPED"}" },
            )
        }
}

private const val TAG: String = "BaselineJourney"
private const val SHORT_WAIT_MS: Long = 5_000
private const val FEED_WAIT_MS: Long = 15_000
private const val RESULTS_SETTLE_MS: Long = 2_000

/**
 * Hard sign-in gate (shared by both tests). A `false` from `device.wait`
 * means the cold-start path did not reach the signed-in feed — almost
 * always "no OAuth session on the device, so Splash → Login". Fail fast
 * with a message pointing at the sign-in pre-requisite so the operator
 * doesn't burn a multi-minute run and then puzzle over an empty profile.
 */
private fun awaitSignedInFeed(device: UiDevice) {
    val found = device.wait(Until.hasObject(By.res(FEED_LIST_RES_ID)), FEED_WAIT_MS)
    check(found) {
        "FeedScreen's LazyColumn (res id '$FEED_LIST_RES_ID') did not appear within " +
            "${FEED_WAIT_MS}ms. The device is not signed in, so Splash routed to Login " +
            "instead of MainShell → Feed. Sign in once, then re-run " +
            "`./gradlew :app:generateReleaseBaselineProfile`."
    }
}

/** Run [block]; on any failure log it and report `false` (journey continues). */
private inline fun step(
    name: String,
    block: () -> Unit,
): Boolean =
    try {
        block()
        true
    } catch (t: Throwable) {
        Log.w(TAG, "journey step '$name' skipped: ${t.message}")
        false
    }

private fun UiDevice.awaitRes(
    resId: String,
    timeoutMs: Long = SHORT_WAIT_MS,
): UiObject2? = wait(Until.findObject(By.res(resId)), timeoutMs)

private fun UiDevice.requireRes(
    resId: String,
    timeoutMs: Long = SHORT_WAIT_MS,
): UiObject2 =
    awaitRes(resId, timeoutMs)
        ?: error("expected resource-id '$resId' did not appear within ${timeoutMs}ms")

private fun UiDevice.tapDesc(desc: String): Boolean {
    val obj = wait(Until.findObject(By.desc(desc)), SHORT_WAIT_MS) ?: return false
    obj.click()
    waitForIdle()
    return true
}

private fun UiDevice.tapText(text: String): Boolean {
    val obj = wait(Until.findObject(By.text(text)), SHORT_WAIT_MS) ?: return false
    obj.click()
    waitForIdle()
    return true
}

/** Fling a list down then back up (re-finding the node between flings). */
private fun UiDevice.flingList(resId: String) {
    awaitRes(resId)?.apply {
        // Keep the gesture off the screen edges so it isn't swallowed by the
        // system back / navigation gestures.
        setGestureMargin(displayWidth / 5)
        fling(Direction.DOWN)
    }
    waitForIdle()
    awaitRes(resId)?.apply {
        // Freshly-resolved node — the DOWN fling's gesture inset doesn't carry
        // over, so re-apply it or the UP fling gets eaten by system gesture-nav.
        setGestureMargin(displayWidth / 5)
        fling(Direction.UP)
    }
    waitForIdle()
}

/**
 * Tap a row inside a list by coordinate (no per-row test tag needed). The
 * point sits [yFraction] down the visible list — far enough below the top
 * chrome to land on a card body that navigates, not an action button.
 */
private fun UiDevice.tapInList(
    resId: String,
    yFraction: Float = 0.22f,
) {
    val bounds = requireRes(resId).visibleBounds
    click(bounds.centerX(), bounds.top + (bounds.height() * yFraction).toInt())
    waitForIdle()
}
