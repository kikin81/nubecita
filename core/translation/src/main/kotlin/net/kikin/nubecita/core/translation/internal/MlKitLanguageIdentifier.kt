package net.kikin.nubecita.core.translation.internal

import com.google.mlkit.nl.languageid.LanguageIdentification
import kotlinx.coroutines.tasks.await
import net.kikin.nubecita.core.common.coroutines.runCatchingCancellable
import net.kikin.nubecita.core.common.language.LanguageTags
import net.kikin.nubecita.core.translation.LanguageIdentifier
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import com.google.mlkit.nl.languageid.LanguageIdentifier as MlKitIdentifier

/**
 * ML Kit behind [LanguageIdentifier].
 *
 * The client is created on first use rather than at construction, so a reader
 * who never turns translation on never loads the identification model. That
 * laziness is the whole reason the translate chip can be shown to everyone at
 * no cost: with the feature off, detection is a comparison against the post's
 * declared `langs` and nothing here is ever touched.
 */
@Singleton
internal class MlKitLanguageIdentifier
    @Inject
    constructor() : LanguageIdentifier {
        private val lock = Any()

        @Volatile
        private var client: MlKitIdentifier? = null

        override suspend fun identify(text: String): String? {
            if (text.isBlank()) return null
            val result =
                runCatchingCancellable { client().identifyLanguage(text).await() }
                    .onFailure { Timber.w(it, "Language identification failed") }
                    .getOrNull()
                    ?: return null
            // ML Kit spells "I don't know" as the ISO 639-2 undetermined code.
            // Passing it on as a language would have callers trying to
            // translate from a language that does not exist.
            return if (result == UNDETERMINED) null else LanguageTags.normalize(result)
        }

        /**
         * `synchronized`, not an `AtomicReference` compare-and-set: the CAS
         * lambda is retried under contention, and each retry would build
         * another ML Kit client that nothing ever closes.
         */
        private fun client(): MlKitIdentifier {
            client?.let { return it }
            return synchronized(lock) {
                client ?: LanguageIdentification.getClient().also { client = it }
            }
        }

        override fun release() {
            val open = synchronized(lock) { client.also { client = null } }
            if (open != null) {
                runCatching { open.close() }
                    .onFailure { Timber.w(it, "Failed to close language identifier") }
            }
        }

        private companion object {
            const val UNDETERMINED = "und"
        }
    }
