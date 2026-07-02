package net.kikin.nubecita.core.klipy.internal

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.klipy.KlipyReportReason
import net.kikin.nubecita.core.klipy.KlipyRepository
import net.kikin.nubecita.data.models.KlipyMediaPage
import net.kikin.nubecita.data.models.KlipyMediaType
import net.kikin.nubecita.data.models.KlipyMediaUiFixtures
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bench-flavor [KlipyRepository]. Serves canned [KlipyMediaUiFixtures] data with
 * no API key and no network, so the picker (nubecita-m1xp) is exercisable
 * offline and its screenshot tests are deterministic.
 *
 * Every fetch succeeds. Pagination is faked: pages 1–2 return items with
 * `hasNext` true then false, page ≥ 3 returns empty — enough to exercise the
 * paging load-more / end-of-feed path. Tracking and report/hide are no-ops that
 * always succeed. Mirrors `:core:preferences`'s bench `FakeUserPreferencesRepository`.
 */
@Singleton
internal class BenchFakeKlipyRepository
    @Inject
    constructor() : KlipyRepository {
        override suspend fun search(
            type: KlipyMediaType,
            query: String,
            page: Int,
        ): Result<KlipyMediaPage> = Result.success(pageFor(type, page))

        override suspend fun trending(
            type: KlipyMediaType,
            page: Int,
        ): Result<KlipyMediaPage> = Result.success(pageFor(type, page))

        override suspend fun recents(
            type: KlipyMediaType,
            page: Int,
        ): Result<KlipyMediaPage> = Result.success(pageFor(type, page))

        override suspend fun categories(type: KlipyMediaType): Result<ImmutableList<String>> = Result.success(CATEGORIES)

        override fun trackView(
            type: KlipyMediaType,
            slug: String,
        ) = Unit

        override fun trackShare(
            type: KlipyMediaType,
            slug: String,
        ) = Unit

        override suspend fun report(
            type: KlipyMediaType,
            slug: String,
            reason: KlipyReportReason,
        ): Result<Unit> = Result.success(Unit)

        override suspend fun hideFromRecents(
            type: KlipyMediaType,
            slug: String,
        ): Result<Unit> = Result.success(Unit)

        private fun pageFor(
            type: KlipyMediaType,
            page: Int,
        ): KlipyMediaPage {
            if (page > LAST_PAGE) return KlipyMediaPage(items = persistentListOf(), hasNext = false)
            val items =
                when (type) {
                    KlipyMediaType.GIF -> KlipyMediaUiFixtures.page().items
                    KlipyMediaType.STICKER -> KlipyMediaUiFixtures.stickers
                }
            return KlipyMediaPage(items = items, hasNext = page < LAST_PAGE)
        }

        private companion object {
            const val LAST_PAGE = 2
            val CATEGORIES: ImmutableList<String> =
                persistentListOf("Recents", "Trending", "Reactions", "Love", "Happy", "Sad")
        }
    }
