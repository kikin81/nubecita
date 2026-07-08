package net.kikin.nubecita.feature.login.impl

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
 * 3. **DID passthrough** — a `did:` identifier is opaque and case-sensitive, so it
 *    is returned as-is (no lowercasing, no suffix).
 * 4. Lowercase — atproto handles are case-insensitive.
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
    // DIDs are opaque, case-sensitive identifiers — never mangle or suffix them.
    if (withoutAt.startsWith("did:", ignoreCase = true)) return withoutAt
    val lower = withoutAt.lowercase()
    // Only a bare username (no dot) can be shorthand for a bsky.social handle;
    // anything containing a dot is already a fully-qualified handle/domain.
    return if (lower.contains('.')) lower else "$lower.bsky.social"
}
