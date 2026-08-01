package net.kikin.nubecita.core.actors.internal

import net.kikin.nubecita.core.actors.PublicActorSearch
import net.kikin.nubecita.data.models.ActorUi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deterministic, offline suggestions for the bench flavor's login screen.
 *
 * Bench must issue zero network calls, so the real implementation's AppView and
 * DID-document lookups are replaced by a fixed roster filtered on the query.
 * One entry is deliberately hosted somewhere other than Bluesky: the network
 * line only says anything for a self-hosted account, so without it the bench
 * build could never render the case the line exists for.
 */
@Singleton
internal class BenchFakePublicActorSearch
    @Inject
    constructor() : PublicActorSearch {
        override suspend fun searchTypeahead(
            query: String,
            limit: Int,
        ): Result<List<ActorUi>> {
            val q = query.trim().removePrefix("@").lowercase()
            if (q.isEmpty()) return Result.success(emptyList())
            return Result.success(
                ROSTER
                    .filter {
                        it.handle.contains(q) ||
                            it.displayName
                                .orEmpty()
                                .lowercase()
                                .contains(q)
                    }.take(limit),
            )
        }

        override suspend fun resolvePdsHost(did: String): String? = PDS_HOSTS[did]

        private companion object {
            val ROSTER =
                listOf(
                    actor("did:plc:bench0001", "alice.bsky.social", "Alice Chen"),
                    actor("did:plc:bench0002", "alicia.bsky.social", "Alicia Ruiz"),
                    actor("did:plc:bench0003", "alex.example.com", "Alex Self-Hosted"),
                    actor("did:plc:bench0004", "bob.bsky.social", "Bob Nakamura"),
                )

            val PDS_HOSTS =
                mapOf(
                    "did:plc:bench0001" to "morel.us-east.host.bsky.network",
                    "did:plc:bench0002" to "russula.us-west.host.bsky.network",
                    // The one that makes the network line worth rendering.
                    "did:plc:bench0003" to "pds.example.com",
                    "did:plc:bench0004" to "shiitake.us-east.host.bsky.network",
                )

            fun actor(
                did: String,
                handle: String,
                displayName: String,
            ) = ActorUi(
                did = did,
                handle = handle,
                displayName = displayName,
                avatarUrl = null,
            )
        }
    }
