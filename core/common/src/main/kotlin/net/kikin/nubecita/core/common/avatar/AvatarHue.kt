package net.kikin.nubecita.core.common.avatar

/**
 * Deterministic hue in `0..359` derived from `did + first char of handle`.
 * Seeds the initials-disc avatar fallback so the same user paints identically
 * on every surface. `Math.floorMod` (not `abs % 360`) because
 * `abs(Int.MIN_VALUE) == Int.MIN_VALUE`; floorMod is non-negative for all input.
 *
 * Stable by contract: callers commit to specific hues across screens because the
 * (did, handle) seed is stable, and `String.hashCode` is defined by the JVM spec
 * (sum of `s[i] * 31^(n-1-i)`) — independent of JVM vendor, version, or runtime
 * architecture. Tests pin representative pairs to literal expected hues so any
 * drift in the formula surfaces immediately.
 *
 * Lives in `:core:common` so both feature modules and `:designsystem`
 * (which renders the fallback) can share it without a layering inversion.
 */
fun avatarHueFor(
    did: String,
    handle: String,
): Int {
    val seed = did + (handle.firstOrNull()?.toString() ?: "")
    return Math.floorMod(seed.hashCode(), 360)
}
