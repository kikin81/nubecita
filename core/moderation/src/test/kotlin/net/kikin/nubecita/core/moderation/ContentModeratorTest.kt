package net.kikin.nubecita.core.moderation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ContentModeratorTest {
    private val adultOn = ModerationPrefs.DEFAULT.copy(adultContentEnabled = true)
    private val adultOff = ModerationPrefs.DEFAULT // default: adult disabled

    private fun label(
        value: String,
        src: String = "did:plc:labeler",
        negated: Boolean = false,
    ) = ModerationLabel(value = value, src = src, isNegated = negated)

    private fun decide(
        labels: List<ModerationLabel>,
        prefs: ModerationPrefs,
        authorDid: String? = "did:plc:author",
        viewerDid: String? = "did:plc:viewer",
    ) = ContentModerator.decide(labels, authorDid, viewerDid, prefs)

    // --- adult gate OFF: the three adult labels are force-hidden, no override ---

    @Test
    fun `adult disabled force-hides porn with no override`() {
        val d = decide(listOf(label("porn")), adultOff)
        assertTrue(d is MediaModerationDecision.Filter)
        d as MediaModerationDecision.Filter
        assertEquals(ContentLabel.PORN, d.category)
        assertFalse(d.overridable)
    }

    @Test
    fun `adult disabled force-hides sexual and graphic-media with no override`() {
        for (value in listOf("sexual", "graphic-media")) {
            val d = decide(listOf(label(value)), adultOff)
            assertTrue(d is MediaModerationDecision.Filter, "expected Filter for $value")
            assertFalse((d as MediaModerationDecision.Filter).overridable, "expected noOverride for $value")
        }
    }

    @Test
    fun `non-sexual nudity is NOT gated by the adult switch`() {
        // Even with adult disabled, nudity follows its own default (show).
        assertEquals(MediaModerationDecision.Show, decide(listOf(label("nudity")), adultOff))
    }

    // --- adult gate ON: Bluesky defaults apply ---

    @Test
    fun `adult enabled applies category defaults`() {
        // porn=hide (overridable, user-chosen not forced), sexual/graphic=warn, nudity=show
        val porn = decide(listOf(label("porn")), adultOn)
        assertTrue(porn is MediaModerationDecision.Filter)
        assertTrue((porn as MediaModerationDecision.Filter).overridable)

        assertEquals(MediaModerationDecision.Warn(ContentLabel.SEXUAL), decide(listOf(label("sexual")), adultOn))
        assertEquals(MediaModerationDecision.Warn(ContentLabel.GRAPHIC_MEDIA), decide(listOf(label("graphic-media")), adultOn))
        assertEquals(MediaModerationDecision.Show, decide(listOf(label("nudity")), adultOn))
    }

    @Test
    fun `a per-category override is honored when adult enabled`() {
        val prefs = adultOn.copy(visibilities = adultOn.visibilities + (ContentLabel.PORN to LabelVisibility.WARN))
        assertEquals(MediaModerationDecision.Warn(ContentLabel.PORN), decide(listOf(label("porn")), prefs))
    }

    // --- aggregation: strongest wins ---

    @Test
    fun `strongest visibility wins across multiple labels`() {
        // warn + show -> warn
        assertEquals(
            MediaModerationDecision.Warn(ContentLabel.SEXUAL),
            decide(listOf(label("nudity"), label("sexual")), adultOn),
        )
        // hide + warn -> filter
        val d = decide(listOf(label("sexual"), label("porn")), adultOn)
        assertTrue(d is MediaModerationDecision.Filter)
        assertEquals(ContentLabel.PORN, (d as MediaModerationDecision.Filter).category)
    }

    // --- exemptions / edge cases ---

    @Test
    fun `the viewer's own content is never moderated`() {
        val d = decide(listOf(label("porn")), adultOff, authorDid = "did:plc:me", viewerDid = "did:plc:me")
        assertEquals(MediaModerationDecision.Show, d)
    }

    @Test
    fun `a negation retracts a prior label`() {
        val d = decide(listOf(label("porn"), label("porn", negated = true)), adultOff)
        assertEquals(MediaModerationDecision.Show, d)
    }

    @Test
    fun `unknown labels and empty labels are shown`() {
        assertEquals(MediaModerationDecision.Show, decide(listOf(label("!some-system-label")), adultOff))
        assertEquals(MediaModerationDecision.Show, decide(emptyList(), adultOff))
    }

    @Test
    fun `self-labels are honored`() {
        // author self-labels their own post porn; a *different* viewer with adult off -> filtered.
        val d =
            decide(
                listOf(label("porn", src = "did:plc:author")),
                adultOff,
                authorDid = "did:plc:author",
                viewerDid = "did:plc:viewer",
            )
        assertTrue(d is MediaModerationDecision.Filter)
    }
}
