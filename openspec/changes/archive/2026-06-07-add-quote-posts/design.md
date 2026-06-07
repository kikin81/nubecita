## Context

The unified composer (`nubecita-wtq`) already supports new posts and replies, text + up to 4 images, mentions typeahead, and the audience picker. Post interactions (`nubecita-8f6`) provide like/repost/reply/share with a cache+broadcast `PostInteractionsRepository`. The audience picker (`nubecita-33bw`) already **writes** threadgate/postgate on the user's own posts. Missing: authoring a quote, a quote affordance on the repost button, and honoring **other** authors' quote gates.

A spike confirmed the composer is reusable as-is: it is already on the Navigation 3 `adaptiveDialog()` pattern, and every embed converges in one function (`DefaultPostingRepository.embedFor`). The SDK's `PostEmbedUnion` already has `Record` and `RecordWithMedia` variants. Full brainstorm: `docs/superpowers/specs/2026-06-06-quote-posts-design.md`.

## Goals / Non-Goals

**Goals:**
- Quote-compose as a mode of the **existing** composer (no duplicate).
- Reply + quote coexist (two independent optional inputs).
- Two entry points: repost menu and paste-a-link detection.
- Generalize the embed seam so future embed types (GIF/klipy) touch one place.
- Honor others' postgate gates (read-side enforcement only).

**Non-Goals:**
- Detached-quote placeholder rendering; "detach quote of my post" action.
- In-composer post search/picker entry; GIF/klipy embeds.

## Decisions

1. **One composer, parallel inputs.** Add `quotePostUri: String?` + `quotePostLoad: QuoteLoadStatus?` to `ComposerState`, mirroring the reply fields. Reply and quote stay distinct concepts (reply affects threading/gating; quote is an embed) — not collapsed into one abstraction. Both may be non-null at once.
2. **Generalized embed seam (Approach B).** Introduce `ComposerEmbedIntent(attachments, quote, …future)` in `:core:posting` and one resolver `intent (+ uploaded blobs) → AtField<PostEmbedUnion>` emitting `Missing` / `Images` / `Record` / `RecordWithMedia`. The composer declares intent and never references union variants. `createPost` gains `quote: StrongRef?`; analytics `CreatePost(isQuote = quote != null)`.
3. **Paste-a-link.** A `snapshotFlow` collector detects `bsky.app/profile/{handle-or-did}/post/{rkey}` or `at://…/app.bsky.feed.post/…`, enters `Loading` (URL stays in text), resolves via a lightweight `QuotePostFetcher` (getPost, not getPostThread), and strips the URL **only** on `Loaded`. On `Failed`, the URL is kept. One quote max.
4. **canSubmit.** A loaded quote counts as content, so `hasContent = text.isNotBlank() || attachments.isNotEmpty() || quotePostLoad is Loaded` (`ComposerViewModel.kt:516`). Readiness gate: if `quotePostUri != null`, the quote must be `Loaded` before submit.
5. **Read-side postgate.** Add `canViewerQuote: Boolean = true` to `ViewerStateUi`, mapped from `viewer.embeddingDisabled` (inverse; absent ⇒ true), mirroring `replyDisabled → canViewerReply`. The repost menu hides "Quote post" when `canViewerQuote == false`; paste-quoting a gated post is rejected with a one-off `ComposerEffect.ShowError`.
6. **Repost affordance.** In `:designsystem`, the repost icon gets a tap → `DropdownMenu` (Repost/Quote, or Undo repost/Quote when reposted; Quote omitted when `canViewerQuote == false`) and a long-press → instant toggle. `PostCallbacks` gains `onQuote(post)`; `onRepost` stays for the toggle. Screens wire `onRepost → PostInteractionsRepository.toggleRepost`, `onQuote →` a nav effect carrying `ComposerRoute(quotePostUri = …)` collected onto `LocalMainShellNavState` (ViewModels never touch nav state).

## Risks / Trade-offs

- **Long-press discoverability.** Long-press is a power-user shortcut; mitigated by every action also being reachable via the tap menu, plus an `onLongClickLabel`.
- **Plain `DropdownMenu`, not `ExposedDropdownMenuBox`.** The latter is for text fields; the icon-anchored `DropdownMenu` is the correct primitive.
- **Embed-seam refactor touches the existing images-only write path.** Mitigated by a table test over all four resolver rows and keeping the existing images-only + threadgate/postgate tests green.
- **Eventual consistency.** A freshly created quote target may lag in the appview; the composer resolves the explicit `uri`/`cid`, so the embed ref is correct regardless of feed indexing.
