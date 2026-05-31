# Epic: Nubecita Pro — first in-app purchase (RevenueCat subscription)

**Date:** 2026-05-31
**Status:** Design — pending review
**bd epic:** _to be created_ (single epic, sequenced children)

## Summary

Introduce Nubecita's first paid offering: **Nubecita Pro**, an auto-renewing subscription
(monthly + annual) sold through Google Play Billing and mediated by **RevenueCat**. Pro
unlocks two perks in v1:

1. **Picture-in-Picture (PiP) video** — a pop-out video player.
2. **Supporter badge** — a self-visible badge on the signed-in user's own profile.

The framing is an honest indie "support the developer" pitch, not a feature-extraction
paywall: the entire free app stays fully usable, and moderation/safety features are never
gated.

This is delivered as **one bd epic with sequenced children**, landing the billing
foundation first and the perks on top, so the whole "Pro" launch ships as one coherent
release.

## Decisions locked during brainstorming

| # | Decision | Rationale |
|---|----------|-----------|
| 1 | **Single epic, sequenced children** | One coherent Pro launch; simpler to track than splitting foundation vs perks. |
| 2 | **Custom Compose paywall**, not RevenueCat's drop-in `purchases-ui` | The app's identity is hand-crafted native M3 Expressive UI at 120hz; the paywall is the one screen a generic "bought" look would undercut the craftsman-indie pitch. Live prices still come from RevenueCat `Offerings`. |
| 3 | **SDK-agnostic boundary** — no RevenueCat type leaks past `:core:billing` | Must be able to swap RevenueCat for a self-hosted server / Firebase later by rewriting one `impl` and touching nothing else. |
| 4 | **Pro bound to the Google Play account** (anonymous RevenueCat `appUserId`), **not** the Bluesky DID | Pro is a person/device capability. Avoids the deleted-DID reconciliation problem. Lets the same Google account use Pro on multiple devices via Restore. Keeps multi-account open as a *future* Pro perk. No DID sent to RevenueCat. |
| 5 | **Badge v1 = Tier 1, self-visible only, no backend** | Keeps the first paid release shippable with zero server dependency. A "Nubecita Supporter" badge is only ever rendered by Nubecita anyway, so a networked/labeler badge is deferred to a later child / fast-follow. |
| 6 | **Pricing: $1.99/mo + $19.99/yr (16% off, ~2 months free), no free trial, no lifetime** | Indie "support the dev" comps cluster here. A trial frames a *supporter* sub as transactional. Lifetime/tip-jar is a **separate future epic**. |
| 7 | **Copy: "Keep Nubecita flying" voice, open-source sub-line, "Become a Supporter" CTA, em-dash-free** | Honest to a public GitHub repo; supporter language is consistent across headline, badge, and CTA. |

## Non-goals (explicitly out of scope for this epic)

- **Networked / public supporter badge** (others see it), ATProto Labeler — deferred (Tier 2/3).
- **Multiple Bluesky accounts** — a *future* Pro perk, not this epic.
- **Tip jar / one-time donation** — separate epic (genuine no-perk donations have a different
  Play-policy/payment story).
- **Lifetime / non-renewing product.**
- **Alternative billing / user-choice billing** (EEA DMA, etc.) — revisit only if fee economics
  justify it.
- **Media3 `MediaSession` migration** for unified PiP + lockscreen/notification controls — a
  worthwhile fast-follow, but a larger `SharedVideoPlayer` refactor; not required for PiP v1.
- **PiP playback-position persistence across process death** — already deferred in the
  fullscreen-video design; restarting at 0:00 after process death is acceptable for v1.

## Architecture

### Module boundary — `:core:billing` (api/impl split)

Mirrors the feature `api`/`impl` convention so the RevenueCat SDK is fully swappable.

```
core/billing/                 (or split api/impl if a pure-Kotlin api module is wanted)
  api  — pure-Kotlin interfaces + our own domain models (no RevenueCat, no Android-UI types)
  impl — RevenueCat SDK wiring, Hilt bindings
```

**Public surface (the only thing the rest of the app sees):**

```kotlin
// :core:billing api — SDK-agnostic
interface EntitlementRepository {
    /** Single source of truth for "does this user have Pro right now?". */
    val isPro: StateFlow<Boolean>
    suspend fun refresh()
}

interface BillingRepository {
    /** Our domain offerings, populated from the billing SDK at runtime. */
    suspend fun loadPlans(): Result<SubscriptionOffering>
    /** Launches the Play purchase flow for a plan. Returns updated entitlement. */
    suspend fun purchase(activity: Activity, plan: SubscriptionPlan): Result<Boolean>
    suspend fun restorePurchases(): Result<Boolean>
}
```

