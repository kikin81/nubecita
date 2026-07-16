package net.kikin.nubecita.core.posting

/**
 * The classified result of an inbound Android share (`ACTION_SEND`). Slice B of
 * the share-target epic covers text/links only; image variants are added by a
 * later slice.
 */
sealed interface SharedContent {
    /**
     * Text to seed the composer with, verbatim. May contain an `http`/`https`
     * URL, which the composer's existing scanner turns into a link card; a bare
     * non-http(s) URI never reaches here (see [ShareIntentParser]).
     */
    data class Text(
        val text: String,
    ) : SharedContent

    /** Nothing usable was shared (blank, oversized, wrong action/MIME, or a bare unsafe-scheme URI). */
    data object Invalid : SharedContent
}

/**
 * Pure classifier for an inbound `ACTION_SEND` share. Takes the already-extracted
 * intent fields (no Android types) so it runs as a plain JVM unit test; the
 * `:app` share branch does the `Intent` → fields extraction and calls this.
 *
 * Security (share-target design Decision 5 §1): the entry point is
 * world-launchable, so the payload is untrusted. This slice handles `text/plain`
 * only, allowlists the URL scheme to `http`/`https`, and caps the length.
 */
object ShareIntentParser {
    const val ACTION_SEND: String = "android.intent.action.SEND"

    // Leading URI scheme, per RFC 3986 (`scheme = ALPHA *( ALPHA / DIGIT / "+" / "-" / "." )`).
    private val SCHEME = Regex("^([a-zA-Z][a-zA-Z0-9+.\\-]*):")

    fun parse(
        action: String?,
        mimeType: String?,
        extraText: String?,
        maxTextLength: Int,
    ): SharedContent {
        if (action != ACTION_SEND) return SharedContent.Invalid
        if (mimeType == null || !mimeType.startsWith("text/")) return SharedContent.Invalid

        val text = extraText?.trim().orEmpty()
        if (text.isEmpty() || text.length > maxTextLength) return SharedContent.Invalid

        // A whitespace-free payload that is a single URI: allowlist the scheme to
        // http/https. A bare javascript:/file:/content:/intent:/… URI is rejected
        // (never seeded). Prose (has whitespace, or no leading scheme) is seeded
        // verbatim — the composer's scanner only ever cards http/https within it.
        if (text.none { it.isWhitespace() }) {
            val scheme =
                SCHEME
                    .find(text)
                    ?.groupValues
                    ?.get(1)
                    ?.lowercase()
            if (scheme != null && scheme != "http" && scheme != "https") return SharedContent.Invalid
        }

        return SharedContent.Text(text)
    }
}
