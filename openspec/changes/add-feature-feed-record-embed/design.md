## Context

Bluesky's `app.bsky.embed.record` is a "quoted post" embed — the lexicon view payload (`recordViewRecord`) carries the full quoted post (author, text record, optional inner embeds, counts, indexedAt, AT URI, CID). Today nubecita's `FeedViewPostMapper` routes every `RecordView` to `EmbedUi.Unsupported`, so users see a degradation chip on every quoted post in their timeline. Quoted posts are heavily used on Bluesky for commentary and news reposting; the gap is highly visible.

This was deliberately deferred per `2026-04-25-postcard-embed-scope-v1.md`:

> **`record` (v2):** Feed scrolling perf is verified at 120Hz; we have a clear answer on whether quoted PostCards use the same density or a compact variant.

Both gates have now cleared (PR #61 shipped external embeds; macrobench setup is in flight under `nubecita-ppj`). PostCard's existing slot-based architecture (per `add-feature-feed-video-embeds`) makes adding a record-embed surface straightforward — the harder questions are density, recursion bounding, and how the existing autoplay-muted video coordinator behaves when the video lives inside a quoted post.

## Goals / Non-Goals

**Goals:**

- Render `app.bsky.embed.record#viewRecord` as a nested quoted-post card whose density matches the official Bluesky Android client (parent layout minus action row; embed dispatch including inline video).
- Render the four "unavailable" union variants (`viewNotFound`, `viewBlocked`, `viewDetached`, future `Unknown`) as a single small chip — YAGNI on per-variant copy.
- Make the recursion bound (one level deep) a compile-time guarantee, not a runtime check.
- Extend the existing autoplay-muted video coordinator so a quoted post's video binds when its parent feed item is the most-visible target — without changing the visibility math (no sub-rect / `onGloballyPositioned` plumbing).
- Drop no parent posts because of malformed quoted records — the parent stays visible with a `RecordUnavailable.Unknown` stub for the bad embed.

**Non-Goals:**

- A working tap-to-open-quoted-post destination. The card is non-interactive in this change; introducing `PostCallbacks.onQuotedPostTap` and the post-detail screen is bundled in a separate bd issue so the wiring lands once instead of being introduced and then rewired.
- Sub-rect visibility for quoted videos. The coordinator's bind decision stays at the parent feed-item granularity — when a parent item carries a quoted video, the player binds the quoted video iff the parent item meets the existing 0.6 visible-fraction threshold.
- Differentiated copy per `RecordUnavailable.Reason`. Single string ("Quoted post unavailable") for v1; per-variant copy can land in a follow-up if user feedback shows it matters.
- `app.bsky.embed.recordWithMedia` rendering. Tracked under `nubecita-umn`; depends on this change shipping first.
- Two-level recursion (quote-of-quote-of-quote). Bounded to one level by the type system — a `RecordView` inside a quoted post's `embeds` list maps to `QuotedEmbedUi.QuotedThreadChip`.

## Decisions

### Decision 1 — Density: near-parent, not compact

Rejected the compact-density-with-truncated-text option. The official Bluesky Android client renders quoted posts at near-parent density (parent layout minus action row, full body text, ~32 dp avatar instead of 40 dp). This keeps the quoted post a first-class read while remaining visually distinct via surface treatment (`surfaceContainerLow` rounded inset) and the absent action row.

Compact density (single-line truncated text, smaller avatar, no inner embed) was attractive for screen real estate but leaves the user reading half-truncated quotes — worse experience than parity with the platform peer.

### Decision 2 — Compile-time recursion bound via separate `QuotedEmbedUi` type

The naive approach reuses `EmbedUi` for the quoted post's inner embed slot, then enforces the one-level recursion at the mapper (record-inside-record produces a sentinel value). This pushes the invariant to runtime and creates a footgun where future code might forget the guard.

Chosen: introduce a dedicated `QuotedEmbedUi` sealed interface that deliberately excludes a `Record` variant. The inner-embed dispatch in `PostCardQuotedPost` cannot have a `Record` arm — there's nothing to spell. A nested record in the wire data maps to `QuotedEmbedUi.QuotedThreadChip` ("View thread" placeholder) at the mapper boundary; thereafter, the type system carries the bound.

Wrapper-type duplication (`EmbedUi.Images` + `QuotedEmbedUi.Images`) is acceptable because the underlying payloads (`ImmutableList<ImageUi>`, the video field-set, the external field-set) are shared. Logic-side duplication is eliminated by extracting shared payload helpers in the mapper (`ImagesView.toImageUiList()`, `VideoView.toVideoPayload()`) used by both the parent and inner mappers.

### Decision 3 — Single-stub `RecordUnavailable`, with `Reason` retained for telemetry

The lexicon distinguishes four unavailable variants (`viewNotFound`, `viewBlocked`, `viewDetached`, plus the open-union `Unknown` fallback). The official client gives them subtly different copy (e.g. "Blocked" with a shield icon).

For v1 we render a single piece of copy ("Quoted post unavailable") regardless of `Reason`. The `Reason` enum is still carried in `EmbedUi.RecordUnavailable.Reason` so:

- A future per-variant-copy upgrade is non-breaking (UI consumes the existing field).
- Telemetry / debug logs can distinguish the wire-side reason without reading the lexicon dispatch.

Users rarely need to know *why* a quoted post is gone — they need to know it's gone.

### Decision 4 — No tap destination in this PR

The official client opens the quoted post on tap. We don't have a post-detail screen yet. Three options:

- **a)** Wire Custom Tabs to `https://bsky.app/profile/{handle}/post/{rkey}` — works today, but the tap target gets rewired when the in-app post-detail screen lands.
- **b)** No-op clickable surface — confusing UX (ripple, no destination).
- **c)** Defer the tap target entirely — quoted card is non-interactive in v1.

