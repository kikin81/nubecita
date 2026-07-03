package net.kikin.nubecita.feature.composer.impl.state

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.core.posting.ComposerAttachment
import net.kikin.nubecita.core.posting.PostAudience
import net.kikin.nubecita.data.models.KlipyMediaUi

/**
 * Flat, UI-ready state for the unified composer screen.
 *
 * Per the repo's MVI conventions:
 * - **Independent flags** ([graphemeCount], [isOverLimit],
 *   [attachments]) live as flat fields. Composables read them
 *   directly without a `when` on a sum-type wrapper.
 * - **Mutually-exclusive lifecycles** ([submitStatus],
 *   [replyParentLoad], [typeahead]) are sealed status sums — the
 *   type system forbids invalid combinations like "Submitting AND
 *   Error".
 *
 * No `Async<T>` / `Result<T>` wrappers. No remote-data sum at the UI
 * boundary. Concrete fields the composable can read with zero
 * branching.
 *
 * **Text ownership exception.** The composer's text and selection
 * live in `ComposerViewModel.textFieldState` (a Compose
 * `TextFieldState`), NOT on this state object. This is a deliberate,
 * editor-only departure from the MVI baseline — see
 * `ComposerViewModel`'s Kdoc and `openspec/changes/add-composer-mention-typeahead/design.md`
 * for the rationale (round-trip elimination → no IME cursor lag).
 * `graphemeCount` and `isOverLimit` ARE projected onto this state
 * object so existing UI gates (`canPost`, character counter) keep
 * reading them via the standard MVI shape; both are updated by the
 * VM's `snapshotFlow` collector observing the field.
 *
 * @property graphemeCount Grapheme-cluster count of the field's
 *   current text — what the counter renders. Recomputed on every
 *   `TextFieldState` snapshot emission using
 *   `java.text.BreakIterator.getCharacterInstance()`. NOT
 *   `text.length` (UTF-16 code units) and NOT `text.codePointCount`
 *   (Unicode codepoints) — both miscount emoji ZWJ sequences.
 * @property isOverLimit `true` iff [graphemeCount] > 300 (the AT
 *   Protocol post-text limit). Derived from [graphemeCount]; the
 *   collector keeps both consistent on every update.
 * @property attachments Up to 4 image attachments. Defaults to empty.
 *   Cap enforced by the reducer (the picker also caps via
 *   `maxItems`).
 * @property replyToUri Copied from `ComposerRoute.replyToUri` at VM
 *   init time. `null` in new-post mode; non-null in reply mode.
 *   Stored as `String` (matching the NavKey's String shape) — the
 *   eventual `ParentFetchSource` lifts to `AtUri` at the call site
 *   to the atproto runtime.
 * @property replyParentLoad Reply-mode parent-fetch lifecycle.
 *   `null` in new-post mode; non-null and starts in
 *   `ParentLoadStatus.Loading` in reply mode, transitioning to
 *   `Loaded` or `Failed`.
 * @property submitStatus Submit-flow lifecycle. Defaults to
 *   `ComposerSubmitStatus.Idle`.
 * @property typeahead `@`-mention typeahead lifecycle. Defaults to
 *   `TypeaheadStatus.Idle`. Independent of [submitStatus] and
 *   [replyParentLoad] — the user can be typing into the field while
 *   the parent post is still loading, etc. Driven by the VM's
 *   `snapshotFlow` collector; transitions occur outside the
 *   `handleEvent` reducer path.
 */
