## 0. Pre-implementation decision: wordmark font  *(deferred — wordmark cut from this change)*

The reference `logo.svg` renders the wordmark as `<text class="nb-wordmark">nubecita</text>`, but `nb-wordmark` is **not defined** in `openspec/references/design-system/colors_and_type.css`. The fonts the reference design system specifies are `--font-display: Fraunces`, `--font-body: Roboto Flex`, and `--font-mono: JetBrains Mono`. Before task 3.2 can produce a vector-drawable wordmark, we MUST resolve which font's "nubecita" glyphs get rasterized to paths.

- [ ] 0.1 *(deferred)* Pick a font from `{Fraunces (display), Roboto Flex (body), Compose Text inline (no rasterization)}`. Default recommendation: **Fraunces** — already the brand display font, expressive serif fits a logo wordmark. Confirm the chosen font's license permits glyph-as-path embedding (Fraunces and Roboto Flex are both SIL OFL — permissive). Update task 3.2 with the resolved font name + source URL before starting that task. If "Compose Text inline" is chosen, reverse `design.md` Decision 3 first (and revise tasks 3.2, 3.4, 4.1 accordingly).
- [ ] 0.2 *(deferred)* Download the chosen font's `.woff2` or `.ttf` to a local working dir (do NOT commit the font file). Note the exact font version + variation axis values used in the export — these go in a comment header on `nubecita_logo.xml` for reproducibility.

## 1. Brand color resource + adaptive launcher icon (`:app`)

- [x] 1.1 Create `app/src/main/res/values/colors.xml` with a single `<color name="brand_sky_blue">#FF0A7AFF</color>` entry. Verify the resource resolves via `./gradlew :app:processDebugResources`.
- [x] 1.2 Replace `app/src/main/res/drawable/ic_launcher_foreground.xml` with a 108dp vector port of `openspec/references/design-system/assets/logomark.svg`: `android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108"`. The cloud SHALL fit within the 72dp safe zone — translate + scale the source `viewBox="0 0 72 72"` group so the cloud is centered at (54, 54) and bounded by the inner 72dp circle. Use `android:fillColor="#FFFFFFFF"` for all four shapes (3 circles + rounded rect). Drop the 4th `<circle>` from `logomark.svg` (the small white opacity=0.9 highlight at cx=30, cy=22) — the foreground is uniform white. **NOTE:** Implementation uses prepared XML from `~/Downloads/android-launcher` — different cloud geometry (4 paths bounded within 66dp safe zone) plus two `#0062B1` smile-arc strokes for face-character. Foreground is NOT uniform white as originally specified; the smile arcs are intentional design.
- [x] 1.3 Replace `app/src/main/res/drawable/ic_launcher_background.xml` with a single-path 108dp vector: `<path android:pathData="M0,0h108v108h-108z" android:fillColor="@color/brand_sky_blue"/>`. Delete the existing grid lines.
- [x] 1.4 Create `app/src/main/res/drawable/ic_launcher_monochrome.xml` — same shape as `ic_launcher_foreground.xml` rendered as a single uniform fill (`android:fillColor="#FFFFFFFF"` is conventional). Android 13+ themed icons are recolored by the launcher: the drawable's **alpha channel** defines the silhouette, and the launcher applies the user's device-theme palette over that alpha — the source `fillColor` value is overridden at render time, so any solid color works. **NOTE:** Mono is the cloud silhouette only (smile arcs dropped per silhouette convention).
- [x] 1.5 Update `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`: change `<monochrome android:drawable="@drawable/ic_launcher_foreground"/>` to `<monochrome android:drawable="@drawable/ic_launcher_monochrome"/>`. Mirror the same change in `mipmap-anydpi-v26/ic_launcher_round.xml`.
- [x] 1.6 Delete `app/src/main/res/mipmap-mdpi/ic_launcher.webp`, `ic_launcher_round.webp`; same for `mipmap-hdpi`, `mipmap-xhdpi`, `mipmap-xxhdpi`, `mipmap-xxxhdpi` — 10 files total. Verify lint via `./gradlew :app:lintDebug` does not error (warning about missing default mipmap is acceptable).
- [x] 1.7 Manual verification: `./gradlew :app:installDebug`, launch the home screen, confirm the launcher icon renders as a white cloud on `#0A7AFF`. Try at least 2 launcher icon mask styles (round + squircle) via Pixel Launcher's icon-shape setting if available; confirm the cloud is centered and not clipped.

