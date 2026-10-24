package net.kikin.nubecita.designsystem.component

import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import io.github.kikin81.atproto.app.bsky.richtext.Facet
import io.github.kikin81.atproto.app.bsky.richtext.FacetByteSlice
import io.github.kikin81.atproto.app.bsky.richtext.FacetLink
import io.github.kikin81.atproto.app.bsky.richtext.FacetMention
import io.github.kikin81.atproto.app.bsky.richtext.FacetTag
import io.github.kikin81.atproto.runtime.Did
import io.github.kikin81.atproto.runtime.Uri
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.FacetTarget
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [buildTappableBlueskyAnnotatedString] — the pure builder behind
 * the post-body rich text. We assert the *routing*: which [FacetTarget] a tap on
 * each facet span produces. Tapping is simulated by pulling the link annotations
 * off the result and invoking each `LinkAnnotation.Clickable` listener directly,
 * so no Compose runtime / device is needed.
 */
class TappableBlueskyAnnotatedStringTest {
    private val linkStyle = SpanStyle()

    // A mention → its own account's profile, carrying the DID (not the author).
    @Test
    fun mentionRoutesToMentionTargetWithDid() {
        // "Hello @alice.bsky.social" — mention spans bytes 6..24 (ASCII → char offsets match).
        val text = "Hello @alice.bsky.social"
        val facets =
            persistentListOf(
                Facet(
                    features = listOf(FacetMention(did = Did("did:plc:alice000000000000000"))),
                    index = FacetByteSlice(byteStart = 6, byteEnd = 24),
                ),
            )
        var tapped: FacetTarget? = null
        val annotated = buildTappableBlueskyAnnotatedString(text, facets, linkStyle) { tapped = it }

        val link = annotated.getLinkAnnotations(0, annotated.length).single().item as LinkAnnotation.Clickable
        requireNotNull(link.linkInteractionListener).onClick(link)

        assertEquals(FacetTarget.Mention("did:plc:alice000000000000000"), tapped)
    }

    // A link facet → in-app browser, carrying the raw URI.
    @Test
    fun linkRoutesToLinkTargetWithUri() {
        val text = "check out https://nubecita.app"
        val facets =
            persistentListOf(
                Facet(
                    features = listOf(FacetLink(uri = Uri("https://nubecita.app"))),
                    index = FacetByteSlice(byteStart = 10, byteEnd = 30),
                ),
            )
        var tapped: FacetTarget? = null
        val annotated = buildTappableBlueskyAnnotatedString(text, facets, linkStyle) { tapped = it }

        val link = annotated.getLinkAnnotations(0, annotated.length).single().item as LinkAnnotation.Clickable
        requireNotNull(link.linkInteractionListener).onClick(link)

        assertEquals(FacetTarget.Link("https://nubecita.app"), tapped)
    }

    // Security: facet URIs are untrusted content. A non-web scheme (intent:,
    // market:, file:, …) must be styled but NOT tappable, so it can never reach a
    // host's Custom Tab and drive app-switching / a file probe.
    @Test
    fun nonWebSchemeLinkIsStyledButNotTappable() {
        val text = "get the app market://details?id=com.evil"
        val start = text.indexOf("market://")
        val facets =
            persistentListOf(
                Facet(
                    features = listOf(FacetLink(uri = Uri("market://details?id=com.evil"))),
                    index = FacetByteSlice(byteStart = start.toLong(), byteEnd = text.length.toLong()),
                ),
            )
        val annotated = buildTappableBlueskyAnnotatedString(text, facets, linkStyle) { }

        assertTrue(
            annotated.getLinkAnnotations(0, annotated.length).isEmpty(),
            "a non-http(s) link scheme must not be a tappable link annotation",
        )
        assertEquals(text, annotated.text)
    }

