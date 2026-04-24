## Context

The Nubecita brand system is fully specified in `openspec/references/design-system/`:

- `colors_and_type.css` — canonical tokens (tonal palette, semantic roles for light + dark, shape scale, motion springs, type scale, spacing, elevation)
- `README.md` — brand voice, microcopy rules, component guidelines, layout breakpoints
- `SKILL.md` — agent-facing usage guide
- `assets/` — SVG logos, cloud illustrations, app icon (not imported in this change)
- `preview/` — HTML cards demonstrating each token group (reference only)
- `ui_kits/nubecita-android/` — JSX-style primitives showing Android screen composition (reference only)

The brand system authored the aesthetic decisions: primary Sky `#0A7AFF`, secondary Peach `#C06C00`, tertiary Lilac `#6250B0`, pill-shaped buttons as the M3 Expressive signal, Fraunces serif for display type, Roboto Flex for body/headlines, JetBrains Mono for handles/DIDs, M3 Expressive spring motion. This change is the *translation* of those decisions into Compose.

The existing Compose theme at `app/src/main/java/net/kikin/nubecita/theme/*` is the Android Studio wizard scaffold — generic Purple80/Pink40 palette, single `bodyLarge` typography. It gets deleted.

`MaterialExpressiveTheme` is stable in the Material3 release bundled with Compose BOM `2026.04.01`. It accepts a `MotionScheme` parameter that stock `MaterialTheme` doesn't, which is why we prefer it for Nubecita.

## Goals / Non-Goals

**Goals:**

- Feature code can `NubecitaTheme { ... }` at its `setContent {}` root and get the full brand look: colors, typography, shape, motion, spacing, elevation — without importing anything brand-specific from deeper in the module.
- Token additions (new semantic color, new shape role) have exactly one source of truth: a named file under `:designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/`. No duplicated hex values in feature code.
- The translation from `colors_and_type.css` is mechanical and reviewable — every CSS custom property maps 1:1 to a named Kotlin constant, preserving the brand system's intent.
- Runtime flexibility: dynamic-color opt-out, reduce-motion opt-out, dark-theme override all available as composable params.

**Non-Goals:**

- Reusable composables beyond `NubecitaTheme` and previews. Split into `nubecita-w0d`.
- Adaptive layouts / `WindowSizeClass`. Split into `nubecita-5l3`.
- Persistent user preferences for theme knobs. A future settings screen owns DataStore wiring.
- Asset imports into module resources. Referenced but not used yet.
- Migration of feature screens (none exist yet).

## Decisions

### `MaterialExpressiveTheme` over stock `MaterialTheme`

Options considered:

- **`MaterialExpressiveTheme`** — ships in Material3 1.3+, accepts a `MotionScheme` parameter, enables M3 Expressive component defaults (pill-shaped buttons, new FAB variants, updated app bar behaviors).
- **Stock `MaterialTheme`** — older API, no `MotionScheme` slot. We'd build motion ourselves via per-composable `animateFoo(animationSpec = ...)` calls.

**Choice: `MaterialExpressiveTheme`.** Motion is a load-bearing part of Nubecita's personality per the brand system (spring-based, shape-morphing, overshooting). Stock `MaterialTheme` would force every animate-call site to know the motion tokens; `MaterialExpressiveTheme` lets composables read `MaterialTheme.motionScheme.defaultSpatialSpec` and get brand-consistent motion by default. It also matches the brand system's explicit "M3 Expressive" branding.

**Trade-off:** Some `MaterialExpressiveTheme` APIs are marked `@ExperimentalMaterial3ExpressiveApi` even in the 2026.04 BOM. We opt in explicitly at the theme callsite only; feature code never sees the annotation because it reads tokens via `MaterialTheme.*`. If Google stabilizes or renames the API, the blast radius is a single file.

### `CompositionLocal` + `MaterialTheme` extension properties for extended tokens

Options considered:

- **`CompositionLocal<NubecitaTokens>` + extension properties** — `data class NubecitaTokens(spacing, elevation, ...)` is provided at the theme root; `MaterialTheme.spacing` etc. are `@Composable` extension properties reading the CompositionLocal. Matches how Material3 internally wires its own theme.
- **Plain `object NubecitaTokens`** — `NubecitaTokens.Spacing.s4 = 16.dp` as a static constant. Simplest. No composition.
- **Every token as a Kotlin top-level constant** (`val NubecitaSpacingS4 = 16.dp`) — no structure, no discoverability.

