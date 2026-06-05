package net.kikin.nubecita.core.moderation

import io.github.kikin81.atproto.app.bsky.actor.AdultContentPref
import io.github.kikin81.atproto.app.bsky.actor.ContentLabelPref
import io.github.kikin81.atproto.app.bsky.actor.GetPreferencesResponse
import io.github.kikin81.atproto.app.bsky.actor.GetPreferencesResponsePreferencesUnion
import io.github.kikin81.atproto.app.bsky.actor.PutPreferencesRequestPreferencesUnion
import io.github.kikin81.atproto.app.bsky.actor.SavedFeedsPrefV2
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure parse/merge contract for the typed `app.bsky.actor` preferences list.
 * These functions are the read-modify-write core of
 * [DefaultModerationPreferencesRepository]; the network plumbing around them is
 * exercised by [ModerationPreferencesBoundaryTest]. Fixtures are decoded through
 * the SDK serializer so they exercise the real typed-union shape.
 */
class ModerationPreferencesTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun prefsOf(arrayBody: String): List<GetPreferencesResponsePreferencesUnion> = json.decodeFromString(GetPreferencesResponse.serializer(), """{"preferences":$arrayBody}""").preferences

    private fun parse(arrayBody: String): ModerationPrefs = parseModerationPrefs(prefsOf(arrayBody))

    private fun List<PutPreferencesRequestPreferencesUnion>.globalLabels(): List<ContentLabelPref> = filterIsInstance<ContentLabelPref>().filter { it.labelerDid == null }

    // --- parse ---

    @Test
    fun `empty array yields adult-off and no explicit visibilities`() {
        val prefs = parse("[]")
        assertFalse(prefs.adultContentEnabled)
        assertEquals(LabelVisibility.HIDE, prefs.visibilityFor(ContentLabel.PORN))
        assertEquals(LabelVisibility.SHOW, prefs.visibilityFor(ContentLabel.NUDITY))
    }

    @Test
    fun `parses adult gate and global content-label prefs`() {
        val prefs =
            parse(
                """
                [
                  {"${'$'}type":"app.bsky.actor.defs#adultContentPref","enabled":true},
                  {"${'$'}type":"app.bsky.actor.defs#contentLabelPref","label":"porn","visibility":"warn"},
                  {"${'$'}type":"app.bsky.actor.defs#contentLabelPref","label":"nudity","visibility":"hide"}
                ]
                """.trimIndent(),
            )
        assertTrue(prefs.adultContentEnabled)
        assertEquals(LabelVisibility.WARN, prefs.visibilityFor(ContentLabel.PORN))
        assertEquals(LabelVisibility.HIDE, prefs.visibilityFor(ContentLabel.NUDITY))
        assertEquals(LabelVisibility.WARN, prefs.visibilityFor(ContentLabel.SEXUAL))
    }

    @Test
    fun `duplicate adult gates resolve last-wins`() {
        // The gate is a server singleton; if duplicates ever appear, the last
        // entry wins (matching the prior loop-assignment behavior).
        val prefs =
            parse(
                """
                [
                  {"${'$'}type":"app.bsky.actor.defs#adultContentPref","enabled":false},
                  {"${'$'}type":"app.bsky.actor.defs#adultContentPref","enabled":true}
                ]
                """.trimIndent(),
            )
        assertTrue(prefs.adultContentEnabled)
    }

    @Test
    fun `ignore wire value maps to show`() {
        val prefs =
            parse(
                """[{"${'$'}type":"app.bsky.actor.defs#contentLabelPref","label":"graphic-media","visibility":"ignore"}]""",
            )
        assertEquals(LabelVisibility.SHOW, prefs.visibilityFor(ContentLabel.GRAPHIC_MEDIA))
    }

    @Test
    fun `labeler-scoped content prefs are ignored`() {
        val prefs =
            parse(
                """
                [{"${'$'}type":"app.bsky.actor.defs#contentLabelPref","label":"porn",
                  "labelerDid":"did:plc:somelabeler","visibility":"warn"}]
                """.trimIndent(),
            )
        // Falls back to DEFAULT (hide), not the labeler-scoped warn.
        assertEquals(LabelVisibility.HIDE, prefs.visibilityFor(ContentLabel.PORN))
    }

    @Test
    fun `unknown labels and preference kinds are skipped`() {
        val prefs =
            parse(
                """
                [
                  {"${'$'}type":"app.bsky.actor.defs#savedFeedsPrefV2","items":[]},
                  {"${'$'}type":"app.bsky.actor.defs#contentLabelPref","label":"spam","visibility":"hide"}
                ]
                """.trimIndent(),
            )
        assertFalse(prefs.adultContentEnabled)
        assertEquals(LabelVisibility.HIDE, prefs.visibilityFor(ContentLabel.PORN))
    }

    // --- merge ---

    @Test
    fun `merge preserves foreign entries (known and unknown) and replaces managed ones`() {
        val original =
            prefsOf(
                """
                [
                  {"${'$'}type":"app.bsky.actor.defs#savedFeedsPrefV2","items":[]},
                  {"${'$'}type":"app.bsky.actor.defs#someFuturePref","custom":"keep-me"},
                  {"${'$'}type":"app.bsky.actor.defs#adultContentPref","enabled":false},
                  {"${'$'}type":"app.bsky.actor.defs#contentLabelPref","label":"porn","visibility":"hide"},
                  {"${'$'}type":"app.bsky.actor.defs#contentLabelPref","label":"porn",
                   "labelerDid":"did:plc:keepme","visibility":"warn"}
                ]
                """.trimIndent(),
            )
        val merged =
            mergeModerationPrefs(
                original,
                ModerationPrefs(
                    adultContentEnabled = true,
                    visibilities = mapOf(ContentLabel.PORN to LabelVisibility.WARN),
                ),
            )

        // Foreign known entry (saved feeds) survives untouched.
        assertEquals(1, merged.count { it is SavedFeedsPrefV2 })
        // Foreign UNKNOWN entry survives via the Unknown(type, raw) remap.
        val unknown =
            merged
                .filterIsInstance<PutPreferencesRequestPreferencesUnion.Unknown>()
                .single { it.type == "app.bsky.actor.defs#someFuturePref" }
        assertEquals("keep-me", unknown.raw["custom"]?.toString()?.trim('"'))
        // The labeler-scoped porn pref is preserved (we only own the global one).
        val labelerScoped = merged.filterIsInstance<ContentLabelPref>().filter { it.labelerDid != null }
        assertEquals(1, labelerScoped.size)
        // Exactly one global adult-content pref, now enabled.
        assertTrue(merged.filterIsInstance<AdultContentPref>().single().enabled)
        // All four global content-label prefs written exactly once each; porn = warn.
        assertEquals(4, merged.globalLabels().size)
        assertEquals("warn", merged.globalLabels().single { it.label == "porn" }.visibility)
    }

    @Test
    fun `merge then parse round-trips the resolved prefs`() {
        val prefs =
            ModerationPrefs(
                adultContentEnabled = true,
                visibilities =
                    mapOf(
                        ContentLabel.PORN to LabelVisibility.SHOW,
                        ContentLabel.SEXUAL to LabelVisibility.HIDE,
                        ContentLabel.GRAPHIC_MEDIA to LabelVisibility.WARN,
                        ContentLabel.NUDITY to LabelVisibility.WARN,
                    ),
            )
        // The merged prefs implement both unions, so they are also GET-union
        // members parse can read back.
        val asGet = mergeModerationPrefs(emptyList(), prefs).filterIsInstance<GetPreferencesResponsePreferencesUnion>()
        val roundTripped = parseModerationPrefs(asGet)
        assertEquals(prefs.adultContentEnabled, roundTripped.adultContentEnabled)
        ContentLabel.entries.forEach { category ->
            assertEquals(prefs.visibilityFor(category), roundTripped.visibilityFor(category), "round-trip $category")
        }
    }

    @Test
    fun `merge drops a pre-existing managed entry exactly once`() {
        // Two stray global porn prefs in the source must both be removed and
        // replaced by the single canonical one.
        val original =
            prefsOf(
                """
                [
                  {"${'$'}type":"app.bsky.actor.defs#contentLabelPref","label":"porn","visibility":"hide"},
                  {"${'$'}type":"app.bsky.actor.defs#contentLabelPref","label":"porn","visibility":"warn"}
                ]
                """.trimIndent(),
            )
        val merged = mergeModerationPrefs(original, ModerationPrefs.DEFAULT)
        assertEquals(1, merged.globalLabels().count { it.label == "porn" })
    }
}
