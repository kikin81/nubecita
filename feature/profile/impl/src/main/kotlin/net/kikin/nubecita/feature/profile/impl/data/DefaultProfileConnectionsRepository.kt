package net.kikin.nubecita.feature.profile.impl.data

import io.github.kikin81.atproto.app.bsky.actor.ProfileView
import io.github.kikin81.atproto.app.bsky.graph.GetFollowersRequest
import io.github.kikin81.atproto.app.bsky.graph.GetFollowsRequest
import io.github.kikin81.atproto.app.bsky.graph.GraphService
import io.github.kikin81.atproto.runtime.AtIdentifier
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.common.coroutines.runCatchingCancellable
import timber.log.Timber
import javax.inject.Inject

internal class DefaultProfileConnectionsRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        private val sessionStateProvider: SessionStateProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ProfileConnectionsRepository {
        override suspend fun getFollowers(
            actor: String,
            cursor: String?,
        ): Result<ConnectionPage> =
            load {
                val response =
                    GraphService(xrpcClientProvider.authenticated()).getFollowers(
                        GetFollowersRequest(actor = AtIdentifier(actor), limit = PAGE_LIMIT, cursor = cursor),
                    )
                response.followers.toPage(response.cursor)
            }

        override suspend fun getFollows(
            actor: String,
            cursor: String?,
        ): Result<ConnectionPage> =
            load {
                val response =
                    GraphService(xrpcClientProvider.authenticated()).getFollows(
                        GetFollowsRequest(actor = AtIdentifier(actor), limit = PAGE_LIMIT, cursor = cursor),
                    )
                response.follows.toPage(response.cursor)
            }

        private suspend fun load(block: suspend () -> ConnectionPage): Result<ConnectionPage> =
            withContext(dispatcher) {
                runCatchingCancellable { block() }
                    .onFailure { throwable ->
                        Timber.tag(TAG).w(throwable, "connections page failed: %s", throwable.javaClass.name)
                    }
            }

        private fun List<ProfileView>.toPage(cursor: String?): ConnectionPage {
            val viewerDid = (sessionStateProvider.state.value as? SessionState.SignedIn)?.did
            return ConnectionPage(rows = map { it.toConnectionRowUi(viewerDid) }.toImmutableList(), cursor = cursor)
        }

        private companion object {
            const val PAGE_LIMIT = 50L
            const val TAG = "ProfileConnectionsRepo"
        }
    }
