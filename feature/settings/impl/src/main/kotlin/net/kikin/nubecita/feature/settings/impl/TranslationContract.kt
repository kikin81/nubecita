package net.kikin.nubecita.feature.settings.impl

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.data.models.DownloadedLanguage

/**
 * Flat state for the Translation screen.
 *
 * No sealed status sum: every source flow emits immediately, so there is no
 * loading, error or empty mode to make unrepresentable.
 */
internal data class TranslationState(
    /**
     * The master switch. Defaults to `false`, which is also what a fresh
     * install reads, so the first frame is never a wrong `true` that flips.
     *
     * Off does **not** mean "no translate affordance". It means detection uses
     * the post's declared language only — a string comparison with no model
     * loaded — so a reader who never translates pays nothing while the feature
     * stays discoverable.
     */
    val autoTranslateEnabled: Boolean = false,
    /** Languages translated automatically, as bare BCP-47 subtags. */
    val autoTranslate: ImmutableList<String> = persistentListOf(),
    /** Languages the reader reads and never wants translated. */
    val neverTranslate: ImmutableList<String> = persistentListOf(),
    /**
     * Every language the engine can translate, for the add-language picker.
     *
     * Read from the engine rather than hard-coded so the picker cannot drift
     * from what `supports` will actually accept.
     */
    val availableLanguages: ImmutableList<String> = persistentListOf(),
    /** Downloaded packs with their measured on-disk sizes. */
    val downloaded: ImmutableList<DownloadedLanguage> = persistentListOf(),
) : UiState {
    /** Combined on-disk cost of every downloaded pack. */
    val totalBytes: Long get() = downloaded.sumOf { it.sizeBytes }
}

internal sealed interface TranslationEvent : UiEvent {
    data class AutoTranslateToggled(
        val enabled: Boolean,
    ) : TranslationEvent

    data class AutoTranslateLanguagesChanged(
        val languages: Set<String>,
    ) : TranslationEvent

    data class NeverTranslateLanguagesChanged(
        val languages: Set<String>,
    ) : TranslationEvent

    /**
     * Delete a downloaded pack.
     *
     * Also drops the language from the auto list — an automatic translation
     * with no model on disk is just a spinner.
     */
    data class LanguageDeleted(
        val languageTag: String,
    ) : TranslationEvent
}

internal sealed interface TranslationEffect : UiEffect {
    /** A preference or model write failed; the screen surfaces a snackbar. */
    data object ShowSaveError : TranslationEffect
}
