package net.kikin.nubecita.data.models

import androidx.compose.runtime.Immutable
import io.github.kikin81.atproto.app.bsky.richtext.Facet
import kotlinx.collections.immutable.ImmutableList

/**
 * What a post card should say about translation, if anything.
 *
 * A sealed sum rather than flat fields because exactly one applies at a time
 * and the combinations are nonsense — "showing a translation and offering to
 * translate" is not a state to reason about. `null` means say nothing at all,
 * which is the case for every post in the reader's own language and every
 * surface that does not translate.
 *
 * Carried as a parameter to the card rather than a field on [PostUi]: a
 * post's declared language is intrinsic to the post, but whether *this*
 * surface has translated it is not. Keeping it out of [PostUi] is what lets
 * post detail, profile, notifications, bookmarks, search, the widgets and the
 * Wear app render byte-identically while the feed gains this.
 */
@Immutable
public sealed interface PostTranslationUi {
    /** The bare BCP-47 subtag the post is in. */
    public val sourceLanguage: String

    /**
     * Not translated, but it could be. The card offers a chip.
     *
     * Reached with automatic translation off as well as on — detection by the
     * post's own declared language costs nothing, so the offer is there for
     * every reader rather than only those who opted in.
     */
    @Immutable
    public data class Offered(
        override val sourceLanguage: String,
    ) : PostTranslationUi

    /**
     * The reader asked, but the language pack needs Wi-Fi.
     *
     * A state of its own, not an error and not silence: a 62MB download on
     * someone's cellular plan is not ours to start, and a chip that does
     * nothing when tapped reads as a broken feature.
     */
    @Immutable
    public data class WaitingForWifi(
        override val sourceLanguage: String,
    ) : PostTranslationUi

    /**
     * A language pack is downloading before anything can be translated.
     *
     * A pack is around 45MB, so this can take a while, and until it lands the
     * chip would otherwise show nothing at all after a tap.
     */
    @Immutable
    public data class Downloading(
        override val sourceLanguage: String,
    ) : PostTranslationUi

    /** Translation in flight; the card shimmers over the body. */
    @Immutable
    public data class InProgress(
        override val sourceLanguage: String,
    ) : PostTranslationUi

    /**
     * Translated, and currently shown in place of the original.
     *
     * [facets] index [text] — they are the reconciled facets, not the post's
     * originals, because translation destroys byte offsets. A kind that could
     * not be reconciled is simply absent here and renders as plain text.
     */
    @Immutable
    public data class Shown(
        override val sourceLanguage: String,
        val text: String,
        val facets: ImmutableList<Facet>,
    ) : PostTranslationUi

    /**
     * Translated, but the reader asked to see the original again.
     *
     * Distinct from [Offered] so the card can offer "Show translation" without
     * re-running anything, and so the reader's choice survives the post
     * scrolling out of view and back.
     */
    @Immutable
    public data class OriginalShown(
        override val sourceLanguage: String,
    ) : PostTranslationUi
}