**Our domain models live in `:data:models`** (`@Immutable`, no Compose-ui dep), e.g.:

```kotlin
@Immutable data class SubscriptionOffering(
    val monthly: SubscriptionPlan,
    val annual: SubscriptionPlan,
)
@Immutable data class SubscriptionPlan(
    val id: SubscriptionPlanId,           // Monthly | Annual
    val priceFormatted: String,           // "$1.99" — localized, from the store
    val period: BillingPeriod,            // Monthly | Annual
    val perMonthEquivalent: String?,      // "$1.67/mo" for annual
    val savingsPercent: Int?,             // 16
)
```

No `Package`, `StoreProduct`, `CustomerInfo`, or `Offerings` type ever crosses the `:core:billing`
boundary. The paywall, PiP gate, badge, and settings consume only the types above.

### RevenueCat impl details (inside `:core:billing` impl only)

- **Artifact:** `com.revenuecat.purchases:purchases` (v9.x line; v9 supports Google Play Billing
  Library 8, min Kotlin 1.8 which we clear on Kotlin 2.3). We do **not** add `purchases-ui`
  (custom paywall). _Pin the exact current version in `libs.versions.toml` at implementation time._
- **Init:** `Purchases.configure(...)` in `Application.onCreate` (SDK requirement). The
  `EntitlementRepository`/`BillingRepository` Hilt bindings wrap the configured singleton; init
  itself is not Hilt-managed.
- **Identity (decision #4):** use RevenueCat's **anonymous `appUserId`**. We do **not** call
  `logIn(did)`. The Play purchase attaches to the anonymous ID / Google account; `restorePurchases()`
  re-associates the Play receipt on a new device. No DID is sent to RevenueCat.
- **Entitlement:** one RevenueCat entitlement, id `pro`. `isPro` is derived from
  `CustomerInfo.entitlements["pro"]?.isActive == true`, bridged to a `StateFlow<Boolean>` via an
  `UpdatedCustomerInfoListener` so Compose recomposes reactively on purchase/refund/expiry.
- **Offerings → our models:** map the current `Offering`'s monthly/annual packages into
  `SubscriptionOffering`, reading localized price strings from the store products. Compute
  `perMonthEquivalent` and `savingsPercent` from the two prices.
- **API-key handling:** RevenueCat public SDK key via `BuildConfig`/manifest, same pattern as the
  existing Firebase keys.

### Entitlement gating — the `PipController` choke-point

Per the PiP research, gating goes through exactly **one** boolean so the ~4 PiP call sites never
each branch on entitlement:

```kotlin
@Singleton
class PipController @Inject constructor(
    @ApplicationContext context: Context,
    entitlements: EntitlementRepository,
) {
    private val deviceSupports = context.supportsPip()    // hasSystemFeature + SDK>=26, evaluated once
    /** PiP is live iff device-capable AND the user has Pro. The only flag the PiP code reads. */
    val isEnabled: StateFlow<Boolean> =
        entitlements.isPro.map { deviceSupports && it }.stateIn(appScope, Eagerly, false)
    var isInPip: Boolean = false   // set by the Activity bridge; read by SharedVideoPlayer seam
}
```

The badge reads `EntitlementRepository.isPro` directly (it has no device-capability dimension).

## Feature design

### 1. Picture-in-Picture video (Pro-gated)

Grounding: `:core:video/SharedVideoPlayer` is a **process-scoped single `ExoPlayer`** (not owned by
the Activity or any Composable). This already solves the hard part of PiP — the player survives the
window transition. The work is mostly chrome + params + one lifecycle gate. Builds directly on
`docs/superpowers/specs/2026-05-16-fullscreen-video-player-design.md`, which already named this
follow-up.

- **Manifest (`MainActivity`):** add `android:supportsPictureInPicture="true"` and
  `android:configChanges="screenSize|smallestScreenSize|screenLayout|orientation|keyboardHidden"`.
  The `configChanges` set is **load-bearing**: without it the OS destroys/recreates `MainActivity`
  on every PiP transition, tearing down the Nav3 backstack. Keep existing `launchMode="singleTask"`
  and `windowSoftInputMode="adjustResize"`.
- **Entry model:** target the **API 31+ auto-enter flow** (`setAutoEnterEnabled`) as primary —
  publish `PictureInPictureParams` (aspect ratio from `SharedVideoPlayer.videoAspectRatio`, source
  rect hint from the player `Box` via `onGloballyPositioned`, play/pause `RemoteAction`), and the OS
  enters PiP on the home gesture. `autoEnter = pipController.isEnabled && isPlaying`. API 26–30
  fallback: manual `enterPictureInPictureMode()` in `onUserLeaveHint()` gated on the same flag.
- **Compose:** `rememberIsInPipMode()` (official AndroidX `addOnPictureInPictureModeChangedListener`
  helper). While in PiP, hide all chrome (player controls, system bars) **and** collapse
  `MainShell`'s `NavigationSuiteScaffold` to `NavigationSuiteType.None` (reuse the existing
  sub-route override).
