package net.kikin.nubecita.core.profile

/**
 * Deterministic hue in `0..359` derived from `did + first char of
 * handle`. Used as the seed for the initials-disc avatar fallback
 * across every surface that renders an actor (Profile hero, Chats
 * convo rows, Settings identity header, future hover cards, etc.) so
 * the same user paints identically everywhere.
 *
 * Hashing [did] alone would mean two users with similar DIDs map to
 * the same hue; mixing in `handle.first()` spreads the hash across
 * the alphabet too. `Math.floorMod` is used (not `abs % 360`) because
 * `abs(Int.MIN_VALUE)` is still `Int.MIN_VALUE` — `floorMod` returns
 * a non-negative result for every input.
 *
 * Stable by contract: callers commit to specific hues across screens
 * because the (did, handle) seed is stable, and `String.hashCode` is
 * defined by the JVM spec (sum of `s[i] * 31^(n-1-i)`) — independent
 * of JVM vendor, version, or runtime architecture. Tests pin
 * representative pairs to literal expected hues so any drift in the
 * formula surfaces immediately.
 *
 * Lives in `:core:profile` because the same function used to be
 * duplicated across `:feature:profile:impl`, `:feature:chats:impl`,
 * and `:feature:settings:impl`. Promoted in nubecita-ws8k once the
 * third copy landed.
 */
fun avatarHueFor(
    did: String,
    handle: String,
): Int {
    val seed = did + (handle.firstOrNull()?.toString() ?: "")
    return Math.floorMod(seed.hashCode(), 360)
}
