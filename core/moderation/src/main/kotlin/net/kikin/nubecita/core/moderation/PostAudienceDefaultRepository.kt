package net.kikin.nubecita.core.moderation

import io.github.kikin81.atproto.app.bsky.actor.ActorService
import io.github.kikin81.atproto.app.bsky.actor.GetPreferencesRequest
import io.github.kikin81.atproto.app.bsky.actor.GetPreferencesResponsePreferencesUnion
import io.github.kikin81.atproto.app.bsky.actor.PostInteractionSettingsPref
import io.github.kikin81.atproto.app.bsky.actor.PostInteractionSettingsPrefPostgateEmbeddingRulesUnion
import io.github.kikin81.atproto.app.bsky.actor.PostInteractionSettingsPrefThreadgateAllowRulesUnion
import io.github.kikin81.atproto.app.bsky.actor.PutPreferencesRequest
import io.github.kikin81.atproto.app.bsky.actor.PutPreferencesRequestPreferencesUnion
import io.github.kikin81.atproto.app.bsky.feed.PostgateDisableRule
import io.github.kikin81.atproto.app.bsky.feed.ThreadgateFollowerRule
import io.github.kikin81.atproto.app.bsky.feed.ThreadgateFollowingRule
import io.github.kikin81.atproto.app.bsky.feed.ThreadgateMentionRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * Default implementation backed by the typed `app.bsky.actor.getPreferences` /
 * `putPreferences` array (the `preferences` field became a proper typed
 * `List<union>` in atproto-kotlin 9.2.0; see issue #132). The read-modify-write
 * touches the WHOLE array so foreign entries — saved feeds, content-label prefs,
 * unmodeled future kinds (carried as the union's `Unknown(type, raw)` member) —
 * are preserved; only the single `postInteractionSettingsPref` entry we own is
 * replaced.
 *
 * Writes are **optimistic**: [setDefault] publishes the new value immediately,
 * then reconciles against the live array under [writeMutex]; a failure reverts
 * to the previous value (unless a newer write already superseded it). Mirrors
 * the moderation repo's pattern.
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
            val parsed = parsePostAudienceDefault(fetchPreferences())
            writeMutex.withLock { _default.value = parsed }
        }

        override fun resetToDefault() {
            _default.value = PostAudience.DEFAULT
        }

        override suspend fun setDefault(audience: PostAudience) {
            // Optimistic: the publish below is the single publish point — the
            // default is one atomic value, so concurrent calls resolve
            // last-write-wins and we don't republish after the write. The mutex
            // only serializes the server read-modify-write. On failure we revert
            // ONLY if no newer write superseded us; a rare double-failure can
            // leave a stale optimistic value that the next refresh reconciles.
            // Cancellation rethrows without reverting.
            val previous = _default.value
            _default.value = audience
            try {
                writeMutex.withLock {
                    val original = fetchPreferences()
                    writePreferences(mergePostAudienceDefault(original, audience))
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                if (_default.value == audience) _default.value = previous
                throw throwable
            }
        }

        private suspend fun fetchPreferences(): List<GetPreferencesResponsePreferencesUnion> = ActorService(xrpcClientProvider.authenticated()).getPreferences(GetPreferencesRequest()).preferences

        private suspend fun writePreferences(preferences: List<PutPreferencesRequestPreferencesUnion>) {
            ActorService(xrpcClientProvider.authenticated()).putPreferences(PutPreferencesRequest(preferences))
        }
    }

/**
 * Pure projection of the typed `preferences` list into the default [PostAudience].
 *
 * Reads the single [PostInteractionSettingsPref] entry. Per the lexicon:
 * `threadgateAllowRules` *absent* (`null`) → anyone replies ([ReplyAudience.Everyone]);
 * *empty* → no one ([ReplyAudience.Nobody]); *non-empty* → the listed groups
 * ([ReplyAudience.Combination]). `postgateEmbeddingRules` *absent or empty* →
 * quotes allowed; containing the disable rule → quotes off. An absent entry
 * falls back to [PostAudience.DEFAULT]. Unknown threadgate rules (e.g. the
 * deferred list rule) are ignored — only the three managed groups are read, so
 * a list-scoped default round-trips lossily until lists are supported. No I/O.
 */
internal fun parsePostAudienceDefault(preferences: List<GetPreferencesResponsePreferencesUnion>): PostAudience {
    val entry = preferences.filterIsInstance<PostInteractionSettingsPref>().firstOrNull() ?: return PostAudience.DEFAULT

    val reply =
        when (val rules = entry.threadgateAllowRules) {
            null -> ReplyAudience.Everyone
            else ->
                if (rules.isEmpty()) {
                    ReplyAudience.Nobody
                } else {
                    ReplyAudience.Combination(
                        followers = rules.any { it is ThreadgateFollowerRule },
                        following = rules.any { it is ThreadgateFollowingRule },
                        mentioned = rules.any { it is ThreadgateMentionRule },
                    )
                }
        }

    val allowQuotes = entry.postgateEmbeddingRules?.none { it is PostgateDisableRule } ?: true

    return PostAudience(reply = reply, allowQuotes = allowQuotes)
}

/**
 * Pure merge: produce a new `preferences` list that drops the existing
 * [PostInteractionSettingsPref] entry (preserving every other entry — known
 * members pass straight through since they implement both the GET and PUT union;
 * `Unknown` members are remapped verbatim) and appends a fresh entry reflecting
 * [audience]. Following the lexicon, [ReplyAudience.Everyone] omits
 * `threadgateAllowRules` (anyone) and allowed quotes omit `postgateEmbeddingRules`
 * (anyone) — never written as empty lists, which would mean "no one". No I/O.
 */
internal fun mergePostAudienceDefault(
    original: List<GetPreferencesResponsePreferencesUnion>,
    audience: PostAudience,
): List<PutPreferencesRequestPreferencesUnion> =
    original
        .filterNot { it is PostInteractionSettingsPref }
        .map { it.asPutPreference() } + buildPostInteractionSettingsPref(audience)

private fun buildPostInteractionSettingsPref(audience: PostAudience): PostInteractionSettingsPref =
    PostInteractionSettingsPref(
        postgateEmbeddingRules =
            if (audience.allowQuotes) {
                null
            } else {
                listOf<PostInteractionSettingsPrefPostgateEmbeddingRulesUnion>(PostgateDisableRule())
            },
        threadgateAllowRules =
            when (val reply = audience.reply) {
                ReplyAudience.Everyone -> null
                ReplyAudience.Nobody -> emptyList()
                is ReplyAudience.Combination ->
                    buildList<PostInteractionSettingsPrefThreadgateAllowRulesUnion> {
                        if (reply.followers) add(ThreadgateFollowerRule())
                        if (reply.following) add(ThreadgateFollowingRule())
                        if (reply.mentioned) add(ThreadgateMentionRule())
                    }
            },
    )
