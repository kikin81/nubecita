## ADDED Requirements

### Requirement: `EmbedUi` exposes a `Video` variant for `app.bsky.embed.video#view`

The `EmbedUi` sealed interface in `:data:models` MUST expose a `Video` data class variant carrying:

- `posterUrl: String?` — fully-qualified URL to the JPEG/WebP poster, or `null` when the lexicon's `view` form omits the optional `thumbnail` field. The render layer falls back to a gradient placeholder when null.
- `playlistUrl: String` — fully-qualified URL to the HLS .m3u8 playlist. Required by the lexicon `view` form; the mapper falls through to `EmbedUi.Unsupported` when absent.
- `aspectRatio: Float` — width / height ratio from the lexicon (e.g. `1.777f` for 16:9). Used to size the poster + PlayerView surface. The mapper supplies a 16:9 fallback (`1.777f`) when the lexicon omits the optional `aspectRatio` field, since the render layer needs a stable measurement before the poster loads.
- `durationSeconds: Int?` — duration in seconds, or `null` when not available. **The `app.bsky.embed.video#view` lexicon does NOT currently expose a duration field** (verified against the upstream Bluesky lexicon; only `cid`, `playlist`, `thumbnail`, `aspectRatio`, `presentation`, `alt` are present). The mapper SHALL pass `null` for v1; the field is reserved for a future phase that sources duration either from a lexicon evolution or from the HLS manifest's `EXT-X-PLAYLIST-TYPE: VOD` segments after the player loads. Render layer renders the duration chip ONLY when this field is non-null.
- `altText: String?` — optional alt-text for accessibility surfaces.

The `EmbedUi` sealed interface remains `@Immutable` (the convention `EmbedUi` already follows — variants inherit the annotation and MUST contain only immutable value fields). All five new fields are immutable values, so `EmbedUi.Video` satisfies the existing stability contract without per-variant annotation. A null `EmbedUi` instance is NOT permissible — every well-formed video view yields a non-null `EmbedUi.Video`; a malformed view (missing required `playlist`) yields `EmbedUi.Unsupported(typeUri = "app.bsky.embed.video")` instead.

#### Scenario: Video variant is part of the sealed hierarchy

- **WHEN** an exhaustive `when (embed: EmbedUi)` is written in the project source
- **THEN** the compiler SHALL require an arm for `EmbedUi.Video`; omitting it is a compile error

#### Scenario: Stable for Compose skipping via the sealed-interface annotation

- **WHEN** a `PostUi` whose `embed is EmbedUi.Video` is passed to a `@Composable` function whose parameter is `EmbedUi`
- **THEN** Compose SHALL treat the parameter as stable (because `EmbedUi` is `@Immutable`-annotated at the sealed-interface level and `EmbedUi.Video`'s fields are all immutable values) and skip recomposition when the embed reference is structurally equal across recompositions
