## Context

On-device translation shipped in the feed only (`add-on-device-post-translation`, archived 2026-11-06). Three things keep it there:

- **Decision logic is feature-private.** `FeedTranslationCoordinator` (detect the source language, check packs, download, translate, reconcile facets, log analytics) lives in `:feature:feed:impl`. Feature `impl` modules may not depend on each other, so post detail cannot reuse it.
- **State is screen-scoped.** `FeedTranslationState` is created with `remember` in `FeedScreen` and holds results in a `mutableStateMapOf`. When the feed leaves composition its translations go with it; another screen cannot see them.
- **The cache was never wired.** #1100 added `post_translation` (`TranslationEntity`, `TranslationDao`, keyed by post URI + target language, bounded at 500 rows). Nothing reads or writes it. The spec scenario "already-translated posts are not re-translated" holds only within one feed composition.

Post detail is a plain `LazyColumn` over `PostDetailState.items: ImmutableList<ThreadItem>` (ancestors → focus → replies) with a hoisted `LazyListState`, and renders each post through `PostCard`, which already accepts `translation`, `onTranslate` and `onToggleTranslation`. The UI is ready; the logic and state are not reachable.

Constraints: 120hz scrolling is a hard requirement, and the settle gate (dispatch only when the list is at rest, visible posts only) is what makes translation free. It was measured at 1.77% / 1.65% janky frames on vs 2.01% off. The gate must move without changing.

## Goals / Non-Goals

**Goals:**
- One reusable implementation that any post-list surface adopts with a few lines, with no copy of decision logic, state or the offer dialog per feature.
- Post detail translates its ancestors, focus and replies under the feed's rules.
- A translation made anywhere shows everywhere, including after a restart.
- The feed behaves exactly as it does today, and its committed screenshot baselines stay byte-identical.

**Non-Goals:**
- Wiring search, bookmarks, liked posts or profiles (bd follow-ups after this lands).
- Any change to detection, Wi-Fi gating, the offer-once rule, facet reconciliation, `PostCard`, or `PostTranslationUi`.
- Persisting "show original" across process death.
- New analytics events or parameters.

## Decisions

### D1. Two modules, mirroring `:core:post-interactions` / `:core:post-interactions-ui`

- `:core:post-translation` (non-UI; `nubecita.android.library` + `.hilt`): `PostTranslationCoordinator` (moved from the feed, renamed, behaviour unchanged), the `PostTranslations` store, and a `TranslationCache` seam over `TranslationDao`.
- `:core:post-translation-ui` (`nubecita.android.library.compose` + `.hilt`): `rememberPostTranslations(...)` (the settle gate, moved), the per-screen offer holder, `AutoTranslateOfferDialog`, and the four offer strings with their es / pt-BR translations (moved; resource names lose the `feed_` prefix).

