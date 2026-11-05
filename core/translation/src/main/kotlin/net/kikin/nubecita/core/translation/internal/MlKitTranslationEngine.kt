package net.kikin.nubecita.core.translation.internal

import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.common.coroutines.DefaultDispatcher
import net.kikin.nubecita.core.common.coroutines.runCatchingCancellable
import net.kikin.nubecita.core.common.language.LanguageTags
import net.kikin.nubecita.core.translation.TranslationEngine
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ML Kit behind [TranslationEngine].
 *
 * Holds one translator per language pair, created lazily and **closed when
 * the app backgrounds**. That last part is not housekeeping: with five
 * translators loaded the process reached roughly 400MB RSS in measurement,
 * which is enough to make the app an attractive target for the low-memory
 * killer while it is sitting in the background doing nothing.
 */
@Singleton
internal class MlKitTranslationEngine
    @Inject
    constructor(
        @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
    ) : TranslationEngine {
        /**
         * One translator per ordered language pair.
         *
         * A `ConcurrentHashMap` rather than a map behind a `Mutex` because
         * [release] has to drain it from the main thread and cannot suspend —
         * mixing a coroutine mutex with a synchronized block over the same map
         * would guard nothing. `computeIfAbsent` is atomic, so two posts
         * settling at once in the same language cannot create (and leak) two
         * translators for it.
         */
        private val translators = ConcurrentHashMap<String, Translator>()

        override fun supports(
            sourceLanguage: String,
            targetLanguage: String,
        ): Boolean = mlKitTag(sourceLanguage) != null && mlKitTag(targetLanguage) != null

        override suspend fun translate(
            text: String,
            sourceLanguage: String,
            targetLanguage: String,
        ): Result<String> {
            if (text.isBlank()) return Result.success(text)
            val source = mlKitTag(sourceLanguage) ?: return Result.failure(UnsupportedLanguage(sourceLanguage))
            val target = mlKitTag(targetLanguage) ?: return Result.failure(UnsupportedLanguage(targetLanguage))
            if (source == target) return Result.success(text)

            return runCatchingCancellable {
                val translator = translator(source, target)
                withContext(defaultDispatcher) { translator.translate(text).await() }
            }.onFailure { error ->
                Timber.w(error, "Translation failed for $sourceLanguage -> $targetLanguage")
            }
        }

        /**
         * The translator for a pair, created once.
         *
         * No `downloadModelIfNeeded` here. Downloading is
         * `TranslationModelRepository`'s job because it is the thing that
         * knows about metered connections and reader consent; an engine that
         * quietly downloaded 62MB on first use would route around all of that.
         * Translating without a pack simply fails, and a failure shows the
         * original.
         */
        private fun translator(
            source: String,
            target: String,
        ): Translator =
            translators.computeIfAbsent("$source->$target") {
                Translation.getClient(
                    TranslatorOptions
                        .Builder()
                        .setSourceLanguage(source)
                        .setTargetLanguage(target)
                        .build(),
                )
            }

        override fun release() {
            // Closing a translator mid-translate is safe: the in-flight task
            // fails, the caller falls back to showing the original, and the
            // next call builds a fresh translator.
            val open = translators.keys.toList().mapNotNull(translators::remove)
            open.forEach { translator ->
                runCatching { translator.close() }
                    .onFailure { Timber.w(it, "Failed to close translator") }
            }
        }

        private fun mlKitTag(language: String): String? = LanguageTags.normalize(language)?.let(TranslateLanguage::fromLanguageTag)

        internal class UnsupportedLanguage(
            languageTag: String,
        ) : IllegalArgumentException("No on-device translation model exists for '$languageTag'")
    }
