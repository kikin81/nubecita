## MODIFIED Requirements

### Requirement: `:app` defines a `Splash` NavKey as the start destination

`:app` SHALL define `@Serializable data object Splash : NavKey` as the start destination returned by `StartDestinationModule.provideStartDestination()`. `MainNavigation` SHALL register an `entry<Splash>` rendering a `NubecitaLogomark` (from `:designsystem`) centered over `MaterialTheme.colorScheme.background`. The system SplashScreen API overlays this composable via `setKeepOnScreenCondition` while session bootstrap runs; once the system splash dismisses, the composable below is already drawing the same brand cloud, eliminating any flash of empty surface between dismiss and the route swap to Login or Main.

#### Scenario: Cold start shows the system splash, then the routed destination

- **WHEN** the user cold-starts the app and a session is present in the store
- **THEN** the system splash SHALL be visible during bootstrap, then SHALL dismiss revealing `Main` (no flicker of an empty surface; the brand cloud rendered by the `Splash` composable provides visual continuity during the brief window before `navigator.replaceTo(Main)` swaps the back stack)

#### Scenario: Cold start with no session routes to Login

- **WHEN** the user cold-starts the app and `OAuthSessionStore.load()` returns `null`
- **THEN** the system splash SHALL be visible during bootstrap, then SHALL dismiss revealing `Login` (same continuity guarantee — the brand cloud is on-screen until `navigator.replaceTo(Login)` resolves)

## ADDED Requirements

### Requirement: `Theme.Nubecita` is configured as a `Theme.SplashScreen`

`app/src/main/res/values/themes.xml` SHALL parent `Theme.Nubecita` to `Theme.SplashScreen` (from `androidx.core:core-splashscreen`) and SHALL define the following items:

- `windowSplashScreenBackground` MUST be `@color/brand_sky_blue` (`#FF0A7AFF`).
- `windowSplashScreenAnimatedIcon` MUST be `@drawable/ic_launcher_foreground` (the brand cloud foreground vector).
- `postSplashScreenTheme` MUST be `@style/Theme.Nubecita.PostSplash`, where `Theme.Nubecita.PostSplash` is parented to `android:Theme.Material.Light.NoActionBar` (preserves the prior post-splash activity look).

The `:app` `<application>` (and/or `MainActivity`'s `<activity>`) entry in `AndroidManifest.xml` SHALL continue to reference `@style/Theme.Nubecita` — no manifest change is required.

#### Scenario: System splash renders the brand cloud on `#0A7AFF`

- **WHEN** the user cold-starts the app on Android 12+
- **THEN** the system splash SHALL render the white brand cloud (from `ic_launcher_foreground.xml`) centered on a `#0A7AFF` background, NOT the platform default white background with a default app icon

#### Scenario: Post-splash activity surface uses the prior light Material look

- **WHEN** the system splash dismisses
- **THEN** the activity's window background SHALL transition to the standard light Material background (from `android:Theme.Material.Light.NoActionBar`) via `postSplashScreenTheme`, NOT remain `#0A7AFF`

### Requirement: Adaptive launcher icon renders the brand cloud

`app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` and `mipmap-anydpi-v26/ic_launcher_round.xml` SHALL define a three-layer adaptive icon:

- `<background android:drawable="@drawable/ic_launcher_background"/>` — solid `@color/brand_sky_blue` 108dp rect.
- `<foreground android:drawable="@drawable/ic_launcher_foreground"/>` — white brand cloud (3 circles + rounded rect base) ported from `openspec/references/design-system/assets/logomark.svg`, sized to fit within the 72dp adaptive safe zone.
- `<monochrome android:drawable="@drawable/ic_launcher_monochrome"/>` — same cloud silhouette as the foreground, suitable for Android 13+ themed icons (the system tints it via the device accent).

The dpi-bucketed raster fallbacks under `mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher*.webp` SHALL NOT exist — `:app` `minSdk = 28`, so the adaptive icon is always used and the rasters are dead code. Lint warnings about a missing raster fallback for `mipmap-anydpi-v26` are acceptable.

#### Scenario: Launcher renders brand cloud in any mask shape

- **WHEN** the user installs the app and views the launcher icon under any of Pixel Launcher's mask shapes (circle, squircle, teardrop, rounded square)
- **THEN** the icon SHALL render a white cloud centered on a `#0A7AFF` background, with the cloud fully visible (not clipped by the mask) regardless of which shape is selected

#### Scenario: Themed icon uses the monochrome layer

- **WHEN** the user enables Android 13+ themed icons and the launcher recolors the app icon
- **THEN** the cloud silhouette SHALL render tinted to the system accent color, with the launcher providing the matching tinted background — the `<monochrome>` layer SHALL be the source of the tinted shape (NOT the foreground or background)