*Alternatives.* Folding this into `:core:translation` was rejected: that module is the SDK boundary and is deliberately free of analytics, database and preferences (the coordinator's own comment calls this "the seam the design doc asks for"), and all three are needed here. One combined module was rejected because it would pull `material3` / `:designsystem` into anything that only needs the store.

### D2. The store is a `@Singleton` holding Compose snapshot state, not a `StateFlow<Map>`

`PostTranslations` keeps `entries: SnapshotStateMap<String, PostTranslationUi>` and `showOriginal: SnapshotStateMap<String, Boolean>`, keyed by post URI. Its public surface (names are the builder's; the shape is the decision):

- `stateOf(post): PostTranslationUi?`: a snapshot read folding in show-original, exactly what `FeedTranslationState.forPost` does today. **No side effects.** It is called during composition, so it must not touch the LRU order or start work.
- `onSettled(posts)`: resolve every visible post that has no entry and has not failed; log one `translate_batch` per settle, as today.
- `translate(post): Deferred<String?>`: a chip tap. Completes with the source language if the post ended up `Shown`, so the caller can decide on the offer.
- `toggleOriginal(postUri)`.
- `shouldOfferAuto` / `acceptAuto` / `declineAuto`: pass-throughs to the coordinator.
- `generation: Int` (snapshot state): bumped whenever preferences invalidate entries (D5).

A snapshot map recomposes only the card whose entry changed. A `StateFlow<Map>` would recompose every visible card on each change, and a flow per post means one collector per card. Both are what #1100's DAO KDoc ruled out for the same 120hz reason. The cost is a `compose-runtime` dependency in a non-UI module, which `:data:models` already takes.

**Threading.** Snapshot maps are written on the main thread, as `FeedTranslationState` does now. Engine work stays where the coordinator already puts it (`Dispatchers.Default` inside `:core:translation`). `:core:common` has no main-dispatcher qualifier, so add `@MainDispatcher` beside `@DefaultDispatcher` / `@IoDispatcher` and inject it (tests swap it via `MainDispatcherExtension`).

### D3. Work runs on `@ApplicationScope`, not the screen's scope

The store launches resolution on the injected `@ApplicationScope` (a `SupervisorJob`), switching to the main dispatcher for map writes. A translation started in the feed therefore finishes and lands in the shared map even if the reader opens the post mid-translation, and post detail shows the shimmer and then the result without a second request (the `inFlight` guard is store-wide). This also removes today's failure mode where a throw in a `rememberCoroutineScope` (a plain `Job`) cancels every later translation on the screen. The per-post containment (`runCatchingCancellable` plus the `failed` set) moves over unchanged.

The screen's own scope is used only to await `translate(post)` and raise the offer: the offer belongs to the screen the reader is looking at.

### D4. Resolution order: memory, detection, cache, then the engine

For a post with no entry:

1. **Detect first, as today.** `detectSource` runs before anything else, because a post in one of the reader's languages, including one they've since added to never-translate, must show nothing even if a translation of it is cached. Detection is a string compare on the declared language; identification only runs with the master toggle on, as today.
2. **Cache.** If the post is foreign, `TranslationCache.find(postUri, target)`. A hit becomes `Shown` straight away, with no `InProgress` and so no shimmer flash, and **regardless of the auto list**: a translation follows its post (spec: *A translation follows its post*). Cached facets are the reconciled ones, so no reconciliation re-runs.
3. **Otherwise the coordinator's existing path,** unchanged from `supports` onward: wanted, packs, download, translate, reconcile.
4. **On `Shown`,** write `TranslationEntity(postUri, target, source, text, facetsJson, now)` via `upsertAndTrim`, fire-and-forget on the application scope. A failed write logs and is otherwise ignored: the cache is disposable.

Facets serialize with the atproto models' own `Facet.serializer()` (they are `@Serializable`) through a module-private `Json`. Cache reads happen only for posts without an entry, at settle, so they never run mid-scroll.

`TranslationCache` is a small internal interface over the DAO so the store's JVM tests use an in-memory fake rather than Robolectric + Room.

### D5. Keeping a process-long store from going stale

Screen-scoped state got this for free: a new feed composition re-resolved every post. An app-wide store lasts for the whole process, so anything that used to be fixed by "the next time the feed opens" needs an explicit trigger. Each trigger below either clears entries or bumps `generation`, which makes every surface at rest re-settle (below).

- **Target language.** Android announces a locale change (it is a configuration change: `MainActivity` doesn't list `locale` in `configChanges`, so it is recreated; `ComponentCallbacks.onConfigurationChanged` fires app-wide; `ACTION_LOCALE_CHANGED` is broadcast). But our code doesn't listen: `DeviceLocales.current()` is a plain function, so `ReaderLanguages.targetLanguage` re-emits only when the never-translate preference does, and an app-wide store outlives the recreated activity. The fix is at the source. `DeviceLocales` gains `val locales: Flow<List<String>>`, a `callbackFlow` that registers a `ComponentCallbacks` on the application context, emits `current()` on start and on each `onConfigurationChanged`, and unregisters on close (`distinctUntilChanged`). `ReaderLanguages.languages` becomes `combine(locales, neverTranslateLanguages)`, so `targetLanguage` is a real flow. The store watches it like any preference; on change it clears `entries`, `showOriginal` and `failed`. The cache needs nothing; it is keyed by target.

  The store collects the reader languages and target **once**, into `StateFlow`s via `stateIn(applicationScope, SharingStarted.Eagerly, …)`, and the coordinator reads their current values instead of calling `.first()` per post as it does today. With a callback-backed flow, a `.first()` per resolution would register and unregister a `ComponentCallbacks` for every post. One app-wide registration lives for the process.
- **Reader languages (`neverTranslateLanguages` via `ReaderLanguages.languages`).** On change, clear every entry that isn't in flight, including `Shown`. A reader who adds Spanish to never-translate stops seeing Spanish translations at once. D4's detect-first order keeps the cache from bringing them back.
- **Auto list or master toggle.** Remove every `Offered` entry and clear `failed`. `Shown` and in-flight states stay; a hand translation must not revert.
- **A language pack becoming available.** The store collects `TranslationModelRepository.states`. When a pack turns downloaded, it removes `WaitingForWifi` (and stale `Downloading`) entries for that language. Otherwise a post that hit cellular would say "waiting for Wi-Fi" for the rest of the process, even after the repository finished the download on Wi-Fi.
- **Failures.** Today the `failed` set and a failed or empty translation (which falls back to `Offered`) are retried the next time the feed is composed, which is roughly "one try per post per screen visit". To keep that, the coordinator reports engine failures to the store, so those posts join `failed` rather than becoming indistinguishable `Offered` entries. `rememberPostTranslations` calls `translations.onSurfaceEntered()` once when it enters composition; that clears `failed` and the failure-derived entries, so each surface visit gets one fresh try per post, as now.

All preference flows are collected with `distinctUntilChanged`, skipping the first emission. `rememberPostTranslations` adds `generation` to its settle signal, so a screen at rest re-settles at once. Accepting the offer in post detail therefore translates the other visible replies in that language without a scroll.

### D6. Bounded memory: LRU at the cache's capacity

`entries` is capped at `TranslationDao.DEFAULT_CAPACITY` (500). An access-order key list, not snapshot state, is touched in `onSettled` and `translate`, never in `stateOf` (D2). On overflow the least recently used keys are removed from `entries`. An evicted `Shown` comes back from the cache on its next settle.

`showOriginal` is **not** part of the LRU. It is a set of URIs the reader reverted by hand, a few bytes each and bounded by taps, and the spec says the choice holds for the rest of the process. Evicting it would silently re-translate a post the reader asked to see in the original.

### D7. Screens get the store from their ViewModel, as the feed does today

`FeedViewModel` already exposes `translationCoordinator` to `FeedScreen`. Both `FeedViewModel` and `PostDetailViewModel` now inject `PostTranslations` and expose it as `val translations`, which the screen passes to `rememberPostTranslations`. Translation state stays out of `UiState`, as today: it belongs to the surface, not the post, and is read per card from snapshot state.

*Alternative.* A Hilt `EntryPoint` lookup inside the helper was rejected: it hides the dependency, and a nullable parameter is what lets previews and screenshot fixtures pass `null` and render with no translation.

### D8. The settle helper moves verbatim, and gains `generation` and a key-based visible-posts contract

`rememberPostTranslations(listState, visiblePosts: () -> List<PostUi>, translations: PostTranslations?)` carries `SettleSignal` and the `snapshotFlow` gate unchanged (the 120hz measurement depends on it), plus `generation` in the signal. It returns a per-screen handle exposing `stateOf`, `translate` (which awaits and raises the offer once), `toggleOriginal`, and `autoOffer` for the dialog. It also renders nothing itself. The screen places `AutoTranslateOfferDialog` from the handle, as `FeedScreen` does now.

Each surface supplies `visiblePosts` from `listState.layoutInfo.visibleItemsInfo`. Post detail must map **item keys** (`ThreadItem.key`) to posts rather than indices, because its list interleaves outgoing replies, folds, and blocked / not-found rows; the feed keeps its own mapping.

### D9. Analytics count work and demand, not cache hits

The events and parameters are unchanged; what they count must stay the same too.

- **`translate_batch`** counts translations the engine actually performed in a settle. A cache hit is not a translation, so it is excluded. Otherwise every settle after a restart would log batches for posts that were translated days ago, inflating the auto-translation number.
- **`translate_post`** is logged on a chip tap, before any work, because it measures demand ("how many readers want this"). A tap served from the cache is still a reader asking, so it is still logged, as today.
- **`translation_model_download`** and **`translate_error`** are unchanged; a cache hit triggers neither.

The store's resolution result therefore distinguishes "translated by the engine" from "served from the cache", and the batch counts only the former.

### D10. Post detail wiring

`Ancestor`, `Focus` and `Reply` items pass `translation = handle.stateOf(post)`, `onTranslate` and `onToggleTranslation` to their `PostCard`. `Blocked`, `NotFound`, `Fold` and outgoing replies do not translate. The focus post's emphasis (container colour / shape) is unaffected: `PostCard` swaps the body in place.

## Risks / Trade-offs

- **[A global snapshot map mutated off the main thread]** → All writes go through the main dispatcher (D2); a unit test asserts the store never writes from the engine's dispatcher.
- **[The feed regresses while being moved]** → There are **no feed screenshot baselines that render translation**: translation fixtures live only in `:designsystem`'s `PostCardTranslationScreenshotTest`, which this change doesn't touch, so screenshots cannot catch a feed regression. The real guards are the ported unit tests (`FeedTranslationCoordinatorTest`, `FeedTranslationStateTest` and `FeedTranslationAutoOfferTest`, moved with every case intact), the bench device pass, and re-measuring the 120hz scroll rather than arguing it. PR 2 still runs CI without `update-baselines`, so an unintended visual change elsewhere in the feed fails.
- **[Cache hits show a translation for a language the reader did not put on the auto list]** → Intended (spec: *A translation follows its post*). The reader asked for that post once; "Show original" still reverts it for the session.
- **[`Offered` entries thrown away on every preference change]** → They are cheap to recompute (declared-language detection is a string compare), and preference changes are rare.
- **[Post detail's visible-posts mapping drifts from its item model]** → Key-based mapping (D8) plus a unit test over a thread with folds and outgoing replies.
- **[A slow cache read on the first settle after a cold start]** → It runs only at rest, off the main thread, and only for posts without an entry; a miss falls through to the existing path.

## Migration Plan

No schema change: `post_translation` exists at version 10 and gains its first reader and writer. The stack lands atomically, with one release:

1. `:core:post-translation`: coordinator moved (the feed switches its import), store, cache seam, `@MainDispatcher`, tests moved and added.
2. `:core:post-translation-ui` plus the feed migration in one PR, because extracting the helper and dialog *is* deleting them from the feed: helper, offer holder, dialog and strings moved; the feed uses the store. No baseline changes.
3. Post detail: wiring, key-based visible posts, screenshot fixtures for a translated reply and a chip reply, and a bench-flavor device pass.

**Bench fixture.** `BenchFakePostThreadRepository.getPostThread` returns the same `BENCH_THREAD` for every URI, and its focus isn't one of the timeline's foreign posts, so no post appears on both surfaces in bench. The fake becomes keyed by URI: the timeline's foreign posts return a thread focused on that post with foreign-language replies (one language on the bench reader's auto list, one not); **every other URI keeps returning `BENCH_THREAD` unchanged**. That leaves the marketing capture (`MarketingScreenshotJourney`'s post-detail shot) and the deliberate handle-URI vs DID mismatch that reproduces the media-viewer deep-link bug exactly as they are.

Rollback is a revert of the merge commit; nothing persistent depends on the new modules beyond cache rows, which the old code ignores.

## Open Questions

- Should `translate_post` / `translate_batch` gain a `surface` parameter (feed vs post detail) so adoption per screen is measurable? Left out here because it needs a GA4 custom dimension registered as well; worth its own bd issue once post detail ships.
