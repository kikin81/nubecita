package net.kikin.nubecita.feature.settings.impl.data

/**
 * Deterministic hue in `0..359` derived from `did + first char of
 * handle`. Identical algorithm to
 * `:feature:profile:impl/data/AuthorProfileMapper.avatarHueFor` and
 * `:feature:chats:impl/data/ConvoMapper.avatarHueFor`, duplicated
 * here so `:feature:settings:impl` stays free of any cross-feature
 * `:impl`-on-`:impl` dependency. Three call sites is the trigger
 * threshold to promote to a shared `:core:profile` module if/when a
 * fourth shows up.
 *
 * `Math.floorMod` is used (not `abs % 360`) because
 * `abs(Int.MIN_VALUE)` is still `Int.MIN_VALUE` — `floorMod` returns
 * a non-negative result for every input.
 */
internal fun avatarHueFor(
    did: String,
    handle: String,
): Int {
    val seed = did + (handle.firstOrNull()?.toString() ?: "")
    return Math.floorMod(seed.hashCode(), 360)
}
