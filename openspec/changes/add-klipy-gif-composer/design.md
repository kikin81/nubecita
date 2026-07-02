## Context

Full brainstorm: `docs/superpowers/specs/2026-07-02-klipy-gif-composer-design.md` (PR #650). Tracked by beads epic `nubecita-50ge`.

The composer supports photo attachments but no GIFs. Bluesky posts a GIF as an `app.bsky.embed.external` and clients render it inline-animated only if the URL matches a known GIF host. The official Bluesky app migrated Tenor→KLIPY; its `parseKlipyGif` animates any external embed whose `external.uri` is `static.klipy.com/ii/…?hh=&ww=&mp4=&webm=` — detection is keyed on the URL, not on who posted it. Nubecita's own feed render path already recognizes the same shape (`isGifExternalUri` allowlists `static.klipy.com`; `EmbedUi.Gif`; `PostCardGifEmbed`).

## Goals / Non-Goals

**Goals:**
- Attach a KLIPY GIF/sticker from the post composer and publish it so it renders inline-animated in Nubecita AND on the official app / other clients.
- SDK-agnostic `:core:klipy` boundary; no new HTTP dependency; offline-testable on the bench flavor.
- Meet KLIPY compliance (branding + view/share/report tracking).

**Non-Goals (deferred / out):**
- **Clips** (native video: download → uploadBlob → Bluesky async video processing → `app.bsky.embed.video`, needs a background job + foreground-service notification) — its own epic.
- The **DM/chat composer** (messages allow only record embeds).
- Re-hosting KLIPY media as Bluesky-native blobs.

## Decisions

1. **Direct KLIPY API (own key), not Bluesky's private `gifs.bsky.app` proxy.** Sanctioned; returns the real `static.klipy.com` CDN URLs. (Reusing Bluesky's proxy would mean impersonating their `client_key` and bypassing KLIPY attribution/tracking.)
2. **The embed shape is the contract.** Write `external.uri = https://static.klipy.com/ii/<path>/<file>.gif?hh=<H>&ww=<W>&mp4=<slug>[&webm=<slug>]`, `title`, `description = "ALT: …"`, `thumb = <uploaded preview blob>`. Host must be exactly `static.klipy.com`, path `/ii/…`, `hh`/`ww` mandatory.
3. **`:core:klipy` = SDK-agnostic boundary** (mirrors `:core:billing`). `KlipyRepository` returns `:data:models` types; DTOs/Ktor never leak. **Ktor + kotlinx.serialization** (already in the tree — CardyB uses Ktor), a dedicated client instance (not the OAuth/DPoP one), with a polymorphic DTO deserializer keyed on `type`, a `KlipyMediaUi` mapper that retains the embed source (gif URL + hh/ww + mp4/webp slugs) and grid rendition (light `xs/sm` webp), Paging 3, and a stable `customer_id` in DataStore.
4. **Key confidentiality.** The key is a URL path segment → configure Ktor's `Logging` plugin to sanitize/omit the path (default logging leaks it).
5. **Tracking on `@ApplicationScope`.** `trackView`/`trackShare` fire-and-forget on an application-scoped coroutine so a send that finishes the composer can't cancel the request mid-flight.
6. **Picker presentation mirrors the composer's existing overlays** — `ModalBottomSheet` on compact, `Popup`-over-`Surface` on medium/expanded (composer is a centered dialog on tablet), exactly like `AudiencePicker`/`LanguagePicker`. Grid = `LazyVerticalStaggeredGrid`, cells play light webp via one shared Coil `ImageLoader` (never per-cell ExoPlayer — protects 120hz). Search debounce ~300ms.
7. **One embed slot.** A GIF is mutually exclusive with photos/quote/link-card; the GIF entry point is disabled when photos are attached, and vice-versa.
8. **Reuse the render + posting machinery.** Render side exists (no changes). Posting reuses `:core:posting`'s external-embed builder (already uploads a thumb for link cards) via a new `ComposerEmbedIntent.Gif` variant. Composer preview reuses `PostCardGifEmbed`.
9. **Bench fake** `BenchFakeKlipyRepository` with canned data so the picker is exercisable offline, no key.

## Risks / Trade-offs

- **Off-app playback:** the official app plays our-posted GIF by host-swapping `static.klipy.com` → its proxy `k.gifs.bsky.app`; this relies on Bluesky's proxy serving a KLIPY path we originated. Our own in-app render plays `static.klipy.com` directly and is unaffected. Verify off-app animation with a live test-post.
- **`webm` availability:** KLIPY supplies mp4 + webp (not necessarily webm); `mp4` alone covers the official web client's `<video>` fallback per research — confirm no client regresses if `webm` is omitted.
- **Production key approval** is gated on KLIPY approving the branding; ship on the test key (100 req/hr) behind a flag until approved. Confirm the exact content-filter param against the docs once keyed.
