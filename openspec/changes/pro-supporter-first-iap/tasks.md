# Tasks: Nubecita Pro — first in-app purchase

> Beads epic: **nubecita-q5ge**. Each task group below maps 1:1 to a child issue; the beads id
> is the source of truth for status, the OpenSpec checkboxes track in-PR progress.
> Sequence is enforced by `blocks` edges in beads.

## 1. `:core:billing` skeleton — beads nubecita-q5ge.1

- [x] 1.1 Create `:core:billing` module (single module: `public` interfaces + `internal` impls + `@Binds` module, per `:core:*` convention — see design Open Questions resolution) + wire into `settings.gradle.kts` and convention plugins
- [x] 1.2 Define `EntitlementRepository` (`isPro: StateFlow<Boolean>`, `refresh()`) and `BillingRepository` (`loadPlans`, `purchase`, `restorePurchases`) interfaces — pure Kotlin, no provider types
- [x] 1.3 Add `:data:models` subscription types (`SubscriptionOffering`, `SubscriptionPlan`, `SubscriptionPlanId`, `BillingPeriod`) as `@Immutable` + fixtures
- [x] 1.4 Provide a fake/in-memory `impl` for downstream builds and tests
- [x] 1.5 Unit tests: fake `EntitlementRepository` emits `isPro` transitions; offering model mapping (per-month equiv, savings %)

## 2. RevenueCat impl — beads nubecita-q5ge.2

- [x] 2.1 Add `com.revenuecat.purchases:purchases` (pinned **10.7.0** — latest major, chosen over 9.x; Play Billing 8.3.0) to `libs.versions.toml`; API key via `BuildConfig.REVENUECAT_API_KEY` (gradle property + empty default)
- [x] 2.2 `Purchases.configure` via a production-flavor `AppInitializer` (keeps bench network-silent) with anonymous `appUserID(null)` (no `logIn(did)`)
- [x] 2.3 Implement `EntitlementRepository` impl: bridge `CustomerInfo`/`UpdatedCustomerInfoListener` → `isPro: StateFlow<Boolean>` for the `pro` entitlement
- [x] 2.4 Implement `BillingRepository` impl: map `Offerings` → `SubscriptionOffering`; `purchase(activity, plan)`; `restorePurchases()`
- [x] 2.5 Hilt bindings swapping the fake for the RevenueCat impl
- [x] 2.6 Unit tests over the SDK boundary (faked): entitlement mapping, offering mapping, purchase result types. **Sandbox/license-tester smoke (instrumented, `run-instrumented`) deferred** — needs a real `goog_…` key + the Play base plans + RevenueCat dashboard entitlement/offering from task 11; tracked as a follow-up.

## 3. `PipController` choke-point — beads nubecita-q5ge.3

- [x] 3.1 Add `PipController` in `:core:video`: `isEnabled = deviceSupports && isPro` as `StateFlow` (seeded from current `isPro`), plus `isInPip` field + `setInPip`
- [x] 3.2 `Context.supportsPip()` feature-detect (SDK ≥ 26 + `FEATURE_PICTURE_IN_PICTURE`), provided as a `@PipDeviceSupport` boolean so the controller stays platform-free/testable
- [x] 3.3 Unit tests: `isEnabled` truth table (device × pro); reacts to `isPro` changes; `isInPip` default + set

## 4. Manifest PiP attrs + Activity PiP bridge — beads nubecita-q5ge.4

- [x] 4.1 `MainActivity` manifest: add `supportsPictureInPicture` + `configChanges` (kept `singleTask`, `adjustResize`)
- [x] 4.2 Activity `updateParams` (aspect via clamped `Rational`, source-rect hint, play/pause action, auto-enter); API 31+ auto-enter, API 26–30 `onUserLeaveHint` fallback — in `ActivityPipBridge`
- [x] 4.3 `onPictureInPictureModeChanged` → set `PipController.isInPip`; pause-on-dismiss flows through the (soon PiP-aware, q5ge.5) `SharedVideoPlayer.onStop` observer per design D6
- [x] 4.4 Play/pause `RemoteAction` + `RECEIVER_NOT_EXPORTED` broadcast receiver → `SharedVideoPlayer`
- [x] 4.5 Instrumented test: PiP transition does NOT recreate `MainActivity` (`run-instrumented`; written, runs under the label on a PiP-capable device)

