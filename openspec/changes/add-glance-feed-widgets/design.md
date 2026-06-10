## Context

Sub-projects A and B are merged: `:core:feed-cache` owns the DID-keyed Room cache and exposes `head(feedKey, n): Flow<List<PostUi>>`; `:core:widget-sync` (B) runs the battery-cooperative `WidgetRefreshRunner` that refreshes Following + the fixed Discover partition in the background (foreground-guarded, per-feed-independent, retry-only-on-total-failure) and then calls a `WidgetUpdater` seam — currently bound to `NoOpWidgetUpdater`. Nothing renders the cache yet.

C adds the Jetpack Glance widgets. Glance is **Compose-runtime, not Compose-UI** (`androidx.glance:glance-appwidget` 1.1.1): widget composables compile to `RemoteViews`, cannot reuse `PostCard`/Coil/Material3, have no Hilt composition, run `provideGlance` on the main thread, and refresh by *push* (`updateAll`) rather than by continuous observation. The render path must do zero network — pure cache read + pre-decoded bitmaps — to honor the project's battery-first rule.

Research and the agreed image/quality decisions live in `docs/superpowers/specs/2026-06-09-glance-widget-c-design-input.md`; the epic decisions (D1–D11) in `docs/superpowers/specs/2026-06-08-glance-feed-widgets-design.md`.

## Goals / Non-Goals

**Goals:**
- Three home-screen widgets (Following free, Discover free, configurable Pro-surface) rendering `head(feedKey, n)` from the offline cache, with loading/empty/signed-out states and a manual refresh.
- Single-thumbnail + "+N" media per post, fed by an off-thread image-prefetch pipeline with bounded eviction.
- Real Glance `WidgetUpdater` replacing B's no-op; deep-link tap-through into the thread.
- Meet Google's widget-quality Tier-1/2 requirements (fill-grid, 48dp targets, system radii, responsive sizing, light/dark + dynamic color, preview + description, no double-padding).
- Keep the configurable-widget entitlement decision behind a seam D can plug into without restructuring.

**Non-Goals:**
- The `isPro` gate + paywall upsell (D). The app feed `PagingData` migration (E). Reproducing the app's multi-image layouts/carousel. Inline reply, multi-instance same-feed, refresh-cadence UI, multi-account.

## Decisions

### D-C1: Module = `:feature:widgets` (`api` + `impl`); Glance lives only in `:impl`
Follows the repo's feature api/impl split. `:impl` applies `nubecita.android.feature` + adds `androidx.glance:glance-appwidget`, `glance-material3`, `glance-appwidget-testing`. `:api` carries only what `:app`/other modules must reference without pulling Glance — primarily nothing UI, but it hosts the deep-link `NavKey` reuse contract if needed. The widgets are **not** `@MainShell`/`@OuterShell` `NavDisplay` entries (a widget is a `RemoteViews` host outside the `NavDisplay`), so no `EntryProviderInstaller` qualifier applies. **Alternative rejected:** putting widgets in `:core:widget-sync` — would force Glance into B and break its deliberate Glance-free boundary.

### D-C2: DI into Glance via `@EntryPoint` + `EntryPointAccessors.fromApplication`
Glance widgets aren't Hilt-injected. Define a `@EntryPoint @InstallIn(SingletonComponent::class) interface WidgetEntryPoint { fun feedRepository(): FeedRepository; fun sessionStateProvider(): SessionStateProvider; fun widgetThumbnailStore(): WidgetThumbnailStore; fun pinnedFeedsRepository(): PinnedFeedsRepository }` and resolve it inside `provideGlance`/the receiver. Cache reads run via `withContext(Dispatchers.IO)` (head is a suspend/Flow first value), never on Glance's main-thread compose. **Alternative rejected:** custom Hilt component for widgets — unnecessary; `fromApplication` on the singleton graph is the documented Glance pattern.

