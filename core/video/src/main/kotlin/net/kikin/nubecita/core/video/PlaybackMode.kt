package net.kikin.nubecita.core.video

/**
 * Operating mode for [SharedVideoPlayer]. Determines audio-focus
 * discipline and default volume:
 *
 * - [FeedPreview]: ExoPlayer runs with `handleAudioFocus = false` and
 *   `volume = 0f`. Opening the app to scroll the feed must never
 *   interrupt the user's music — feed autoplay is a silent preview.
 * - [Fullscreen]: ExoPlayer runs with `handleAudioFocus = true` and
 *   `volume = 1f`. Media3's built-in focus handler manages the OS
 *   audio-focus hierarchy (pause on incoming call, duck on transient
 *   loss, resume on focus regained) without per-listener wiring.
 *
 * Mode flips re-call `Player.setAudioAttributes(attrs, handleAudioFocus)`
 * with the new flag rather than touching `android.media.AudioManager`
 * directly. See the design doc for the rationale:
 * `docs/superpowers/specs/2026-05-16-fullscreen-video-player-design.md`,
 * "SharedVideoPlayer contract" section.
 */
enum class PlaybackMode { FeedPreview, Fullscreen }
