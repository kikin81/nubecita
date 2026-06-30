# Post-Interaction Consolidation — PR4 (Post-Detail + Close Block Parity) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan. Steps use checkbox (`- [ ]`) syntax.

**Goal:** The epic finale. (1) Migrate `:feature:postdetail:impl`'s hand-inlined post-interaction wiring onto the shared `PostInteractionHandler` + `rememberPostInteractions` (the last inline duplicate), and (2) **close block parity** — flip `BlockAuthor` from coming-soon→real on **both** post-detail and profile (via the handler's `NavigateToBlock`). After PR4 all four surfaces (feed/search/profile/post-detail) share one interaction path with identical capabilities.

**Architecture:** `PostDetailViewModel by handler` + `bind(PostSurface.PostDetail, viewModelScope)`; retire the inline like/repost/share/overflow handlers; `onOverflowAction` overrides ONLY mute/unmute (flag-flip across Ancestor/Focus/Reply, like profile) and delegates everything else — including `BlockAuthor`, which now reaches the handler → `NavigateToBlock` (real). Simultaneously remove profile's PR3 `BlockAuthor`-coming-soon override arm so profile's block also goes real. The post-detail migration is **byte-identical** (stateless `*Content` untouched); block→real is the only intentional behavior change (rendering unchanged — the overflow menu is identical, so screenshots stay byte-identical; the block *tests* flip).

**Tech Stack:** PR1's `:core:post-interactions` (`PostInteractionHandler`, `PostInteractionsCache`, `PostSurface.PostDetail`) + `:core:post-interactions-ui` (`rememberPostInteractions`, `InteractionStrings`), Hilt (`@AssistedInject`), Compose/M3, JUnit5 + MockK + Turbine, screenshot tests.

**Design spec:** `docs/superpowers/specs/2026-06-29-post-interaction-consolidation-design.md` (step 4: "Migrate post-detail + close block parity. Block goes coming-soon → real here, via the shared handler's NavigateToBlock").

## Global Constraints

