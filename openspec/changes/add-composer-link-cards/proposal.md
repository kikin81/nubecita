# Proposal: Composer paste-a-link external embeds

## Why

The post composer can attach images, galleries, and quoted posts, but **not external link cards** — pasting a URL today posts bare text with no preview. The render path for `app.bsky.embed.external` already exists end-to-end (`EmbedUi.External` → `FeedMapping.toEmbedUiExternalOrGif` → `PostCardExternalEmbed`); only the **create** side is missing. This is the most broadly useful composer gap: nearly every user pastes links.

This change is the foundation that the deferred `nubecita-kpqb` (Atmosphere `associatedRefs` enhancement via `getEmbedExternalView`) must layer on top of — and which the kpqb evaluation surfaced as the real prerequisite.

## What changes

- When the user types or pastes the **first** non–Bluesky-quote URL, the composer auto-fetches a preview via **CardyB** (`cardyb.bsky.app/v1/extract`, the service the official app uses) and shows a dismissable link card below the text.
- On submit, the composer builds an `app.bsky.embed.external` record (uploading the thumbnail blob best-effort).
- The card is **mutually exclusive with images** (images win), and **may coexist with a quoted post** via `recordWithMedia`.
- The genuinely-shared "detect-a-link-in-text, memoize attempts" orchestration is extracted into a small `TextLinkScanner` helper, and the existing quote-link detection is refactored onto it (same behavior).

## Out of scope (explicit)

- `associatedRefs` / Atmosphere `site.standard.*` records — that is `nubecita-kpqb`, which depends on this change.
- Remove-thumbnail toggle, manual card entry/editing, GIF picker, cross-session preview cache, multiple cards per post.

## Impact

- `:core:posting` — new `ExternalLinkMetadataRepository` (CardyB) + `ExternalLinkDetector`; `ComposerEmbedIntent` + `resolveEmbed` gain an `external` arm; thumbnail blob upload reuses the existing `uploadBlob` path.
- `:feature:composer:impl` — new `ExternalLinkStatus` on `ComposerState`, detection in the existing `snapshotFlow` collector, `TextLinkScanner` helper (+ quote-link refactor onto it), a `ComposerLinkCard` composable.
- No change to the render path or `:data:models`.

Tracked by `nubecita-gfli`. Precursor to `nubecita-kpqb`.
