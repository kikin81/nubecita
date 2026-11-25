package net.kikin.nubecita.designsystem.spatial

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Minimum hit-area height for dense inline actions, such as the `PostCard` action row, or
 * [Dp.Unspecified] for none.
 *
 * Unspecified on phones, foldables and tablets: those cells are drawn smaller than 48dp, and
 * Compose already extends a finger touch to 48dp around them without changing layout. That
 * extension covers touch only, not a mouse or an XR raycast, so `MainShell` provides
 * [SpatialMinimumTargetSize] on Android XR (Home Space and Full Space alike), where a pinch
 * aimed from across the room would otherwise have a ~26dp target.
 *
 * Static: the value is fixed for the life of the shell, so the rare full-subtree
 * recomposition a static local costs never happens at runtime.
 */
val LocalSpatialMinimumTargetSize = staticCompositionLocalOf { Dp.Unspecified }

/** Gaze / raycast target height on Android XR (design: 56–64dp for primary post actions). */
val SpatialMinimumTargetSize = 56.dp
