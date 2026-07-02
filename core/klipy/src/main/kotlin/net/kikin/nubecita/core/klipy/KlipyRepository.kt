package net.kikin.nubecita.core.klipy

import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.data.models.KlipyMediaPage
import net.kikin.nubecita.data.models.KlipyMediaType

/**
 * SDK-agnostic boundary over KLIPY. Callers see only `:data:models` types; the
 * Ktor client, DTOs, and the path-embedded API key never leak past this module
 * (mirrors the `:core:billing` boundary).
 *
 * Fetches return [Result] so the picker can surface a retry without exceptions
 * crossing the boundary. Tracking calls ([trackView]/[trackShare]) are
 * fire-and-forget — they return `Unit` and run on an application-scoped
 * coroutine in the implementation, so finishing the composer (which cancels the
 * screen scope) can't cancel a share report mid-flight.
 *
 * The production implementation lands in nubecita-ubzz (fetch + paging) and
 * nubecita-3hu9 (tracking); a `BenchFakeKlipyRepository` lands in nubecita-srx0.
 */
public interface KlipyRepository {
    /** Search [type] media for [query]; a blank query yields trending. `page` is 1-based. */
    public suspend fun search(
        type: KlipyMediaType,
        query: String,
        page: Int,
    ): Result<KlipyMediaPage>

    /** The trending feed for [type]. `page` is 1-based. */
    public suspend fun trending(
        type: KlipyMediaType,
        page: Int,
    ): Result<KlipyMediaPage>

    /** The signed-in user's recently shared [type] items. `page` is 1-based. */
    public suspend fun recents(
        type: KlipyMediaType,
        page: Int,
    ): Result<KlipyMediaPage>

    /** Category titles for [type], with Recents and Trending leading the list. */
    public suspend fun categories(type: KlipyMediaType): Result<ImmutableList<String>>

    /** Report that the user previewed [slug]. Fire-and-forget. */
    public fun trackView(
        type: KlipyMediaType,
        slug: String,
    )

    /** Report that the user selected [slug] to send. Fire-and-forget; also adds to Recents. */
    public fun trackShare(
        type: KlipyMediaType,
        slug: String,
    )

    /** Report [slug] with a [reason]. */
    public suspend fun report(
        type: KlipyMediaType,
        slug: String,
        reason: KlipyReportReason,
    ): Result<Unit>

    /** Remove [slug] from the user's Recents. */
    public suspend fun hideFromRecents(
        type: KlipyMediaType,
        slug: String,
    ): Result<Unit>
}

/**
 * The report reasons KLIPY accepts. The wire value ([apiValue]) is sent as the
 * `reason` body field of `POST …/report/{slug}`.
 */
public enum class KlipyReportReason(
    public val apiValue: String,
) {
    SPAM("spam"),
    NUDITY("nudity"),
    VIOLENCE("violence"),
    OTHER("other"),
}
