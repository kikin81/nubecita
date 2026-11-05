package net.kikin.nubecita.core.translation

import kotlinx.coroutines.flow.Flow
import net.kikin.nubecita.data.models.DownloadedLanguage
import net.kikin.nubecita.data.models.TranslationModelState

/**
 * The on-device language packs: what is here, what it costs, and getting rid
 * of it.
 *
 * Storage is a first-class concern rather than an implementation detail.
 * Measured on device, a pack is ~45MB — 62MB for Japanese — because the engine
 * fetches both directions of a pair; five languages came to 241.8MB, and it
 * lands in app storage where the reader sees it. A feature that can quietly
 * consume a quarter of a gigabyte owes the reader a list and a delete button.
 */
interface TranslationModelRepository {
    /**
     * The state of every language the reader has shown interest in, keyed by
     * bare BCP-47 subtag.
     *
     * A language absent from the map is [TranslationModelState.NotDownloaded];
     * the map only carries what has a story.
     */
    val states: Flow<Map<String, TranslationModelState>>

    /** Every downloaded pack with its measured size, for the storage UI. */
    val downloaded: Flow<List<DownloadedLanguage>>

    /**
     * Ask for [languageTag]'s pack.
     *
     * Downloads immediately on an unmetered connection. On a metered one the
     * request is *remembered*, the language moves to
     * [TranslationModelState.WaitingForUnmeteredNetwork], and the transfer
     * starts by itself when an unmetered connection appears — a 62MB download
     * is not something to put on someone's cellular plan without asking, and
     * failing silently would read as the feature being broken.
     *
     * Idempotent: asking for a pack that is present or already in flight does
     * nothing.
     */
    suspend fun request(languageTag: String)

    /**
     * Remove [languageTag]'s pack and forget any pending request for it.
     *
     * The caller is responsible for dropping the language from the
     * auto-translate list — an automatic translation with no pack on disk is
     * just a spinner.
     */
    suspend fun delete(languageTag: String)

    /** Whether a pack exists for [languageTag] right now. */
    suspend fun isDownloaded(languageTag: String): Boolean
}
