package net.kikin.nubecita.core.posting

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ShareIntentParser] — the pure classifier behind the inbound
 * Android share target (`ACTION_SEND` / `text/plain`). Strings in, a
 * [SharedContent] sum out; no Android framework types, so it runs on the JVM.
 *
 * Slice B scope: text/URL only. The scheme allowlist (Decision 5 §1) is the
 * security-relevant part — a bare non-http(s) URI must NOT be seeded.
 */
class ShareIntentParserTest {
    private val cap = 5_000

    private fun parse(
        action: String? = ShareIntentParser.ACTION_SEND,
        mimeType: String? = "text/plain",
        extraText: String? = null,
    ) = ShareIntentParser.parse(action, mimeType, extraText, maxTextLength = cap)

    @Test
    fun httpsUrl_isText() {
        assertEquals(SharedContent.Text("https://example.com/a"), parse(extraText = "https://example.com/a"))
    }

    @Test
    fun httpUrl_isText() {
        assertEquals(SharedContent.Text("http://example.com"), parse(extraText = "http://example.com"))
    }

    @Test
    fun plainProse_isText() {
        assertEquals(SharedContent.Text("hello world"), parse(extraText = "hello world"))
    }

    @Test
    fun proseContainingUrl_isSeededVerbatim() {
        // The composer's scanner cards the embedded http(s) URL; we seed as-is.
        assertEquals(
            SharedContent.Text("check https://example.com out"),
            parse(extraText = "check https://example.com out"),
        )
    }

    @Test
    fun bareJavascriptScheme_isInvalid() {
        assertEquals(SharedContent.Invalid, parse(extraText = "javascript:alert(1)"))
    }

    @Test
    fun bareFileScheme_isInvalid() {
        assertEquals(SharedContent.Invalid, parse(extraText = "file:///etc/passwd"))
    }

    @Test
    fun bareContentScheme_isInvalid() {
        assertEquals(SharedContent.Invalid, parse(extraText = "content://com.evil/secret"))
    }

    @Test
    fun bareIntentScheme_isInvalid() {
        assertEquals(SharedContent.Invalid, parse(extraText = "intent://scan/#Intent;scheme=x;end"))
    }

    @Test
    fun leadingAndTrailingWhitespace_isTrimmed() {
        assertEquals(SharedContent.Text("https://example.com"), parse(extraText = "  https://example.com  "))
    }

    @Test
    fun blankOrEmpty_isInvalid() {
        assertEquals(SharedContent.Invalid, parse(extraText = ""))
        assertEquals(SharedContent.Invalid, parse(extraText = "   "))
        assertEquals(SharedContent.Invalid, parse(extraText = null))
    }

    @Test
    fun overLengthCap_isInvalid() {
        assertEquals(SharedContent.Invalid, parse(extraText = "x".repeat(cap + 1)))
    }

    @Test
    fun wrongAction_isInvalid() {
        assertEquals(SharedContent.Invalid, parse(action = "android.intent.action.VIEW", extraText = "https://x.com"))
    }

    @Test
    fun nonTextMime_isInvalid() {
        // Slice B handles text only; an image share isn't parsed here.
        assertEquals(SharedContent.Invalid, parse(mimeType = "image/png", extraText = "https://x.com"))
    }

    @Test
    fun nonPlainTextMime_isInvalid() {
        // Only text/plain — not text/html, text/vcard, etc. The manifest filter
        // advertises text/plain, and this is a world-launchable entry point, so
        // the parser must not silently accept other text subtypes.
        assertEquals(SharedContent.Invalid, parse(mimeType = "text/html", extraText = "https://x.com"))
        assertEquals(SharedContent.Invalid, parse(mimeType = "text/vcard", extraText = "https://x.com"))
    }

    @Test
    fun textPlainWithCharsetParam_isText() {
        // A MIME with parameters (text/plain;charset=utf-8) is still text/plain.
        assertEquals(
            SharedContent.Text("https://x.com"),
            parse(mimeType = "text/plain;charset=utf-8", extraText = "https://x.com"),
        )
    }

    @Test
    fun nullMime_isInvalid() {
        assertEquals(SharedContent.Invalid, parse(mimeType = null, extraText = "https://x.com"))
    }
}
