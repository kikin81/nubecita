## 1. Brand color resource + adaptive launcher icon (`:app`)

- [ ] 1.1 Create `app/src/main/res/values/colors.xml` with a single `<color name="brand_sky_blue">#FF0A7AFF</color>` entry. Verify the resource resolves via `./gradlew :app:processDebugResources`.
- [ ] 1.2 Replace `app/src/main/res/drawable/ic_launcher_foreground.xml` with a 108dp vector port of `openspec/references/design-system/assets/logomark.svg`: `android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108"`. The cloud SHALL fit within the 72dp safe zone — translate + scale the source `viewBox="0 0 72 72"` group so the cloud is centered at (54, 54) and bounded by the inner 72dp circle. Use `android:fillColor="#FFFFFFFF"` for all four shapes (3 circles + rounded rect). Drop the 4th `<circle>` from `logomark.svg` (the small white opacity=0.9 highlight at cx=30, cy=22) — the foreground is uniform white.
- [ ] 1.3 Replace `app/src/main/res/drawable/ic_launcher_background.xml` with a single-path 108dp vector: `<path android:pathData="M0,0h108v108h-108z" android:fillColor="@color/brand_sky_blue"/>`. Delete the existing grid lines.
- [ ] 1.4 Create `app/src/main/res/drawable/ic_launcher_monochrome.xml` — same shape as `ic_launcher_foreground.xml` but with `android:fillColor="#FFFFFFFF"` (the system tints monochrome layers via `windowAccentColor`; the source color is irrelevant beyond opacity, but `#FFFFFFFF` matches the foreground for visual continuity in dev tools).
- [ ] 1.5 Update `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`: change `<monochrome android:drawable="@drawable/ic_launcher_foreground"/>` to `<monochrome android:drawable="@drawable/ic_launcher_monochrome"/>`. Mirror the same change in `mipmap-anydpi-v26/ic_launcher_round.xml`.
- [ ] 1.6 Delete `app/src/main/res/mipmap-mdpi/ic_launcher.webp`, `ic_launcher_round.webp`; same for `mipmap-hdpi`, `mipmap-xhdpi`, `mipmap-xxhdpi`, `mipmap-xxxhdpi` — 10 files total. Verify lint via `./gradlew :app:lintDebug` does not error (warning about missing default mipmap is acceptable).
- [ ] 1.7 Manual verification: `./gradlew :app:installDebug`, launch the home screen, confirm the launcher icon renders as a white cloud on `#0A7AFF`. Try at least 2 launcher icon mask styles (round + squircle) via Pixel Launcher's icon-shape setting if available; confirm the cloud is centered and not clipped.

## 2. System SplashScreen theme (`:app`)

- [ ] 2.1 Update `app/src/main/res/values/themes.xml`. Rename the existing `Theme.Nubecita` to `Theme.Nubecita.PostSplash` and reparent it to `android:Theme.Material.Light.NoActionBar` (preserves the prior post-splash activity look). Add a new `Theme.Nubecita` style parented to `Theme.SplashScreen` (from `androidx.core:core-splashscreen`) with these items:
  - `<item name="windowSplashScreenBackground">@color/brand_sky_blue</item>`
  - `<item name="windowSplashScreenAnimatedIcon">@drawable/ic_launcher_foreground</item>`
  - `<item name="postSplashScreenTheme">@style/Theme.Nubecita.PostSplash</item>`
