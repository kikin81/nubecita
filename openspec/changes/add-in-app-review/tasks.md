## 1. Module scaffold & dependencies

- [ ] 1.1 Create `:core:review` module (apply `nubecita.android.library` + `nubecita.android.hilt`; namespace `net.kikin.nubecita.core.review`) and register it in `settings.gradle.kts`
- [ ] 1.2 Add `com.google.android.play:review` (current: 2.0.2 — verify latest on Google Maven) to the version catalog; confirm `kotlinx-coroutines-play-services` is already present and reuse it
- [ ] 1.3 Wire `:core:review` deps: `review`, `coroutines-play-services`, `:core:common` (IoDispatcher, clock), Hilt; add `testImplementation com.google.android.play:review-ktx` for `FakeReviewManager`
- [ ] 1.4 Confirm the module participates in the `production`/`bench` product flavors (mirror `:core:actors`)

## 2. Eligibility state & policy

- [ ] 2.1 Add `ReviewState` (firstLaunchAt, successfulPostCount, requestCount, lastRequestedAt) and `ReviewPreferences` (own DataStore in `:core:review`): read snapshot + `incrementPostCount`, `recordReviewRequested(now)`, `stampFirstLaunchIfUnset(now)`
- [ ] 2.2 Add pure `ReviewPolicy` object with constants (MIN_POSTS=3, MIN_DAYS_SINCE_FIRST_LAUNCH=3, MAX_LIFETIME_REQUESTS=3, COOLDOWN_DAYS=90) and `isEligible(state, now)` using exact `Duration` math

## 3. Play seam & manager

- [ ] 3.1 Add internal `ReviewClient` interface (`requestReview(activity): ReviewHandle`, `launchReview(activity, handle)`) and `ReviewHandle` (internal wrapper over `ReviewInfo`)
- [ ] 3.2 Implement `PlayReviewClient` over `ReviewManagerFactory` using `Task.await()` (coroutines-play-services)
- [ ] 3.3 Add public `ReviewManager` interface (`suspend fun onPostPublished(activity: Activity)`) and `DefaultReviewManager`: increment → read state+now → `if (!eligible) return` → `requestReview` → record attempt → `launchReview` (try/catch); confine to `@IoDispatcher`; fail-silent (Timber.d); read failure ⇒ not eligible

## 4. DI, flavor split & bench inertness

- [ ] 4.1 `src/production/.../ReviewModule` — bind `DefaultReviewManager` + `PlayReviewClient`
- [ ] 4.2 `src/bench/.../ReviewModule` — bind no-op `BenchFakeReviewManager` (`onPostPublished` does nothing)
- [ ] 4.3 Stamp `firstLaunchAt` once in the production `AppInitializer` (alongside `RevenueCatInitializer`); ensure bench never registers it

## 5. Composer trigger

- [ ] 5.1 In the composer screen Composable, on `ComposerEffect.OnSubmitSuccess`, launch `reviewManager.onPostPublished(activity)` on the host Activity's `lifecycleScope` (resolve the Activity the same way the paywall purchase flow does); expose `ReviewManager` to the Composable without the VM launching it. No composer ViewModel/state changes.

## 6. Settings "Rate Nubecita" entry

- [ ] 6.1 Add `playStoreListingIntent(pkg)` + `PlayStoreLauncher.openListing(context)` in `:core:review` (market:// → https fallback on `ActivityNotFoundException`; release applicationId constant)
- [ ] 6.2 Add a "Rate Nubecita" row to the Settings About section in `:feature:settings:impl` that calls `PlayStoreLauncher.openListing` (relates to `nubecita-37to.7`)

## 7. Tests (unit + preview + screenshot per convention)

- [ ] 7.1 `ReviewPolicyTest` — eligibility truth table incl. boundaries (exactly 3 posts / 3 days / 90 days / 3rd request)
- [ ] 7.2 `DefaultReviewManagerTest` — fake `ReviewClient` + in-memory `ReviewPreferences`: ineligible⇒increment-only; eligible⇒launch+record; `requestReview` throws⇒silent, not recorded; `launchReview` throws⇒silent, recorded; within-cooldown⇒no launch
- [ ] 7.3 `ReviewClientTest` — verify `PlayReviewClient` wiring with Google's `FakeReviewManager`
- [ ] 7.4 `ReviewPreferencesTest` — counter increments, first-launch stamp idempotency, request recording
- [ ] 7.5 `PlayStoreLauncherTest` — intent action/data + https fallback
- [ ] 7.6 Settings row `@Preview` + screenshot baseline; UI test asserting the tap fires the listing intent

## 8. Wiring, validation & tracking

- [ ] 8.1 Run `./gradlew :core:review:testDebugUnitTest spotlessCheck lint :app:checkSortDependencies` and the relevant `:feature:settings:impl` checks; commit screenshot baselines
- [ ] 8.2 Verify bench build issues zero Play calls (assemble bench; confirm no `ReviewModule` production binding)
- [ ] 8.3 Branch `feat/nubecita-ijuv-in-app-review`; PR with `Closes: nubecita-ijuv`; add `update-baselines` and (if androidTest added) `run-instrumented` labels
