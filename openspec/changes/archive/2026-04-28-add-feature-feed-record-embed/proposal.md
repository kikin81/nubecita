## Why

Quoted posts (`app.bsky.embed.record`) are a first-class Bluesky timeline surface — used heavily for commentary, news reposts, and threaded discussion. Today the `FeedViewPostMapper` routes `RecordView` to `EmbedUi.Unsupported`, so every quoted post in the user's feed renders as a generic "Unsupported embed" chip. This was a deliberate v1 deferral per `2026-04-25-postcard-embed-scope-v1.md` — v2 of the embed scope roadmap, gated on (a) PostCard v1 + v1.1 (text + images + external) shipping, and (b) feed scrolling being verified at 120 Hz on a known-good device. Both gates have now been cleared (v1.1 just shipped via PR #61; macrobench setup is in flight under `nubecita-ppj`).

This change closes the user-visible gap and brings nubecita's feed parity in line with the official Bluesky Android client — the quoted post renders as a near-full-density nested card with embed dispatch (including inline video playback), bounded to one level of recursion at compile time.

## What Changes

- **MODIFIED capability** `data-models` — adds two top-level variants to the sealed `EmbedUi` interface: `Record(quotedPost: QuotedPostUi)` for the resolved-quote case, and `RecordUnavailable(reason: Reason)` covering the lexicon's `viewNotFound` / `viewBlocked` / `viewDetached` + `Unknown` open-union variants. Single-stub copy on the unavailable case (YAGNI; promote to per-variant copy only if user feedback warrants). Introduces `QuotedPostUi` (uri, cid, author, createdAt, text, facets, embed) and `QuotedEmbedUi` (sealed; `Empty` / `Images` / `Video` / `External` / `QuotedThreadChip` / `Unsupported`). The recursion bound — record-inside-record renders a "View thread" chip — is enforced at the type system: `QuotedEmbedUi` deliberately excludes a `Record` variant.
- **MODIFIED capability** `feature-feed` — `FeedViewPostMapper.toEmbedUi` replaces the `RecordView -> EmbedUi.Unsupported` arm with a dispatch over `RecordViewRecordUnion`'s 4 + 1 variants (resolved record → `EmbedUi.Record`; not-found / blocked / detached / Unknown → `EmbedUi.RecordUnavailable` with the matching `Reason`). Quoted-post construction shares the existing `runCatching` decode-or-null pattern: a malformed quoted-record `JsonObject` produces `RecordUnavailable.Unknown` rather than dropping the parent post. Inner-embed mapping (`RecordViewRecordEmbedsUnion?.toQuotedEmbedUi()`) reuses extracted payload helpers from the existing `Images` / `Video` / `External` mappers — no logic duplication, only wrapper-type duplication. `RecordWithMediaView` continues to fall through to `EmbedUi.Unsupported` (out of scope; tracked under `nubecita-umn`).
- **MODIFIED capability** `design-system` — adds `PostCardQuotedPost` (renders a `QuotedPostUi` at near-parent density: 32 dp avatar, single-row author treatment, full body text, embed slot, no action row, `surfaceContainerLow` rounded surface) and `PostCardRecordUnavailable` (small chip "Quoted post unavailable"). `PostCard` gains one new optional parameter `quotedVideoEmbedSlot: (@Composable (QuotedEmbedUi.Video) -> Unit)? = null` — same inversion-of-control as the existing `videoEmbedSlot` so `:designsystem` does not depend on `:feature:feed:impl` / Media3. **No clickable** on the quoted card in v1; `PostCallbacks.onQuotedPostTap` is intentionally NOT introduced here — it lands together with the post-detail destination in a separate bd issue (avoids wire-once / rewire-when-PostDetail-lands churn).
- **MODIFIED capability** `feature-feed-video` — extends the most-visible-video coordinator so quoted videos count as bind candidates. `mostVisibleVideoTarget` keeps its parent-feed-item-level visibility math (no `Modifier.onGloballyPositioned` plumbing); it descends one level into the quoted post's embed when the parent has no own video. Bind identity becomes "the URI of the post whose video plays" — parent video uses `post.id`; quoted video uses `quotedPost.uri` — naturally distinguishing the two while reusing the existing `VideoBindingTarget(postId, playlistUrl)` shape. Parent video wins over quoted video when an item carries both (vanishingly rare). Sub-rect visibility (true "where on screen is the quoted video") is explicitly out of scope.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `data-models`: adds `EmbedUi.Record` + `EmbedUi.RecordUnavailable` variants and the `QuotedPostUi` + `QuotedEmbedUi` types they carry.
- `feature-feed`: `FeedViewPostMapper` dispatches `RecordView` to the new `EmbedUi.Record` / `EmbedUi.RecordUnavailable` variants instead of `Unsupported`; inner-embed mapping for quoted posts is bounded at one level by the type system.
- `design-system`: `PostCard` renders the new `EmbedUi.Record` and `EmbedUi.RecordUnavailable` arms via `PostCardQuotedPost` and `PostCardRecordUnavailable`; gains a `quotedVideoEmbedSlot` slot lambda paralleling the existing video slot.
- `feature-feed-video`: `mostVisibleVideoTarget` descends into quoted-post embeds when picking the bind target; bind identity uses the URI of the video-owning post.

