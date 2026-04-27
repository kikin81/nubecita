## ADDED Requirements

### Requirement: `EmbedUi` exposes a `Video` variant for `app.bsky.embed.video#view`

The `EmbedUi` sealed interface in `:data:models` MUST expose a `Video` data class variant carrying:

- `posterUrl: String` — fully-qualified URL to the JPEG/WebP poster.
- `playlistUrl: String` — fully-qualified URL to the HLS .m3u8 playlist.
- `aspectRatio: Float` — width / height ratio from the lexicon (e.g. `1.777f` for 16:9). Used to size the poster + PlayerView surface.
- `durationSeconds: Int` — duration in seconds (the lexicon carries milliseconds; `FeedViewPostMapper` performs the conversion at the boundary, with a 1-second floor for very short clips so the render layer never displays `0:00`).
- `altText: String?` — optional alt-text for accessibility surfaces.

The `EmbedUi` sealed interface remains `@Immutable` (the convention `EmbedUi` already follows — variants inherit the annotation and MUST contain only immutable value fields). All five new fields are immutable values, so `EmbedUi.Video` satisfies the existing stability contract without per-variant annotation. A null `EmbedUi` instance is NOT permissible — every well-formed video view yields a non-null `EmbedUi.Video`; a malformed view yields `EmbedUi.Unsupported(typeUri = "app.bsky.embed.video")` instead.

#### Scenario: Video variant is part of the sealed hierarchy

- **WHEN** an exhaustive `when (embed: EmbedUi)` is written in the project source
- **THEN** the compiler SHALL require an arm for `EmbedUi.Video`; omitting it is a compile error

#### Scenario: Stable for Compose skipping via the sealed-interface annotation

- **WHEN** a `PostUi` whose `embed is EmbedUi.Video` is passed to a `@Composable` function whose parameter is `EmbedUi`
- **THEN** Compose SHALL treat the parameter as stable (because `EmbedUi` is `@Immutable`-annotated at the sealed-interface level and `EmbedUi.Video`'s fields are all immutable values) and skip recomposition when the embed reference is structurally equal across recompositions
