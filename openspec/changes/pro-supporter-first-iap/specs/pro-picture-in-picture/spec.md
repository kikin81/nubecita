## ADDED Requirements

### Requirement: PiP gated through a single entitlement choke-point
Picture-in-Picture SHALL be enabled only when the device supports PiP AND the user has Pro,
folded into a single `PipController.isEnabled: StateFlow<Boolean>` that every PiP call site
reads. No PiP call site SHALL branch on the entitlement independently.

#### Scenario: Pro user on a capable device
- **WHEN** the user has Pro and the device reports `FEATURE_PICTURE_IN_PICTURE`
- **THEN** `PipController.isEnabled` is `true` and PiP entry is published

#### Scenario: Non-Pro user
- **WHEN** the user does not have Pro
- **THEN** `PipController.isEnabled` is `false` and the app never enters PiP

#### Scenario: Entitlement flips at runtime
- **WHEN** the user's Pro entitlement is gained or lost while the app runs
- **THEN** `PipController.isEnabled` updates reactively and the published PiP params follow it

### Requirement: PiP entry and playback continuity
On Android API 31+ the app SHALL use the auto-enter flow (`setAutoEnterEnabled`) with correct
aspect ratio and source-rect hint; on API 26–30 it SHALL fall back to manual entry via
`onUserLeaveHint`. Video playback SHALL continue across the transition.

#### Scenario: Auto-enter on home gesture (API 31+)
- **WHEN** a Pro user is watching fullscreen video and performs the home gesture on API 31+
- **THEN** the app enters PiP and the video keeps playing

#### Scenario: Playback not paused by backgrounding during PiP
- **WHEN** the app receives the process `onStop` signal while in PiP
- **THEN** `SharedVideoPlayer` does NOT auto-pause the player

#### Scenario: Dismissing the PiP window stops playback
- **WHEN** the user swipes the PiP window away
- **THEN** the player pauses (a real stop)

### Requirement: PiP UI and controls
While in PiP the app SHALL hide all non-video chrome (player controls, system bars, and the
`MainShell` navigation suite) and SHALL provide a working play/pause control via a `RemoteAction`.

#### Scenario: Chrome hidden in PiP
- **WHEN** the app is in PiP mode
- **THEN** only the video surface is visible and the navigation suite is suppressed

#### Scenario: Play/pause works in PiP
- **WHEN** the user taps the PiP play/pause action
- **THEN** the shared player toggles and the action icon updates

### Requirement: PiP driven from the Activity/Compose layer, not the ViewModel
The PiP bridge SHALL be reached from the screen Composable via a `CompositionLocal`
(`LocalPipController`). ViewModels SHALL NOT access the Activity or PiP APIs.

#### Scenario: VM stays PiP-agnostic
- **WHEN** PiP params are published
- **THEN** the screen Composable reads VM state and calls the Activity bridge, and no ViewModel references the Activity

### Requirement: Graceful degradation and upsell
When PiP is unsupported (pre-26, missing system feature, or user-disabled) the app SHALL behave
exactly as today with no crash. A non-Pro user tapping the explicit pop-out affordance SHALL be
routed to the paywall rather than entering PiP.

#### Scenario: Unsupported device
- **WHEN** the device lacks `FEATURE_PICTURE_IN_PICTURE`
- **THEN** no pop-out affordance triggers PiP and fullscreen video is unchanged

#### Scenario: Non-Pro taps pop-out
- **WHEN** a non-Pro user taps the pop-out affordance
- **THEN** the app navigates to the paywall instead of entering PiP
