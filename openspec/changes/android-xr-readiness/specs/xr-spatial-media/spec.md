## ADDED Requirements

### Requirement: Video player controls render in floating spatial Orbiter in XR mode

When executing in an Android XR session, the fullscreen and inline video player controls (play/pause, seek scrubber, comments button, volume, fullscreen toggle) SHALL be hoisted into an external spatial `Orbiter` (`androidx.xr.compose.material3`) anchored to the bottom edge outside the video frame, leaving the video surface 100% unobscured.

#### Scenario: Video controls float in external Orbiter during XR session
- **WHEN** a video is played within an Android XR environment
- **THEN** player controls SHALL render within a floating spatial `Orbiter` anchored adjacent to the video panel

#### Scenario: Standard devices render internal overlay controls
- **WHEN** video is played on standard non-XR devices
- **THEN** controls SHALL render as an on-surface overlay fading out after inactivity

### Requirement: Media3 video playback routes through spatialized audio in XR environment

The video playback engine (`:core:video` / `:feature:videoplayer`) SHALL configure Media3 audio attributes for spatial audio support when running in Android XR, anchoring the audio emitter position to the 3D coordinates of the video panel.

#### Scenario: Audio emits from spatial panel coordinate
- **WHEN** video audio is playing in an XR spatial environment
- **THEN** the audio output SHALL be spatialized corresponding to the panel's position relative to the user's headset

### Requirement: Theater and Full Space mode toggle for video playback

The video player UI SHALL provide a "Theater Mode" action button when running in XR. Selecting Theater Mode SHALL request Android XR Full Space mode, expanding the video canvas into a curved virtual screen and dimming the passthrough environment.

#### Scenario: Toggling theater mode enters Full Space
- **WHEN** the user selects the Theater Mode action in an XR video player session
- **THEN** the application SHALL request Full Space mode and render the video on a curved virtual surface with dimmed surrounding environment

#### Scenario: Exiting theater mode restores Home Space panel
- **WHEN** the user dismisses Theater Mode
- **THEN** the application SHALL return to the standard resizable 2D spatial panel in Home Space
