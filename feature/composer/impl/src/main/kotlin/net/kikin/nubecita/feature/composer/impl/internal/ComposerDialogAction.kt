package net.kikin.nubecita.feature.composer.impl.internal

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

/**
 * One button in a [ComposerDiscardDialog]. Data-driven so the dialog
 * supports an arbitrary action set: V1 ships exactly two
 * (`Cancel` + `Discard`), but the eventual `:core:drafts` integration
 * adds a third "Save draft" action without a layout rewrite.
 *
 * `destructive = true` paints the button with `colorScheme.error` per
 * M3's destructive-tone guidance — used on `Discard` so the visual
 * weight of the irreversible action matches its impact.
 */
@Immutable
internal data class ComposerDialogAction(
    @StringRes val label: Int,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)
