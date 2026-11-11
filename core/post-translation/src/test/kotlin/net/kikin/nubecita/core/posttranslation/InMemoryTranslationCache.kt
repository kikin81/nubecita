package net.kikin.nubecita.core.posttranslation

import net.kikin.nubecita.data.models.PostTranslationUi

/** [TranslationCache] over a map, recording every write in order. */
internal class InMemoryTranslationCache(
    initial: Map<Pair<String, String>, PostTranslationUi.Shown> = emptyMap(),
) : TranslationCache {
    private val rows = initial.toMutableMap()
    val writes = mutableListOf<Triple<String, String, PostTranslationUi.Shown>>()
    var reads = 0
        private set

    override suspend fun find(
        postUri: String,
        targetLanguage: String,
    ): PostTranslationUi.Shown? {
        reads++
        return rows[postUri to targetLanguage]
    }

    override suspend fun put(
        postUri: String,
        targetLanguage: String,
        translation: PostTranslationUi.Shown,
    ) {
        writes += Triple(postUri, targetLanguage, translation)
        rows[postUri to targetLanguage] = translation
    }
}
