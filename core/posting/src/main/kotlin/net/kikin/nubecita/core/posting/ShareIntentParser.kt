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

    /**
     * An image share (`ACTION_SEND` with an image MIME). The bytes live in
     * `EXTRA_STREAM` — resolved, copied, and byte-verified by `SharedMediaStore`
     * on the `MainActivity` side, not here. [caption] is any accompanying
     * `EXTRA_TEXT` (trimmed, within cap), seeded verbatim into the composer; a
     * blank or oversize caption is dropped but the image is still kept.
     */
    data class Image(
        val caption: String?,
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
        if (mimeType == null) return SharedContent.Invalid
        // Strip any MIME parameters (`text/plain;charset=utf-8`) and match the
        // essence. text/plain is matched exactly (not text/html, text/vcard);
        // image/* is matched by prefix — the real gate is the magic-byte sniff in
        // SharedMediaStore, so the declared image subtype can be permissive.
        val essence = mimeType.substringBefore(';').trim().lowercase(Locale.ROOT)
        return when {
            essence == "text/plain" -> parseText(extraText, maxTextLength)
            essence.startsWith("image/") -> SharedContent.Image(caption = caption(extraText, maxTextLength))
            else -> SharedContent.Invalid
        }
    }

    private fun parseText(
        extraText: String?,
        maxTextLength: Int,
    ): SharedContent {
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

    /**
     * The optional caption that rides along with an image share. Length-gated
     * before trim (same OOM guard as text); a blank or oversize caption becomes
     * null so the image is kept without it. Seeded verbatim — no scheme
     * allowlist, since it is accompanying prose, not the shared payload itself.
     */
    private fun caption(
        extraText: String?,
        maxTextLength: Int,
    ): String? =
        extraText
            ?.takeIf { it.length <= maxTextLength }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
}
