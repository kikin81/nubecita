package net.kikin.nubecita.feature.profile.impl.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

// Bluesky's app.bsky.actor.getProfile returns the bio/description as plain text
// with NO richtext facets (unlike posts), so we detect bare URLs ourselves to
// make them tappable. Mirrors the composer's link facet regex
// (:core:posting FacetExtractor.LINK_REGEX): the leading `(?:^|[^\w@])` avoids
// matching inside emails / @handles, and the URL is capture group 1.
private val BIO_LINK_REGEX =
    Regex(
        """(?:^|[^\w@])(https?://(?:www\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\.[a-zA-Z0-9()]{1,6}\b(?:[-a-zA-Z0-9()@:%_+.~#?&/=]*[-a-zA-Z0-9@%_+~#/=])?)""",
    )

/**
 * Detects `http(s)://` URLs in [text], returning each URL's UTF-16 char range
 * (as it appears in [text]) paired with the URL string. Pure + unit-testable;
 * the byte-offset facet dance posts use isn't needed because we build the
 * [AnnotatedString] directly from char ranges.
 */
internal fun detectLinkRanges(text: String): List<Pair<IntRange, String>> =
    BIO_LINK_REGEX
        .findAll(text)
        .mapNotNull { match ->
            val url = match.groups[1] ?: return@mapNotNull null
            url.range to url.value
        }.toList()

/**
 * Builds a bio [AnnotatedString] with each detected URL wrapped in a clickable,
 * [linkColor]-styled [LinkAnnotation.Url]. The per-link [onLinkClick] listener
 * overrides Compose's default `LocalUriHandler` so taps route to an in-app
 * Custom Tab (see [openBioLinkInCustomTab]) instead of the external browser.
 */
internal fun buildBioAnnotatedString(
    bio: String,
    linkColor: Color,
    onLinkClick: (String) -> Unit,
): AnnotatedString =
    buildAnnotatedString {
        append(bio)
        for ((range, url) in detectLinkRanges(bio)) {
            val start = range.first
            val end = range.last + 1
            // Explicit SpanStyle for the visual (LinkAnnotation's own TextLinkStyles
            // don't render reliably in the screenshot host); LinkAnnotation carries
            // the click → Custom Tab via onLinkClick.
            addStyle(SpanStyle(color = linkColor), start, end)
            addLink(
                url = LinkAnnotation.Url(url = url, linkInteractionListener = { onLinkClick(url) }),
                start = start,
                end = end,
            )
        }
    }

/**
 * Opens [url] in an in-app Chrome Custom Tab, matching the profile screen's
 * external-embed-card handling. Narrowed catch: silently no-ops only when no
 * CCT-capable browser is installed, so a bio link can never crash the app
 * (consistent with nubecita-ywme's login guard); other failures propagate.
 */
internal fun openBioLinkInCustomTab(
    context: Context,
    url: String,
) {
    try {
        CustomTabsIntent
            .Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, Uri.parse(url))
    } catch (_: ActivityNotFoundException) {
        // No browser available — silent no-op.
    }
}
