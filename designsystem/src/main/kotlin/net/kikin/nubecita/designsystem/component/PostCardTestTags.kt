package net.kikin.nubecita.designsystem.component

/**
 * Stable Compose `testTag` constants for [PostCard].
 *
 * The seam between the post card and the `:benchmark` module's
 * `BaselineProfileGenerator`, which taps a card to open post detail.
 * `:benchmark` does not depend on `:designsystem`, so the contract is the
 * tag *value*: renaming [BODY] means updating `POST_CARD_BODY_RES_ID` in
 * `benchmark/.../BenchmarkConstants.kt` in the same change (pinned by
 * `PostCardTestTagsTest`).
 */
object PostCardTestTags {
    /**
     * The post's body text. It sits inside the card's own `onTap`, so a tap
     * on it opens post detail — unlike a coordinate tap into the card, which
     * can land on the action row and like or repost on a real account.
     */
    const val BODY: String = "post_card_body"
}