- **MVI boundary:** the ViewModel must **not** touch the Activity or PiP APIs. PiP is an
  Activity/Compose-runtime concern (like `LocalMainShellNavState` and the tab-retap signal). Expose
  an Activity-side `PipController`/bridge via a `CompositionLocal` (`LocalPipController`); the screen
  Composable reads VM state (`isPlaying`, `videoAspectRatio`) and drives the bridge. **No Activity
  reference is injected into any Hilt ViewModel.**
- **The load-bearing lifecycle change:** `SharedVideoPlayer.appBackgroundObserver` currently pauses
  the player on `ProcessLifecycleOwner.onStop`. In PiP that would stop the just-popped-out video.
  Gate it: inject an `isInPip: () -> Boolean` supplier (default `{ false }`); skip the auto-pause
  when in PiP. Keeps `:core:video` Activity-free. On a real dismiss (swipe the PiP window away),
  `onPictureInPictureModeChanged(false)` while stopping → pause (that *is* a real stop).
- **Controls:** play/pause `RemoteAction` backed by a `PendingIntent` broadcast
  (`FLAG_IMMUTABLE`), received by `MainActivity` → `SharedVideoPlayer.play()/pause()`. Re-publish
  params whenever `isPlaying` flips to swap the icon. A PiP window with no working controls looks
  broken; this is table stakes, not polish.
- **Graceful degradation:** `supportsPip()` false (device lacks `FEATURE_PICTURE_IN_PICTURE`, user
  disabled per-app PiP, or pre-26) → no pop-out affordance, fullscreen behaves exactly as today.
  No crash, no toast. PiP is purely additive.
- **Upsell:** an explicit "pop out" affordance in the fullscreen chrome, shown to everyone; non-Pro
  taps route to the paywall (value made tangible at the moment of intent) rather than entering PiP.
  Do **not** fire an upsell interstitial on the home gesture (would feel like the OS broke).

### 2. Supporter badge (Tier 1 — self-visible, Pro-gated)

- **Behavior:** when `EntitlementRepository.isPro` is true, render a "Supporter" badge on the
  signed-in user's **own** profile header, visible only to them in their own app. No network write,
  no AT Protocol record, no backend, no abuse surface.
- **Placement:** a small badge chip/icon in the profile hero of `:feature:profile`, next to the
  display name. Uses the design system; per surface-role conventions, no new color tokens unless a
  brand "supporter" accent is explicitly chosen during build.
- **Copy:** badge label "Supporter". Tooltip/microcopy avoids any "verified"/official-Bluesky
  connotation (Play UGC + trademark caution): this is a Nubecita supporter mark, not a network
  verification.
- **Forward-compatibility:** Tier 2 (networked, other Nubecita users see it) is a later child. When
  it lands, the chosen badged DID is recorded via a RevenueCat **subscriber attribute**
  (`badge_did`) verified server-side; the Tier 1 rendering composable is reused unchanged.

### 3. Paywall (custom Compose)

- **New `:feature:paywall` module** (api/impl) — **decided.** The paywall is its own module rather
  than living in `:feature:settings:impl` so the upsell can be triggered from anywhere in the app
  (PiP pop-out, settings, and future surfaces like a feed banner or onboarding) without depending on
  settings. `@MainShell` sub-route `NavKey` (`PaywallRoute`) in `:feature:paywall:api`.
- **MVI:** `PaywallViewModel : MviViewModel<PaywallState, PaywallEvent, PaywallEffect>`.
  - `PaywallState` exposes a flat, UI-ready projection: `offering: SubscriptionOffering?`,
    a mutually-exclusive `status: PaywallStatus` sealed sum (`Loading / Ready / Error`), and
    `selectedPlan: SubscriptionPlanId` (default Annual).
  - Purchase is a one-shot command: `viewModelScope.launch { billing.purchase(...) }` with inline
    `try/catch` → `PaywallEffect.ShowError`. On success, emit a navigation/close effect; the
    entitlement `StateFlow` propagates the unlock everywhere reactively.
  - The Activity reference required by Play's `purchase(activity, ...)` is passed from the
    Composable layer (not injected into the VM).
