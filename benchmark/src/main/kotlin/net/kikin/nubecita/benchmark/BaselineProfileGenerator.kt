package net.kikin.nubecita.benchmark

import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
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
 * `app/src/<flavor>Release/generated/baselineProfiles/`:
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
 * 3. Trending videos: Discover chip → Trending carousel → vertical video feed
 *    → swipe through three videos → open the comments sheet → back to the
 *    Following feed
 * 4. `Profile` (You tab) → switch Posts/Replies/Media → scroll
 * 5. `Search` → type a query → results
 * 6. `Chats` → open a conversation thread (skipped if the account has none)
 * 7. `Composer` → open → type
 * 8. **Two-pane list-detail** — captured implicitly: run this same
 *    generator on a **tablet** and journeys 2–4 render through the
 *    `ListDetailSceneStrategy` + `ActiveTabScopedSceneStrategy` two-pane
 *    path. For full coverage, generate on a phone AND a tablet and let
 *    AGP merge the two profiles.
 *
 * A step that fails is retried once, and the journey carries on through the
 * remaining steps either way, so a single run names every step that is
 * broken rather than only the first. Then the run **fails**: a profile is
 * only as good as the journeys that fed it, and one generated with a step
 * missing is indistinguishable from a complete one once it is a text file in
 * `src/`. The failure message lists the skipped steps and why; logcat (tag
 * `$TAG`) has a line per step with its timing, and a
 * `"baseline journey coverage: …"` summary per iteration.
 *
 * Every step after the feed scroll first returns to the Feed tab on the
 * Following feed ([returnToFeed]): a step that fails part-way leaves the app
 * wherever it got to — on a phone a sub-route hides the tab bar — and without
 * that reset every later step fails to find its tab.
 *
 * ## Pre-requisite: the bench device must be signed in
 *
 * Both tests drive the **signed-in** shape (Splash → MainShell → Feed). A
 * fresh install with no OAuth session routes Splash → Login, the
 * `feed_list` selector never appears, and the run fails fast with a
 * pointer back to this requirement. Sign in on the device before running
 * `./gradlew :app:generateProductionReleaseBaselineProfile` (with the
 * `class=` filter for this class — see `benchmark/README.md`). The OAuth
 * session lives in the app's `EncryptedSharedPreferences`, so it survives
 * the task's own `nonMinifiedRelease` reinstall (same signing certificate),
 * but not the uninstall at the end of the run: sign in again before every
 * regen.
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
            maxIterations = MAX_JOURNEY_ITERATIONS,
            includeInStartupProfile = false,
        ) {
            pressHome()
            startActivityAndWait()
            awaitSignedInFeed(device)

            // Step name → why it was skipped, or null if it ran.
            val coverage = linkedMapOf<String, String?>()

            // 1. Feed scroll.
            coverage["feed_scroll"] = step("feed_scroll") { device.flingList(FEED_LIST_RES_ID) }

            // 2. Open a post → PostDetail → scroll → back.
            coverage["post_detail"] =
                step("post_detail") {
                    device.returnToFeed()
                    device.openAPost()
                    device.flingList(POST_DETAIL_LIST_RES_ID)
                    device.pressBack()
                    device.requireRes(FEED_LIST_RES_ID)
                }

            // 3. Trending videos → vertical feed → comments → back.
            coverage["trending_videos"] =
                step("trending_videos") {
                    device.returnToFeed()
                    check(device.tapFeedChip(FEED_DISCOVER_CHIP_TEXT)) { "Discover chip not found" }
                    // The carousel heads the Discover list, so it is never
                    // scrolled out of reach — when it is missing it simply has
                    // no data. Selecting Discover asks the trending source to
                    // load, but that first load regularly lands empty, and an
                    // empty `thumbs` renders no header at all. A pull-to-refresh
                    // re-issues the load, which is what actually populates it
                    // (verified on device, nubecita-09n0).
                    val poster =
                        device.awaitRes(TRENDING_VIDEO_THUMB_RES_ID, TRENDING_FIRST_LOAD_MS)
                            ?: run {
                                device.pullToRefresh(FEED_LIST_RES_ID)
                                device.requireRes(TRENDING_VIDEO_THUMB_RES_ID, FEED_WAIT_MS)
                            }
                    poster.click()
                    device.requireRes(VIDEO_FEED_RES_ID, FEED_WAIT_MS)
                    repeat(TRENDING_VIDEO_SWIPES) {
                        device.requireRes(VIDEO_FEED_RES_ID)
                        // DOWN the feed, to the next video. This used to be
                        // `fling(Direction.UP)`, which reads as "swipe up" but
                        // names where the content goes — toward the previous
                        // page, and from the first video there is none. The
                        // journey sat on one clip for all three "swipes"
                        // (nubecita-kfbr).
                        device.flingRes(VIDEO_FEED_RES_ID, Direction.DOWN)
                        // Let the promoted clip actually start playing.
                        SystemClock.sleep(VIDEO_PLAY_MS)
                    }
                    device.requireRes(VIDEO_FEED_RAIL_REPLY_RES_ID).click()
                    // The comments are a bottom sheet on a narrow window and a
                    // side pane on a wide one, and no single element is in
                    // both. The sheet has the drag handle and — on device —
                    // never renders the docked pill (nubecita-09n0); the pane
                    // has the pill and no handle. Gating on the handle alone
                    // failed this leg on every unfolded foldable.
                    //
                    // Not the comments list either: a video with no comments
                    // never composes one, so it only gets a short wait below.
                    check(
                        device.awaitAny(
                            FEED_WAIT_MS,
                            By.desc(SHEET_DRAG_HANDLE_DESC),
                            By.res(DOCKED_COMMENT_PILL_RES_ID),
                        ),
                    ) { "comments did not open as a sheet or as a side pane" }
                    device.awaitRes(POST_COMMENTS_LIST_RES_ID, SHORT_WAIT_MS)
                    SystemClock.sleep(RESULTS_SETTLE_MS)
                    if (device.hasObject(By.res(POST_COMMENTS_LIST_RES_ID))) {
                        device.flingList(POST_COMMENTS_LIST_RES_ID)
                    }
                    // Back out of the sheet and the vertical feed, and switch the
                    // persisted feed choice back to Following.
                    device.returnToFeed()
                }

            // 4. Profile (You) → switch tabs → scroll.
            coverage["profile"] =
                step("profile") {
                    device.returnToFeed()
                    check(device.tapDesc(TAB_YOU_DESC)) { "You tab not found" }
                    device.requireRes(PROFILE_LIST_RES_ID)
                    // The tab row sits under the profile header and scrolls
                    // with it. On a short window — a foldable in landscape —
                    // the header fills the screen and the row is at or below
                    // the bottom edge, where a tap does not land.
                    device.scrollIntoReach(PROFILE_LIST_RES_ID, PROFILE_TABS.first())
                    // Checked: an unchecked miss here was a silent five-second
                    // wait per tab, and a profile with no tab switch in it.
                    PROFILE_TABS.forEach { tab ->
                        check(device.tapTextIn(PROFILE_LIST_RES_ID, tab)) { "profile tab '$tab' not found" }
                    }
                    device.flingList(PROFILE_LIST_RES_ID)
                }

            // 5. Search → type → results.
            coverage["search"] =
                step("search") {
                    device.returnToFeed()
                    check(device.tapDesc(TAB_SEARCH_DESC)) { "Search tab not found" }
                    device.requireRes(SEARCH_INPUT_RES_ID).click()
                    // Found by focus, not by tag. The tap expands the bar into
                    // its own window, and `testTagsAsResourceId` does not cross
                    // a window boundary: the expanded field has no resource-id
                    // at all, and the collapsed node it replaced goes stale.
                    // Typing into that stale node is what threw on every
                    // iteration but the first (nubecita-z1zh).
                    check(device.typeInto("blue") { device.findObject(By.clazz(EDIT_TEXT_CLASS).focused(true)) }) {
                        "the expanded search field could not be typed into"
                    }
                    device.waitForIdle()
                    // Let the typeahead query + results list render (network-backed).
                    SystemClock.sleep(RESULTS_SETTLE_MS)
                    device.pressBack() // collapse the search overlay
                }

            // 6. Chats → open a thread (optional: the account may have no convos).
            coverage["chats"] =
                step("chats") {
                    device.returnToFeed()
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

            // 7. Composer → open → type.
            coverage["composer"] =
                step("composer") {
                    device.returnToFeed()
                    check(device.tapDesc(COMPOSE_FAB_DESC)) { "Compose FAB not found" }
                    val field =
                        device.awaitComposerField()
                            ?: error("composer text field did not appear within ${SHORT_WAIT_MS}ms")
                    field.click()
                    check(device.typeInto("gm") { device.findComposerField() }) {
                        "the composer field could not be typed into"
                    }
                    device.waitForIdle()
                    device.pressBack() // dismiss IME (or close composer if no IME shown)
                    // On devices without a soft IME (headless CI / hardware
                    // keyboard) the first back already closed the composer, so
                    // only back out again if the field is still up — otherwise
                    // we'd navigate out of Feed and exit the app.
                    if (device.findComposerField() != null) {
                        device.pressBack() // close the composer
                    }
                }

            Log.i(
                TAG,
                "baseline journey coverage: " +
                    coverage.entries.joinToString { "${it.key}=${if (it.value == null) "ok" else "SKIPPED"}" },
            )
            val skipped = coverage.filterValues { it != null }
            check(skipped.isEmpty()) {
                "Baseline journey steps skipped in iteration $iteration: " +
                    skipped.entries.joinToString { "${it.key} (${it.value})" } +
                    ". A profile from this run would lack them, so none is produced. " +
                    "Each attempt is in logcat under the '$TAG' tag."
            }
        }
}

