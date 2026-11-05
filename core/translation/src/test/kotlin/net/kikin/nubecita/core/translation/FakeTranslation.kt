package net.kikin.nubecita.core.translation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import net.kikin.nubecita.data.models.DownloadedLanguage
import net.kikin.nubecita.data.models.TranslationModelState

/**
 * In-memory stand-ins for the translation boundary.
 *
 * They live in `src/test` rather than in shared test fixtures on purpose,
 * mirroring `:core:billing`: a downstream module that wants to test against
 * translation should mock the *interfaces* with MockK and drive them from its
 * own test, not reach for a fake whose behaviour it does not control. Keeping
 * these unexported stops that from becoming the path of least resistance.
 */
internal class FakeTranslationEngine(
    private val supported: Set<String> = setOf("ja", "de", "es", "en", "pt", "fr", "it"),
    private val translator: (String) -> String = { "translated:$it" },
) : TranslationEngine {
    var releaseCount: Int = 0
        private set

    val translateCalls: MutableList<Triple<String, String, String>> = mutableListOf()

    var failWith: Throwable? = null

    override suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String,
    ): Result<String> {
        translateCalls += Triple(text, sourceLanguage, targetLanguage)
        failWith?.let { return Result.failure(it) }
        if (!supports(sourceLanguage, targetLanguage)) {
            return Result.failure(IllegalArgumentException("unsupported"))
        }
        return Result.success(translator(text))
    }

    override fun supports(
        sourceLanguage: String,
        targetLanguage: String,
    ): Boolean = sourceLanguage in supported && targetLanguage in supported

    override fun release() {
        releaseCount++
    }
}

internal class FakeLanguageIdentifier(
    private val answers: Map<String, String?> = emptyMap(),
) : LanguageIdentifier {
    var identifyCount: Int = 0
        private set

    var releaseCount: Int = 0
        private set

    override suspend fun identify(text: String): String? {
        identifyCount++
        return answers[text]
    }

    override fun release() {
        releaseCount++
    }
}

internal class FakeTranslationModelRepository(
    initial: Map<String, TranslationModelState> = emptyMap(),
) : TranslationModelRepository {
    private val backing = MutableStateFlow(initial)

    val requested: MutableList<String> = mutableListOf()
    val deleted: MutableList<String> = mutableListOf()

    /** Set true to make [request] park in `WaitingForUnmeteredNetwork`. */
    var metered: Boolean = false

    override val states: Flow<Map<String, TranslationModelState>> = backing.asStateFlow()

    override val downloaded: Flow<List<DownloadedLanguage>> =
        backing.map { states ->
            states
                .mapNotNull { (tag, state) ->
                    (state as? TranslationModelState.Downloaded)
                        ?.let { DownloadedLanguage(tag, it.sizeBytes) }
                }.sortedBy { it.languageTag }
        }

    override suspend fun request(languageTag: String) {
        requested += languageTag
        backing.value =
            backing.value +
            (
                languageTag to
                    if (metered) {
                        TranslationModelState.WaitingForUnmeteredNetwork
                    } else {
                        TranslationModelState.Downloaded(sizeBytes = DEFAULT_SIZE)
                    }
            )
    }

    override suspend fun delete(languageTag: String) {
        deleted += languageTag
        backing.value = backing.value - languageTag
    }

    override suspend fun isDownloaded(languageTag: String): Boolean = backing.value[languageTag] is TranslationModelState.Downloaded

    private companion object {
        /** Roughly what a pack actually measured at on device. */
        const val DEFAULT_SIZE = 45L * 1024 * 1024
    }
}
