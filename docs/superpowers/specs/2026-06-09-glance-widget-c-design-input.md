# Glance feed widgets — sub-project C design input (Jetpack Glance + widget quality)

**Status:** Research / design input (not a spec) — 2026-06-09
**Epic:** `nubecita-lgoo` · **For:** sub-project **C** (`nubecita-lgoo.3`, the Glance widgets) and **D** (`nubecita-lgoo.4`, Pro gating)
**Depends on:** A (`:core:feed-cache`, complete) + B (`:core:widget-sync`, `nubecita-lgoo.2`, spec'd)
**Companion to:** the epic design doc `docs/superpowers/specs/2026-06-08-glance-feed-widgets-design.md`

> Captures the Jetpack Glance API constraints, Google's widget-quality requirements, and the image-handling decisions agreed for the widget, so C can be designed/built against a known target. Sourced from the official Android docs (developer.android.com + the Android Knowledge Base) and the widget-quality guideline.

## 1. Jetpack Glance — constraints that shape C

- **Version:** `androidx.glance:glance-appwidget` **1.1.1** is current stable (1.2.x bumps minSdk to 23, still pre-release). Use `glance-material3` for `ColorProviders`, `glance-appwidget-testing` for unit tests.
- **Compose runtime, NOT Compose UI.** Separate composables + `GlanceModifier`, compiled to `RemoteViews`. **We cannot reuse `PostCard`/our Compose UI or Coil composables in the widget.**
- **Composable surface:** `Box / Column / Row / Text / Image / Button / Spacer`, `LazyColumn` (`ListView`-backed, **vertical only**, keep to *tens* of items, stable `key`), `CircularProgressIndicator`, `Scaffold`. **No `LazyRow`, no nested lazy lists, no arbitrary Compose, no `AsyncImage`.**
- **Update-driven, not reactive.** A widget reads data at compose time (`collectAsState` on a repo flow) but is **not** a continuous observer on the home screen. Fresh content is **pushed** via `GlanceAppWidget.update()/updateAll(context)` — which is exactly what **B's worker + `WidgetUpdater` seam** do.
- **DI:** widgets aren't Hilt-injected — use `@EntryPoint` + `EntryPointAccessors.fromApplication(context)` inside `provideGlance` to get `FeedRepository` (read via A's `head(feedKey, n)`).
- **Reads the cache:** C renders from `:core:feed-cache` `head(feedKey, n)` (n small, ~5–6). No network in `provideGlance` (and it runs on the main thread → `withContext(IO)` for any work).
- **Tap-through:** `actionStartActivity(Intent(ACTION_VIEW, "nubecita://…".toUri()).setPackage(packageName))` — reuses our existing deep-link routing. Android 12 **trampoline ban**: must use `actionStartActivity`, not a `clickable {}` lambda, to open the app.
- **Sizing:** `SizeMode.Responsive(setOf(DpSize…))` (recommended) + Android-12 `targetCellWidth/Height`.
- **Testing:** `glance-appwidget-testing` `runGlanceAppWidgetUnitTest` asserts composition + which `Action` a clickable carries (no render); receiver/end-to-end is instrumented.

## 2. Widget-quality hard requirements (must-not-ship-without)

Source: https://developer.android.com/docs/quality-guidelines/widget-quality (Tier 1/2).