private const val TAG: String = "BaselineJourney"

/**
 * Upper bound on how many times `collect` re-runs the journey.
 *
 * The library default is 15, and `collect` only stops sooner when consecutive
 * iterations dump an identical rule set. The journey never does — seven
 * surfaces, video playback and fixed sleeps do not produce the same rules
 * twice — so the default was not a ceiling, it was the run length: all 15
 * iterations, every time, which made every second added to the journey cost
 * fifteen. On the nightly emulator that was 29 of the generate step's 39
 * minutes (nubecita-8gq8).
 *
 * The profile accumulates across iterations rather than being replaced, so
 * fewer of them can only drop rules for code that turns hot late. Compare the
 * rule counts against the previous profile when regenerating the shipped one.
 *
 * The startup profile keeps the default on purpose. Its iterations are a cold
 * start each, about ten seconds, so there is nothing to save by cutting them
 * short — and against the real network it was still changing after five.
 */
private const val MAX_JOURNEY_ITERATIONS: Int = 5
private const val SHORT_WAIT_MS: Long = 5_000
private const val FEED_WAIT_MS: Long = 15_000

/**
 * How long to wait for the carousel before forcing a refresh.
 *
 * Shorter than [FEED_WAIT_MS] on purpose: when the first load comes back empty
 * there is nothing to wait for, and the refresh is what fixes it.
 */
