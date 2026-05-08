package net.kikin.nubecita.core.common.navigation

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Read-only composer submit-success stream, scoped to `MainShell`.
 * Hosted at the shell level so any descendant screen can observe
 * submits and react (e.g., the feed shows a snackbar + runs an
 * optimistic `replyCount + 1` on the parent post). The corresponding
 * write side lives behind [LocalComposerSubmitEventsEmitter] so
 * arbitrary descendants of `MainShell` can't emit — only the composer
 * hosts can.
 *
 * The default value is a singleton silent [Flow] that never emits, so
 * previews / screenshot tests / detached compositions don't need to
 * wrap their content in a custom
 * [androidx.compose.runtime.CompositionLocalProvider]. The singleton
 * is held privately to avoid an allocation per composition; nothing
 * has a write side onto it, so multiple readers of the default share
 * the same no-op stream without any coupling between them.
 *
 * Allowlisted in `.editorconfig`'s `compose_allowed_composition_locals`
 * so ktlint's `compose:compositionlocal-allowlist` rule lets it
 * through.
 *
 * @see ComposerSubmitEventsBus — the host-side state holder.
 * @see ComposerSubmitEvent — the event payload.
 * @see LocalComposerSubmitEventsEmitter — the producer-side handle.
 */
val LocalComposerSubmitEvents: ProvidableCompositionLocal<Flow<ComposerSubmitEvent>> =
    compositionLocalOf { EmptyComposerSubmitEvents }

/**
 * Producer-side companion to [LocalComposerSubmitEvents]. Held by
 * `ComposerOverlay` (Medium / Expanded Dialog host) and
 * `ComposerNavigationModule` (Compact route host) — the only sites
 * that should be able to publish to the shell-scoped bus.
 *
 * The default is a no-op [ComposerSubmitEventsEmitter]; previews and
 * detached compositions can call [ComposerSubmitEventsEmitter.emit]
 * without wiring anything up, and the call is silently dropped (the
 * paired [LocalComposerSubmitEvents] default never emits either, so
 * the read and write defaults stay consistent).
 *
 * Allowlisted in `.editorconfig`'s `compose_allowed_composition_locals`.
 *
 * @see ComposerSubmitEventsBus — the host-side state holder.
 * @see LocalComposerSubmitEvents — the consumer-side handle.
 */
val LocalComposerSubmitEventsEmitter: ProvidableCompositionLocal<ComposerSubmitEventsEmitter> =
    compositionLocalOf { NoOpComposerSubmitEventsEmitter }

private val EmptyComposerSubmitEvents: Flow<ComposerSubmitEvent> = emptyFlow()

private val NoOpComposerSubmitEventsEmitter: ComposerSubmitEventsEmitter =
    ComposerSubmitEventsEmitter { /* no-op default for previews / detached compositions */ }
