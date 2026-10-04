## ADDED Requirements

### Requirement: Shared video player handles same-URL re-binding after completion or error

The system SHALL allow `SharedVideoPlayer.bind` to re-prepare and reset position when requested with the currently bound playlist URL if the underlying player is in `STATE_IDLE`, `STATE_ENDED`, or holds an active playback error.

#### Scenario: Re-binding same playlist URL
- **WHEN** `SharedVideoPlayer.bind` is called with a URL matching `boundPlaylistUrl` while the player has finished playing or encountered an error
- **THEN** `SharedVideoPlayer` clears any prior error, seeks to position 0, and calls `prepare()`

### Requirement: Shared video player enforces emulator-safe codec selection

The system SHALL configure `DefaultRenderersFactory` with `forceDisableMediaCodecAsynchronousQueueing()` and prioritize Google software decoders (`c2.android.*`, `OMX.google.*`) when executing on Android emulator environments.

#### Scenario: Playing video on Android emulator
- **WHEN** video playback initializes inside an emulator runtime environment
- **THEN** the player avoids crashing buggy hardware vendor decoders and successfully plays video content
