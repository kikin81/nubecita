## Context

Nubecita never asks for ratings, so it sits at 2 reviews despite ~1.66K acquisitions; recent installs skew to regions with low 7-day retention. We want to prompt **retained, engaged** users at a delight moment using Google's native In-App Review API, without ever interrupting or annoying. The API has hard constraints baked into this design: Google decides whether a dialog actually shows (opaque quota, ~once/month), the card must not be modified or pre-gated with "do you like the app?" questions, and a button-triggered request can render nothing once the quota is hit. The implementation mirrors the existing `:core:billing` SDK-boundary pattern (Activity passed from the Composable, inert in the bench flavor).

## Goals / Non-Goals

**Goals:**
- Native in-app review request fired after a successful post publish, gated to retained users.
- SDK-agnostic boundary: no Play Core type leaks past `:core:review`.
- Fully fail-silent; never blocks Main; inert in keyless/bench builds.
- A manual Play-Store-listing entry point in Settings for users who proactively want to rate.
- High unit-test coverage of the eligibility policy and orchestration.

**Non-Goals:**
- Custom rating UI, "enjoying the app?" pre-prompts, or any modification of Google's card (prohibited by the API guidelines).
- Triggering from other moments (feed "caught up", follow, etc.) — post-publish only for v1.
- Tracking whether the user actually submitted a review (the API never reports this).
- Server-side or remote-config control of the gate (constants are compiled in for v1).

## Decisions

**D1 — Dedicated `:core:review` module over folding into `:core:preferences`/composer.**
Mirrors `:core:billing`: a self-contained capability with its own SDK boundary, flavor split, and state. Keeps Play Core out of feature modules and makes the boundary unit-testable. *Alternatives:* put it in `:core:preferences` (leaks a Play SDK dep into a shared module; mixes concerns) or in `:feature:composer:impl` (couples Play + eligibility to one feature, not reusable for the Settings row, puts an Activity-bound launch near a ViewModel). Rejected.

**D2 — Public boundary is `suspend fun onPostPublished(activity: Activity)`; Activity comes from the Composable.**
Matches billing design D5 — the ViewModel has no Activity handle and must never hold one. The composer Composable already collects `ComposerEffect.OnSubmitSuccess` and passes `LocalActivity.current`. *Alternative:* inject Activity / hold it in the VM — rejected (leaks, lifecycle mismatch).

**D3 — Launch on the host Activity's `lifecycleScope`, not composer/viewModel scope.**
The composer route pops immediately on success, cancelling any `rememberCoroutineScope`/`viewModelScope` mid-`await`. The Activity outlives the pop, so the review flow completes. *Alternative:* keep the composer composed until the flow returns (bad UX) or an app-scoped singleton scope (works but the Activity scope is the natural lifetime and already in hand).

**D4 — Conservative eligibility owned by a pure `ReviewPolicy` + DataStore counters.**
`isEligible = posts ≥ 3 ∧ (now − firstLaunch) ≥ 3d ∧ requestCount < 3 ∧ (lastRequested == null ∨ (now − lastRequested) ≥ 90d)`. The predicate is a pure function over a `ReviewState` snapshot + `now` (from `:core:common`'s clock), so the full truth table is unit-testable with zero IO. Day math uses **exact `Duration`** (e.g. `Duration.ofDays(3)`), not calendar days, to stay timezone/region/reinstall-safe. *Alternative:* calendar-day math (fragile across timezones); remote-config thresholds (overkill for v1).

**D5 — Record the attempt on `requestReviewFlow` success, before `launchReviewFlow`.**
Google's quota keys on the *request*, and the API never tells us if a dialog showed or if the user rated — so the cooldown/lifetime cap must count *requests we made*. A `ReviewClient` seam exposes `requestReview` + `launchReview` separately so the manager can record between them: `requestReview` failure → silent, **not** recorded (retries on a later eligible post, no storm); `launchReview` failure → silent, **recorded** (attempt consumed). *Alternative:* a single `requestAndLaunch` recording on completion — can't distinguish the two failure modes.

**D6 — IO confinement + fail-silent.**
`DefaultReviewManager` wraps its prefs reads/writes and Play orchestration in `withContext(@IoDispatcher)` (matching `DefaultModerationRepository`), so nothing blocks Main right after the post animation. Every Play/DataStore interaction is `try/catch` → `Timber.d`; an eligibility read failure is treated as not-eligible. No user-visible error path exists.

**D7 — Bench inertness via flavor split.**
`src/production/ReviewModule` binds `DefaultReviewManager` + `PlayReviewClient`; `src/bench/ReviewModule` binds a no-op `BenchFakeReviewManager`. `firstLaunchAt` is stamped only by the production `AppInitializer` (alongside `RevenueCatInitializer`); bench never registers it. Result: macrobench/keyless builds make zero Play calls and never prompt — same guarantee billing gives.

**D8 — Settings "Rate Nubecita" uses a Play-Store deep link, not the in-app API.**
Per Google: a button must not trigger the in-app flow (quota may render nothing → broken UX); redirect to the store instead. `PlayStoreLauncher` opens `market://details?id=<release appId>` and falls back to the https listing on `ActivityNotFoundException`. Uses the **release** applicationId constant, not runtime `packageName` (which carries debug suffixes). The pure `playStoreListingIntent(pkg)` helper is unit-tested.

**D9 — Reuse `kotlinx-coroutines-play-services` for `Task.await()`; no `review-ktx` in main.**
It's already in the catalog, so main needs only `com.google.android.play:review`. `review-ktx` is added as `testImplementation` solely for Google's `FakeReviewManager`, used to verify `PlayReviewClient`.

## Risks / Trade-offs

- **Opaque Play quota** → Our gate only decides *when we're willing to ask*; the design assumes a dialog may never appear and is fail-silent, so this is benign. We never build UX that depends on the dialog showing.
- **Counters not synced across devices/reinstall** → Acceptable: re-prompting a reinstalled user after they re-cross the 3-post/3-day gate is fine, and the lifetime cap + cooldown bound it. No cross-device sync needed for v1.
- **`onPostPublished` running on Activity scope after composer pop** → Bounded, short-lived `await` on an Activity that is still foreground (the user just posted); if the Activity dies, the coroutine cancels harmlessly (CancellationException propagates, nothing recorded inconsistently because the record happens synchronously between the two awaits).
- **First Play Core dependency** → Small, Google-maintained, already implicitly available via Play; gated behind the module boundary so it can be swapped/removed in one place.
- **We can't measure conversion** → The API gives no submission signal; success is judged indirectly via the Play Console ratings count over time.

## Migration Plan

Additive only — no data migration, no breaking changes. Rollout: ship the module + composer hook + Settings row behind the normal release. Rollback is a code revert (no persisted schema beyond four additive DataStore keys, which are ignored if the feature is removed). The bench flavor is unaffected at all times.

## Open Questions

- Exact latest `com.google.android.play:review` version to pin (currently 2.0.2 on Google Maven) — confirm at implementation.
- Confirm `LocalActivity.current` is available/non-null in the composer's host (Compose 1.x `LocalActivity`) vs. resolving the Activity from `LocalContext`; pick whichever the codebase already uses for the paywall purchase Activity.
