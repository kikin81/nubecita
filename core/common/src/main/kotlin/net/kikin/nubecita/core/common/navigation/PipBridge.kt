package net.kikin.nubecita.core.common.navigation

import android.graphics.Rect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The Compose → Activity Picture-in-Picture seam (design D5: PiP is driven from
 * the Activity/Compose layer, never a ViewModel — a VM can't hold an `Activity`).
 * Lives in `:core:common:navigation` alongside the other CompositionLocal seams
 * (and so the local providing it doesn't create a `:core:common → :core:video`
 * cycle). The video screen reads it via [LocalPipController] and calls
 * [updateParams]; `:app`'s Activity provides the concrete implementation.
 *
 * [isEnabled] is surfaced here (delegated by the impl from `:core:video`'s
 * `PipController`) so the screen can key its params-publishing `LaunchedEffect`
 * on it.
 */
public interface PipBridge {
    /** Whether PiP is currently enabled (device supports it AND setting is enabled). */
    public val isEnabled: StateFlow<Boolean>

    /**
     * Whether this device physically supports Picture-in-Picture.
     * The explicit pop-out affordance keys its *visibility* on this.
     */
    public val isPipSupported: Boolean

    /**
     * Enter Picture-in-Picture now, in response to the explicit pop-out button
     * (nubecita-q5ge.8). No-op if the device doesn't support PiP. Callers gate
     * the check on [isEnabled]; this just performs the entry with the current
     * player params.
     */
    public fun enterPip()

    /**
     * Publish the current PiP parameters. [aspectRatio] is the decoded
     * `width / height` (or null if unknown — the impl clamps/falls back);
     * [isPlaying] drives the play/pause action;
     * [sourceRectHint] is the on-screen video bounds for a smooth enter animation
     * (null until the Compose layer measures it).
     */
    public fun updateParams(
        aspectRatio: Float?,
        isPlaying: Boolean,
        sourceRectHint: Rect? = null,
    )
}

/** Inert [PipBridge] for composables rendered outside the PiP-capable Activity (previews, screenshot tests). */
public object NoOpPipBridge : PipBridge {
    // asStateFlow() so it can't be cast back to MutableStateFlow and mutated —
    // the no-op stays genuinely inert.
    override val isEnabled: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()

    override val isPipSupported: Boolean = false

    override fun enterPip(): Unit = Unit

    override fun updateParams(
        aspectRatio: Float?,
        isPlaying: Boolean,
        sourceRectHint: Rect?,
    ): Unit = Unit
}