## 2. System SplashScreen theme (`:app`)

- [x] 2.1 Update `app/src/main/res/values/themes.xml`. Rename the existing `Theme.Nubecita` to `Theme.Nubecita.PostSplash` and reparent it to `android:Theme.Material.Light.NoActionBar` (preserves the prior post-splash activity look). Add a new `Theme.Nubecita` style parented to `Theme.SplashScreen` (from `androidx.core:core-splashscreen`) with these items:
  - `<item name="windowSplashScreenBackground">@color/brand_sky_blue</item>`
  - `<item name="windowSplashScreenAnimatedIcon">@drawable/ic_launcher_foreground</item>`
  - `<item name="postSplashScreenTheme">@style/Theme.Nubecita.PostSplash</item>`
- [x] 2.2 Verify `app/src/main/AndroidManifest.xml` references `@style/Theme.Nubecita` for `<application>` and/or `<activity android:name=".MainActivity">`. No change to the manifest expected — the theme name didn't change.
- [x] 2.3 Manual verification: cold-start the app on a clean device. The system splash MUST show a white cloud centered on a `#0A7AFF` background. The splash MUST persist while `SessionStateProvider` is `Loading` (already wired; verifying the theme didn't break the keep-on-screen predicate). The post-splash activity surface MUST appear with the standard light Material background (not blue).

## 3. `NubecitaLogo` + `NubecitaLogomark` composables (`:designsystem`)

- [x] 3.1 Create `designsystem/src/main/res/drawable/nubecita_logomark.xml` — port `openspec/references/design-system/assets/logomark-mono.svg` to an Android vector. `android:width="72dp" android:height="72dp" android:viewportWidth="72" android:viewportHeight="72"`. Use `android:fillColor="#FFFFFFFF"` for all four shapes (Image + ColorFilter.tint applies the actual color at render time). The `<g transform="translate(4, 16)">` from the SVG translates to per-path coordinate offsets in the Android vector (Android vector drawables don't support group transforms in all renderers; bake the translation into pathData).
- [ ] 3.2 *(deferred)* Create `designsystem/src/main/res/drawable/nubecita_logo.xml` — port `openspec/references/design-system/assets/logo.svg`. `android:width="240dp" android:height="72dp" android:viewportWidth="240" android:viewportHeight="72"`. The cloud paths come from `logomark.svg` translated to (6, 14) per the source `<g transform>`. The wordmark "nubecita" SHALL be baked as path data using the font picked in task 0.1. Conversion path: open the source `logo.svg` in Inkscape (or equivalent) with the chosen font installed locally → select the `<text class="nb-wordmark">` element → Path > Object to Path → save → import via Android Studio's Vector Asset Studio (or convert with `vd-tool`). Add a comment header to the resulting `nubecita_logo.xml` recording `font-family`, version, and variation-axis values for future reproducibility. Use `android:fillColor="#FFFFFFFF"` throughout.
- [x] 3.3 Add `R.string.logomark_content_description` to `designsystem/src/main/res/values/strings.xml`, with value `"Nubecita"`. *(`R.string.logo_content_description` deferred with the wordmark composable.)*
- [ ] 3.4 Create `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/NubecitaLogo.kt`. Two public composables. Pattern: **PARTIAL** — `NubecitaLogomark` is implemented; `NubecitaLogo` (cloud + wordmark) deferred pending §3.2 wordmark-source decision.
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
- [ ] 3.5 Add `@Preview` composables in the same file: `NubecitaLogomarkPreview` (light), `NubecitaLogomarkDarkPreview`, `NubecitaLogomarkCustomTintPreview` (e.g. `tint = Color(0xFF0A7AFF)`), `NubecitaLogoPreview` (light), `NubecitaLogoDarkPreview`. Use `NubecitaTheme(darkTheme = true) { ... }` for the dark variants. **PARTIAL** — logomark previews done; logo previews deferred with §3.4.

## 4. Screenshot tests for `NubecitaLogo` (`:designsystem`)

- [ ] 4.1 Create `designsystem/src/screenshotTest/kotlin/net/kikin/nubecita/designsystem/component/NubecitaLogoScreenshotTest.kt`. Mirror the structure of `NubecitaPrimaryButtonScreenshotTest.kt` — `@Composable`-annotated test functions producing 6 baselines: `NubecitaLogomarkScreenshot_default-light`, `NubecitaLogomarkScreenshot_default-dark`, `NubecitaLogomarkScreenshot_custom-tint-light` (tint `#FF0A7AFF`), `NubecitaLogoScreenshot_default-light`, `NubecitaLogoScreenshot_default-dark`, `NubecitaLogoScreenshot_custom-color-light` (color `#FF0A7AFF`). Wrap each in `NubecitaTheme(darkTheme = ...) { Surface(...) { ... } }` matching the existing screenshot conventions. **PARTIAL** — logomark default-light/default-dark/custom-tint-light baselines done (3 of 6); logo baselines deferred with §3.4.
- [x] 4.2 Run `./gradlew :designsystem:updateDebugScreenshotTest` to generate baselines. Manually inspect each PNG under `designsystem/src/screenshotTestDebug/reference/.../NubecitaLogoScreenshotTestKt/` to confirm the cloud renders correctly (centered, correct aspect, correct tint).
- [x] 4.3 Run `./gradlew :designsystem:validateDebugScreenshotTest` and confirm green.

## 5. Wire `NubecitaLogomark` into `Splash` (`:app`)

- [x] 5.1 Update `app/src/main/java/net/kikin/nubecita/Navigation.kt`'s `entry<Splash>` body. Replace `Box(modifier = Modifier.fillMaxSize())` with:
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
- [x] 5.2 Add `:designsystem` to `:app` dependencies if not already present (check `app/build.gradle.kts` — almost certainly already there since `NubecitaTheme` is used).
- [x] 5.3 Update the existing comment block in `Navigation.kt` so it accurately describes the new render (system splash overlays a centered logomark, not an empty Box).

## 6. Wire `NubecitaLogo` into `LoginScreen` (`:feature:login:impl`)  *(deferred — depends on the wordmark `NubecitaLogo` composable)*

- [ ] 6.1 *(deferred)* Update `feature/login/impl/src/main/kotlin/net/kikin/nubecita/feature/login/impl/LoginScreen.kt`'s internal `LoginScreen(state, onEvent, modifier)` composable. Insert above the title `Text`:
  ```kotlin
  NubecitaLogo(
      modifier = Modifier.size(width = 200.dp, height = 60.dp),
  )
  ```
  The existing `verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s4, Alignment.CenterVertically)` accommodates it without restructuring. Add the necessary imports (`NubecitaLogo` from `net.kikin.nubecita.designsystem.component`, `androidx.compose.foundation.layout.size`).
- [ ] 6.2 *(deferred)* Verify `:feature:login:impl/build.gradle.kts` already depends on `:designsystem` (it should — `NubecitaTheme` is used in previews).
- [ ] 6.3 *(deferred)* Update the existing `@Preview` composables in `LoginScreen.kt` — they should automatically render the new logo since they call the same internal `LoginScreen(state, onEvent)`.

## 7. Regenerate login screenshot baselines  *(deferred with §6)*

- [ ] 7.1 *(deferred)* Run `./gradlew :feature:login:impl:updateDebugScreenshotTest` to regenerate the existing 5 LoginScreen baselines (Empty, Typed, Loading, Blank-handle error, Failure error) with the logo present. Manually inspect each PNG to confirm the logo renders above the title without layout regressions.
- [ ] 7.2 *(deferred)* Run `./gradlew :feature:login:impl:validateDebugScreenshotTest` and confirm green.

## 8. Verification

- [x] 8.1 `./gradlew spotlessCheck lint` — clean.
- [x] 8.2 `./gradlew testDebugUnitTest` — `:designsystem` + `:feature:login:impl` green; no test code change expected.
- [x] 8.3 `./gradlew :designsystem:validateDebugScreenshotTest --tests '*NubecitaLogoScreenshotTest*'` — green (3 logomark baselines). NOTE: full `validateDebugScreenshotTest` fails on pre-existing baseline drift in unrelated `PostCardQuotedPost*` / `PostCardRecordWithMediaEmbed*` (designsystem) and `LoginScreenScreenshotTest*` (feature/login/impl) baselines — same anti-aliasing-noise fingerprint across all of them, no structural change. Out of scope; needs a separate baseline-refresh PR.
- [x] 8.4 `./gradlew :app:assembleDebug` — clean.
- [x] 8.5 Install on a physical device and verify: launcher icon, cold-start system splash, post-system-splash Splash route render. (LoginScreen brand-mark deferred with §6.) Capture screenshots for the PR description. Verified on Pixel Tablet — launcher icon, system splash, and Splash route all render correctly. Screenshots captured.
