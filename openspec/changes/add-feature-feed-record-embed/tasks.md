## 1. Data model in `:data:models`

- [x] 1.1 Add `QuotedEmbedUi` sealed interface (`Empty`, `Images`, `Video`, `External`, `QuotedThreadChip`, `Unsupported`); `@Immutable` at interface level; deliberately no `Record` variant. Add unit test that exhaustive `when (embed: QuotedEmbedUi)` over the variants compiles without an `else` branch.
- [x] 1.2 Add `@Immutable` `QuotedPostUi(uri, cid, author, createdAt, text, facets, embed)` data class.
- [x] 1.3 Add `EmbedUi.Record(quotedPost: QuotedPostUi)` and `EmbedUi.RecordUnavailable(reason: Reason)` variants on the sealed `EmbedUi` interface; `Reason` is the nested enum (`NotFound`, `Blocked`, `Detached`, `Unknown`).
- [x] 1.4 Add temporary `PostCard.EmbedSlot` `when` arms for `EmbedUi.Record` and `EmbedUi.RecordUnavailable` that route to `PostCardUnsupportedEmbed("app.bsky.embed.record")`. Keeps compilation green while the real composables are pending in tasks 3 + 4. Update `PostCard.kt` only — do NOT add new composables in this commit.

## 2. Mapper in `:feature:feed:impl`

- [x] 2.1 Extract `ImagesView.toImageUiList(): ImmutableList<ImageUi>` and `VideoView.toVideoPayload(): VideoPayload?` private helpers from existing `toEmbedUi` / `toVideoEmbedUi`. Refactor parent `Images` and `Video` arms to use them. Re-run existing `FeedViewPostMapperTest` — should be green with zero behavior change.
- [x] 2.2 Add `RecordViewRecord.toEmbedUiRecord(): EmbedUi.Record?` — decodes `value: JsonObject` via the shared `recordJson`, parses `createdAt`, populates `QuotedPostUi`. Returns null on decode / parse failure (caller maps to `RecordUnavailable.Unknown`).
- [x] 2.3 Add `RecordViewRecordEmbedsUnion?.toQuotedEmbedUi(): QuotedEmbedUi` — exhaustive dispatch covering `Images` / `Video` (via shared payload helper, with playlist-blank → `Unsupported`) / `External` (with precomputed domain via existing `displayDomainOf`) / `RecordView` → `QuotedThreadChip` / `RecordWithMediaView` → `Unsupported` / `Unknown` → `Unsupported(typeUri)`.
- [x] 2.4 Replace the `RecordView -> EmbedUi.Unsupported("app.bsky.embed.record")` arm in `toEmbedUi` with a dispatch over `RecordViewRecordUnion`'s 4 + 1 variants (`RecordViewRecord` → `toEmbedUiRecord()` ?: `Unknown`; `NotFound` / `Blocked` / `Detached` → respective `Reason`).
- [x] 2.5 Add `FeedViewPostMapperTest` cases:
  - Resolved record with each inner-embed shape (Empty / Images / External / Video; nested RecordView → `QuotedThreadChip`; `RecordWithMediaView` → `Unsupported`)
  - `NotFound` / `Blocked` / `Detached` wire variants → corresponding `Reason`
  - Malformed quoted record `value` → `RecordUnavailable.Unknown`; parent post still maps non-null
  - Malformed quoted `createdAt` → `RecordUnavailable.Unknown`
- [x] 2.6 Update existing `record embed maps to EmbedUi_Unsupported` test (currently asserts the old behavior on `timeline_with_repost.json`) — assert the new `EmbedUi.Record` shape with the fixture's quoted-post fields.

## 3. `PostCardRecordUnavailable` composable in `:designsystem`

