package net.kikin.nubecita.core.moderation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure parse/merge contract for the `app.bsky.actor` preferences array. These
 * functions are the read-modify-write core of [DefaultModerationPreferencesRepository];
 * the network plumbing around them is exercised by [ModerationPreferencesBoundaryTest].
 */
class ModerationPreferencesTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun parse(body: String): ModerationPrefs = parseModerationPrefs(json.decodeFromString(JsonArray.serializer(), body))

    private fun array(body: String): JsonArray = json.decodeFromString(JsonArray.serializer(), body)

    // --- parse ---

    @Test
    fun `empty array yields adult-off and no explicit visibilities`() {
        val prefs = parse("[]")
        assertFalse(prefs.adultContentEnabled)
        // Absent entries resolve via DEFAULT.
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
        // Unset categories still fall back to DEFAULT.
        assertEquals(LabelVisibility.WARN, prefs.visibilityFor(ContentLabel.SEXUAL))
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
        // A label pref scoped to a specific labeler must NOT configure our global gate.
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
        // No managed entry touched.
        assertEquals(LabelVisibility.HIDE, prefs.visibilityFor(ContentLabel.PORN))
    }

    // --- merge ---

    @Test
    fun `merge preserves foreign entries and replaces managed ones`() {
        val original =
            array(
                """
                [
                  {"${'$'}type":"app.bsky.actor.defs#savedFeedsPrefV2","items":[{"id":"x"}]},
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

        val byType = merged.map { (it as JsonObject)["\$type"]!!.jsonPrimitive.content }

        // Foreign saved-feeds entry survives untouched.
        assertEquals(1, byType.count { it == "app.bsky.actor.defs#savedFeedsPrefV2" })
        // The labeler-scoped porn pref is preserved (we only own the global one).
        val labelerScoped =
            merged.map { it as JsonObject }.filter {
                it["\$type"]?.jsonPrimitive?.content == "app.bsky.actor.defs#contentLabelPref" &&
                    it["labelerDid"] != null
            }
        assertEquals(1, labelerScoped.size)
        assertEquals("did:plc:keepme", labelerScoped.single()["labelerDid"]!!.jsonPrimitive.content)

        // Exactly one global adult-content pref, now enabled.
        val adult = merged.map { it as JsonObject }.single { it["\$type"]?.jsonPrimitive?.content == "app.bsky.actor.defs#adultContentPref" }
        assertTrue(adult["enabled"]!!.jsonPrimitive.booleanOrNull == true)

        // All four global content-label prefs are written exactly once each.
        val global =
            merged.map { it as JsonObject }.filter {
                it["\$type"]?.jsonPrimitive?.content == "app.bsky.actor.defs#contentLabelPref" &&
                    it["labelerDid"] == null
            }
        assertEquals(4, global.size)
        val pornGlobal = global.single { it["label"]!!.jsonPrimitive.content == "porn" }
        assertEquals("warn", pornGlobal["visibility"]!!.jsonPrimitive.content)
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
        val roundTripped = parseModerationPrefs(mergeModerationPrefs(JsonArray(emptyList()), prefs))
        assertEquals(prefs.adultContentEnabled, roundTripped.adultContentEnabled)
        ContentLabel.entries.forEach { category ->
            assertEquals(prefs.visibilityFor(category), roundTripped.visibilityFor(category), "round-trip $category")
        }
    }

    @Test
    fun `merge drops a pre-existing managed entry exactly once`() {
        // Two stray global porn prefs in the source array must both be removed
        // and replaced by the single canonical one.
        val original =
            array(
                """
                [
                  {"${'$'}type":"app.bsky.actor.defs#contentLabelPref","label":"porn","visibility":"hide"},
                  {"${'$'}type":"app.bsky.actor.defs#contentLabelPref","label":"porn","visibility":"warn"}
                ]
                """.trimIndent(),
            )
        val merged = mergeModerationPrefs(original, ModerationPrefs.DEFAULT)
        val pornGlobal =
            merged.map { it as JsonObject }.filter {
                it["\$type"]?.jsonPrimitive?.content == "app.bsky.actor.defs#contentLabelPref" &&
                    it["label"]?.jsonPrimitive?.content == "porn"
            }
        assertEquals(1, pornGlobal.size)
        assertNull(pornGlobal.single()["labelerDid"])
    }
}
