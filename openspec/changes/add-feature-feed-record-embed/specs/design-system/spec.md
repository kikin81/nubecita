## MODIFIED Requirements

### Requirement: PostCard's embed slot dispatches on the sealed `EmbedUi` type

PostCard MUST contain an embed slot that uses a `when (post.embed)` expression to dispatch to one of the supporting embed composables. The dispatch MUST be exhaustive over `EmbedUi.Empty`, `EmbedUi.Images`, `EmbedUi.Video`, `EmbedUi.External`, `EmbedUi.Record`, `EmbedUi.RecordUnavailable`, and `EmbedUi.Unsupported`:

- `EmbedUi.Empty` → no embed slot rendered
- `EmbedUi.Images` → `PostCardImageEmbed(items = embed.items)` (1–4 images, see separate requirement)
- `EmbedUi.Video` → `videoEmbedSlot(embed)` (host-supplied; see separate requirement)
- `EmbedUi.External` → `PostCardExternalEmbed(uri, domain, title, description, thumbUrl, onTap = callbacks.onExternalEmbedTap)`
- `EmbedUi.Record` → `PostCardQuotedPost(quotedPost = embed.quotedPost, quotedVideoEmbedSlot)` (see separate requirement)
- `EmbedUi.RecordUnavailable` → `PostCardRecordUnavailable(reason = embed.reason)` (see separate requirement)
- `EmbedUi.Unsupported` → `PostCardUnsupportedEmbed(typeUri = embed.typeUri)`

When new embed variants land in future changes (e.g. `EmbedUi.RecordWithMedia` per `nubecita-umn`), this dispatch MUST be extended in the same change that adds the variant — the sealed type makes this a compile error otherwise.

#### Scenario: Image embed renders the supporting composable

- **WHEN** PostCard renders a `PostUi` whose `embed is EmbedUi.Images` with two image items
- **THEN** `PostCardImageEmbed` is invoked exactly once with the `items` list, and the composable lays out the two images per the image-embed sub-requirement.

#### Scenario: Record embed renders the quoted-post composable

- **WHEN** PostCard renders a `PostUi` whose `embed is EmbedUi.Record` with a populated `QuotedPostUi`
- **THEN** `PostCardQuotedPost` is invoked exactly once with the quoted post data; the parent post's body text + author header continue to render above the quoted-post surface

#### Scenario: RecordUnavailable embed renders the unavailable chip

- **WHEN** PostCard renders a `PostUi` whose `embed is EmbedUi.RecordUnavailable(Reason.NotFound)`
- **THEN** `PostCardRecordUnavailable` is invoked with `reason = Reason.NotFound`; renders a small chip with copy "Quoted post unavailable" — the same copy is rendered for `Reason.Blocked`, `Reason.Detached`, and `Reason.Unknown` (single-stub per the design)

#### Scenario: Unsupported embed renders the deliberate-degradation chip

- **WHEN** PostCard renders a `PostUi` whose `embed is EmbedUi.Unsupported(typeUri = "app.bsky.embed.recordWithMedia")`
- **THEN** `PostCardUnsupportedEmbed` is invoked, rendering a small `surfaceContainerHighest` chip with secondary-text label "Unsupported embed: quoted post with media" — derived from the lexicon URI via the existing friendly-name mapping. No error styling, no error icon — this is deliberate degradation, not a failure.

## ADDED Requirements

### Requirement: `PostCardQuotedPost` renders a Bluesky `app.bsky.embed.record#viewRecord` at near-parent density

`PostCardQuotedPost` MUST be a public composable in `:designsystem` accepting a `QuotedPostUi` parameter, an optional `quotedVideoEmbedSlot: (@Composable (QuotedEmbedUi.Video) -> Unit)? = null` parameter, and a `Modifier`. It MUST render the quoted post inside a `Surface` with `color = surfaceContainerLow` and `shape = RoundedCornerShape(12.dp)`.

The composable's layout, top to bottom:

