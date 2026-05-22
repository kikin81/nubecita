package net.kikin.nubecita

import net.kikin.nubecita.core.auth.redactDid

/**
 * Replaces any `did:<method>:<identifier>` substrings in a URI path
 * with their `redactDid()`-truncated form, leaving non-DID path
 * segments untouched.
 *
 * Used by `MainActivity.handleIntent`'s unmatched deep-link Timber.d
 * log. DIDs are classified as PII-grade in this codebase (see
 * `core/auth/RedactDid.kt`), and the unmatched branch logs the URI
 * for diagnostic purposes — without redaction, a deep link like
 * `nubecita://profile/did:plc:abc...` would land the full DID in
 * crash-reporter breadcrumbs.
 *
 * The regex is anchored on `[^/?#]+` so it stops at the next path
 * separator or query/fragment boundary; this preserves the URI's
 * shape while truncating only the sensitive identifier.
 *
 * Handles are intentionally NOT redacted — per the kf6k.5 security
 * checklist, AT Protocol handles are public identifiers and the
 * diagnostic value of seeing `alice.bsky.social` in an unmatched-link
 * log outweighs the marginal privacy cost.
 */
internal fun redactDidsInPath(path: String): String = path.replace(DID_SEGMENT_REGEX) { match -> match.value.redactDid() }

private val DID_SEGMENT_REGEX = Regex("""did:[a-z0-9]+:[^/?#]+""")
