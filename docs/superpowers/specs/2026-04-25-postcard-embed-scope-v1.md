# PostCard embed scope — v1

- **Date:** 2026-04-25
- **bd:** [nubecita-6rh](../../../) (decision)
- **Implements against:** nubecita-w0d (PostCard)
- **Status:** Accepted

## Decision

PostCard v1 renders **text + `app.bsky.embed.images`** only. All other embed types (`external`, `record`, `video`, `recordWithMedia`) degrade to an "Unsupported embed" placeholder chip.

## Context

Bluesky posts may carry exactly one of five embed types (or none):

| Embed lexicon | Description | Cost to ship |
|---|---|---|
| `app.bsky.embed.images` | 1–4 images, each with alt text + aspect ratio + blob ref | **Low** — `NubecitaAsyncImage` already exists; layout is a known pattern |
| `app.bsky.embed.external` | Link card (uri / title / description / optional thumb blob) | Medium — new "link card" composable, OG-preview-style layout |
| `app.bsky.embed.record` | Quoted post (recursive `post.view`) | High — recursive PostCard rendering, density-vs-readability tradeoff |
| `app.bsky.embed.video` | Video clip with `.m3u8` HLS playlist URL | High — Media3/ExoPlayer integration is a multi-day item |
| `app.bsky.embed.recordWithMedia` | Composition: `record` + (`images` \| `video`) | Highest — only viable after `record` + media tiers |

Each tier inflates feed-shell scope. Shipping all five for v1 turns PostCard from a sprintable component into a multi-week effort.

## Rationale for v1 = text + images

- `images` covers the largest share of posts in a typical feed.
- Image rendering's prerequisites are already in place (Coil 3 + `NubecitaAsyncImage`/`NubecitaAvatar`, PR #35).
- Deferring `record` avoids the recursive composable problem and the density-vs-readability decision until feed scrolling is verified at 120Hz on a known-good device.
- Deferring `video` avoids a multi-day Media3/ExoPlayer integration before any feed exists to host video.
- The "Unsupported embed" chip is a known degradation pattern from other AT Protocol clients — users tolerate it well in early-version clients.

## Roadmap

| Tier | Adds | Target | bd issue |
|---|---|---|---|
| v1 | text + `images` | This PostCard work | nubecita-w0d |
| v1.1 | `external` | Next | filed alongside this decision |
| v2 | `record` | After feed scaffolding stabilizes | filed alongside this decision |
| v3 | `video`, `recordWithMedia` | After video scope decision (Media3 stack pick) | filed alongside this decision |

## Consequences for PostCard implementation

- **KDoc on `PostCard` must list supported embed types** and explicitly call out the "Unsupported embed" fallback so future implementers don't assume PostCard handles everything.
- **Embed slot composable** uses an exhaustive `when (embed)` → `Images(...)` → `PostCardImageEmbed`; `Unsupported(typeUri)` → `PostCardUnsupportedEmbed` (a small `surfaceContainerHighest` chip with secondary-text label).
- **DTO → UI mapper sealed type** for embeds carries `Images(...)` + `Unsupported(typeUri: String)` variants. The chip can name the unrecognized type for debugging (e.g. `"Unsupported embed: app.bsky.embed.video"` in `BuildConfig.DEBUG`, generic label in release).
- **Unsupported chip is not an error** — no error styling, no error icon. It's a deliberate degradation, not a failure.

## Migration triggers

A future tier should be promoted from "deferred" to "in flight" when:
- **`external` (v1.1):** PostCard feedback indicates link-share posts feel broken (links appear in text but no card).
- **`record` (v2):** Feed scrolling perf is verified at 120Hz; we have a clear answer on whether quoted PostCards use the same density or a compact variant.
- **`video` / `recordWithMedia` (v3):** Media3 vs ExoPlayer (raw) decision lands; a video-embed test post in our feed is identifiable as a real UX gap, not a long-tail rarity.
