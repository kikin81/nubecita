# Quote posts: quote-compose, repost menu, postgate read — Design

**Date:** 2026-06-06
**Status:** Approved (brainstorm), pending implementation plan
**Related epics:** `nubecita-wtq` (unified composer), `nubecita-8f6` (post interactions), `nubecita-33bw` (post audience picker — threadgate/postgate write)

## Summary

Nubecita can already author new posts and replies, like/repost/reply, and set
who-can-reply/who-can-quote on its **own** posts (threadgate + postgate write).
It cannot yet **author a quote post**, the repost button has no quote affordance,
and it does not honor **other** authors' quote gates.

This change adds quote posts as a first-class capability built on the **existing
unified composer** (no second composer), reworks the repost button into a
tap-menu + long-press affordance, and adds read-side enforcement of others'
postgate (quote) gates.

A quote post embeds another post as `app.bsky.embed.record` (or
`app.bsky.embed.recordWithMedia` when combined with images). On AT Protocol the
post record's `reply` (threading) and `embed` (content) fields are **orthogonal**,
so a single post can be a reply *and* carry a quote at the same time. This design
supports that.

## Goals

- Author a quote post from any post card (repost button → "Quote post").
- Attach a quote while already composing (incl. mid-reply) by **pasting a Bluesky
  post URL**, which is detected, resolved, and rendered as a quote card.
- Support **reply + quote simultaneously** (independent inputs, not exclusive modes).
- Rework the repost button: single tap → dropdown menu; long-press → instant repost.
- Honor **other** authors' quote gates (postgate): hide "Quote post" and reject
  paste-quoting when a target post has quoting disabled.
- Do all of this through **one composer** and **one embed seam**, so future embed
  types (e.g. GIF via klipy) require no second composer and no rework of the
  reply/threadgate/postgate logic.

## Non-goals (deferred follow-ups, filed as separate beads — not children)

- Detached-quote placeholder rendering (`postgate.detachedEmbeddingUris`).
- "Detach quote of my post" author action.
- In-composer post **search/picker** entry point (paste-a-link only for now).
- GIF / klipy embeds (the embed seam is built to accept them; the feature is separate).

## Decisions (from brainstorm)

1. **One composer.** Quote is a mode of the existing unified composer. No duplicate.
2. **Reply and quote coexist.** Modeled as two independent optional inputs, not a
   mutually-exclusive sealed mode. Matches the protocol (`reply` ⟂ `embed`).
3. **In-composer add-quote = paste-a-link detection.** Detect a `bsky.app/...` or
   `at://...` post URL → resolve via `getPost` → attach as a quote card → **strip
   the raw URL from the text only on successful resolution** (cleaner than the
   official client, which leaves it; on failure the URL is kept so nothing is lost).
4. **Read-side postgate = honor the gate only.** Hide "Quote post" and reject
   paste-quoting a gated post. No placeholder, no detach action.
5. **Repost UX.** Single tap → dropdown menu (Repost/Quote, or Undo repost/Quote
   when already reposted); long-press → instant repost/undo (symmetric); icon
   reflects active state.
6. **Generalized embed seam (Approach B).** Introduce a `ComposerEmbedIntent` model
   + a single resolver `intent → PostEmbedUnion` in `:core:posting`. The composer
   declares intent and never references `PostEmbedUnion` variants.

## Architecture

### 1. Composer: two slots + paste-link detection (`:feature:composer`)

**State (`ComposerState`)** — add quote fields in parallel to the existing reply fields:

```kotlin
internal data class ComposerState(
    // …existing…
    val replyToUri: String? = null,                 // existing
    val replyParentLoad: ParentLoadStatus? = null,  // existing
    val quotePostUri: String? = null,               // NEW
    val quotePostLoad: QuoteLoadStatus? = null,      // NEW
    // …existing…
) : UiState
```

```kotlin
internal sealed interface QuoteLoadStatus {
    data object Loading : QuoteLoadStatus
    data class Loaded(val post: QuotedPostUi) : QuoteLoadStatus  // :data:models type
    data class Failed(val cause: ComposerError) : QuoteLoadStatus
}
```

Both `reply*` and `quote*` may be non-null at once → the reply+quote case. Reply
and quote remain **distinct concepts** (reply affects threading + gating; quote is
an embed) — they are not collapsed into one "context" abstraction.

**Layout — three stacked zones (matches the official client):**

- **Reply slot** (top): existing `ComposerReplyParentSection`, shown when
  `replyParentLoad != null`.