## 5. `SharedVideoPlayer` `isInPip` seam — beads nubecita-q5ge.5 (load-bearing)

- [x] 5.1 Inject `isInPip: () -> Boolean` (default `{ false }`) into `SharedVideoPlayer` + `createSharedVideoPlayer`; gate `appBackgroundObserver.onStop` auto-pause on it. Production wires `{ pipController.isInPip.value }` via `VideoPlayerModule` (no Hilt cycle — `PipController` doesn't depend on `SharedVideoPlayer`)
- [x] 5.2 Unit test: `onStop` auto-pause is suppressed when `isInPip()` true; still pauses when false (default-`{ false }` path covered by the existing `appBackgrounded_onStop_pausesPlayingVideo`)

## 6. PiP Compose wiring — beads nubecita-q5ge.6

- [ ] 6.1 `rememberIsInPipMode()` helper (AndroidX `addOnPictureInPictureModeChangedListener`)
- [ ] 6.2 Hide player chrome + system bars in PiP; capture source-rect via `onGloballyPositioned`
- [ ] 6.3 `MainShell` suppresses `NavigationSuiteScaffold` (`NavigationSuiteType.None`) in PiP
- [ ] 6.4 `LocalPipController` CompositionLocal; `LaunchedEffect` publishes params keyed on `isPlaying`/aspect/`isEnabled`
- [ ] 6.5 Screenshot test: video screen in PiP (chrome hidden) vs normal

## 7. `:feature:paywall` module — beads nubecita-q5ge.7

- [ ] 7.1 Create `:feature:paywall` (api/impl); `@MainShell` `PaywallRoute` NavKey + entry provider
- [ ] 7.2 `PaywallViewModel : MviViewModel<PaywallState, PaywallEvent, PaywallEffect>` — `PaywallStatus` sealed (Loading/Ready/Error), offering, selected plan (annual default)
- [ ] 7.3 Paywall Composable with locked copy, perks, plan picker (prices from offering), CTA "Become a Supporter", disclosure block + Terms/Privacy/Restore
- [ ] 7.4 Purchase flow: Composable passes Activity to `billing.purchase`; errors → `ShowError` effect; success dismisses
- [ ] 7.5 Unit tests: reducer (load→ready, plan select, purchase success/cancel/error). Screenshot tests: light/dark × selected plan × loading × error

## 8. Pop-out upsell affordance — beads nubecita-q5ge.8

- [ ] 8.1 Add explicit pop-out affordance to fullscreen video chrome (shown to all)
- [ ] 8.2 Non-Pro tap → navigate to `PaywallRoute`; Pro tap → enter PiP
- [ ] 8.3 Unit/UI test: non-Pro routes to paywall; Pro enters PiP

## 9. Supporter badge (Tier 1) — beads nubecita-q5ge.9

- [ ] 9.1 Render Supporter badge in `:feature:profile` hero gated on `isPro` (own profile only); neutral wording (no "verified")
- [ ] 9.2 Screenshot test: profile hero with and without badge

## 10. Settings Pro section — beads nubecita-q5ge.10

- [ ] 10.1 Add Nubecita Pro section in `:feature:settings`: non-Pro row → paywall; Pro shows plan + manage-subscription Play deep link + restore
- [ ] 10.2 Unit test: section state for Pro vs non-Pro. Screenshot test: both states

## 11. Play Console / Data-safety / legal — beads nubecita-q5ge.11

- [ ] 11.1 Create Play base plans `monthly` ($1.99) / `annual` ($19.99); map to `pro` entitlement + one Offering in RevenueCat; Play Developer API service account
- [ ] 11.2 Update Data-safety form (purchase history + IDs; RevenueCat as processor; deletion path)
- [ ] 11.3 In-app + listing Terms of Service + Privacy Policy links; verify live Play subscription/disclosure policy wording before submission
- [ ] 11.4 Confirm "In-app purchases" listing badge + 15% service-fee tier; payments profile/tax verified
