## ADDED Requirements

### Requirement: AndroidManifest declares spatial panel compatibility and resizability

The application manifest SHALL declare `android:resizeableActivity="true"` on `MainActivity` and declare the `android.software.xr.api` feature with `android:required="false"`. The app MUST install and run on both standard mobile/tablet devices and Android XR headsets without build variant bifurcation.

#### Scenario: App declares optional XR feature
- **WHEN** the application manifest is inspected
- **THEN** `android.software.xr.api` SHALL be declared with `android:required="false"` and `MainActivity` SHALL have `android:resizeableActivity="true"`

### Requirement: Multi-pane SceneStrategy expands side-by-side on wide spatial canvases

The inner navigation display SHALL evaluate spatial window dimensions and apply Navigation 3 `ListDetailSceneStrategy` or `SupportingPaneSceneStrategy` whenever the spatial window width exceeds the expanded breakpoint (>= 840dp). On wide spatial windows, detail entries (e.g., Post Thread Detail, Profile, Video Comments) SHALL be rendered alongside list entries without pushing the list off-screen or opening modal takeovers.

#### Scenario: Expanded spatial window renders list and detail panes concurrently
- **WHEN** the window width in the spatial environment is 840dp or greater and a list item is selected
- **THEN** the list pane and the detail pane SHALL be rendered side-by-side concurrently

#### Scenario: Compact spatial window displays single pane with back navigation
- **WHEN** the spatial window is resized below 840dp
- **THEN** only the topmost destination SHALL be visible, and back navigation affordances SHALL be displayed

### Requirement: Dynamic freeform window resizing preserves UI and scroll state

All feed lists (`LazyColumn`, `LazyVerticalGrid`), active composer drafts, and ongoing media playback sessions SHALL persist their state across continuous freeform resizing events in 3D space without layout jumping or state recreation.

#### Scenario: Dynamic window resizing retains feed scroll position
- **WHEN** a user resizes a floating spatial panel while scrolled into a post feed
- **THEN** the scroll offset and visible first item index SHALL be retained without resetting to top

#### Scenario: Dynamic window resizing preserves video playback continuity
- **WHEN** a user resizes a spatial window playing a video feed item
- **THEN** playback SHALL continue uninterrupted without reloading or buffering from the start
