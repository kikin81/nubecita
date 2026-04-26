## ADDED Requirements

### Requirement: `LoginScreen` wraps content in a `Scaffold` with `WindowInsets.safeDrawing`

The stateless `LoginScreen(state, onEvent, modifier)` composable MUST wrap its content `Column` inside a `Scaffold(contentWindowInsets = WindowInsets.safeDrawing)`. The Scaffold's content lambda receives `innerPadding` which the inner Column applies via `Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)`. This satisfies the edge-to-edge skill's "RIGHT" pattern for a screen with a `TextField` whose visibility must be preserved when the IME opens, while keeping content out of the status bar and gesture bar without an opaque scrim.

#### Scenario: IME opens without occluding the handle field

- **WHEN** the user taps the `OutlinedTextField` and the soft keyboard opens
- **THEN** the field SHALL remain fully visible above the IME — the Scaffold's `WindowInsets.safeDrawing` includes the IME inset, and `adjustResize` (already declared in the AndroidManifest) re-lays out the content area accordingly

#### Scenario: Content respects status bar inset on cold start

- **WHEN** `LoginScreen` is the start destination and the app cold-starts with edge-to-edge enabled
- **THEN** the title `Text` SHALL appear below the status bar (no visual overlap), and the status bar area SHALL show the underlying surface color

#### Scenario: Content respects gesture-nav bottom inset

- **WHEN** `LoginScreen` is rendered on a 3-button or gesture-nav device
- **THEN** the submit button SHALL appear above the system gesture bar — the bottom inset SHALL be reflected in the Scaffold's `innerPadding.calculateBottomPadding()`