- **Text field** (middle): unchanged.
- **Quote slot** (bottom): NEW `ComposerQuoteSection`, shown when
  `quotePostLoad != null`, with Loading / Loaded / Failed states, a **dismiss ✕**
  that clears `quotePostUri` + `quotePostLoad`, rendered via the reusable
  `PostCardQuotedPost` (`:designsystem`).

**Two entry points set `quotePostUri`:**

1. **Repost → Quote post**: via the route — `ComposerRoute(quotePostUri = …)`
   (route currently carries `replyToUri`; add `quotePostUri: String? = null`).
2. **Paste-a-link**: a `snapshotFlow` collector on the text field watches for a
   `bsky.app/profile/{handle-or-did}/post/{rkey}` or
   `at://…/app.bsky.feed.post/…` URL. On match: set `quotePostUri` and enter
   `Loading` (the URL stays in the text), resolve (handle→did if needed, then
   `getPost`), and **only on transition to `Loaded` strip the URL from the text**.
   If resolution fails (network, deleted post, or a postgate block → `Failed`),
   the URL is **left intact** so the user can retry or edit — no data loss from a
   pre-emptive strip.

**Quote fetch.** A lightweight `QuotePostFetcher` (uses `getPost`, not
`getPostThread`) mirrors the existing `ParentFetchSource`. Kept separate from the
reply fetcher to keep concerns clean.

**Guards.**

- One quote max (lexicon): paste-detection ignores a second URL when a quote is
  already attached.
- `canSubmit()` changes two ways:
  - A loaded quote **counts as content**. An empty-text quote post (quoting with
    no added text or images) is valid on AT Protocol because the `record` embed
    *is* the content, so the existing
    `hasContent = text.isNotBlank() || attachments.isNotEmpty()`
    (`ComposerViewModel.kt:516`) must also accept
    `quotePostLoad is QuoteLoadStatus.Loaded`.
  - Readiness gate: if `quotePostUri != null`, the quote must be `Loaded` before
    submit (mirrors the reply parent-loaded gate).
- Postgate (§3) rejects attaching a gated post.

**Audience picker.** Threadgate (reply-gate) only applies to root posts, so it stays
hidden on replies as today. The postgate (quote-gate) half is available on
non-reply posts as today. (Showing only the postgate half on replies is a possible
later refinement; not in this change.)

### 2. Posting: generalized embed seam (`:core:posting`)

Replace the inline `embedFor(blobs)` with an explicit intent + resolver. The
composer fills the intent; the resolver is the **only** place `PostEmbedUnion` is
constructed.

```kotlin
data class ComposerEmbedIntent(
    val attachments: List<ComposerAttachment> = emptyList(), // images today
    val quote: StrongRef? = null,                            // uri + cid of quoted post
    // future: gif: GifAttachment? = null, external: ExternalCard? = null
)
```

Resolver `intent (+ uploaded blobs) → AtField<PostEmbedUnion>`:

| attachments | quote | emitted embed |
|---|---|---|
| empty | null | `AtField.Missing` |
| images | null | `Images` |
| empty | set | `Record` |
| images | set | `RecordWithMedia(record, media = Images)` |

- Blob upload runs first (unchanged parallel `awaitAll`); uploaded blobs + the
  quote ref feed the resolver.
- `createPost(...)` gains a `quote: StrongRef?` parameter.
- Analytics: `CreatePost(isQuote = quote != null)` (field exists, currently
  hardcoded `false`).
- The reply ref stays a **separate** field on the `Post` record (orthogonal to
  embed), so reply + quote + image is just `reply` set **and** `RecordWithMedia`
  emitted.

**Why Approach B:** future GIF/klipy = add `gif` to `ComposerEmbedIntent` + one
resolver branch + the attach UI. No second composer, no other write-path edits,
no touching reply/threadgate/postgate logic.

### 3. Read-side postgate (`:data:models`, `:core:feed-mapping`, enforcement)

- **`:data:models`**: add `canViewerQuote: Boolean = true` to `ViewerStateUi`
  (same shape + fail-open default as `canViewerReply`).
- **`:core:feed-mapping`**: map the wire `viewer.embeddingDisabled` →
  `canViewerQuote` (inverse; absent ⇒ `true`), mirroring the existing
  `replyDisabled → canViewerReply` mapping.
- **Enforcement (two gates):**
  1. Repost menu: the "Quote post" item is **hidden** (not greyed) when
     `post.viewer.canViewerQuote == false`.
  2. Paste-a-link: when a resolved post has quoting disabled, **reject the attach**
     and surface a one-off `ComposerEffect.ShowError("Quotes are turned off for
     this post")`, leaving the URL as plain text.

