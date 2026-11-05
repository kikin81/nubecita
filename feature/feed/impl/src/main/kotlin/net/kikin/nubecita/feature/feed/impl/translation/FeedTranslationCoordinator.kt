package net.kikin.nubecita.feature.feed.impl.translation

import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.TranslateBatch
import net.kikin.nubecita.core.analytics.TranslateError
import net.kikin.nubecita.core.analytics.TranslatePost
import net.kikin.nubecita.core.analytics.TranslationDownloadOutcome
import net.kikin.nubecita.core.analytics.TranslationErrorReason
import net.kikin.nubecita.core.analytics.TranslationModelDownload
import net.kikin.nubecita.core.analytics.TranslationModelReadiness
import net.kikin.nubecita.core.common.language.LanguageTags
import net.kikin.nubecita.core.preferences.ReaderLanguages
import net.kikin.nubecita.core.preferences.TranslationPreferences
import net.kikin.nubecita.core.translation.LanguageIdentifier
import net.kikin.nubecita.core.translation.TranslationEngine
import net.kikin.nubecita.core.translation.TranslationModelRepository
import net.kikin.nubecita.core.translation.restoreFacets
import net.kikin.nubecita.data.models.PostTranslationUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.TranslationModelState
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides what a post's translation state should be, and produces it.
 *
 * Injected as a whole rather than as five collaborators so the feed's
 * composable layer holds one reference and the decision order lives in one
 * readable place.
 */
@Singleton
class FeedTranslationCoordinator
    @Inject
    constructor(
        private val engine: TranslationEngine,
        private val identifier: LanguageIdentifier,
        private val models: TranslationModelRepository,
        private val preferences: TranslationPreferences,
        private val readerLanguages: ReaderLanguages,
        private val analytics: AnalyticsClient,
    ) {
        /**
         * Resolve [post] and report each state it passes through via [emit].
         *
         * [userInitiated] is the difference between "this reader has opted
         * into this language" and "they just tapped the chip on this one
         * post". Both translate; only the first happens on its own.
         */
        suspend fun resolve(
            post: PostUi,
            userInitiated: Boolean,
            emit: (PostTranslationUi) -> Unit,
        ) {
            val reader = readerLanguages.languages.first()
            val target = readerLanguages.targetLanguage.first()
            val enabled = preferences.autoTranslateEnabled.first()

            val source = detectSource(post, reader, enabled) ?: return
            if (!engine.supports(source, target)) return

            val auto = preferences.autoTranslateLanguages.first()
            val wanted = userInitiated || (enabled && LanguageTags.matchesAny(source, auto))
            if (!wanted) {
                emit(PostTranslationUi.Offered(source))
                return
            }

            var readiness = TranslationModelReadiness.Ready
            if (!models.isDownloaded(source)) {
                models.request(source)
                val state = models.states.first()[source]
                if (state is TranslationModelState.WaitingForUnmeteredNetwork) {
                    // Not an error and not silence. A 62MB download is not ours
                    // to start on someone's cellular plan, and a chip that does
                    // nothing when tapped reads as a broken feature.
                    logTap(userInitiated, source, TranslationModelReadiness.WaitingForWifi)
                    analytics.log(TranslationModelDownload(TranslationDownloadOutcome.DeferredWifi, source))
                    emit(PostTranslationUi.WaitingForWifi(source))
                    return
                }
                // The download outcome is read here rather than inside the
                // repository so that :core:translation stays free of an
                // analytics dependency — the seam the design doc asks for.
                // Everything needed is already observable from this side.
                if (!models.isDownloaded(source)) {
                    logTap(userInitiated, source, TranslationModelReadiness.Downloading)
                    analytics.log(TranslationModelDownload(TranslationDownloadOutcome.Failed, source))
                    analytics.log(TranslateError(TranslationErrorReason.ModelUnavailable, source))
                    emit(PostTranslationUi.Offered(source))
                    return
                }
                analytics.log(TranslationModelDownload(TranslationDownloadOutcome.Completed, source))
                readiness = TranslationModelReadiness.Downloading
            }
            logTap(userInitiated, source, readiness)

            emit(PostTranslationUi.InProgress(source))
            val translated =
                engine
                    .translate(post.text, source, target)
                    .onFailure { Timber.w(it, "Translation failed for ${post.id}") }
                    .getOrElse {
                        // Showing the original is always a correct thing to
                        // show, so a failure falls back to the offer rather
                        // than to an error state.
                        analytics.log(TranslateError(TranslationErrorReason.TranslateFailed, source))
                        emit(PostTranslationUi.Offered(source))
                        return
                    }
            if (translated.isBlank() && post.text.isNotBlank()) {
                // The engine returned, so nothing threw, but there is nothing
                // to show. Indistinguishable from success on device: the body
                // would simply go empty.
                analytics.log(TranslateError(TranslationErrorReason.EmptyResult, source))
                emit(PostTranslationUi.Offered(source))
                return
            }

            val restored = restoreFacets(post.text, translated, post.facets)
            emit(
                PostTranslationUi.Shown(
                    sourceLanguage = source,
                    text = restored.text,
                    facets = restored.facets.toImmutableList(),
                ),
            )
        }

        /**
         * Report a chip tap, and only a chip tap.
         *
         * The automatic pass is excluded deliberately: it is reported in
         * aggregate by [logBatch]. One reader with auto-translate on would
         * otherwise out-count a hundred readers who tapped, and the headline
         * number this event exists to answer — how many people want this —
         * would stop meaning anything.
         */
        private fun logTap(
            userInitiated: Boolean,
            source: String,
            readiness: TranslationModelReadiness,
        ) {
            if (userInitiated) analytics.log(TranslatePost(source, readiness))
        }

        /**
         * Report one settle's worth of automatic translations.
         *
         * Called by [FeedTranslationState], which is the only thing that knows
         * where a settle begins and ends. [sourceLanguage] is the first of the
         * batch: a settle is a single screenful, so a mixed-language one is
         * rare enough that a representative beats inventing a param for it.
         */
        fun logBatch(
            translatedCount: Int,
            sourceLanguage: String,
        ) {
            analytics.log(TranslateBatch(translatedCount, sourceLanguage))
        }

        /**
         * The language [post] is in, or null if it needs no translating.
         *
         * Declaration first, identification second, neither as a gate:
         *
         * - `langs` is author-declared and frequently absent, so gating on it
         *   would skip exactly the unlabelled foreign post a reader most needs
         *   help with;
         * - identification alone reads the motivating post — a German greeting
         *   followed by nine English hashtags — as **English**, and skips it.
         *
         * With automatic translation off, only the declaration is consulted.
         * That is a string comparison with no model loaded and no inference, so
         * the chip is discoverable at zero cost to a reader who never uses it.
         */
        private suspend fun detectSource(
            post: PostUi,
            readerLanguages: List<String>,
            identificationAllowed: Boolean,
        ): String? {
            val declared = LanguageTags.normalizeAll(post.langs)
            if (declared.isNotEmpty()) {
                // Any overlap means the reader can read it. A post declaring
                // both `ja` and `en` to an English reader is not foreign.
                if (declared.any { LanguageTags.matchesAny(it, readerLanguages) }) return null
                return declared.first()
            }
            if (!identificationAllowed) return null
            val identified = identifier.identify(post.text) ?: return null
            if (LanguageTags.matchesAny(identified, readerLanguages)) return null
            return identified
        }
    }