internal data class ComposerState(
    val graphemeCount: Int = 0,
    val isOverLimit: Boolean = false,
    val attachments: ImmutableList<ComposerAttachment> = persistentListOf(),
    val replyToUri: String? = null,
    val replyParentLoad: ParentLoadStatus? = null,
    /**
     * Copied from `ComposerRoute.quotePostUri` at VM init, or set when the user
     * pastes a post link. `null` when not quoting; non-null while a quote is
     * attached. Orthogonal to [replyToUri] — both may be non-null (reply + quote).
     * Cleared by `RemoveQuote` (the dismiss affordance).
     */
    val quotePostUri: String? = null,
    /**
     * Quote-mode quoted-post-fetch lifecycle. `null` when not quoting; non-null
     * and starts in `QuoteLoadStatus.Loading`, transitioning to `Loaded` or
     * `Failed`. Submit is gated on `Loaded` (the embed ref needs the CID).
     */
    val quotePostLoad: QuoteLoadStatus? = null,
    /**
     * Per-post BCP-47 language tags chosen by the user via the
     * language chip + picker. `null` means "user has not touched the
     * picker; let `PostingRepository.createPost` derive the device-
     * locale default per `nubecita-wtq.12`'s contract." A non-null
     * list (including the empty list) is an explicit caller override
     * passed through verbatim.
     *
     * Typed as [ImmutableList] for the same reason [attachments] is —
     * Compose stability + defense against post-set mutation by a
     * caller still holding a reference to the original list. The VM's
     * reducer for `LanguageSelectionConfirmed` normalizes incoming
     * `List<String>` events to `ImmutableList` at the state boundary.
     */
    val selectedLangs: ImmutableList<String>? = null,
    /**
     * Who may reply / quote this (top-level) post. Seeded at VM init from the
     * synced [net.kikin.nubecita.core.posting.PostAudience] default and updated
     * by `AudienceSelectionConfirmed`. Passed to `createPost`; the audience chip
     * reads it for its "Visible to all" / "Interaction limited" label and is
     * hidden entirely in reply mode ([replyToUri] non-null).
     */
    val audience: PostAudience = PostAudience.DEFAULT,
    val submitStatus: ComposerSubmitStatus = ComposerSubmitStatus.Idle,
    val typeahead: TypeaheadStatus = TypeaheadStatus.Idle,
    /**
     * Index of the attachment whose alt text is being edited, or `null` when the
     * alt editor is closed. A non-null value swaps the composer body for the
     * per-photo alt editor layer (a paged editor opened at this index). The
     * editor and the composer body are mutually exclusive, so the attachment
     * row's mutations (add / remove / reorder) never race the editor.
     */
    val altEditTarget: Int? = null,
    /**
     * External link-preview card lifecycle. [ExternalLinkStatus.Idle] when there's
     * no card. Set to [ExternalLinkStatus.Loading] when the first eligible pasted
     * URL is detected, then [ExternalLinkStatus.Loaded] once CardyB resolves a
     * preview. Mutually exclusive with [attachments] (images win); may coexist
     * with a quote. Never blocks submit.
     */
    val externalLink: ExternalLinkStatus = ExternalLinkStatus.Idle,
    /**
     * A KLIPY GIF/sticker the user picked, or `null`. A GIF occupies the single
     * external-embed slot: it's **mutually exclusive with [attachments]** (the
     * add-image affordance and the GIF chip disable each other) and suppresses
     * the auto-detected [externalLink] card. At submit it's built into an
     * `app.bsky.embed.external` via [KlipyMediaUi.toExternalEmbedUri]. Cleared by
     * `RemoveGif`.
     */
    val pickedGif: KlipyMediaUi? = null,
) : UiState

/**
 * True when this is a gallery post (more than 4 images, emitted as
 * `app.bsky.embed.gallery`) and at least one image still has blank alt text.
 * Galleries require a description on every photo; images embeds (≤4) do not.
 * Drives both the submit gate (`canSubmit` / `canPost`) and the "needs alt" hint.
 * Demoting below 5 images (or describing the last one) flips this to false.
 */
internal val ComposerState.isGalleryMissingAlt: Boolean
    get() = attachments.size > GALLERY_ALT_REQUIRED_ABOVE && attachments.any { it.alt.isBlank() }

/** Above this image count, a post is a gallery and every photo must have alt text. */
internal const val GALLERY_ALT_REQUIRED_ABOVE = 4
