package net.kikin.nubecita.core.moderation

import io.github.kikin81.atproto.runtime.NoXrpcParams
import io.github.kikin81.atproto.runtime.UnitResponseSerializer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.posting.PostAudience
import net.kikin.nubecita.core.posting.ReplyAudience
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * The viewer's **default** post audience — who can reply / quote a new post —
 * as a single reactive source of truth. The composer pre-fills from [default]
 * and the picker's "save these options for next time" drives [setDefault].
 *
 * Backed by the synced AT Protocol `app.bsky.actor.defs#postInteractionSettingsPref`
 * preference (the same `app.bsky.actor.getPreferences` array the content-filter
 * prefs live in — which is why this repository co-locates with `:core:moderation`
 * even though the *domain* is posting-side; the wire plumbing is shared). There
 * is no Room / local copy: the synced preference is canonical.
 *
 * [default] starts at [PostAudience.DEFAULT] (anyone can reply, quotes allowed)
 * so any reader before the first [refresh] sees the wide-open default — the same
 * value the lexicon assumes when the preference is absent.
 */
interface PostAudienceDefaultRepository {
    /** Hot stream of the resolved default, seeded with [PostAudience.DEFAULT]. */
    val default: StateFlow<PostAudience>

    /** Re-read `app.bsky.actor.getPreferences` and publish to [default]. */
    suspend fun refresh()

    /**
     * Reset [default] back to [PostAudience.DEFAULT]. Called on sign-out so a
     * subsequent account never reads the previous account's default in the
     * window before its own [refresh] lands (the repo is an app-scoped singleton).
     */
    fun resetToDefault()

    /** Persist [audience] as the synced default (read-modify-write of the array). */
    suspend fun setDefault(audience: PostAudience)
}

/**
 * Default implementation backed by the AT Protocol preferences array.
 *
 * The SDK mis-models the `preferences` field as a `JsonObject` rather than an
 * array (TODO(atproto-kotlin#132): drop this raw-JSON path once codegen emits
 * `List<union>` for it), so — exactly like [DefaultModerationPreferencesRepository] —
 * we decode the raw response object, operate on the `preferences` array
 * ourselves, and write back a hand-built `{"preferences":[…]}` body. The read-modify-write
 * touches the WHOLE array so foreign entries (saved feeds, content-label prefs,
 * …) are preserved; only the single `postInteractionSettingsPref` entry we own
 * is replaced.
 *
 * Writes are **optimistic**: [setDefault] publishes the new value immediately,
 * then reconciles against the live array under [writeMutex]; a failure reverts
 * to the previous value (unless a newer write already superseded it). Mirrors
 * the moderation repo's `update {}`.
 */
