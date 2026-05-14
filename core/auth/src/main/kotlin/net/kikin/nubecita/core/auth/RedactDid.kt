package net.kikin.nubecita.core.auth

const val DID_IDENTIFIER_PREVIEW = 8

// Truncate the DID identifier component for logging. bsky DIDs are
// `did:plc:<24-char-base32>`; keeping the method prefix + first 8 chars
// of the identifier is enough to disambiguate single-account cache
// events without committing the full PII-grade DID to a log surface
// that may be captured by a future release crash reporter.
fun String.redactDid(): String {
    val lastColon = lastIndexOf(':')
    if (lastColon < 0 || lastColon == length - 1) return this
    val prefix = substring(0, lastColon + 1)
    val identifier = substring(lastColon + 1)
    return if (identifier.length <= DID_IDENTIFIER_PREVIEW) {
        this
    } else {
        prefix + identifier.take(DID_IDENTIFIER_PREVIEW) + "…"
    }
}