### 4. Repost interaction (`:designsystem`, feature wiring)

**`:designsystem` — gesture surface only** (the repost icon in `PostCard`):

- **Single tap →** a Material 3 `DropdownMenu` anchored to the repost icon
  (plain `DropdownMenu`, **not** `ExposedDropdownMenuBox`, which is for text fields):
  - Not reposted: **Repost** · **Quote post**
  - Already reposted: **Undo repost** · **Quote post**
  - "Quote post" omitted when `canViewerQuote == false` (§3).
- **Long-press →** instant action, no menu: repost if not reposted, undo if
  reposted (symmetric). Driven by `viewerRepostUri` from the interactions cache.
- Icon reflects active state (filled/tinted when reposted).

**`PostCallbacks` seam:** keep `onRepost` (plain toggle) for the long-press path;
add `onQuote(post)` for the menu's Quote item. The dropdown + long-press logic
lives inside the `:designsystem` repost composable so screens stay dumb.

**Wiring (Feed / Profile / PostDetail):**

- `onRepost` → existing `PostInteractionsRepository.toggleRepost(...)` (unchanged
  optimistic cache from `nubecita-8f6`).
- `onQuote` → emit a per-screen nav effect carrying
  `ComposerRoute(quotePostUri = post.uri)`; the screen collects it and calls
  `LocalMainShellNavState.current.add(route)` — the established tab-internal nav
  pattern (ViewModels never touch nav state).

**Accessibility / discoverability:** long-press is a power-user shortcut and is
purely additive — every action is reachable via the tap menu. The repost icon
gets an `onLongClickLabel`; menu items get content descriptions.

## Testing

Per repo conventions (JUnit Jupiter, Turbine, MockK; screenshot tests via the AGP
plugin with committed baselines):

- **`:core:posting`**: resolver table test over all four rows (future-proofed);
  `DefaultPostingRepository` Record / RecordWithMedia write assertions; existing
  images-only + threadgate/postgate tests stay green.
- **`:core:feed-mapping`**: `embeddingDisabled → canViewerQuote` mapping test
  (absent / true / false), alongside the existing reply-gate test.
- **`:feature:composer`**: VM tests for quote load lifecycle (Loading → Loaded →
  submit; Failed → retry; dismiss), paste-detection reducer (detect/resolve,
  **strip URL only on `Loaded`**, **keep URL on `Failed`**, ignore second URL,
  reject gated post), `canSubmit` gate (incl. **empty-text quote-only post is
  submittable**); screenshot tests for the composer with reply + quote + image
  stacked.
- **`:designsystem`**: screenshot tests for the repost menu (reposted vs not,
  with/without Quote item) and the active icon state.
- **feature VMs (feed/profile/postdetail)**: `onQuote` emits the composer route;
  `onRepost` toggles the cache.

No new instrumented tests required; add the `run-instrumented` PR label only if a
child's nav flow warrants it.

## Decomposition (epic + children)

New epic: **Quote posts: quote-compose, repost menu, postgate read** (sibling of
wtq / 8f6 / 33bw).

| # | Child | Module(s) | Depends on |
|---|---|---|---|
| .1 | Generalize embed seam → `ComposerEmbedIntent` + resolver; write `Record`/`RecordWithMedia`; `isQuote` analytics | `:core:posting` | — |
| .2 | `canViewerQuote` model + `embeddingDisabled` mapping | `:data:models`, `:core:feed-mapping` | — |
| .3 | Composer quote slot: route `quotePostUri`, `QuoteLoadStatus` lifecycle, `getPost` fetcher, dismiss ✕, `canSubmit` gate | `:feature:composer` | .1 |
| .4 | Paste-a-link: detect → resolve → attach → strip URL; reject gated post | `:feature:composer` | .3, .2 |
| .5 | Repost affordance: `DropdownMenu` + long-press + `onQuote` callback; active icon state | `:designsystem` | .2 |
| .6 | Wire repost menu + quote entry across Feed/Profile/PostDetail | `:feature:feed/profile/postdetail` | .5, .3 |
| .7 | Make reply/quote context cards look like a full post (avatar, display name + handle, richer body, media thumbnail) — upgrades the minimal 2-line reply preview *and* the quote slot | `:designsystem`, `:feature:composer` | .3 |

`.1` and `.2` are the unblocked roots (parallelizable). `.7` is a polish task carved
out so the functional quote flow (.3–.6) can ship on the existing minimal card and
the richer look lands independently.

## Open questions

None blocking. Possible later refinements (not in this change): showing the
postgate half of the audience picker on replies; mimicking the official client by
keeping the pasted URL in text.
