package net.kikin.nubecita.feature.starterpack.impl.data

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.data.models.ActorUi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bench-flavor stand-in for [StarterPackRepository]: one deterministic pack, whatever the URI,
 * served without a network call so the offline bench build can open a pack from search.
 *
 * The pack has [MEMBER_TOTAL] members served in pages of the requested size, and two feeds, so
 * the screen's header, paging and feed rows can all be exercised. Never fails.
 */
@Singleton
internal class BenchFakeStarterPackRepository
    @Inject
    constructor() : StarterPackRepository {
        override suspend fun getStarterPack(uri: String): Result<StarterPackDetail> =
            Result.success(
                StarterPackDetail(
                    uri = uri,
                    name = "Android developers",
                    creatorHandle = "labnotes.bsky.social",
                    description = "People building, designing and writing about Android.",
                    memberCount = MEMBER_TOTAL,
                    listUri = LIST_URI,
                    feeds =
                        persistentListOf(
                            PackFeedUi(
                                uri = "at://did:plc:android/app.bsky.feed.generator/android",
                                displayName = "Android",
                                creatorHandle = "labnotes.bsky.social",
                                avatarUrl = null,
                            ),
                            PackFeedUi(
                                uri = "at://did:plc:compose/app.bsky.feed.generator/compose",
                                displayName = "Jetpack Compose",
                                creatorHandle = "studio.bsky.social",
                                avatarUrl = null,
                            ),
                        ),
                ),
            )

        override suspend fun getMembers(
            listUri: String,
            cursor: String?,
            limit: Int,
        ): Result<PackMembersPage> {
            val start = cursor?.toIntOrNull() ?: 0
            val end = minOf(start + limit, MEMBER_TOTAL)
            return Result.success(
                PackMembersPage(
                    members = (start until end).map { member(it) }.toImmutableList(),
                    nextCursor = if (end < MEMBER_TOTAL) end.toString() else null,
                ),
            )
        }

        private fun member(index: Int): ActorUi =
            ActorUi(
                did = "did:plc:member$index",
                handle = "member$index.bsky.social",
                displayName = "Member ${index + 1}",
                avatarUrl = null,
                bio = "Builds things for Android, member ${index + 1}.",
            )

        private companion object {
            const val MEMBER_TOTAL = 60
            const val LIST_URI = "at://did:plc:labnotes/app.bsky.graph.list/android-devs"
        }
    }