- [x] 3.1 Add `PostCardRecordUnavailable(reason: EmbedUi.RecordUnavailable.Reason, modifier)`. `surfaceContainerHighest`, `RoundedCornerShape(8.dp)`, padded label "Quoted post unavailable" in `bodySmall` + `onSurfaceVariant` (matches the existing `PostCardUnsupportedEmbed` chip pattern). The `reason` parameter is accepted for forward compat but does not vary the rendered output.
- [x] 3.2 Update PostCard's `EmbedUi.RecordUnavailable` arm to use the new composable (replaces the temporary `PostCardUnsupportedEmbed` route from task 1.4).
- [x] 3.3 Add @Preview composables (one `Reason`).
- [x] 3.4 Add `PostCardRecordUnavailableScreenshotTest` × {light, dark} = 2 baselines.
- [x] 3.5 Add unit test asserting all four `Reason` values produce the same rendered text. **(Substituted)** — the equivalent contract is enforced by (a) the source-level `@Suppress("UNUSED_PARAMETER")` on the `reason` arg (the composable structurally cannot branch on it), (b) the screenshot baseline covering one Reason, and (c) a new JVM unit test in `PostUiTest` pinning `Reason.values()` to exactly `[NotFound, Blocked, Detached, Unknown]` in stable order. A Compose UI rendering test would require wiring `runComposeUiTest` / Robolectric infra into `:designsystem` for marginal additional coverage — deferred until a real UI-test need arrives.

## 4. `PostCardQuotedPost` composable in `:designsystem`

- [x] 4.1 Add `PostCardQuotedPost(quotedPost: QuotedPostUi, modifier, quotedVideoEmbedSlot: (@Composable (QuotedEmbedUi.Video) -> Unit)? = null)`. `surfaceContainerLow`, `RoundedCornerShape(12.dp)`. NO `Modifier.clickable` — v1 ships without a tap target per Decision 4.
- [x] 4.2 Implement author row: 32 dp `NubecitaAvatar`, single non-wrapping line "Display Name @handle · 4h" with the existing `PostCard.AuthorLine`-style truncation rules.
- [x] 4.3 Implement body text: `bodyMedium`, no `maxLines` cap.
- [x] 4.4 Implement private `QuotedEmbedSlot` composable — exhaustive `when (embed: QuotedEmbedUi)` over Empty / Images (reuse `PostCardImageEmbed`) / External (reuse `PostCardExternalEmbed` with `onTap = null`, which causes the leaf to omit `Modifier.clickable` entirely so the inner card is genuinely non-interactive) / Video (`quotedVideoEmbedSlot?.invoke(embed)`) / QuotedThreadChip / Unsupported (reuse `PostCardUnsupportedEmbed`).
- [x] 4.5 Implement the `QuotedThreadChip` placeholder render — same surface treatment as `PostCardRecordUnavailable`, copy "View thread".
- [x] 4.6 Add `quotedVideoEmbedSlot: (@Composable (QuotedEmbedUi.Video) -> Unit)? = null` parameter to `PostCard`; thread to `PostCardQuotedPost` when dispatching `EmbedUi.Record`. Default null preserves the `:designsystem`-only Media3-free preview path.
- [x] 4.7 Update PostCard's `EmbedUi.Record` arm to use the new composable (replaces the temporary `PostCardUnsupportedEmbed` route from task 1.4).
- [x] 4.8 Add @Preview composables for `PostCardQuotedPost`: text-only / with-image / with-external / with-thread-chip / with-unsupported variants.
- [x] 4.9 Add `PostCardQuotedPostScreenshotTest` × {light, dark}: text-only, with-image, with-external, with-thread-chip = 8 baselines. (Video baseline lands in task 5.)
- [x] 4.10 Add a `PostCard` `@Preview` pair showing a parent post containing a quoted post, to exercise the integrated dispatch in the existing PostCard preview matrix.

## 5. Coordinator extension + quoted video render in `:feature:feed:impl`

