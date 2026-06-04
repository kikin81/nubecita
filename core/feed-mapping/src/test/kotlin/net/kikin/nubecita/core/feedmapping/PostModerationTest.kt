package net.kikin.nubecita.core.feedmapping

import io.github.kikin81.atproto.com.atproto.label.Label
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import net.kikin.nubecita.core.moderation.ContentLabel
import net.kikin.nubecita.core.moderation.LabelVisibility
import net.kikin.nubecita.core.moderation.MediaModerationDecision
import net.kikin.nubecita.core.moderation.ModerationPrefs
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.ContentWarningCategory
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.ImageUi
import net.kikin.nubecita.data.models.MediaContentWarning
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

/**
 * Unit tests for the moderation bridge ([applyModeration] and the label /
 * category / decision projections). The Play-critical resolver itself lives —
 * and is exhaustively tested — in `:core:moderation`; here we pin the wire→UI
 * threading and the feed-list drop-vs-cover behavior.
 */
internal class PostModerationTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    private val viewer = "did:plc:viewer"
    private val author = "did:plc:author"
    private val adultOff = ModerationPrefs.DEFAULT
    private val adultOn = ModerationPrefs.DEFAULT.copy(adultContentEnabled = true)

    private fun labels(json5: String): List<Label> = json.decodeFromString(ListSerializer(Label.serializer()), json5)

    private fun labelJson(
        value: String,
        src: String = "did:plc:labeler",
        neg: Boolean = false,
    ) = """{"src":"$src","uri":"at://$author/app.bsky.feed.post/p1","val":"$value","cts":"2026-01-01T00:00:00Z","neg":$neg}"""

    private fun post(
        authorDid: String = author,
        embed: EmbedUi = EmbedUi.Images(persistentListOf(ImageUi("https://cdn/f.jpg", null, null, null))),
    ): PostUi =
        PostUi(
            id = "at://$authorDid/app.bsky.feed.post/p1",
            cid = "bafycidfake",
            author = AuthorUi(did = authorDid, handle = "a.bsky.social", displayName = "A", avatarUrl = null),
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            text = "hi",
            facets = persistentListOf(),
            embed = embed,
            stats = PostStatsUi(),
            viewer = ViewerStateUi(),
            repostedBy = null,
        )

    private fun PostUi.imageWarning() = (embed as EmbedUi.Images).contentWarning

    // --- label / category / decision projections ---

    @Test
    fun `toModerationLabel maps value src and negation`() {
        val l = labels("[${labelJson("porn", src = "did:plc:x", neg = true)}]").single().toModerationLabel()
        assertEquals("porn", l.value)
        assertEquals("did:plc:x", l.src)
        assertTrue(l.isNegated)
    }

    @Test
    fun `null and empty wire labels become an empty list`() {
        assertTrue((null as List<Label>?).toModerationLabels().isEmpty())
        assertTrue(emptyList<Label>().toModerationLabels().isEmpty())
    }

    @Test
    fun `every content label maps to a warning category`() {
        assertEquals(ContentWarningCategory.ADULT_CONTENT, ContentLabel.PORN.toContentWarningCategory())
        assertEquals(ContentWarningCategory.SEXUALLY_SUGGESTIVE, ContentLabel.SEXUAL.toContentWarningCategory())
        assertEquals(ContentWarningCategory.GRAPHIC_MEDIA, ContentLabel.GRAPHIC_MEDIA.toContentWarningCategory())
        assertEquals(ContentWarningCategory.NON_SEXUAL_NUDITY, ContentLabel.NUDITY.toContentWarningCategory())
    }

    @Test
    fun `decision projects to the right cover`() {
        assertNull(MediaModerationDecision.Show.toMediaContentWarning())
        assertEquals(
            MediaContentWarning(ContentWarningCategory.SEXUALLY_SUGGESTIVE, overridable = true),
            MediaModerationDecision.Warn(ContentLabel.SEXUAL).toMediaContentWarning(),
        )
        assertEquals(
            MediaContentWarning(ContentWarningCategory.ADULT_CONTENT, overridable = false),
            MediaModerationDecision.Filter(ContentLabel.PORN, overridable = false).toMediaContentWarning(),
        )
    }

    // --- applyModeration ---

    @Test
    fun `adult-off porn is dropped from a feed list`() {
        val result = post().applyModeration(labels("[${labelJson("porn")}]"), viewer, adultOff, dropFiltered = true)
        assertNull(result)
    }

    @Test
    fun `adult-off porn is covered (non-overridable) in post-detail`() {
        val result = post().applyModeration(labels("[${labelJson("porn")}]"), viewer, adultOff, dropFiltered = false)
        val warning = result!!.imageWarning()!!
        assertEquals(ContentWarningCategory.ADULT_CONTENT, warning.category)
        assertFalse(warning.overridable)
    }

    @Test
    fun `warn label covers the media with an overridable warning`() {
        val result = post().applyModeration(labels("[${labelJson("sexual")}]"), viewer, adultOn, dropFiltered = true)
        val warning = result!!.imageWarning()!!
        assertEquals(ContentWarningCategory.SEXUALLY_SUGGESTIVE, warning.category)
        assertTrue(warning.overridable)
    }

    @Test
    fun `a show outcome returns the same post instance unchanged`() {
        // nudity defaults to show; the post passes through untouched (no copy).
        val original = post()
        val result = original.applyModeration(labels("[${labelJson("nudity")}]"), viewer, adultOff, dropFiltered = true)
        assertSame(original, result)
        assertNull(result!!.imageWarning())
    }

    @Test
    fun `null and empty labels take the fast path and return the post unchanged`() {
        val original = post()
        assertSame(original, original.applyModeration(null, viewer, adultOff, dropFiltered = true))
        assertSame(original, original.applyModeration(emptyList(), viewer, adultOff, dropFiltered = true))
    }

    @Test
    fun `the viewer's own porn post is never filtered or covered`() {
        val mine = post(authorDid = viewer)
        val result = mine.applyModeration(labels("[${labelJson("porn", src = viewer)}]"), viewer, adultOff, dropFiltered = true)
        assertSame(mine, result)
        assertNull(result!!.imageWarning())
    }

    @Test
    fun `a per-category hide is overridable, unlike the forced adult-gate hide`() {
        // User chose to hide graphic-media while adult content is enabled — a
        // soft hide, revealable in post-detail (overridable = true).
        val prefs = adultOn.copy(visibilities = adultOn.visibilities + (ContentLabel.GRAPHIC_MEDIA to LabelVisibility.HIDE))
        val result = post().applyModeration(labels("[${labelJson("graphic-media")}]"), viewer, prefs, dropFiltered = false)
        assertTrue(result!!.imageWarning()!!.overridable)
    }
}
