## 1. PR 1 — `:core:post-translation`: shared store, cache, coordinator

- [ ] 1.1 Create `:core:post-translation` (`nubecita.android.library` + `.hilt`, empty `consumer-rules.pro`, `compose-runtime` dep) and register it in `settings.gradle.kts`.
- [ ] 1.2 Make `DeviceLocales` observable (D5): a `locales` flow from a `ComponentCallbacks` on the application context, and `ReaderLanguages.languages` combining it with `neverTranslateLanguages`. Unit-test that a locale emission changes `targetLanguage`, keeping the existing `ReaderLanguages` resolution tests green. `SystemDeviceLocales` holds it as a process-long `StateFlow`, so per-post reads register nothing.
- [ ] 1.3 Add `@MainDispatcher` to `:core:common` beside `@DefaultDispatcher` / `@IoDispatcher`, bound to `Dispatchers.Main.immediate`.
- [ ] 1.4 Move `FeedTranslationCoordinator` → `PostTranslationCoordinator` with behaviour unchanged; the feed switches its import. Move `FeedTranslationCoordinatorTest` with every case intact.
- [ ] 1.5 Add the internal `TranslationCache` interface plus a Room implementation over `TranslationDao` (facets via `Facet.serializer()`), with a JVM round-trip test for facet serialization.
- [ ] 1.6 Implement `PostTranslations` (D2–D6, D9): snapshot maps written on `@MainDispatcher`, work on `@ApplicationScope`, detect-then-cache resolution, write-through on `Shown`, the D5 staleness triggers (target language flow, reader languages, auto list / toggle, pack availability, failures plus `onSurfaceEntered`) with `generation`, an LRU of 500 touched only outside `stateOf`, `showOriginal` kept outside the LRU, and a result that separates engine work from cache hits.
- [ ] 1.7 Port `FeedTranslationStateTest` cases to `PostTranslationsTest` against a fake cache, and add tests: a cache hit shows `Shown` with no `InProgress` and no engine call; the write-through writes once; a target-language emission clears entries so memory is not served stale; a never-translate change clears `Shown` and a cached translation of a now-reader language is not shown; an auto-list change drops only `Offered` and bumps `generation`; a pack turning downloaded re-resolves its `WaitingForWifi` posts; an engine failure lands in `failed` and `onSurfaceEntered` retries it once; LRU eviction then recovery from the cache, with `showOriginal` surviving eviction; cache hits excluded from `translate_batch`; no map write off the main dispatcher; an in-flight post requested from a second caller is not translated twice.
- [ ] 1.8 Gate: `:core:post-translation:lintDebug`, `testDebugUnitTest`, and `:feature:feed:impl:lintProductionDebug` + `testProductionDebugUnitTest` (the feed still compiles on the moved coordinator).

## 2. PR 2 — `:core:post-translation-ui` and the feed migration

- [ ] 2.1 Create `:core:post-translation-ui` (`nubecita.android.library.compose` + `.hilt`, empty `consumer-rules.pro`).
- [ ] 2.2 Move the settle gate into `rememberPostTranslations(listState, visiblePosts, translations)` with `SettleSignal` unchanged plus `generation`; it returns the per-screen handle (`stateOf`, `translate` that raises the offer once, `toggleOriginal`, `autoOffer`, accept / decline).
- [ ] 2.3 Move `AutoTranslateOfferDialog` and its four strings (drop the `feed_` prefix) with the `es` / `pt-BR` translations; run the module's own lint for `MissingTranslation`.
- [ ] 2.4 Port `FeedTranslationAutoOfferTest` to the handle; add a test that a `generation` bump re-settles a list at rest.
- [ ] 2.5 Feed: `FeedViewModel` injects `PostTranslations` and exposes `translations`; `FeedScreen` / `PostFeedList` use the helper and handle; delete `feature/feed/impl/.../translation/`, the dialog and the strings. Update `FeedViewModelTest`.
- [ ] 2.6 Run CI without `update-baselines` and verify no feed baseline changes; note that no feed baseline renders translation, so the unit tests and 2.7 are the real regression guards. Verify `checkSortDependencies` and that the bench variant compiles.
- [ ] 2.7 Device pass (bench flavor): a foreign post on the auto list translates on settle, a chip tap offers once, and "Show original" sticks across scroll. Re-measure the 120hz scroll with translation on against the recorded numbers.

## 3. PR 3 — Post detail translates its thread

- [ ] 3.1 `PostDetailViewModel` injects `PostTranslations` and exposes `translations`; update `PostDetailViewModelTest` construction.
- [ ] 3.2 `PostDetailScreen`: build `visiblePosts` from `visibleItemsInfo` **keys** mapped to `ThreadItem.Ancestor` / `Focus` / `Reply` posts; unit-test the mapping over a thread with a fold, a blocked row and an outgoing reply.
- [ ] 3.3 Pass `translation`, `onTranslate` and `onToggleTranslation` to the ancestor, focus and reply `PostCard`s; place `AutoTranslateOfferDialog` from the handle.
- [ ] 3.4 Screenshot fixtures (light / dark): a thread with a translated reply, a reply showing the chip, and a focus post showing its translation. Baselines via the `update-baselines` label, checked to discriminate.
- [ ] 3.5 Key `BenchFakePostThreadRepository` by URI: the timeline's foreign posts return a thread focused on that post with foreign-language replies (one auto-list language, one not); every other URI keeps returning `BENCH_THREAD` unchanged, so the marketing post-detail capture and the handle-vs-DID deep-link repro are untouched. Add a JVM test for both branches.
- [ ] 3.6 Device pass (bench flavor): open a post the feed translated (shown at once, no shimmer); auto-list replies translate on settle; a chip tap translates and offers; accepting translates the other visible replies without a scroll; go back and the feed shows the reply translated; "Show original" in detail holds in the feed; force-stop and reopen, and the translations come back from the cache.
- [ ] 3.7 Top-of-stack gate: `:app:assembleDebug`, lint for every touched module, and the compose-expert review over `git diff origin/main...HEAD`.

## 4. After the stack lands

- [ ] 4.1 File bd follow-ups: translation on search, bookmarks, liked posts and profiles (each: the ViewModel exposes `translations` plus the helper call), and the `surface` analytics parameter (design Open Questions).
