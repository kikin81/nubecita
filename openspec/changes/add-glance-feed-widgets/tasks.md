# Tasks — add-glance-feed-widgets (sub-project C, bead nubecita-lgoo.3)

Cross-links: bead `nubecita-lgoo.3`. Reference `proposal.md`, `design.md` (D-C1–D-C10), and the design input `docs/superpowers/specs/2026-06-09-glance-widget-c-design-input.md`. Each task is one PR-sized chunk; `Closes:`/`Refs: nubecita-lgoo.3` in PR footers.

## 1. Module + Glance scaffolding (D-C1)

- [x] 1.1 Add the `:feature:widgets:impl` module (settings.gradle + convention plugins); `:impl` applies `nubecita.android.feature` and adds `androidx.glance:glance-appwidget`, `glance-material3`, `glance-appwidget-testing` to the version catalog + `:impl` build file; `:checkSortDependencies` passes; `:feature:widgets:impl:compileDebugKotlin` builds. (No `:api` module — widgets reuse `:feature:postdetail:api`'s `PostDetailRoute` NavKey, so there is no new NavKey type to host.)
- [x] 1.2 Define the `WidgetEntryPoint` (`@EntryPoint @InstallIn(SingletonComponent::class)`) exposing `FeedRepository`, `SessionStateProvider`, `PinnedFeedsRepository`, plus the `Context.widgetEntryPoint()` resolver using `EntryPointAccessors.fromApplication` (D-C2). `WidgetThumbnailStore` (task 4) and `WidgetEntitlementGate` (task 7) join the entry point when they land. No unit test: resolving an `@EntryPoint` needs a Hilt+Android graph, so testing it would test the framework — it's exercised by the real widgets (task 5) + the instrumented pass (task 9).

## 2. WidgetUpdater binding relocation (D-C3)

- [x] 2.1 Remove the unconditional `@Binds WidgetUpdater` from `:core:widget-sync`'s `WidgetSyncModule` (deleted the module — it bound only the updater); add a `TestWidgetUpdaterModule` in `:core:widget-sync`'s `androidTest` binding `NoOpWidgetUpdater`, since its own `@HiltAndroidTest` builds `WidgetRefreshWorker` via the `HiltWorkerFactory` and the worker graph still needs the binding. `:core:widget-sync` JVM tests (inject fakes directly) + androidTest compile stay green.
- [x] 2.2 Bind `WidgetUpdater` per `:app` flavor via `WidgetUpdaterModule` in `app/src/production` and `app/src/bench` — both no-op for now (production is the placeholder; group 8 swaps it to the real `GlanceWidgetUpdater`). Verified both `:app:assembleProductionDebug` and `:app:assembleBenchDebug` assemble (the `@HiltWorker` forces every flavor's graph to resolve `WidgetUpdater`, so the bench bind is required too — no duplicate/missing-binding error).

## 3. Image-prefetch seam in the worker (D-C4) — modifies widget-feed-refresh

- [x] 3.1 Added `interface WidgetImagePrefetcher { suspend fun prefetch(feedKey: FeedKey) }` + `NoOpWidgetImagePrefetcher` to `:core:widget-sync` (mirrors `WidgetUpdater`; no `androidx.glance`/image-loader dep). Bound at the `:app` flavor level + the androidTest test module (renamed the group-2 `WidgetUpdaterModule`/`TestWidgetUpdaterModule` → `WidgetSeamBindingsModule`/`TestWidgetSeamBindingsModule`, each now binding both seams) — same flavor-relocation pattern as the updater.
- [x] 3.2 `WidgetRefreshRunner` injects `WidgetImagePrefetcher` and calls `prefetch(feedKey)` after a feed's `trimToCap` succeeds, in its **own** nested try/catch (rethrow `CancellationException`; a prefetch throw is logged and fails only that feed's images — the refresh already succeeded, so it never flips the outcome, skips the updater, or aborts other feeds). `WidgetRefreshRunnerTest` extended (8 tests): prefetch invoked once per succeeded feed (order Following→Discover), not at all when every feed fails, only the succeeded feed on partial failure, and a throwing prefetcher leaves the worker `SUCCESS` with the other feed still refreshed/trimmed/prefetched + updater run once.

## 4. Thumbnail store + prefetch implementation (D-C5)

- [ ] 4.1 Implement `WidgetThumbnailStore`: dedicated cache dir `cacheDir/widget_thumbs/<accountDid>/`, write/read thumbnail file by `postUri`, and `clearAccount(did)`. JVM unit test: path/key mapping, write→read round-trip (temp dir), `clearAccount` deletes only that account's dir.
- [ ] 4.2 Implement the real `WidgetImagePrefetcher`: for each `head(n)` post with media, decode the first image `thumb` / video poster via the app Coil `ImageLoader.execute` downscaled to a fixed ≈180dp box, persist via the store. JVM unit test: decode-decision logic (which posts/embeds yield a decode; quote/external skipped) with a faked image loader.
- [ ] 4.3 Implement eviction in the prefetcher: after computing the current `head(n)` `postUri` set, delete files+keys for URIs no longer present; wire `clearAccount` to logout/account-clear. JVM unit test: prune-set math (URIs to delete = cached − current head) and that a post leaving the head is evicted.

## 5. Free widgets: Following + Discover (D-C6, D-C8)

- [ ] 5.1 Widget item view-model mapping: `PostUi` → compact widget item (author, text snippet, relative time, single-thumb path + "+N" count, post URI). JVM unit test: count derivation, text truncation, thumb-path selection, a11y description.
- [ ] 5.2 Build the shared widget composables (header, post row at ≥48dp, thumbnail+badge, loading/empty/signed-out states) with system radii (`.appWidgetBackground()`, system background + inner radius), no double-padding, `glance-material3` `ColorProviders` (light/dark + dynamic). Glance unit test (`runGlanceAppWidgetUnitTest`): loading/empty/signed-out compose; a populated head composes N rows.
- [ ] 5.3 Implement `FollowingFeedWidget` + `DiscoverFeedWidget` (`GlanceAppWidget` reading `head(feedKey,n)` via the entry point under `IO`) and their `GlanceAppWidgetReceiver`s; `SizeMode.Responsive` breakpoints, `previewLayout` with sample content, `<appwidget-provider>` xml (targetCell + legacy min sizes + resizeMode + non-generic description), `values-v31` `widget_margin` 0dp. Register receivers in `:app` manifest. Glance unit test: each widget composes from a stub head.

## 6. Tap-through + manual refresh (D-C7)

- [ ] 6.1 Implement the post-tap `actionStartActivity` deep-link builder (reuse the `nubecita://`/`app.nubecita` URI shape routed by `NavKeyDeepLinkMatcher`/`DeepLinkRouter`); no `clickable{}`-lambda Activity launch. JVM unit test: builder produces the expected URI/intent for a post URI. Glance unit test: a post row carries the correct `actionStartActivity` Action.
- [ ] 6.2 Wire the manual-refresh affordance + widget-add (`onUpdate`/receiver) to enqueue `:core:widget-sync`'s on-demand `OneTimeWorkRequest` (`KEEP`); the widget render path issues no network. Test: tapping refresh enqueues the unique on-demand work (assert via the work-scheduler seam / Glance action test).

## 7. Configurable widget + config activity (D-C9)

- [ ] 7.1 Add the `WidgetEntitlementGate` seam with an `AlwaysAllowed` impl bound in C (D swaps the `isPro` impl later). JVM unit test: always-allowed returns allow; the seam is injectable/overridable.
- [ ] 7.2 Implement `ConfigurableFeedWidget` (reads its instance's configured `FeedKey` from Glance state, defaults to Following when unset) + `GlanceAppWidgetReceiver` with `APPWIDGET_CONFIGURE`. Glance unit test: configured feed renders; unconfigured defaults to Following.
- [ ] 7.3 Implement `WidgetConfigureActivity`: list saved/pinned feeds (`PinnedFeedsRepository`), persist the chosen `FeedKey` to the widget instance state, return `RESULT_OK` with `appWidgetId`; dismiss → default Following. Register the activity in `:app` manifest. Screenshot test of the picker list (Compose, not Glance); unit test: selection persists the FeedKey + sets the result.

## 8. Real WidgetUpdater + production wiring (D-C3)

- [ ] 8.1 Implement the Glance-backed `WidgetUpdater` (`GlanceAppWidgetManager` + `updateAll` for each widget class) in `:feature:widgets:impl`; bind it in the `:app` production flavor (replacing the 2.2 placeholder). JVM/Glance test of the updater decision where feasible; verify `assembleProductionDebug` + `assembleBenchDebug`.
- [ ] 8.2 Bind the real `WidgetImagePrefetcher` in the `:app` production flavor (bench keeps no-op). Verify both flavors assemble and bench issues no Glance/widget work.

## 9. Quality + budget verification (D-C5, D-C8, D-C10)

- [ ] 9.1 Add an instrumented `run-instrumented` receiver end-to-end test (widget pin → cache populated → render) and a widget-scale bitmap budget test (the C analogue of A's §9.4): assert single-thumb-per-row stays within the RemoteViews IPC budget at the largest responsive size. If it fails, fall back to text-only + "🖼 N" indicator (D-C5) and record the decision in the change.
- [ ] 9.2 Verify widget-quality checklist (D-C8) against a device/emulator: fill-grid, 48dp at min size, system radii, light/dark+dynamic, preview + description, no double-padding. Capture screenshots for the PR; note any deferrals.

## 10. Wrap-up

- [ ] 10.1 Run `./gradlew :app:assembleProductionDebug :app:assembleBenchDebug testDebugUnitTest spotlessCheck lint :app:checkSortDependencies` + the touched modules' screenshot tasks; all green.
- [ ] 10.2 `openspec validate add-glance-feed-widgets --type change --strict`; update tasks checkboxes; prepare for archive once merged.
