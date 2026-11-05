package net.kikin.nubecita.core.translation.internal

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import net.kikin.nubecita.core.common.coroutines.runCatchingCancellable
import net.kikin.nubecita.core.common.language.LanguageTags
import net.kikin.nubecita.core.common.network.NetworkStatus
import net.kikin.nubecita.core.translation.TranslationModelRepository
import net.kikin.nubecita.data.models.DownloadedLanguage
import net.kikin.nubecita.data.models.TranslationModelState
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ML Kit's model manager behind [TranslationModelRepository].
 *
 * Two things here are load-bearing rather than incidental.
 *
 * **The metered gate is ours, not the SDK's.** `DownloadConditions` has a
 * `requireWifi()` flag, but a download that simply never completes is
 * indistinguishable from a broken feature. Instead the request is recorded,
 * the language moves to [TranslationModelState.WaitingForUnmeteredNetwork] so
 * the UI can say why nothing is happening, and a collector starts the transfer
 * when the connection becomes unmetered.
 *
 * **Sizes are measured, not quoted.** The documented figure for a pack is
 * around 30MB; on device it is roughly 45MB, and 62MB for Japanese, because
 * the engine stores both directions of a pair. Nothing here reports a constant.
 */
@Singleton
internal class MlKitTranslationModelRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val networkStatus: NetworkStatus,
        @param:ApplicationScope private val scope: CoroutineScope,
    ) : TranslationModelRepository {
        private val modelManager by lazy { RemoteModelManager.getInstance() }
        private val lock = Mutex()
        private val pending = mutableSetOf<String>()
        private val stateMap = MutableStateFlow<Map<String, TranslationModelState>>(emptyMap())

        override val states: Flow<Map<String, TranslationModelState>> = stateMap.asStateFlow()

        override val downloaded: Flow<List<DownloadedLanguage>> =
            stateMap
                .map { states ->
                    states
                        .mapNotNull { (tag, state) ->
                            (state as? TranslationModelState.Downloaded)
                                ?.let { DownloadedLanguage(languageTag = tag, sizeBytes = it.sizeBytes) }
                        }.sortedBy { it.languageTag }
                }.distinctUntilChanged()

        init {
            scope.launch { refreshDownloaded() }
            // Drain the deferred queue the moment the connection stops costing
            // the reader money.
            networkStatus.isMetered
                .distinctUntilChanged()
                .filter { metered -> !metered }
                .onEach { drainPending() }
                .launchIn(scope)
        }

        override suspend fun request(languageTag: String) {
            val tag = LanguageTags.normalize(languageTag) ?: return
            if (TranslateLanguage.fromLanguageTag(tag) == null) return
            if (isDownloaded(tag)) return

            lock.withLock {
                if (!pending.add(tag)) return
            }
            if (isMeteredNow()) {
                setState(tag, TranslationModelState.WaitingForUnmeteredNetwork)
            } else {
                download(tag)
            }
        }

        override suspend fun delete(languageTag: String) {
            val tag = LanguageTags.normalize(languageTag) ?: return
            val model = model(tag) ?: return
            lock.withLock { pending.remove(tag) }
            // Drop the entry outright. `refreshDownloaded` rebuilds only the
            // Downloaded half of the map and deliberately preserves everything
            // else, so a tag deleted while Downloading or waiting for Wi-Fi
            // would otherwise keep that state for the life of the process with
            // nothing left to carry it.
            stateMap.value = stateMap.value - tag
            runCatchingCancellable { modelManager.deleteDownloadedModel(model).await() }
                .onFailure { Timber.w(it, "Failed to delete translation model for $tag") }
            refreshDownloaded()
        }

        override suspend fun isDownloaded(languageTag: String): Boolean {
            val model = model(languageTag) ?: return false
            return runCatchingCancellable { modelManager.isModelDownloaded(model).await() }.getOrDefault(false)
        }

        private suspend fun download(tag: String) {
            val model = model(tag) ?: return
            setState(tag, TranslationModelState.Downloading)
            val outcome =
                runCatchingCancellable {
                    modelManager
                        .download(model, DownloadConditions.Builder().build())
                        .await()
                }
            lock.withLock { pending.remove(tag) }
            if (outcome.isFailure) {
                Timber.w(outcome.exceptionOrNull(), "Translation model download failed for $tag")
                setState(tag, TranslationModelState.NotDownloaded)
                return
            }
            refreshDownloaded()
        }

        private fun drainPending() {
            scope.launch {
                val queued = lock.withLock { pending.toList() }
                queued.forEach { tag -> download(tag) }
            }
        }

        /**
         * Rebuild the state map from what is actually on disk.
         *
         * Asking the model manager rather than trusting an in-memory tally:
         * packs survive process death, and the reader may have cleared app
         * storage from Settings between runs.
         */
        private suspend fun refreshDownloaded() {
            val models =
                runCatchingCancellable { modelManager.getDownloadedModels(TranslateRemoteModel::class.java).await() }
                    .onFailure { Timber.w(it, "Failed to list downloaded translation models") }
                    .getOrNull() ?: return
            val sizes = measureSizes()
            val present =
                models.mapNotNull { model ->
                    val tag = LanguageTags.normalize(model.language) ?: return@mapNotNull null
                    // English is the pivot every pair goes through, so ML Kit
                    // reports it as downloaded as soon as any language is. It
                    // is not a language the reader added and it owns no
                    // directory of its own, so listing it would show a reader
                    // a pack they never asked for and cannot meaningfully
                    // delete.
                    if (tag == PIVOT_LANGUAGE) return@mapNotNull null
                    tag to TranslationModelState.Downloaded(sizeBytes = sizes[tag] ?: 0L)
                }
            stateMap.value =
                stateMap.value
                    .filterValues { it !is TranslationModelState.Downloaded }
                    .plus(present)
        }

        /**
         * On-disk bytes per language, read from ML Kit's own model directory.
         *
         * The layout is `<noBackupFilesDir>/com.google.mlkit.translate.models/<a>_<b>/`,
         * where the directory name is the *pair* and holds both directions —
         * `en_ja` is where the 62MB actually measured for Japanese lives.
         *
         * Every pair pivots through English, so a directory is attributed
         * wholly to its non-English side: that is the language the reader
         * chose to add and the one whose delete button has to free this space.
         * Crediting both halves would have English accumulate the sum of every
         * other language. A directory that does not parse is skipped rather
         * than guessed at, which under-reports instead of mis-reporting.
         */
        private fun measureSizes(): Map<String, Long> {
            val root = File(context.noBackupFilesDir, MODEL_DIR)
            val pairs = root.listFiles()?.filter { it.isDirectory } ?: return emptyMap()
            return buildMap {
                for (dir in pairs) {
                    if (dir.name == TEMP_DIR) continue
                    val parts = dir.name.split('_')
                    if (parts.size != 2) continue
                    val tags = parts.mapNotNull(LanguageTags::normalize)
                    val tag = tags.singleOrNull { it != PIVOT_LANGUAGE } ?: continue
                    val size = dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
                    put(tag, (get(tag) ?: 0L) + size)
                }
            }
        }

        /**
         * Defaults to "metered" when the connection cannot be read.
         *
         * The safe side of the guess: deferring a 62MB download that could
         * have run costs the reader a wait, while starting one that should
         * have waited costs them money.
         */
        private suspend fun isMeteredNow(): Boolean = runCatchingCancellable { networkStatus.isMetered.first() }.getOrDefault(true)

        private fun setState(
            tag: String,
            state: TranslationModelState,
        ) {
            stateMap.value = stateMap.value + (tag to state)
        }

        private fun model(languageTag: String): TranslateRemoteModel? {
            val tag = LanguageTags.normalize(languageTag) ?: return null
            val mlKitTag = TranslateLanguage.fromLanguageTag(tag) ?: return null
            return TranslateRemoteModel.Builder(mlKitTag).build()
        }

        private companion object {
            const val MODEL_DIR = "com.google.mlkit.translate.models"
            const val TEMP_DIR = "temp"
            const val PIVOT_LANGUAGE = "en"
        }
    }
