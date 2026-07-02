# KLIPY GIF support in the post composer — design

**Status:** design agreed (brainstorm). Next: beads epic + tasks → migrate to OpenSpec.
**Date:** 2026-07-02
**Related:** `nubecita-q01y` (existing GIF *render* support in `:core:feed-mapping` + `PostCardGifEmbed`), `:core:billing` (the SDK-agnostic-boundary pattern this mirrors).

## Goal

Let users attach an animated GIF (or sticker) from KLIPY to a post from the composer. A picked GIF is published as an `app.bsky.embed.external` shaped so it renders **inline-animated** in Nubecita **and** on the official Bluesky app + other clients — not just as a link card.

## Scope

**In (v1):**
- **Surface:** the post composer (`:feature:composer`) only.
- **Content:** **GIFs + Stickers** (both are KLIPY `static.klipy.com/ii/…gif` items and animate everywhere via the existing renderer).
- Direct KLIPY API integration, the picker UI, posting the embed, compliance (branding + tracking + report).

**Out (deferred, separate specs):**
- **Clips.** KLIPY clips are longer MP4 on a different path; the right treatment is **native Bluesky video** (`app.bsky.embed.video`), not an external card. That requires downloading the clip, `uploadBlob`, and Bluesky's **asynchronous video processing** — driven by a **background job with a foreground-service notification** and job-status polling. Its own feature/epic.
- The **DM/chat composer** (chat messages only allow record embeds; a GIF there would be a bare link).

## Key decisions

1. **Direct KLIPY API (Approach A), not Bluesky's proxy.** We call `api.klipy.com/api/v1/{key}/…` with our own key. This is the sanctioned path and returns the real `static.klipy.com` CDN URLs. (The official app fetches KLIPY through Bluesky's *private* proxy `gifs.bsky.app/klipy/v2/*` with a `client_key`; reusing that would mean impersonating their client and dodging KLIPY's attribution/tracking — rejected.)
2. **The embed shape is what makes it render everywhere.** Inline animation on the official app is keyed on the URL (`parseKlipyGif`), not on who posted it. We must write `external.uri = https://static.klipy.com/ii/<path>/<file>.gif?hh=<H>&ww=<W>&mp4=<slug>[&webm=<slug>]` with a `thumb` blob. Host **must** be exactly `static.klipy.com`, path must start `/ii/`, and `hh`/`ww` are mandatory.
3. **Ktor + kotlinx.serialization**, reusing the app's existing HTTP stack — no new HTTP library. (`:core:posting`'s CardyB fetcher already uses Ktor directly; the AT-Proto client is Ktor too.)
4. **Render side already exists — reuse, don't rebuild** (see §Rendering).

## Architecture

### `:core:klipy` (new module — `nubecita.android.library` + `nubecita.android.hilt`)

SDK-agnostic boundary, mirroring `:core:billing`: no KLIPY/Ktor/DTO type leaks past it; consumers see only `:data:models` types.

- **`KlipyRepository`** interface, returning `:data:models` types:
  - `search(type, query, page)`, `trending(type, page)`, `categories(type)`, `recents(type, page)`
  - `trackView(type, slug)`, `trackShare(type, slug)`, `report(type, slug, reason)`, `hideRecent(type, slug)`
  - `type ∈ { Gif, Sticker }` (Clip deferred). Note: `type` here selects the **endpoint prefix** (`gifs/` vs `stickers/`, which share an identical request/response shape); it is distinct from the per-item `type` field in the response that discriminates a normal item from an injected `ad`.
- **Networking:** a dedicated **Ktor `HttpClient`** instance (JSON content negotiation via kotlinx.serialization) — *not* the OAuth/DPoP-configured client from `:core:auth`. Base URL `https://api.klipy.com/api/v1/{key}/`. **The key is a path segment, so the entire base URL is a secret** — never logged. This requires **explicit Ktor logging sanitization** (strip/omit the URL path, or disable network logging for this client): Ktor's `Logging` plugin logs the full path by default, and standard sanitizers only strip query params, so the key would otherwise leak into logcat/build logs/APM.
- **DTOs:** `@Serializable`, with a **custom polymorphic deserializer keyed on the `type` field** (general / ad; clip ignored in v1) — the kotlinx equivalent of the demo's Gson deserializer. Envelope: `{ result, data: { data: [...], has_next, meta: { item_min_width } } }`. Each item: `{ slug, title, blur_preview, file: { hd, md, sm, xs } }`, each size bucket → `{ gif, webp, mp4 }` each `{ url, width, height, size }`.
- **Mapper → `KlipyMediaUi`** (new `:data:models` type, `@Immutable`). Retains:
  - grid rendition: light `xs`/`sm` **webp** (or gif) URL for the scroll grid;
  - preview rendition: `md`;
  - **embed source** (critical): the `static.klipy.com/ii/…gif` URL + `width`/`height` + the `mp4` (and `webp`) slugs, plus the `blur_preview` and preview URL — everything the composer needs to build the compliant embed + thumb.
- **`customer_id`:** a stable per-install (or per-account) GUID persisted in a tiny DataStore; sent on fetches + tracking bodies.
- **Tracking:** `trackView`/`trackShare` fire-and-forget on an **application-scoped coroutine** (injected `@ApplicationScope`, on IO) — NOT `viewModelScope`, which would cancel the request mid-flight when the composer finishes right after a send. Failures swallowed (telemetry, not user-facing).
- **Paging:** a `KlipyPagingSource` (Paging 3) — infinite scroll with reset-on-query/tab/category change.
- **Bench fake:** `BenchFakeKlipyRepository` with canned offline data, so the whole picker is exercisable on the bench flavor with no key/network.
- **Config:** KLIPY key via a Gradle secret → `BuildConfig`. Bench flavor uses the fake (no key). Test key = 100 req/hr — the ~300ms debounce + Paging caching keep dev under it; the production key has higher limits.

