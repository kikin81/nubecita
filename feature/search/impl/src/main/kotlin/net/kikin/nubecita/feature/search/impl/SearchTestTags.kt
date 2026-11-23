package net.kikin.nubecita.feature.search.impl

/**
 * Compose `testTag`s the `:benchmark` module finds by resource id (the app's
 * root `testTagsAsResourceId` surfaces them as bare ids). The macrobench module
 * does not depend on this one, so it hardcodes the same literals in
 * `BenchmarkConstants`; `SearchTestTagsTest` pins the values so a rename fails a
 * unit test instead of a bench run.
 */
internal object SearchTestTags {
    /** The Posts results `LazyColumn`. */
    const val POSTS_LIST: String = "search_posts_list"

    /** The Explore pane's `LazyColumn` (wide windows only). */
    const val EXPLORE_LIST: String = "search_explore_list"

    /** The Summary pane's `LazyColumn` (wide windows only). */
    const val SUMMARY_LIST: String = "search_summary_list"
}
