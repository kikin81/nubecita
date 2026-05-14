package net.kikin.nubecita.core.auth

private const val DID_IDENTIFIER_PREVIEW = 8

// Truncate the method-specific-id of a DID for logging. AT Protocol DIDs are
// `did:<method>:<method-specific-id>`; for `did:plc` the id is a 24-char
// base32, but for `did:web` it can contain additional ':' separators
// (e.g. `did:web:example.com:user:alice`). Anchor on the first two colons so
// the preview always covers the full method-specific-id regardless of any
// colons inside it. Keeping the method prefix + first 8 chars of the
// identifier is enough to disambiguate single-account cache events without
// committing the full PII-grade DID to a log surface that may be captured
// by a future release crash reporter.
fun String.redactDid(): String {
    val firstColon = indexOf(':')
    if (firstColon < 0) return this
    val secondColon = indexOf(':', startIndex = firstColon + 1)
    if (secondColon < 0 || secondColon == length - 1) return this
    val prefix = substring(0, secondColon + 1)
    val identifier = substring(secondColon + 1)
    return if (identifier.length <= DID_IDENTIFIER_PREVIEW) {
        this
    } else {
        prefix + identifier.take(DID_IDENTIFIER_PREVIEW) + "…"
    }
}
