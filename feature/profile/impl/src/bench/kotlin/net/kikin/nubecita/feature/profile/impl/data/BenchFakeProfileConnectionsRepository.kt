package net.kikin.nubecita.feature.profile.impl.data

import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.data.models.ActorUi
import javax.inject.Inject

/**
 * Offline connections for the bench build: two pages per list, covering a row
 * the viewer already follows, the viewer's own row, and one with no follow
 * action, so every row state can be reached on a device.
 */
internal class BenchFakeProfileConnectionsRepository
    @Inject
    constructor() : ProfileConnectionsRepository {
        override suspend fun getFollowers(
            actor: String,
            cursor: String?,
        ): Result<ConnectionPage> = Result.success(page(prefix = "follower", cursor = cursor))

        override suspend fun getFollows(
            actor: String,
            cursor: String?,
        ): Result<ConnectionPage> = Result.success(page(prefix = "following", cursor = cursor))

        private fun page(
            prefix: String,
            cursor: String?,
        ): ConnectionPage {
            val second = cursor != null
            val start = if (second) PAGE_SIZE else 0
            val rows =
                (start until start + PAGE_SIZE).map { index ->
                    ConnectionRowUi(
                        actor =
                            ActorUi(
                                did = if (index == PROFILE_ROW) PROFILE_DID else "did:plc:bench$prefix$index",
                                handle = "$prefix$index.bsky.social",
                                displayName = "Bench ${prefix.replaceFirstChar(Char::uppercase)} $index",
                                avatarUrl = null,
                            ),
                        followUri = if (index % 4 == 1) "at://did:plc:bench/app.bsky.graph.follow/$prefix$index" else null,
                        canFollow = index != 2,
                    )
                }
            return ConnectionPage(rows = rows.toImmutableList(), cursor = if (second) null else NEXT_CURSOR)
        }

        private companion object {
            const val PAGE_SIZE = 20
            const val NEXT_CURSOR = "bench-page-2"

            /**
             * The bench profile fake serves this one account for every handle,
             * so this row is the one whose profile matches it: following from
             * there has to show here.
             */
            const val PROFILE_ROW = 3
            const val PROFILE_DID = "did:plc:benchnubecita0000000000000"
        }
    }