**Choice: CompositionLocal + extension properties.** Some extended tokens vary between light/dark (elevation shadow alpha) or by user setting (motion durations under reduce-motion). A static object can't flip; CompositionLocal does. Extension properties on `MaterialTheme` give the ergonomic `MaterialTheme.spacing.s4` call site so consumers don't remember to import `LocalNubecitaTokens`. It's one more indirection than a static object but matches Compose's conventions exactly.

**Trade-off:** Reads must happen inside `@Composable` functions. Non-composable code (a `PaddingValues` constant shared across files, for example) can't read tokens — those rare cases reach into `NubecitaDimens` top-level constants as an escape hatch when we need them. None are needed for this change.

### Six color schemes, not two

Options considered:

- **Two schemes (light + dark)** — the CSS only defines these explicitly.
- **Six schemes (light + dark × standard/medium-contrast/high-contrast)** — Material 3's full accessibility complement.

**Choice: six schemes.** Android 14+ exposes a "contrast level" setting in system preferences; if we only ship two, users who bump contrast up get the standard scheme anyway. The tonal palette is fully defined (tones 0 through 100 in `colors_and_type.css`), so the medium/high variants derive mechanically (e.g., high-contrast-light sets `primary = sky30`, `onPrimary = white`, `outline = neutral20`). Zero additional design work, significant accessibility benefit.

**Trade-off:** Eight extra hand-written `ColorScheme` declarations. The six schemes total ~600 LOC across `Color.kt`. Acceptable for tokens — this is the one file that's allowed to be verbose because every value matters.

### Custom `MotionScheme` from CSS springs, not `MotionScheme.expressive()`

Options considered:

- **Stock `MotionScheme.expressive()`** — Google's canonical expressive motion. Well-tuned, well-tested, zero work.
- **Custom `MotionScheme` reproducing the CSS spring curves** — `ease-spring-fast` / `ease-spring-slow` / `ease-spring-bouncy` translated to Compose `spring()` or `keyframes`.

**Choice: custom MotionScheme.** The brand system explicitly defined spring curves — `ease-spring-bouncy` has a ~1.25× overshoot pattern that Material's stock expressive curves don't reproduce. `fast` and `slow` translate cleanly to Compose `spring(dampingRatio, stiffness)`. `bouncy` is the distinctive one and uses `keyframes` because the linear() curve has a specific overshoot-then-settle pattern that spring damping can approximate but not match exactly.

**Trade-off:** Kotlin spring parameters are tuned by feel, not mathematically derived from the linear() curves. We'll iterate on the emulator/device to match what the CSS looks like in a browser. The brand system's README includes HTML preview cards (`preview/spacing-motion.html`) that serve as the ground truth.

### Dynamic color default-on, brand-palette as fallback

User asked for "dynamic-first" (default `true`, user toggles off). The implication worth calling out:

- On Android 12+ with `dynamicColor = true`, Nubecita's Sky/Peach/Lilac palette is **not visible** — wallpaper-derived tones win.
- Brand identity only shows pre-Android-12 or when the user explicitly opts out.

This is a product decision, not a code one, but it belongs in the module KDoc so future contributors understand why the brand palette they spent weeks choosing doesn't show up on their phone. The `dynamicColor` parameter flips it off for any composable (including the main `setContent {}`).

### Hybrid font delivery

- **Bundled (`:designsystem/src/main/res/font/`):** Roboto Flex (variable, `.ttf`, ~500KB) and JetBrains Mono (variable, `.ttf`, ~200KB). These are always on screen (body text, handles) — an async first-load would cause visible FOUT.
- **Downloadable (`androidx.compose.ui.text.googlefonts`):** Fraunces (display-only, FOUT acceptable — big text catches your eye after fonts settle) and Material Symbols Rounded (variable icon font, ~2MB+ if bundled). Google Fonts provider via Play Services.

The Downloadable Fonts setup needs a cert hash resource (`res/values/font_certs.xml`) declaring the Google certificates. Standard AOSP boilerplate; documented in `androidx.compose.ui.text.googlefonts.GoogleFont.Provider`.