private const val TRENDING_FIRST_LOAD_MS: Long = 5_000
private const val PULL_REFRESH_START_PX: Int = 80
private const val PULL_REFRESH_END_PX: Int = 1_000
private const val PULL_REFRESH_BOTTOM_MARGIN_PX: Int = 10
private const val PULL_REFRESH_STEPS: Int = 60
private const val PULL_REFRESH_SETTLE_MS: Long = 3_000
private const val RESULTS_SETTLE_MS: Long = 2_000
private const val TRENDING_VIDEO_SWIPES: Int = 3
private const val VIDEO_PLAY_MS: Long = 1_500
private const val MAX_BACK_PRESSES: Int = 4

/** How long a tap on the Feed tab gets to bring the feed back before back is tried instead. */
private const val TAB_SWITCH_WAIT_MS: Long = 2_000

/** Slow enough to be a drag: a fling would overshoot and bounce the chip row. */
private const val CHIP_ROW_SWIPE_STEPS: Int = 40

private const val POLL_MS: Long = 100

private const val TYPE_ATTEMPTS: Int = 3

/** The profile's tabs, in the order the journey visits them — ending back on Posts. */
private val PROFILE_TABS: List<String> = listOf("Replies", "Media", "Posts")

private const val MAX_REACH_SCROLLS: Int = 4

/** How many posts [openAPost] tries before giving up. */
private const val MAX_POST_CANDIDATES: Int = 4

