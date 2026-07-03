## Why

Users can attach photos to a post but not an animated GIF or sticker, a table-stakes expression tool for a social client. KLIPY is a free GIF/sticker provider, and — crucially — the official Bluesky app already detects KLIPY's CDN (`static.klipy.com`) and renders those external embeds inline-animated, so a GIF we post the right way animates across the whole Bluesky ecosystem, not just in-app.

## What Changes

- Add a **KLIPY GIF/sticker picker** to the post composer: search ("Search KLIPY"), GIF/Sticker tabs, categories (incl. Recents/Trending), an infinite-scroll staggered grid, and a long-press preview with a Report action.
- Attach a picked item as the post's embed. A post has exactly one embed, so a GIF is **mutually exclusive** with photo attachments and quote/link cards.
- Post the selection as `app.bsky.embed.external` with the exact `static.klipy.com/ii/…?hh=&ww=&mp4=` URL shape + a thumbnail blob, so it renders **inline-animated in Nubecita and on the official Bluesky app / other clients** (their detection is keyed on the URL). The Nubecita feed/thread render path already recognizes this shape (`isGifExternalUri` / `PostCardGifEmbed`) — reused, not rebuilt.
- Introduce a new **`:core:klipy`** SDK-agnostic boundary (Ktor + kotlinx.serialization) for KLIPY search/trending/categories/recents plus the mandated view/share/report tracking and a stable `customer_id`.
- Bake in KLIPY compliance: "Search KLIPY" placeholder, a visible "Powered by KLIPY" mark, and view/share tracking (production API access is gated on this branding).
- **Out of scope / deferred:** KLIPY Clips (native video — download → uploadBlob → Bluesky async video processing → `app.bsky.embed.video`, needs a background job + foreground-service notification: its own epic) and the DM/chat composer (messages allow only record embeds).

## Capabilities

### New Capabilities
- `klipy-media`: Fetching KLIPY GIFs/stickers (search, trending, categories, recents) and the mandated view/share/report tracking, behind an SDK-agnostic `:core:klipy` repository returning `:data:models` types.

### Modified Capabilities
- `feature-composer`: Add a GIF/sticker picker surfaced from the composer and the ability to attach a picked KLIPY item as the post's single, mutually-exclusive embed.

## Impact

- **New:** `:core:klipy` module (Ktor client, DTOs + polymorphic deserializer, mapper, `KlipyRepository`, Paging 3 source, `customer_id` store, bench fake); a `KlipyMediaUi` type in `:data:models`.
- **Modified:** `:feature:composer:impl` (picker UI + VM, GIF button, mutual-exclusivity, pending-embed preview via `PostCardGifEmbed`); `:core:posting` reuses its existing `app.bsky.embed.external` builder with a new `ComposerEmbedIntent.Gif` variant.
- **Reused as-is:** `:core:feed-mapping` (`isGifExternalUri` already allowlists `static.klipy.com`) + `designsystem/PostCardGifEmbed` — no render changes.
- **Config/secrets:** KLIPY API key via a Gradle secret → `BuildConfig` (key is in the URL path; Ktor logging must sanitize the path). Bench flavor uses the fake (no key/network).
- **Deps:** none new — Ktor + kotlinx.serialization + Coil are already present.
