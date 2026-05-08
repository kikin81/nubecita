package net.kikin.nubecita.core.common.navigation

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Read-only composer submit-success stream, scoped to `MainShell`.
 * Hosted at the shell level so any descendant screen can observe
 * submits and react (e.g., the feed shows a snackbar + runs an
 * optimistic `replyCount + 1` on the parent post). The write side is
 * surfaced separately as [LocalComposerSubmitEventsEmitter]; reading
 * this local exposes only a [Flow] (no `.emit`), so consumers can't
 * accidentally publish. See [ComposerSubmitEventsBus]'s kdoc for the
 * "API separation, not access control" trade-off — both locals are
 * provided shell-wide, and the emitter side is producer-only by
 * convention rather than scoping.
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
 * Producer-side companion to [LocalComposerSubmitEvents]. Read by
 * `ComposerOverlay` (Medium / Expanded Dialog host) and
 * `ComposerNavigationModule` (Compact route host) — by convention
 * the only sites that publish to the shell-scoped bus. Provided at
 * the same `MainShell` root as [LocalComposerSubmitEvents], so any
 * descendant could in principle read this local and emit; the
 * producer/consumer split is enforced by naming and call-site
 * convention, not by visibility scoping. See [ComposerSubmitEventsBus]'s
 * kdoc for the rationale.
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
