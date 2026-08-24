package net.kikin.nubecita.feature.chats.impl.ui

import android.content.ActivityNotFoundException
import android.content.Context
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.net.toUri
import io.github.kikin81.atproto.app.bsky.richtext.Facet
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.core.common.text.LinkPatterns
import net.kikin.nubecita.data.models.FacetTarget
import net.kikin.nubecita.designsystem.component.rememberTappableBlueskyAnnotatedString

/**
 * Renders a message's body as tappable richtext.
 *
 * Two paths, because DMs predate nubecita sending facets:
 *
 * - **[facets] present** — the wire told us where the links and mentions are, so
 *   defer to the shared post renderer. Identical look and behaviour to a post
 *   body, and it carries that renderer's http(s)-only guard: a facet URI is
 *   whatever the sender put there, and a DM sender is not necessarily someone
 *   you follow.
 * - **[facets] empty** — every message sent before nubecita attached facets
 *   (nubecita-io24.1), plus any client that omits them. Detect URLs with the
 *   shared [LinkPatterns] regex, which only matches `https?://`, so this path
 *   cannot produce a non-web scheme either.
 *
 * The fallback is deliberately links-only. It is a rescue for text the server
 * gave us no annotations for, not a second source of truth — when the wire
 * carries facets they win outright, so nothing here can shadow server state.
 *
 * [linkColor] should be the host bubble's own content colour rather than
 * `colorScheme.primary`. This began as an AA fix — primary on `primaryContainer`
 * measured 3.11:1, below the 4.5:1 floor. The Azure palette lifted that pairing
 * to 4.99:1, so the contrast argument no longer forces it, but the behaviour is
 * kept deliberately: the link is distinguished by the underline the renderer
 * applies rather than by hue, and the bubble's own content colour is what keeps
 * it legible on every host surface.
 */
@Composable
internal fun rememberMessageBodyAnnotatedString(
    text: String,
    facets: ImmutableList<Facet>,
    linkColor: Color,
    onFacetTap: (FacetTarget) -> Unit,
): AnnotatedString =
    if (facets.isNotEmpty()) {
        rememberTappableBlueskyAnnotatedString(
            text = text,
            facets = facets,
            onFacetTap = onFacetTap,
            linkStyle = SpanStyle(color = linkColor),
            // Required, not decorative: without an explicit TextLinkStyles the
            // LinkAnnotation inherits the theme default and repaints the link
            // `primary` rather than the bubble's content colour.
            linkStyles = bubbleLinkStyles(linkColor),
        )
    } else {
        buildFallbackLinkAnnotatedString(text = text, linkColor = linkColor, onFacetTap = onFacetTap)
    }

/**
 * Regex linkification for facet-less messages.
 *
 * Memoized on `(text, linkColor)` with the listener reading the latest
 * [onFacetTap] through [rememberUpdatedState] — the same shape the shared
 * renderer uses. Both halves are needed: without `remember`, every visible
 * bubble in a scrolling thread rebuilds its AnnotatedString on each
 * recomposition; without `rememberUpdatedState`, the memoized string would pin
 * the first lambda and taps would route to a stale handler.
 */
@Composable
private fun buildFallbackLinkAnnotatedString(
    text: String,
    linkColor: Color,
    onFacetTap: (FacetTarget) -> Unit,
): AnnotatedString {
    val latestOnFacetTap = rememberUpdatedState(onFacetTap)
    return remember(text, linkColor) {
        linkifiedAnnotatedString(text, linkColor) { latestOnFacetTap.value(it) }
    }
}

/** Pure builder — no Compose runtime, so it is unit-testable on its own. */
private fun linkifiedAnnotatedString(
    text: String,
    linkColor: Color,
    onFacetTap: (FacetTarget) -> Unit,
): AnnotatedString =
    buildAnnotatedString {
        append(text)
        for (match in LinkPatterns.URL_REGEX.findAll(text)) {
            val url = match.groups[LinkPatterns.URL_GROUP] ?: continue
            val start = url.range.first
            val end = url.range.last + 1
            addStyle(SpanStyle(color = linkColor), start, end)
            addLink(
                LinkAnnotation.Clickable(tag = "link", styles = bubbleLinkStyles(linkColor)) {
                    onFacetTap(FacetTarget.Link(url.value))
                },
                start,
                end,
            )
        }
    }

/**
 * Opens a tapped message link in an in-app Custom Tab, matching how profile
 * bios and external-embed cards behave. Narrow catch: a device with no
 * CCT-capable browser silently no-ops rather than crashing the thread — same
 * posture as `openBioLinkInCustomTab`.
 *
 * Scheme safety is upstream, not here: both paths in
 * [rememberMessageBodyAnnotatedString] only ever emit `http(s)` URLs, so this
 * cannot be handed an `intent:` or `file:` URI by a hostile sender.
 */
internal fun openMessageLinkInCustomTab(
    context: Context,
    url: String,
) {
    try {
        CustomTabsIntent
            .Builder()
            .setShowTitle(true)
            .build()
            .launchUrl(context, url.toUri())
    } catch (_: ActivityNotFoundException) {
        // No browser available — silent no-op.
    }
}

/**
 * Link styling for a message bubble: the bubble's own content colour plus an
 * underline. Colour alone can't carry the affordance here — the link has to sit
 * on `primaryContainer` (outgoing) or `surfaceContainerHigh` (incoming), and the
 * one hue that reads as "link" (`primary`) fails AA against the former.
 */
private fun bubbleLinkStyles(linkColor: Color): TextLinkStyles =
    TextLinkStyles(
        style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline),
    )
