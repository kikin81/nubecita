package net.kikin.nubecita.core.klipy.internal

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.klipy.KlipyReportReason
import net.kikin.nubecita.core.klipy.KlipyRepository
import net.kikin.nubecita.core.klipy.internal.di.KlipyClient
import net.kikin.nubecita.data.models.KlipyMediaPage
import net.kikin.nubecita.data.models.KlipyMediaType
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Production [KlipyRepository] backed by the KLIPY REST API.
 *
 * The injected [KlipyClient] Ktor client already carries the base URL with the
 * key baked into its path (nubecita-gotc), so calls use relative paths only —
 * the key never appears at a call site. Requests carry the stable, anonymous
 * [KlipyCustomerIdStore] id (KLIPY uses it to personalise trending/search and
 * key the user's Recents) plus the device locale.
 *
 * Fetches decode on [ioDispatcher] and return a [Result] — a failed network or
 * decode call becomes [Result.failure] rather than an exception crossing the
 * boundary. Tracking ([trackView]/[trackShare]) is fire-and-forget on
 * [appScope] so finishing the composer can't cancel a report mid-flight.
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
        ): Result<KlipyMediaPage> =
            if (query.isBlank()) {
                trending(type, page)
            } else {
                guarded {
                    httpClient
                        .get("${type.prefix}/search") {
                            parameter("q", query)
                            parameter("page", page)
                            parameter("per_page", PER_PAGE)
                            parameter("customer_id", customerIdStore.get())
                            parameter("locale", locale())
                        }.decodePage()
                }
            }

        override suspend fun trending(
            type: KlipyMediaType,
            page: Int,
        ): Result<KlipyMediaPage> =
            guarded {
                httpClient
                    .get("${type.prefix}/trending") {
                        parameter("page", page)
                        parameter("per_page", PER_PAGE)
                        parameter("customer_id", customerIdStore.get())
                        parameter("locale", locale())
                    }.decodePage()
            }

        override suspend fun recents(
            type: KlipyMediaType,
            page: Int,
        ): Result<KlipyMediaPage> =
            guarded {
                val customerId = customerIdStore.get()
                httpClient
                    .get("${type.prefix}/recent/$customerId") {
                        parameter("page", page)
                        parameter("per_page", PER_PAGE)
                    }.decodePage()
            }

        override suspend fun categories(type: KlipyMediaType): Result<ImmutableList<String>> =
            guarded {
                val response = httpClient.get("${type.prefix}/categories")
                val dto =
                    json.decodeFromString(
                        KlipyCategoriesResponseDto.serializer(),
                        response.bodyAsText(),
                    )
                // Recents + Trending lead the server-provided categories.
                (LEADING_CATEGORIES + dto.data.orEmpty()).toImmutableList()
            }

        override fun trackView(
            type: KlipyMediaType,
            slug: String,
        ) = track(type, slug, action = "view")

        override fun trackShare(
            type: KlipyMediaType,
            slug: String,
        ) = track(type, slug, action = "share")

        override suspend fun report(
            type: KlipyMediaType,
            slug: String,
            reason: KlipyReportReason,
        ): Result<Unit> =
            guarded {
                httpClient.post("${type.prefix}/report/$slug") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        json.encodeToString(
                            KlipyReportRequest.serializer(),
                            KlipyReportRequest(customerIdStore.get(), reason.apiValue),
                        ),
                    )
                }
                Unit
            }

        override suspend fun hideFromRecents(
            type: KlipyMediaType,
            slug: String,
        ): Result<Unit> =
            guarded {
                httpClient.delete("${type.prefix}/recent/${customerIdStore.get()}") {
                    parameter("slug", slug)
                }
                Unit
            }

        private fun track(
            type: KlipyMediaType,
            slug: String,
            action: String,
        ) {
            appScope.launch(ioDispatcher) {
                try {
                    httpClient.post("${type.prefix}/$action/$slug") {
                        contentType(ContentType.Application.Json)
                        setBody(
                            json.encodeToString(
                                KlipyTrackRequest.serializer(),
                                KlipyTrackRequest(customerIdStore.get()),
                            ),
                        )
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    Timber.tag(TAG).d(error, "KLIPY %s tracking failed for %s", action, slug)
                }
            }
        }

        private suspend fun <T> guarded(block: suspend () -> T): Result<T> =
            withContext(ioDispatcher) {
                try {
                    Result.success(block())
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    Timber.tag(TAG).d(error, "KLIPY call failed")
                    Result.failure(error)
                }
            }

        private suspend fun HttpResponse.decodePage(): KlipyMediaPage = json.decodeFromString(KlipyMediaResponseDto.serializer(), bodyAsText()).toPage()

        private fun KlipyMediaResponseDto.toPage(): KlipyMediaPage =
            KlipyMediaPage(
                items = data?.data?.mapNotNull { it.toUiOrNull() }?.toImmutableList() ?: persistentListOf(),
                hasNext = data?.hasNext ?: false,
            )

        private fun locale(): String = Locale.getDefault().language

        private val KlipyMediaType.prefix: String
            get() =
                when (this) {
                    KlipyMediaType.GIF -> "gifs"
                    KlipyMediaType.STICKER -> "stickers"
                }

        private companion object {
            const val TAG = "Klipy"
            const val PER_PAGE = 30
            val LEADING_CATEGORIES = listOf("Recents", "Trending")
        }
    }
