## Why

Nubecita's UI currently uses the Android Studio wizard defaults (`Purple80`/`Pink40` palette, single-typography-role scaffold) that ship with every new Jetpack Compose project. There's no brand identity, no Material 3 Expressive opt-in, no shared token source for feature screens to consume, and the theme is bolted to `:app` where it can't be reused by any future module. Feature work already on the roadmap (feed, post detail, profile, login, composer) can't start in earnest because every screen would either duplicate theme plumbing or depend on the wrong primitives.

The brand direction has been settled out-of-band in `openspec/references/design-system/` — a fully specified Nubecita design system (tonal palette, shape scale, M3 Expressive spring motion, typography, microcopy voice, asset kit). This change translates that brand system into a Compose `:designsystem` Gradle module, exposes it as `NubecitaTheme`, and lets feature screens `@Inject`/reference theme tokens through `MaterialTheme.*` + a small extended-tokens CompositionLocal.

This bundles two related bd issues — `nubecita-7dp` (scaffold the `:designsystem` Gradle module) and `nubecita-mi3` (implement the Material 3 Expressive theme). Separating them into two PRs would be pure ceremony: the module has no purpose without the theme, and the theme has no home without the module. One openspec, one PR, both bd tickets close on merge.

## What Changes

### Gradle module

- Create a new `:designsystem` Gradle module at `designsystem/` with its own `build.gradle.kts`, registered in `settings.gradle.kts`.
- `:app` gains a dependency on `:designsystem` (replacing the wizard-defaulted `app/src/main/java/net/kikin/nubecita/theme/*` files, which are deleted).
- Module dependencies: `androidx-compose-bom`, `androidx-compose-material3`, `androidx-compose-ui`, `androidx-core-ktx`, `androidx-compose-ui-text-google-fonts` (new catalog entry). No Hilt (theme is pure Compose), no Android framework beyond `Context` for dynamic color.

### Token files

Under `designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/`:

- `Color.kt` — full tonal palette (Sky / Peach / Lilac / Neutral / NeutralVariant) + 6 `ColorScheme`s (light, light-medium-contrast, light-high-contrast, and their dark counterparts).
- `Shape.kt` — `Shapes` mapping M3's 5 roles to CSS tokens (`extraSmall` 4dp → `extraLarge` 28dp) + extended shapes (2xl 36dp, `pill` = `CircleShape`) on `NubecitaTokens.shape`.
- `Type.kt` — `Typography` with all 15 M3 roles, assigning Fraunces (display, headline-large) + Roboto Flex (other headlines, titles, body, labels). `body-large` uses 17sp/26sp line-height (one step above M3 default) for feed readability. `mono` on `NubecitaTokens.typography`.
- `Motion.kt` — custom `MotionScheme` built from the CSS spring curves (`fast` / `slow` as Compose `spring()` with tuned damping/stiffness; `bouncy` as `keyframes` because the overshoot pattern is too distinctive to approximate). Duration tokens (`short1..xLong1`) on `NubecitaTokens.duration`.
- `Spacing.kt` — `NubecitaTokens.spacing.s1..s24` matching the CSS 4pt scale.
- `Elevation.kt` — `NubecitaTokens.elevation.e1..e5` shadow tokens (light + dark variants).
- `Fonts.kt` — Fraunces + Material Symbols Rounded declared via `androidx.compose.ui.text.googlefonts.GoogleFont` (Downloadable Fonts provider, Google Fonts cert hash in `res/values/font_certs.xml`). Roboto Flex + JetBrains Mono bundled as `.ttf` in `designsystem/src/main/res/font/`.
- `Tokens.kt` — `data class NubecitaTokens(...)` + `LocalNubecitaTokens` `CompositionLocal` + `MaterialTheme.*` extension properties (`MaterialTheme.spacing`, `MaterialTheme.elevation`, `MaterialTheme.semanticColors`, `MaterialTheme.motionDurations`, `MaterialTheme.extendedShape`).
- `Theme.kt` — `NubecitaTheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = true, content: @Composable () -> Unit)`. Picks the color scheme (dynamic on Android 12+ when `dynamicColor = true`, brand palette otherwise). Swaps motion scheme when the runtime reduce-motion accessibility flag is set. Wraps `MaterialExpressiveTheme` and provides `LocalNubecitaTokens`.
- `preview/NubecitaThemePreview.kt` — `@Preview` composables for color roles (light/dark/high-contrast × 2), typography scale, shape scale, and a representative motion showcase. Doubles as the visual sanity check for the Gemini handoff.

