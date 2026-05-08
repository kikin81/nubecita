package net.kikin.nubecita.feature.composer.impl.internal

/**
 * Gate decision for a composer close attempt (back-press or toolbar
 * X-tap). Pure data — the screen's wiring picks the right branch and
 * dispatches; this enum just makes the decision unit-testable
 * independently of Compose / BackHandler infrastructure.
 *
 * The truth table is straightforward but load-bearing: the
 * `Submitting` branch must NOT propagate the close attempt (otherwise
 * a back-press during a submit would close the Compact composer route
 * mid-flight), and an empty draft must NOT show the discard dialog
 * (no point asking the user to confirm tossing nothing).
 */
internal enum class ComposerCloseAction {
    /**
     * Submit is in flight — eat the close attempt entirely. Neither
     * the dialog nor the back-navigation runs.
     */
    Swallow,

    /**
     * Draft has unsent content — show the discard confirmation
     * dialog. Confirmation flow takes over from here.
     */
    ShowDiscardDialog,

    /**
     * Empty draft — close the composer immediately (no confirmation
     * needed when there's nothing to lose).
     */
    NavigateBack,
}

/**
 * Pure decision function for what should happen when the user
 * attempts to close the composer. Splits the 2×2 truth table of
 * `(hasContent, isSubmitting)` into the three actions defined in
 * [ComposerCloseAction]:
 *
 * |              | hasContent = true  | hasContent = false |
 * |--------------|--------------------|--------------------|
 * | submitting   | Swallow            | Swallow            |
 * | not submitting | ShowDiscardDialog | NavigateBack       |
 *
 * Submitting takes precedence over hasContent — even with text in the
 * field, a submit-in-flight back-press is swallowed.
 */
internal fun composerCloseAttempt(
    hasContent: Boolean,
    isSubmitting: Boolean,
): ComposerCloseAction =
    when {
        isSubmitting -> ComposerCloseAction.Swallow
        hasContent -> ComposerCloseAction.ShowDiscardDialog
        else -> ComposerCloseAction.NavigateBack
    }