## Impact

- **Non-goals (logged here so they don't slip into scope):**
  - Sub-rect visibility for quoted videos — coordinator stays at parent feed-item granularity.
  - Tap-to-open destination on the quoted card — deferred to a separate bd issue paired with the post-detail screen epic.
  - Per-`Reason` copy on `PostCardRecordUnavailable` — single piece of copy in v1.
  - Rendering the quoted post's action row, like/repost counts, or interaction affordances.
  - Two-level recursion (quote-of-quote-of-quote) — compile-time bounded to one level.
  - `RecordWithMediaView` — separate bd issue (`nubecita-umn`).

- **Code:**
  - Modified: `data/models/.../EmbedUi.kt` — new variants + nested types.
  - Modified: `feature/feed/impl/.../data/FeedViewPostMapper.kt` — `RecordView` dispatch arms; extracted shared payload helpers (`toImageUiList`, `toVideoPayload`).
  - Modified: `feature/feed/impl/.../data/FeedViewPostMapperTest.kt` — new test cases for resolved + unavailable + nested recursion + malformed-quoted-record-doesn't-drop-parent.
  - New: `designsystem/.../component/PostCardQuotedPost.kt`.
  - New: `designsystem/.../component/PostCardRecordUnavailable.kt`.
  - Modified: `designsystem/.../component/PostCard.kt` — `EmbedSlot` arms for `Record` / `RecordUnavailable`; `quotedVideoEmbedSlot` parameter.
  - New: `designsystem/.../screenshotTest/.../PostCardQuotedPostScreenshotTest.kt` (10 baselines: 5 inner-embed shapes × light/dark) + `PostCardRecordUnavailableScreenshotTest.kt` (2 baselines).
  - Modified: `feature/feed/impl/.../video/VideoBindingTarget.kt` — `mostVisibleVideoTarget` descends into quoted embed; `videoBindingFor(post)` helper.
  - Modified: `feature/feed/impl/.../video/MostVisibleVideoTargetTest.kt` — quoted-video-binds, parent-precedence, topmost-rule-with-mixed-sources, `videoBindingFor` standalone tests.
  - Modified: `feature/feed/impl/.../FeedScreen.kt` — builds `quotedVideoSlot` per item (closure captures `quotedPost.uri` as bind key) and threads it through `PostCard`.
  - New (small): a `PostCardVideoEmbed` overload accepting `(quotedVideo: QuotedEmbedUi.Video, postId: String, coordinator)` that unpacks to the same private impl as the parent overload.

- **Dependencies:** none added.

- **Risk:** the type-system change to `EmbedUi` is a closed-set extension on a public sealed interface — every consumer's `when` becomes non-exhaustive at compile time and surfaces the work needed. No runtime risk.

- **Roadmap context:** unblocks `nubecita-umn` (`recordWithMedia`) which depends on having a working quoted-post renderer.
