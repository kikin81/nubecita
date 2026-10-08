# app-splash-routing Specification

## Purpose
Cold-start routing: the `Splash` start destination held on screen by the system SplashScreen API while session bootstrap runs, the reactive back-stack replacement to Login or Main once `SessionState` resolves, and the brand theming (splash theme, adaptive launcher icon) that makes that handoff flicker-free.
## Requirements
### Requirement: `:app` defines a `Splash` NavKey as the start destination

`:app` SHALL define `@Serializable data object Splash : NavKey` as start destination via `StartDestinationModule`. `MainNavigation` SHALL register `entry<Splash>` rendering centered `NubecitaLogomark` over background. The SplashScreen API overlays this composable via `setKeepOnScreenCondition` while session bootstrap runs, avoiding flicker before swapping routes to Login or Main.

#### Scenario: Cold start shows the system splash, then the routed destination

- **WHEN** the user cold-starts the app and a session is present in the store
- **THEN** the system splash SHALL be visible during bootstrap, then SHALL dismiss revealing `Main` (no flicker of an empty surface; the brand cloud rendered by the `Splash` composable provides visual continuity during the brief window before `navigator.replaceTo(Main)` swaps the back stack)

#### Scenario: Cold start with no session routes to Login

- **WHEN** the user cold-starts the app and `OAuthSessionStore.load()` returns `null`
- **THEN** the system splash SHALL be visible during bootstrap, then SHALL dismiss revealing `Login` (same continuity guarantee — the brand cloud is on-screen until `navigator.replaceTo(Login)` resolves)

### Requirement: `MainActivity` installs the SplashScreen API and holds it while session is Loading

`MainActivity.onCreate` SHALL call `installSplashScreen()` before `super.onCreate`, call `splashScreen.setKeepOnScreenCondition { sessionStateProvider.state.value is SessionState.Loading }`, inject `SessionStateProvider`, and after `setContent` launch a coroutine calling `sessionStateProvider.refresh()`. The keep-on-screen predicate SHALL be a cheap synchronous read.

#### Scenario: SplashScreen is installed before super.onCreate

- **WHEN** `MainActivity.kt` is inspected
- **THEN** the call to `installSplashScreen()` SHALL appear before `super.onCreate(savedInstanceState)` in the onCreate body

#### Scenario: Keep-on-screen predicate releases when state leaves Loading

- **WHEN** the bootstrap coroutine completes a `refresh()` and the state transitions away from `Loading`
- **THEN** the next platform frame callback SHALL evaluate the predicate as `false` and dismiss the system splash

### Requirement: `MainActivity` reactively replaces the back stack when SessionState changes

`MainActivity.onCreate` SHALL collect `sessionStateProvider.state` across activity lifetime and invoke `navigator.replaceTo(Login)` on `SessionState.SignedOut` or `navigator.replaceTo(Main)` on `SessionState.SignedIn` (no-op on `Loading`). `navigator.replaceTo` SHALL be idempotent.

#### Scenario: SignedIn transition replaces Splash with Main on cold start

- **WHEN** the bootstrap completes with a present session and emits `SignedIn`
- **THEN** `Navigator.backStack` SHALL contain exactly `[Main]` (the prior `[Splash]` having been cleared by `replaceTo`)

#### Scenario: SignedOut transition replaces Splash with Login on cold start

- **WHEN** the bootstrap completes with no session and emits `SignedOut`
- **THEN** `Navigator.backStack` SHALL contain exactly `[Login]`

#### Scenario: SignedOut transition mid-session reroutes to Login

- **WHEN** the user is on `Main` and a `signOut()` triggers `SessionStateProvider.refresh()` emitting `SignedOut`
- **THEN** `MainActivity`'s collector SHALL invoke `navigator.replaceTo(Login)` and the visible destination SHALL transition to `Login` without any other code calling navigation explicitly

### Requirement: `Theme.Nubecita` is configured as a `Theme.SplashScreen`

`themes.xml` SHALL parent `Theme.Nubecita` to `Theme.SplashScreen` defining `windowSplashScreenBackground` as `@color/brand_sky_blue`, `windowSplashScreenAnimatedIcon` as `@drawable/ic_launcher_foreground`, and `postSplashScreenTheme` as `@style/Theme.Nubecita.PostSplash` (parented to `android:Theme.Material.Light.NoActionBar`). `AndroidManifest.xml` references `@style/Theme.Nubecita`.

#### Scenario: System splash renders the brand cloud on `#0A7AFF`

- **WHEN** the user cold-starts the app on Android 12+
- **THEN** the system splash SHALL render the white brand cloud (from `ic_launcher_foreground.xml`) centered on a `#0A7AFF` background, NOT the platform default white background with a default app icon

#### Scenario: Post-splash activity surface uses the prior light Material look

- **WHEN** the system splash dismisses
- **THEN** the activity's window background SHALL transition to the standard light Material background (from `android:Theme.Material.Light.NoActionBar`) via `postSplashScreenTheme`, NOT remain `#0A7AFF`

### Requirement: Adaptive launcher icon renders the brand cloud

Adaptive icons in `mipmap-anydpi-v26/ic_launcher*.xml` SHALL define three layers: background (`@drawable/ic_launcher_background`), foreground (`@drawable/ic_launcher_foreground` brand cloud in safe zone), and monochrome (`@drawable/ic_launcher_monochrome` silhouette for themed icons). Raster fallbacks under `mipmap-*dpi` SHALL NOT exist.

#### Scenario: Launcher renders brand cloud in any mask shape

- **WHEN** the user installs the app and views the launcher icon under any of Pixel Launcher's mask shapes (circle, squircle, teardrop, rounded square)
- **THEN** the icon SHALL render a white cloud centered on a `#0A7AFF` background, with the cloud fully visible (not clipped by the mask) regardless of which shape is selected

#### Scenario: Themed icon uses the monochrome layer

- **WHEN** the user enables Android 13+ themed icons and the launcher recolors the app icon
- **THEN** the cloud silhouette SHALL render tinted to the system accent color, with the launcher providing the matching tinted background — the `<monochrome>` layer SHALL be the source of the tinted shape (NOT the foreground or background)
