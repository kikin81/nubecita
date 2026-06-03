## ADDED Requirements

### Requirement: About entry in Settings
The Settings screen SHALL present an "About" row that navigates to the About sub-screen.

#### Scenario: Opening About from Settings
- **WHEN** the user taps the "About" row in Settings
- **THEN** the About sub-screen is pushed onto the MainShell back stack
- **AND** pressing back returns to the Settings screen

### Requirement: Adaptive presentation of the Settings/About surfaces
The Settings, About, and About-licenses routes SHALL present full-screen on Compact widths and as a single shared, content-swapping dialog on Medium/Expanded widths (the contributor opting in only via the `adaptiveDialog` tag, with no per-feature navigation logic).

#### Scenario: Phone pushes full-screen pages
- **WHEN** the user is on a Compact-width device and navigates Settings → About → Open source licenses
- **THEN** each destination is presented as a full-screen page pushed onto the back stack

#### Scenario: Tablet swaps content within one dialog
- **WHEN** the user is on a Medium or Expanded width device and navigates Settings → About → Open source licenses
- **THEN** the content is swapped within a single centered dialog (one scrim and card), not stacked as multiple dialogs
- **AND** a back affordance returns to the previous content within the same dialog
- **AND** dismissing the first dialog level closes the dialog and returns to the underlying screen

### Requirement: About screen header and source link
The About screen SHALL display the application name and version, and SHALL provide a link to the project's public source repository.

#### Scenario: Version is shown
- **WHEN** the About screen is displayed
- **THEN** it shows the application name and the current app version string

#### Scenario: Opening the source repository
- **WHEN** the user taps the "Source on GitHub" row
- **THEN** the system opens `https://github.com/kikin81/nubecita` in an in-app browser tab

#### Scenario: No browser available
- **WHEN** the user taps an external link and no browser is installed
- **THEN** the app does not crash and remains on the About screen

### Requirement: Special Thanks rows with live profile data
The About screen SHALL display a Special Thanks section listing each contributor with their live profile data (avatar, display name, handle) and a short blurb. Contributors are identified by a fixed DID so links survive handle changes.

#### Scenario: Rendering a contributor row
- **WHEN** the About screen loads and a contributor's profile is fetched successfully
- **THEN** the row shows that contributor's avatar, display name, handle, and blurb

#### Scenario: Profile fetch fails for a contributor
- **WHEN** a contributor's profile data cannot be fetched
- **THEN** the row still renders with the contributor's handle, blurb, and a placeholder avatar
- **AND** the rest of the screen remains usable

#### Scenario: Deep-linking to a contributor profile
- **WHEN** the user taps a Special Thanks row
- **THEN** the app navigates to that contributor's Profile screen using their DID

### Requirement: Open source licenses screen
The About screen SHALL provide access to an open-source licenses screen that lists the third-party libraries bundled in the app, generated from the build, and SHALL let the user open each library's URL.

#### Scenario: Opening the licenses screen
- **WHEN** the user taps the "Open source licenses" row on the About screen
- **THEN** the licenses screen is pushed onto the MainShell back stack
- **AND** it lists the app's third-party libraries rendered with the app's design-system rows

#### Scenario: Opening a library's page
- **WHEN** the user taps a library entry that has an associated URL
- **THEN** the system opens that URL in an in-app browser tab

#### Scenario: Returning from licenses
- **WHEN** the user presses back on the licenses screen
- **THEN** the app returns to the About screen
