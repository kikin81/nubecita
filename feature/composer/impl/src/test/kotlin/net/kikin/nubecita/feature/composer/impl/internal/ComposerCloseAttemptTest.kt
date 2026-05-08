package net.kikin.nubecita.feature.composer.impl.internal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Truth-table coverage for [composerCloseAttempt]. The gate's three
 * branches translate directly into bd `nubecita-wtq.8`'s acceptance:
 *
 * - "back-press ignored entirely while Submitting" → [ComposerCloseAction.Swallow]
 * - "confirmation appears for non-empty draft"     → [ComposerCloseAction.ShowDiscardDialog]
 * - "Empty draft back-press skips the confirmation" → [ComposerCloseAction.NavigateBack]
 *
 * The submitting branch is asserted at both `hasContent = true` and
 * `hasContent = false` because the precedence ordering matters: the
 * spec requires submit to win even with unsent text in the field.
 */
internal class ComposerCloseAttemptTest {
    @Test
    fun `submitting + hasContent — Swallow (submit precedence wins over content)`() {
        assertEquals(
            ComposerCloseAction.Swallow,
            composerCloseAttempt(hasContent = true, isSubmitting = true),
        )
    }

    @Test
    fun `submitting + empty — Swallow`() {
        assertEquals(
            ComposerCloseAction.Swallow,
            composerCloseAttempt(hasContent = false, isSubmitting = true),
        )
    }

    @Test
    fun `not submitting + hasContent — ShowDiscardDialog`() {
        assertEquals(
            ComposerCloseAction.ShowDiscardDialog,
            composerCloseAttempt(hasContent = true, isSubmitting = false),
        )
    }

    @Test
    fun `not submitting + empty — NavigateBack (no confirmation needed)`() {
        assertEquals(
            ComposerCloseAction.NavigateBack,
            composerCloseAttempt(hasContent = false, isSubmitting = false),
        )
    }
}
