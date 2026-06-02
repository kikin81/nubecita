package net.kikin.nubecita.core.common.navigation

import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.metadata

/**
 * Tags a Navigation 3 `entry` as "present me as a centered dialog on Medium /
 * Expanded widths, full-screen on Compact." Pass to `entry(metadata = …)`; the
 * `:app` `AdaptiveDialogSceneStrategy` reads [AdaptiveDialogKey] and wraps the
 * entry in a `Dialog` on large screens, or declines (full-screen) on phones.
 *
 * This is the **entire** per-feature opt-in for the "two canonical layouts"
 * (phone full-screen / tablet modal) pattern — no launcher, no overlay state,
 * no CompositionLocal, no Dialog host. A feature pushes its route like any
 * other; the scene strategy decides the presentation. Mirrors navigation3's
 * built-in `DialogSceneStrategy.dialog()`, plus a Compact width gate.
 *
 * Pure data (navigation3-runtime only) so feature modules can reference it; the
 * Dialog-rendering strategy lives in `:app` next to `MainShell`'s `NavDisplay`.
 */
fun adaptiveDialog(): Map<String, Any> = metadata { put(AdaptiveDialogKey, true) }

/** Metadata marker read by `AdaptiveDialogSceneStrategy`. See [adaptiveDialog]. */
object AdaptiveDialogKey : NavMetadataKey<Boolean>
