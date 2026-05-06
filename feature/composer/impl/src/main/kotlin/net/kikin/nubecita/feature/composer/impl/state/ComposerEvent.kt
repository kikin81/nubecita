package net.kikin.nubecita.feature.composer.impl.state

import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.posting.ActorTypeaheadUi
import net.kikin.nubecita.core.posting.ComposerAttachment

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
sealed interface ComposerEvent : UiEvent {
    /**
     * The picker returned URIs. Reducer appends up to the 4-image
     * cap; URIs beyond the cap are silently dropped (the picker also
     * caps via `maxItems = 4 - state.attachments.size` so this is
     * defensive depth).
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
        val actor: ActorTypeaheadUi,
    ) : ComposerEvent
}
