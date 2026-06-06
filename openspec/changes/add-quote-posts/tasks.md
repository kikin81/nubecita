<!-- Each task group maps to a beads child of epic nubecita-8g28. Bead ids in [brackets]. -->

## 1. Generalized embed seam + quote write [nubecita-8g28.1]

- [ ] 1.1 Add `ComposerEmbedIntent(attachments, quote: StrongRef?)` model to `:core:posting`
- [ ] 1.2 Replace `DefaultPostingRepository.embedFor(blobs)` with a single resolver `intent (+ uploaded blobs) → AtField<PostEmbedUnion>` emitting `Missing` / `Images` / `Record` / `RecordWithMedia`
- [ ] 1.3 `createPost(...)` gains `quote: StrongRef?`; set `CreatePost(isQuote = quote != null)`
- [ ] 1.4 Resolver table test over all four rows; keep images-only + threadgate/postgate tests green

## 2. Read-side postgate mapping [nubecita-8g28.2]

- [ ] 2.1 Add `canViewerQuote: Boolean = true` to `ViewerStateUi` (`:data:models`)
- [ ] 2.2 Map wire `viewer.embeddingDisabled → canViewerQuote` (inverse; absent ⇒ true) in `FeedMapping.kt`
- [ ] 2.3 Mapping unit test (absent / true / false)

## 3. Composer quote slot [nubecita-8g28.3] (depends on §1)

- [ ] 3.1 Add `quotePostUri: String? = null` to `ComposerRoute`
- [ ] 3.2 Add `quotePostUri` + `quotePostLoad: QuoteLoadStatus` (Loading / Loaded(QuotedPostUi) / Failed) to `ComposerState`
- [ ] 3.3 `QuotePostFetcher` (getPost) mirroring `ParentFetchSource`; launch fetch from route at VM init
- [ ] 3.4 `ComposerQuoteSection` rendered via `PostCardQuotedPost`, with dismiss clearing the quote inputs
- [ ] 3.5 `canSubmit`: loaded quote counts as content; quote must be `Loaded` if `quotePostUri != null`
- [ ] 3.6 VM lifecycle tests + composer screenshot test (reply + quote + image stacked)

## 4. Paste-a-link quote detection [nubecita-8g28.4] (depends on §3, §2)

- [ ] 4.1 `snapshotFlow` collector detecting `bsky.app/profile/{handle-or-did}/post/{rkey}` and `at://…/app.bsky.feed.post/…`
- [ ] 4.2 Resolve (handle→did if needed, getPost); enter `Loading` with URL retained
- [ ] 4.3 Strip URL only on `Loaded`; keep URL on `Failed`; ignore a second URL when a quote is attached
- [ ] 4.4 Reject paste-quoting a gated post (`canViewerQuote == false`) → `ComposerEffect.ShowError`
- [ ] 4.5 Reducer tests (resolve/strip, keep-on-fail, one-quote-max, gated-reject)

## 5. Repost affordance [nubecita-8g28.5] (depends on §2)

- [ ] 5.1 Repost icon: single tap → `DropdownMenu` (Repost/Quote, or Undo repost/Quote when reposted; Quote omitted when `canViewerQuote == false`)
- [ ] 5.2 Long-press → instant repost/undo toggle; active/filled icon from `viewerRepostUri`
- [ ] 5.3 `PostCallbacks.onQuote(post)` added; `onRepost` retained; `onLongClickLabel` + content descriptions
- [ ] 5.4 Screenshot tests for menu states and active icon

## 6. Wire across Feed / Profile / PostDetail [nubecita-8g28.6] (depends on §5, §3)

- [ ] 6.1 `onRepost → PostInteractionsRepository.toggleRepost`
- [ ] 6.2 `onQuote →` per-screen nav effect carrying `ComposerRoute(quotePostUri = post.uri)`, collected onto `LocalMainShellNavState`
- [ ] 6.3 Per-VM tests (onQuote emits route; onRepost toggles cache)

## 7. Full-post-look reply/quote context cards [nubecita-8g28.7] (depends on §3)

- [ ] 7.1 Upgrade the reply context preview and the composer quote slot to a fuller presentation (avatar, display name + handle, richer body, media thumbnail)
- [ ] 7.2 Screenshot baselines for the new card presentation
