package net.kikin.nubecita.core.video

import android.graphics.Rect

/**
 * The seam between the Compose layer and the Activity's Picture-in-Picture APIs
 * (design D5: PiP is driven from the Activity/Compose layer, never a ViewModel —
 * a VM can't hold an `Activity`). The video screen calls [updateParams] with the
 * current aspect ratio and play state; the `:app` Activity implements this to
 * build and apply `PictureInPictureParams` (and, on API 31+, the auto-enter
 * flag). A later task exposes the live implementation through a `CompositionLocal`
 * (mirroring `LocalMainShellNavState`); [NoOpPipBridge] is the default so a
 * composable rendered outside the Activity (previews, screenshot tests) is inert.
 */
public interface PipBridge {
    /**
     * Publish the current PiP parameters. [aspectRatio] is the decoded
     * `width / height` (or null if unknown — the implementation clamps/falls back
     * via [clampPipAspectRatio]); [isPlaying] drives the play/pause action icon
     * and, on API 31+, whether auto-enter is armed. [sourceRectHint] is the
     * on-screen bounds of the video surface for a smooth enter animation —
     * supplied by the Compose layer once it measures the player (a later task);
     * null until then.
     */
    public fun updateParams(
        aspectRatio: Float?,
        isPlaying: Boolean,
        sourceRectHint: Rect? = null,
    )
}

/** Inert [PipBridge] for composables rendered outside the PiP-capable Activity. */
public object NoOpPipBridge : PipBridge {
    override fun updateParams(
        aspectRatio: Float?,
        isPlaying: Boolean,
        sourceRectHint: Rect?,
    ): Unit = Unit
}
