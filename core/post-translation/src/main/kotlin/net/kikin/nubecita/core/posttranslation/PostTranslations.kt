package net.kikin.nubecita.core.posttranslation

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import net.kikin.nubecita.core.common.coroutines.MainDispatcher
import net.kikin.nubecita.core.common.coroutines.runCatchingCancellable
import net.kikin.nubecita.core.preferences.ReaderLanguages
import net.kikin.nubecita.core.preferences.TranslationPreferences
import net.kikin.nubecita.core.translation.TranslationModelRepository
import net.kikin.nubecita.data.models.PostTranslationUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.TranslationModelState
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every post's translation state, shared by every surface that shows posts.
 *
 * One answer per post for the whole app: a post translated in the feed opens
 * translated in post detail, and one translated there is translated when the
 * reader goes back. Surfaces adopt it through the Compose helper in
 * `:core:post-translation-ui`, never by holding state of their own.
 *
 * **Snapshot state, read per card.** Cards read their entry during
 * composition. A `SnapshotStateMap` is one state object, so a write
 * invalidates every visible card that read it, not just the card whose entry
 * changed; this is the same map the feed held before, which is what the 120hz
 * measurement was taken against. It is cheap in practice because writes
 * happen only when a list is at rest, and each re-run card's `PostCard` skips
 * because its inputs didn't change. Per-post state holders would narrow the
 * invalidation to one card; that is worth doing only if a measurement shows
 * the re-runs cost frames.
 *
 * **Main-thread confined.** Every field, snapshot or not, is read and written
 * on [main]. The coordinator resolves on the caller's dispatcher and only the
 * engine itself leaves it, so its emissions land here too. That is why there
 * is no lock: there is nothing for two threads to race on.
 *
 * **Work outlives the screen.** Resolution runs on the application scope, so a
 * translation the feed started still lands if the reader opens the post
 * mid-way, and post detail shows it without asking twice. It also means one
 * throwing post can't cancel the others, which is what a screen's plain `Job`
 * used to do.
 *
 * **A process-long store must not go stale.** A screen-scoped map was rebuilt
 * on every visit, which quietly re-resolved everything. Here each thing that
 * visit used to fix has an explicit trigger instead; see [observe].
 */