- **Content (locked copy, em-dash-free):**
  - Headline: **Keep Nubecita flying ☁️**
  - Sub-line (A): *Nubecita is an independent, open-source passion project. Going Pro keeps the
    app ad-free and actively maintained.*
  - Perks: `📼 Picture-in-Picture video` · `💙 Supporter badge on your profile` · `✨ Support the dev`
  - Plans: **Annual $19.99/yr — just $1.67/mo, save 16%** (hero, "BEST") and **Monthly $1.99/mo**.
    Prices rendered from the localized store values, not hardcoded.
  - CTA: **Become a Supporter**.
  - Required disclosure block directly under the CTA (Play subscription policy): *Auto renews until
    you cancel. Cancel anytime in Google Play.* + links **Terms · Privacy · Restore Purchases**.
- **Entry points:** (a) PiP pop-out tap by a non-Pro user; (b) a "Nubecita Pro" row in Settings;
  (c) optionally a one-time dismissible hint. All route to `PaywallRoute`.
- **Screenshot tests** for the paywall (light/dark, both plans selected, loading, error) per the
  testing conventions.

### 4. Settings — manage subscription

- In `:feature:settings:impl`, add a **Nubecita Pro** section:
  - Non-Pro: a row that opens the paywall.
  - Pro: shows current plan + a **Manage subscription** row deep-linking to Play
    (`https://play.google.com/store/account/subscriptions?sku=<id>&package=<pkg>`) and a
    **Restore purchases** row.
- Restore is also surfaced on the paywall. (Play has no hard "Restore" mandate, but it's expected
  UX and a support-ticket reducer; with anonymous IDs it also covers users who never bound to a
  Google account cleanly.)

## Data flow

```
Google Play Billing  ──►  RevenueCat SDK (impl only)
                               │  CustomerInfo / Offerings
                               ▼
        :core:billing  EntitlementRepository.isPro : StateFlow<Boolean>
                       BillingRepository (our SubscriptionOffering/Plan models)
                               │
            ┌──────────────────┼───────────────────────────┐
            ▼                  ▼                           ▼
   PipController.isEnabled   Profile badge (isPro)     Paywall (offering + purchase)
   (= deviceSupports && pro)                              Settings (manage/restore)
            │
            ▼
   Activity PiP bridge (params, RemoteAction, isInPip)  ◄─ LocalPipController (CompositionLocal)
            │
            ▼
   SharedVideoPlayer (skips onStop auto-pause when isInPip)
```

## Error handling

- **Entitlement check never blocks UI.** `isPro` starts `false` and flips when the SDK's cached
  `CustomerInfo` resolves on cold start. Features read the `StateFlow`; no synchronous gate.
- **Purchase errors** (user cancel, billing unavailable, network) → `PaywallEffect.ShowError`
  snackbar; no sticky error state. User-cancel is a no-op, not an error toast.
- **Offerings load failure** → `PaywallStatus.Error` with retry.
- **Entitlement loss mid-session** (refund/expiry): stop offering *new* PiP entries (auto-enter goes
  false reactively); do **not** yank an already-open PiP window. Badge disappears on next recompose.
- **Restore with nothing to restore** → friendly "No purchases found" message, not an error.

## Testing strategy

- **Unit (`:core:testing`, JUnit5 + Turbine + MockK):**
  - `PipController.isEnabled` folding (device-support × pro) truth table.
  - `EntitlementRepository` `StateFlow` emits on simulated `CustomerInfo` changes (fake the SDK
    boundary behind the interface).
  - `PaywallViewModel` reducer: load → ready, plan selection, purchase success/cancel/error effects.
  - Offering mapping: prices, `perMonthEquivalent`, `savingsPercent`.
  - `SharedVideoPlayer` seam: `onStop` auto-pause is suppressed when `isInPip()` is true.
- **Screenshot:** paywall (light/dark × selected plan × loading/error), profile with/without badge.
- **Instrumented (`run-instrumented` label):** PiP transition does not recreate `MainActivity`;
  chrome hides in PiP; nav suite collapses. License-tester / RevenueCat sandbox purchase smoke test.
- **Fake entitlements:** because gating is behind `EntitlementRepository`, tests/dev builds inject a
  fake `isPro` flow with no SDK dependency.

