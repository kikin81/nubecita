package net.kikin.nubecita.data.models

import androidx.compose.runtime.Immutable

/**
 * A post's place in its author's thread: "post [index] of [count]".
 *
 * The numbers are the server's (`opThreadPostIndex` / `opThreadPostCount` on
 * feed entries, quoted record views and thread items). They describe the
 * unbroken chain of self-replies by the root author, starting at the root, and
 * are taken from the whole chain rather than from the slice a response happened
 * to carry. The app never counts posts to produce one.
 *
 * Build one through [of], which is the only place the pair is validated.
 */
@Immutable
public data class ThreadPositionUi(
    val index: Int,
    val count: Int,
) {
    /** The first post of the same thread. */
    public fun root(): ThreadPositionUi? = of(index = 1, count = count.toLong())

    /**
     * The post this one replies to, or null for the first post. Exact rather
     * than a guess: post *n* of an unbroken chain replies to post *n − 1*.
     */
    public fun parent(): ThreadPositionUi? = of(index = index - 1L, count = count.toLong())

    public companion object {
        /**
         * The position for a server-provided pair, or null when either field is
         * missing or the pair is not a real position (`index` below 1 or above
         * `count`, or a value beyond [Int]).
         */
        public fun of(
            index: Long?,
            count: Long?,
        ): ThreadPositionUi? {
            if (index == null || count == null) return null
            if (index < 1 || count < 1 || index > count || count > Int.MAX_VALUE) return null
            return ThreadPositionUi(index = index.toInt(), count = count.toInt())
        }
    }
}
