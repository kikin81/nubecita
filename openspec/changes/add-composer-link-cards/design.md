# Design: Composer paste-a-link external embeds

## Context

Render path for `app.bsky.embed.external` is complete; only creation is missing. The composer already auto-detects Bluesky **quote** links in its `snapshotFlow` text collector (`ComposerViewModel.maybeDetectQuoteLink`, memoized via `attemptedQuoteLinks`, lifecycle modeled by the sealed `QuoteLoadStatus`). This change mirrors that machinery for arbitrary URLs and shares the common orchestration.

## Decisions

### D1 — Preview source: CardyB
Fetch previews from `GET https://cardyb.bsky.app/v1/extract?url=<paste>` → `{error, likely_type, url, title, description, image}` (empty `error` = success; `image` is CardyB's own proxied URL). This is the service the official Bluesky app uses, so cards render consistently across clients. Chosen over client-side OpenGraph parsing because it is dramatically less code, battery/data-friendly (one small GET vs. fetching+parsing arbitrary HTML), and avoids leaking the user's IP to arbitrary destination sites. The privacy tradeoff (Bluesky sees the URL pre-post) is negligible — the user is about to post that URL publicly to Bluesky anyway.

### D2 — Auto-detect + dismissable
The first non–quote-link URL the user types/pastes auto-fetches and shows a card; an `X` dismisses it and memoizes the URL so it does not re-pop. Mirrors the official app and reuses the existing memoized-detector pattern. Detection runs in the existing `snapshotFlow` collector — no new inbound event stream.

### D3 — Coexistence: card XOR images; card + quote allowed
A link card is mutually exclusive with images/gallery (Bluesky forbids both as media; images win). A card **may** coexist with a quoted post, emitted as `recordWithMedia(record=quote, media=external)` — wire-legal because the SDK's `External` is a valid `RecordWithMediaMediaUnion`. Enforced at compose time (adding images clears the card; the scanner suppresses a card while images exist) and defensively in `resolveEmbed`.

### D4 — Shared `TextLinkScanner`
The genuinely common orchestration — "on each text change, prune the attempted set to URLs still present, skip if the slot is already handled, run a detect function excluding attempted, memoize and fire a callback" — is extracted into a small generic helper in `:feature:composer:impl/internal`. The **detectors** (`QuoteLinkDetector`, new `ExternalLinkDetector`), the **fetches**, and the **sealed status types** stay separate (per the repo's per-concern MVI convention). The existing quote-link detection is refactored onto the scanner (behavior-preserving; guarded by its existing tests).

```kotlin
internal class TextLinkScanner<T>(
    private val detect: (text: String, exclude: Set<String>) -> Match<T>?,
    private val alreadyHandled: () -> Boolean,
    private val onDetected: (T) -> Unit,
) { fun scan(text: String) { /* prune ∩ text; if handled return; detect; remember; callback */ } }
```

### D5 — Thumbnail upload is best-effort, post-time
At compose time only the CardyB metadata + image URL are held; Coil loads the thumb directly from CardyB's image URL for preview (no blob upload while composing). At post time, `DefaultPostingRepository` downloads the image bytes and uploads them via the existing `uploadBlob` path → `external.thumb`. CardyB already returns resized/optimized images, so bytes are uploaded directly (a size guard skips the thumb rather than dragging the `content://`-oriented `ImageEncoder` into a bytes path). Any failure — download, oversize, upload — yields `thumb = AtField.Missing` and the post proceeds; a thumbnail never blocks or fails a post.

## Architecture

### `:core:posting`
- `ExternalLinkDetector` — pure: first `http(s)` URL in text, excluding Bluesky quote-links and an exclude set.
- `ExternalLinkMetadataRepository` (interface) + `CardyBExternalLinkMetadataRepository` (impl), injecting the singleton Ktor `HttpClient` from `:core:auth` DI:
  - `suspend fun fetch(url: String): LinkPreview?` — CardyB call; blank/`error`/network → `null`. Short timeout.
  - `suspend fun downloadThumb(imageUrl: String): ByteArray?`
- `LinkPreview(uri, title, description, imageUrl)` — internal domain model.
- `ComposerEmbedIntent` gains `external: PreparedExternal?` (uri/title/description/thumbImageUrl).
- `resolveEmbed` gains the external arm (truth table below).

### `:feature:composer:impl`
- `ComposerState.externalLink: ExternalLinkStatus = Idle`; sealed `ExternalLinkStatus { Idle; Loading(url); Loaded(preview) }`. Fetch failure returns to `Idle` silently (no separate Failed state — it would never render); the URL stays memoized so it does not retry.
- `TextLinkScanner` helper + `ExternalLinkDetector` wiring; quote detection refactored onto the scanner.
- New event `RemoveExternalLink` (the card's `X`).
- `ComposerLinkCard` composable — spinner while Loading; thumb/title/description/domain + dismiss `X` while Loaded.

## Data flow

1. Text change → existing `snapshotFlow` collector → `externalScanner.scan(text)`.
2. `alreadyHandled` true when images attached or a card already loaded → no fetch.
3. Fresh URL → `setState { Loading(url) }`, memoize, `launchExternalFetch(url)`.
4. `metadataRepo.fetch(url)` → `Loaded(preview)`, or `null`/error → `Idle` silently (no snackbar; URL stays memoized).
5. `RemoveExternalLink` → `Idle`, URL stays memoized.
6. Submit → `ComposerEmbedIntent.external` → `resolveEmbed`.

### `resolveEmbed` truth table

| images | quote | external | → embed |
|---|---|---|---|
| ∅ | ∅ | ∅ | Missing |
| yes | any | any | `images` / `recordWithMedia(record, images)` — **external dropped** |
| ∅ | ∅ | yes | `external` |
| ∅ | yes | ∅ | `record` |
| ∅ | yes | yes | `recordWithMedia(record=quote, media=external)` |

`canPost` is unchanged — a card never gates submit; `Loading` does not block (resolved/skipped at post time).

## Error handling

- CardyB null/error/network/timeout → silent `Idle`, memoized (no retry loop, no snackbar). Short GET timeout.
- Thumbnail failure (download/oversize/upload) → `thumb = Missing`, post proceeds.
- Offline → no card; posting unaffected.
- Races → adding images clears a loaded card; scanner suppresses a card while images present.

## Testing

- **VM unit** (drive `snapshotFlow` via `Snapshot.sendApplyNotifications()` + `runCurrent()`): URL → Loading→Loaded (fake repo); quote-link excluded; images suppress fetch; adding images clears card; dismiss → Idle + memoized; CardyB null/error → silent Idle.
- **Repository** (Ktor `MockEngine`): success → `LinkPreview`; blank/`error` → null; network fail → null; `downloadThumb` success/fail.
- **`resolveEmbed`** (`:core:posting`): external-only; external+quote → recordWithMedia; external+images → dropped; thumb success → Defined, failure → Missing + post proceeds.
- **`TextLinkScanner`** unit tests + regression-confirm the refactored quote-link path passes its existing tests.
- **Screenshot**: composer with a Loaded card + the Loading state (feature module → `update-baselines` on CI).
