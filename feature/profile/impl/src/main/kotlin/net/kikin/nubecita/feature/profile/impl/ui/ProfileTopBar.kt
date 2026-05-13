package net.kikin.nubecita.feature.profile.impl.ui

/**
 * Multiplier applied to the hero's measured height to derive the
 * fade window. With `0.5f` the bar is fully visible by the time the
 * hero is half-scrolled — empirically that's "around when the avatar
 * leaves the viewport."
 *
 * Tunable during on-device verification; private to keep the surface
 * area of [ProfileTopBar] minimal.
 */
internal const val PROFILE_BAR_FADE_MULTIPLIER: Float = 0.5f

/**
 * Maps the LazyColumn's scroll position into a 0..1 alpha for the
 * collapsing top bar.
 *
 * - Hero scrolled past the first item ([firstVisibleItemIndex] > 0) → 1f.
 * - Hero hasn't measured yet ([fadeWindowPx] <= 0) → 0f (the bar stays
 *   invisible until the hero reports a height; without this guard,
 *   the divide produces `Infinity`).
 * - Otherwise → `(offset / window).coerceIn(0f, 1f)` — linear lerp
 *   from invisible at 0 px scrolled to fully visible at `fadeWindowPx`
 *   pixels scrolled.
 *
 * Pure function so it's unit-testable without a Compose runtime. The
 * caller wraps this in `derivedStateOf { ... }` so the bar only
 * recomposes when the discrete alpha actually changes.
 */
internal fun computeBarAlpha(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    fadeWindowPx: Int,
): Float =
    when {
        firstVisibleItemIndex > 0 -> 1f
        fadeWindowPx <= 0 -> 0f
        else -> (firstVisibleItemScrollOffset.toFloat() / fadeWindowPx).coerceIn(0f, 1f)
    }
