package net.kikin.nubecita.feature.feed.impl.video

/**
 * Transient hint surfaced by [FeedVideoPlayerCoordinator] to the bound
 * video card so the card can render an inline affordance. Coordinator
 * MUST NOT round-trip these through the VM event stream — they are
 * card-local and self-clear via `coordinator.resume()`.
 */
internal enum class PlaybackHint {
    /** No hint. The bound card renders normally. */
    None,

    /**
     * The coordinator was holding audio focus (i.e. user had unmuted)
     * and then lost it (incoming call, music app gaining focus,
     * `ACTION_AUDIO_BECOMING_NOISY` from headphones unplug). Player
     * is paused, focus is released, but `isUnmuted` stays `true` to
     * preserve the user's intent. The bound card surfaces a "tap to
     * resume" affordance; tap calls `coordinator.resume()`.
     */
    FocusLost,
}
