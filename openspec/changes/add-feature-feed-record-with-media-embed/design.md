## Context

`app.bsky.embed.recordWithMedia#view` is a composite embed: a `media` slot (`Images` / `Video` / `External`) layered above a `record` slot (`RecordView` — same shape 6vq handles, with its own `viewRecord` / `viewNotFound` / `viewBlocked` / `viewDetached` variants). The lexicon shape:

```
RecordWithMediaView(
    media: RecordWithMediaViewMediaUnion,   // Images | Video | External | Unknown
    record: RecordView                       // wraps RecordViewRecordUnion (4 + Unknown)
)
```

Previously this was the v3 gate of the embed-scope roadmap, blocked on its constituent parts. Both blockers cleared (`nubecita-6vq` shipped record/quoted-post; video shipped earlier under `nubecita-sbc.4`). The mapper currently routes `RecordWithMediaView` to `EmbedUi.Unsupported("app.bsky.embed.recordWithMedia")`.

6vq's primitives factored cleanly enough that the bd issue calls this "little new code if the Record + Images/Video ones are well-factored." This change validates that — the renderer is a 30-line composition shell over existing leaf composables, the mapper reuses 6vq's helpers verbatim, and the data-model addition is two tiny marker interfaces + one new variant.

## Goals / Non-Goals

**Goals:**

- Render `app.bsky.embed.recordWithMedia#view` as media-on-top + quoted card below, matching the official Bluesky Android client's layout.
- Reuse 6vq's primitives (`PostCardQuotedPost`, `PostCardRecordUnavailable`, `PostCardImageEmbed`, `PostCardExternalEmbed`, video slot pattern, `RecordViewRecord.toEmbedUiRecord()`, shared payload helpers) verbatim — zero rendering or mapping code duplication.
- Express the recursion bound at the type system, consistent with 6vq's `QuotedEmbedUi` philosophy. Marker sealed interfaces (`RecordOrUnavailable`, `MediaEmbed`) make `RecordWithMedia` inside `RecordWithMedia` structurally inexpressible.
- Extend the most-visible-video coordinator to recognize the two new video-can-live-here positions (`media: Video` and `record.quotedPost.embed: Video`) without changing the visibility math.
- Drop no parent posts because of malformed quoted records — same contract as 6vq.

**Non-Goals:**

- Tap-to-PostDetail destination on the recordWithMedia card. Same deferral as 6vq's quoted card; lands in the post-detail-screen epic.
- Sub-rect visibility for nested videos. Coordinator stays at parent feed-item granularity (B-lite). RecordWithMedia + nested-quote-video is two levels deep but still resolves to a single bind candidate per item.
- Wrapping `Surface` to visually group media + quote into a single bordered card. The official client uses adjacency, not a bounding container. Revisit only if user feedback shows the visual grouping is unclear.
- `recordWithMedia` whose media is itself a `recordWithMedia`. The lexicon's `RecordWithMediaViewMediaUnion` doesn't allow this; the marker `MediaEmbed` enforces the bound at the type system.
- Per-`Reason` copy on the unavailable-record-inside-recordWithMedia path. Same single-stub design as 6vq.
- `EmbedUi.Unsupported` membership in `MediaEmbed`. Adding it would let the renderer show a "media unavailable" chip alongside a successfully-rendered quote, but the rare malformed-media case is better served by falling the whole composition through to `Unsupported` (Decision 4 below).

## Decisions

### Decision 1 — Compose via marker sealed interfaces, not new wrapper variants

Three options for the data model shape considered:

- **A (chosen).** Marker sealed interfaces. `EmbedUi.RecordOrUnavailable` is implemented by `Record` + `RecordUnavailable` only; `EmbedUi.MediaEmbed` is implemented by `Images` + `Video` + `External` only. `RecordWithMedia(record: RecordOrUnavailable, media: MediaEmbed)` reuses the existing data-class instances verbatim.
- **B.** New parallel wrapper types (`RecordWithMediaRecord`, `RecordWithMediaMedia`) following 6vq's `QuotedEmbedUi` pattern.
- **C.** Loose typing (`record: EmbedUi, media: EmbedUi`) with documentation + runtime guards.