private const val POST_OPEN_WAIT_MS: Long = 8_000
private const val REACH_SWIPE_STEPS: Int = 60

/** What a Compose text field reports as its class to accessibility. */
private const val EDIT_TEXT_CLASS: String = "android.widget.EditText"

/** Tries per journey step: the failures seen in practice are one-offs that pass on a second go. */
private const val STEP_ATTEMPTS: Int = 2

/**
 * `UiObject2.fling`'s own default speed, so a fling here travels as far and
 * lands on the same rows as the call it replaces.
 */
private const val FLING_SPEED_DP_PER_SECOND: Int = 7_500

/** `UiDevice.swipe` injects one move per step, 5ms apart. */
private const val SWIPE_STEP_MS: Int = 5

/**
 * How long a fling's landing rows get to load before the journey moves on.
 *
 * The five-second stall this replaces was dead time for the gesture, but on a
 * real account the rows a fling lands on are still fetching their images and
 * video when it ends. A second keeps some of that slack.
 *
 * Not shown to matter on the bench flavor, where every asset is local: the
 * generated rule set was the same to within run-to-run noise with and without
 * it (55,984 vs 55,932 rules in common with the previous profile).
 */
private const val FLING_SETTLE_MS: Long = 1_000

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

/**
 * Get back to the Feed tab showing the Following feed, from wherever the last
 * step left the app.
 *
 * The Feed tab is tapped if it can be seen, and then back is pressed until the
 * feed list is on screen. Both halves are needed. A bottom bar is hidden by a
 * sub-route, so there the tab cannot be tapped until back has been pressed;
 * a wide window's rail stays up over a sub-route, so there tapping the tab
 * changes nothing and only back gets out. The earlier version took a visible
 * tab to mean "already at the tab's root", which is only true of a bottom bar
 * — on an unfolded foldable it left the vertical video feed up and every
 * later step failed looking for the feed underneath it.
 *
 * Back is never pressed while the feed list is visible: from the Feed tab's
 * root it would leave the app.
 */
private fun UiDevice.returnToFeed() {
    var backPresses = 0
    while (!hasObject(By.res(FEED_LIST_RES_ID))) {
        val tappedFeedTab = hasObject(By.desc(TAB_FEED_DESC)) && tapDesc(TAB_FEED_DESC)
        if (tappedFeedTab && awaitRes(FEED_LIST_RES_ID, TAB_SWITCH_WAIT_MS) != null) break
        if (backPresses == MAX_BACK_PRESSES) error("feed not reachable after $MAX_BACK_PRESSES back presses")
        pressBack()
        waitForIdle()
        backPresses++
    }
    selectFollowingFeed()
    requireRes(FEED_LIST_RES_ID, FEED_WAIT_MS)
}

/**
 * Put the feed back on Following. The selected feed is persisted, so a journey
 * that leaves Discover selected changes what every later step — and the next
 * cold start, including the startup-profile capture — opens on.
 *
 * The chip is not always there to tap: selecting a feed scrolls the chip row
 * to it, and with a few pinned feeds that pushes Following off the leading
 * edge. The row is dragged back to its start first. Before this, a missing
 * chip was a silent no-op after a five-second wait, and the rest of the
 * journey ran on Discover.
 */
private fun UiDevice.selectFollowingFeed() {
    revealChipRow()
    if (findFeedChip(FEED_FOLLOWING_CHIP_TEXT) == null) {
        val chip = findFeedChip(FEED_DISCOVER_CHIP_TEXT)?.visibleCenter
        val list = findObject(By.res(FEED_LIST_RES_ID))?.boundsOrNull()
        if (chip != null && list != null) {
            swipe(chip.x, chip.y, list.right - displayWidth / 10, chip.y, CHIP_ROW_SWIPE_STEPS)
            waitForIdle()
        }
    }
    check(tapFeedChip(FEED_FOLLOWING_CHIP_TEXT)) { "Following chip not found, so the feed could not be put back on it" }
}

/**
 * A feed chip, by its label — and only a chip.
 *
 * Matching the label anywhere on screen is not safe on a real account. In a
 * two-pane window the detail pane can be showing someone's profile, and the
 * follow button on a profile you follow reads "Following": a bare text match
 * can land on that and unfollow them. A chip is told apart by where it is —
 * in the row above the feed list, within the feed's own pane.
 */
