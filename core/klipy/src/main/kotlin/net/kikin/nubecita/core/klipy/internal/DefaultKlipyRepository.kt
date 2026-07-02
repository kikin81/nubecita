package net.kikin.nubecita.core.klipy.internal

import io.ktor.client.HttpClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.klipy.KlipyReportReason
import net.kikin.nubecita.core.klipy.KlipyRepository
import net.kikin.nubecita.core.klipy.internal.di.KlipyClient
import net.kikin.nubecita.data.models.KlipyMediaPage
import net.kikin.nubecita.data.models.KlipyMediaType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [KlipyRepository] backed by the KLIPY REST API. Bound in the
 * `production` flavor by `KlipyBindingsModule`; the `bench` flavor binds
 * [BenchFakeKlipyRepository] instead.
 *
 * The constructor wires the full `:core:klipy` dependency set assembled in
 * nubecita-gotc — the qualified [KlipyClient] Ktor client, the shared [Json],
 * the [KlipyCustomerIdStore], the IO dispatcher, and the application scope for
 * fire-and-forget tracking — so the DI graph resolves end to end today.
 *
 * The endpoint bodies (search / trending / categories / recents +
 * `KlipyPagingSource`) and the tracking calls land in **nubecita-ubzz** and
 * **nubecita-3hu9**; until then the fetches return a failed [Result] and the
 * fire-and-forget tracking is a no-op — nothing consumes this in production yet
 * (the picker uses the bench fake), so the skeleton stays inert rather than
 * pretending to return data.
 */
@Singleton
internal class DefaultKlipyRepository
    @Inject
    constructor(
        @KlipyClient private val httpClient: HttpClient,
        private val json: Json,
        private val customerIdStore: KlipyCustomerIdStore,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
        @ApplicationScope private val appScope: CoroutineScope,
    ) : KlipyRepository {
        override suspend fun search(
            type: KlipyMediaType,
            query: String,
            page: Int,
        ): Result<KlipyMediaPage> = notYetImplemented()

        override suspend fun trending(
            type: KlipyMediaType,
            page: Int,
        ): Result<KlipyMediaPage> = notYetImplemented()

        override suspend fun recents(
            type: KlipyMediaType,
            page: Int,
        ): Result<KlipyMediaPage> = notYetImplemented()

        override suspend fun categories(type: KlipyMediaType): Result<ImmutableList<String>> = notYetImplemented()

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
        ): Result<Unit> = notYetImplemented()

        override suspend fun hideFromRecents(
            type: KlipyMediaType,
            slug: String,
        ): Result<Unit> = notYetImplemented()

        private fun <T> notYetImplemented(): Result<T> = Result.failure(UnsupportedOperationException("KLIPY endpoints land in nubecita-ubzz / nubecita-3hu9"))
    }
