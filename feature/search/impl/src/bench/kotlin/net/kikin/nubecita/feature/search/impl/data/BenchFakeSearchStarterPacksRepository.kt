package net.kikin.nubecita.feature.search.impl.data

import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.data.models.StarterPackUi
import net.kikin.nubecita.data.models.StarterPackUiFixtures
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bench-flavor stand-in for [SearchStarterPacksRepository]. Ignores the query and serves a fixed
 * list of [TOTAL] packs in pages of the requested size (the cursor is the next start index), so
 * the bench journey can scroll the Packs tab and watch it paginate without a network call. Packs
 * alternate between having a member sample and not, and a known and an unknown member count.
 * Never fails.
 */
@Singleton
internal class BenchFakeSearchStarterPacksRepository
    @Inject
    constructor() : SearchStarterPacksRepository {
        override suspend fun searchStarterPacks(
            query: String,
            cursor: String?,
            limit: Int,
        ): Result<SearchStarterPacksPage> {
            val start = cursor?.toIntOrNull() ?: 0
            val end = minOf(start + limit, TOTAL)
            return Result.success(
                SearchStarterPacksPage(
                    items = (start until end).map { pack(it) }.toImmutableList(),
                    nextCursor = if (end < TOTAL) end.toString() else null,
                ),
            )
        }

        private fun pack(index: Int): StarterPackUi =
            StarterPackUiFixtures
                .packWithAvatars(
                    count = if (index % 3 == 0) 0 else 4,
                    slug = "pack$index",
                    name = NAMES[index % NAMES.size] + if (index >= NAMES.size) " ${index / NAMES.size + 1}" else "",
                    memberCount = if (index % 4 == 3) null else 8 + index * 3,
                ).copy(creatorHandle = "${CREATORS[index % CREATORS.size]}.bsky.social")

        private companion object {
            const val TOTAL = 40
            val NAMES = listOf("Android developers", "Science writers", "Trail runners", "Coffee people", "Indie game devs", "Photographers")
            val CREATORS = listOf("labnotes", "studio", "switchback", "thirdwave", "shipit", "goldenhour")
        }
    }
