## Why

The app currently ships the default Android Studio green-droid launcher icon and the system splash falls back to platform defaults because `Theme.Nubecita` (`app/src/main/res/values/themes.xml`) is not a `Theme.SplashScreen` — `installSplashScreen()` in `MainActivity.kt:41` resolves to a default-themed splash. The reference design system at `openspec/references/design-system/assets/` has been carrying the brand cloud + wordmark (`app-icon.svg`, `logomark.svg`, `logomark-mono.svg`, `logo.svg`) since the design-system reference was checked in (Apr 24); time to land it.

This change replaces the placeholder launcher icon with our adaptive brand mark, fixes the system-splash theme so it renders the brand cloud on `#0A7AFF` instead of platform defaults, ships `NubecitaLogo` + `NubecitaLogomark` composables in `:designsystem`, and wires those composables into the post-system-splash `Splash` route and the `LoginScreen` so the brand identity is consistent across the cold-start funnel.

## What Changes

- **Adaptive launcher icon (`:app`)** — port `logomark.svg` to `ic_launcher_foreground.xml` (white cloud, sized inside the 72dp adaptive safe zone), replace `ic_launcher_background.xml` with a solid `#0A7AFF` rect, and add a dedicated `ic_launcher_monochrome.xml` (cloud silhouette) for Android 13+ themed icons. The `mipmap-anydpi-v26/ic_launcher.xml` already wires all three slots — only the `<monochrome>` pointer changes (currently aliased to foreground; gets its own drawable).
- **Brand color resource** — new `res/values/colors.xml` with `@color/brand_sky_blue` = `#0A7AFF` as the single source of truth.
- **Delete legacy raster mipmaps** — the dpi-bucketed `mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher*.webp` files are vestigial under `minSdk = 28` (every device renders adaptive). Removed.
- **System SplashScreen theme** — repoint `Theme.Nubecita` parent to `Theme.SplashScreen`, set `windowSplashScreenBackground` = `@color/brand_sky_blue`, `windowSplashScreenAnimatedIcon` = `@drawable/ic_launcher_foreground`, `postSplashScreenTheme` = a new `Theme.Nubecita.PostSplash` carrying the prior `Material.Light.NoActionBar` parent.
- **`NubecitaLogomark` composable (`:designsystem`)** — new `component/NubecitaLogo.kt` exposing one public `@Composable` entry point. `NubecitaLogomark` renders the cloud-only mark from `nubecita_logomark.xml` (square aspect, default tint `MaterialTheme.colorScheme.primary`).
- **`Splash` route renders the logomark** — `app/Navigation.kt`'s `entry<Splash>` swaps from an empty `Box` to a centered `NubecitaLogomark` over `MaterialTheme.colorScheme.background`, eliminating the brief flash of empty surface between system-splash dismiss and the route swap to Login or Main.

**Descoped from this change** (deferred to a follow-up):

- Wordmark composable (`NubecitaLogo`) and `nubecita_logo.xml` vector — wordmark glyph→path conversion needs an offline tool pass and a font choice the design system hasn't pinned. Out of scope until the wordmark source is settled. Spec deltas, design Decisions 3 + 5, Open Q1, and tasks 0.x / 3.2 / 3.4 (NubecitaLogo half) / 3.5 (NubecitaLogo half) / 4.1 (logo half) / 6 / 7 captured the intent and remain in the change file as deferred record.
- `LoginScreen` brand-mark insertion — the original intent was to drop the wordmark logo above the title; without the wordmark, the standalone logomark would be redundant with the existing title text. Defer until the wordmark lands.

## Capabilities

### New Capabilities
<!-- None — extends existing capabilities only. -->

### Modified Capabilities
- `design-system`: adds `NubecitaLogomark` composable backed by a vector drawable; adds one content-description string resource.
- `app-splash-routing`: `Theme.Nubecita` becomes a `Theme.SplashScreen` configured with the brand cloud + sky-blue background; the `Splash` `entry<Splash>` renders `NubecitaLogomark` instead of an empty `Box`.

### Removed Capabilities
<!-- None. -->

## Impact

**Code:**
- `app/src/main/res/values/themes.xml` — `Theme.Nubecita` parent + window splash attrs; new `Theme.Nubecita.PostSplash` style.
- `app/src/main/res/values/colors.xml` — new file, `@color/brand_sky_blue`.
- `app/src/main/res/drawable/ic_launcher_foreground.xml` — replaced.
- `app/src/main/res/drawable/ic_launcher_background.xml` — replaced.
- `app/src/main/res/drawable/ic_launcher_monochrome.xml` — new file.
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` — `<monochrome>` pointer change.
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` — same `<monochrome>` pointer change.
- `app/src/main/res/mipmap-{m,h,xh,xxh,xxxh}dpi/ic_launcher*.webp` — deleted (10 files).
- `app/src/main/java/net/kikin/nubecita/Navigation.kt` — `entry<Splash>` body.
- `designsystem/src/main/kotlin/.../component/NubecitaLogo.kt` — new file (logomark only; wordmark `NubecitaLogo` deferred).
- `designsystem/src/main/res/drawable/nubecita_logomark.xml` — new file.
- `designsystem/src/main/res/values/strings.xml` — one new content-description string (`logomark_content_description`).
- `designsystem/src/screenshotTest/.../component/NubecitaLogoScreenshotTest.kt` — new file (3 logomark baselines).

**Tests:**
- New `NubecitaLogoScreenshotTest.kt` baselines: 3 PNGs — logomark default-light, logomark default-dark, logomark custom-tint-light. (Wordmark baselines deferred with the wordmark composable.)
- No new unit tests (composables are pure rendering; theme is XML-only).

**Build / runtime:**
- No new dependencies. `androidx.core:core-splashscreen` is already declared directly by `:app` (`app/build.gradle.kts:69` — `implementation(libs.androidx.core.splashscreen)`); `installSplashScreen()` is in use.
- `apk` size reduced slightly — the deleted webp's are larger than the new vector drawables.

**Rollout:** Single PR. No feature flag, no migration. Visible on first install + on app upgrade (the launcher caches icon assets, so existing installs may need the launcher to refresh; manual fix is reinstall).
