## ADDED Requirements

### Requirement: `NubecitaIconName` exposes glyphs required by the notifications surface

`NubecitaIconName` SHALL include entries for the following Material Symbols glyphs:

- `AlternateEmail` (codepoint ``) — the `@` glyph
- `ExpandMore` (codepoint ``) — chevron-down
- `FormatQuote` (codepoint ``) — curly double-quote
- `Verified` (codepoint ``) — verified-badge mark

The existing `Notifications` entry's codepoint SHALL be corrected from `` (`notifications_none`) to `` (`notifications`) so the variable font's FILL axis renders the activity dot on FILL=1.

#### Scenario: New icons render via NubecitaIcon

- **WHEN** any of the new icon names is passed to `NubecitaIcon(name = …)`
- **THEN** the icon SHALL render correctly in both `filled = true` and `filled = false` states using the shipped subset font

#### Scenario: Notifications icon shows the activity dot when filled

- **WHEN** `NubecitaIcon(name = NubecitaIconName.Notifications, filled = true)` is rendered
- **THEN** the rendered glyph SHALL be the canonical filled bell with the activity dot (codepoint `` with FILL=1)

### Requirement: Material Symbols subset font is regenerated after adding new icons

After adding entries to `NubecitaIconName`, the `./scripts/update_material_symbols.sh` script SHALL be re-run so the subset font under `designsystem/src/main/res/font/` includes the new glyphs. The committed font file SHALL include all codepoints referenced by `NubecitaIconName`.

#### Scenario: Unit test guards codepoint validity

- **WHEN** `./gradlew :designsystem:testDebugUnitTest` runs
- **THEN** `NubecitaIconNameTest.every_codepoint_isASingleScalar` SHALL pass for every entry, confirming each codepoint is a single Unicode scalar value

### Requirement: `NotificationReasonIcon` composable maps `NotificationReason` to icon + tint

`:designsystem` SHALL expose a `NotificationReasonIcon(reason: NotificationReason, modifier: Modifier = Modifier)` composable that renders the correct glyph + tint pair for each reason. The mapping SHALL be:

| Reason | Icon | Tint |
|---|---|---|
| `Like`, `LikeViaRepost` | `Favorite` (filled) | extended `likeAccent` token (or `colorScheme.error` fallback) |
| `Repost`, `RepostViaRepost` | `Repeat` | extended `repostAccent` token (or `colorScheme.tertiary` fallback) |
| `Follow`, `ContactMatch`, `StarterpackJoined` | `PersonAdd` | `colorScheme.primary` |
| `Reply` | `Reply` | `colorScheme.onSurfaceVariant` |
| `Mention` | `AlternateEmail` | `colorScheme.onSurfaceVariant` |
| `Quote` | `FormatQuote` | `colorScheme.onSurfaceVariant` |
| `Verified` | `Verified` (filled) | `colorScheme.primary` |
| `Unverified` | `Verified` (unfilled) | `colorScheme.onSurfaceVariant` |
| `SubscribedPost` | `Article` | `colorScheme.onSurfaceVariant` |
| `Unknown` | `Notifications` (unfilled) | `colorScheme.onSurfaceVariant` |

The composable SHALL be exhaustive over `NotificationReason` so adding a new enum value SHALL produce a compile error in `:designsystem` until the mapping is updated.

#### Scenario: Like reason renders the heart with like-accent tint

- **WHEN** `NotificationReasonIcon(reason = NotificationReason.Like)` is rendered
- **THEN** the icon SHALL be the filled `Favorite` glyph tinted with the `likeAccent` extended token

#### Scenario: Adding a new reason fails compilation until mapped

- **WHEN** a new value is added to `NotificationReason` and `NotificationReasonIcon` is rebuilt without an updated mapping
- **THEN** the Kotlin compiler SHALL flag a non-exhaustive `when` expression in `NotificationReasonIcon`'s implementation

### Requirement: `NotificationReasonIcon` ships `@Preview` and screenshot tests

`:designsystem` SHALL include a `@Preview`-annotated showcase composable rendering `NotificationReasonIcon` for every `NotificationReason` value, plus a corresponding `@PreviewTest`. Baselines SHALL be committed under `designsystem/src/screenshotTestDebug/reference/`.

#### Scenario: Showcase preview renders all reasons

- **WHEN** the design-system screenshot test job runs
- **THEN** the `NotificationReasonIcon` showcase SHALL render at least one row per `NotificationReason` value and match the committed baseline
