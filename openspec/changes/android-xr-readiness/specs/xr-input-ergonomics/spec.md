## ADDED Requirements

### Requirement: Primary interaction touch targets expand to minimum 56dp for XR and pointer environments

Post interaction buttons (reply, repost, like, bookmark, share), top-level tab buttons, and floating action buttons SHALL provide touch and pinch bounds of at least 56dp × 56dp when rendered on Android XR or desktop pointer devices, while maintaining their optical visual icon bounds.

#### Scenario: Post interaction buttons expand touch target bounds in XR
- **WHEN** post interactions are composed in an Android XR session or pointer-precision environment
- **THEN** each action's hit-test bounding box SHALL be at least 56dp × 56dp, while icon graphic rendering remains at standard optical scale

#### Scenario: Touch targets on standard handheld touchscreen devices
- **WHEN** post interactions are composed on standard handheld touchscreens
- **THEN** touch targets SHALL conform to standard 48dp minimum bounds

### Requirement: Interactive elements display distinct visual focus and hover states for gaze and raycast targeting

All clickable cards, post action buttons, and list items SHALL support visual focus and hover states when targeted by eye gaze, controller raycasts, or mouse cursors, rendering subtle surface elevation and tonal highlights.

#### Scenario: Gaze or pointer hover over post action triggers visual highlight
- **WHEN** an interaction button or card receives pointer or gaze hover
- **THEN** the component SHALL display a visible hover state overlay or tonal highlight

#### Scenario: Controller focus renders visible focus ring
- **WHEN** an element acquires spatial focus via controller raycast or keyboard navigation
- **THEN** the element SHALL render an accessible, high-contrast focus outline

### Requirement: Key event handlers support hardware keyboard shortcuts

The application SHALL intercept hardware keyboard events when a physical Bluetooth or USB keyboard is connected in XR productivity setups, providing standard navigation and action hotkeys.

#### Scenario: j and k keys navigate feed items
- **WHEN** a feed has focus and the user presses 'j' (down) or 'k' (up)
- **THEN** focus and scroll position SHALL advance to the next or previous post item respectively

#### Scenario: r key triggers reply to focused post
- **WHEN** a post item is focused and the user presses 'r'
- **THEN** the post composer SHALL open targeted to reply to that post

#### Scenario: Space key toggles video playback
- **WHEN** the video player is visible and the user presses 'Space'
- **THEN** active video playback SHALL toggle between playing and paused
