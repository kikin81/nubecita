package net.kikin.nubecita.screenshots

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import net.kikin.nubecita.BuildConfig
import net.kikin.nubecita.MainActivity
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.UiAutomatorScreenshotStrategy
import tools.fastlane.screengrab.locale.LocaleTestRule

/**
 * fastlane screengrab journey — Play Store marketing captures driven by the
 * **bench flavor** (signed-in, deterministic fake data, zero network). Each
 * `@Test` launches a fresh [MainActivity], navigates to one screen, and emits
 * one Screengrab frame; the `screenshots` fastlane lane runs the class and
 * pulls the PNGs into `fastlane/metadata/android/<locale>/images/`, which
 * `frame_marketing_screenshots` then wraps in a device frame + headline.
 *
 * Screenshot names are numbered to control listing order (frameit keys
 * `title.strings` by these names). All eight surfaces are backed by `src/bench`
 * fakes: feed/discover/chat/profile/video plus the search (posts + feeds) and
 * notifications fakes added in Phase 2.
 *
 * Bench-only: `FakeSessionStateProvider` boots signed-in, so on `production`
 * (no fake session) the screens never appear; the suite [assumeTrue]-skips.
 *
 * Navigation uses UiAutomator against tags surfaced by MainActivity's
 * `testTagsAsResourceId = true`: bottom-nav items by `contentDescription`
 * (By.desc), lists/rows/fields by their Compose `testTag` (By.res).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class MarketingScreenshotJourney {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    // Pre-grant POST_NOTIFICATIONS so a fresh install's runtime-permission
    // dialog never covers the first screen (it silently blocked the feed on a
    // clean tablet install — the phone only passed because a prior run had
    // already granted it). API < 33: a no-op.
    @get:Rule
    val grantNotifications: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        assumeTrue("Marketing journey requires the bench flavor", BuildConfig.FLAVOR == "bench")
        hiltRule.inject()
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        Screengrab.setDefaultScreenshotStrategy(UiAutomatorScreenshotStrategy())
    }

    /** 01 — Following timeline (the hero shot). */
    @Test
    fun a01Feed() =
        capture("01_feed") {
            awaitTag(FEED_LIST)
        }

    // 02 (Discover) dropped: Play caps the listing at 8 screenshots/locale, and
    // the Discover feed shot was a near-duplicate of 01 (both timeline lists).
    // The feeds story is covered by 07 (search Feeds directory) + the Following
    // hero. The numbering keeps its gap — Play orders by filename, so 01,03,04…
    // sorts fine and renumbering would churn every locale/bucket needlessly.

    /** 03 — A direct-message thread: Chats tab → first conversation. */
    @Test
    fun a03Chat() =
        capture("03_chat") {
            tap(By.desc("Chats")) { "Chats bottom-nav tab" }
            tap(By.res(CHAT_CONVO_ITEM)) { "first conversation row" }
            awaitTag(CHAT_THREAD_LIST)
        }

    /** 04 — Immersive fullscreen video player (tap a feed video embed). */
    @Test
    fun a04Video() =
        capture("04_video") {
            awaitTag(FEED_LIST)
            scrollFeedTo(FEED_VIDEO_EMBED).click()
            awaitTag(VIDEO_PLAYER)
        }

    /** 05 — Notifications (likes/follows/reposts/replies). */
    @Test
    fun a05Notifications() =
        capture("05_notifications") {
            tap(By.desc("Notifications")) { "Notifications bottom-nav tab" }
            awaitTag(NOTIFICATIONS_LIST)
        }

    /** 06 — Search results, Posts tab (query-driven). */
    @Test
    fun a06SearchPosts() =
        capture("06_search_posts") {
            tap(By.desc("Search")) { "Search bottom-nav tab" }
            typeInto(SEARCH_INPUT, "design")
            awaitTag(SEARCH_POSTS_LIST)
        }

    /** 07 — Search results, Feeds tab. */
    @Test
    fun a07SearchFeeds() =
        capture("07_search_feeds") {
            tap(By.desc("Search")) { "Search bottom-nav tab" }
            typeInto(SEARCH_INPUT, "feeds")
            tap(By.text("Feeds")) { "Feeds search sub-tab" }
            awaitTag(SEARCH_FEEDS_LIST)
        }

    /** 08 — Profile: the "You" tab (hero + Posts/Replies/Media pill tabs). */
    @Test
    fun a08Profile() =
        capture("08_profile") {
            tap(By.desc("You")) { "You bottom-nav tab" }
            awaitTag(PROFILE_LIST)
        }

    /**
     * 09 — Post detail: a deep link straight into the bench gallery post
     * (`BenchFakePostThreadRepository`). Renders the multi-image focus post +
     * replies; on tablets this fills the list-detail detail pane instead of the
     * empty "select post" placeholder. Launches via the VIEW deep-link intent
     * (the same path adb uses), so no feed-row tap / post-row tag is needed.
     */
    @Test
    fun a09PostDetail() =
        captureDeepLink("09_post_detail", POST_DEEPLINK_URI) {
            awaitTag(POST_DETAIL_LIST)
        }

    /**
     * Like [capture], but drives a screen via a VIEW deep-link [uri]. Launches
     * [MainActivity] the normal (class-based) way — `ActivityScenario` can't
     * monitor a `singleTask` activity launched straight from a VIEW intent (it
     * hangs at PRE_ON_CREATE) — then, once MainShell has mounted, delivers the
     * deep link to the running instance via `startActivity`. `singleTask` routes
     * it to `onNewIntent` → `MainActivity.handleIntent` → the deep-link matchers,
     * exactly as an external link / `adb am start` would. Waiting for the feed
     * first ensures the `DeepLinkRouter` emission is collected, not dropped
     * before MainShell subscribes.
     */
    private fun captureDeepLink(
        name: String,
        uri: String,
        navigate: () -> Unit,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // ActivityScenario boots the app under the bench Hilt graph; once the
        // feed is up, deliver the deep link as a NEW_TASK VIEW intent so
        // singleTask routes it to onNewIntent -> MainActivity.handleIntent -> the
        // matchers (exactly like an external link / adb am start). The NEW_TASK
        // reuse detaches the instance from ActivityScenario, so its close() can't
        // reach DESTROYED — swallow that benign teardown failure (the capture is
        // already done) rather than fail the journey.
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            awaitTag(FEED_LIST)
            // Let MainShell's DeepLinkRouter collector subscribe before delivering
            // — otherwise a fast emission can land before the collector is ready
            // and the route is dropped (the post-detail then never appears).
            device.waitForIdle()
            Thread.sleep(SETTLE_MS)
            val intent =
                Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                    .setClass(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            navigate()
            hideKeyboardIfShown()
            device.waitForIdle()
            Thread.sleep(SETTLE_MS)
            Screengrab.screenshot(name)
        } finally {
            try {
                scenario.close()
            } catch (teardownFailure: Throwable) {
                // The NEW_TASK deep-link reuse detached the instance from
                // ActivityScenario, so close() throws trying to reach DESTROYED.
                // The capture already happened — swallow rather than fail the run.
            }
        }
    }

    /** Launch a fresh activity, run [navigate], let images settle, then shoot [name]. */
    private fun capture(
        name: String,
        navigate: () -> Unit,
    ) {
        ActivityScenario.launch(MainActivity::class.java).use {
            navigate()
            // Marketing shots must show the screen, not the IME. Search auto-focuses
            // its field (and calls keyboardController.show()), and a soft keyboard
            // raised on one screen persists at the window level into later captures.
            // Dismiss it before settling so the keyboard never bleeds into a frame.
            hideKeyboardIfShown()
            device.waitForIdle()
            Thread.sleep(SETTLE_MS)
            Screengrab.screenshot(name)
        }
    }

    /**
     * Dismiss the soft keyboard iff it is currently shown. Detects the IME via the
     * accessibility window list ([AccessibilityWindowInfo.TYPE_INPUT_METHOD]) and
     * only then dispatches a single back press — which the system routes to the IME
     * to hide it. The guard matters: a back press with no keyboard up would instead
     * pop the navigation stack and ruin the capture.
     */
    private fun hideKeyboardIfShown() {
        val windows = InstrumentationRegistry.getInstrumentation().uiAutomation.windows
        val keyboardShown = windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        // The returned infos are caller-owned pooled instances on API 28–32 (our
        // minSdk floor); release them so the journey doesn't leak across its eight
        // captures. recycle() is a documented no-op from API 33 on.
        @Suppress("DEPRECATION")
        windows.forEach { it.recycle() }
        if (keyboardShown) {
            device.pressBack()
            device.waitForIdle()
        }
    }

    /** Wait for a Compose testTag (surfaced as a bare resource-id) to appear. */
    private fun awaitTag(resId: String) {
        device.wait(Until.findObject(By.res(resId)), WAIT_MS)
            ?: throw AssertionError(
                "Tag '$resId' did not appear within ${WAIT_MS}ms — verify the bench fake " +
                    "is bound and the testTag is still applied.",
            )
    }

    /** Tap the first node matching [selector], failing with [describe] if absent. */
    private fun tap(
        selector: BySelector,
        describe: () -> String,
    ) {
        val node =
            device.wait(Until.findObject(selector), WAIT_MS)
                ?: throw AssertionError("Could not find ${describe()} within ${WAIT_MS}ms")
        node.click()
        device.waitForIdle()
    }

    /**
     * Type [text] into the field tagged [resId] (drives query-based screens).
     * Focuses the field, then dispatches the text as key events via
     * `sendStringSync` — UiObject2.setText does NOT reliably reach a Compose
     * `TextFieldState`-backed field, leaving the query blank (search then never
     * leaves its Discover phase, so the result tabs/lists never mount).
     */
    private fun typeInto(
        resId: String,
        text: String,
    ) {
        val field =
            device.wait(Until.findObject(By.res(resId)), WAIT_MS)
                ?: throw AssertionError("Could not find input '$resId' within ${WAIT_MS}ms")
        field.click()
        device.waitForIdle()
        Thread.sleep(FOCUS_SETTLE_MS)
        InstrumentationRegistry.getInstrumentation().sendStringSync(text)
        device.waitForIdle()
        // The field's imeAction is Search: results (and the Posts/People/Feeds
        // tabs) mount on SUBMIT, not on type. Press the action to leave the
        // Discover phase and enter Results.
        device.pressEnter()
        device.waitForIdle()
    }

    /** Find [resId] in the feed, scrolling down up to [SCROLL_TRIES] times if needed. */
    private fun scrollFeedTo(resId: String): UiObject2 {
        repeat(SCROLL_TRIES) {
            device.findObject(By.res(resId))?.let { return it }
            device.findObject(By.res(FEED_LIST))?.scroll(Direction.DOWN, 0.8f)
            device.waitForIdle()
        }
        // Final attempt waits a full window: a tile that just scrolled into view
        // may not have composed its node yet, and a non-blocking findObject would
        // miss it and fail spuriously on slower devices.
        return device.wait(Until.findObject(By.res(resId)), WAIT_MS)
            ?: throw AssertionError("'$resId' not found after $SCROLL_TRIES feed scrolls")
    }

    private companion object {
        // Tags contracted with the feature modules (mirrors the macrobench
        // FEED_LIST_RES_ID convention); single-arg By.res matches the
        // unqualified resource-id Compose emits under testTagsAsResourceId.
        const val FEED_LIST = "feed_list"
        const val FEED_VIDEO_EMBED = "feed_video_embed"
        const val VIDEO_PLAYER = "video_player"
        const val CHAT_CONVO_ITEM = "chat_convo_item"
        const val CHAT_THREAD_LIST = "chat_thread_list"
        const val NOTIFICATIONS_LIST = "notifications-list"
        const val SEARCH_INPUT = "search_input"
        const val SEARCH_POSTS_LIST = "search_posts_list"
        const val SEARCH_FEEDS_LIST = "search_feeds_list"
        const val PROFILE_LIST = "profile_list"
        const val POST_DETAIL_LIST = "post_detail_list"

        // Deep link into the bench gallery post (BenchFakePostThreadRepository /
        // benchGalleryPost). The rkey is a valid 13-char TID so the post
        // deep-link matcher (isValidRkey) accepts it.
        const val POST_DEEPLINK_URI = "https://bsky.app/profile/jess.trails/post/ridgewalk2357"
        const val WAIT_MS = 10_000L
        const val SETTLE_MS = 1_200L
        const val FOCUS_SETTLE_MS = 400L
        const val SCROLL_TRIES = 5

        @JvmField
        @ClassRule
        val localeTestRule = LocaleTestRule()
    }
}
