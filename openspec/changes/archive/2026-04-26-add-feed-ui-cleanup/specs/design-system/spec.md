## ADDED Requirements

### Requirement: `RobotoFlexFontFamily` honors the variable `wght` axis via per-weight `FontVariation` declarations

`RobotoFlexFontFamily` MUST declare one `Font` entry per supported `FontWeight` referenced by `nubecitaTypography` in `Type.kt`. Each entry MUST pass `variationSettings = FontVariation.Settings(FontVariation.weight(N.toFloat()))` so the Compose text engine resolves the correct axis position when rendering. At minimum, the family MUST include entries for `FontWeight.Normal` (400), `FontWeight.Medium` (500), `FontWeight.SemiBold` (600), and `FontWeight.Bold` (700). All entries reference the same bundled `R.font.roboto_flex` variable .ttf — the FontVariation settings are what make the rendering differ.

#### Scenario: bodyLarge renders at FontWeight.Normal visually distinct from titleMedium SemiBold

- **WHEN** a screenshot test renders two stacked `Text` composables — one styled `bodyLarge` (declared `FontWeight.Normal`) and one styled `titleMedium` (declared `FontWeight.SemiBold`) — with identical text content
- **THEN** the rendered glyphs SHALL show visibly different stroke weights — the SemiBold text SHALL be heavier than the Normal text

#### Scenario: Adding a new FontWeight to Type.kt requires a corresponding FontFamily entry

- **WHEN** a future change adds a `Type.kt` style declaring `FontWeight.ExtraBold` (800) on `RobotoFlexFontFamily`
- **THEN** that change MUST also add a `Font(resId = R.font.roboto_flex, weight = FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800f)))` entry to `RobotoFlexFontFamily`, otherwise the new style SHALL fall back to the closest declared weight at the same heavy-default rendering this requirement was created to fix

### Requirement: `PostCard.AuthorLine` renders displayName + handle + timestamp in a single non-wrapping row with right-pinned timestamp

`PostCard.AuthorLine` MUST render the author's display name, handle, and relative timestamp on exactly one visual line, regardless of handle length. The layout uses three separate `Text` composables in a `Row(verticalAlignment = Alignment.CenterVertically)`:

- The display name has `maxLines = 1` and `overflow = TextOverflow.Ellipsis`. It does NOT take a layout weight — it claims its intrinsic width up to the row's available space.
- The handle has `maxLines = 1`, `overflow = TextOverflow.Ellipsis`, and `Modifier.weight(1f, fill = false)` so it shrinks first when horizontal space is constrained.
- A `Spacer(Modifier.weight(1f))` between the handle and the timestamp absorbs all remaining horizontal space, pinning the timestamp to the row's trailing edge.
- The timestamp `Text` has `maxLines = 1` and no weight modifier — it renders at its intrinsic width on the right.

The string resources `postcard_handle` (formatted as `@%1$s`) and `postcard_relative_time` (the rendered duration string from the existing relative-time helper) replace the prior composite `postcard_handle_and_timestamp` resource.

#### Scenario: Long handle truncates with ellipsis instead of wrapping the timestamp

- **WHEN** a `PostCard` renders a post whose handle is `someverylonghandle.bsky.social` (30+ chars) and whose display name is `Alice Chen`
- **THEN** the row SHALL render on exactly one visual line: `Alice Chen   @someverylonghan…       5h` — the handle truncates with an ellipsis and the timestamp remains right-pinned

#### Scenario: Short handle leaves slack between handle and timestamp

- **WHEN** a `PostCard` renders a post whose handle is `alice.bsky.social` and display name is `Alice Chen`
- **THEN** the row SHALL render the full display name + full handle on the left, the full timestamp on the right, with the `Spacer(weight = 1f)` filling the gap between them

#### Scenario: Empty display name still pins timestamp right

- **WHEN** a `PostCard` renders a post whose `author.displayName` is the empty string (Bluesky permits this)
- **THEN** the row SHALL render the handle on the left and the timestamp on the right with no visual misalignment
