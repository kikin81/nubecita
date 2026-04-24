## 1. Gradle module scaffold

- [ ] 1.1 Create `designsystem/` at the repo root with a `build.gradle.kts` applying `com.android.library` (not `com.android.application`), `org.jetbrains.kotlin.android`, `com.google.devtools.ksp` (only if needed; theme has no KSP-generated code, can omit), `org.jetbrains.kotlin.plugin.compose`, and `com.squareup.sort-dependencies`. Set `namespace = "net.kikin.nubecita.designsystem"`, `compileSdk = 37`, `minSdk = 24`, `compileOptions { sourceCompatibility/targetCompatibility = VERSION_17 }`, `kotlin { jvmToolchain(17) }`, `buildFeatures { compose = true }`.
- [ ] 1.2 Register the module in `settings.gradle.kts` via `include(":designsystem")`.
- [ ] 1.3 In `designsystem/build.gradle.kts` `dependencies { }` block, add: `implementation(platform(libs.androidx.compose.bom))`, `implementation(libs.androidx.compose.material3)`, `implementation(libs.androidx.compose.ui)`, `implementation(libs.androidx.compose.ui.tooling.preview)`, `implementation(libs.androidx.core.ktx)`, `implementation(libs.androidx.compose.ui.text.google.fonts)`. Also add `debugImplementation(libs.androidx.compose.ui.tooling)` for `@Preview` rendering, and `testImplementation(libs.junit)`.
- [ ] 1.4 Add `androidx-compose-ui-text-google-fonts = { module = "androidx.compose.ui:ui-text-google-fonts" }` to `[libraries]` in `gradle/libs.versions.toml` (version comes from the Compose BOM).
- [ ] 1.5 Run Sonatype check on `pkg:maven/androidx.compose.ui/ui-text-google-fonts` before the coord lands — stop and flag if it's malicious, EOL, or policy-non-compliant.
- [ ] 1.6 In `app/build.gradle.kts` `dependencies { }` block, add `implementation(project(":designsystem"))`.
- [ ] 1.7 Run `./gradlew :designsystem:assembleDebug` and confirm it compiles (empty module; validates Gradle wiring).

## 2. Resources — fonts + cert hashes

- [ ] 2.1 Download Roboto Flex variable `.ttf` from `https://fonts.google.com/specimen/Roboto+Flex` (variable axes `wght 100..1000, opsz 8..144, slnt -10..0, GRAD -200..150, YOPQ 25..135, YTAS 649..854, YTDE -305..-98, YTFI 560..788, YTLC 416..570, YTUC 528..760, wdth 25..151`). Place at `designsystem/src/main/res/font/roboto_flex.ttf`.
- [ ] 2.2 Download JetBrains Mono variable `.ttf` (weight axis 100..800) from `https://fonts.google.com/specimen/JetBrains+Mono`. Place at `designsystem/src/main/res/font/jetbrains_mono.ttf`.
- [ ] 2.3 Create `designsystem/src/main/res/values/font_certs.xml` declaring the Google Fonts provider cert array. Use the canonical `com.google.android.gms.fonts` provider hashes from AOSP docs. Example content:
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <resources>
      <array name="com_google_android_gms_fonts_certs">
          <item>@array/com_google_android_gms_fonts_certs_dev</item>
          <item>@array/com_google_android_gms_fonts_certs_prod</item>
      </array>
      <string-array name="com_google_android_gms_fonts_certs_dev">
          <item>MIIEqDCCA5CgAwIBAgIJANWFuGx90071MA0GCSqGSIb3DQEBBAUAMIGUMQswCQYD... (full dev cert) ...</item>
      </string-array>
      <string-array name="com_google_android_gms_fonts_certs_prod">
          <item>MIIEQzCCAyugAwIBAgIJAMLgh0ZkSjCNMA0GCSqGSIb3DQEBBAUAMHQxCzAJBgNV... (full prod cert) ...</item>
      </string-array>
  </resources>
  ```
  (Use the exact cert strings from `https://developer.android.com/develop/ui/views/text-and-emoji/downloadable-fonts#using-downloadable-fonts-without-androidx` — don't make them up.)

## 3. Token files

