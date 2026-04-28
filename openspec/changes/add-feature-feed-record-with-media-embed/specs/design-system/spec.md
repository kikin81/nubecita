## MODIFIED Requirements

### Requirement: PostCard's embed slot dispatches on the sealed `EmbedUi` type

PostCard MUST contain an embed slot that uses a `when (post.embed)` expression to dispatch to one of the supporting embed composables. The dispatch MUST be exhaustive over `EmbedUi.Empty`, `EmbedUi.Images`, `EmbedUi.Video`, `EmbedUi.External`, `EmbedUi.Record`, `EmbedUi.RecordUnavailable`, `EmbedUi.RecordWithMedia`, and `EmbedUi.Unsupported`:

- `EmbedUi.Empty` → no embed slot rendered
- `EmbedUi.Images` → `PostCardImageEmbed(items = embed.items)` (1–4 images, see separate requirement)
- `EmbedUi.Video` → `videoEmbedSlot(embed)` (host-supplied; see separate requirement)
- `EmbedUi.External` → `PostCardExternalEmbed(uri, domain, title, description, thumbUrl, onTap = callbacks.onExternalEmbedTap)`
- `EmbedUi.Record` → `PostCardQuotedPost(quotedPost = embed.quotedPost, quotedVideoEmbedSlot)` (see separate requirement)
- `EmbedUi.RecordUnavailable` → `PostCardRecordUnavailable(reason = embed.reason)` (see separate requirement)
- `EmbedUi.RecordWithMedia` → `PostCardRecordWithMediaEmbed(record = embed.record, media = embed.media, onExternalMediaTap = callbacks.onExternalEmbedTap, videoEmbedSlot, quotedVideoEmbedSlot)` (see separate requirement)
- `EmbedUi.Unsupported` → `PostCardUnsupportedEmbed(typeUri = embed.typeUri)`

Future lexicon evolution that adds an `EmbedUi` variant MUST be handled in the same change that adds the variant — the sealed type makes this a compile error otherwise.

#### Scenario: Image embed renders the supporting composable

- **WHEN** PostCard renders a `PostUi` whose `embed is EmbedUi.Images` with two image items
- **THEN** `PostCardImageEmbed` is invoked exactly once with the `items` list, and the composable lays out the two images per the image-embed sub-requirement.

#### Scenario: Record embed renders the quoted-post composable

- **WHEN** PostCard renders a `PostUi` whose `embed is EmbedUi.Record` with a populated `QuotedPostUi`
- **THEN** `PostCardQuotedPost` is invoked exactly once with the quoted post data; the parent post's body text + author header continue to render above the quoted-post surface

#### Scenario: RecordUnavailable embed renders the unavailable chip

- **WHEN** PostCard renders a `PostUi` whose `embed is EmbedUi.RecordUnavailable(Reason.NotFound)`
- **THEN** `PostCardRecordUnavailable` is invoked with `reason = Reason.NotFound`; renders a small chip with copy "Quoted post unavailable" — the same copy is rendered for `Reason.Blocked`, `Reason.Detached`, and `Reason.Unknown` (single-stub per the design)

#### Scenario: RecordWithMedia embed renders the composite composable

- **WHEN** PostCard renders a `PostUi` whose `embed is EmbedUi.RecordWithMedia` carrying a resolved `Record` + `Images` media
- **THEN** `PostCardRecordWithMediaEmbed` is invoked exactly once with the record + media slots, the parent's `callbacks.onExternalEmbedTap` (for the case where the media is `External`), and both video slot lambdas. The composable lays media above the quoted card per its own requirement.

#### Scenario: Unsupported embed renders the deliberate-degradation chip

- **WHEN** PostCard renders a `PostUi` whose `embed is EmbedUi.Unsupported(typeUri = "app.bsky.embed.somethingNew")`
- **THEN** `PostCardUnsupportedEmbed` is invoked, rendering a small `surfaceContainerHighest` chip with secondary-text label derived from the lexicon URI via the existing friendly-name mapping. No error styling, no error icon — this is deliberate degradation, not a failure.

## ADDED Requirements

### Requirement: `PostCardRecordWithMediaEmbed` renders a Bluesky `app.bsky.embed.recordWithMedia#view` as media-above-quote composition

`PostCardRecordWithMediaEmbed` MUST be a public composable in `:designsystem` accepting:

