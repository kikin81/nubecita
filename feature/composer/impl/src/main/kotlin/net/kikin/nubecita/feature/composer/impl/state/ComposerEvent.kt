package net.kikin.nubecita.feature.composer.impl.state

import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.posting.ComposerAttachment

/**
 * Inbound UI intents the composer screen dispatches into the VM.
 * Each event maps to a single reducer or async-launched flow inside
 * `ComposerViewModel.handleEvent`.
 */
sealed interface ComposerEvent : UiEvent {
    /**
     * The text field's value changed. Reducer recomputes
     * [ComposerState.graphemeCount] and [ComposerState.isOverLimit].
     */
    data class TextChanged(
        val text: String,
    ) : ComposerEvent

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
}