- **Author row.** A `Row` with 8 dp horizontal arrangement: a 32 dp `NubecitaAvatar` (smaller than the parent post's 40 dp), then a single non-wrapping `Text` line carrying the display name + handle + a relative-time stamp (e.g. "Acyn @acyn.bsky.social · 4h"). Display name uses `labelLarge`; handle and timestamp use `bodySmall` + `onSurfaceVariant`. The line MUST NOT wrap; ellipsis truncates the handle when space is tight (display name + timestamp keep priority).
- **Body text.** The full `text` from `QuotedPostUi`, rendered in `bodyMedium`. NO `maxLines` cap — the quoted post is meant to be read in full per the official client's behavior.
- **Inner embed.** Dispatched via the internal `QuotedEmbedSlot` composable per the separate requirement below.

`PostCardQuotedPost` MUST NOT render an action row (no reply / repost / like / share affordances). It MUST NOT have a `Modifier.clickable` — v1 ships without a tap target. The `PostCallbacks.onQuotedPostTap` callback is intentionally NOT introduced in this change; tap-to-open lands together with the in-app post-detail destination in a separate bd issue.

#### Scenario: Avatar size is 32 dp

- **WHEN** `PostCardQuotedPost` is rendered with a non-null `quotedPost.author.avatarUrl`
- **THEN** the rendered avatar's size is exactly `32.dp` (not the 40 dp the parent `PostCard` author row uses)

#### Scenario: Body text has no maxLines cap

- **WHEN** `PostCardQuotedPost` is rendered with `quotedPost.text` of 800 characters
- **THEN** the body `Text` composable renders without `maxLines` truncation; ellipsis MUST NOT appear in the rendered output

#### Scenario: No action row is rendered

- **WHEN** the rendered `PostCardQuotedPost` composable's subtree is inspected
- **THEN** there SHALL be no `IconButton` or `PostStat` instance for reply / repost / like / share inside the quoted card's `Surface`

#### Scenario: Card surface is not clickable in v1

- **WHEN** the rendered `PostCardQuotedPost` composable's `Surface` modifier chain is inspected
- **THEN** there SHALL be no `Modifier.clickable` applied to the `Surface` — taps on the quoted card are deliberate no-ops in v1

### Requirement: `QuotedEmbedSlot` dispatches the quoted post's inner embed exhaustively over `QuotedEmbedUi`

`PostCardQuotedPost` MUST contain (or compose) an internal embed-dispatch slot that uses an exhaustive `when (quotedPost.embed)` expression over the `QuotedEmbedUi` sealed interface:

- `QuotedEmbedUi.Empty` → no embed rendered
- `QuotedEmbedUi.Images` → `PostCardImageEmbed(items = embed.items)` — same leaf composable the parent `EmbedSlot` uses
- `QuotedEmbedUi.External` → `PostCardExternalEmbed(uri, domain, title, description, thumbUrl, onTap = {})` — same leaf composable the parent `EmbedSlot` uses; tap is a no-op inside a quoted card (the outer card owns the tap surface, even though it's deferred in v1)
- `QuotedEmbedUi.Video` → `quotedVideoEmbedSlot?.invoke(embed)` — when the host did not supply a slot, no video is rendered (default-null behavior preserves `:designsystem`'s media-free preview / screenshot tests)
- `QuotedEmbedUi.QuotedThreadChip` → small surface-tile placeholder with copy "View thread" in `labelMedium` + `onSurfaceVariant` — same surface treatment as `PostCardRecordUnavailable`, different copy
- `QuotedEmbedUi.Unsupported` → `PostCardUnsupportedEmbed(typeUri = embed.typeUri)` — reuses the existing friendly-name mapping leaf composable

The `QuotedEmbedUi` dispatch MUST be exhaustive at compile time. Because `QuotedEmbedUi` deliberately excludes a `Record` variant (per the data-models spec), this dispatch site CAN NOT have an arm for nested record embeds — the recursion bound is structural, not runtime.

#### Scenario: Compile-time dispatch is exhaustive

- **WHEN** the source for `PostCardQuotedPost`'s `when (embed)` expression is inspected
- **THEN** every variant of `QuotedEmbedUi` is covered; there is no `else ->` branch (the sealed interface makes `else` redundant)

#### Scenario: Quoted thread chip renders "View thread"

- **WHEN** `PostCardQuotedPost` renders a `QuotedPostUi` whose `embed is QuotedEmbedUi.QuotedThreadChip`
- **THEN** a small surface-tile is rendered carrying `Text("View thread")` in `labelMedium` + `onSurfaceVariant`; no further descent into a doubly-quoted post occurs

#### Scenario: Default null video slot renders nothing

- **WHEN** `PostCardQuotedPost` is invoked without a `quotedVideoEmbedSlot` and the quoted post's `embed is QuotedEmbedUi.Video`
- **THEN** the inner embed region renders empty — no crash, no `Unsupported` chip, no fallback poster

### Requirement: `PostCardRecordUnavailable` renders the single-stub unavailable chip

`PostCardRecordUnavailable` MUST be a public composable in `:designsystem` accepting an `EmbedUi.RecordUnavailable.Reason` parameter and a `Modifier`. It MUST render a small chip with the same surface treatment as `PostCardUnsupportedEmbed` (`surfaceContainerHighest`, `RoundedCornerShape(8.dp)`, padded label) carrying the single piece of copy "Quoted post unavailable" regardless of `Reason`. The `Reason` argument is accepted for forward compatibility (per-variant copy upgrade) and for telemetry / debug consumers; v1 MUST NOT use it to vary the rendered copy or icon.

#### Scenario: All four Reason values render identical copy

- **WHEN** `PostCardRecordUnavailable` is rendered four times with `Reason.NotFound`, `Reason.Blocked`, `Reason.Detached`, `Reason.Unknown` respectively
- **THEN** the rendered `Text` content is the string "Quoted post unavailable" in all four cases; the only deliberate variation is the input parameter, not the output

### Requirement: `PostCard` exposes a `quotedVideoEmbedSlot` lambda for host-supplied quoted-video render

`PostCard` MUST accept an optional `quotedVideoEmbedSlot: (@Composable (QuotedEmbedUi.Video) -> Unit)? = null` parameter, paralleling the existing `videoEmbedSlot` parameter. When `EmbedSlot` dispatches and the post's embed is `EmbedUi.Record` whose `quotedPost.embed is QuotedEmbedUi.Video`, the slot lambda MUST be invoked with the quoted-video data; a default `null` slot causes no quoted video to render (preserving `:designsystem`'s media-free preview path). `:designsystem` MUST NOT import or depend on any feature module to render the quoted video; the slot's body lives in `:feature:feed:impl` and is supplied by `FeedScreen` when it constructs PostCard.

`PostCard` MUST forward the `quotedVideoEmbedSlot` parameter to `PostCardQuotedPost` when dispatching `EmbedUi.Record`.

#### Scenario: Default slot is no-op for quoted videos

- **WHEN** `PostCard` is invoked without supplying `quotedVideoEmbedSlot` and the post's `embed is EmbedUi.Record` whose inner `embed is QuotedEmbedUi.Video`
- **THEN** the quoted card renders the author row + body text but the inner embed region is empty (no crash, no `Unsupported` chip)

#### Scenario: Host-supplied slot renders the quoted-video composable

- **WHEN** `PostCard` is invoked with `quotedVideoEmbedSlot = { qVideo -> PostCardVideoEmbed(quotedVideo = qVideo, postId = quotedPost.uri, coordinator) }`
- **THEN** the slot is invoked with the `QuotedEmbedUi.Video` instance; PostCard does not also render its own placeholder for the quoted-video region

#### Scenario: `:designsystem` does not depend on `:feature:feed:impl` for quoted video rendering

- **WHEN** `:designsystem`'s `build.gradle.kts` is inspected after this change lands
- **THEN** there SHALL still be no `implementation(project(":feature:feed:impl"))` dependency — the slot pattern keeps `:feature:feed:impl → :designsystem` as the only edge
