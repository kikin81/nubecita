# Proposal: Nubecita Pro — first in-app purchase

> Source design: `docs/superpowers/specs/2026-05-31-pro-supporter-first-iap-design.md`
> Beads epic: `nubecita-q5ge` (children `nubecita-q5ge.1`–`.11`)

## Why

Nubecita has no revenue path; the developer has enabled a Google Play payment profile but
the app ships no billing. This change introduces **Nubecita Pro**, an honest indie
"support the developer" subscription that keeps the free app fully usable while offering a
couple of perks as a thank-you. It funds ongoing independent, ad-free maintenance.

## What Changes

- Add an auto-renewing **subscription** (monthly $1.99 / annual $19.99, 16% off, no trial)
  sold via **Google Play Billing**, mediated by **RevenueCat** (new dependency).
- Add a new **SDK-agnostic `:core:billing`** module exposing `EntitlementRepository`
  (`isPro: StateFlow<Boolean>`) and `BillingRepository` over our own `:data:models` types —
  no RevenueCat type leaks past the module boundary, so the provider is swappable later.
- Bind Pro to the **Google Play account** via an anonymous RevenueCat `appUserId` (not the
  Bluesky DID), so Pro survives a deleted Bluesky account and follows the Google account
  across devices via Restore.
- Add **Picture-in-Picture (PiP)** video as a Pro perk, gated through a single
  `PipController.isEnabled` choke-point (device-support × entitlement). Targets the API 31+
  auto-enter flow with an API 26–30 manual fallback.
- Add a **self-visible Supporter badge** (Tier 1, no backend) on the signed-in user's own
  profile, gated on `isPro`.
- Add a **custom Compose paywall** in a new **`:feature:paywall`** module (not in settings),
  so the upsell can be triggered from any surface. Live prices come from RevenueCat
  `Offerings`, rendered with our design system.
- Add a **Nubecita Pro** section in Settings (manage-subscription Play deep link + restore).
- Wire **Play Console products, Data-safety declarations, and ToS/Privacy links**.

## Non-goals

- Networked / public Supporter badge (others see it) and any ATProto Labeler — deferred
  (Tier 2/3). A "Nubecita Supporter" mark is only ever rendered by Nubecita anyway.
- Multiple Bluesky accounts (a *future* Pro perk).
- Tip jar / one-time donation (separate epic; different Play-policy/payment story).
- Lifetime / non-renewing product.
- Free trial; alternative/user-choice billing (EEA DMA).
- Media3 `MediaSession` migration for unified PiP + lockscreen controls (fast-follow).
- PiP playback-position persistence across process death.

## Capabilities

### New Capabilities
- `core-billing`: SDK-agnostic subscription + entitlement domain — repository interfaces,
  our own subscription/offering/entitlement models, and the swappable RevenueCat
  implementation. Anonymous Google-Play-account identity.
- `feature-paywall`: the custom Compose paywall screen, its MVI presenter, the locked
  "Keep Nubecita flying" copy + required Play disclosures, and the upsell entry points.
- `pro-picture-in-picture`: Picture-in-Picture video gated on the Pro entitlement, including
  the `PipController` choke-point, the Activity PiP bridge, and the `SharedVideoPlayer`
  background-pause seam.
- `pro-supporter-badge`: the Tier-1 self-visible Supporter badge on the user's own profile.

### Modified Capabilities
<!-- No existing capability's documented requirements change at the spec level; PiP adds new
     behavior captured under pro-picture-in-picture, and the badge under pro-supporter-badge.
     Settings/profile/video integration is captured in Impact below. -->

## Impact

- **New modules:** `:core:billing` (api/impl), `:feature:paywall` (api/impl).
- **New dependency:** `com.revenuecat.purchases:purchases` (v9.x; Google Play Billing 8).
  First third-party billing/purchase SDK; declare in `libs.versions.toml`.
- **`:app`:** `Application.onCreate` calls `Purchases.configure` (sanctioned SDK-init spot);
  `MainActivity` gains PiP manifest attrs (`supportsPictureInPicture`, `configChanges`),
  a PiP bridge, and a `RemoteAction` broadcast receiver; `MainShell` suppresses its nav suite
  in PiP; a new `@MainShell` `PaywallRoute` and `LocalPipController` CompositionLocal.
- **`:core:video`:** `SharedVideoPlayer.appBackgroundObserver` gains an `isInPip` seam so it
  does not auto-pause during PiP (load-bearing lifecycle change).
- **`:feature:videoplayer` / `:feature:mediaviewer`:** PiP Compose wiring + chrome hiding;
  pop-out upsell affordance.
- **`:feature:profile`:** Supporter badge in the profile hero.
- **`:feature:settings`:** Nubecita Pro section.
- **`:data:models`:** new `@Immutable` subscription/offering/entitlement models.
- **MVI/Compose deviations to call out:** PiP is driven from the Activity/Compose-runtime
  layer via a `CompositionLocal`, **not** from any ViewModel (VMs never touch the Activity or
  PiP APIs); `Purchases.configure` runs in `Application.onCreate`, not via Hilt. Both are
  consistent with existing patterns (`LocalMainShellNavState`, app-level SDK init).
- **Store/legal/ops:** Google Play products + base plans, Data-safety form update (purchase
  history + IDs, RevenueCat as processor), in-app + listing ToS/Privacy links, Play Developer
  API service-account credential for RevenueCat.