## Play Console / store / legal checklist (sequenced into the epic)

1. **Payments profile + tax info** verified in Play Console — *start first, it's the slowest step
   (identity/bank/tax verification can take days).* (User has enabled the payment profile.)
2. Create base plans `monthly` ($1.99) and `annual` ($19.99) and map them in RevenueCat to the `pro`
   entitlement and a single Offering. Use Play price templates for local-currency.
3. RevenueCat dashboard: `pro` entitlement, Offering with monthly+annual packages, Play service
   account credential for the Developer API.
4. **Data safety form:** declare Purchase history (collected, shared with RevenueCat as processor),
   Device/other IDs, encryption in transit, deletion path covering RevenueCat. Treat RevenueCat like
   the existing Firebase processors.
5. **Legal:** Privacy Policy (mandatory, in-app + listing) and Terms of Service linked on/near the
   paywall. Ensure they don't contradict statutory refund/withdrawal rights.
6. Store listing reflects **In-app purchases**; keep "independent/unofficial client" framing; do not
   imply Bluesky endorsement; never call the badge "verified."
7. Confirm Play **service fee 15%** tier (under $1M) applies.
8. Verify the live Google Play policy wording before submission (digital-goods requirement,
   subscription disclosures, Data safety) — these were summarized from durable policy structure, not
   fetched live during research.

## Compliance guardrails (must hold)

- The full free app stays usable; **moderation/safety (block/mute/report) is never gated.**
- Play Billing is mandatory here (the perks are digital goods); the "support" framing does not make
  it a donation. RevenueCat is a compliant layer over Play Billing, not an alternate processor.
- Paywall disclosures (price, period, auto-renew, how to cancel) are above the fold, with no dark
  patterns; CTA copy reflects reality (no "free" without a trial — and there is no trial).

## Proposed child-issue sequence (one epic)

1. **`:core:billing` skeleton** — api interfaces + `:data:models` types + fake impl + unit tests.
   (No RevenueCat yet; everything downstream can build against the interface.)
2. **RevenueCat impl** — Gradle artifact, `Purchases.configure` in `Application`, anonymous identity,
   `pro` entitlement → `isPro` StateFlow, Offerings mapping, purchase/restore. Sandbox smoke test.
3. **`PipController` choke-point** + unit tests (device-support × pro folding).
4. **Manifest PiP attrs + Activity PiP bridge** — params, auto-enter (31+) / `onUserLeaveHint`
   (26–30), RemoteAction broadcast, `onPictureInPictureModeChanged` → `isInPip`/dismiss-pause.
5. **`SharedVideoPlayer` `isInPip` seam** — gate the `onStop` auto-pause + unit test. *(load-bearing)*
6. **PiP Compose wiring** in videoplayer/mediaviewer — `rememberIsInPipMode`, chrome hiding,
   `MainShell` nav-suite suppression, source-rect-hint, `LaunchedEffect` param publishing.
7. **Custom paywall** (`:feature:paywall`) — MVI VM, locked copy, plan picker, purchase flow,
   disclosure block, screenshot tests.
8. **Pop-out upsell affordance** — non-Pro pop-out tap → paywall.
9. **Supporter badge (Tier 1)** in `:feature:profile` — `isPro` → self-visible badge + screenshot
   test.
10. **Settings Pro section** — paywall entry, manage-subscription deep link, restore.
11. **Play Console / Data-safety / legal** wiring + sandbox→production launch checklist.

## Deferred follow-ups (post-epic)

- Tier 2 networked supporter badge (RevenueCat webhook/REST verify → `badge_did` → Nubecita-client
  query). Possibly its own epic.
- Multiple Bluesky accounts as a Pro perk.
- Tip jar (separate epic).
- Media3 `MediaSession` for unified PiP + notification/lockscreen controls.
- Remote-config-driven paywall copy (recovers option A's "edit without release" benefit behind our
  own interface).
- PiP playback-position persistence.

## Resolved during review

- **Paywall lives in its own `:feature:paywall` module** (not folded into settings) so the upsell is
  triggerable from any surface. _Decided 2026-05-31._

## Open questions (deferred to implementation)

- `:core:billing` as a single module with an internal interface, or a strict `api`/`impl` split like
  features? (Default: api/impl split for true swappability.)
- Final exact prices per market (US anchors $1.99 / $19.99 confirmed; Play templates handle the rest).
- Whether a brand "supporter" accent color/token is introduced for the badge (design-system call).