Chosen (c). Adding the `PostCallbacks.onQuotedPostTap` callback now means committing to a destination contract that gets re-implemented when post-detail lands; deferring it is one fewer churn cycle. Filed as a follow-up bd issue.

### Decision 5 — Video coordinator extension at parent feed-item granularity ("B-lite")

Two options for "video inside a quoted post":

- **A.** Render quoted videos as static thumbnails only — never bind the player.
- **B-plus.** Sub-rect visibility — the coordinator computes where in the viewport the quoted video's geometry actually sits (via `Modifier.onGloballyPositioned` plumbing into the coordinator), so a parent post that carries both its own video and a quoted video can bind whichever is more visible.
- **B-lite (chosen).** Visibility is at the parent feed-item level. When a feed item is the topmost item meeting the 0.6 visibility threshold, the coordinator binds the parent's video if any, else the quoted post's video. The coordinator's existing `mostVisibleVideoTarget` math is unchanged; only the per-item resolver descends one level into `EmbedUi.Record.quotedPost.embed`.

A is rejected — divergent behavior from the official client for a feature class (video-inside-quote) that's reasonably common. B-plus is rejected for now because it requires `Modifier.onGloballyPositioned` plumbing that complicates the coordinator's API and isn't required to match the user-visible behavior 99% of the time. B-plus is the natural promotion path if real-world feedback shows the parent-item-granular bind picks the wrong target.

Parent video wins over quoted video when an item carries both — a vanishingly rare case (a post with its own video that quotes another video post), and the parent is what the user is "on" when scrolling reaches that item.

### Decision 6 — Bind identity is the URI of the post whose video plays

The existing `VideoBindingTarget(postId: String, playlistUrl: String)` data class uses `postId` as the bind identity. For the quoted-video case, the parent post's id is wrong (it doesn't change when the user scrolls between two parent items that each have a different quoted video — the player would think the bind hadn't changed and skip rebinding). The quoted post's URI is the right identity:

- Parent video → `postId = post.id` (unchanged).
- Quoted video → `postId = quotedPost.uri`.

Bind identities are naturally distinct between parent + quoted videos for the same item, and across different items' quoted videos, with no `Source` tag or `#quoted` synthetic-suffix gymnastics. The `VideoBindingTarget` data class shape doesn't change.

### Decision 7 — Shared payload helpers in the mapper

The inner-embed dispatch (`RecordViewRecordEmbedsUnion?.toQuotedEmbedUi()`) needs to map the same lexicon types as the parent dispatch (`PostViewEmbedUnion?.toEmbedUi()`) for `images` / `video` / `external`. Naive copy-paste duplicates the construction logic (e.g. video aspect-ratio fallback, external domain precompute) — a real maintenance liability.

Chosen: extract the per-variant payload construction into private helpers:

```kotlin
private fun ImagesView.toImageUiList(): ImmutableList<ImageUi>
private data class VideoPayload(val posterUrl: String?, val playlistUrl: String, val aspectRatio: Float, val durationSeconds: Int?, val altText: String?)
private fun VideoView.toVideoPayload(): VideoPayload?  // null when playlist is blank
```

Parent and inner mappers each call the helpers, then wrap the result in `EmbedUi.X` or `QuotedEmbedUi.X` respectively. Wrapper-type duplication stays (compile-time recursion bound, see Decision 2); logic duplication is eliminated.

The existing `displayDomainOf` (introduced in PR #61 for external embeds) already serves both call sites without further extraction.

## Risks / Trade-offs

- **Risk:** A `RecordView` whose `recordViewRecord.value` decodes successfully but contains an empty `text` and an empty `embeds` list renders a near-empty quoted card.
  → **Mitigation:** This is also what the official client does for the same wire shape. No special handling — the mapper produces a `QuotedPostUi` with empty `text` and `embed = QuotedEmbedUi.Empty`; the composable simply renders the author row and an empty body. Acceptable.

- **Risk:** Quoted-video binding under B-lite may pick the "wrong" video when a feed item carries both a parent video and a quoted video (parent always wins, even if the quoted video is more visible).
  → **Mitigation:** This case is vanishingly rare in real-world data (a video post that also quotes a video post). If real-world feedback shows otherwise, B-plus (sub-rect visibility) is a clean follow-up — only the per-item resolver changes; the bind identity contract holds.

- **Risk:** `EmbedUi` sealed interface gains two new variants (`Record`, `RecordUnavailable`); every consumer's `when (embed)` becomes non-exhaustive at compile time.
  → **Mitigation:** This is the intended behavior — the compile errors surface every dispatch site that needs an arm for the new variants. No runtime risk.

- **Risk:** A quoted post that itself has a video pulls another HLS playlist into the prefetch / play candidate set — could increase steady-state network usage.
  → **Mitigation:** The coordinator still materializes at most one `ExoPlayer` instance (per the existing `feature-feed-video` requirement). A quoted video binds the same single player; no concurrent HLS streams. Coil's image-cache already amortizes thumbnail fetches.

- **Trade-off:** No tap target in v1 means users will tap a quoted card and see no response. We're betting this feels like "post detail isn't shipped yet" rather than "the app is broken." The bd follow-up issue makes the gap explicit.

- **Trade-off:** Compile-time recursion bound via a separate `QuotedEmbedUi` type duplicates wrapper variants; the alternative (single sealed type with a sentinel) duplicates *logic* (every dispatch site must remember the recursion guard). Wrapper duplication is the cheaper price.