- `record: EmbedUi.RecordOrUnavailable` — either a resolved `Record` (carrying a `QuotedPostUi`) or a `RecordUnavailable` (with a `Reason`).
- `media: EmbedUi.MediaEmbed` — exactly one of `Images`, `Video`, or `External`.
- `modifier: Modifier = Modifier`.
- `onExternalMediaTap: ((uri: String) -> Unit)? = null` — invoked when `media is External` and the user taps it. Default null causes `PostCardExternalEmbed` to omit `Modifier.clickable` for the same null-tap-omits-clickable contract that 6vq established. The host (`PostCard.EmbedSlot`) MUST pass `callbacks.onExternalEmbedTap` for parent-feed rendering — the External media is a real top-level link card on the post and SHOULD open in Custom Tabs the same way a top-level `EmbedUi.External` does.
- `videoEmbedSlot: (@Composable (EmbedUi.Video) -> Unit)? = null` — invoked when `media is Video`. Same shape as `PostCard.videoEmbedSlot`; the host passes through its own slot. Default null leaves the video region empty (preview / screenshot tests).
- `quotedVideoEmbedSlot: (@Composable (QuotedEmbedUi.Video) -> Unit)? = null` — forwarded to `PostCardQuotedPost` when `record is Record` and the inner `quotedPost.embed is Video`. Default null leaves nested-quoted-video region empty.

Layout (top to bottom):

- **Media region.** Renders the media at its native treatment via the existing leaf composable for that variant. NO surrounding `Surface` — adjacency to the quoted card below provides the visual grouping (matches the official Bluesky Android client's layout).
- **8 dp `Spacer`** between media and quoted card.
- **Quoted card.** Renders the record at its native treatment via either `PostCardQuotedPost` (resolved) or `PostCardRecordUnavailable` (unavailable chip).

The composable MUST NOT wrap its content in a `Surface` or other bounding container. The two regions stack as siblings inside a `Column`, separated only by the `Spacer`.

The composable MUST NOT have its own `Modifier.clickable`. Same tap-target deferral as `PostCardQuotedPost` — tap-to-open-PostDetail lands in a follow-up bd issue paired with the post-detail destination so wiring lands once.

The internal media-side `when` MUST be exhaustive over `EmbedUi.MediaEmbed` (no `else` branch). The internal record-side `when` MUST be exhaustive over `EmbedUi.RecordOrUnavailable` (no `else` branch). Both are sealed; future variant additions to either marker would surface as compile errors.

#### Scenario: Resolved record + Images media renders the composition

- **WHEN** `PostCardRecordWithMediaEmbed` is rendered with `record = EmbedUi.Record(quotedPost)` and `media = EmbedUi.Images(items)` carrying two images
- **THEN** the rendered subtree contains exactly one `PostCardImageEmbed` (above) and exactly one `PostCardQuotedPost` (below), separated by an 8 dp `Spacer`. NO surrounding `Surface` is present.

#### Scenario: RecordUnavailable + External media renders the unavailable chip + the link card

- **WHEN** `PostCardRecordWithMediaEmbed` is rendered with `record = EmbedUi.RecordUnavailable(Reason.NotFound)` and `media = EmbedUi.External(...)` and `onExternalMediaTap = lambda`
- **THEN** the rendered subtree contains exactly one `PostCardExternalEmbed` (above, with `onTap = lambda` so it's tappable) and exactly one `PostCardRecordUnavailable` (below, rendering the "Quoted post unavailable" chip)

#### Scenario: Default null onExternalMediaTap is non-clickable

- **WHEN** `PostCardRecordWithMediaEmbed` is rendered with `media = EmbedUi.External(...)` and `onExternalMediaTap = null`
- **THEN** the inner `PostCardExternalEmbed` is rendered without `Modifier.clickable` (no ripple, no tap target) per the leaf composable's null-tap contract

#### Scenario: Default null videoEmbedSlot leaves the media region empty

- **WHEN** `PostCardRecordWithMediaEmbed` is rendered with `media = EmbedUi.Video(...)` and `videoEmbedSlot = null`
- **THEN** the media region renders nothing — no crash, no `Unsupported` chip, no fallback poster. The quoted card below still renders.

#### Scenario: Composable is not clickable in v1

- **WHEN** the rendered `PostCardRecordWithMediaEmbed` composable's root modifier chain is inspected
- **THEN** there SHALL be no `Modifier.clickable` on the root; tap-to-open is deferred to a follow-up bd issue
