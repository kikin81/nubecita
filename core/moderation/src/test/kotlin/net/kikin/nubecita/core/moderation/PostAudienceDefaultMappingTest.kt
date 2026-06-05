package net.kikin.nubecita.core.moderation

import io.github.kikin81.atproto.app.bsky.actor.GetPreferencesResponse
import io.github.kikin81.atproto.app.bsky.actor.GetPreferencesResponsePreferencesUnion
import io.github.kikin81.atproto.app.bsky.actor.PostInteractionSettingsPref
import io.github.kikin81.atproto.app.bsky.actor.PutPreferencesRequestPreferencesUnion
import io.github.kikin81.atproto.app.bsky.actor.SavedFeedsPrefV2
import io.github.kikin81.atproto.app.bsky.feed.PostgateDisableRule
import io.github.kikin81.atproto.app.bsky.feed.ThreadgateFollowerRule
import io.github.kikin81.atproto.app.bsky.feed.ThreadgateFollowingRule
import io.github.kikin81.atproto.app.bsky.feed.ThreadgateMentionRule
import kotlinx.serialization.json.Json
import net.kikin.nubecita.core.posting.PostAudience
import net.kikin.nubecita.core.posting.ReplyAudience
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure mapping between the typed `postInteractionSettingsPref` entry and
 * [PostAudience]. Fixtures are decoded through the SDK serializer (so they
 * exercise the real wire shape) into the typed `preferences` list. The wire
 * semantics match the SDK's `PostInteractionSettingsPref`: `threadgateAllowRules`
 * *absent* = anyone, *empty* = no one, *non-empty* = the listed groups;
 * `postgateEmbeddingRules` *absent or empty* = anyone can quote.
 */
internal class PostAudienceDefaultMappingTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun prefsOf(body: String): List<GetPreferencesResponsePreferencesUnion> = json.decodeFromString(GetPreferencesResponse.serializer(), body).preferences

    private fun List<PutPreferencesRequestPreferencesUnion>.postInteraction(): PostInteractionSettingsPref = filterIsInstance<PostInteractionSettingsPref>().single()

    // ---- parse ----

    @Test
    fun parse_noEntry_isDefault() {
        assertEquals(PostAudience.DEFAULT, parsePostAudienceDefault(prefsOf("""{"preferences":[]}""")))
    }

    @Test
    fun parse_entryWithBothFieldsAbsent_isEveryoneQuotesOn() {
        val audience =
            parsePostAudienceDefault(
                prefsOf("""{"preferences":[{"${'$'}type":"app.bsky.actor.defs#postInteractionSettingsPref"}]}"""),
            )
        assertEquals(ReplyAudience.Everyone, audience.reply)
        assertTrue(audience.allowQuotes)
    }

    @Test
    fun parse_emptyThreadgateAllow_isNobody() {
        val prefs =
            prefsOf(
                """{"preferences":[{"${'$'}type":"app.bsky.actor.defs#postInteractionSettingsPref","threadgateAllowRules":[]}]}""",
            )
        assertEquals(ReplyAudience.Nobody, parsePostAudienceDefault(prefs).reply)
    }

    @Test
    fun parse_nonEmptyThreadgateAllow_isCombinationOfPresentRules() {
        val prefs =
            prefsOf(
                """
                {"preferences":[{"${'$'}type":"app.bsky.actor.defs#postInteractionSettingsPref","threadgateAllowRules":[
                  {"${'$'}type":"app.bsky.feed.threadgate#followerRule"},
                  {"${'$'}type":"app.bsky.feed.threadgate#mentionRule"}]}]}
                """.trimIndent(),
            )
        assertEquals(
            ReplyAudience.Combination(followers = true, following = false, mentioned = true),
            parsePostAudienceDefault(prefs).reply,
        )
    }

    @Test
    fun parse_unknownThreadgateRule_isDiscardedKeepingKnownGroups() {
        // A list rule (deferred — not in our model) alongside known rules is
        // dropped; the known groups still resolve. Documents the lossy read.
        val prefs =
            prefsOf(
                """
                {"preferences":[{"${'$'}type":"app.bsky.actor.defs#postInteractionSettingsPref","threadgateAllowRules":[
                  {"${'$'}type":"app.bsky.feed.threadgate#followerRule"},
                  {"${'$'}type":"app.bsky.feed.threadgate#listRule","list":"at://did:plc:x/app.bsky.graph.list/a"},
                  {"${'$'}type":"app.bsky.feed.threadgate#mentionRule"}]}]}
                """.trimIndent(),
            )
        assertEquals(
            ReplyAudience.Combination(followers = true, following = false, mentioned = true),
            parsePostAudienceDefault(prefs).reply,
        )
    }

    @Test
    fun parse_postgateDisableRule_disallowsQuotes() {
        val prefs =
            prefsOf(
                """
                {"preferences":[{"${'$'}type":"app.bsky.actor.defs#postInteractionSettingsPref","postgateEmbeddingRules":[
                  {"${'$'}type":"app.bsky.feed.postgate#disableRule"}]}]}
                """.trimIndent(),
            )
        assertFalse(parsePostAudienceDefault(prefs).allowQuotes)
    }

    @Test
    fun parse_emptyPostgateRules_allowsQuotes() {
        val prefs =
            prefsOf(
                """{"preferences":[{"${'$'}type":"app.bsky.actor.defs#postInteractionSettingsPref","postgateEmbeddingRules":[]}]}""",
            )
        assertTrue(parsePostAudienceDefault(prefs).allowQuotes)
    }

    // ---- merge ----

    @Test
    fun merge_everyoneQuotesOn_omitsBothFields() {
        val entry = mergePostAudienceDefault(emptyList(), PostAudience.DEFAULT).postInteraction()
        assertNull(entry.threadgateAllowRules, "Everyone must omit threadgateAllowRules (anyone)")
        assertNull(entry.postgateEmbeddingRules, "quotes-on must omit postgateEmbeddingRules")
    }

    @Test
    fun merge_nobody_writesEmptyThreadgateAllow() {
        val entry =
            mergePostAudienceDefault(emptyList(), PostAudience(ReplyAudience.Nobody, allowQuotes = true)).postInteraction()
        assertTrue(entry.threadgateAllowRules?.isEmpty() == true, "Nobody == empty threadgateAllowRules")
    }

    @Test
    fun merge_combination_writesOnlyCheckedRules() {
        val audience =
            PostAudience(
                ReplyAudience.Combination(followers = true, following = false, mentioned = true),
                allowQuotes = false,
            )
        val entry = mergePostAudienceDefault(emptyList(), audience).postInteraction()
        val threadgate = entry.threadgateAllowRules!!
        assertTrue(threadgate.any { it is ThreadgateFollowerRule })
        assertTrue(threadgate.any { it is ThreadgateMentionRule })
        assertFalse(threadgate.any { it is ThreadgateFollowingRule })
        assertTrue(entry.postgateEmbeddingRules!!.any { it is PostgateDisableRule })
    }

    @Test
    fun merge_preservesForeignEntries() {
        val original =
            prefsOf(
                """
                {"preferences":[
                  {"${'$'}type":"app.bsky.actor.defs#savedFeedsPrefV2","items":[]},
                  {"${'$'}type":"app.bsky.actor.defs#postInteractionSettingsPref",
                   "threadgateAllowRules":[{"${'$'}type":"app.bsky.feed.threadgate#followerRule"}]}]}
                """.trimIndent(),
            )
        val merged = mergePostAudienceDefault(original, PostAudience.DEFAULT)
        // Foreign saved-feeds entry preserved; exactly one (replaced) post-interaction entry.
        assertTrue(merged.any { it is SavedFeedsPrefV2 }, "foreign entry preserved")
        assertEquals(1, merged.count { it is PostInteractionSettingsPref })
    }

    @Test
    fun roundTrip_isStableAcrossVariants() {
        listOf(
            PostAudience.DEFAULT,
            PostAudience(ReplyAudience.Everyone, allowQuotes = false),
            PostAudience(ReplyAudience.Nobody, allowQuotes = true),
            PostAudience(ReplyAudience.Nobody, allowQuotes = false),
            PostAudience(ReplyAudience.Combination(true, true, false), allowQuotes = true),
            PostAudience(ReplyAudience.Combination(false, false, true), allowQuotes = false),
        ).forEach { audience ->
            // The merged PostInteractionSettingsPref implements both unions, so it
            // is also a GET-union member parse can read back.
            val asGet =
                mergePostAudienceDefault(emptyList(), audience)
                    .filterIsInstance<GetPreferencesResponsePreferencesUnion>()
            assertEquals(audience, parsePostAudienceDefault(asGet))
        }
    }
}
