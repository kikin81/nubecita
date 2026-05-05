package net.kikin.nubecita.feature.composer.impl.state

import net.kikin.nubecita.core.posting.ComposerError

/**
 * Mutually-exclusive submission lifecycle for the composer.
 *
 * Modeled as a sealed interface — per the repo's MVI rule for
 * mutually-exclusive view modes — so the type system forbids invalid
 * combinations like "submitting + error". Transitions are documented on
 * the spec for `feature-composer`'s "Submission lifecycle is modeled
 * as a sealed status sum" requirement; reducer enforces them.
 *
 * Distinct from `ParentLoadStatus`: that one's for the reply-mode
 * parent fetch lifecycle, this is for the post-submit lifecycle.
 * Both lifecycles can be in flight simultaneously (parent loads
 * while user types, then user hits Submit before parent has
 * loaded — the Submit reducer is the gate that enforces "must be
 * Loaded before Submitting").
 */
sealed interface ComposerSubmitStatus {
    /** Initial / not-yet-submitting / between-error-and-retry. */
    data object Idle : ComposerSubmitStatus

    /** The submit is in flight (uploading blobs and/or creating record). */
    data object Submitting : ComposerSubmitStatus

    /**
     * The submit completed successfully. Terminal — the screen
     * Composable consumes this and dismisses; no further `Submit`
     * events are processed.
     */
    data object Success : ComposerSubmitStatus

    /**
     * The submit failed with a typed cause. Recoverable: dispatching
     * `Submit` again transitions back to `Submitting` and the prior
     * error is no longer observable in state.
     */
    data class Error(
        val cause: ComposerError,
    ) : ComposerSubmitStatus
}