**Fallback chain on devices without Play Services:** Fraunces → `FontFamily.Serif`; Material Symbols Rounded → system font with icon characters missing (rare; all icons we use are in system emoji range or fallback cleanly). Documented in the module README.

### Variable font axis support

Fraunces uses `font-variation-settings: "SOFT" 50` in the CSS (per the brand system). Compose supports this via `FontVariation.Setting("SOFT", 50f)` on `Font(...)` declarations, but:

- Variable font axes require **API 26+ at runtime**. Our minSdk is 24.
- On API 24-25, the font renders at the default axis — no SOFT customization, but still Fraunces at the right weight. Visually fine, minor brand-voice degradation on ~0% of live devices (Android 7.0/7.1 is deep single-digits).

Documented in the module README. Not a blocker.

### Module package: `net.kikin.nubecita.designsystem`, not `.ds` or `.ui.theme`

Options considered:

- `net.kikin.nubecita.designsystem` — matches the Gradle module name, easy to find.
- `net.kikin.nubecita.theme` — matches the current (wizard) package, but "theme" is only one file; the module has spacing, motion, elevation, etc.
- `net.kikin.nubecita.ds` — short; easy to type in imports. Opaque for outsiders.

**Choice: `net.kikin.nubecita.designsystem`.** Matches the module path. Reads naturally in imports. No one has to know what "ds" stands for.

## Risks / Trade-offs

- **M3 Expressive experimental opt-ins** — `@ExperimentalMaterial3ExpressiveApi` may require opt-in at more sites than `Theme.kt` alone as components are added (e.g., expressive `FloatingActionButton` variants). Mitigation: we annotate the one entry-point composable with `@OptIn`, and add opt-ins component-by-component as feature screens need them. Tracked in consumer bd issues (`w0d`, etc.).
- **Dynamic color washing out brand identity** — addressed above; product decision, not a code risk. Mitigation: default `true` per user's call, knob exposed, KDoc documents the trade.
- **Custom spring tuning drift** — if Compose's `spring()` damping/stiffness model diverges subtly from the CSS `linear(...)` curves, motion won't look identical to the HTML previews. Mitigation: iterate on device, accept "visually close enough." Screenshot tests are out of scope for this change; motion eyeballing is it.
- **Downloadable Fonts latency** — first launch on a fresh install needs ~100-500ms over Play Services to fetch Fraunces. During that window, display-type fallback to `FontFamily.Serif`. Non-blocking but visible on the splash / onboarding screens that will use display type. Mitigation: `FontFamily.Resolver` preloads at `Application.onCreate()` (tracked informally — can add if FOUT becomes a complaint).
- **Variable font fallback on API 24-25** — Fraunces loses its SOFT axis, Roboto Flex loses weight interpolation. Acceptable for the devices still running those APIs.
- **APK weight from bundled Roboto Flex + JetBrains Mono** — ~700KB. Non-trivial but the variable fonts replace what would otherwise be 4+ separate weight files. Net win.

## Migration Plan

Greenfield — no existing `:designsystem` consumers to migrate. The only moving parts are:

1. Scaffold the `:designsystem` Gradle module + register in `settings.gradle.kts`.
2. Write token files + `Theme.kt` + previews.
3. Add `implementation(project(":designsystem"))` to `app/build.gradle.kts`.
4. Delete `app/src/main/java/net/kikin/nubecita/theme/*`.
5. Update `MainActivity` + any other callers to import `net.kikin.nubecita.designsystem.NubecitaTheme`.
6. Sanity: `./gradlew :designsystem:assembleDebug :app:assembleDebug spotlessCheck sortDependencies lint` green.
7. Open PR, squash-merge on green CI.

Rollback: a single revert commit. The wizard theme files come back from git; feature code hasn't started consuming tokens yet, so there's nothing downstream to unwind.

## Open Questions

None. The brand system has already answered every aesthetic question (palette, fonts, shapes, motion, microcopy), and the user has resolved the engineering questions (dynamic-first default, hybrid font delivery). Implementation is mechanical from here — which is the point of doing this work via detailed openspec: Gemini can execute the tasks without needing additional design input.
