## ADDED Requirements

### Requirement: Fraunces variable font with `SOFT` axis is bundled and exposed via Typography

`:designsystem` SHALL ship the Fraunces variable font as a bundled font asset (`:designsystem/src/main/res/font/fraunces.ttf` or equivalent) and SHALL expose at least one Typography style that uses the variable font with the `SOFT` variable axis configurable. The display-name style used by the profile hero MUST set `SOFT = 70`. The font MUST be loaded via the standard Compose `FontFamily` API — no reflection, no manual `Typeface` construction. The font asset MUST NOT be downloaded at runtime; it MUST be embedded in the APK at build time.

#### Scenario: Display style uses Fraunces with `SOFT = 70`

- **WHEN** any consumer renders a `Text` with the profile-display-name style sourced from `:designsystem`'s Typography
- **THEN** the resolved `TextStyle` carries `FontFamily(Font(R.font.fraunces, FontVariation.Settings(FontVariation.Setting("SOFT", 70f), …)))`; the rendered glyph metrics differ measurably from the default-`SOFT` Fraunces rendering when verified on a real device

#### Scenario: Fraunces is bundled in the APK

- **WHEN** the debug APK is built and inspected (`./gradlew :app:assembleDebug` followed by `aapt2 dump resources`)
- **THEN** the Fraunces font file is present under `res/font/`; no network request is made to fetch the font during APK install or at first render

### Requirement: JetBrains Mono variable font is bundled and exposed via Typography

`:designsystem` SHALL ship JetBrains Mono as a bundled variable font asset and SHALL expose a monospace Typography style suitable for rendering user handles at 13 sp. The font MUST be loaded via the standard Compose `FontFamily` API and MUST be embedded in the APK at build time (no runtime download).

#### Scenario: Handle style uses JetBrains Mono at 13 sp

- **WHEN** any consumer renders a `Text` with the handle style sourced from `:designsystem`'s Typography
- **THEN** the resolved `TextStyle` carries `FontFamily(Font(R.font.jetbrains_mono, …))` and `fontSize = 13.sp`

### Requirement: `BoldHeroGradient` composable owns Palette extraction and avatarHue fallback

`:designsystem` SHALL ship a `BoldHeroGradient(banner: Url?, avatarHue: Int, modifier: Modifier = Modifier, content: @Composable () -> Unit)` composable (or equivalent surface API). The composable MUST decode the `banner` image off the main thread via Coil's image-loader pipeline (no direct `BitmapFactory.decodeStream` in the design system or any consumer), pass the decoded bitmap to `androidx.palette.graphics.Palette.from(bitmap).generate()` on a background coroutine context, cache the resulting `Palette` keyed on the banner URL or blob `cid`, and render a 2-stop gradient derived from the cached palette. When `banner == null`, the gradient MUST be derived deterministically from `avatarHue` (an integer hue in `0..360`). The feature module that consumes `BoldHeroGradient` MUST NOT import `androidx.palette.*` or `androidx.compose.ui.graphics.Brush` for gradient construction — both are encapsulated by this composable.

#### Scenario: Palette extraction runs off the main thread

- **WHEN** `BoldHeroGradient` is composed with a non-null `banner` URL whose image has not been previously palette-extracted
- **THEN** the Palette extraction runs on `Dispatchers.Default` (or equivalent off-main dispatcher); the main thread is not blocked during decode + extraction; the composable initially renders the `avatarHue`-derived fallback gradient and swaps to the palette-derived gradient when extraction completes

#### Scenario: Palette result is cached per banner

- **WHEN** `BoldHeroGradient` is composed twice in succession with the same `banner` URL (e.g., navigating away from a profile and back)
- **THEN** the second composition retrieves the cached `Palette` synchronously on first composition; no re-decode of the banner bitmap occurs; the gradient renders without a fallback flicker

#### Scenario: Null banner uses avatarHue fallback deterministically

- **WHEN** `BoldHeroGradient` is composed with `banner = null` and `avatarHue = 217`
- **THEN** the rendered gradient is derived deterministically from `avatarHue = 217`; the same `avatarHue` always produces the same gradient; no `Palette` call is made

#### Scenario: Minimum-contrast adjustment for very-light banners

- **WHEN** `BoldHeroGradient` is composed with a banner whose extracted palette returns swatches with luminance above the contrast threshold needed for WCAG AA against white text overlays
- **THEN** the composable darkens the dominant stop of the gradient until contrast clears; the rendered gradient is dark enough to maintain AA contrast for white text overlays at the hero's name + handle positions

### Requirement: `ProfilePillTabs` composable wraps `PrimaryTabRow` with M3 Expressive pill chrome

`:designsystem` SHALL ship a `ProfilePillTabs(tabs: List<PillTab>, selectedTab: PillTab, onTabSelect: (PillTab) -> Unit, modifier: Modifier = Modifier)` composable that renders pill-shaped tabs. Each pill MUST be 36 dp tall. The active tab MUST have `MaterialTheme.colorScheme.primary` container fill and `onPrimary` content color; inactive tabs MUST have a transparent container and `onSurface` content color. Each tab's icon (when present) MUST render via `NubecitaIcon` with the `FILL` variable axis at 1 when active and 0 when inactive. The composable MUST be implemented as a thin wrapper around `androidx.compose.material3.PrimaryTabRow` (or equivalent M3 tab primitive) — no hand-rolled `Row` of buttons, no custom `Indicator` that draws outside the M3 vocabulary.

#### Scenario: Active tab renders with primary container fill and filled icon

- **WHEN** `ProfilePillTabs` is composed with `tabs = [Posts, Replies, Media]` and `selectedTab = Posts`
- **THEN** the rendered Posts pill has `Color = MaterialTheme.colorScheme.primary` as its container fill, the Posts icon renders with `FontVariation.Setting("FILL", 1f)` applied via `NubecitaIcon`, and the Replies + Media pills render with transparent container fills and `FILL = 0`

#### Scenario: Tab selection invokes onTabSelect with the new tab

- **WHEN** the user taps the Replies pill while Posts is currently active
- **THEN** `onTabSelect(Replies)` is invoked exactly once; the composable does NOT internally re-render with `selectedTab = Replies` until the parent passes the new `selectedTab` parameter (state hoisting is preserved)
