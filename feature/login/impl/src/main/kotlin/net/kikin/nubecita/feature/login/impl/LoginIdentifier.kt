package net.kikin.nubecita.feature.login.impl

import java.util.Locale

/**
 * Normalize a raw login identifier typed on the sign-in screen into something the
 * atproto OAuth resolver can actually resolve, WITHOUT altering valid inputs.
 *
 * This exists because GA4 showed `handle_not_found` (stage=begin) as by far the
 * dominant login failure: users type `@alice`, a bare `alice`, or `Alice.Bsky.Social`
 * and the raw string fails handle resolution before OAuth ever launches
 * (`nubecita-mbzp`).
 *
 * Rules (each deliberately conservative — a valid handle/DID must round-trip
 * unchanged):
 * 1. Trim surrounding whitespace.
 * 2. Strip a single leading `@` (Twitter/Mastodon muscle memory; atproto handles
 *    never carry one), then re-trim so `"@ alice"` collapses cleanly.
 * 3. **DID normalization** — the `did:` scheme and method segment are lowercased
 *    (both are case-insensitive per the DID spec and a soft keyboard may
 *    auto-capitalize the leading `Did:`), while the case-sensitive
 *    method-specific identifier is preserved verbatim.
 * 4. Lowercase — atproto handles are case-insensitive. `Locale.ROOT` avoids the
 *    Turkish `I`→`ı` dotless-i mangling that a locale-sensitive `lowercase()`
 *    would inflict on a handle.
 * 5. **Append `.bsky.social` only when there is no dot.** A bare username is the
 *    *only* dotless input, so this never touches a custom-domain handle
 *    (`franciscovelazquez.com`), a subdomain (`me.example.co.uk`), or a DID — all of
 *    which contain a dot (or were handled in rule 3) and pass through untouched.
 *
 * Returns `""` for blank / `@`-only input; the caller keeps its existing
 * blank-handle validation.
 *
 * Deliberately out of scope (YAGNI until data warrants): extracting a handle from a
 * pasted `bsky.app/profile/<handle>` URL, and entryway-first login with no handle
 * at all (tracked separately as the atproto-kotlin "Tier 2" work).
 */
internal fun normalizeLoginIdentifier(raw: String): String {
    val withoutAt = raw.trim().removePrefix("@").trim()
    if (withoutAt.isEmpty()) return ""
    // DID: normalize the case-insensitive scheme + method, preserve the identifier.
    if (withoutAt.startsWith("did:", ignoreCase = true)) return normalizeDid(withoutAt)
    val lower = withoutAt.lowercase(Locale.ROOT)
    // Only a bare username (no dot) can be shorthand for a bsky.social handle;
    // anything containing a dot is already a fully-qualified handle/domain.
    return if (lower.contains('.')) lower else "$lower.bsky.social"
}

/**
 * Lowercase the `did:<method>:` prefix (both are case-insensitive per the DID
 * Core spec) while preserving the case-sensitive method-specific identifier. A
 * malformed DID missing its method or identifier is returned unchanged so the
 * resolver — not this normalizer — decides it's invalid.
 */
private fun normalizeDid(did: String): String {
    val parts = did.split(":", limit = 3)
    // Malformed — missing/blank method or identifier — returned unchanged so the
    // resolver, not this normalizer, rejects it.
    if (parts.size < 3 || parts[1].isBlank() || parts[2].isBlank()) return did
    return "did:${parts[1].lowercase(Locale.ROOT)}:${parts[2]}"
}
