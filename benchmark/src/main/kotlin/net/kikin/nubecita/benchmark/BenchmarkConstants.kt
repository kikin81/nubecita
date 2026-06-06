package net.kikin.nubecita.benchmark

/**
 * Package name of the target APK. Hardcoded — Macrobench requires a
 * literal, and there's no other consumer of this constant so reading
 * it from BuildConfig would only add ceremony.
 */
internal const val TARGET_PACKAGE: String = "net.kikin.nubecita"

/**
 * Default iteration count for every benchmark. AndroidX's own default
 * is 5; keeping the value here so all benchmark classes share one
 * knob — bumping to 10 (for variance reduction) is a one-line change.
 */
internal const val DEFAULT_ITERATIONS: Int = 5

/**
 * Resource-id selector that surfaces FeedScreen's `LazyColumn` to
 * UIAutomator. Compose's `testTagsAsResourceId = true` (root semantics
 * flag on `MainActivity`) exposes the `feed_list` Compose `testTag` as
 * a bare `resource-id` value with no package qualifier — so the
 * single-arg `By.res("feed_list")` form matches and the two-arg
 * `By.res(packageName, id)` form (which builds `packageName:id/<id>`)
 * silently never matches. The string is the contract between this
 * module and `:feature:feed:impl`, intentionally hardcoded — the
 * macrobench module deliberately doesn't depend on the production
 * module — and pinned by `:feature:feed:impl/FeedTestTagsTest` so a
 * silent rename surfaces in fast unit-test runs.
 *
 * Single source of truth shared by `BaselineProfileGenerator` (waits
 * for it during profile capture) and `FeedScrollBenchmark` (waits for
 * it before flinging).
 */
internal const val FEED_LIST_RES_ID: String = "feed_list"

// ── Post-startup journey selectors (nubecita-ioe5) ────────────────────
//
// Same contract shape as FEED_LIST_RES_ID: each is a Compose `testTag`
// surfaced as a bare `resource-id` via `testTagsAsResourceId`, matched
// with the single-arg `By.res(id)`. The macrobench module deliberately
// does NOT depend on the feature modules, so these strings are the
// hardcoded contract — keep them in sync with the tag at each call site.

/** `PostDetailScreen`'s loaded-thread `LazyColumn`. */
internal const val POST_DETAIL_LIST_RES_ID: String = "post_detail_list"

/** `ProfileScreenContent`'s `LazyColumn`. */
internal const val PROFILE_LIST_RES_ID: String = "profile_list"

/** The Search `SearchBar` input field. */
internal const val SEARCH_INPUT_RES_ID: String = "search_input"

/** The composer's `OutlinedTextField`. */
internal const val COMPOSER_TEXT_FIELD_RES_ID: String = "composer_text_field"

/** A conversation row in the Chats list. */
internal const val CHAT_CONVO_ITEM_RES_ID: String = "chat_convo_item"

/** The Chat thread's message `LazyColumn`. */
internal const val CHAT_THREAD_LIST_RES_ID: String = "chat_thread_list"

// Bottom-nav item content descriptions (== the localized tab labels; the
// generation device is assumed English). Tapped via `By.desc(...)`.
internal const val TAB_FEED_DESC: String = "Feed"
internal const val TAB_SEARCH_DESC: String = "Search"
internal const val TAB_CHATS_DESC: String = "Chats"
internal const val TAB_YOU_DESC: String = "You"

/** Compose FAB icon content description (`feed_compose_new_post`). */
internal const val COMPOSE_FAB_DESC: String = "Compose new post"