- [ ] 2.2 Verify `app/src/main/AndroidManifest.xml` references `@style/Theme.Nubecita` for `<application>` and/or `<activity android:name=".MainActivity">`. No change to the manifest expected — the theme name didn't change.
- [ ] 2.3 Manual verification: cold-start the app on a clean device. The system splash MUST show a white cloud centered on a `#0A7AFF` background. The splash MUST persist while `SessionStateProvider` is `Loading` (already wired; verifying the theme didn't break the keep-on-screen predicate). The post-splash activity surface MUST appear with the standard light Material background (not blue).

## 3. `NubecitaLogo` + `NubecitaLogomark` composables (`:designsystem`)

- [ ] 3.1 Create `designsystem/src/main/res/drawable/nubecita_logomark.xml` — port `openspec/references/design-system/assets/logomark-mono.svg` to an Android vector. `android:width="72dp" android:height="72dp" android:viewportWidth="72" android:viewportHeight="72"`. Use `android:fillColor="#FFFFFFFF"` for all four shapes (Image + ColorFilter.tint applies the actual color at render time). The `<g transform="translate(4, 16)">` from the SVG translates to per-path coordinate offsets in the Android vector (Android vector drawables don't support group transforms in all renderers; bake the translation into pathData).
- [ ] 3.2 Create `designsystem/src/main/res/drawable/nubecita_logo.xml` — port `openspec/references/design-system/assets/logo.svg`. `android:width="240dp" android:height="72dp" android:viewportWidth="240" android:viewportHeight="72"`. The cloud paths come from `logomark.svg` translated to (6, 14) per the source `<g transform>`. The wordmark "nubecita" SHALL be baked as path data — convert the SVG `<text>` to paths using a Vector Asset Studio import OR a manual conversion via `inkscape --export-text-to-path` from the source font (verify font license permits glyph embedding before checking in). Use `android:fillColor="#FFFFFFFF"` throughout.
- [ ] 3.3 Add `R.string.logomark_content_description` and `R.string.logo_content_description` to `designsystem/src/main/res/values/strings.xml`, both with value `"Nubecita"`.
- [ ] 3.4 Create `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/NubecitaLogo.kt`. Two public composables. Pattern:
  ```kotlin
  @Composable
  fun NubecitaLogomark(
      modifier: Modifier = Modifier,
      tint: Color = MaterialTheme.colorScheme.primary,
  ) {
      Image(
          painter = painterResource(R.drawable.nubecita_logomark),
          contentDescription = stringResource(R.string.logomark_content_description),
          colorFilter = ColorFilter.tint(tint),
          modifier = modifier,
      )
  }

  @Composable
  fun NubecitaLogo(
      modifier: Modifier = Modifier,
      color: Color = MaterialTheme.colorScheme.primary,
  ) {
      Image(
          painter = painterResource(R.drawable.nubecita_logo),
          contentDescription = stringResource(R.string.logo_content_description),
          colorFilter = ColorFilter.tint(color),
          modifier = modifier,
      )
  }
  ```
  Add a brief KDoc on each documenting the source asset, default tint, and intended-aspect (square for logomark, ~3.3:1 for logo).
- [ ] 3.5 Add `@Preview` composables in the same file: `NubecitaLogomarkPreview` (light), `NubecitaLogomarkDarkPreview`, `NubecitaLogomarkCustomTintPreview` (e.g. `tint = Color(0xFF0A7AFF)`), `NubecitaLogoPreview` (light), `NubecitaLogoDarkPreview`. Use `NubecitaTheme(darkTheme = true) { ... }` for the dark variants.

## 4. Screenshot tests for `NubecitaLogo` (`:designsystem`)

- [ ] 4.1 Create `designsystem/src/screenshotTest/kotlin/net/kikin/nubecita/designsystem/component/NubecitaLogoScreenshotTest.kt`. Mirror the structure of `NubecitaPrimaryButtonScreenshotTest.kt` — `@Composable`-annotated test functions producing 6 baselines: `NubecitaLogomarkScreenshot_default-light`, `NubecitaLogomarkScreenshot_default-dark`, `NubecitaLogomarkScreenshot_custom-tint-light` (tint `#FF0A7AFF`), `NubecitaLogoScreenshot_default-light`, `NubecitaLogoScreenshot_default-dark`, `NubecitaLogoScreenshot_custom-color-light` (color `#FF0A7AFF`). Wrap each in `NubecitaTheme(darkTheme = ...) { Surface(...) { ... } }` matching the existing screenshot conventions.
- [ ] 4.2 Run `./gradlew :designsystem:updateDebugScreenshotTest` to generate baselines. Manually inspect each PNG under `designsystem/src/screenshotTestDebug/reference/.../NubecitaLogoScreenshotTestKt/` to confirm the cloud renders correctly (centered, correct aspect, correct tint).
- [ ] 4.3 Run `./gradlew :designsystem:validateDebugScreenshotTest` and confirm green.

## 5. Wire `NubecitaLogomark` into `Splash` (`:app`)

- [ ] 5.1 Update `app/src/main/java/net/kikin/nubecita/Navigation.kt`'s `entry<Splash>` body. Replace `Box(modifier = Modifier.fillMaxSize())` with:
  ```kotlin
  Box(
      modifier = Modifier
          .fillMaxSize()
          .background(MaterialTheme.colorScheme.background),
      contentAlignment = Alignment.Center,
  ) {
      NubecitaLogomark(
          modifier = Modifier.size(96.dp),
      )
  }
  ```
  Add the necessary imports (`Alignment`, `background`, `size`, `dp`, `MaterialTheme`, `NubecitaLogomark` from `net.kikin.nubecita.designsystem.component`).
- [ ] 5.2 Add `:designsystem` to `:app` dependencies if not already present (check `app/build.gradle.kts` — almost certainly already there since `NubecitaTheme` is used).
- [ ] 5.3 Update the existing comment block in `Navigation.kt` so it accurately describes the new render (system splash overlays a centered logomark, not an empty Box).

## 6. Wire `NubecitaLogo` into `LoginScreen` (`:feature:login:impl`)

- [ ] 6.1 Update `feature/login/impl/src/main/kotlin/net/kikin/nubecita/feature/login/impl/LoginScreen.kt`'s internal `LoginScreen(state, onEvent, modifier)` composable. Insert above the title `Text`:
  ```kotlin
  NubecitaLogo(
      modifier = Modifier.size(width = 200.dp, height = 60.dp),
  )
  ```
  The existing `verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s4, Alignment.CenterVertically)` accommodates it without restructuring. Add the necessary imports (`NubecitaLogo` from `net.kikin.nubecita.designsystem.component`, `androidx.compose.foundation.layout.size`).
- [ ] 6.2 Verify `:feature:login:impl/build.gradle.kts` already depends on `:designsystem` (it should — `NubecitaTheme` is used in previews).
- [ ] 6.3 Update the existing `@Preview` composables in `LoginScreen.kt` — they should automatically render the new logo since they call the same internal `LoginScreen(state, onEvent)`.

## 7. Regenerate login screenshot baselines

- [ ] 7.1 Run `./gradlew :feature:login:impl:updateDebugScreenshotTest` to regenerate the existing 5 LoginScreen baselines (Empty, Typed, Loading, Blank-handle error, Failure error) with the logo present. Manually inspect each PNG to confirm the logo renders above the title without layout regressions.
- [ ] 7.2 Run `./gradlew :feature:login:impl:validateDebugScreenshotTest` and confirm green.

## 8. Verification

- [ ] 8.1 `./gradlew spotlessCheck lint` — clean.
- [ ] 8.2 `./gradlew testDebugUnitTest` — all green (no test code change expected; verifying no compile-time regression from the Splash render swap).
- [ ] 8.3 `./gradlew validateDebugScreenshotTest` — all green (designsystem + login screenshots).
- [ ] 8.4 `./gradlew :app:assembleDebug` — clean.
- [ ] 8.5 Install on a physical device and verify: launcher icon, cold-start system splash, post-system-splash Splash route render, LoginScreen layout. Capture screenshots for the PR description.
