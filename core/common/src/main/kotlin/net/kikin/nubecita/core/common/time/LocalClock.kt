package net.kikin.nubecita.core.common.time

import androidx.compose.runtime.compositionLocalOf
import kotlin.time.Clock

/**
 * Composition-scoped clock for relative-time rendering.
 *
 * Defaults to [Clock.System] so production call sites get the wall clock
 * with no extra wiring. Screenshot tests + Compose previews that render
 * `PostCard` (or anything else that calls [rememberRelativeTimeText])
 * MUST wrap their content in
 * `CompositionLocalProvider(LocalClock provides <fixedClock>)` so the
 * "now" used to compute the relative-time label is pinned to a fixed
 * `Instant`. Combined with truly fixed `Instant.parse(...)` for `then`
 * (the post's `createdAt`), this makes screenshot baselines deterministic
 * across runs forever — no `Clock.System.now()` allowed in test fixtures.
 *
 * Justified CompositionLocal: defaults to `Clock.System` (no behavior
 * change for production), only screenshot tests + previews override it.
 * Threading a `Clock` through every `PostCard` call site (and every
 * future relative-time consumer) would be strictly worse than this
 * implicit dependency. Allowlisted in `.editorconfig` via
 * `compose_allowed_composition_locals` so ktlint's
 * `compose:compositionlocal-allowlist` rule lets it through.
 */
val LocalClock = compositionLocalOf<Clock> { Clock.System }
