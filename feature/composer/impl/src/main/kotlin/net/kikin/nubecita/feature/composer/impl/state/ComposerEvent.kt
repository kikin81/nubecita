package net.kikin.nubecita.feature.composer.impl.state

import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.posting.ComposerAttachment
import net.kikin.nubecita.core.posting.PostAudience
import net.kikin.nubecita.data.models.ActorUi

/**
 * Inbound UI intents the composer screen dispatches into the VM.
 * Each event maps to a single reducer or async-launched flow inside
 * `ComposerViewModel.handleEvent`.
 *
 * **Note** — text input does NOT flow through this surface. The
 * composer's text and selection live in `ComposerViewModel.textFieldState`
 * (a Compose `TextFieldState`) and the VM observes them via
 * `snapshotFlow`. There is no `TextChanged(text)` event because the
 * IME never round-trips through the reducer — see `ComposerViewModel`'s
 * Kdoc for the rationale.
 */
internal sealed interface ComposerEvent : UiEvent {
    /**
     * The picker returned URIs. Reducer appends up to the
     * `MAX_ATTACHMENTS` cap; URIs beyond the cap are silently dropped
     * (the picker also caps via `maxItems = MAX_ATTACHMENTS -
     * state.attachments.size` so this is defensive depth).
     */
    data class AddAttachments(
        val attachments: List<ComposerAttachment>,
    ) : ComposerEvent

    /**
     * The user tapped the X icon on attachment chip [index]. Reducer
     * removes that attachment and shifts the rest down.
     */
    data class RemoveAttachment(
        val index: Int,
    ) : ComposerEvent

    /**
     * The user dragged the attachment at [from] to position [to]
     * (drag-to-reorder). Reducer moves that attachment, preserving the
     * others' relative order — post order is list order. Emitted once per
     * single-step crossing during a drag.
     */
    data class MoveAttachment(
        val from: Int,
        val to: Int,
    ) : ComposerEvent

    /**
     * The user tapped attachment chip [index] to describe it. Reducer opens
     * the per-photo alt editor focused on that index ([ComposerState.altEditTarget]).
     */
    data class OpenAltEditor(
        val index: Int,
    ) : ComposerEvent

    /** The user dismissed the alt editor (back/done). Reducer clears `altEditTarget`. */
    data object CloseAltEditor : ComposerEvent

    /**
     * The user edited the alt text for the photo at [index] in the editor.
     * Reducer writes [text] onto that attachment's `alt`.
     */
    data class SetAltText(
        val index: Int,
        val text: String,
    ) : ComposerEvent

    /**
     * The user tapped Post. Triggers the submit flow: validate (text
     * non-blank or attachments non-empty, not over limit, reply
     * parent loaded if reply-mode), transition to
     * `ComposerSubmitStatus.Submitting`, call `PostingRepository`,
     * transition to `Success` or `Error`.
     */
    data object Submit : ComposerEvent

    /**
     * In reply mode after a `ParentLoadStatus.Failed`, the user
     * tapped the inline retry tile. Re-launches the parent fetch.
     */
    data object RetryParentLoad : ComposerEvent

    /**
     * In quote mode after a `QuoteLoadStatus.Failed`, the user tapped the inline
     * retry tile. Re-launches the quote fetch.
     */
    data object RetryQuoteLoad : ComposerEvent

    /**
     * The user tapped the dismiss (✕) on the quote card. Reducer clears
     * `quotePostUri` + `quotePostLoad`, detaching the quote without touching the
     * reply context or the entered text.
     */
    data object RemoveQuote : ComposerEvent

    /**
     * The user tapped the dismiss (✕) on the link-preview card. Reducer clears
     * `externalLink` to [net.kikin.nubecita.feature.composer.impl.state.ExternalLinkStatus.Idle]
     * and cancels any in-flight fetch. The URL stays memoized so it does not
     * re-pop while it remains in the text (a manual dismiss, unlike the
     * non-memoizing clear when images are added).
     */
    data object RemoveExternalLink : ComposerEvent

    /**
     * The user tapped a row in the `@`-mention typeahead dropdown.
     * Reducer atomically replaces the active `@`-token substring in
     * the field's text with `@<actor.handle> ` (trailing space) and
     * places the cursor at the end of the insertion. The next
     * `snapshotFlow` emission sees no active token (whitespace
     * boundary) and drives `state.typeahead` back to
     * `TypeaheadStatus.Idle`.
     *
     * No-op if the active `@`-position can't be re-located at
     * dispatch time (concurrent edit raced the click) — defends
     * against corrupting the text with a stale insertion.
     */
    data class TypeaheadResultClicked(
        val actor: ActorUi,
    ) : ComposerEvent

    /**
     * Dispatched by the language picker when the user taps `Done` after
     * choosing 0..3 BCP-47 tags. The reducer assigns `tags` to
     * `ComposerState.selectedLangs`, defensively no-op'ing when
     * `tags.size > 3` (the picker UI also enforces the cap by
     * disabling unchecked checkboxes once 3 are selected).
     *
     * `tags = emptyList()` is honored — it means "user explicitly
     * cleared all languages", surfaced to `PostingRepository.createPost`
     * as `langs = emptyList()`, which the repository serializes as the
     * `langs` field omitted entirely (per `nubecita-wtq.12`'s contract).
     */
    data class LanguageSelectionConfirmed(
        val tags: List<String>,
    ) : ComposerEvent

    /**
     * Dispatched by the audience picker when the user taps `Done`. The reducer
     * assigns [audience] to `ComposerState.audience`; when [saveAsDefault] is
     * true it also persists it as the synced account default (a failed save
     * surfaces a non-blocking error effect — the local selection still stands).
     */
    data class AudienceSelectionConfirmed(
        val audience: PostAudience,
        val saveAsDefault: Boolean,
    ) : ComposerEvent
}