### Picker UI (`:feature:composer:impl`)

Kept in the composer feature for now (extractable to a shared module if DMs ever want it — YAGNI until then). Its own `KlipyPickerViewModel : MviViewModel<S,E,F>`.

- **Presentation, form-factor aware, reusing the composer's existing overlay pattern:** `ModalBottomSheet` on compact; `Popup`-over-`Surface` on medium/expanded (because the composer is itself a centered dialog on tablet — exactly what `AudiencePicker`/`LanguagePicker` already do). Local `showGifPicker` state + `BackHandler` dismissal like the other pickers.
- **Structure:** a **"Search KLIPY"**-placeholder search bar (~300ms debounce — snappy; the test-key rate limit is a dev-only concern, and Paging caching absorbs load); **GIF / Sticker** tabs; a categories row (Recents + Trending prepended); a **`LazyVerticalStaggeredGrid`** (~2 cols) whose cells play the light webp/gif via a **single shared Coil `ImageLoader`** (never a per-cell ExoPlayer — protects 120hz scroll); `blur_preview` placeholders; Paging 3 infinite scroll.
- **Long-press preview** → `trackView(slug)`, bigger rendition, with a **Report** action (predefined reasons → `report(slug, reason)`).
- A persistent **"Powered by KLIPY"** mark (KLIPY brand-kit asset).

### Selection → post embed

- On tap: VM fires **`trackShare(slug)`** (Recents + KLIPY engagement contract), returns a `KlipyMediaUi` to the composer.
- Composer stores a **pending GIF embed** and shows an **animated preview** (reusing `PostCardGifEmbed`) with a remove ✕.
- **Mutual exclusivity:** a Bluesky post has exactly one embed, so the GIF is mutually exclusive with photo attachments and quote/link cards. A **GIF button** in `ComposerOptionsChipRow` opens the picker and is **disabled when images are attached** (and vice-versa) — one "embed slot".
- **Record construction** reuses the composer's existing `app.bsky.embed.external` builder (already used for link cards, already uploads a thumb blob). Add a `ComposerEmbedIntent.Gif` variant folded into that builder, writing:
  ```
  app.bsky.embed.external
    external.uri = https://static.klipy.com/ii/<path>/<file>.gif?hh=<H>&ww=<W>&mp4=<slug>[&webm=<slug>]
    external.title = <title>
    external.description = "ALT: <content description or user alt>"
    external.thumb = <blob>   ← download KLIPY's preview image, uploadBlob (same as link-card thumbs)
  ```

## Rendering (already built — reuse)

The feed/thread render path already targets KLIPY:
- `GifEmbed.isGifExternalUri` **already allowlists `static.klipy.com`** (+ Tenor/Giphy/`.gif`), and `gifAspectRatioOrNull` **already parses `ww`/`hh`**.
- `EmbedUi.Gif` model + `PostCardGifEmbed` render it inline+animated (Coil `AnimatedImageDecoder`), via `ExternalView.toEmbedUiExternalOrGif`.

So a KLIPY GIF we post renders inline in our own feed/threads with **no new render code**, and the composer preview reuses the same component. The embed our builder emits is, by construction, exactly what `isGifExternalUri` accepts.

## Compliance (KLIPY requirements)

- **Branding (gates production API approval):** "Search KLIPY" search placeholder + a visible "Powered by KLIPY" mark, using KLIPY brand-kit assets.
- **Tracking:** `trackView` on preview, `trackShare` on send.
- **Report:** in-preview Report action with predefined reasons.
- **Content filter:** configured server-side on the key in the KLIPY Partner Panel (optionally a request-time `rating` param — confirm name against docs once we have a key).
- **Release gate:** production key requires KLIPY approving the integration + branding; ship on the test key behind a flag until approved.

## Testing

- **Unit:** DTO→`KlipyMediaUi` mapper (rendition selection); polymorphic `type` deserializer; **embed-URI builder** — with an assertion that `isGifExternalUri` accepts what the builder emits (produce↔consume contract); `customer_id` stability; mutual-exclusivity reducer.
- **Bench:** `BenchFakeKlipyRepository` → picker fully exercisable offline on the bench flavor (per convention).
- **Screenshot:** picker sheet + composer GIF preview.
- Compose review gate applies (new `@Composable`s).

## Open risks / to validate with a real test-post

1. **Off-app playback:** the official app plays our-posted GIF by host-swapping `static.klipy.com` → its proxy `k.gifs.bsky.app`. This relies on Bluesky's proxy serving a KLIPY path *we* originated. Our own in-app render doesn't depend on it (we play `static.klipy.com` directly), but off-app animation does — verify with a live post.
2. **`webm` availability:** KLIPY supplies mp4 + webp (not necessarily webm). `mp4` alone covers the official web client's `<video>` fallback per research; confirm whether omitting `webm` degrades any client.
3. **Production key approval** + exact content-filter param name — confirm in the Partner Panel / docs once keyed.

## Deferred / future (own specs)

- **Clips → native video.** Download the KLIPY clip MP4 → `uploadBlob` → Bluesky's async video processing (job submit + status poll) → post `app.bsky.embed.video`. Needs a **background job + foreground-service notification** for the upload/processing lifecycle. Separate epic.
- **DM/chat composer** GIF support (constrained: chat messages allow only record embeds).
