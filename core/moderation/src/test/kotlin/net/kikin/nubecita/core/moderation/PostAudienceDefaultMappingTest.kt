package net.kikin.nubecita.core.moderation

import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import net.kikin.nubecita.core.posting.PostAudience
import net.kikin.nubecita.core.posting.ReplyAudience
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pure mapping between the raw `postInteractionSettingsPref` entry and
 * [PostAudience]. The wire semantics are exactly those documented on the SDK's
 * `PostInteractionSettingsPref`: for `threadgateAllowRules`, *absent* = anyone,
 * *empty* = no one, *non-empty* = the listed groups; for `postgateEmbeddingRules`,
 * *absent or empty* = anyone can quote.
 */
internal class PostAudienceDefaultMappingTest {
    private companion object {
        const val PREF = "app.bsky.actor.defs#postInteractionSettingsPref"
        const val MENTION = "app.bsky.feed.threadgate#mentionRule"
        const val FOLLOWING = "app.bsky.feed.threadgate#followingRule"
        const val FOLLOWER = "app.bsky.feed.threadgate#followerRule"
        const val DISABLE = "app.bsky.feed.postgate#disableRule"
        const val LIST = "app.bsky.feed.threadgate#listRule"
    }

    private fun ruleTypes(
        obj: kotlinx.serialization.json.JsonObject,
        field: String,
    ): Set<String> =
        (obj[field] as? kotlinx.serialization.json.JsonArray)
            ?.map { (it as kotlinx.serialization.json.JsonObject)["\$type"]!!.jsonPrimitive.content }
            ?.toSet()
            .orEmpty()

    // ---- parse ----

    @Test
    fun parse_noEntry_isDefault() {
        assertEquals(PostAudience.DEFAULT, parsePostAudienceDefault(buildJsonArray {}))
    }

    @Test
    fun parse_entryWithBothFieldsAbsent_isEveryoneQuotesOn() {
        val arr = buildJsonArray { addJsonObject { put("\$type", PREF) } }
        val audience = parsePostAudienceDefault(arr)
        assertEquals(ReplyAudience.Everyone, audience.reply)
        assertTrue(audience.allowQuotes)
    }

    @Test
    fun parse_emptyThreadgateAllow_isNobody() {
        val arr =
            buildJsonArray {
                addJsonObject {
                    put("\$type", PREF)
                    putJsonArray("threadgateAllowRules") {}
                }
            }
        assertEquals(ReplyAudience.Nobody, parsePostAudienceDefault(arr).reply)
    }

    @Test
    fun parse_nonEmptyThreadgateAllow_isCombinationOfPresentRules() {
        val arr =
            buildJsonArray {
                addJsonObject {
                    put("\$type", PREF)
                    putJsonArray("threadgateAllowRules") {
                        addJsonObject { put("\$type", FOLLOWER) }
                        addJsonObject { put("\$type", MENTION) }
                    }
                }
            }
        assertEquals(
            ReplyAudience.Combination(followers = true, following = false, mentioned = true),
            parsePostAudienceDefault(arr).reply,
        )
    }

    @Test
    fun parse_unknownThreadgateRule_isDiscardedKeepingKnownGroups() {
        // A list rule (deferred — not modelled) alongside known rules is dropped;
        // the known groups still resolve. Documents the intentional lossy read.
        val arr =
            buildJsonArray {
                addJsonObject {
                    put("\$type", PREF)
                    putJsonArray("threadgateAllowRules") {
                        addJsonObject { put("\$type", FOLLOWER) }
                        addJsonObject { put("\$type", LIST) }
                        addJsonObject { put("\$type", MENTION) }
                    }
                }
            }
        assertEquals(
            ReplyAudience.Combination(followers = true, following = false, mentioned = true),
            parsePostAudienceDefault(arr).reply,
        )
    }

    @Test
    fun parse_postgateDisableRule_disallowsQuotes() {
        val arr =
            buildJsonArray {
                addJsonObject {
                    put("\$type", PREF)
                    putJsonArray("postgateEmbeddingRules") { addJsonObject { put("\$type", DISABLE) } }
                }
            }
        assertFalse(parsePostAudienceDefault(arr).allowQuotes)
    }

    @Test
    fun parse_emptyPostgateRules_allowsQuotes() {
        // SDK: empty postgateEmbeddingRules == anyone can embed.
        val arr =
            buildJsonArray {
                addJsonObject {
                    put("\$type", PREF)
                    putJsonArray("postgateEmbeddingRules") {}
                }
            }
        assertTrue(parsePostAudienceDefault(arr).allowQuotes)
    }

    // ---- merge ----

    @Test
    fun merge_everyoneQuotesOn_omitsBothFields() {
        val out = mergePostAudienceDefault(buildJsonArray {}, PostAudience.DEFAULT)
        val entry =
            out.single { (it as kotlinx.serialization.json.JsonObject)["\$type"]!!.jsonPrimitive.content == PREF }
                as kotlinx.serialization.json.JsonObject
        assertNull(entry["threadgateAllowRules"], "Everyone must omit threadgateAllowRules (anyone)")
        assertNull(entry["postgateEmbeddingRules"], "quotes-on must omit postgateEmbeddingRules")
    }

    @Test
    fun merge_nobody_writesEmptyThreadgateAllow() {
        val out = mergePostAudienceDefault(buildJsonArray {}, PostAudience(ReplyAudience.Nobody, allowQuotes = true))
        val entry =
            out.single { (it as kotlinx.serialization.json.JsonObject)["\$type"]!!.jsonPrimitive.content == PREF }
                as kotlinx.serialization.json.JsonObject
        val rules = entry["threadgateAllowRules"] as kotlinx.serialization.json.JsonArray
        assertTrue(rules.isEmpty(), "Nobody == empty threadgateAllowRules")
    }

    @Test
    fun merge_combination_writesOnlyCheckedRules() {
        val audience =
            PostAudience(
                ReplyAudience.Combination(followers = true, following = false, mentioned = true),
                allowQuotes = false,
            )
        val out = mergePostAudienceDefault(buildJsonArray {}, audience)
        val entry =
            out.single { (it as kotlinx.serialization.json.JsonObject)["\$type"]!!.jsonPrimitive.content == PREF }
                as kotlinx.serialization.json.JsonObject
        assertEquals(setOf(FOLLOWER, MENTION), ruleTypes(entry, "threadgateAllowRules"))
        assertEquals(setOf(DISABLE), ruleTypes(entry, "postgateEmbeddingRules"))
    }

    @Test
    fun merge_preservesForeignEntries() {
        val original =
            buildJsonArray {
                addJsonObject {
                    put("\$type", "app.bsky.actor.defs#savedFeedsPrefV2")
                    putJsonArray("items") {}
                }
                // A stale post-interaction entry that must be replaced, not duplicated.
                addJsonObject {
                    put("\$type", PREF)
                    putJsonArray("threadgateAllowRules") { addJsonObject { put("\$type", FOLLOWER) } }
                }
            }
        val out = mergePostAudienceDefault(original, PostAudience.DEFAULT)
        val types = out.map { (it as kotlinx.serialization.json.JsonObject)["\$type"]!!.jsonPrimitive.content }
        assertTrue(types.contains("app.bsky.actor.defs#savedFeedsPrefV2"), "foreign entry preserved")
        assertEquals(1, types.count { it == PREF }, "exactly one post-interaction entry (replaced, not duplicated)")
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
            assertEquals(audience, parsePostAudienceDefault(mergePostAudienceDefault(buildJsonArray {}, audience)))
        }
    }
}