### Existing files deleted

- `app/src/main/java/net/kikin/nubecita/theme/Color.kt`, `Theme.kt`, `Type.kt` — wizard defaults replaced by the `:designsystem` equivalents.
- `MainActivity` and any other `NubecitaTheme` callsites update their import from `net.kikin.nubecita.theme.NubecitaTheme` to `net.kikin.nubecita.designsystem.NubecitaTheme`.

### Catalog additions

- `gradle/libs.versions.toml`: `androidx-compose-ui-text-google-fonts = { module = "androidx.compose.ui:ui-text-google-fonts" }` (version from the Compose BOM).

### Non-goals

- **No reusable composables beyond `NubecitaTheme`.** `PostCard`, `Avatar`, buttons, inputs, nav scaffolds — all out. They're tracked as `nubecita-w0d` and will consume this theme.
- **No `material3-adaptive` dependency, no `WindowSizeClass`, no two-pane scaffolding.** Tracked as `nubecita-5l3`.
- **No DataStore / SharedPreferences persistence of `dynamicColor` / `reduceMotion` user preferences.** The theme exposes them as composable parameters; a future settings screen wires persistence.
- **No asset import into `:designsystem/res/drawable/`.** The SVG logos and illustrations in `openspec/references/design-system/assets/` stay in reference-material land until a feature screen needs them.
- **No screenshot tests for the theme.** `compose-screenshot` is wired into `:app` but not `:designsystem`; wiring it and baselining theme previews is a separate ticket when it becomes useful.
- **No Hilt module in `:designsystem`.** The theme is applied via composition, not injection. No `@Provides`, no `@InstallIn`.
- **No migration of feature code to consume tokens.** No feature screens exist yet; this change just lands the theme. As feature tickets start, they'll pull tokens naturally.

## Capabilities

### New Capabilities

- `design-system` — Defines the contract for Nubecita's visual foundation: the `NubecitaTheme` composable entry point, the full set of M3 color/shape/typography/motion tokens plus extended Nubecita-specific tokens (spacing, elevation, semantic colors, pill shape, durations), the dynamic-color policy, the reduce-motion policy, and the invariant that feature code reads tokens exclusively through `MaterialTheme.*` (never hard-codes `Color`/`dp`/`TextStyle`).

### Modified Capabilities

- None. This is a greenfield module with no existing specs to extend.

## Impact

- **Code**: One new Gradle module (`designsystem/`) with ~10 Kotlin files and one `build.gradle.kts`. Three files deleted from `:app`. `settings.gradle.kts` + `app/build.gradle.kts` get one line each.
- **APK size**: Bundled fonts (Roboto Flex + JetBrains Mono variable `.ttf`s) add ~700KB. Fraunces + Material Symbols Rounded load via Downloadable Fonts (zero APK cost, cached after first fetch).
- **Runtime deps**: Google Play Services required for Downloadable Fonts. On devices without Play Services (F-Droid installs, microG, GrapheneOS without sandboxed Play Services), Fraunces falls back to the configured fallback (system serif); Material Symbols falls back to system icons when rendered via `Icon(imageVector = ...)`. Acceptable; our primary audience has Play Services.
- **Dependencies**: `androidx-compose-ui-text-google-fonts` added to the catalog (already transitively available via Compose BOM; we just expose it as a `libs.*` reference). Sonatype check runs before landing.
- **Tests**: Two JVM unit tests (`NubecitaThemeTest`, `ColorSchemeTest`). No instrumented tests, no screenshot baselines.
- **Downstream unblocks**: `nubecita-w0d` (PostCard + primitives — they need `MaterialTheme.spacing` and `shape.pill`), `nubecita-uf5` (Login screen UI), any feature screen that has been waiting for a brand theme. Also `nubecita-5l3` (adaptive two-pane) because it'll want `NubecitaTheme` wrapping the scaffold.
