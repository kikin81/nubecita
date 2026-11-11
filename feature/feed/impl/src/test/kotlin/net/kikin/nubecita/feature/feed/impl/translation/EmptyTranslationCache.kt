package net.kikin.nubecita.feature.feed.impl.translation

import net.kikin.nubecita.core.posttranslation.TranslationCache
import net.kikin.nubecita.data.models.PostTranslationUi

/** A cache that never hits: these tests are about the feed's state, not the cache. */
internal object EmptyTranslationCache : TranslationCache {
    override suspend fun find(
        postUri: String,
        targetLanguage: String,
    ): PostTranslationUi.Shown? = null

    override suspend fun put(
        postUri: String,
        targetLanguage: String,
        translation: PostTranslationUi.Shown,
    ) = Unit
}
