# Nubecita Pro — store / legal launch checklist

Runbook for **nubecita-q5ge.11** (epic **nubecita-q5ge**, design `openspec/changes/pro-supporter-first-iap/design.md`). Most steps are **manual web-console work** (Play Console, RevenueCat dashboard, Google payments) that cannot be scripted; this doc is the ordered, reviewable source of truth for them. Check items off as they land.

> **Do not ship the Pro epic to production until every "blocking" item below is ✅.** The free app must stay fully functional; never gate moderation/safety (design Goals/Non-Goals).

## Reference state (verified 2026-06-01 via RevenueCat MCP)

| Thing | Value |
|---|---|
| RevenueCat project | `Nubecita` — `projb2427c61` |
| Play Store app (RC) | `Nubecita (Play Store)` — `app53f75a9b9d`, package `net.kikin.nubecita` |
| Test Store app (RC) | `app04ee7f7969` (sample/scaffold only) |
| Entitlement (code expects) | `pro` — `PRO_ENTITLEMENT_ID` in `core/billing/.../RevenueCatMappers.kt` |
| Entitlement (dashboard today) | active = `Nubecita Pro` (`entlf3dc56b1fa`); a phantom `pro` lookup_key exists but isn't API-visible |
| Pricing anchors (design D9) | monthly **$1.99**, annual **$19.99** (≈16% off), no trial |
| Legal pages | `https://nubecita.app/terms/` + `https://nubecita.app/privacy-policy/` (repo `nubecita-web`) — wired into the app paywall |
| RC→Firebase integration | **active** (re-created 2026-06-01; Android app id + GA4 Measurement-Protocol secret + revenue reporting + sandbox) |

## A. RevenueCat ↔ Play reconciliation  *(blocking — without it a purchase never grants Pro)*

Mirrors the acceptance criteria recorded on this issue.

- [ ] **A1. Entitlement identifier is exactly `pro`.** Today the active entitlement is `Nubecita Pro` (id ≠ `"pro"`), so `"pro" in entitlements.active` is always false → `isPro` never flips. The v2 API can't rename a `lookup_key` (immutable) and `create pro` 409s on a phantom. **Resolve in the dashboard UI:** find the phantom/hidden `pro` (likely archived) → unarchive & reuse it, *or* delete it then rename `Nubecita Pro`'s identifier to `pro`. End state: exactly one **active** entitlement with identifier `pro`; archive (don't delete) the leftover for a reversible safety net.
- [ ] **A2. Create the Play subscription products** (see §B) and **import/link them into RevenueCat** (`app53f75a9b9d`). RC currently has **zero** Play products — today's `monthly`/`yearly`/`lifetime` are Test-Store samples.
- [ ] **A3. Attach the Play monthly + annual products to the `pro` entitlement.** Drop `lifetime` — it's a design Non-Goal.
- [ ] **A4. Repoint the `default` offering's `$rc_monthly` / `$rc_annual` packages** from the Test-Store samples to the real Play products. Confirm `is_current = true`. (`SubscriptionOfferingFixtures` is preview/test-only; production reads this offering.)

## B. Play Console — products  *(blocking; Play Console UI only)*

- [ ] **B1. Create one subscription** with base plans **`monthly`** ($1.99, P1M) and **`annual`** ($19.99, P1Y), auto-renewing, no free trial (design D9). Let Play expand the US anchors per market.
- [ ] **B2. Activate the base plans** and set availability for target countries.
- [ ] **B3. Google Play Developer API service account** linked to RevenueCat (so RC can read purchase/renewal state). RC dashboard → app credentials.

## C. Data-safety form  *(blocking; Play Console UI)*

- [ ] **C1.** Declare **Purchase history** + **device/other IDs** as collected (via the billing/RevenueCat path).
- [ ] **C2.** List **RevenueCat as a processor**; describe the deletion path. Pro is bound to an **anonymous** Play-account `appUserId`, **not** the Bluesky DID (design D3) — no DID leaves the device.
- [ ] **C3.** Keep existing Firebase Analytics/Crashlytics declarations accurate (now also receiving subscription events via the RC→Firebase integration).

## D. Legal content (ToS + Privacy)  *(blocking; repo `nubecita-web`)*

The app already links the live pages, but their **content predates Pro** (last updated 2026-05-16). Draft updates tracked separately in `nubecita-web`.

- [ ] **D1. Terms** (`terms/index.html`): remove/qualify "free of charge"; add a **Nubecita Pro subscription** section — auto-renewing, billed through Google Play, store-localized pricing, renews unless cancelled ≥24h before period end, manage/cancel in Google Play, refunds per Google Play policy, what Pro unlocks (PiP video + Supporter badge — convenience/cosmetic; free app stays fully functional), RevenueCat as billing infrastructure. Bump "Last updated".
- [ ] **D2. Privacy** (`privacy-policy/index.html`): add a **Purchases** section — Google Play processes payment; **RevenueCat** (processor) manages subscription state and receives purchase tokens + an anonymous app-user id + device/purchase metadata (no DID/Bluesky identity — design D3); subscription events may forward to Firebase Analytics via the RC→Firebase integration. Add RevenueCat to the third-parties list + link its privacy policy. Bump "Last updated".
- [ ] **D3.** Verify the in-app paywall disclosure block wording matches live Play subscription/disclosure policy before submission.
- [x] **D4.** App paywall points at the real URLs (`/terms/`, `/privacy-policy/`) — done in this change (`PaywallViewModel`).

## E. Payments / tax / listing  *(blocking; Google Play & payments console)*

- [ ] **E1.** Google **payments profile** + **tax** info verified for the developer account.
- [ ] **E2.** Confirm the **15% service-fee tier** (first $1M/yr) applies.
- [ ] **E3.** Store listing shows the **"In-app purchases"** badge.

## F. Pre-submission verification

- [ ] **F1.** Sandbox / license-tester purchase grants `isPro = true` in-app and unlocks PiP + the Supporter badge — this is the **nubecita-q5ge.12** smoke test (`run-instrumented`). Depends on A1–A4 + B.
- [ ] **F2.** Restore purchases works on a second device / reinstall (same Play account).
- [ ] **F3.** Cancellation reflects in-app after the period ends (RC entitlement expiry → `isPro` false), and an active PiP session is **not** force-closed mid-use (design risk note).
- [ ] **F4.** RC→Firebase events arrive in GA4 for a sandbox purchase (sandbox reporting is enabled). App-side `setFirebaseAppInstanceID` linkage is **nubecita-q5ge.13**.

## Out of scope (deferred, per design Non-Goals)

Lifetime purchase, free trial, networked/public badge, multi-account, tip jar, alternative billing.
