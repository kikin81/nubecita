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

### Requirement: Logomark content-description string

`:designsystem/src/main/res/values/strings.xml` SHALL define a string resource used as the `contentDescription` for the brand-mark composable:

- `<string name="logomark_content_description">Nubecita</string>`

The string SHALL be `translatable="true"` (default). When the app gains localized resources for additional locales, the brand name MAY be transliterated per the conventions of that locale.

#### Scenario: String resolves to the brand name in the default locale

- **WHEN** `stringResource(R.string.logomark_content_description)` is read inside a Composable on a device set to the default locale
- **THEN** the call SHALL return `"Nubecita"`