private fun UiDevice.findFeedChip(text: String): UiObject2? {
    val list = findObject(By.res(FEED_LIST_RES_ID))?.boundsOrNull() ?: return null
    return findObjects(By.text(text)).firstOrNull { candidate ->
        val bounds = candidate.boundsOrNull() ?: return@firstOrNull false
        bounds.bottom <= list.top && bounds.centerX() in list.left..list.right
    }
}

/**
 * Bring the chip row back if a scroll has tucked it away. It leaves with the
 * feed scrolling down and returns with any scroll up, so a short drag the
 * other way is enough — and without it every chip lookup after a fling down
 * the feed finds nothing.
 */
private fun UiDevice.revealChipRow() {
    if (findFeedChip(FEED_FOLLOWING_CHIP_TEXT) != null || findFeedChip(FEED_DISCOVER_CHIP_TEXT) != null) return
    val list = findObject(By.res(FEED_LIST_RES_ID))?.boundsOrNull() ?: return
    val x = list.centerX()
    swipe(x, list.centerY(), x, list.centerY() + displayHeight / 8, REACH_SWIPE_STEPS)
    waitForIdle()
}

private fun UiDevice.tapFeedChip(text: String): Boolean {
    val chip = awaitNotNull { findFeedChip(text) } ?: return false
    chip.click()
    waitForIdle()
    return true
}

/**
 * Run [block] and return why it failed, or `null` if it ran.
 *
 * A failure is retried once before it counts: every step but the first opens
 * with [returnToFeed], so a second attempt starts from a known place. Either
 * way this returns rather than throws, and the caller fails the run once the
 * whole journey has been walked.
 */
private inline fun step(
    name: String,
    block: () -> Unit,
): String? {
    var failure: Throwable? = null
    repeat(STEP_ATTEMPTS) { attempt ->
        val startedAt = SystemClock.elapsedRealtime()
        try {
            block()
            val attemptNote = if (attempt > 0) " (attempt ${attempt + 1})" else ""
            Log.i(TAG, "journey step '$name' ok in ${SystemClock.elapsedRealtime() - startedAt}ms$attemptNote")
            return null
        } catch (t: Throwable) {
            // A failed step, not a failed process. An assertion is the first
            // kind even though it is an Error, so `catch (e: Exception)` would
            // let one end the journey before the other steps had been walked.
            // Anything else that is an Error — out of memory, a blown stack —
            // is not something a retry answers, and goes up as it is.
            if (t is Error && t !is AssertionError) throw t
            failure = t
            // The throwable itself, not its message: UiAutomator's
            // StaleObjectException has none, and "skipped: null" explains nothing.
            Log.w(TAG, "journey step '$name' failed (attempt ${attempt + 1} of $STEP_ATTEMPTS)", t)
        }
    }
    return failure.toString()
}

