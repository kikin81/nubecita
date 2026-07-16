package net.kikin.nubecita.core.posting

import java.util.Locale

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
        // Exactly text/plain — not text/html, text/vcard, etc. Strip any MIME
        // parameters (`text/plain;charset=utf-8`) and match the essence.
        if (mimeType == null) return SharedContent.Invalid
        val essence = mimeType.substringBefore(';').trim().lowercase(Locale.ROOT)
        if (essence != "text/plain") return SharedContent.Invalid

        // Length-gate BEFORE trim: an untrusted sender could hand over a
        // multi-megabyte EXTRA_TEXT, and trim() would allocate a full copy first
        // (OOM risk on low-end devices). Reject on the raw length, then trim.
        if (extraText == null || extraText.length > maxTextLength) return SharedContent.Invalid
        val text = extraText.trim()
        if (text.isEmpty()) return SharedContent.Invalid

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
                    ?.lowercase(Locale.ROOT)
            if (scheme != null && scheme != "http" && scheme != "https") return SharedContent.Invalid
        }

        return SharedContent.Text(text)
    }
}
