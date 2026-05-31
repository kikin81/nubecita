package net.kikin.nubecita.core.feeds

import io.github.kikin81.atproto.app.bsky.actor.GetPreferencesRequest
import io.github.kikin81.atproto.app.bsky.actor.SavedFeed
import io.github.kikin81.atproto.app.bsky.actor.SavedFeedsPrefV2
import io.github.kikin81.atproto.app.bsky.feed.FeedService
import io.github.kikin81.atproto.app.bsky.feed.GetFeedGeneratorsRequest
import io.github.kikin81.atproto.runtime.AtUri
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        // `getPreferences` returns an array of `$type`-tagged preference
        // objects under `preferences`; we only care about savedFeedsPrefV2
        // and must tolerate every other (un-modeled) preference kind.
        private val json = Json { ignoreUnknownKeys = true }

        override suspend fun getSavedFeedItems(): List<SavedFeed>? {
            val client = xrpcClientProvider.authenticated()
            // The generated `ActorService.getPreferences` / `GetPreferencesResponse`
            // model the `preferences` FIELD as a `JsonObject`, but the canonical
            // `app.bsky.actor.getPreferences` body has `preferences` as an ARRAY of
            // `$type`-tagged objects. Deserializing the real body through the SDK
            // response type throws `JsonDecodingException` ("Expected start of the
            // object '{', but had '['" at $.preferences), so the typed call can
            // never succeed for a real account. We instead decode the response as a
            // raw top-level `JsonObject` (the body itself IS an object) and read the
            // `preferences` array ourselves. `JsonObject.serializer()` is built-in,
            // so no serialization compiler plugin is needed in this module.
            val response =
                client.query(
                    GET_PREFERENCES_NSID,
                    GetPreferencesRequest(),
                    GetPreferencesRequest.serializer(),
                    JsonObject.serializer(),
                )
            return extractSavedFeedItems(response, json)
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
 * Locates the single `app.bsky.actor.defs#savedFeedsPrefV2` entry inside a raw
 * `getPreferences` response [body] and returns its `items` in stored order, or
 * `null` when no such entry exists.
 *
 * The response shape is `{ "preferences": [ { "$type": …, … }, … ] }` — note
 * `preferences` is an ARRAY (the generated SDK type mis-models it as an object,
 * which is why we decode the raw [JsonObject] body here). Each element is
 * `$type`-tagged; we scan for the savedFeedsPrefV2 discriminator, decode that
 * element with the generated [SavedFeedsPrefV2] serializer, and read its
 * `items`. Pure (no I/O) so the decoding contract is unit-tested in isolation.
 */
internal fun extractSavedFeedItems(
    body: JsonObject,
    json: Json,
): List<SavedFeed>? {
    val entries = body["preferences"] as? JsonArray ?: return null
    val savedFeedsEntry =
        entries.firstOrNull { element ->
            (element as? JsonObject)
                ?.get("\$type")
                ?.jsonPrimitive
                ?.content == SAVED_FEEDS_PREF_V2_TYPE
        } ?: return null
    val pref = json.decodeFromJsonElement(SavedFeedsPrefV2.serializer(), savedFeedsEntry.jsonObject)
    return pref.items
}

private const val GET_PREFERENCES_NSID = "app.bsky.actor.getPreferences"
private const val SAVED_FEEDS_PREF_V2_TYPE = "app.bsky.actor.defs#savedFeedsPrefV2"