### D-C3: Relocate the `WidgetUpdater` binding to the `:app` flavor level (fixes the duplicate-binding trap)
B binds `NoOpWidgetUpdater` unconditionally in `:core:widget-sync`'s `WidgetSyncModule` (`SingletonComponent`). C cannot add a second `@Binds WidgetUpdater` in another `SingletonComponent` module — Hilt rejects duplicate bindings. Fix: **delete the unconditional bind from `WidgetSyncModule`** and select the impl per flavor, matching the existing `ProductionBootstrapModule` pattern:
- `app/src/production/...` binds the real `GlanceWidgetUpdater` (from `:feature:widgets:impl`) and the real `WidgetImagePrefetcher`.
- `app/src/bench/...` (and any non-production flavor) binds the `NoOp*` defaults (kept in `:core:widget-sync`), so bench builds stay Glance-free and issue zero widget work.

This keeps B independently testable (its unit tests already inject a fake `WidgetUpdater` directly, not via Hilt) and is the minimal, convention-aligned change to B.

### D-C4: Add a `WidgetImagePrefetcher` seam in `:core:widget-sync`, called by the worker
The image-prefetch must run in the same backgrounded, foreground-guarded context as the cache refresh (it's off-UI bitmap decode), so it belongs to the worker flow — but the *decode* (Coil) and *Glance state write* are C concerns. Mirror the `WidgetUpdater` pattern: `interface WidgetImagePrefetcher { suspend fun prefetch(feedKey: FeedKey) }` + `NoOpWidgetImagePrefetcher`, called by `WidgetRefreshRunner` **after a feed's `trimToCap` succeeds**, inside the same per-feed try/catch (a prefetch failure fails only that feed's images, never the refresh or the widget update; rethrow `CancellationException`). This is the one behavior change to the `widget-feed-refresh` capability. **Alternative rejected:** a separate prefetch worker — a second background job violates the battery rule; piggy-backing on B's run is free.

### D-C5: Image model — one thumbnail + "+N" badge; bounded cache dir keyed by `postUri`; eviction required
Per the design input §3. For each `head(n)` post with media: decode **only the first image's `thumb`** (or video poster) via the app's Coil `ImageLoader.execute` → `Bitmap`, downscaled to a **single fixed bounding box (≈180dp)** since the worker can't know the active responsive cell. Persist as a file in a dedicated cache dir (`cacheDir/widget_thumbs/<accountDid>/`); Glance state holds only the path/URI (small). The "+N" count is free from the cached embed (no decode). Render via `ImageProvider(bitmap-from-file)`; play-icon overlay for video; quote/external = text-only. **Eviction (required):** on each prefetch run, after computing the current `head(n)` URI set, delete any thumbnail file + key for a `postUri` no longer in that set; clear an account's whole dir on logout (mirrors `clearAccount`). This bounds the image cache to ~`n` thumbnails per feed — the image analogue of A's `trimToCap`. **Conservative fallback** retained: if a widget-scale bitmap budget test (a C analogue of A's §9.4 scale test) shows even one-thumb-per-row strains the `RemoteViews` IPC budget, drop to text-only + a "🖼 N" indicator; ship single-thumb + badge as the default.

### D-C6: Widgets read the cache head directly (D8), render-only, zero network
Each widget's `provideGlance` reads `head(feedKey, n)` (n ≈ 5–6, `ORDER BY position LIMIT n`) and the prefetched thumbnail paths from Glance state. No `getTimeline`/`getFeed` in the widget. Freshness comes from B's worker → `WidgetUpdater.updateAll`. The widget composition observes the cache only at compose time (`runBlocking`/first value under `IO`), not as a live home-screen observer.

### D-C7: Tap-through via `actionStartActivity` + existing deep-link routing (Android-12 trampoline-safe)
A post row's `GlanceModifier.clickable(actionStartActivity(...))` launches `MainActivity` with a `nubecita://`/`app.nubecita` `ACTION_VIEW` intent carrying the post URI, routed by the existing `NavKeyDeepLinkMatcher` / `DeepLinkRouter` (the same machinery push/DM taps use) into `MainShell`'s inner `NavDisplay` → `PostDetailRoute`. Must use `actionStartActivity` (not a `clickable {}` lambda that starts an Activity) to comply with the Android-12 trampoline ban. The widget background tap (empty area) opens the relevant tab/app.

### D-C8: Quality compliance baked into the layout
Root `Scaffold`/`Box` uses `.appWidgetBackground()` + `@android:dimen/system_app_widget_background_radius`; thumbnails/avatars use `system_app_widget_inner_radius` (8dp). `SizeMode.Responsive(setOf(...))` with breakpoints sized so every interactive element is ≥48dp at the smallest declared size (small sizes → row-tap-only, ~2 posts at 4×2). `targetCellWidth/Height` (Android 12) + legacy `minWidth/Height` (`70n−30`) + `resizeMode` + `previewLayout` with real sample content + a non-generic `<appwidget-provider>` `description`. No double-padding (rely on auto-padding at `targetSdk ≥ 31`; `widget_margin` 0dp in `values-v31`). Colors via `glance-material3` `ColorProviders` from the app scheme → light/dark + dynamic color; WCAG contrast checked.

### D-C9: Configurable widget + config activity (built, gate ungated)
A fourth `GlanceAppWidget` + a `WidgetConfigureActivity` (declared as the receiver's `android.appwidget.action.APPWIDGET_CONFIGURE`). The activity lists the user's saved/pinned feeds from `:core:feeds` `PinnedFeedsRepository`, writes the chosen `FeedKey` into the widget's Glance state, and returns `RESULT_OK` with the `appWidgetId`. **Sensible default:** if the user dismisses configuration, default to Following so the widget is never blank. The entitlement decision sits behind an injectable `WidgetEntitlementGate` (C ships an `AlwaysAllowed` impl; D swaps in an `isPro`-backed impl + paywall-upsell state) so D is a binding swap, not a rewrite.

### D-C10: Testing
- **JVM unit (`:core:testing`):** the `WidgetImagePrefetcher` eviction logic (prune set math), the thumbnail-store path/key mapping, the `WidgetEntitlementGate` decision, the head→widget view-model mapping (PostUi → compact widget item incl. "+N" count), the deep-link intent builder.
- **Glance unit (`glance-appwidget-testing`):** `runGlanceAppWidgetUnitTest` asserts composition + that a row carries the correct `actionStartActivity` Action, and that loading/empty/signed-out states compose.
- **Instrumented (`run-instrumented`):** receiver end-to-end + the widget-scale bitmap budget test (D-C5 fallback gate).
- Worker change in B re-uses B's existing JVM runner tests (add a prefetch-seam call assertion + per-feed isolation of a prefetch throw).

## Risks / Trade-offs

- **RemoteViews IPC bitmap budget** → one small bitmap per post, fixed-box downscale, eviction to ~n; instrumented budget test gates the single-thumb decision with a text-only fallback (D-C5).
- **`WidgetUpdater` binding relocation touches merged B code** → minimal: delete one `@Binds`, move impl selection to `:app` flavors; B's tests inject fakes directly so they're unaffected. Verify both flavors assemble (`assembleProductionDebug` + `assembleBenchDebug`).
- **Glance main-thread `provideGlance`** → all cache/disk reads wrapped in `withContext(IO)`; no network at all on this path.
- **Configurable widget ships ungated until D** → accepted per the scope decision; the gate seam is present so D is a drop-in. Flagged so the release between C and D is understood to expose the Pro surface for free.
- **Deep-link parity** → reuse the exact `NavKeyDeepLinkMatcher`/`DeepLinkRouter` path push/DM taps use; no new URI shape, so manifest intent-filters are unchanged.
- **Glance 1.1.1 vs 1.2.x** → stay on 1.1.1 stable (1.2.x bumps minSdk to 23 and is pre-release); revisit when 1.2 stabilizes.

## Open Questions

- Final `n` (head count) per size breakpoint — start ~5–6, tune against the 48dp density requirement during layout.
- Whether the configurable picker reuses any in-flight custom-feeds work (`nubecita-lq9t.3.2`) or reads `PinnedFeedsRepository` directly — default to the latter unless that work lands first.
- Exact responsive `DpSize` breakpoint set — finalized with the layout mockups during implementation.