- [ ] 3.1 `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/Color.kt` — declare `object NubecitaPalette { val Sky0..Sky100, Peach0..Peach98, Lilac30..Lilac95, Neutral0..Neutral100, NeutralVariant30..NeutralVariant90, Error40..Error90, Success40..Success90, Warning40, Warning80 }` matching every hex in `openspec/references/design-system/colors_and_type.css`. Then declare six `ColorScheme`s: `nubecitaLightColorScheme()`, `nubecitaLightMediumContrastColorScheme()`, `nubecitaLightHighContrastColorScheme()`, `nubecitaDarkColorScheme()`, `nubecitaDarkMediumContrastColorScheme()`, `nubecitaDarkHighContrastColorScheme()` as internal functions returning fully-populated `ColorScheme` instances. Light-mode maps per lines 87-138 of the CSS; dark-mode per lines 259-312. Medium-contrast and high-contrast schemes derive by shifting tonal anchor points (high-contrast: primary = tone-30 light / tone-90 dark, outline = tone-20 / tone-95 — verify against M3's canonical contrast-variant generator).
- [ ] 3.2 `Shape.kt` — declare `val nubecitaShapes = Shapes(extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp), extraLarge = RoundedCornerShape(28.dp))`. Declare `data class NubecitaExtendedShape(val extraExtraLarge: Shape = RoundedCornerShape(36.dp), val pill: Shape = CircleShape, val sheet: Shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp, bottomStart = 0.dp, bottomEnd = 0.dp))`.
- [ ] 3.3 `Fonts.kt` — declare the `GoogleFont.Provider` using the cert array from task 2.3; declare `val FrauncesFontFamily` via `FontFamily(Font(googleFont = GoogleFont("Fraunces"), fontProvider = provider, variationSettings = FontVariation.Settings(FontVariation.weight(600), FontVariation.Setting("SOFT", 50f))))` (API 26+ will honor the variation; 24-25 falls back). Declare `val MaterialSymbolsRoundedFontFamily` similarly for icons. Declare `val RobotoFlexFontFamily = FontFamily(Font(R.font.roboto_flex))` and `val JetBrainsMonoFontFamily = FontFamily(Font(R.font.jetbrains_mono))` from bundled resources.
- [ ] 3.4 `Type.kt` — declare `val nubecitaTypography = Typography(...)` populating all 15 M3 roles per `colors_and_type.css` lines 220-253 (sizes, line-heights, weights, letter spacing). Assignments:
  - `displayLarge/Medium/Small`, `headlineLarge` → `FrauncesFontFamily`, weight 600, negative letter-spacing
  - `headlineMedium/Small` → `RobotoFlexFontFamily`, weight 700
  - `titleLarge/Medium/Small`, `bodyLarge/Medium/Small`, `labelLarge/Medium/Small` → `RobotoFlexFontFamily` at spec weights (400 body, 600 labels/titles)
  - `bodyLarge.fontSize = 17.sp, lineHeight = 26.sp` (NOT M3 default 16/24)
- [ ] 3.5 `Motion.kt` — construct two `MotionScheme` instances (`nubecitaStandardMotionScheme`, `nubecitaReducedMotionScheme`) using whatever constructor the current Material3 release exposes (builder DSL, factory fn, or anonymous implementation of the `MotionScheme` interface — check the symbols in `androidx.compose.material3.MotionScheme` at Compose BOM 2026.04). The standard scheme uses `spring(dampingRatio = ~0.65, stiffness = ~380)` for `defaultSpatialSpec` / `fastSpatialSpec` (~300ms settle), `spring(dampingRatio = ~0.55, stiffness = ~200)` for `slowSpatialSpec` (~500ms), and a `keyframes<Float>` matching the CSS `linear(0, 0.007, 0.030, 0.080 2.6%, 0.25 5.3%, 0.63 10%, 1.06 17%, 1.20 21%, 1.25 24%, 1.22 27%, 1.08 34%, 1.00 41%, 0.97 46%, 0.98 56%, 1.00)` pattern for the bouncy effect spec (tune on device against `openspec/references/design-system/preview/spacing-motion.html`). The reduced scheme uses `tween(easing = LinearEasing, durationMillis = ...)` with halved durations across all specs. If `MotionScheme.expressive()` / `MotionScheme.standard()` factories exist and their damping/stiffness are close enough to the CSS springs, it's acceptable to seed from those and override only what drifts — document the choice in the file header.
- [x] 3.6 `NubecitaSpacing.kt` — declare `data class NubecitaSpacing(val s0: Dp = 0.dp, val s1: Dp = 4.dp, val s2: Dp = 8.dp, val s3: Dp = 12.dp, val s4: Dp = 16.dp, val s5: Dp = 20.dp, val s6: Dp = 24.dp, val s7: Dp = 28.dp, val s8: Dp = 32.dp, val s10: Dp = 40.dp, val s12: Dp = 48.dp, val s16: Dp = 64.dp, val s20: Dp = 80.dp, val s24: Dp = 96.dp)`. (File named after the class per ktlint's `standard:filename` rule.)
- [x] 3.7 `NubecitaElevation.kt` — declare `data class NubecitaElevation(val e0: Dp, val e1: Dp, val e2: Dp, val e3: Dp, val e4: Dp, val e5: Dp)` with M3-matching values: `e0 = 0.dp, e1 = 1.dp, e2 = 3.dp, e3 = 6.dp, e4 = 8.dp, e5 = 12.dp`. (M3 prefers tonal elevation over shadow; these are consumed via `Surface(tonalElevation = MaterialTheme.elevation.e2)`. Shadow alpha differs light/dark but that lives in the M3 implementation.)
- [ ] 3.8 `Tokens.kt` — declare `data class NubecitaTokens(val spacing: NubecitaSpacing = NubecitaSpacing(), val elevation: NubecitaElevation = NubecitaElevation(...), val semanticColors: NubecitaSemanticColors, val motionDurations: NubecitaDurations = NubecitaDurations(), val extendedShape: NubecitaExtendedShape = NubecitaExtendedShape(), val extendedTypography: NubecitaExtendedTypography)`. Also declare `data class NubecitaSemanticColors(val success, onSuccess, successContainer, onSuccessContainer, warning, onWarning)` (hexes from `colors_and_type.css` success/warning palette), `data class NubecitaDurations(val short1 = 50, ..., val xLong1 = 700 /* all in ms as Int */)`, `data class NubecitaExtendedTypography(val mono: TextStyle)`.
- [ ] 3.9 In `Tokens.kt`, declare `val LocalNubecitaTokens = staticCompositionLocalOf<NubecitaTokens> { error("NubecitaTokens not provided — wrap your UI with NubecitaTheme { ... }") }`. Then declare `@Composable @ReadOnlyComposable extension properties`: `val MaterialTheme.spacing: NubecitaSpacing get() = LocalNubecitaTokens.current.spacing`, and equivalents for `elevation`, `semanticColors`, `motionDurations`, `extendedShape`, `extendedTypography`.

## 4. NubecitaTheme composable

- [ ] 4.1 `Theme.kt` — declare `@OptIn(ExperimentalMaterial3ExpressiveApi::class) @Composable fun NubecitaTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = true, content: @Composable () -> Unit)`.
- [ ] 4.2 Compute the color scheme: on API 31+ with `dynamicColor = true`, use `dynamicLightColorScheme(context)` / `dynamicDarkColorScheme(context)`. Otherwise pick from the six brand schemes based on `darkTheme` and (when available) the `android.provider.Settings.Secure.CONTRAST_LEVEL` system setting (API 34+; see `UiModeManager.contrast` or fall back to the standard variant).
- [ ] 4.3 Compute the motion scheme: read the reduce-motion flag via `LocalAccessibilityManager.current?.isRemoveAnimationsEnabled ?: false`; use `nubecitaReducedMotionScheme` when true, `nubecitaStandardMotionScheme` otherwise. Track the flag reactively (it can change at runtime without process death).
- [ ] 4.4 Build `NubecitaTokens` with `semanticColors` flipped for dark mode (success = Success80 dark, Success40 light; warning = Warning80 dark, Warning40 light). The rest of the tokens are theme-invariant.
- [ ] 4.5 Wrap the composition: `CompositionLocalProvider(LocalNubecitaTokens provides nubecitaTokens) { MaterialExpressiveTheme(colorScheme = ..., typography = nubecitaTypography, shapes = nubecitaShapes, motionScheme = ..., content = content) }`.

## 5. Previews

- [ ] 5.1 `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/preview/ColorRolesPreview.kt` — four `@Preview` composables (`name = "Light"`, `"Dark"`, `"Light HC"`, `"Dark HC"`) rendering the full color roster as labeled swatches inside `NubecitaTheme(darkTheme, dynamicColor = false)`. Include primary/secondary/tertiary/error containers and all surface containers.
- [ ] 5.2 `TypographyScalePreview.kt` — one `@Preview` rendering every M3 type role (`MaterialTheme.typography.displayLarge` through `labelSmall`) plus `MaterialTheme.extendedTypography.mono` as a labeled column. Show both light and dark via `@Preview(uiMode = UI_MODE_NIGHT_YES)`.
- [ ] 5.3 `ShapeScalePreview.kt` — `@Preview` rendering a 64dp box at each shape radius (xs, sm, md, lg, xl, 2xl, pill) labeled with the token name.
- [ ] 5.4 `MotionShowcasePreview.kt` — `@Preview` with a representative `Button` at `MaterialTheme.extendedShape.pill` and a caption noting that motion can't be previewed statically ("see emulator / spacing-motion.html for ground truth").

## 6. Delete the wizard theme + update callers

- [ ] 6.1 Delete `app/src/main/java/net/kikin/nubecita/theme/Color.kt`, `Theme.kt`, `Type.kt`.
- [ ] 6.2 Update `MainActivity` (and any other `setContent { ... }` callsite) to `import net.kikin.nubecita.designsystem.NubecitaTheme`. `grep -rn 'net.kikin.nubecita.theme' app/src/main` MUST return zero matches after this step.

## 7. Tests

- [ ] 7.1 `designsystem/src/test/kotlin/net/kikin/nubecita/designsystem/ColorSchemeTest.kt` — JUnit 4 test verifying `nubecitaLightColorScheme()` and `nubecitaDarkColorScheme()` return `ColorScheme` instances whose `primary` matches the expected brand hex (`0xFF0A7AFF` light, `0xFFA6C8FF` dark). Pure function; no Compose runtime.
- [ ] 7.2 `designsystem/src/test/kotlin/net/kikin/nubecita/designsystem/NubecitaThemeTest.kt` — Compose UI test using `createComposeRule()` verifying:
  (a) `NubecitaTheme(dynamicColor = false, darkTheme = false) { val c = MaterialTheme.colorScheme.primary; ... }` captures `primary == Color(0xFF0A7AFF)`.
  (b) `NubecitaTheme(dynamicColor = false, darkTheme = true) { ... }` captures `primary == Color(0xFFA6C8FF)`.
  (c) Reading `MaterialTheme.spacing.s4` inside the theme returns `16.dp`.
  (d) Reading `MaterialTheme.spacing` outside the theme throws `IllegalStateException` with a message mentioning "NubecitaTheme".

## 8. Style, lint, and validation gates

- [ ] 8.1 Run `./gradlew spotlessCheck sortDependencies :designsystem:lint :app:lint`. Fix any findings (Spotless may reorder imports; sortDependencies normalizes the new `libs.androidx.compose.ui.text.google.fonts` reference).
- [ ] 8.2 Run `./gradlew :designsystem:testDebugUnitTest :app:testDebugUnitTest` — all tests pass.
- [ ] 8.3 Run `./gradlew :designsystem:assembleDebug :app:assembleDebug` — both modules build clean.
- [ ] 8.4 Run `openspec validate add-designsystem-theme` — change is still valid.
- [ ] 8.5 Run `git grep -nE 'Color\(0x[0-9A-Fa-f]+\)' app/src/main` and confirm zero matches (the regex should return nothing; color literals now live in `:designsystem` only). Matches inside `@Preview` composables anywhere are fine — scope this grep to `app/src/main` specifically.
- [ ] 8.6 Run the Android Studio previews for `ColorRolesPreview`, `TypographyScalePreview`, `ShapeScalePreview` and confirm they render without errors. (Visual sanity check for the Gemini handoff.)

## 9. PR and archive

- [ ] 9.1 Commit in logical steps with Conventional Commit subjects and `Refs: nubecita-mi3 nubecita-7dp` in each footer.
- [ ] 9.2 Push the branch and open a PR with `Closes: nubecita-mi3, nubecita-7dp` in the body (both bd issues close on squash-merge).
- [ ] 9.3 After the PR merges, on `main`: run `openspec archive add-designsystem-theme` to fold the `design-system` spec into `openspec/specs/`, then `bd close nubecita-mi3 nubecita-7dp`.