/** Wait until any of [selectors] matches; `false` if none does within [timeoutMs]. */
private fun UiDevice.awaitAny(
    timeoutMs: Long,
    vararg selectors: BySelector,
): Boolean {
    val deadline = SystemClock.uptimeMillis() + timeoutMs
    while (true) {
        if (selectors.any { hasObject(it) }) return true
        if (SystemClock.uptimeMillis() >= deadline) return false
        SystemClock.sleep(POLL_MS)
    }
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

/**
 * Open a post's detail from the feed.
 *
 * Always inside the tagged body text, never a blind coordinate: a tap anywhere
 * else in a card can land on the action row and like or repost on the
 * signed-in account.
 *
 * No point inside the text is reliably "the post", though. On a real timeline
 * the centre can be a hashtag, mention or link, which opens something else —
 * a post ending in a block of hashtags sent the journey to a hashtag screen.
 * The edges are worse: the first line's start is inside the avatar's touch
 * target and its top is inside the author row's, and both open a profile.
 *
 * So the tap goes to the centre, which is right for most posts, and a post
 * that does not open is passed over for the next one down, scrolling for more
 * when the screen runs out. Posts under the top of the list are skipped: the
 * list draws behind the chip row, and the new-posts pill floats there.
 */
private fun UiDevice.openAPost() {
    requireRes(POST_CARD_BODY_RES_ID)
    var onScreenIndex = 0
    repeat(MAX_POST_CANDIDATES) {
        var bodies = tappablePostBodies()
        if (onScreenIndex >= bodies.size) {
            flingRes(FEED_LIST_RES_ID, Direction.DOWN)
            bodies = tappablePostBodies()
            onScreenIndex = 0
        }
        val body = bodies.getOrNull(onScreenIndex++) ?: return@repeat
        click(body.centerX(), body.centerY())
        if (awaitRes(POST_DETAIL_LIST_RES_ID, POST_OPEN_WAIT_MS) != null) return
        returnToFeed()
    }
    error("none of $MAX_POST_CANDIDATES posts opened its detail within ${POST_OPEN_WAIT_MS}ms")
}

/** Post bodies in the feed's own pane (a post open in the detail pane has bodies too), clear of the top overlays. */
private fun UiDevice.tappablePostBodies(): List<Rect> {
    val list = requireRes(FEED_LIST_RES_ID).boundsOrNull() ?: return emptyList()
    val clearOfOverlays = list.top + displayHeight / 10
    return findObjects(By.res(POST_CARD_BODY_RES_ID))
        .mapNotNull { it.boundsOrNull() }
        .filter { list.contains(it) && it.top >= clearOfOverlays }
}

/**
 * The composer's text field, whichever way the composer is presented.
 *
 * Full-screen on a narrow window, where the field carries its tag. A dialog on
 * a wide one — its own window, so `testTagsAsResourceId` never reaches it and
 * the field has no resource-id at all. It is the only text field on screen
 * either way.
 */
private fun UiDevice.findComposerField(): UiObject2? = findObject(By.res(COMPOSER_TEXT_FIELD_RES_ID)) ?: findObject(By.clazz(EDIT_TEXT_CLASS))

private fun UiDevice.awaitComposerField(): UiObject2? = awaitNotNull { findComposerField() }

/**
 * This node's bounds, or `null` if it has left the screen since it was found.
 *
 * A node is a snapshot. A list that is still loading or settling replaces its
 * rows between the lookup and the read, and reading a replaced one throws —
 * which is not "the step failed", only "look again".
 */
private fun UiObject2.boundsOrNull(): Rect? =
    try {
        visibleBounds
    } catch (_: StaleObjectException) {
        null
    }

/**
 * Type [text] into the field [find] returns, looking it up again if it goes
 * stale between being found and being typed into.
 */
private fun UiDevice.typeInto(
    text: String,
    find: () -> UiObject2?,
): Boolean {
    repeat(TYPE_ATTEMPTS) {
        val field = awaitNotNull(find) ?: return false
        try {
            field.text = text
            return true
        } catch (_: StaleObjectException) {
            waitForIdle()
        }
    }
    return false
}

/** Poll [find] until it returns something, or give up after [SHORT_WAIT_MS]. */
private inline fun <T : Any> awaitNotNull(find: () -> T?): T? {
    val deadline = SystemClock.uptimeMillis() + SHORT_WAIT_MS
    while (true) {
        find()?.let { return it }
        if (SystemClock.uptimeMillis() >= deadline) return null
        SystemClock.sleep(POLL_MS)
    }
}

/**
 * The [resId] node furthest to the leading side. In a two-pane window the same
 * screen can be up twice — your own profile in the list pane, someone else's in
 * the detail pane — and the journey means the one it navigated to.
 */
private fun UiDevice.leading(resId: String): UiObject2? = findObjects(By.res(resId)).filter { it.boundsOrNull() != null }.minByOrNull { it.boundsOrNull()?.left ?: Int.MAX_VALUE }

/** [text] inside the [listResId] the journey navigated to, not a same-named label somewhere else on screen. */
private fun UiDevice.findTextIn(
    listResId: String,
    text: String,
): UiObject2? {
    val list = leading(listResId)?.boundsOrNull() ?: return null
    return findObjects(By.text(text)).firstOrNull { candidate -> candidate.boundsOrNull()?.let(list::contains) == true }
}

private fun UiDevice.tapTextIn(
    listResId: String,
    text: String,
): Boolean {
    val target = awaitNotNull { findTextIn(listResId, text) } ?: return false
    target.click()
    waitForIdle()
    return true
}

/**
 * Scroll the list [listResId] until [text] sits clear of its bottom edge, where
 * it can be tapped. Does nothing when it already does.
 */
private fun UiDevice.scrollIntoReach(
    listResId: String,
    text: String,
) {
    repeat(MAX_REACH_SCROLLS) {
        val list = leading(listResId)?.boundsOrNull() ?: return
        val target = findTextIn(listResId, text)?.boundsOrNull()
        if (target != null && target.bottom <= list.bottom - displayHeight / 8) return
        // A quarter of the list, slowly: a drag, so it stops where it is let go.
        val x = list.centerX()
        swipe(x, list.centerY() + list.height() / 8, x, list.centerY() - list.height() / 8, REACH_SWIPE_STEPS)
        waitForIdle()
    }
}

private fun UiDevice.tapDesc(desc: String): Boolean {
    val obj = wait(Until.findObject(By.desc(desc)), SHORT_WAIT_MS) ?: return false
    obj.click()
    waitForIdle()
    return true
}

/**
 * Swipe down from the top of [resId] to trigger pull-to-refresh, then wait for
 * the list to settle.
 *
 * Deliberately a raw swipe rather than `fling(Direction.UP)`: a fling is a
 * scroll gesture, and on a list already at the top it is a no-op rather than a
 * refresh.
 */
private fun UiDevice.pullToRefresh(resId: String) {
    val list = awaitRes(resId) ?: return
    val bounds = list.visibleBounds
    val x = bounds.centerX()
    // Clamp inside the list: the nominal pull distance is taller than a short
    // list, a landscape window or a split-screen pane, and a swipe that runs
    // past the bottom edge is not a pull.
    val startY = bounds.top + PULL_REFRESH_START_PX
    val endY = minOf(bounds.top + PULL_REFRESH_END_PX, bounds.bottom - PULL_REFRESH_BOTTOM_MARGIN_PX)
    if (endY <= startY) return
    swipe(x, startY, x, endY, PULL_REFRESH_STEPS)
    waitForIdle()
    SystemClock.sleep(PULL_REFRESH_SETTLE_MS)
}

/** Fling a list down then back up. */
private fun UiDevice.flingList(resId: String) {
    flingRes(resId, Direction.DOWN)
    flingRes(resId, Direction.UP)
}

/**
 * Fling the scrollable [resId] so that it shows what lies in [direction]:
 * `DOWN` moves on through the content, `UP` goes back toward the start.
 *
 * A raw swipe rather than `UiObject2.fling`. That call waits for an
 * accessibility event saying the scroll reached its end, or five seconds, and
 * a Compose list does not send one — so every fling cost a flat five seconds
 * of an app sitting idle. With eleven of them that was 57 of a 104-second
 * iteration (nubecita-kfbr). [waitForIdle] returns as soon as the scroll
 * events stop instead, and [FLING_SETTLE_MS] then gives the rows it landed on
 * time to load.
 */
private fun UiDevice.flingRes(
    resId: String,
    direction: Direction,
) {
    // Fails rather than returns: a fling that quietly does nothing is a
    // journey step that passes without having scrolled anything.
    val bounds =
        awaitNotNull { leading(resId)?.boundsOrNull() }
            ?: error("nothing to fling: '$resId' did not appear within ${SHORT_WAIT_MS}ms")
    // Keep the gesture off the screen edges so it isn't swallowed by the
    // system back / navigation gestures.
    val margin = displayWidth / 5
    val top = bounds.top + margin
    val bottom = bounds.bottom - margin
    if (bottom <= top) return
    val x = bounds.centerX()
    val density =
        InstrumentationRegistry
            .getInstrumentation()
            .context.resources.displayMetrics.density
    val swipeMs = (bottom - top) * 1_000 / (FLING_SPEED_DP_PER_SECOND * density)
    val steps = (swipeMs / SWIPE_STEP_MS).toInt().coerceAtLeast(2)
    when (direction) {
        // The finger travels the other way from the content it reveals.
        Direction.DOWN -> swipe(x, bottom, x, top, steps)
        Direction.UP -> swipe(x, top, x, bottom, steps)
        else -> error("flingRes only flings vertically, not $direction")
    }
    waitForIdle()
    SystemClock.sleep(FLING_SETTLE_MS)
}
