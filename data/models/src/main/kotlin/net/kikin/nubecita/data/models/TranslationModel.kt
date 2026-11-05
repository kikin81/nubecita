package net.kikin.nubecita.data.models

import androidx.compose.runtime.Immutable

/**
 * Where one language's on-device translation pack currently stands.
 *
 * A sealed sum rather than flat flags because exactly one is true at a time
 * and the combinations are meaningless — "downloading and not downloaded" is
 * not a state anything should have to reason about. The UI branches on this
 * with `when`.
 */
@Immutable
public sealed interface TranslationModelState {
    /** No pack on disk, and none requested. */
    @Immutable
    public data object NotDownloaded : TranslationModelState

    /**
     * The reader asked for this language while on a metered connection.
     *
     * A distinct state, not an error: the request is remembered and the
     * download starts on its own once an unmetered connection appears. The UI
     * says so, because a reader who taps Translate and sees nothing happen
     * will reasonably conclude the feature is broken.
     */
    @Immutable
    public data object WaitingForUnmeteredNetwork : TranslationModelState

    /** Transfer in progress. */
    @Immutable
    public data object Downloading : TranslationModelState

    /**
     * Ready to translate, occupying [sizeBytes] on disk.
     *
     * The size is measured from the files rather than quoted from a table.
     * Published figures put a pack near 30MB; the real cost measured on device
     * is roughly 45MB — 62MB for Japanese — because the engine fetches both
     * directions of a pair. A reader deciding whether to keep five of these
     * deserves the real number.
     */
    @Immutable
    public data class Downloaded(
        val sizeBytes: Long,
    ) : TranslationModelState
}

/**
 * A downloaded language pack, for the storage section of Settings.
 *
 * [languageTag] is a bare BCP-47 subtag; the display name is resolved at the
 * UI layer against the reader's own locale, so this stays free of
 * presentation concerns.
 */
@Immutable
public data class DownloadedLanguage(
    val languageTag: String,
    val sizeBytes: Long,
)