@Singleton
internal class DefaultPostAudienceDefaultRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
    ) : PostAudienceDefaultRepository {
        private val writeMutex = Mutex()

        private val _default = MutableStateFlow(PostAudience.DEFAULT)
        override val default: StateFlow<PostAudience> = _default.asStateFlow()

        override suspend fun refresh() {
            val parsed = parsePostAudienceDefault(fetchPreferencesArray())
            writeMutex.withLock { _default.value = parsed }
        }

        override fun resetToDefault() {
            _default.value = PostAudience.DEFAULT
        }

        override suspend fun setDefault(audience: PostAudience) {
            // Optimistic: the publish below is the single publish point — the
            // default is one atomic value (unlike the moderation repo, which
            // re-reads and re-applies independent toggles under the lock), so
            // concurrent calls resolve last-write-wins and we don't republish
            // after the write (republishing would briefly roll a newer write
            // back to ours). The mutex only serializes the server read-modify-
            // write. On failure we revert ONLY if no newer write superseded us;
            // a rare double-failure can leave a stale optimistic value that the
            // next refresh reconciles. Cancellation rethrows without reverting.
            val previous = _default.value
            _default.value = audience
            try {
                writeMutex.withLock {
                    val original = fetchPreferencesArray()
                    writePreferencesArray(mergePostAudienceDefault(original, audience))
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                // Revert only if no newer write has superseded our optimistic value.
                if (_default.value == audience) _default.value = previous
                throw throwable
            }
        }

        private suspend fun fetchPreferencesArray(): JsonArray {
            val response =
                xrpcClientProvider.authenticated().query(
                    GET_PREFERENCES_NSID,
                    NoXrpcParams,
                    NoXrpcParams.serializer(),
                    JsonObject.serializer(),
                )
            return response["preferences"] as? JsonArray ?: JsonArray(emptyList())
        }

        private suspend fun writePreferencesArray(preferences: JsonArray) {
            val body = buildJsonObject { put("preferences", preferences) }
            xrpcClientProvider.authenticated().procedure(
                PUT_PREFERENCES_NSID,
                NoXrpcParams,
                NoXrpcParams.serializer(),
                body,
                JsonObject.serializer(),
                UnitResponseSerializer,
            )
        }
    }

/**
 * Pure projection of the raw `preferences` array into the default [PostAudience].
 *
 * Reads the single global `postInteractionSettingsPref` entry. Per the lexicon:
 * `threadgateAllowRules` *absent* → anyone replies ([ReplyAudience.Everyone]);
 * *empty* → no one ([ReplyAudience.Nobody]); *non-empty* → the listed groups
 * ([ReplyAudience.Combination]). `postgateEmbeddingRules` *absent or empty* →
 * quotes allowed; containing the disable rule → quotes off. An absent entry
 * falls back to [PostAudience.DEFAULT]. Unknown threadgate rules (e.g. the
 * deferred list rule) are ignored — only the three managed groups are read, so
 * a list-scoped default round-trips lossily until lists are supported. No I/O.
 */
internal fun parsePostAudienceDefault(preferences: JsonArray): PostAudience {
    val entry =
        preferences
            .filterIsInstance<JsonObject>()
            .firstOrNull { it.typeTag() == POST_INTERACTION_PREF_TYPE }
            ?: return PostAudience.DEFAULT

    val reply =
        when (val rules = entry["threadgateAllowRules"]) {
            null -> ReplyAudience.Everyone
            is JsonArray ->
                if (rules.isEmpty()) {
                    ReplyAudience.Nobody
                } else {
                    val types = rules.ruleTypes()
                    ReplyAudience.Combination(
                        followers = THREADGATE_FOLLOWER_RULE in types,
                        following = THREADGATE_FOLLOWING_RULE in types,
                        mentioned = THREADGATE_MENTION_RULE in types,
                    )
                }
            // A malformed/unknown shape (JsonNull, primitive) is treated as
            // absent → anyone. This is a default for *new* posts, not an
            // enforcement gate, so falling back to the open default is safe.
            else -> ReplyAudience.Everyone
        }

    val allowQuotes = POSTGATE_DISABLE_RULE !in (entry["postgateEmbeddingRules"] as? JsonArray).orEmptyRuleTypes()

    return PostAudience(reply = reply, allowQuotes = allowQuotes)
}

/**
 * Pure merge: produce a new `preferences` array that drops the existing
 * `postInteractionSettingsPref` entry from [original] (preserving every other
 * entry in place) and appends a fresh one reflecting [audience]. Following the
 * lexicon, [ReplyAudience.Everyone] omits `threadgateAllowRules` (anyone) and
 * allowed quotes omit `postgateEmbeddingRules` (anyone) — never written as empty
 * arrays, which would mean "no one". No I/O.
 */
internal fun mergePostAudienceDefault(
    original: JsonArray,
    audience: PostAudience,
): JsonArray {
    val preserved =
        original.filterNot { it is JsonObject && it.typeTag() == POST_INTERACTION_PREF_TYPE }

    return buildJsonArray {
        preserved.forEach(::add)
        addJsonObject {
            put("\$type", POST_INTERACTION_PREF_TYPE)
            when (val reply = audience.reply) {
                ReplyAudience.Everyone -> Unit // omit threadgateAllowRules → anyone
                ReplyAudience.Nobody -> putJsonArray("threadgateAllowRules") {} // empty → no one
                is ReplyAudience.Combination ->
                    // An all-false combination yields an empty array (== Nobody);
                    // the picker prevents it, and that read-back is the correct
                    // interpretation, so we don't special-case it here.
                    putJsonArray("threadgateAllowRules") {
                        if (reply.followers) addRule(THREADGATE_FOLLOWER_RULE)
                        if (reply.following) addRule(THREADGATE_FOLLOWING_RULE)
                        if (reply.mentioned) addRule(THREADGATE_MENTION_RULE)
                    }
            }
            if (!audience.allowQuotes) {
                putJsonArray("postgateEmbeddingRules") { addRule(POSTGATE_DISABLE_RULE) }
            }
        }
    }
}

// Safe `$type` read: `as?` (not `jsonPrimitive`, which throws) so a malformed
// or foreign entry whose `$type` is not a string can't crash parsing of the
// shared multi-writer preferences array.
private fun JsonObject.typeTag(): String? = (this["\$type"] as? JsonPrimitive)?.content

private fun JsonArray.ruleTypes(): Set<String> = filterIsInstance<JsonObject>().mapNotNull { it.typeTag() }.toSet()

private fun JsonArray?.orEmptyRuleTypes(): Set<String> = this?.ruleTypes().orEmpty()

private fun kotlinx.serialization.json.JsonArrayBuilder.addRule(type: String) {
    addJsonObject { put("\$type", type) }
}

private const val GET_PREFERENCES_NSID = "app.bsky.actor.getPreferences"
private const val PUT_PREFERENCES_NSID = "app.bsky.actor.putPreferences"
private const val POST_INTERACTION_PREF_TYPE = "app.bsky.actor.defs#postInteractionSettingsPref"
private const val THREADGATE_MENTION_RULE = "app.bsky.feed.threadgate#mentionRule"
private const val THREADGATE_FOLLOWING_RULE = "app.bsky.feed.threadgate#followingRule"
private const val THREADGATE_FOLLOWER_RULE = "app.bsky.feed.threadgate#followerRule"
private const val POSTGATE_DISABLE_RULE = "app.bsky.feed.postgate#disableRule"
