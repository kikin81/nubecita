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
 * Previews and screenshot fixtures use [previewPostTranslations].
 */
@Stable
interface PostTranslationsHandle {
    /** The language being offered as automatic, if any. */
    val autoOffer: String?

    /** What the card for [post] should say about translation, or null. */
    fun stateOf(post: PostUi): PostTranslationUi?

    /** Toggle between the translation and the original for [postUri]. */
    fun toggleOriginal(postUri: String)

    /** The reader tapped the chip on [post]. */
    fun translate(post: PostUi)

    /** The reader accepted the automatic-translation offer. */
    fun acceptAutoOffer()

    /** The reader dismissed the offer. */
    fun declineAutoOffer()
}

/**
 * A handle with fixed per-post states and no behaviour, for previews and
 * screenshot fixtures that need to render translated, offered or in-progress
 * posts without a store. [states] is keyed by post uri.
 */
fun previewPostTranslations(states: Map<String, PostTranslationUi>): PostTranslationsHandle = FixedPostTranslations(states)

@Stable
private class FixedPostTranslations(
    private val states: Map<String, PostTranslationUi>,
) : PostTranslationsHandle {
    override val autoOffer: String? = null

    override fun stateOf(post: PostUi): PostTranslationUi? = states[post.id]

    override fun toggleOriginal(postUri: String) = Unit

    override fun translate(post: PostUi) = Unit

    override fun acceptAutoOffer() = Unit

    override fun declineAutoOffer() = Unit
}

/** The [PostTranslationsHandle] every real screen gets, over the shared store. */
@Stable
internal class StorePostTranslationsHandle(
    private val store: PostTranslations,
    private val scope: CoroutineScope,
) : PostTranslationsHandle {
    override var autoOffer: String? by mutableStateOf(null)
        private set

    override fun stateOf(post: PostUi): PostTranslationUi? = store.stateOf(post)

    override fun toggleOriginal(postUri: String) = store.toggleOriginal(postUri)

    override fun translate(post: PostUi) {
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

    override fun acceptAutoOffer() {
        val source = autoOffer ?: return
        autoOffer = null
        scope.launch { guarded("accepting automatic translation for $source") { store.acceptAuto(source) } }
    }

    override fun declineAutoOffer() {
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