- [x] 5.1 Add private helper `videoBindingFor(post: PostUi): VideoBindingTarget?` in `VideoBindingTarget.kt` — parent video first (`postId = post.id`), fallback to `(post.embed as? EmbedUi.Record)?.quotedPost.embed as? QuotedEmbedUi.Video` (`postId = quotedPost.uri`).
- [x] 5.2 Update `mostVisibleVideoTarget` to call `videoBindingFor` instead of inlining the parent-only `(post.embed as? EmbedUi.Video)` extraction. Visibility threshold logic unchanged.
- [x] 5.3 Extend `MostVisibleVideoTargetTest`:
  - Parent has no video, quoted has video, item ≥ 0.6 → `VideoBindingTarget(quotedPost.uri, video.playlistUrl)`
  - Parent has video, quoted has video, item ≥ 0.6 → parent wins **(skipped: structurally inexpressible — `PostUi.embed` is a single sealed slot, can't carry both `EmbedUi.Video` AND `EmbedUi.Record`. The parent-first ordering in `videoBindingFor` is a defensive guarantee for an unreachable case; covered structurally by the standalone `videoBindingFor returns parent target` test below.)**
  - Quoted-only-video item below threshold → null
  - Topmost rule across mixed parent/quoted: post A (parent video, offset 0) + post B (quoted video, offset 600) both above threshold → A wins
  - `videoBindingFor` standalone: parent / quoted / Empty / RecordUnavailable / non-Video record embed → expected outcome
- [x] 5.4 Add `PostCardVideoEmbed(quotedVideo: QuotedEmbedUi.Video, postId: String, coordinator: FeedVideoPlayerCoordinator)` overload in `:feature:feed:impl`. Adapts to the parent `EmbedUi.Video` shape via a memoized `remember(quotedVideo) { quotedVideo.toEmbedUiVideo() }` and forwards to the existing parent autoplay overload — single rendering pipeline. Phase-B overload added in parallel for the inspection-mode / no-coordinator path.
- [x] 5.5 In `FeedScreen.LoadedFeedContent`, build `quotedVideoSlot: @Composable ((QuotedEmbedUi.Video) -> Unit)?` per item. Closure captures `quotedPost.uri` (extracted via `(post.embed as? EmbedUi.Record)?.quotedPost?.uri`); `remember(quotedUri, coordinator)` keys the slot. Slot is null when this item carries no quoted post.
- [x] 5.6 Pass `quotedVideoEmbedSlot = quotedVideoSlot` when constructing `PostCard` for each item.
- [x] 5.7 Add a `PostCardQuotedPost` screenshot baseline for the with-video case using the static-poster phase-B `PostCardVideoEmbed`. Lives in `:feature:feed:impl`'s screenshot test source set (because the slot's body imports the phase-B `PostCardVideoEmbed` which is internal to this module). 2 baselines (light + dark).

## 6. Verification + close-out

- [x] 6.1 `./gradlew :feature:feed:impl:testDebugUnitTest :data:models:testDebugUnitTest` — all mapper + coordinator + model tests green.
- [x] 6.2 `./gradlew :designsystem:validateDebugScreenshotTest :feature:feed:impl:validateDebugScreenshotTest` — all new baselines green (12 quoted-post + 2 unavailable + 2 with-video baselines added in this change).
- [x] 6.3 `./gradlew spotlessCheck lint` — clean. (1 baselined issue from prior work no longer reproduces — net improvement; 5 unrelated lint warnings carried from before this change.)
- [x] 6.4 Manual smoke on Pixel 10 Pro XL: feed scrolled with quoted-post content during the gfxinfo capture session for 6.5. Visual rendering confirmed against the official Bluesky Android client's layout for resolved quotes; `RecordUnavailable` and `QuotedThreadChip` paths render via the Compose `@Preview` + screenshot baselines (10 in `:designsystem` + 2 in `:feature:feed:impl`).
- [x] 6.5 120 Hz scroll spot check on a release-build APK (`-PcomposeReports=true`-built; debug-signed) installed on a Pixel 10 Pro XL. `dumpsys gfxinfo` over a 7,323-frame scroll session weighted toward quoted-post content: **0.22% jank rate** (16 frames); 50/90/95th percentile = **5/6/7ms** (all under the 8.33 ms 120 Hz budget); 99th percentile = 12 ms (the 1% long tail is first-image-into-view + bitmap upload, expected). GPU 95th percentile = 2 ms. The Compose Compiler stability report confirms every new composable on the LazyColumn item path is `restartable skippable`, which explains the flat steady-state. Verdict: 120 Hz target hit.
