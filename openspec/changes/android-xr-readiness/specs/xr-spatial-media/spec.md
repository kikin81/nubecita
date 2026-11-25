## ADDED Requirements

### Requirement: Video player controls render in floating spatial Orbiter in XR mode

When executing in an Android XR session, the fullscreen and inline video player controls (play/pause, seek scrubber, comments button, volume, fullscreen toggle) SHALL be hoisted into an external spatial `Orbiter` (`androidx.xr.compose.material3`) anchored to the bottom edge outside the video frame, leaving the video surface 100% unobscured.

#### Scenario: Video controls float in external Orbiter during XR session
- **WHEN** a video is played within an Android XR environment
- **THEN** player controls SHALL render within a floating spatial `Orbiter` anchored adjacent to the video panel

#### Scenario: Standard devices render internal overlay controls
- **WHEN** video is played on standard non-XR devices
- **THEN** controls SHALL render as an on-surface overlay fading out after inactivity

### Requirement: Video audio is spatialized to the app's panel in XR

On Android XR, video audio SHALL play from the app's panel through the platform's automatic panel spatialization. The player SHALL NOT configure positional audio itself: the positional APIs do not accept `USAGE_MEDIA` audio, and the platform already anchors an app's audio to the panel its UI is rendered on.

#### Scenario: Audio emits from the panel
- **WHEN** video audio is playing in an Android XR environment
- **THEN** the audio SHALL be heard from the app panel's position, with no XR-specific audio configuration in the player

### Requirement: Theater and Full Space mode toggle for video playback

The video player UI SHALL provide a "Theater Mode" action button when running in XR. Selecting Theater Mode SHALL request Android XR Full Space mode and fade the passthrough out, so the room darkens behind the video.

#### Scenario: Toggling theater mode enters Full Space
- **WHEN** the user selects the Theater Mode action in an XR video player session
- **THEN** the application SHALL request Full Space mode and set its passthrough preference to fully faded out

#### Scenario: Exiting theater mode restores Home Space panel
- **WHEN** the user dismisses Theater Mode
- **THEN** the application SHALL clear its passthrough preference and, if Theater Mode is what entered Full Space, return to the standard resizable panel in Home Space