@Stable
@Singleton
class PostTranslations
    @Inject
    internal constructor(
        private val coordinator: PostTranslationCoordinator,
        private val preferences: TranslationPreferences,
        private val readerLanguages: ReaderLanguages,
        private val models: TranslationModelRepository,
        @param:ApplicationScope private val scope: CoroutineScope,
        @param:MainDispatcher private val main: CoroutineDispatcher,
    ) {
        private val entries = mutableStateMapOf<String, PostTranslationUi>()

        /**
         * Posts the reader switched back to the original.
         *
         * Not part of the [recency] bound: it is a handful of URIs the reader
         * reverted by hand, and evicting one would silently re-translate a
         * post they asked to read in the original.
         */
        private val showOriginal = mutableStateMapOf<String, Boolean>()

        /**
         * Bumped whenever a change invalidates entries, so every surface at
         * rest re-settles and resolves its visible posts again without
         * waiting for a scroll. Read inside the settle signal.
         */
        var generation: Int by mutableIntStateOf(0)
            private set

        private val inFlight = mutableSetOf<String>()

        /**
         * Posts that failed, whether the engine failed or resolution threw.
         *
         * Not a retry list, the opposite: settles skip these, so a post that
         * fails for a persistent reason logs once rather than on every settle.
         * [onSurfaceEntered] clears it, which gives each visit one fresh try,
         * the same budget a screen-scoped map had.
         */
        private val failed = mutableSetOf<String>()

        /**
         * Posts the reader explicitly asked to translate.
         *
         * Remembered so that a post re-resolved later, when the pack it was
         * waiting for arrives, is still treated as the reader's request rather
         * than demoted to an offer.
         */
        private val requestedByReader = mutableSetOf<String>()

        /** Access-ordered keys of [entries], oldest first, for the bound. */
        private val recency = LinkedHashMap<String, Unit>(INITIAL_CAPACITY, LOAD_FACTOR, true)

        /**
         * Which reader-languages snapshot the current entries were made for.
         * A resolution that started under an older one drops its emissions
         * instead of writing a translation into the wrong language.
         */
        private var languagesEpoch = 0

        init {
            observe()
        }

        /**
         * What the card for [post] should say, or null for "say nothing".
         *
         * Called during composition, so it only reads: no recency update, no
         * work. Reading a snapshot map means a translation landing recomposes
         * only this card.
         */
        fun stateOf(post: PostUi): PostTranslationUi? {
            val entry = entries[post.id] ?: return null
            return if (showOriginal[post.id] == true && entry is PostTranslationUi.Shown) {
                PostTranslationUi.OriginalShown(entry.sourceLanguage)
            } else {
                entry
            }
        }

        /** Toggle between the translation and the original for [postUri]. */
        fun toggleOriginal(postUri: String) {
            if (showOriginal[postUri] == true) showOriginal.remove(postUri) else showOriginal[postUri] = true
        }

        /**
         * The reader tapped the chip on [post].
         *
         * Completes with the source language if the post ended up translated,
         * whether by the engine or from the cache, so the surface can decide
         * whether to offer the language as automatic. Null otherwise.
         */
        fun translate(post: PostUi): Deferred<String?> {
            showOriginal.remove(post.id)
            requestedByReader += post.id
            touch(post.id)
            return scope.async(main) {
                when (val outcome = request(post, explicit = true).await()) {
                    is ResolveOutcome.Translated -> outcome.sourceLanguage
                    is ResolveOutcome.Cached -> outcome.sourceLanguage
                    else -> null
                }
            }
        }

        /**
         * Resolve every visible post this store has said nothing about yet.
         *
         * Called only when a list has settled; the gate lives in the Compose
         * helper. Posts that already have a state are skipped, and must be: a
         * post the reader translated by hand is, to an automatic pass, still
         * in a language that isn't on the auto list, so re-resolving it would
         * put the offer straight back over the translation.
         */
        fun onSettled(posts: List<PostUi>) {
            posts.forEach { touch(it.id) }
            val pending = posts.filterNot { post -> post.id in entries || post.id in failed }
            if (pending.isEmpty()) return
            scope.launch(main) {
                // Counted from what each request reports it did, so a post two
                // overlapping settles both asked for is counted once, by the
                // one that did the work, and a cache hit is not counted at all.
                val translated =
                    pending
                        .map { post -> request(post, explicit = false) }
                        .awaitAll()
                        .filterIsInstance<ResolveOutcome.Translated>()
                if (translated.isNotEmpty()) {
                    coordinator.logBatch(translated.size, translated.first().sourceLanguage)
                }
            }
        }

        /**
         * A surface showing posts has just appeared.
         *
         * Gives every failed post one fresh try, which is what rebuilding a
         * screen-scoped map on each visit used to do implicitly.
         */
        fun onSurfaceEntered() {
            failed.forEach { postUri -> entries.remove(postUri) }
            failed.clear()
        }

        /** Whether to offer [source] as automatic after a tap. See the coordinator. */
        suspend fun shouldOfferAuto(source: String): Boolean = coordinator.shouldOfferAuto(source)

        /** The reader accepted automatic translation for [source]. */
        suspend fun acceptAuto(source: String) = coordinator.acceptAuto(source)

        /** The reader declined automatic translation for [source]. */
        suspend fun declineAuto(source: String) = coordinator.declineAuto(source)

        private fun request(
            post: PostUi,
            explicit: Boolean,
        ): Deferred<ResolveOutcome?> =
            scope.async(main) {
                // Re-checked here, not only in onSettled's filter: two settles
                // can both be dispatched before either runs. A chip tap is
                // exempt, because the post it targets already has its offer.
                if (!explicit && post.id in entries) return@async null
                if (!inFlight.add(post.id)) return@async null
                val epoch = languagesEpoch
                try {
                    runCatchingCancellable {
                        coordinator.resolve(
                            post = post,
                            userInitiated = explicit || post.id in requestedByReader,
                            countTap = explicit,
                        ) { state ->
                            if (epoch == languagesEpoch) {
                                entries[post.id] = state
                                touch(post.id)
                            }
                        }
                    }.onFailure { error ->
                        // Contained per post. A preferences read or a model
                        // query can throw, and only the engine call is guarded
                        // inside the coordinator.
                        Timber.w(error, "Translation resolution failed for ${post.id}")
                        failed += post.id
                        coordinator.logUnexpectedFailure()
                    }.getOrNull()
                        ?.also { outcome -> if (outcome is ResolveOutcome.Failed) failed += post.id }
                        ?.takeIf { epoch == languagesEpoch }
                } finally {
                    inFlight.remove(post.id)
                }
            }

        /**
         * The triggers that replace "the screen was rebuilt".
         *
         * Every flow skips its first emission: that is the state the current
         * entries were already made under.
         */
        private fun observe() {
            scope.launch(main) {
                // The reader's languages, which include the device locale and
                // so the target. A change can make a translated post one the
                // reader now reads, or put its translation in the wrong
                // language, so nothing in memory survives it. The cache needs
                // nothing: it is keyed by target, and detection runs before it.
                readerLanguages.languages.distinctUntilChanged().drop(1).collect {
                    languagesEpoch++
                    entries.clear()
                    showOriginal.clear()
                    failed.clear()
                    recency.clear()
                    generation++
                }
            }
            scope.launch(main) {
                // The auto list or master switch. Only offers can change: a
                // translation must not revert because the reader turned
                // automatic translation off.
                combine(preferences.autoTranslateEnabled, preferences.autoTranslateLanguages, ::Pair)
                    .distinctUntilChanged()
                    .drop(1)
                    .collect {
                        entries.entries.removeAll { (_, state) -> state is PostTranslationUi.Offered }
                        failed.clear()
                        generation++
                    }
            }
            scope.launch(main) {
                // A pack arriving. A post parked "waiting for Wi-Fi" would
                // otherwise stay parked for the rest of the process, even
                // after the repository finished the download on Wi-Fi.
                models.states
                    .map { states -> states.filterValues { it is TranslationModelState.Downloaded }.keys }
                    .distinctUntilChanged()
                    .collect(::onPacksDownloaded)
            }
        }

        private var downloadedPacks: Set<String>? = null

        private suspend fun onPacksDownloaded(downloaded: Set<String>) {
            val previous = downloadedPacks
            downloadedPacks = downloaded
            if (previous == null) return
            val arrived = downloaded - previous
            if (arrived.isEmpty()) return
            // A parked post waits on its own pack and the target's. If the
            // target's just arrived, every parked post may be ready.
            val target = readerLanguages.targetLanguage.first()
            val released =
                entries.entries.removeAll { (_, state) ->
                    state is PostTranslationUi.WaitingForWifi &&
                        (target in arrived || state.sourceLanguage in arrived)
                }
            if (released) generation++
        }

        private fun touch(postUri: String) {
            recency[postUri] = Unit
            trim()
        }

        /**
         * Keep [entries] within the bound, least recently used first, and
         * [recency] from outgrowing it.
         *
         * An evicted translation is not lost: it is in the cache, and comes
         * back from there on the post's next settle. [recency] also records
         * settled posts that never get an entry (every post in the reader's
         * own language), so it is trimmed of those, oldest first, or it would
         * grow with every post the reader scrolls past.
         */
        private fun trim() {
            val overflow = entries.size - CAPACITY
            if (overflow > 0) {
                recency.keys
                    .asSequence()
                    .filter { it in entries && it !in inFlight }
                    .take(overflow)
                    .toList()
                    .forEach { postUri ->
                        entries.remove(postUri)
                        recency.remove(postUri)
                    }
            }
            val keys = recency.keys.iterator()
            while (recency.size > CAPACITY && keys.hasNext()) {
                if (keys.next() !in entries) keys.remove()
            }
        }

        private companion object {
            /** Matches the cache's own bound, `TranslationDao.DEFAULT_CAPACITY`. */
            const val CAPACITY = 500
            const val INITIAL_CAPACITY = 64
            const val LOAD_FACTOR = 0.75f
        }
    }
