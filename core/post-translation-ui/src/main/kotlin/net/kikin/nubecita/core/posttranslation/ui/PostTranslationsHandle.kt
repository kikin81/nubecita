package net.kikin.nubecita.core.posttranslation.ui

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.coroutines.runCatchingCancellable
import net.kikin.nubecita.core.posttranslation.PostTranslations
import net.kikin.nubecita.data.models.PostTranslationUi
import net.kikin.nubecita.data.models.PostUi
import timber.log.Timber

/**
 * One screen's view of the shared [PostTranslations] store.
 *
 * The translations themselves are app-wide; what belongs to the screen is the
 * auto-translate offer. It follows a tap the reader made *here*, so it is
 * raised here and outlives the post that triggered it scrolling away.
 *
 * Get one from [rememberPostTranslations]; pass [stateOf], [translate] and
 * [toggleOriginal] to each `PostCard`, and place [AutoTranslateOffer] once.
 */
@Stable
class PostTranslationsHandle internal constructor(
    private val store: PostTranslations,
    private val scope: CoroutineScope,
) {
    /** The language being offered as automatic, if any. */
    var autoOffer: String? by mutableStateOf(null)
        private set

    /** What the card for [post] should say about translation, or null. */
    fun stateOf(post: PostUi): PostTranslationUi? = store.stateOf(post)

    /** Toggle between the translation and the original for [postUri]. */
    fun toggleOriginal(postUri: String) = store.toggleOriginal(postUri)

    /** The reader tapped the chip on [post]. */
    fun translate(post: PostUi) {
        val pending = store.translate(post)
        scope.launch {
            val source = pending.await() ?: return@launch
            // Only after the translation actually landed. Offering to do this
            // for every post in a language, before the reader has seen what
            // one translation looks like, asks them to commit to something
            // they can't yet judge.
            val offer =
                runCatchingCancellable { store.shouldOfferAuto(source) }
                    .onFailure { error -> Timber.w(error, "Couldn't read whether %s was offered", source) }
                    .getOrDefault(false)
            if (offer) autoOffer = source
        }
    }

    /** The reader accepted the automatic-translation offer. */
    fun acceptAutoOffer() {
        val source = autoOffer ?: return
        autoOffer = null
        scope.launch { guarded("accepting automatic translation for $source") { store.acceptAuto(source) } }
    }

    /** The reader dismissed the offer. */
    fun declineAutoOffer() {
        val source = autoOffer ?: return
        autoOffer = null
        scope.launch { guarded("declining automatic translation for $source") { store.declineAuto(source) } }
    }

    /**
     * Run a preference read or write without letting a failure take the
     * screen down.
     *
     * [scope] is a `rememberCoroutineScope`, whose job is a plain `Job`, not a
     * `SupervisorJob`: an escaping throw cancels it, and with it every later
     * chip tap on the screen. A failed write means the reader's choice didn't
     * stick, which is worth a log, not disabling translation until they leave.
     */
    private suspend fun guarded(
        what: String,
        block: suspend () -> Unit,
    ) {
        runCatchingCancellable { block() }
            .onFailure { error -> Timber.w(error, "Translation preference write failed: %s", what) }
    }
}
