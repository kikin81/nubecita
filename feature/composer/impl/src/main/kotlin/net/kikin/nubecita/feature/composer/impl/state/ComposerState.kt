package net.kikin.nubecita.feature.composer.impl.state

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.core.posting.ComposerAttachment
import net.kikin.nubecita.core.posting.PostAudience

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
) : UiState
