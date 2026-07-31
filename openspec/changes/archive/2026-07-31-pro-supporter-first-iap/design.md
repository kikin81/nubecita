# Design: Nubecita Pro — first in-app purchase

> Full source design (with mockups, research, and child-issue sequence):
> `docs/superpowers/specs/2026-05-31-pro-supporter-first-iap-design.md`.
> This document condenses the architectural decisions for the OpenSpec change.

## Context

Nubecita ships no billing. The developer has enabled a Google Play payment profile and added
the RevenueCat MCP. We need a first paid offering that funds independent maintenance without
degrading the free experience. The app is MVI + Compose + Hilt; `:core:video` already owns a
single process-scoped `ExoPlayer` via `SharedVideoPlayer`, which is the key enabler for PiP.

## Goals / Non-Goals

**Goals:**
- A shippable, policy-compliant subscription (monthly + annual) via Google Play Billing.
- PiP video and a self-visible Supporter badge as Pro perks.
- A billing boundary that lets us swap RevenueCat for a self-hosted backend later.
- Keep the full free app usable; never gate moderation/safety.

**Non-Goals:**
- Networked/public badge, ATProto Labeler, multi-account, tip jar, lifetime, free trial,
  alternative billing, `MediaSession` migration, PiP position persistence. (See proposal.)

## Decisions

### D1 — SDK-agnostic `:core:billing` boundary (RevenueCat in `impl` only)
Expose `EntitlementRepository` + `BillingRepository` over `:data:models` types; RevenueCat lives
only in `impl`, configured in `Application.onCreate`. **Why:** future provider swap rewrites one
module. **Alternative considered:** use RevenueCat types directly in features — rejected (lock-in).

### D2 — Custom Compose paywall, not the drop-in `purchases-ui`
The app's identity is hand-crafted native UI at 120hz; the paywall is where a generic look hurts
most. Live prices still come from `Offerings`. **Alternative:** drop-in paywall (faster, dashboard-
editable) — rejected for v1; remote-config copy is a deferred way to recover that benefit.

### D3 — Bind Pro to the Google Play account (anonymous `appUserId`), not the DID
Pro is a person/device capability. **Why:** survives a deleted Bluesky account, follows the
Google account across devices via Restore, sends no DID to the provider, and leaves multi-account
open as a future perk. **Alternative:** `logIn(did)` — rejected (deleted-DID reconciliation, badge
coupling).

### D4 — Single `PipController.isEnabled` choke-point (device-support × `isPro`)
Folds two conditions into one declarative boolean that gates all ~4 PiP call sites; auto-enter is
published as `autoEnter = isEnabled && isPlaying`. **Why:** no scattered entitlement branches.

### D5 — PiP via the Activity/Compose layer, never the ViewModel
Reach the Activity PiP bridge through `LocalPipController` (mirrors `LocalMainShellNavState`).
VMs expose `isPlaying`/`videoAspectRatio`; the Composable drives the bridge. **Why:** preserves
the MVI boundary; VMs cannot hold an Activity reference.

### D6 — `SharedVideoPlayer` `isInPip` seam (load-bearing)
`appBackgroundObserver` currently pauses on `ProcessLifecycleOwner.onStop`; inject an
`isInPip: () -> Boolean` (default `{ false }`) and skip the pause while in PiP, keeping
`:core:video` Activity-free. This is the single riskiest behavioral change.

### D7 — Target API 31+ auto-enter, fall back to manual entry on 26–30
minSdk is 26, so basic PiP is universal; `setAutoEnterEnabled` needs 31. Manifest gains
`supportsPictureInPicture` + `configChanges` (load-bearing: prevents Activity recreation that
would tear down the Nav3 backstack).

### D8 — Badge Tier 1 (self-visible, no backend) for v1
`isPro` → render badge on the user's own profile. **Why:** keeps the first paid release server-
free and rollback-safe. Tier 2 (networked, RevenueCat webhook/REST verify → `badge_did`) is a
later child; the Tier-1 composable is reused.

### D9 — Pricing: $1.99/mo + $19.99/yr (16% off), no trial
Indie "support the dev" comps; a trial frames a supporter sub as transactional. Prices rendered
from store-localized values; US figures are the anchors Play templates expand per market.

## Risks / Trade-offs

- **PiP `onStop` gate regression** → could pause (or fail to pause) at the wrong time. Mitigation:
  unit-test the seam (PiP-state suppresses the auto-pause; real dismiss still pauses); instrumented
  transition test.
- **Activity recreation on PiP transition** (missing `configChanges`) → tears down Nav3 backstack.
  Mitigation: declare the full `configChanges` set; instrumented "no recreate" test.
- **Entitlement check blocking UI / cold-start latency** → Mitigation: `isPro` is a `StateFlow`
  starting `false`; features never synchronously gate.
- **Play policy non-compliance** (disclosures, Data-safety, digital-goods) → Mitigation: paywall
  disclosure block + Terms/Privacy/Restore; Data-safety form update; verify live policy wording
  before submission. Keep moderation/safety free; keep "unofficial client" framing; no "verified".
- **Provider lock-in** → Mitigation: D1 boundary; no provider type leaks.
- **Entitlement loss mid-session** yanking an open PiP window → Mitigation: stop offering new PiP
  entries only; never force-close an active PiP session.

## Migration Plan

Sequenced children (beads `nubecita-q5ge.1`–`.11`): billing skeleton → RevenueCat impl →
PipController → manifest+Activity bridge → SharedVideoPlayer seam → PiP Compose wiring → paywall →
pop-out upsell → badge → settings → store/legal. The billing skeleton ships a fake `impl` so all
downstream work builds against the interface before RevenueCat lands. **Rollback:** every step is
an app release except the Play Console/store config; no server component in v1, so the perks can
be disabled by shipping a build that treats `isPro` as `false` if needed.

## Open Questions

- ~~`:core:billing` strict `api`/`impl` split vs single module with internal interface (default: split).~~
  **Resolved (q5ge.1):** single `:core:billing` module — `public` repository
  interfaces + `internal` impls + a `@Binds` `BillingModule`, matching the
  established `:core:*` convention (`profile`/`posts`/`auth`). The provider impl
  stays `internal`, so no consumer can reference a RevenueCat type; the extra
  module the split would add buys nothing over this.
- Whether a brand "supporter" accent color/token is introduced for the badge (design-system call).
- Exact RevenueCat `purchases` version to pin in `libs.versions.toml` at implementation time.
