package net.kikin.nubecita.core.feeds

import io.github.kikin81.atproto.app.bsky.actor.ActorService
import io.github.kikin81.atproto.app.bsky.actor.GetPreferencesRequest
import io.github.kikin81.atproto.app.bsky.actor.GetPreferencesResponsePreferencesUnion
import io.github.kikin81.atproto.app.bsky.actor.SavedFeed
import io.github.kikin81.atproto.app.bsky.actor.SavedFeedsPrefV2
import io.github.kikin81.atproto.app.bsky.feed.FeedService
import io.github.kikin81.atproto.app.bsky.feed.GetFeedGeneratorsRequest
import io.github.kikin81.atproto.runtime.AtUri
import net.kikin.nubecita.core.auth.XrpcClientProvider
import javax.inject.Inject

/**
 * Slim, UI-free projection of one `app.bsky.feed.defs#generatorView`,
 * carrying only the fields the feed-switcher chip needs. Decouples the
 * repository's orchestration (and its tests) from the heavy wire
 * `GeneratorView` value-class shape.
 */
internal data class GeneratorMeta(
    val uri: String,
    val displayName: String,
    val avatarUrl: String?,
)

/**
 * The seam between [DefaultPinnedFeedsRepository]'s pure orchestration and
 * the atproto XRPC services. Isolating the two network calls behind this
 * interface lets the repository's order/filter/split/fallback logic be
 * unit-tested with a mock, without forging HTTP responses through a real
 * [io.github.kikin81.atproto.runtime.XrpcClient].
 */
internal interface FeedsDataSource {
    /**
     * Reads `app.bsky.actor.getPreferences` and returns the
     * `savedFeedsPrefV2` items in stored order, or `null` when the account
     * has no such preference entry. Throws on transport/auth failure.
     */
    suspend fun getSavedFeedItems(): List<SavedFeed>?

    /**
     * Batch-hydrates generator metadata for [uris] via
     * `app.bsky.feed.getFeedGenerators`. Throws on transport/auth failure.
     */
    suspend fun getFeedGenerators(uris: List<String>): List<GeneratorMeta>
}

internal class DefaultFeedsDataSource
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
    ) : FeedsDataSource {
        override suspend fun getSavedFeedItems(): List<SavedFeed>? {
            val response = ActorService(xrpcClientProvider.authenticated()).getPreferences(GetPreferencesRequest())
            return extractSavedFeedItems(response.preferences)
        }

        override suspend fun getFeedGenerators(uris: List<String>): List<GeneratorMeta> {
            if (uris.isEmpty()) return emptyList()
            val client = xrpcClientProvider.authenticated()
            val response =
                FeedService(client).getFeedGenerators(
                    GetFeedGeneratorsRequest(feeds = uris.map { AtUri(it) }),
                )
            return response.feeds.map { view ->
                GeneratorMeta(
                    uri = view.uri.raw,
                    displayName = view.displayName,
                    avatarUrl = view.avatar?.raw,
                )
            }
        }
    }

/**
 * Returns the `items` of the single [SavedFeedsPrefV2] entry in a typed
 * `getPreferences` [preferences] list (stored order), or `null` when the account
 * has no such entry. Every other preference kind — including unmodeled future
 * ones surfaced as the union's `Unknown` member — is ignored. Pure (no I/O), so
 * the selection contract is unit-tested in isolation.
 */
internal fun extractSavedFeedItems(
    preferences: List<GetPreferencesResponsePreferencesUnion>,
): List<SavedFeed>? = preferences.filterIsInstance<SavedFeedsPrefV2>().firstOrNull()?.items