- **PR1+PR2+PR3 merged** — `PostInteractionHandler`, `rememberPostInteractions`, `InteractionStrings`, `PostSurface.PostDetail`, the `Block`/`Report` moderation NavKeys all exist; `:feature:postdetail:impl` already depends on `:feature:moderation:api` (Block) + `:core:post-interactions`. Reuse; do NOT modify `:core:post-interactions*`.
- **Byte-identical screenshots** — the stateless `PostDetailScreenContent` + every `ui/*` content composable UNTOUCHED; tap-markers (`lastLikeTapPostUri`/`lastRepostTapPostUri`) STAY on `PostDetailState`, driven by a `handler.tapMarkers` collector (mirror Profile). Same for profile (`ProfileScreenContent` untouched). **0 baseline changes → NO `update-baselines` label.** (Block→real changes navigation, not rendering.)
- **Atomic cache-merge** — post-detail currently reads OUTSIDE `setState` (like the feed). Rewrite the (retired-as-direct, re-added-as-tapMarker) merge to read+merge INSIDE `setState` like ProfileViewModel — actually the cache merge moves into the handler; what stays is the `handler.tapMarkers → state` collector. (The handler owns the cache now; the VM no longer collects `cache.state` directly.)
- **Block parity is the one behavior change** — `BlockAuthor` → real `NavigateToBlock` on post-detail AND profile. The hero-level `StubbedAction.Block` (profile, via `ProfileEvent.StubActionTapped`) is SEPARATE and stays coming-soon — do NOT touch `ProfileEffect.ShowComingSoon`/`comingSoonBlock`.
- **Dead-string cleanup** — after the flip, the per-post block-coming-soon strings are unused. Remove `R.string.postdetail_snackbar_overflow_block_coming_soon` and `R.string.profile_snackbar_post_overflow_block_coming_soon` from `values/` + `values-b+es+419/` + `values-pt-rBR/` (avoids Android lint `UnusedResources`); set each `InteractionStrings.blockComingSoon` field to an existing coming-soon string (it is never displayed now since block navigates — reuse e.g. `unblockComingSoon`'s value). Verify `:feature:postdetail:impl:lintProductionDebug` + `:feature:profile:impl:lintProductionDebug` (no `UnusedResources`, no `MissingTranslation`).
- **Surface attribution:** `bind(PostSurface.PostDetail, …)` so `interact_post`/`share` carry `source_surface=postdetail`. PII-free enums.
- Conventional Commits, **lowercase subject** (commitlint rejects capitalized/"PR4" leads); `Refs: nubecita-58dy`; no Co-Authored-By/Claude-Session footer; never `--no-verify`. `:feature:*:impl`/`:app` flavored → use `:testProductionDebugUnitTest` etc. Pre-commit hook is SLOW (>2min) — let it finish.

---

### Task 1: Migrate `PostDetailViewModel` + `PostDetailScreen` (byte-identical, block→real)

**Files:**
- Modify: `feature/postdetail/impl/.../PostDetailViewModel.kt` (ctor, `by handler`, `bind`, tap-marker collector, `onOverflowAction` override = mute/unmute only, retire inline handlers)
- Modify: `feature/postdetail/impl/.../PostDetailContract.kt` (retire `OnLikeClicked/OnRepostClicked/OnShareClicked/OnShareLongPressed` events + `ShowComingSoon/SharePost/CopyPermalink` effects; KEEP `OnOverflowAction` event routed to the override, KEEP `ShowError` + the Navigate* effects)
- Modify: `feature/postdetail/impl/.../PostDetailScreen.kt` (`rememberPostInteractions` + `callbacks` + shrink effect collector; drop dead string/clipboard vars)
- DO NOT MODIFY: `PostDetailScreenContent.kt` / any `ui/*Content` (byte-identical)
- Strings: remove `postdetail_snackbar_overflow_block_coming_soon` (values + es + pt)
- Test: `feature/postdetail/impl/src/test/.../PostDetailViewModelTest.kt` + add `FakePostInteractionHandler.kt` (mirror profile's)

**Interfaces — Consumes:** `PostInteractionHandler` (`bind`, by-delegated methods, `tapMarkers`, `interactionEffects`), `PostSurface.PostDetail`, `Block.forAccount`/`Report.forPost`. **THE reference is `ProfileViewModel`/`ProfileScreen` (on main, PR3)** — read them; post-detail mirrors profile's migration almost exactly (flag-flip mute, tap-marker mirror, atomic shape) EXCEPT block delegates (real) instead of being overridden to coming-soon.

- [ ] **Step 1 — failing/updated tests.** Add `FakePostInteractionHandler` to post-detail's test source set (copy profile's). Update the VM test factory to drop `postInteractionsCache`+`analytics`, inject the fake handler; keep `FakeMuteRepository`. **Delete** the moved tests (like/repost `toggleLike`+`InteractPost`+failure, the `cache emission projects onto Focus/Reply` merge test, share/copy analytics — they live in `DefaultPostInteractionHandlerTest`). **Keep** the mute/unmute flag-flip + rollback tests (lines ~576–725). **FLIP** the overflow test (line ~500): remove `BlockAuthor` from the coming-soon `stubbedVariants`; **add** `OnOverflowAction(BlockAuthor) → InteractionEffect.NavigateToBlock` on `vm.interactionEffects`; flip the `ReportPost` test to assert `NavigateToReport(post)` on `vm.interactionEffects`. **Add** a tap-marker-mirror test + a delegated-coming-soon batch test (Unblock/MuteThread/UnmuteThread/CopyText → `ShowComingSoon` on `interactionEffects`). Watch RED.
- [ ] **Step 2 — run, watch fail** (`:feature:postdetail:impl:testProductionDebugUnitTest`).
- [ ] **Step 3 — implement the VM.** Ctor: keep `muteRepository`; drop `postInteractionsCache`+`analytics` (verify analytics has no non-interaction use — unlike profile, post-detail likely has none); add `handler`. Header `… , PostInteractionHandler by handler`. `init`: `handler.bind(PostSurface.PostDetail, viewModelScope)` + `handler.tapMarkers` collector → `setState { copy(lastLikeTapPostUri = …, lastRepostTapPostUri = …) }` (mirror Profile). **Remove** the direct `postInteractionsCache.state` collector (the handler owns the cache now) + the `applyInteractions`/cache imports IF nothing else uses them. Retire the inline `onLike/onRepost/onShare/onShareLongPress` handlers + those events/effects. **`onOverflowAction` override:** `MuteAuthor`/`UnmuteAuthor` → existing flag-flip `updateMutedByAuthor` + `muteRepository` + rollback (keep); **else → `handler.onOverflowAction(post, action)`** (so `BlockAuthor`→real `NavigateToBlock`, `ReportPost`→real `NavigateToReport`, the rest→`ShowComingSoon`). No block special-case — that's the whole point.
- [ ] **Step 4 — implement the screen.** `PostDetailScreen`: add `val interactions = rememberPostInteractions(handler = viewModel, snackbarHostState, strings = InteractionStrings(<existing postdetail R.string.* — error/link-copied/clip + the delegated coming-soons; set blockComingSoon to a reused existing string since block now navigates>), onInteractionError = { haptics.rejected() })`; `val callbacks = remember(viewModel, haptics, context, interactions.callbacks) { interactions.callbacks.copy(onLike/onRepost haptics-wrapped → viewModel.onLike/onRepost; onShare/onShareLongPress → viewModel.on*; onReply/onQuote keep the existing bypass via currentOnReplyClick/currentOnQuoteClick; onTap/onAuthorTap/onOverflowAction routed as today) }`. **Shrink** the effect collector: remove the `ShowComingSoon`/`SharePost`/`CopyPermalink` arms entirely; KEEP `ShowError` + the Navigate* arms (wrap any host nav callbacks captured in the long-lived `LaunchedEffect(Unit)` in `rememberUpdatedState`, like ProfileScreen). Remove the dead `overflowBlockComingSoon` + other moved string vars + `clipboardManager`. Add `:core:post-interactions-ui` to build.gradle if absent; `:app:checkSortDependencies`.
- [ ] **Step 5 — green** (`:feature:postdetail:impl:testProductionDebugUnitTest` + `compileProductionDebugKotlin` + `validateProductionDebugScreenshotTest` 0-change + `lintProductionDebug`).
- [ ] **Step 6 — commit** `refactor(postdetail): migrate onto PostInteractionHandler; block→real\n\nRefs: nubecita-58dy`

---

### Task 2: Close block parity in profile (flip the PR3 override)

**Files:**
- Modify: `feature/profile/impl/.../ProfileViewModel.kt` (remove the `BlockAuthor` coming-soon override arm → falls through to `handler.onOverflowAction` → real)
- Modify: `feature/profile/impl/.../ProfileScreen.kt` (remove the dead `postOverflowBlock`/`currentPostOverflowBlock` + the `ShowPostOverflowComingSoon` effect-collector arm; update the stale `blockComingSoon` comment)
- Modify: `feature/profile/impl/.../ProfileContract.kt` (retire `ProfileEffect.ShowPostOverflowComingSoon` — no emitter remains; KEEP `ProfileEffect.ShowComingSoon` for the hero `StubbedAction.Block`)
- Strings: remove `profile_snackbar_post_overflow_block_coming_soon` (values + es + pt); point `InteractionStrings.blockComingSoon` at a reused existing string
- Test: `feature/profile/impl/src/test/.../ProfileViewModelTest.kt` — flip the `BlockAuthor` test from `ShowPostOverflowComingSoon` on `vm.effects` → `InteractionEffect.NavigateToBlock` on `vm.interactionEffects`

- [ ] **Step 1 — update the test.** Flip the profile `BlockAuthor` overflow test (~line 904): now asserts `NavigateToBlock(did, handle)` on `vm.interactionEffects`, not `ShowPostOverflowComingSoon` on `vm.effects`. Keep the hero `StubbedAction.Block` test (separate, unchanged). Watch RED.
- [ ] **Step 2 — implement.** Remove the `PostOverflowAction.BlockAuthor →` arm from `ProfileViewModel.onOverflowAction` (it falls through to `else → handler.onOverflowAction`). In `ProfileScreen`, delete `postOverflowBlock`/`currentPostOverflowBlock` + the whole `is ProfileEffect.ShowPostOverflowComingSoon →` arm. Retire `ProfileEffect.ShowPostOverflowComingSoon` from the contract. Remove the dead string (3 locales); set `blockComingSoon` to a reused string + fix the comment.
- [ ] **Step 3 — green** (`:feature:profile:impl:testProductionDebugUnitTest` + `validateProductionDebugScreenshotTest` 0-change + `lintProductionDebug` + `compileProductionDebugKotlin`).
- [ ] **Step 4 — commit** `refactor(profile): block→real (close block parity)\n\nRefs: nubecita-58dy`

---

## Final verification (before PR)
- [ ] `:feature:postdetail:impl` + `:feature:profile:impl` suites + screenshot validate (**0 baseline changes** both) + lint (no UnusedResources/MissingTranslation) green; `:app:assembleProductionDebug` + `:app:assembleBenchDebug` link; spotless + checkSort clean. Root `testDebugUnitTest` (catch any cross-module fake/interface breakage).
- [ ] **Bench smoke** (`:app:installBenchDebug`, emulator-5554) — the parity proof:
  - **Post-detail:** open a thread → tap focus post's **like** (fills, +1, sticks) + **overflow → Block** → **the Block dialog/route opens** (real, NOT a "coming soon" snackbar) + **Mute** (flag-flip) + **Report** (dialog) — no FATAL.
  - **Profile:** open a profile → **overflow → Block** → **the Block route opens** (real now, NOT "coming soon" — the PR3 behavior is intentionally flipped).
- [ ] Compose gate: both screen rewires add `@Composable` wiring → run compose-expert (callbacks keyed on `interactions.callbacks`; disjoint collectors; nav callbacks `rememberUpdatedState`'d).
- [ ] Whole-branch review (opus) — both migrations + the parity flip; atomic tap-marker mirror; mute rollback; no double-collect; block-real on both surfaces; byte-identical screenshots; dead strings gone.
- [ ] PR opens WITHOUT `update-baselines` + `Refs: nubecita-58dy`. **This PR closes the epic** — the PR body notes feed/search/profile/post-detail now share one interaction path with uniform capabilities.
- [ ] Acceptance: post-detail interactions all work via the shared handler (inline duplicate gone); **block is real on all four surfaces**; mute/report unchanged; `source_surface=postdetail`; screenshots byte-identical; epic nubecita-58dy complete (PR4 of 4).

## Closes
`nubecita-tgqv` (PR4). Completes epic `nubecita-58dy`. (Independent follow-up `nubecita-w4o9` — align the feed's `cache.state` merge to the atomic in-setState form — remains open; `nubecita-wogi` — mute success feedback in search — also open.)
