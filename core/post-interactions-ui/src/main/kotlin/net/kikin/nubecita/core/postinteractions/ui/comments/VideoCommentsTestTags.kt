package net.kikin.nubecita.core.postinteractions.ui.comments

/**
 * Stable Compose `testTag` constants for the video comments sheet UI — the seam
 * between the comments sheet and the `:benchmark` Macrobenchmark module.
 *
 * `:benchmark` does not depend on this module, so the contract is the tag *value*.
 * Renaming [COMMENTS_LIST] or [DOCKED_PILL] requires a coordinated change in
 * `benchmark/.../BenchmarkConstants.kt`.
 */
object VideoCommentsTestTags {
    /** The comments sheet's scrollable `LazyColumn`. */
    const val COMMENTS_LIST: String = "video_comments_list"

    /** The docked comment pill at the foot of the sheet. */
    const val DOCKED_PILL: String = "docked_comment_pill"

    /** The retry button in the error state. */
    const val RETRY_BUTTON: String = "video_comments_retry"
}