Chosen: A. The fundamental difference between this case and 6vq's `QuotedEmbedUi` case: a quoted post's inner embed must NOT be a `Record` (recursion bound — record-inside-record is meaningless), so its marker bound goes a different direction than what `EmbedUi`'s sealed hierarchy expresses, requiring a parallel sealed type. Here, the marker bound (`Record` or `RecordUnavailable` only) is a SUBSET of `EmbedUi`'s sealed hierarchy, so a marker interface that picks out that subset is the natural fit. Same instance of `EmbedUi.Record` is structurally an `EmbedUi.RecordOrUnavailable`; no wrapper conversion needed at the call site, no payload duplication.

Compile-time properties:

| What's prevented | How |
|---|---|
| `RecordWithMedia` inside `RecordWithMedia` (any slot) | Doesn't implement either marker |
| `Images` / `Video` / `External` in record slot | Doesn't implement `RecordOrUnavailable` |
| `Record` / `RecordUnavailable` in media slot | Doesn't implement `MediaEmbed` |
| `Empty` / `Unsupported` in either slot | Doesn't implement either marker |

The two marker sealed interfaces extend `EmbedUi` directly (`sealed interface RecordOrUnavailable : EmbedUi`), so any value of these markers is automatically an `EmbedUi`. Implementers declare just `: RecordOrUnavailable` (or `: MediaEmbed`) without redundant `: EmbedUi`.

### Decision 2 — Reuse 6vq's leaf composables verbatim; renderer is dispatch only

`PostCardRecordWithMediaEmbed`'s body is two `when` dispatches over the marker sealed interfaces, each forwarding to the existing leaf composable for that variant:

- `media is Images` → `PostCardImageEmbed`
- `media is Video` → host-supplied `videoEmbedSlot` (bind key = parent `post.id`)
- `media is External` → `PostCardExternalEmbed` (with the parent's `onExternalEmbedTap` callback — the external is a real top-level link card)
- `record is Record` → `PostCardQuotedPost` (with the parent's `quotedVideoEmbedSlot` for inner-quote videos)
- `record is RecordUnavailable` → `PostCardRecordUnavailable`

No new rendering primitives. The composable is a 30-line dispatch shell.

### Decision 3 — No surrounding `Surface` for the composition

Considered wrapping the (media + quote) stack in a single `Surface(surfaceContainerLow)` to visually group them as one composite embed. Rejected:

- The official Bluesky Android client uses **adjacency**, not a bounding container.
- Wrapping in a Surface would force the media (e.g. an Image at full lexicon aspect ratio) into a card layout that competes with the surrounding parent post, and would force the quoted card's own `surfaceContainerLow` to either nest (visually heavier) or be dropped (loses the established quoted-card identity).
- Adjacency lets each component render in its native treatment — exactly what 6vq established for `PostCardQuotedPost` (which is itself a Surface) and what `PostCardImageEmbed` / `PostCardExternalEmbed` already do.

8 dp spacer between media and quote, and the standard 10 dp spacer between the parent post body and the entire composite — same convention every other embed uses.

### Decision 4 — Asymmetric error handling: record-malformed degrades, media-malformed fails

When a wire payload is malformed:

- **Record side malformed** (e.g. quoted record `value` JSON missing required `text`): the record slot becomes `RecordUnavailable.Unknown`. Media still renders. Parent post NEVER dropped. Same contract as 6vq.
- **Media side malformed** (empty video playlist, unknown media variant): the WHOLE composition falls through to `EmbedUi.Unsupported("app.bsky.embed.recordWithMedia")`. The post degrades to a chip, no recordWithMedia rendering at all.

The asymmetry is deliberate. The record side has explicit lexicon variants for the unavailable cases (`viewNotFound` / `viewBlocked` / `viewDetached`) — graceful degradation is the author of the wire payload's intent. The media side has no such graceful-failure shape; an empty video playlist is just malformed data. Half-rendering a recordWithMedia (media-only without the quote) loses the post's communicative intent — the quote is the load-bearing context.

Alternative considered: add `EmbedUi.Unsupported` to `MediaEmbed` so the renderer can show a "media unavailable" chip alongside the successfully-rendered quote. Rejected — the malformed-media case is rare enough that a whole-thing fallthrough is cleaner than a third "media unavailable" UI surface.

### Decision 5 — Coordinator precedence: parent video > media video > nested quoted video

The most-visible-video coordinator's `videoBindingFor(post)` helper resolves a post to its bind candidate. With recordWithMedia in the picture there are now four positions a video can live:

1. Top-level parent (`post.embed is EmbedUi.Video`)
2. recordWithMedia media (`post.embed is RecordWithMedia` whose `media is Video`)
3. recordWithMedia.record.quotedPost (`post.embed is RecordWithMedia` whose `record is Record` whose `quotedPost.embed is QuotedEmbedUi.Video`)
4. quoted post (`post.embed is EmbedUi.Record` whose `quotedPost.embed is QuotedEmbedUi.Video`)

Precedence: **1 > 2 > 3 ≈ 4** (3 and 4 don't co-occur structurally — a post can carry exactly one of `Record` / `RecordWithMedia`).

Why media (2) beats nested quoted (3) when both are present in a single recordWithMedia: the media is the user's primary upload — explicitly attached to THIS post — while the nested quoted-post-video is contextual content authored by someone else. Letting the nested quoted video win would create disjointed UX: a large frozen poster at the top (the media) with a smaller autoplaying video tucked inside the quoted card below.

Bind identities (key the coordinator uses to detect "is this the same target as before?"):

- Parent video → parent `post.id`
- RecordWithMedia.media video → parent `post.id` (same item, no need for a different identity since precedence guarantees only one is bound)
- RecordWithMedia.record.quotedPost video → `quotedPost.uri`
- Quoted post video → `quotedPost.uri`

Visibility math is unchanged from 6vq's B-lite — still parent feed-item granularity, no `Modifier.onGloballyPositioned` plumbing.

### Decision 6 — Centralize "where do quotes hide" in an extension property

Both `FeedScreen`'s `quotedVideoSlot` builder and the coordinator's `videoBindingFor` need to ask the same question: given a post's embed, where (if anywhere) is the quoted post? Without `RecordWithMedia` the answer is always `(post.embed as? EmbedUi.Record)?.quotedPost`. With `RecordWithMedia` it's:

```kotlin
(post.embed as? EmbedUi.Record)?.quotedPost
    ?: ((post.embed as? EmbedUi.RecordWithMedia)?.record as? EmbedUi.Record)?.quotedPost
```

Rather than duplicate this chained-cast at every call site (and risk drift when a future composite embed type lands), introduce one extension property in `:data:models`:

```kotlin
val EmbedUi.quotedRecord: QuotedPostUi?
    get() = when (this) {
        is EmbedUi.Record           -> quotedPost
        is EmbedUi.RecordWithMedia  -> (record as? EmbedUi.Record)?.quotedPost
        else                        -> null
    }
```

Both call sites then just read `post.embed.quotedRecord`. If future lexicon evolution adds another composite embed that contains a quoted post, the extension property is the single point of update.

### Decision 7 — DRY the wrapper-construction helpers across parent and media dispatch

6vq extracted payload helpers (`toImageUiList`, `toVideoPayload`) that return just the inner data, with the wrapper construction at the call site. For umn, the parent `toEmbedUi` dispatch and the new `toMediaEmbed` dispatch construct the SAME wrapper variants (`EmbedUi.Images` / `Video` / `External`). Inline construction at both call sites would risk drift — e.g., an `aspectRatio` calculation tweak getting updated in one path and forgotten in the other.

Extract three wrapper-construction helpers — `ImagesView.toEmbedUiImages()`, `VideoView.toEmbedUiVideo()`, `ExternalView.toEmbedUiExternal()` — that produce the actual `EmbedUi.X` values. The parent dispatch and `toMediaEmbed` both route through these. Single source of truth for wrapper construction.

These wrapper helpers don't replace 6vq's payload helpers — they call them. `toEmbedUiVideo` calls `toVideoPayload`; `toEmbedUiImages` calls `toImageUiList`. The payload helpers stay because the inner-quote dispatch (`toQuotedEmbedUi`, returning `QuotedEmbedUi.Images` etc., NOT `EmbedUi.Images`) needs the inner data without the parent-style wrapper.

### Decision 8 — Tighten 6vq's `toEmbedUiFromRecordView` return type

6vq's helper `RecordView.toEmbedUiFromRecordView(): EmbedUi` always structurally returns one of `EmbedUi.Record`, `EmbedUi.RecordUnavailable`, or `EmbedUi.RecordUnavailable(Unknown)` — every output is by construction a `RecordOrUnavailable`. Renaming to `toRecordOrUnavailable()` and tightening the return type to `EmbedUi.RecordOrUnavailable` lets `toEmbedUiRecordWithMedia` use it directly without an `as` cast.

The parent `is RecordView ->` arm still works because `RecordOrUnavailable : EmbedUi` — the upcast is implicit. Pure type-narrowing refactor; no behavior change.

## Risks / Trade-offs

- **Risk:** Adding `EmbedUi.RecordWithMedia` makes every consumer's `when (embed: EmbedUi)` non-exhaustive at compile time.
  → **Mitigation:** This is the intended behavior — same surface used when `Record` and `RecordUnavailable` were added. Forces every dispatch site to add an arm. Three known consumers (PostCard's EmbedSlot, the mapper's coverage tests) — small enumeration, all updated in this change.

- **Risk:** A `recordWithMedia` post whose media is a Video AND whose nested quoted post also carries a video creates two video sources for the player coordinator. The B-lite precedence picks media.
  → **Mitigation:** Documented in Decision 5; tests cover the case. If real-world feedback shows users expect quoted-video to play instead, B-plus (sub-rect visibility) is the natural promotion path.

- **Risk:** No surrounding `Surface` for the composite means the visual boundary between media and quote relies on adjacency + the quoted card's own surface. On a busy feed this could read as "media followed by a separate quoted-post embed" rather than "one composite."
  → **Mitigation:** Matches the official client's behavior; their UX research presumably validated it. If feedback shows otherwise, wrapping in a `surfaceContainerLow` Surface is a clean follow-up that doesn't affect the data model.

- **Risk:** The `quotedRecord` extension property knows about both `EmbedUi.Record` and `EmbedUi.RecordWithMedia`. A future composite embed type (e.g. `embedWithRecord`) would require updating the extension.
  → **Mitigation:** Single point of update; better than the alternative of chained casts duplicated at every call site. Not a real regression risk.

- **Trade-off:** Marker interfaces add two new public types to `:data:models`'s API surface. The 6vq path of nested wrappers (`QuotedEmbedUi`) avoided that. Accepted because the marker-based approach reuses the existing data classes verbatim — net code volume is lower than the wrapper approach, and the recursion bound is cleaner (no parallel hierarchy to keep in sync with `EmbedUi`).

- **Trade-off:** Whole-thing-fallthrough on malformed media means a single broken video URL on the wire makes a recordWithMedia post degrade to an Unsupported chip even though the quoted post is intact. Acceptable per Decision 4; alternative (render quote alone) loses the post's communicative intent.
