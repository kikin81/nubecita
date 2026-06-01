# Nubecita Pro — store / legal launch checklist

Runbook for **nubecita-q5ge.11** (epic **nubecita-q5ge**, design `openspec/changes/pro-supporter-first-iap/design.md`). Most steps are **manual web-console work** (Play Console, RevenueCat dashboard, Google payments) that cannot be scripted; this doc is the ordered, reviewable source of truth for them. Check items off as they land.

> **Status (2026-06-01):** A–D complete. Two gates remain before shipping: **E** (payments/tax + listing badge — almost certainly already satisfied, since active paid base plans require it, but still a hard prerequisite that must be **confirmed**) and **F1 / nubecita-q5ge.12** (sandbox purchase smoke). **Do not ship to production until E is confirmed AND F1 is ✅.** The free app must stay fully functional; never gate moderation/safety.

## Reference state (verified 2026-06-01 via RevenueCat MCP)

| Thing | Value |
|---|---|
| RevenueCat project | `Nubecita` — `projb2427c61` |
| Play Store app (RC) | `Nubecita (Play Store)` — `app53f75a9b9d`, package `net.kikin.nubecita` |
| Test Store app (RC) | `app04ee7f7969` (sample/scaffold only; no longer in the offering) |
| Entitlement | ✅ active = `pro` (`entlc4a0832656`, display "Nubecita Pro"); the old `Nubecita Pro`-keyed entitlement is gone |
| Play products | `nubecita_pro:monthly` (`prod803bc4ea6f`) + `nubecita_pro:annual` (`prod530933c081`), both attached to `pro` |
| Offering | `default` (current) → `$rc_monthly`/`$rc_annual` point at the Play products; `$rc_lifetime` removed |
| Pricing (design D9) | monthly **$1.99**, annual **$19.99** (≈16% off), no trial |
| Legal pages | `https://nubecita.app/terms/` + `https://nubecita.app/privacy-policy/` — live, updated 2026-06-01, wired into the app paywall |
| RC→Firebase integration | **active** (Android app id + GA4 Measurement-Protocol secret + revenue reporting + sandbox) |

## A. RevenueCat ↔ Play reconciliation  *(blocking — without it a purchase never grants Pro)*  — ✅ DONE

- [x] **A1. Entitlement identifier is exactly `pro`** — single active entitlement `pro` (`entlc4a0832656`); matches `PRO_ENTITLEMENT_ID` in `RevenueCatMappers.kt`.
- [x] **A2. Play subscription products imported into RevenueCat** — `nubecita_pro:monthly` + `nubecita_pro:annual` on `app53f75a9b9d`.
- [x] **A3. Play monthly + annual attached to the `pro` entitlement** — `lifetime` dropped (Non-Goal).
- [x] **A4. `default` offering's `$rc_monthly` / `$rc_annual` packages repointed** from the Test-Store samples to the Play products; `$rc_lifetime` package deleted.

## B. Play Console — products  *(blocking; Play Console UI only)*  — ✅ DONE

- [x] **B1. Subscription `nubecita_pro`** with base plans **`monthly`** ($1.99, P1M) and **`annual`** ($19.99, P1Y), auto-renewing, no free trial. Benefits set (PiP + Supporter badge); age rating 16+.
- [x] **B2. Base plans active** across 174 countries/regions.
- [x] **B3. Google Play Developer API service account** linked (implied — products imported into RC successfully).

## C. Data-safety form  *(blocking; Play Console UI)*  — ✅ DONE

- [x] **C1.** Purchase history declared **collected** (optional — only when a user subscribes), purposes **App functionality + Analytics**, processed-ephemerally = No.
- [x] **C2.** RevenueCat treated as a **service provider** (processing on our behalf → not "shared"); deletion path via `privacy@nubecita.app`. Pro bound to an **anonymous** Play `appUserId`, not the Bluesky DID (design D3).
- [x] **C3.** Existing Firebase Analytics/Crashlytics declarations kept accurate (now also receiving subscription events via the RC→Firebase integration). Photos/videos left undeclared (user-initiated upload to the user's own PDS; no first-party collection).

## D. Legal content (ToS + Privacy)  — ✅ DONE (live)

- [x] **D1. Terms** (`nubecita-web/terms/index.html`): added a Nubecita Pro subscription section (auto-renewal, billed through Play, store-localized pricing, cancel/refund per Play, perks = PiP + Supporter badge, RevenueCat as billing infra). Deployed (PR nubecita-web#16, "Last updated: June 1, 2026").
- [x] **D2. Privacy** (`nubecita-web/privacy-policy/index.html`): added a "Purchases and subscriptions" section (Google Play processes payment; RevenueCat processor; anonymous app-user id, **no DID** per design D3; aggregate Firebase forwarding) + RevenueCat in the third-parties list. Deployed.
- [x] **D3.** Disclosure block + the live policy wording cover the required Play subscription disclosures. *(Final eyeball before submission.)*
- [x] **D4.** App paywall points at the real URLs (`/terms/`, `/privacy-policy/`) — `PaywallViewModel` (PR #383).

## E. Payments / tax / listing  *(Google Play & payments console — likely already satisfied; confirm)*

- [ ] **E1.** Google **payments profile** + **tax** info verified. *(Active paid base plans can't publish without this — confirm.)*
- [ ] **E2.** Confirm the **15% service-fee tier** (first $1M/yr) applies.
- [ ] **E3.** Store listing shows the **"In-app purchases"** badge (auto-appears once an IAP is published).

## F. Pre-submission verification

- [ ] **F1.** Sandbox / license-tester purchase grants `isPro = true` in-app and unlocks PiP + the Supporter badge — **nubecita-q5ge.12** (`run-instrumented`). Now unblocked (A–B done).
- [ ] **F2.** Restore purchases works on a second device / reinstall (same Play account).
- [ ] **F3.** Cancellation reflects in-app after the period ends (`isPro` → false); an active PiP session is **not** force-closed mid-use (design risk note).
- [ ] **F4.** RC→Firebase events arrive in GA4 for a sandbox purchase. The app-side step — linking RevenueCat's anonymous app-user id to the Firebase app-instance id so events attribute to the right user — is **nubecita-q5ge.13** (the RevenueCat SDK exposes a helper for this; confirm the exact API against the pinned SDK version when implementing).

## Out of scope (deferred, per design Non-Goals)

Lifetime purchase, free trial, networked/public badge, multi-account, tip jar, alternative billing.
