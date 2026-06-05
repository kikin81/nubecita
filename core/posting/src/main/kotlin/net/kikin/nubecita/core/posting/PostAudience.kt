package net.kikin.nubecita.core.posting

/**
 * Who may interact with a newly created top-level post: who can reply
 * ([reply], an `app.bsky.feed.threadgate`) and whether quote posts are
 * allowed ([allowQuotes], an `app.bsky.feed.postgate`).
 *
 * [DEFAULT] is the wide-open audience — anyone can reply and quotes are
 * allowed — and maps to **no** threadgate and **no** postgate record. The
 * composer's audience chip reads "Visible to all" for [DEFAULT] and
 * "Interaction limited" for anything else.
 *
 * No Compose stability annotation — `:core:posting` has no `compose-runtime`
 * dependency, exactly like its neighbour [ComposerAttachment]. Compose
 * consumers therefore see it as an *external* (non-Compose-module) type and
 * treat it as unstable, but it is deeply immutable (every field is a `val`
 * holding a `Boolean` or a [ReplyAudience] whose variants hold only `Boolean`s),
 * so strong skipping — on by default in this Kotlin/Compose toolchain — skips
 * correctly by instance equality: a host composable recomposes only when the
 * audience instance actually changes.
 */
data class PostAudience(
    val reply: ReplyAudience,
    val allowQuotes: Boolean,
) {
    companion object {
        /** Anyone can reply, quotes allowed — writes neither gate record. */
        val DEFAULT = PostAudience(reply = ReplyAudience.Everyone, allowQuotes = true)
    }
}

/**
 * Who may reply to a post.
 *
 * - [Everyone] — no threadgate record (the `allow` field is omitted, which
 *   the lexicon reads as "anyone").
 * - [Nobody] — a threadgate with an **empty** `allow` list (which the lexicon
 *   reads as "no one"). Never conflate this with [Everyone]; an empty `allow`
 *   is the opposite of an omitted one.
 * - [Combination] — a threadgate whose `allow` lists exactly the rules for the
 *   selected audiences. At least one toggle is expected to be on (an all-false
 *   combination is equivalent to [Nobody] and the picker prevents it).
 */
sealed interface ReplyAudience {
    data object Everyone : ReplyAudience

    data object Nobody : ReplyAudience

    data class Combination(
        val followers: Boolean,
        val following: Boolean,
        val mentioned: Boolean,
    ) : ReplyAudience
}