- **Layout:** fill the grid (touch all 4 edges); declare Android-12 `targetCellWidth/Height` + legacy `minWidth/Height` (`70*n − 30` dp); resizable to ≥ one of 2×2 / 4×1 / 4×2 with `resizeMode` + min/max resize bounds; **content never cropped at min size**.
- **Touch targets:** **48×48 dp** for every interactive element, *including at the minimum widget size*. → **bounds feed density**: ~2 posts at 4×2, ~4–5 at 4×4; on small sizes prefer **row-tap-only** (open thread), push per-post actions to larger sizes / the app.
- **Corner radius — system only:** root uses `@android:dimen/system_app_widget_background_radius` (≤28dp) + `.appWidgetBackground()`; inner elements (thumbnails/avatars) use `@android:dimen/system_app_widget_inner_radius` (8dp); rule `inner = background − padding`. **No custom radius.** Pre-12 fallback via shape drawable.
- **Padding:** no double-padding — rely on auto-padding at `targetSdk ≥ 31`; `widget_margin` 8dp → **0dp in `values-v31`** for legacy.
- **States:** intentional **loading / empty / signed-out** states (never blank); **manual refresh** affordance; not persistently stale; update **after an in-app action** too (a like in-app reflects in the widget) and after a widget action.
- **Theming:** light **and** dark + dynamic color (Material You on 12+) + WCAG contrast.
- **Discoverability/config:** `previewLayout` with real content; unique name + non-generic `description`; system configuration entry point; **sensible defaults so it works before configuration** (default to Following).
- **Battery:** **not** `updatePeriodMillis` (30-min floor, wakes device) → **WorkManager** + event-driven + manual refresh. (Exactly B's posture; matches the project battery rule.)

## 3. Image handling (the gallery decision + the prefetch pipeline)

**Glance cannot load remote image URLs** (no Coil/`AsyncImage`). Images must be **pre-decoded to bitmaps off-thread**, persisted as a file path / `content://` URI in Glance state, and rendered via `ImageProvider` — bounded by the RemoteViews IPC memory budget (few, small bitmaps, downscaled to the cell).

### Decision: image galleries → first thumbnail + count badge (NOT the app's per-count layouts/carousel)
The app's 1/2/3/4 image layouts and Material carousel are **not** reproduced in the widget. Instead, per post:
- **One thumbnail** when the post has media — the **first image's `thumb`** variant (small, cheap to decode), pre-decoded in the worker, downscaled to the cell.
- **"+N" count badge** overlaid when there's more than one image. The count is **free** — it comes from the cached post's embed (no decode); only the *first* thumb costs a decode.
- **Video embed** → poster thumb + play-icon overlay (one bitmap).
- **Quote-post / external link** → text-only (or a tiny indicator) for MVP — nested/extra decodes aren't worth the budget.
- Tapping the row opens the thread in-app (existing deep-link), where the real carousel handles the full gallery.
- **A11y:** content description like "Image, 4 total".

**Rationale:** RemoteViews IPC budget (one small bitmap per post, not 2–4 × N), bounded decode/battery cost, narrow widget real estate at 48dp rows, and the widget's teaser→tap-through model.

**Conservative fallback:** if a widget-scale bitmap test (a C analogue of A's §9.4) shows even one-thumb-per-row strains the budget, drop to **text-only with a small "🖼 4" media indicator** and no thumbnails. Start with single-thumb + count badge; keep text-only as the escape hatch.

### Image-prefetch pipeline (new scope for C / extends B's worker)
B's worker refreshes the *post* cache but does **not** decode images (B is Glance-free, no image work). C adds an **image-prefetch step**: in the worker (or a C-owned step it triggers), for each of the `head(n)` posts shown, decode **only the first image's `thumb`** (reuse the app's Coil `ImageLoader.execute` → `Bitmap`), then `updateAll()`. Bounded: one decode per shown post, not whole galleries.

**Sizing (per #510 review):** the background worker runs independently of the active widget composition and **cannot know the active responsive cell size**. So decode/downscale to a **single fixed max bounding box** — the largest thumbnail any responsive layout needs (≈150–200dp) — and let Glance scale it down at render. Key the cache by **`postUri`** alone (not `(postUri, size)`).

**Eviction (per #510 review — required):** the on-disk thumbnails and their Glance-state keys must be **pruned**, or they grow unboundedly as posts cycle out of the feed. Strategy: store thumbnails in a **dedicated cache dir** (not inline in DataStore prefs — prefs hold only the small path/URI), and on each prefetch run **delete the thumbnail file + its key for any post no longer in the current `head(n)` set** (and clear all of an account's thumbnails on logout, mirroring `clearAccount`). This bounds the image cache to ~`n` thumbnails per widget feed — the image analogue of A's `trimToCap`.

## 4. What C must carry in (summary)

- Glance module `:feature:widgets` (or similar) applying `androidx.glance`; provides the **real `WidgetUpdater`** (the no-op from B → `FeedWidget().updateAll(context)`), and enqueues B's on-demand refresh on widget add / manual refresh.
- `GlanceAppWidget`(s): **Following** (free), fixed **Discover** (free), **configurable** (Pro, gated by `isPro`, picker reuses `:core:feeds:PinnedFeedsRepository`); config activity with sensible defaults.
- Renders `head(feedKey, n)` from `:core:feed-cache`; one-thumb + count-badge media; deep-link tap-through; loading/empty/signed-out states; manual refresh; `SizeMode.Responsive`.
- Honors every §2 quality requirement (system radius, 48dp, fill-grid, light/dark + dynamic color, preview/description, no double-padding).
- Image-prefetch pipeline (§3).

## Sources
- Jetpack Glance: developer.android.com/develop/ui/compose/glance/{index, setup, create-app-widget, build-ui, user-interaction, glance-app-widget, theme, testing}; Glance release notes.
- Widget quality: developer.android.com/docs/quality-guidelines/widget-quality; develop/ui/views/appwidgets{, /layouts, /enhance}; about/versions/12/features/widgets.
