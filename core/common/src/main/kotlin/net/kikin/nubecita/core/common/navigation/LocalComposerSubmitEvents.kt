package net.kikin.nubecita.core.common.navigation

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * Composer submit-success event bus, scoped to `MainShell`. Hosted at
 * the shell level so any descendant screen can observe submits and
 * react (e.g., the feed shows a snackbar + runs an optimistic
 * `replyCount + 1` on the parent post).
 *
 * The default value is a fresh, non-emitting [ComposerSubmitEvents]
 * instance — previews / screenshot tests / detached compositions read
 * a flow that never emits, so they don't need to wrap their content in
 * a custom [androidx.compose.runtime.CompositionLocalProvider].
 *
 * Allowlisted in `.editorconfig`'s `compose_allowed_composition_locals`
 * so ktlint's `compose:compositionlocal-allowlist` rule lets it
 * through.
 *
 * @see ComposerSubmitEvents — the host-side state holder.
 * @see ComposerSubmitEvent — the event payload.
 */
val LocalComposerSubmitEvents: ProvidableCompositionLocal<ComposerSubmitEvents> =
    compositionLocalOf { EmptyComposerSubmitEvents }

/**
 * Singleton non-emitting default for [LocalComposerSubmitEvents].
 * Reading `.events` returns a [kotlinx.coroutines.flow.SharedFlow]
 * that never emits because nothing calls [ComposerSubmitEvents.emit]
 * on it; previews / tests / detached compositions get a no-op
 * collector at zero cost.
 */
private val EmptyComposerSubmitEvents: ComposerSubmitEvents = ComposerSubmitEvents()
