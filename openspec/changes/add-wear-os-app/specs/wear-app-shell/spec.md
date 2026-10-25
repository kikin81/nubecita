## ADDED Requirements

### Requirement: Wear app is a Compose-only, non-standalone companion
The Wear app SHALL be a separate `:wear:app` module that uses the applicationId `net.kikin.nubecita` and the phone app's signing key. Its UI SHALL be built only with Wear Compose Material 3 and Wear Navigation 3, with no XML layouts, View-based UI, Horologist or Wear Material 2.5. Its manifest SHALL declare `android.hardware.type.watch` and `com.google.android.wearable.standalone=false`, and it SHALL target SDK 34 or higher.

#### Scenario: Manifest marks the app non-standalone
- **WHEN** the merged release manifest of `:wear:app` is inspected
- **THEN** it declares `<uses-feature android:name="android.hardware.type.watch" />` and the `com.google.android.wearable.standalone` meta-data with value `false`, and `targetSdkVersion` is at least 34

#### Scenario: No legacy UI dependencies
- **WHEN** the `:wear:app` release runtime classpath is resolved
- **THEN** it contains no `com.google.android.horologist` and no `androidx.wear.compose:compose-material` (2.5) artifacts, and the module has no layout XML resources

### Requirement: Branded launch and single Recents entry
The Wear app SHALL show a splash screen via `androidx.core:core-splashscreen` with a black background and a 48 dp icon that matches the launcher icon, installed before `super.onCreate()`, without a trampoline activity. The launcher activity SHALL declare a unique, explicit `taskAffinity`.

#### Scenario: Cold start shows the branded splash
- **WHEN** the user launches the Wear app from a cold start
- **THEN** a black splash screen with the launcher icon appears before the first screen, and exactly one Nubecita entry appears in Recents afterwards

### Requirement: Wear UI uses the Wear Compose M3 structure
The app SHALL use exactly one `AppScaffold`, a `ScreenScaffold` per screen and a `TimeText`. Every scrolling list SHALL be a `TransformingLazyColumn` that receives the scaffold's content padding and applies `SurfaceTransformation` and `transformedHeight` to its items. Snapping lists SHALL set both the snap fling and the rotary snap behavior. An `EdgeButton` SHALL live in the `ScreenScaffold` slot, and the `ScrollIndicator` SHALL be hidden during scroll capture.

#### Scenario: Touch and rotary scrolling snap alike
- **WHEN** the user scrolls the feed list with the rotating crown and then flings it by touch
- **THEN** both gestures settle on an item boundary

### Requirement: Wear UI meets the Play quality floors
Backgrounds SHALL be pure black. Touch targets SHALL be at least 48×48 dp. Essential text SHALL be at least 12 sp and other text at least 10 sp. No text or control SHALL clip or overlap at the largest font scale.

#### Scenario: Screens pass the font-scale and device previews
- **WHEN** the screenshot tests render each screen under `@WearPreviewDevices` and `@WearPreviewFontScales`
- **THEN** no text or control is clipped by the screen edge or overlaps another, and every interactive element measures at least 48×48 dp

### Requirement: Watch explains what is missing instead of showing an empty app
When no paired node advertises the `nubecita_phone` capability, the watch SHALL do one of two things:
- On an Android phone, offer a one-tap install of the phone app through `RemoteActivityHelper`.
- On an iPhone, explain that the Android phone app is required.

When the phone app is present but signed out, the watch SHALL show a "Sign in on your phone" state.

#### Scenario: Phone app not installed on an Android phone
- **WHEN** the watch app opens, `PhoneTypeHelper` reports an Android phone, and no node advertises `nubecita_phone`
- **THEN** the watch shows an install prompt, and tapping it opens the Play Store listing for `net.kikin.nubecita` on the phone

#### Scenario: Paired with an iPhone
- **WHEN** the watch app opens paired with an iPhone
- **THEN** it shows a screen saying the Android phone app is required, with no install action

#### Scenario: Phone app signed out
- **WHEN** the latest session snapshot says signed out
- **THEN** the watch shows "Sign in on your phone" and no feed or message data
