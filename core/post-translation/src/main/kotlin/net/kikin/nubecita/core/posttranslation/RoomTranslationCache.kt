package net.kikin.nubecita.core.posttranslation

import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.core.common.coroutines.runCatchingCancellable
import net.kikin.nubecita.core.database.dao.TranslationDao
import net.kikin.nubecita.core.database.model.TranslationEntity
import net.kikin.nubecita.data.models.PostTranslationUi
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Clock

/**
 * [TranslationCache] over the `post_translation` table.
 *
 * Every failure is swallowed into a miss or a no-op, per the cache's contract:
 * a row that won't decode, after a model change say, should cost one
 * re-translation, not a crash or a post stuck untranslated.
 */
internal class RoomTranslationCache
    @Inject
    constructor(
        private val dao: TranslationDao,
    ) : TranslationCache {
        override suspend fun find(
            postUri: String,
            targetLanguage: String,
        ): PostTranslationUi.Shown? =
            runCatchingCancellable {
                dao.find(postUri, targetLanguage)?.let { row ->
                    PostTranslationUi.Shown(
                        sourceLanguage = row.sourceLanguage,
                        text = row.translatedText,
                        facets = FacetsCodec.decode(row.facetsJson).toImmutableList(),
                    )
                }
            }.onFailure { Timber.w(it, "Translation cache read failed for $postUri") }
                .getOrNull()

        override suspend fun put(
            postUri: String,
            targetLanguage: String,
            translation: PostTranslationUi.Shown,
        ) {
            runCatchingCancellable {
                dao.upsertAndTrim(
                    TranslationEntity(
                        postUri = postUri,
                        targetLanguage = targetLanguage,
                        sourceLanguage = translation.sourceLanguage,
                        translatedText = translation.text,
                        facetsJson = FacetsCodec.encode(translation.facets),
                        createdAt = Clock.System.now(),
                    ),
                )
            }.onFailure { Timber.w(it, "Translation cache write failed for $postUri") }
        }
    }