    // The core "multiple handles in one post" case: each mention is its own span
    // and routes to its own DID — no manual hit-testing, no shared target.
    @Test
    fun multipleMentionsEachRouteToTheirOwnDid() {
        // "hi @a.bsky.social and @b.bsky.social"
        //     ^3           ^18(space) @b starts at 22
        val text = "hi @a.bsky.social and @b.bsky.social"
        val aStart = text.indexOf("@a")
        val aEnd = aStart + "@a.bsky.social".length
        val bStart = text.indexOf("@b")
        val bEnd = bStart + "@b.bsky.social".length
        val facets =
            persistentListOf(
                Facet(
                    features = listOf(FacetMention(did = Did("did:plc:aaaa00000000000000000"))),
                    index = FacetByteSlice(byteStart = aStart.toLong(), byteEnd = aEnd.toLong()),
                ),
                Facet(
                    features = listOf(FacetMention(did = Did("did:plc:bbbb00000000000000000"))),
                    index = FacetByteSlice(byteStart = bStart.toLong(), byteEnd = bEnd.toLong()),
                ),
            )
        val captured = mutableListOf<FacetTarget>()
        val annotated = buildTappableBlueskyAnnotatedString(text, facets, linkStyle) { captured += it }

        val links = annotated.getLinkAnnotations(0, annotated.length).sortedBy { it.start }
        assertEquals(2, links.size)
        links.forEach { requireNotNull((it.item as LinkAnnotation.Clickable).linkInteractionListener).onClick(it.item) }

        assertEquals(
            listOf(
                FacetTarget.Mention("did:plc:aaaa00000000000000000"),
                FacetTarget.Mention("did:plc:bbbb00000000000000000"),
            ),
            captured,
        )
    }

    // A tag routes to FacetTarget.Tag with the bare value and the text's author,
    // so the Hashtag screen can offer its "From @author" filter.
    @Test
    fun tagTapRoutesToTagTargetWithAuthor() {
        val text = "love #nubecita"
        val facets =
            persistentListOf(
                Facet(
                    features = listOf(FacetTag(tag = "nubecita")),
                    index = FacetByteSlice(byteStart = 5, byteEnd = 14),
                ),
            )
        var tapped: FacetTarget? = null
        val annotated =
            buildTappableBlueskyAnnotatedString(text, facets, linkStyle, authorHandle = "alice.bsky.social") { tapped = it }

        val range = annotated.getLinkAnnotations(0, annotated.length).single()
        assertEquals(5 to 14, range.start to range.end)
        val link = range.item as LinkAnnotation.Clickable
        requireNotNull(link.linkInteractionListener).onClick(link)

        assertEquals(FacetTarget.Tag("nubecita", "alice.bsky.social"), tapped)
        // Text is preserved verbatim.
        assertEquals(text, annotated.text)
    }

    // Some clients publish the facet value with its '#'; the target carries the bare tag.
    @Test
    fun tagValueWithLeadingHashIsNormalized() {
        val text = "love #nubecita"
        val facets =
            persistentListOf(
                Facet(
                    features = listOf(FacetTag(tag = "#nubecita")),
                    index = FacetByteSlice(byteStart = 5, byteEnd = 14),
                ),
            )
        var tapped: FacetTarget? = null
        val annotated = buildTappableBlueskyAnnotatedString(text, facets, linkStyle) { tapped = it }

        val link = annotated.getLinkAnnotations(0, annotated.length).single().item as LinkAnnotation.Clickable
        requireNotNull(link.linkInteractionListener).onClick(link)

        assertEquals(FacetTarget.Tag("nubecita", null), tapped)
    }

    // No facets → plain text, no links.
    @Test
    fun plainTextHasNoLinks() {
        val text = "just some words"
        val annotated = buildTappableBlueskyAnnotatedString(text, persistentListOf(), linkStyle) { }

        assertEquals(text, annotated.text)
        assertTrue(annotated.getLinkAnnotations(0, annotated.length).isEmpty())
    }
}
