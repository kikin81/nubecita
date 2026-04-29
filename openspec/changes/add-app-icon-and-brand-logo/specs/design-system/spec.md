## ADDED Requirements

### Requirement: `:designsystem` provides a `NubecitaLogomark` composable

`:designsystem/component/NubecitaLogo.kt` SHALL expose a public `@Composable fun NubecitaLogomark(modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary)` that renders the brand cloud-only mark (no wordmark) backed by the `nubecita_logomark.xml` vector drawable.

The vector drawable SHALL be a single-color rendering of the cloud silhouette ported from `openspec/references/design-system/assets/logomark-mono.svg` — 3 circles + 1 rounded rect, all with `android:fillColor="#FFFFFFFF"`. The composable SHALL apply `ColorFilter.tint(tint)` so the rendered color matches the `tint` parameter. The default tint of `MaterialTheme.colorScheme.primary` resolves to brand sky blue (`#0A7AFF`) under the static palette and to the user's wallpaper-derived primary under dynamic color.

The composable SHALL set `contentDescription = stringResource(R.string.logomark_content_description)` (value: `"Nubecita"`) so screen readers announce the brand name when the mark is used as the sole content of a tappable container.

The intrinsic aspect of the underlying vector SHALL be 1:1 (square). Callers control absolute size via the `modifier` parameter (`Modifier.size(...)` or layout-driven sizing).

#### Scenario: Logomark renders with default tint under static palette

- **WHEN** `NubecitaTheme(dynamicColor = false) { NubecitaLogomark(modifier = Modifier.size(96.dp)) }` is composed
- **THEN** a 96dp × 96dp white-cloud image SHALL render tinted to brand sky blue (`#0A7AFF`)

#### Scenario: Logomark accepts a custom tint

- **WHEN** `NubecitaLogomark(tint = Color.White)` is composed inside `NubecitaTheme`
- **THEN** the cloud SHALL render in pure white regardless of the active palette

#### Scenario: Logomark exposes its accessible label

- **WHEN** TalkBack focuses on a `NubecitaLogomark` composable
- **THEN** TalkBack SHALL announce `"Nubecita"` (from `R.string.logomark_content_description`)

### Requirement: `:designsystem` provides a `NubecitaLogo` composable

`:designsystem/component/NubecitaLogo.kt` SHALL expose a public `@Composable fun NubecitaLogo(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary)` that renders the brand cloud + "nubecita" wordmark, backed by the `nubecita_logo.xml` vector drawable.

The vector drawable SHALL contain the cloud paths (translated and scaled per the source `logo.svg` `<g transform>`) AND the "nubecita" wordmark baked as path data (NOT as an SVG `<text>` element). Both the cloud and wordmark SHALL share the same fill color (`#FFFFFFFF` in the source drawable; recolored at render time via `ColorFilter.tint(color)`).

The composable SHALL set `contentDescription = stringResource(R.string.logo_content_description)` (value: `"Nubecita"`).

The intrinsic aspect of the underlying vector SHALL be ~3.3:1 (240dp × 72dp). Callers control size via the `modifier` parameter, typically `Modifier.size(width = ..., height = ...)`.

#### Scenario: Logo renders with default color under static palette

- **WHEN** `NubecitaTheme(dynamicColor = false) { NubecitaLogo(modifier = Modifier.size(width = 200.dp, height = 60.dp)) }` is composed
- **THEN** a 200dp × 60dp image SHALL render the cloud + "nubecita" wordmark tinted to brand sky blue (`#0A7AFF`)

#### Scenario: Logo accepts a custom color

- **WHEN** `NubecitaLogo(color = MaterialTheme.colorScheme.onPrimary)` is composed inside a `Surface(color = MaterialTheme.colorScheme.primary)`
- **THEN** the logo SHALL render in `onPrimary` (white in the static palette) and contrast against the primary-colored surface

#### Scenario: Wordmark survives missing fonts

- **WHEN** the device fails to load the typography font family used by `MaterialTheme.typography`
- **THEN** the `NubecitaLogo` wordmark SHALL render unchanged (it is a baked vector path, not text)

### Requirement: Logo content-description strings

`:designsystem/src/main/res/values/strings.xml` SHALL define two string resources used as the `contentDescription` for the brand-mark composables:

- `<string name="logomark_content_description">Nubecita</string>`
- `<string name="logo_content_description">Nubecita</string>`

Both strings SHALL be `translatable="true"` (default). When the app gains localized resources for additional locales, the brand name MAY be transliterated per the conventions of that locale.

#### Scenario: Strings resolve to the brand name in the default locale

- **WHEN** `stringResource(R.string.logomark_content_description)` and `stringResource(R.string.logo_content_description)` are read inside a Composable on a device set to the default locale
- **THEN** both calls SHALL return `"Nubecita"`
