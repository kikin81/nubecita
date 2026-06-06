## Why

Nubecita can read posts and author new posts/replies, like/repost/reply, and set who-can-reply/who-can-quote on its **own** posts, but it cannot **author a quote post**, the repost button has no quote affordance, and it does not honor **other** authors' quote gates. Quote posts are a core expression primitive on Bluesky; without them the client is read-only for one of the protocol's most-used interactions.

## What Changes

- Author a **quote post** built on the **existing unified composer** (no second composer): a new post embeds another post as `app.bsky.embed.record`, or `app.bsky.embed.recordWithMedia` when combined with images.
- **Reply + quote coexist** — `reply` (threading) and `embed` (content) are orthogonal protocol fields, modeled as two independent optional composer inputs.
- Two entry points to attach a quote: the reworked **repost menu** (single tap → Repost/Quote dropdown; long-press → instant repost/undo) and **paste-a-link** detection inside the composer (paste a `bsky.app/...` or `at://...` post URL → resolve → attach as a quote card; URL stripped only on successful resolution).
- **Generalized embed seam** in `:core:posting` (`ComposerEmbedIntent` + a single resolver → `PostEmbedUnion`) so future embed types (e.g. GIF via klipy) need no second composer and no write-path rework.
- **Read-side postgate enforcement**: derive `canViewerQuote` from the wire `viewer.embeddingDisabled`; hide the "Quote post" menu item and reject paste-quoting when a target post has quoting disabled.

## Capabilities

### New Capabilities
- `quote-posts`: authoring quote posts on the unified composer (record / recordWithMedia embeds, reply+quote coexistence), the repost tap-menu + long-press affordance, paste-a-link quote detection, and read-side postgate enforcement (honoring others' quote gates).

### Modified Capabilities
<!-- None: composer/posting/design-system behavior is extended additively; no existing spec-level requirement is changed or removed. -->

## Non-goals

- Detached-quote placeholder rendering (`postgate.detachedEmbeddingUris`).
- "Detach quote of my post" author action.
- In-composer post **search/picker** entry point (paste-a-link only for now).
- GIF / klipy embeds (the embed seam is built to accept them; the feature is separate).

## Impact

- **`:core:posting`** — `ComposerEmbedIntent` model + resolver; `createPost` gains a `quote: StrongRef?`; `Record`/`RecordWithMedia` writes; `isQuote` analytics.
- **`:feature:composer`** — `ComposerRoute.quotePostUri`; `QuoteLoadStatus` + `ComposerQuoteSection`; `QuotePostFetcher` (getPost); paste-link detection; `canSubmit` accepts a loaded quote as content.
- **`:data:models` / feed-mapping** — `canViewerQuote` on `ViewerStateUi`, mapped from `viewer.embeddingDisabled`.
- **`:designsystem`** — repost `DropdownMenu` + long-press gesture; `PostCallbacks.onQuote`; active repost icon state.
- **`:feature:feed` / `:feature:profile` / `:feature:postdetail`** — wire the repost menu and the quote entry (`ComposerRoute(quotePostUri = …)` via `LocalMainShellNavState`).
- No deviation from the MVI / Compose / Hilt / Coil baseline. No new third-party dependency (klipy is out of scope). The reply ref stays a separate `Post` field, orthogonal to the embed.
