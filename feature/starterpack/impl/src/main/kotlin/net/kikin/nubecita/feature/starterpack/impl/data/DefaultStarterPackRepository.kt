package net.kikin.nubecita.feature.starterpack.impl.data

import io.github.kikin81.atproto.app.bsky.actor.ProfileView
import io.github.kikin81.atproto.app.bsky.feed.GeneratorView
import io.github.kikin81.atproto.app.bsky.graph.GetListRequest
import io.github.kikin81.atproto.app.bsky.graph.GetStarterPackRequest
import io.github.kikin81.atproto.app.bsky.graph.GraphService
import io.github.kikin81.atproto.runtime.AtUri
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.feedmapping.toStarterPackUi
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.data.models.toVerifiedBadge
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [StarterPackRepository] over the SDK's generated [GraphService]
 * (`app.bsky.graph.getStarterPack` and `app.bsky.graph.getList`), through the
 * authenticated client every other reader uses.
 *
 * A URI the SDK rejects, a failed request and a response that does not decode are all a
 * [Result.failure]; cancellation is re-thrown before anything is logged. Nothing about the
 * pack (its URI, its name) is logged: only the failure's class.
 */
@Singleton
internal class DefaultStarterPackRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : StarterPackRepository {
        override suspend fun getStarterPack(uri: String): Result<StarterPackDetail> =
            call("getStarterPack") {
                val view =
                    GraphService(xrpcClientProvider.authenticated())
                        .getStarterPack(GetStarterPackRequest(starterPack = AtUri(uri)))
                        .starterPack
                val summary = view.toStarterPackUi() ?: error("starter pack has no name")
                StarterPackDetail(
                    uri = summary.uri,
                    name = summary.name,
                    creatorHandle = summary.creatorHandle,
                    description = view.record.description(),
                    memberCount = summary.memberCount,
                    listUri = view.list?.uri?.raw,
                    feeds =
                        view.feeds
                            .orEmpty()
                            .map { it.toPackFeedUi() }
                            .toImmutableList(),
                )
            }

        override suspend fun getMembers(
            listUri: String,
            cursor: String?,
            limit: Int,
        ): Result<PackMembersPage> {
            require(limit in 1..100) { "limit must be in 1..100 (atproto lexicon range), got $limit" }
            return call("getList") {
                val response =
                    GraphService(xrpcClientProvider.authenticated())
                        .getList(GetListRequest(list = AtUri(listUri), cursor = cursor, limit = limit.toLong()))
                PackMembersPage(
                    members = response.items.map { it.subject.toActorUi() }.toImmutableList(),
                    nextCursor = response.cursor,
                )
            }
        }

        private suspend fun <T> call(
            what: String,
            block: suspend () -> T,
        ): Result<T> =
            withContext(dispatcher) {
                try {
                    Result.success(block())
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (t: Throwable) {
                    Timber.tag(TAG).w(t, "%s failed: %s", what, t.javaClass.name)
                    Result.failure(t)
                }
            }

        private companion object {
            const val TAG = "StarterPackRepo"
        }
    }

private fun JsonObject.description(): String? =
    (this["description"] as? JsonPrimitive)
        ?.takeIf { it.isString }
        ?.content
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

private fun GeneratorView.toPackFeedUi(): PackFeedUi =
    PackFeedUi(
        uri = uri.raw,
        displayName = displayName,
        creatorHandle = creator.handle.raw,
        avatarUrl = avatar?.raw,
    )

private fun ProfileView.toActorUi(): ActorUi =
    ActorUi(
        did = did.raw,
        handle = handle.raw,
        displayName = displayName?.takeIf { it.isNotBlank() },
        avatarUrl = avatar?.raw,
        verifiedBadge = verification.toVerifiedBadge(),
        bio = description?.takeIf { it.isNotBlank() },
        isFollowing = viewer?.following != null,
        followUri = viewer?.following?.raw,
    )
