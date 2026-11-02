package net.kikin.nubecita.core.common.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * [CompositionLocal] exposing the root [SharedTransitionScope] to descendant
 * composables.
 *
 * Provided at the root of `MainNavigation` by wrapping the outer `NavDisplay` in
 * `SharedTransitionLayout`.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Suppress("ktlint:compose:compositionlocal-allowlist")
val LocalSharedTransitionScope: ProvidableCompositionLocal<SharedTransitionScope?> =
    compositionLocalOf { null }

/**
 * [CompositionLocal] exposing the current Nav entry's [AnimatedVisibilityScope]
 * to descendant composables.
 *
 * Provided inside individual entry providers (such as in `FeedNavigationModule`
 * and `MediaViewerNavigationModule`) where the entry's scope is active.
 */
@Suppress("ktlint:compose:compositionlocal-allowlist")
val LocalAnimatedVisibilityScope: ProvidableCompositionLocal<AnimatedVisibilityScope?> =
    compositionLocalOf { null }
