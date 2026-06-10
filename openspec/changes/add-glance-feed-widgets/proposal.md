## Why

Sub-projects A (`:core:feed-cache`) and B (`:core:widget-sync`) built the offline feed cache and the battery-cooperative background worker, but there is still **no widget on the home screen** — B's `WidgetUpdater` is a no-op and nothing renders the cache. This change ships the actual Jetpack Glance home-screen widgets so a user sees fresh Bluesky posts without opening the app, and taps through into the thread. It is sub-project **C** (`nubecita-lgoo.3`) of the Glance feed widgets epic.

## What Changes

- **New `:feature:widgets` module** (`api` + `impl`) that adds the `androidx.glance` / `glance-appwidget` dependency — the first and only Glance surface in the app (B is deliberately Glance-free).
- **Three `GlanceAppWidget` surfaces:**
  - **Following** (free) — renders the signed-in account's Following head.
  - **Discover** (free) — renders the fixed `whats-hot` feed head.
  - **Configurable** (Pro surface) — a fourth widget whose feed is chosen in a configuration activity that lists the user's saved/pinned feeds (via `:core:feeds`). Built here with a clean entitlement-gate **seam left ungated**; sub-project D (`nubecita-lgoo.4`) plugs in the `isPro` gate + paywall upsell.
- Each widget renders `head(feedKey, n)` from `:core:feed-cache` (n ≈ 5–6) via an `@EntryPoint`-injected `FeedRepository` (Glance has no Hilt composition), with intentional **loading / empty / signed-out** states, a **manual refresh** affordance, `SizeMode.Responsive` breakpoints, light/dark + dynamic color, and 48dp touch targets + system corner radii per Google's widget-quality guidelines.
- **Per-post media = single thumbnail + "+N" badge** (no carousel; Glance has no `LazyRow`): the first image's `thumb` is pre-decoded to a bounded bitmap, with a play-icon overlay for video posters and text-only for quote/external embeds.
- **Image-prefetch pipeline** (extends B's worker via a new `WidgetImagePrefetcher` seam): for each `head(n)` post, decode only the first thumbnail off the UI/scroll path to a bounded bitmap (≈150–200dp box) in a dedicated cache dir, keyed by `postUri`, with **eviction** that prunes thumbnails for posts no longer in `head(n)` (and clears an account's thumbnails on logout).
- **Real Glance-backed `WidgetUpdater`** (`GlanceAppWidgetManager` + `updateAll`) replaces B's `NoOpWidgetUpdater` in the production flavor; widget-add / manual-refresh enqueue B's on-demand `OneTimeWorkRequest`.
- **Tap-through deep links**: tapping a post row opens the thread in-app via `actionStartActivity` + the existing `nubecita://` / `NavKeyDeepLinkMatcher` routing (respecting the Android-12 trampoline ban).

## Capabilities

### New Capabilities
- `feed-widgets`: Jetpack Glance home-screen widgets (Following, Discover, configurable) that render the offline feed cache head, the configuration activity, the real `WidgetUpdater` + `WidgetImagePrefetcher` implementations, the single-thumbnail media model, and deep-link tap-through.

### Modified Capabilities
- `widget-feed-refresh`: the background worker gains an **image-prefetch step** — after a feed partition refreshes successfully it invokes a `WidgetImagePrefetcher` seam (no-op default in B, real impl bound by C) off the scroll path, mirroring the existing `WidgetUpdater` seam. (B's worker control flow — per-feed independence, retry-only-on-total-failure, foreground guard — is unchanged.)

## Impact

- **New dependency:** `androidx.glance:glance-appwidget` (+ `glance-material3`, `glance-appwidget-testing`) in `:feature:widgets:impl` only.
- **New module:** `:feature:widgets:api` (deep-link `NavKey` reuse / widget receiver wiring) + `:feature:widgets:impl` (Glance widgets, config activity, updater + prefetcher impls, DI).
- **Touches `:core:widget-sync` (B):** adds a `WidgetImagePrefetcher` interface + `NoOp` default + worker call site; relocates the `WidgetUpdater` *binding selection* from the always-on `WidgetSyncModule` to the `:app` flavor level so the production flavor binds the Glance impl and the bench flavor keeps the no-op (avoids a Hilt duplicate-binding error).
- **Touches `:app`:** manifest `<receiver>` entries for each `GlanceAppWidgetReceiver` + the configuration `<activity>`; production-flavor Hilt bindings for the real `WidgetUpdater` / `WidgetImagePrefetcher`.
- **Reuses:** `:core:feed-cache` (`head`, `FeedKey`), `:core:feeds` (`PinnedFeedsRepository` for the config picker), `:core:auth` (signed-in DID), Coil `ImageLoader` (off-thread bitmap decode), the existing deep-link routing.
- **Battery:** the widget render path does **zero network** (pure cache read + pre-decoded bitmaps); image decode runs only inside B's already-battery-cooperative backgrounded worker. No new background work, no `updatePeriodMillis`.
- **Deviation from baseline:** Glance is Compose-**runtime**, not Compose-UI — these widget composables cannot reuse `PostCard`/Coil composables/Material3 and do not follow the screen-MVI pattern (a widget is update-driven `RemoteViews`, not a `ViewModel`-backed screen). This deviation is inherent to Glance and scoped to `:feature:widgets`.

## Non-goals

- The `isPro` gate + paywall upsell on the configurable widget (sub-project **D**, `nubecita-lgoo.4`) — C leaves the gate seam ungated.
- The in-app feed screen migration to `PagingData`/`RemoteMediator` (sub-project **E**, `nubecita-lgoo.5`).
- Reproducing the app's 1/2/3/4-image layouts or Material carousel in the widget; inline reply from the widget; multiple instances of the same feed; a user-facing refresh-cadence setting; multi-account widgets (the cache is DID-keyed but MVP is single-account).
